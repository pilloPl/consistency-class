package io.pillopl.consistency;

import org.javamoney.moneta.Money;

class AddLimitService {

    private final VirtualCreditCardDatabase virtualCreditCardDatabase;

    AddLimitService(VirtualCreditCardDatabase virtualCreditCardDatabase) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
    }

    Result addLimit(CardId cardId, Money limit) {
        VirtualCreditCard card = virtualCreditCardDatabase.find(cardId);
        int expectedVersion = card.version();

        Result result = card.assignLimit(limit);

        return result == Result.Success ?
            virtualCreditCardDatabase.save(card, expectedVersion)
            : result;
    }

}
