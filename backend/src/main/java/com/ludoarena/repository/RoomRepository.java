package com.ludoarena.repository;

import com.ludoarena.model.Enums.RoomStatus;
import com.ludoarena.model.Room;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoomRepository extends MongoRepository<Room, String> {
    Optional<Room> findByCode(String code);

    boolean existsByCode(String code);

    List<Room> findByStatusAndCreatedAtBefore(RoomStatus status, Instant before);
}
