package io.finflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OutboxAppender{
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxAppender(OutboxEventRepository repository){
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(String aggregateType, String aggregateId, String type, Object payload){
        final String json;
        try{
            json = objectMapper.writeValueAsString(payload);
        } catch(JsonProcessingException e){
            throw new OutboxException("Failed to serialize outbox payload for type=" + type,e);
        }

        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                type,
                json,
                OffsetDateTime.now());

        return repository.save(event);
    }
}
