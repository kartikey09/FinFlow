package io.finflow.outbox;

/**
 * Thrown when an event payload cannot be serialized to JSON. Unchecked so it
 * propagates out of the caller's @Transactional method and rolls the whole
 * transaction back — a payload we can't serialize must NOT let the business
 * change commit silently without its event.
 */
public class OutboxException extends RuntimeException {
    public OutboxException(String message, Throwable cause){
        super(message, cause);
    }
}