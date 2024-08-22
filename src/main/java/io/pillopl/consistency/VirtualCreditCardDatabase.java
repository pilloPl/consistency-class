package io.pillopl.consistency;

class VirtualCreditCardDatabase {
    private final EventStore eventStore;

    VirtualCreditCardDatabase(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    Result save(VirtualCreditCard card, int expectedVersion) {
        var streamId = card.id().toString();

        return eventStore.appendToStream(
            streamId,
            card.dequeuePendingEvents(),
            expectedVersion
        );
    }

    VirtualCreditCard find(CardId cardId) {
        var streamId = cardId.toString();

        var events = eventStore.readEvents(VirtualCreditCardEvent.class, streamId);

        return VirtualCreditCard.recreate(events);
    }
}

class BillingCycleDatabase {
    private final EventStore eventStore;

    BillingCycleDatabase(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    Result save(BillingCycle cycle, int expectedVersion) {
        var streamId = cycle.id().toString();

        return eventStore.appendToStream(
            streamId,
            cycle.dequeuePendingEvents(),
            expectedVersion
        );
    }

    BillingCycle find(BillingCycleId cycleId) {
        var streamId = cycleId.toString();

        var events = eventStore.readEvents(BillingCycleEvent.class, streamId);

        return BillingCycle.recreate(events);
    }
}

class OwnershipDatabase {
    private final DatabaseCollection<Ownership> ownerships =
        Database.collection(Ownership.class);

    Result save(CardId cardId, Ownership ownership, int expectedVersion) {
        return ownerships.save(cardId.toString(), ownership, expectedVersion);
    }

    Ownership find(CardId cardId) {
        return ownerships.find(cardId.toString()).orElse(Ownership.empty());
    }
}
