package io.pillopl.consistency;

class VirtualCreditCardDatabase {
    private final EventStore eventStore = new EventStore();

    Result save(VirtualCreditCard card, int expectedVersion) {
        var streamId = card.id().id().toString();

        return eventStore.appendToStream(
            streamId,
            card.dequeuePendingEvents(),
            expectedVersion
        );
    }

    VirtualCreditCard find(CardId cardId) {
        var streamId = cardId.id().toString();

        var events = eventStore.readEvents(VirtualCreditCardEvent.class, streamId);

        return VirtualCreditCard.recreate(events);
    }
}

class OwnershipDatabase {
    private final DatabaseCollection<Ownership> ownerships =
        Database.collection(Ownership.class);

    Result save(CardId cardId, Ownership ownership, int expectedVersion) {
        return ownerships.save(cardId.id().toString(), ownership, expectedVersion);
    }

    Ownership find(CardId cardId) {
        return ownerships.find(cardId.id().toString()).orElse(Ownership.empty());
    }
}
