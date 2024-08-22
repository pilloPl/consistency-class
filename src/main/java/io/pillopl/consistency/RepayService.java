package io.pillopl.consistency;

import org.javamoney.moneta.Money;

class RepayService {

    private final VirtualCreditCardDatabase virtualCreditCardDatabase;

    RepayService(VirtualCreditCardDatabase virtualCreditCardDatabase) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
    }

    Result repay(CardId cardId, Money amount) {
        VirtualCreditCard card = virtualCreditCardDatabase.find(cardId);
        int expectedVersion = card.version();

        Result result = card.repay(amount);

        return result == Result.Success ?
            virtualCreditCardDatabase.save(card, expectedVersion)
            : result;

    }
}


