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
        return null;
    }

    VirtualCreditCard(CardId cardId) {
        this.cardId = cardId;
    }

    Result assignLimit(Money limit) {
        this.limit = Limit.initial(limit);
        return Success;
    }

    Result withdraw(Money amount) {
        if (availableLimit().isLessThan(amount)) {
            return Result.Failure;
        }
        if (this.withdrawalsInCycle >= 45) {
            return Result.Failure;
        }
        this.limit = limit.use(amount);
        this.withdrawalsInCycle++;
        return Success;
    }

    Result repay(Money amount) {
        this.limit = limit.topUp(amount);
        return Success;
    }

    Result closeCycle() {
        this.withdrawalsInCycle = 0;
        return Success;
    }

    Money availableLimit() {
        return limit.available();
    }

    CardId id() {
        return cardId;
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
