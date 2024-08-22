package io.pillopl.consistency;

import org.javamoney.moneta.Money;

class WithdrawService {

    private final BillingCycleDatabase billingCycleDatabase;
    private final OwnershipDatabase ownershipDatabase;

    WithdrawService(
        BillingCycleDatabase billingCycleDatabase,
        OwnershipDatabase ownershipDatabase
    ) {
        this.billingCycleDatabase = billingCycleDatabase;
        this.ownershipDatabase = ownershipDatabase;
    }

    Result withdraw(BillingCycleId cycleId, Money amount, OwnerId ownerId) {
        if (!ownershipDatabase.find(cycleId.cardId()).hasAccess(ownerId)) {
            return Result.Failure;
        }

        BillingCycle billingCycle = billingCycleDatabase.find(cycleId);
        int expectedVersion = billingCycle.version();

        Result result = billingCycle.withdraw(amount);

        return result == Result.Success ?
            billingCycleDatabase.save(billingCycle, expectedVersion)
            : Result.Failure;
    }
}


