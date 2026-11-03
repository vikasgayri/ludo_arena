package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.dto.Dtos.RoomDto;
import com.ludoarena.dto.Requests.CreateRoom;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.RoomStatus;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.Room;
import com.ludoarena.model.Room.RoomPlayer;
import com.ludoarena.model.User;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomServiceTest {
    private static final String ROOM_ID = "64b7f8f8f8f8f8f8f8f8f8f8";

    @Mock MongoTemplate mongo;
    @Mock UserService userService;
    @Mock MatchService matchService;
    @Mock SimpMessagingTemplate messaging;
    @Mock PlatformSettingsService settings;

    private RoomService service;

    @BeforeEach
    void setUp() {
        service = new RoomService(mongo, userService, matchService, messaging, settings);
        when(settings.get()).thenReturn(new PlatformSettings());
        when(mongo.insert(any(Room.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static User user(String id, String activeRoomId) {
        User u = new User();
        u.id = id;
        u.name = "Player " + id;
        u.avatar = "avatar-01";
        u.activeRoomId = activeRoomId;
        return u;
    }

    private void claim(boolean success) {
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(success ? 1 : 0, success ? 1L : 0L, null));
    }

    private static Room waitingRoom(int max, String... memberIds) {
        Room r = new Room();
        r.id = ROOM_ID;
        r.code = "ABC234";
        r.name = "Test";
        r.maxPlayers = max;
        r.hostId = memberIds.length > 0 ? memberIds[0] : null;
        r.status = RoomStatus.WAITING;
        r.createdAt = Instant.now();
        for (String id : memberIds) {
            RoomPlayer p = new RoomPlayer();
            p.userId = id;
            p.name = id;
            r.players.add(p);
        }
        return r;
    }

    @Test
    void onlyTwoOrFourPlayerRoomsCanBeCreated() {
        ApiException e = assertThrows(ApiException.class, () -> service.create("u1", new CreateRoom("My room", 3, false)));
        assertEquals("INVALID_MODE", e.code());
        verify(mongo, never()).insert(any(Room.class));
    }

    @Test
    void creatingARoomMakesTheCreatorHostAndFirstPlayer() {
        when(userService.require("u1")).thenReturn(user("u1", null));
        claim(true);
        RoomDto dto = service.create("u1", new CreateRoom("  Friday night ", 4, false));
        assertEquals("Friday night", dto.name());
        assertEquals(4, dto.maxPlayers());
        assertEquals("u1", dto.hostId());
        assertEquals(1, dto.players().size());
        assertTrue(dto.players().get(0).host());
        assertEquals(6, dto.code().length());
    }

    @Test
    void aUserAlreadyInAnotherLiveRoomCannotCreateOne() {
        Room other = waitingRoom(2, "u1");
        other.id = "64b7f8f8f8f8f8f8f8f8f8f9";
        when(userService.require("u1")).thenReturn(user("u1", other.id));
        claim(false);
        when(mongo.findById(other.id, Room.class)).thenReturn(other);
        ApiException e = assertThrows(ApiException.class, () -> service.create("u1", new CreateRoom("Another", 2, false)));
        assertEquals("ALREADY_IN_ROOM", e.code());
        verify(mongo, never()).insert(any(Room.class));
    }

    @Test
    void joiningARoomYouAreAlreadyInIsIdempotent() {
        when(mongo.findById(ROOM_ID, Room.class)).thenReturn(waitingRoom(2, "u1"));
        RoomDto dto = service.join("u1", ROOM_ID);
        assertEquals(1, dto.players().size());
        verify(mongo, never()).findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Room.class));
    }

    @Test
    void joiningAFullRoomIsRejectedAndTheUsersSlotIsReleased() {
        when(mongo.findById(ROOM_ID, Room.class)).thenReturn(waitingRoom(2, "host"));
        when(userService.require("u2")).thenReturn(user("u2", null));
        claim(true);
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(null); // atomic push matched nothing: someone else took the last seat
        ApiException e = assertThrows(ApiException.class, () -> service.join("u2", ROOM_ID));
        assertEquals("ROOM_FULL", e.code());
        // claim + release
        verify(mongo, org.mockito.Mockito.atLeast(2)).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    @Test
    void finishedRoomsCannotBeJoined() {
        Room r = waitingRoom(2, "host");
        r.status = RoomStatus.IN_PROGRESS;
        when(mongo.findById(ROOM_ID, Room.class)).thenReturn(r);
        ApiException e = assertThrows(ApiException.class, () -> service.join("u2", ROOM_ID));
        assertEquals("ROOM_NOT_JOINABLE", e.code());
    }

    @Test
    void unknownRoomIsNotFound() {
        when(mongo.findById(ROOM_ID, Room.class)).thenReturn(null);
        ApiException e = assertThrows(ApiException.class, () -> service.get("u1", ROOM_ID));
        assertEquals("ROOM_NOT_FOUND", e.code());
        assertThrows(ApiException.class, () -> service.get("u1", "not-valid!"));
    }

    @Test
    void privateRoomCodeIsHiddenFromNonMembers() {
        Room r = waitingRoom(2, "host");
        r.isPrivate = true;
        when(mongo.findById(ROOM_ID, Room.class)).thenReturn(r);
        assertEquals(null, service.get("stranger", ROOM_ID).code());
        assertEquals("ABC234", service.get("host", ROOM_ID).code());
    }

    @Test
    void readyOnARoomYouAreNotInIsRejected() {
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(null);
        ApiException e = assertThrows(ApiException.class, () -> service.setReady("u9", ROOM_ID, true));
        assertEquals("NOT_IN_WAITING_ROOM", e.code());
    }

    @Test
    void whenTheRoomIsFullAndEveryoneIsReadyTheMatchStarts() {
        Room all = waitingRoom(2, "u1", "u2");
        all.players.forEach(p -> p.ready = true);
        Room started = waitingRoom(2, "u1", "u2");
        started.status = RoomStatus.IN_PROGRESS;
        com.ludoarena.model.Match m = new com.ludoarena.model.Match();
        m.id = "match-1";
        // 1st findAndModify = set ready, 2nd = WAITING -> IN_PROGRESS
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(all, started);
        when(matchService.startMatch(started)).thenReturn(m);

        service.setReady("u2", ROOM_ID, true);

        verify(matchService).startMatch(started);
    }

    @Test
    void theMatchIsNotStartedWhileSomeoneIsNotReady() {
        Room notAll = waitingRoom(2, "u1", "u2");
        notAll.players.get(0).ready = true;
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(notAll);
        service.setReady("u1", ROOM_ID, true);
        verify(matchService, never()).startMatch(any(Room.class));
    }
}
