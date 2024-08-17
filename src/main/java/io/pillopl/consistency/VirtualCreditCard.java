package io.pillopl.consistency;

import org.javamoney.moneta.Money;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.pillopl.consistency.Result.Failure;
import static io.pillopl.consistency.Result.Success;


class VirtualCreditCard {

    private final CardId cardId = CardId.random();
    private Limit limit;
    private int withdrawalsInCycle;
    private Ownership ownership = Ownership.empty();

    static VirtualCreditCard withLimit(Money limit) {
        VirtualCreditCard card = new VirtualCreditCard();
        card.assignLimit(limit);
        return card;
    }

    static VirtualCreditCard withLimitAndOwner(Money limit, OwnerId ownerId) {
        VirtualCreditCard card = withLimit(limit);
        card.addAccess(ownerId);
        return card;
    }

    Result assignLimit(Money limit) {
        this.limit = Limit.initial(limit);
        return Success;
    }

    Result withdraw(Money amount, OwnerId ownerId) {
        if (availableLimit().isLessThan(amount)) {
            return Result.Failure;
        }
        if (this.withdrawalsInCycle >= 45) {
            return Result.Failure;
        }
        if (!ownership.hasAccess(ownerId)) {
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

    Result addAccess(OwnerId owner) {
        if (ownership.size() >= 2) {
            return Failure;
        }
        this.ownership = ownership.addAccess(owner);
        return Success;
    }

    Result revokeAccess(OwnerId owner) {
        this.ownership = ownership.revoke(owner);
        return Success;
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