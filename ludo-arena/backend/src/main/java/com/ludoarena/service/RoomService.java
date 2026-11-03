package com.ludoarena.service;

import com.ludoarena.dto.Dtos.ActiveGameDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.RoomDto;
import com.ludoarena.dto.Dtos.RoomUpdate;
import com.ludoarena.dto.Requests.CreateRoom;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.RoomStatus;
import com.ludoarena.model.Match;
import com.ludoarena.model.Room;
import com.ludoarena.model.Room.RoomPlayer;
import com.ludoarena.model.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Room lifecycle (create / join / leave / ready / auto-start).
 *
 * Every state change is a single atomic conditional MongoDB update so that simultaneous clicks cannot overfill a
 * room, double-start a match, or put one user in two rooms:
 *  - users.activeRoomId is claimed with "set where activeRoomId is null" (one active room per user);
 *  - joining pushes into players only if the room is WAITING, the user is absent and there is a free seat;
 *  - starting flips WAITING -> IN_PROGRESS exactly once.
 */
@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{6}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long STALE_ROOM_MINUTES = 30;

    private final MongoTemplate mongo;
    private final UserService userService;
    private final MatchService matchService;
    private final SimpMessagingTemplate messaging;
    private final PlatformSettingsService settings;

    public RoomService(MongoTemplate mongo, UserService userService, MatchService matchService,
                       SimpMessagingTemplate messaging, PlatformSettingsService settings) {
        this.mongo = mongo;
        this.userService = userService;
        this.matchService = matchService;
        this.messaging = messaging;
        this.settings = settings;
    }

    // ---------------------------------------------------------------------- create

    public RoomDto create(String userId, CreateRoom req) {
        if (req.maxPlayers() != 2 && req.maxPlayers() != 4) {
            throw ApiException.badRequest("INVALID_MODE", "Rooms support 2 or 4 players");
        }
        if (settings.get().maintenanceMode) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE", "Ludo Arena is under maintenance");
        }
        User u = userService.require(userId);
        Room r = new Room();
        r.id = new ObjectId().toHexString();
        claimSlot(userId, r.id);
        try {
            r.name = req.name().trim();
            r.hostId = userId;
            r.maxPlayers = req.maxPlayers();
            r.isPrivate = req.isPrivate();
            r.status = RoomStatus.WAITING;
            r.createdAt = Instant.now();
            r.updatedAt = r.createdAt;
            r.players.add(player(u));
            for (int attempt = 0; ; attempt++) {
                r.code = newCode();
                try {
                    r = mongo.insert(r);
                    break;
                } catch (DuplicateKeyException e) {
                    if (attempt >= 5) throw e;
                }
            }
        } catch (RuntimeException e) {
            releaseSlot(userId, r.id);
            throw e;
        }
        return RoomDto.from(r, userId);
    }

    // ------------------------------------------------------------------------ join

    public RoomDto join(String userId, String idOrCode) {
        Room r = resolve(idOrCode);
        if (r.hasPlayer(userId)) return RoomDto.from(r, userId); // idempotent
        if (r.status != RoomStatus.WAITING) {
            throw ApiException.conflict("ROOM_NOT_JOINABLE", "This room is no longer accepting players");
        }
        User u = userService.require(userId);
        claimSlot(userId, r.id);
        Room updated;
        try {
            Query q = Query.query(Criteria.where("id").is(r.id)
                    .and("status").is(RoomStatus.WAITING)
                    .and("players.userId").ne(userId)
                    .and("players." + (r.maxPlayers - 1)).exists(false));
            Update upd = new Update().push("players", player(u)).set("updatedAt", Instant.now());
            updated = mongo.findAndModify(q, upd, FindAndModifyOptions.options().returnNew(true), Room.class);
        } catch (RuntimeException e) {
            releaseSlot(userId, r.id);
            throw e;
        }
        if (updated == null) {
            releaseSlot(userId, r.id);
            throw ApiException.conflict("ROOM_FULL", "This room is full or has already started");
        }
        broadcast("PLAYER_JOINED", updated);
        return RoomDto.from(updated, userId);
    }

    // ----------------------------------------------------------------------- leave

    public void leave(String userId, String roomId) {
        Room r = resolve(roomId);
        if (!r.hasPlayer(userId)) {
            releaseSlot(userId, r.id);
            return;
        }
        if (r.status == RoomStatus.IN_PROGRESS) {
            if (r.matchId != null) matchService.forfeit(r.matchId, userId);
            releaseSlot(userId, r.id);
            return;
        }
        if (r.status != RoomStatus.WAITING) {
            releaseSlot(userId, r.id);
            return;
        }
        Query q = Query.query(Criteria.where("id").is(r.id).and("status").is(RoomStatus.WAITING));
        Update upd = new Update().pull("players", new Document("userId", userId)).set("updatedAt", Instant.now());
        Room updated = mongo.findAndModify(q, upd, FindAndModifyOptions.options().returnNew(true), Room.class);
        releaseSlot(userId, r.id);
        if (updated == null) return; // room started concurrently; nothing more to do here
        if (updated.players.isEmpty()) {
            mongo.updateFirst(Query.query(Criteria.where("id").is(r.id)),
                    new Update().set("status", RoomStatus.CLOSED).set("updatedAt", Instant.now()), Room.class);
            updated.status = RoomStatus.CLOSED;
            broadcast("ROOM_CLOSED", updated);
            return;
        }
        if (userId.equals(updated.hostId)) {
            String newHost = updated.players.get(0).userId;
            mongo.updateFirst(Query.query(Criteria.where("id").is(r.id)), new Update().set("hostId", newHost), Room.class);
            updated.hostId = newHost;
        }
        broadcast("PLAYER_LEFT", updated);
    }

    // ----------------------------------------------------------------------- ready

    public RoomDto setReady(String userId, String roomId, boolean ready) {
        Query q = Query.query(Criteria.where("id").is(roomId)
                .and("status").is(RoomStatus.WAITING)
                .and("players.userId").is(userId));
        Room updated = mongo.findAndModify(q, new Update().set("players.$.ready", ready).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Room.class);
        if (updated == null) {
            throw ApiException.conflict("NOT_IN_WAITING_ROOM", "You are not in a room that is waiting to start");
        }
        broadcast("PLAYER_READY", updated);
        if (updated.players.size() == updated.maxPlayers && updated.players.stream().allMatch(p -> p.ready)) {
            tryStart(updated);
        }
        return RoomDto.from(updated, userId);
    }

    /** WAITING -> IN_PROGRESS can succeed only once; the winner of that race creates the match. */
    private void tryStart(Room full) {
        Query q = Query.query(Criteria.where("id").is(full.id)
                .and("status").is(RoomStatus.WAITING)
                .and("players." + (full.maxPlayers - 1)).exists(true));
        Room started = mongo.findAndModify(q, new Update().set("status", RoomStatus.IN_PROGRESS).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Room.class);
        if (started == null) return;
        try {
            Match m = matchService.startMatch(started);
            mongo.updateFirst(Query.query(Criteria.where("id").is(started.id)), new Update().set("matchId", m.id), Room.class);
            started.matchId = m.id;
            messaging.convertAndSend("/topic/room/" + started.id, new RoomUpdate("MATCH_STARTED", RoomDto.from(started, null), m.id));
        } catch (RuntimeException e) {
            log.error("Could not start match for room {}", started.id, e);
            mongo.updateFirst(Query.query(Criteria.where("id").is(started.id)),
                    new Update().set("status", RoomStatus.WAITING).set("players.$[].ready", false), Room.class);
            throw e;
        }
    }

    // ------------------------------------------------------------------------ reads

    public RoomDto get(String userId, String idOrCode) {
        return RoomDto.from(resolve(idOrCode), userId);
    }

    public PageResponse<RoomDto> listPublic(Integer maxPlayers, int page, int size) {
        Criteria c = Criteria.where("status").is(RoomStatus.WAITING).and("isPrivate").is(false);
        if (maxPlayers != null) c = c.and("maxPlayers").is(maxPlayers);
        Page<Room> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), Room.class);
        return PageResponse.of(p, r -> RoomDto.from(r, null));
    }

    /** The room (and running match) the user currently belongs to, used by "resume game". */
    public ActiveGameDto active(String userId) {
        User u = userService.require(userId);
        if (u.activeRoomId == null) return null;
        Room r = mongo.findById(u.activeRoomId, Room.class);
        if (r == null || r.status == RoomStatus.CLOSED || r.status == RoomStatus.FINISHED || !r.hasPlayer(userId)) {
            releaseSlot(userId, u.activeRoomId);
            return null;
        }
        String matchId = r.status == RoomStatus.IN_PROGRESS ? matchService.activeMatchId(userId) : null;
        return new ActiveGameDto(RoomDto.from(r, userId), matchId);
    }

    public boolean isMember(String roomId, String userId) {
        return mongo.exists(Query.query(Criteria.where("id").is(roomId).and("players.userId").is(userId)), Room.class);
    }

    // ---------------------------------------------------------------------- cleanup

    @Scheduled(fixedDelay = 60_000)
    public void closeStaleRooms() {
        Instant cutoff = Instant.now().minus(STALE_ROOM_MINUTES, ChronoUnit.MINUTES);
        List<Room> stale = mongo.find(Query.query(Criteria.where("status").is(RoomStatus.WAITING).and("updatedAt").lt(cutoff)), Room.class);
        for (Room r : stale) {
            var res = mongo.updateFirst(Query.query(Criteria.where("id").is(r.id).and("status").is(RoomStatus.WAITING)),
                    new Update().set("status", RoomStatus.CLOSED).set("updatedAt", Instant.now()), Room.class);
            if (res.getModifiedCount() == 0) continue;
            for (RoomPlayer p : r.players) releaseSlot(p.userId, r.id);
            r.status = RoomStatus.CLOSED;
            broadcast("ROOM_CLOSED", r);
        }
    }

    // --------------------------------------------------------------------- internals

    private Room resolve(String idOrCode) {
        Optional<Room> found;
        if (idOrCode != null && CODE.matcher(idOrCode.toUpperCase()).matches() && !ObjectId.isValid(idOrCode)) {
            found = Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("code").is(idOrCode.toUpperCase())), Room.class));
        } else {
            found = ObjectId.isValid(idOrCode) ? Optional.ofNullable(mongo.findById(idOrCode, Room.class)) : Optional.empty();
        }
        return found.orElseThrow(() -> ApiException.notFound("ROOM_NOT_FOUND", "Room not found"));
    }

    private void claimSlot(String userId, String roomId) {
        if (tryClaim(userId, roomId)) return;
        // self-heal a stale claim (room finished/closed or user no longer inside it) and retry once
        User u = userService.require(userId);
        if (u.activeRoomId != null) {
            Room old = mongo.findById(u.activeRoomId, Room.class);
            boolean stale = old == null || old.status == RoomStatus.CLOSED || old.status == RoomStatus.FINISHED || !old.hasPlayer(userId);
            if (stale) {
                releaseSlot(userId, u.activeRoomId);
                if (tryClaim(userId, roomId)) return;
            }
        }
        throw ApiException.conflict("ALREADY_IN_ROOM", "Leave your current room before joining another one");
    }

    private boolean tryClaim(String userId, String roomId) {
        var res = mongo.updateFirst(Query.query(Criteria.where("id").is(userId).and("activeRoomId").is(null)),
                new Update().set("activeRoomId", roomId), User.class);
        return res.getModifiedCount() > 0;
    }

    private void releaseSlot(String userId, String roomId) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(userId).and("activeRoomId").is(roomId)),
                new Update().unset("activeRoomId"), User.class);
    }

    private void broadcast(String type, Room r) {
        messaging.convertAndSend("/topic/room/" + r.id, new RoomUpdate(type, RoomDto.from(r, null), r.matchId));
    }

    private static RoomPlayer player(User u) {
        RoomPlayer p = new RoomPlayer();
        p.userId = u.id;
        p.name = u.name;
        p.avatar = u.avatar;
        p.ready = false;
        p.joinedAt = Instant.now();
        return p;
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }
}
