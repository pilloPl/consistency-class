package io.pillopl.consistency;

import org.javamoney.moneta.Money;

class RepayService {
    private final BillingCycleDatabase billingCycleDatabase;

    RepayService(BillingCycleDatabase billingCycleDatabase) {
        this.billingCycleDatabase = billingCycleDatabase;
    }

    Result repay(BillingCycleId cycleId, Money amount) {
        BillingCycle billingCycle = billingCycleDatabase.find(cycleId);
        int expectedVersion = billingCycle.version();

        Result result = billingCycle.repay(amount);

        return result == Result.Success ?
            billingCycleDatabase.save(billingCycle, expectedVersion)
            : result;

    }
}


