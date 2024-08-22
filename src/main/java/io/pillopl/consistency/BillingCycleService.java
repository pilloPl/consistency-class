package io.pillopl.consistency;

import org.springframework.retry.support.RetryTemplate;

import java.util.Optional;

class BillingCycleService {
    private final VirtualCreditCardDatabase virtualCreditCardDatabase;
    private final BillingCycleDatabase billingCycleDatabase;

    BillingCycleService(
        VirtualCreditCardDatabase virtualCreditCardDatabase,
        BillingCycleDatabase billingCycleDatabase
    ) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
        this.billingCycleDatabase = billingCycleDatabase;
    }

    Optional<BillingCycleId> getCurrentlyOpenedBillingCycleId(CardId cardId) {
        var virtualCreditCard = virtualCreditCardDatabase.find(cardId);

        var currentBillingCycle = virtualCreditCard.getCurrentBillingCycle();

        return currentBillingCycle.isOpened()
            ? Optional.of(currentBillingCycle.id())
            : Optional.empty();
    }

    Result openNextCycle(CardId cardId) {
        VirtualCreditCard card = virtualCreditCardDatabase.find(cardId);
        int expectedVersion = card.version();

        Result result = card.openNextCycle();

        return result == Result.Success ?
            virtualCreditCardDatabase.save(card, expectedVersion)
            : result;
    }

    Result close(BillingCycleId billingCycleId) {
        BillingCycle billingCycle = billingCycleDatabase.find(billingCycleId);
        int expectedVersion = billingCycle.version();

        Result result = billingCycle.closeCycle();

        return result == Result.Success ?
            billingCycleDatabase.save(billingCycle, expectedVersion)
            : result;
    }
}

// Question: Sync or Async, that is the question!
class BillingCycleEventHandler {
    private final VirtualCreditCardDatabase virtualCreditCardDatabase;
    private final BillingCycleDatabase billingCycleDatabase;
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
        .infiniteRetry()
        .build();

    public BillingCycleEventHandler(
        VirtualCreditCardDatabase virtualCreditCardDatabase,
        BillingCycleDatabase billingCycleDatabase
    ) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
        this.billingCycleDatabase = billingCycleDatabase;
    }

    void handle(Object event) {
        switch (event) {
            case VirtualCreditCardEvent.CycleOpened e ->
                onBillingCycleOpened(e);
            case BillingCycleEvent.CycleClosed e -> onBillingCycleClosed(e);
            default -> {
            }
        }
    }

    void onBillingCycleOpened(VirtualCreditCardEvent.CycleOpened cycleOpened) {
        var cycle = BillingCycle.openCycle(
            cycleOpened.cycleId(),
            cycleOpened.cartId(),
            cycleOpened.from(),
            cycleOpened.to(),
            cycleOpened.startingLimit()
        );

        // we ignore result, as if it was already opened, we can safely ignore it
        billingCycleDatabase.save(cycle, 0);
    }

    // this should be retried on concurrency failure

    void onBillingCycleClosed(BillingCycleEvent.CycleClosed cycleOpened) {
        retryTemplate.execute(context -> {
            VirtualCreditCard card = virtualCreditCardDatabase.find(cycleOpened.cartId());
            int expectedVersion = card.version();

            card.recordCycleClosure(
                cycleOpened.cycleId(),
                cycleOpened.closingLimit(),
                cycleOpened.closedAt()
            );

            var result = virtualCreditCardDatabase.save(card, expectedVersion);

            if (result == Result.Success) {
                context.setExhaustedOnly();
            }

            return result == Result.Success;
        });
    }
}


