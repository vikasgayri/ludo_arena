package com.ludoarena.repository;

import com.ludoarena.model.ChatMessage;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findTop50ByMatchIdOrderByAtDesc(String matchId);
}
