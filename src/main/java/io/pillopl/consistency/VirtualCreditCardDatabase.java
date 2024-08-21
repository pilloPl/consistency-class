package io.pillopl.consistency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class VirtualCreditCardDatabase {
    private final Map<CardId, List<EventEnvelope>> cards = new ConcurrentHashMap<>();

    void save(VirtualCreditCard card) {
        List<EventEnvelope> stream = cards.getOrDefault(card.id(), new ArrayList<>());
        var cartId = card.id().id().toString();
        AtomicInteger version = new AtomicInteger(stream.size());

        var newEvents = card.pendingEvents().stream().map(e ->
                EventEnvelope.from(cartId, e, version.incrementAndGet())
        ).toList();

        stream.addAll(newEvents);
        cards.put(card.id(), stream);
        card.flush();
    }

    VirtualCreditCard find(CardId cardId) {
        List<EventEnvelope> stream = cards.getOrDefault(cardId, new ArrayList<>());
        return VirtualCreditCard.recreate(cardId, stream.stream()
                .map(EventEnvelope::data)
                .filter(event -> event instanceof VirtualCreditCardEvent)
                .map(event -> (VirtualCreditCardEvent) event)
                .toList()
        );
    }
}

record EventMetadata(
        String streamId,
        String eventType,
        UUID eventId,
        int version,
        Instant occurredAt
) {
    public static <T> EventMetadata from(Class<T> eventType, String streamId, int version) {
        return new EventMetadata(streamId, eventType.getTypeName(), UUID.randomUUID(), version, Instant.now());
    }
}

record EventEnvelope(
        Object data,
        EventMetadata metadata
) {

    public static <T> EventEnvelope from(String streamId, Object event, int version) {
        return new EventEnvelope(event, EventMetadata.from(event.getClass(), streamId, version));
    }
}

class OwnershipDatabase {

    private final Map<CardId, Ownership> ownerships = new ConcurrentHashMap<>();

    void save(CardId cardId, Ownership ownership) {
        ownerships.put(cardId, ownership);
    }

    Ownership find(CardId cardId) {
        return ownerships.getOrDefault(cardId, Ownership.empty());
    }

}
