package io.pillopl.consistency;

class CloseCycleService {

    private final VirtualCreditCardDatabase virtualCreditCardDatabase;

    CloseCycleService(VirtualCreditCardDatabase virtualCreditCardDatabase) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
    }

    Result close(CardId cardId) {
        VirtualCreditCard card = virtualCreditCardDatabase.find(cardId);
        int expectedVersion = card.version();

        Result result = card.closeCycle();

        return result == Result.Success ?
            virtualCreditCardDatabase.save(card, expectedVersion)
            : result;
    }
}


