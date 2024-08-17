package io.pillopl.consistency;

class CloseCycleService {

    private final VirtualCreditCardDatabase virtualCreditCardDatabase;

    CloseCycleService(VirtualCreditCardDatabase virtualCreditCardDatabase) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
    }

    Result close(CardId cardId) {
        VirtualCreditCard card = virtualCreditCardDatabase.find(cardId);
        Result result = card.closeCycle();
        virtualCreditCardDatabase.save(card);
        return result;

    }
}


