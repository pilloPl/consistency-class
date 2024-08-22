package io.pillopl.consistency;

import org.javamoney.moneta.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.pillopl.consistency.BillingCycleEvent.*;
import static io.pillopl.consistency.Result.Success;


sealed interface BillingCycleEvent {
    record CycleOpened(
        BillingCycleId cycleId,
        CardId cartId,
        LocalDate from,
        LocalDate to,
        Limit startingLimit,
        Instant openedAt
    ) implements BillingCycleEvent {
    }

    record CardRepaid(
        BillingCycleId cycleId,
        CardId cartId,
        Money amount,
        Instant repaidAt
    ) implements BillingCycleEvent {
    }

    record CardWithdrawn(
        BillingCycleId cycleId,
        CardId cartId,
        Money amount,
        Instant withdrawnAt
    ) implements BillingCycleEvent {
    }

    record CycleClosed(
        BillingCycleId cycleId,
        CardId cartId,
        Limit closingLimit,
        int withdrawalsInCycle,
        Instant closedAt
    ) implements BillingCycleEvent {
    }
}

class BillingCycle implements Versioned {
    enum Status {
        Opened,
        Closed,
    }

    private Status status;
    private int withdrawalsInCycle;
    private Limit limit;
    private final List<BillingCycleEvent> pendingEvents = new ArrayList<>();
    private int version;
    private BillingCycleId id;
    private CardId cardId;


    static BillingCycle withLimit(Money limit) {
        var cartId = CardId.random();
        var cycleId = BillingCycleId.fromNow(cartId);

        List<BillingCycleEvent> events = List.of(
            new CycleOpened(cycleId, cartId, cycleId.from(), cycleId.to(), Limit.initial(limit), Instant.now())
        );
        return recreate(events);
    }

    static BillingCycle recreate(List<BillingCycleEvent> stream) {
        return stream.stream()
            .reduce(
                new BillingCycle(),
                BillingCycle::evolve,
                (cycle1, cycle2) -> cycle1
            );
    }

    void onCycleOpened(CardId cardId, VirtualCreditCardEvent.CycleOpened cycleOpened) {
        enqueue(
            new CycleOpened(
                cycleOpened.cycleId(),
                cycleOpened.cartId(),
                cycleOpened.from(),
                cycleOpened.to(),
                cycleOpened.startingLimit(),
                cycleOpened.openedAt()
            )
        );
    }

    static BillingCycle openCycle(BillingCycleId id, CardId cardId, LocalDate from, LocalDate to, Limit startingLimit) {
        var cycle = new BillingCycle();

        cycle.success(new CycleOpened(id, cardId, from, to, startingLimit, Instant.now()));

        return cycle;
    }

    private BillingCycle opened(CycleOpened event) {
        id = event.cycleId();
        cardId = event.cartId();
        status = Status.Opened;
        limit = event.startingLimit();
        return this;
    }

    Result closeCycle() {
        if (status == Status.Closed) {
            return Result.Failure;
        }
        return success(new CycleClosed(id, cardId, limit, withdrawalsInCycle, Instant.now()));
    }

    private BillingCycle closed(CycleClosed event) {
        status = Status.Closed;
        return this;
    }

    Result withdraw(Money amount) {
        if (status != Status.Opened) {
            return Result.Failure;
        }
        if (availableLimit().isLessThan(amount)) {
            return Result.Failure;
        }
        if (this.withdrawalsInCycle >= 45) {
            return Result.Failure;
        }
        return success(new CardWithdrawn(id, cardId, amount, Instant.now()));
    }

    private BillingCycle cardWithdrawn(CardWithdrawn event) {
        this.limit = limit.use(event.amount());
        this.withdrawalsInCycle++;
        return this;
    }

    Result repay(Money amount) {
        if (status == Status.Closed) {
            // Question: How to handle repaying cycle
            // that was closed without settling all withdrawals?
            return Result.Failure;
        }
        return success(new CardRepaid(id, cardId, amount, Instant.now()));
    }

    private BillingCycle cardRepaid(CardRepaid event) {
        this.limit = limit.topUp(event.amount());
        return this;
    }

    private static BillingCycle evolve(BillingCycle card, BillingCycleEvent event) {
        card.version++;
        return switch (event) {
            case CycleOpened e -> card.opened(e);
            case CardWithdrawn e -> card.cardWithdrawn(e);
            case CardRepaid e -> card.cardRepaid(e);
            case CycleClosed e -> card.closed(e);
        };
    }

    Money availableLimit() {
        return limit.available();
    }

    BillingCycleId id() {
        return id;
    }

    List<BillingCycleEvent> dequeuePendingEvents() {
        var result = pendingEvents.stream().toList();
        pendingEvents.clear();
        return result;
    }

    Result success(BillingCycleEvent event) {
        enqueue(event);
        return Success;
    }

    void enqueue(BillingCycleEvent event) {
        evolve(this, event);
        pendingEvents.add(event);
    }

    @Override
    public int version() {
        return version;
    }
}

record BillingCycleId(CardId cardId, LocalDate from, LocalDate to) {
    static final int cycleLength = 30;

    static BillingCycleId fromNow(CardId cardId) {
        var from = LocalDate.now();
        return new BillingCycleId(cardId, from, from.plusDays(cycleLength));
    }

    BillingCycleId next() {
        var from = this.to.plusDays(1);
        return new BillingCycleId(cardId, from, from.plusDays(cycleLength));
    }

    @Override
    public String toString() {
        return "BillingCycle:" + cardId.contractId() + ":" + from + ":" + to;
    }
}
