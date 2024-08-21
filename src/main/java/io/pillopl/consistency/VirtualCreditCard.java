package io.pillopl.consistency;

import org.javamoney.moneta.Money;

import java.time.Instant;
import java.util.*;

import static io.pillopl.consistency.Result.Success;
import static io.pillopl.consistency.VirtualCreditCardEvent.*;

class VirtualCreditCard {

    private CardId cardId;
    private Limit limit;
    private int withdrawalsInCycle;
    private final List<VirtualCreditCardEvent> pendingEvents = new ArrayList<>();

    static VirtualCreditCard withLimit(Money limit) {
        var cartId = CardId.random();
        List<VirtualCreditCardEvent> events = List.of(new LimitAssigned(cartId, Instant.now(), limit));
        return recreate(CardId.random(), events);
    }

    static VirtualCreditCard recreate(CardId cardId, List<VirtualCreditCardEvent> stream) {
        return stream.stream()
                .reduce(
                        new VirtualCreditCard(),
                        VirtualCreditCard::evolve,
                        (card1, card2) -> card1
                );
    }

    private VirtualCreditCard() {
        
    }

    static VirtualCreditCard create(CardId cardId) {
        var cart = new VirtualCreditCard();
        var event = new CardCreated(cardId, Instant.now());

        cart.created(event);
        cart.pendingEvents.add(event);

        return cart;
    }

    private VirtualCreditCard created(CardCreated event) {
        this.cardId = event.cartId();
        return this;
    }

    Result assignLimit(Money limit) {
        var event = new LimitAssigned(cardId, Instant.now(), limit);
        limitAssigned(event);
        pendingEvents.add(event);
        return Success;
    }

    private VirtualCreditCard limitAssigned(LimitAssigned event) {
        this.limit = Limit.initial(event.amount());
        return this;
    }

    Result withdraw(Money amount) {
        if (availableLimit().isLessThan(amount)) {
            return Result.Failure;
        }
        if (this.withdrawalsInCycle >= 45) {
            return Result.Failure;
        }
        var event = new CardWithdrawn(cardId, Instant.now(), amount);
        cardWithdrawn(event);
        pendingEvents.add(event);
        return Success;
    }

    private VirtualCreditCard cardWithdrawn(CardWithdrawn event) {
        this.limit = limit.use(event.amount());
        this.withdrawalsInCycle++;
        return this;
    }

    Result repay(Money amount) {
        var event = new CardRepaid(cardId, Instant.now(), amount);
        cardRepaid(event);
        pendingEvents.add(event);
        return Success;
    }

    private VirtualCreditCard cardRepaid(CardRepaid event) {
        this.limit = limit.topUp(event.amount());
        return this;
    }

    Result closeCycle() {
        var event = new CycleClosed(cardId, Instant.now());
        billingCycleClosed(event);
        pendingEvents.add(event);
        return Success;
    }

    private VirtualCreditCard billingCycleClosed(CycleClosed event) {
        this.withdrawalsInCycle = 0;
        return this;
    }

    private static VirtualCreditCard evolve(VirtualCreditCard card, VirtualCreditCardEvent event) {
        return switch (event) {
            case CardCreated e -> card.created(e);
            case LimitAssigned e -> card.limitAssigned(e);
            case CardWithdrawn e -> card.cardWithdrawn(e);
            case CardRepaid e -> card.cardRepaid(e);
            case CycleClosed e -> card.billingCycleClosed(e);
        };
    }

    Money availableLimit() {
        return limit.available();
    }

    CardId id() {
        return cardId;
    }

    List<VirtualCreditCardEvent> pendingEvents() {
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

sealed interface VirtualCreditCardEvent {
    record CardCreated(CardId cartId, Instant createdAt) implements VirtualCreditCardEvent {
    }

    record CardRepaid(CardId cartId, Instant repaidAt, Money amount) implements VirtualCreditCardEvent {
    }

    record LimitAssigned(CardId cartId, Instant assignedAt, Money amount) implements VirtualCreditCardEvent {
    }

    record CardWithdrawn(CardId cartId, Instant withdrawnAt, Money amount) implements VirtualCreditCardEvent {
    }

    record CycleClosed(CardId cartId, Instant closedAt) implements VirtualCreditCardEvent {
    }
}
