package io.pillopl.consistency;

import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static io.pillopl.consistency.Result.Success;
import static io.pillopl.consistency.VirtualCreditCardEvent.*;


class VirtualCreditCard implements Versioned {
    record BillingCycle(BillingCycleId id, boolean isOpened) {
        static BillingCycle NotExisting = new BillingCycle(null, false);
    }

    private CardId cardId;
    private CurrencyUnit currency;
    private BillingCycle currentBillingCycle;
    private Limit limit;
    private Money debt;
    private boolean isActive;
    private final List<VirtualCreditCardEvent> pendingEvents = new ArrayList<>();
    private int version;

    static VirtualCreditCard withLimit(Money limit) {
        var cartId = CardId.random();
        List<VirtualCreditCardEvent> events = List.of(
            new CardCreated(cartId, limit.getCurrency(), Instant.now()),
            new LimitAssigned(cartId, limit, Instant.now())
        );
        return recreate(events);
    }

    static VirtualCreditCard recreate(List<VirtualCreditCardEvent> stream) {
        return stream.stream()
            .reduce(
                new VirtualCreditCard(),
                VirtualCreditCard::evolve,
                (card1, card2) -> card1
            );
    }

    private VirtualCreditCard() {

    }

    static VirtualCreditCard create(CardId cardId, CurrencyUnit currency) {
        var cart = new VirtualCreditCard();
        cart.enqueue(new CardCreated(cardId, currency, Instant.now()));
        return cart;
    }

    private VirtualCreditCard created(CardCreated event) {
        this.cardId = event.cartId();
        this.currentBillingCycle = BillingCycle.NotExisting;
        this.isActive = true;
        this.currency = event.currency();
        this.debt = Money.zero(event.currency());
        return this;
    }

    Result assignLimit(Money limit) {
        return success(new LimitAssigned(cardId, limit, Instant.now()));
    }

    private VirtualCreditCard limitAssigned(LimitAssigned event) {
        // Simulating that the debt is transferred to the next cycle
        this.limit = new Limit(event.amount(), debt);
        return this;
    }

    Result openNextCycle() {
        if(currentBillingCycle.isOpened()){
            return Result.Failure;
        }
        if(!isActive) {
            return Result.Failure;
        }

        var nextCycleId = currentBillingCycle != BillingCycle.NotExisting ?
            currentBillingCycle.id().next()
            : BillingCycleId.fromNow(cardId);

        return success(
            new CycleOpened(
                nextCycleId,
                cardId,
                nextCycleId.from(),
                nextCycleId.to(),
                limit,
                Instant.now()
            )
        );
    }

    private VirtualCreditCard cycleOpened(CycleOpened cycleOpened) {
        currentBillingCycle = new BillingCycle(cycleOpened.cycleId(), true);
        return this;
    }

    // No result, as we just need to accept it
    void recordCycleClosure(
        BillingCycleId cycleId,
        Limit closingLimit,
        Instant closedAt
    ) {
        if(!currentBillingCycle.id().equals(cycleId))
            return;
        if(!currentBillingCycle.isOpened())
            return;

        var closingDebt = closingLimit.used();

        enqueue(
            new CycleClosed(
                cycleId,
                cardId,
                closingDebt,
                closedAt
            )
        );

        if(!closingDebt.isZero()){
            enqueue(
                new CardDeactivated(
                    cardId,
                    Instant.now()
                )
            );
        }
    }

    private VirtualCreditCard cycleClosed(CycleClosed cycleClosed) {
        currentBillingCycle = new BillingCycle(cycleClosed.cycleId(), false);
        debt = cycleClosed.debt();
        return this;
    }

    private VirtualCreditCard deactivated(CardDeactivated deactivated) {
        isActive = false;
        return this;
    }

    private static VirtualCreditCard evolve(VirtualCreditCard card, VirtualCreditCardEvent event) {
        card.version++;
        return switch (event) {
            case CardCreated e -> card.created(e);
            case LimitAssigned e -> card.limitAssigned(e);
            case CycleOpened e -> card.cycleOpened(e);
            case CycleClosed e -> card.cycleClosed(e);
            case CardDeactivated e -> card.deactivated(e);
        };
    }

    CardId id() {
        return cardId;
    }

    boolean isActive() {
        return isActive;
    }

    public Limit getLimit() {
        return limit;
    }
    public CurrencyUnit getCurrency() {
        return currency;
    }
    public BillingCycle getCurrentBillingCycle() {
        return currentBillingCycle;
    }

    List<VirtualCreditCardEvent> dequeuePendingEvents() {
        var result = pendingEvents.stream().toList();
        pendingEvents.clear();
        return result;
    }

    Result success(VirtualCreditCardEvent event) {
        enqueue(event);
        return Success;
    }

    void enqueue(VirtualCreditCardEvent event) {
        evolve(this, event);
        pendingEvents.add(event);
    }

    @Override
    public int version() {
        return version;
    }
}

enum Result {
    Success, Failure
}

record CardId(UUID contractId) {
    static CardId random() {
        return new CardId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "Card:" + contractId;
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

sealed interface VirtualCreditCardEvent {
    record CardCreated(
        CardId cartId,
        CurrencyUnit currency,
        Instant createdAt
    ) implements VirtualCreditCardEvent {
    }

    record LimitAssigned(
        CardId cartId,
        Money amount,
        Instant assignedAt
    ) implements VirtualCreditCardEvent {
    }

    record CardDeactivated(
        CardId cartId,
        Instant deactivatedAt
    ) implements VirtualCreditCardEvent {
    }

    record CycleOpened(
        BillingCycleId cycleId,
        CardId cartId,
        LocalDate from,
        LocalDate to,
        Limit startingLimit,
        Instant openedAt
    ) implements VirtualCreditCardEvent {
    }

    record CycleClosed(
        BillingCycleId cycleId,
        CardId cartId,
        Money debt,
        Instant closedAt
    ) implements VirtualCreditCardEvent {
    }
}
