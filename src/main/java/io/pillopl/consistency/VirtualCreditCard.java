package io.pillopl.consistency;

import org.javamoney.moneta.Money;

import java.time.Instant;
import java.util.*;

import static io.pillopl.consistency.Result.Success;


class VirtualCreditCard {

    private final CardId cardId;
    private Limit limit;
    private int withdrawalsInCycle;
    private List<Event> pendingEvents = new ArrayList<>();

    static VirtualCreditCard withLimit(Money limit) {
        VirtualCreditCard card = new VirtualCreditCard(CardId.random());
        card.assignLimit(limit);
        return card;
    }

    static VirtualCreditCard recreate(CardId cardId, List<Event> stream) {
        return stream.stream()
                .reduce(new VirtualCreditCard(cardId), (card, event) -> {
                    switch (event) {
                        case LimitAssigned e -> card.limitAssigned(e);
                        case CardWithdrawn e -> card.cardWithdrawn(e);
                        case CardRepaid e -> card.cardRepaid(e);
                        case CycleClosed e -> card.billingCycleClosed(e);
                        default -> {}
                    }
                    return card;
                }, (card1, card2) -> card1);
    }

    VirtualCreditCard(CardId cardId) {
        this.cardId = cardId;
    }

    Result assignLimit(Money limit) {
        limitAssigned(new LimitAssigned(UUID.randomUUID(), cardId, Instant.now(), limit));
        return Success;
    }

    private VirtualCreditCard limitAssigned(LimitAssigned event) {
        this.limit = Limit.initial(event.amount());
        pendingEvents.add(event);
        return this;
    }

    Result withdraw(Money amount) {
        if (availableLimit().isLessThan(amount)) {
            return Result.Failure;
        }
        if (this.withdrawalsInCycle >= 45) {
            return Result.Failure;
        }
        cardWithdrawn(new CardWithdrawn(UUID.randomUUID(), cardId, Instant.now(), amount));
        return Success;
    }

    private VirtualCreditCard cardWithdrawn(CardWithdrawn event) {
        this.limit = limit.use(event.amount());
        this.withdrawalsInCycle++;
        pendingEvents.add(event);
        return this;
    }

    Result repay(Money amount) {
        cardRepaid(new CardRepaid(UUID.randomUUID(), cardId, Instant.now(), amount));
        return Success;
    }

    private VirtualCreditCard cardRepaid(CardRepaid event) {
        this.limit = limit.topUp(event.amount());
        pendingEvents.add(event);
        return this;
    }

    Result closeCycle() {
        billingCycleClosed(new CycleClosed(UUID.randomUUID(), cardId, Instant.now()));
        return Success;
    }

    private VirtualCreditCard billingCycleClosed(CycleClosed event) {
        this.withdrawalsInCycle = 0;
        pendingEvents.add(event);
        return this;
    }

    Money availableLimit() {
        return limit.available();
    }

    CardId id() {
        return cardId;
    }

    List<Event> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    void flush() {
        pendingEvents.clear();
    }
}


enum Result {
    Success, Failure
}


record CardId(UUID id) {
    static CardId random() {
        return new CardId(UUID.randomUUID());
    }
}

record Limit(Money max, Money used) {

    static Limit initial(Money max) {
        return new Limit(max, Money.zero(max.getCurrency()));
    }

    Limit use(Money amount) {
        return new Limit(max, used.add(amount));
    }

    Limit topUp(Money amount) {
        Money used = this.used.subtract(amount);
        return new Limit(max, used.isPositiveOrZero() ? used : Money.zero(max.getCurrency()));
    }

    public Money available() {
        return max.subtract(used);
    }
}

record OwnerId(UUID id) {
    static OwnerId random() {
        return new OwnerId(UUID.randomUUID());
    }
}

record Ownership(Set<OwnerId> owners) {

    static Ownership of(OwnerId... owners) {
        return new Ownership(Set.of(owners));
    }

    public static Ownership empty() {
        return new Ownership(Set.of());
    }

    boolean hasAccess(OwnerId ownerId) {
        return owners.contains(ownerId);
    }

    Ownership addAccess(OwnerId ownerId) {
        Set<OwnerId> newOwners = new HashSet<>(owners);
        newOwners.add(ownerId);
        return new Ownership(newOwners);
    }

    Ownership revoke(OwnerId ownerId) {
        Set<OwnerId> newOwners = new HashSet<>(owners);
        newOwners.remove(ownerId);
        return new Ownership(newOwners);
    }

    int size() {
        return owners.size();
    }
}

interface Event {
    CardId aggregateId();

    UUID id();

    Instant occuredAt();
}

record CardCreated(UUID id, CardId aggregateId, Instant occuredAt) implements Event {
}

record CardRepaid(UUID id, CardId aggregateId, Instant occuredAt, Money amount) implements Event {
}

record LimitAssigned(UUID id, CardId aggregateId, Instant occuredAt, Money amount) implements Event {
}

record CardWithdrawn(UUID id, CardId aggregateId, Instant occuredAt, Money amount) implements Event {
}

record CycleClosed(UUID id, CardId aggregateId, Instant occuredAt) implements Event {
}
