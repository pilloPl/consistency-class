package io.pillopl.consistency;

import java.util.concurrent.atomic.AtomicInteger;

class VirtualCreditCardDatabase {
    private final DatabaseCollection<EventStream> cards =
        Database.collection(EventStream.class);

    Result save(VirtualCreditCard card) {
        var cartId = card.id().id().toString();

        var stream = cards.find(cartId).orElseGet(() -> EventStream.empty(cartId));

        var version = new AtomicInteger(stream.events().size());

        var newEvents = card.dequeuePendingEvents().stream().map(e ->
            EventEnvelope.from(cartId, e, version.incrementAndGet())
        ).toList();


        return cards.save(cartId, stream.append(newEvents));
    }

    VirtualCreditCard find(CardId cardId) {
        var cartId = cardId.id().toString();

        var stream = cards.find(cartId)
            .orElseGet(() -> EventStream.empty(cartId));

        return VirtualCreditCard.recreate(stream.events().stream()
            .map(EventEnvelope::data)
            .filter(event -> event instanceof VirtualCreditCardEvent)
            .map(event -> (VirtualCreditCardEvent) event)
            .toList()
        );
    }
}


class OwnershipDatabase {

    private final DatabaseCollection<Ownership> ownerships =
        Database.collection(Ownership.class);

    Result save(CardId cardId, Ownership ownership) {
        return ownerships.save(cardId.id().toString(), ownership);
    }

    Ownership find(CardId cardId) {
        return ownerships.find(cardId.id().toString()).orElse(Ownership.empty());
    }

}
