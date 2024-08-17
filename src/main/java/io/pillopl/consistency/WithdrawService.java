package io.pillopl.consistency;

import org.javamoney.moneta.Money;

class WithdrawService {

    private final VirtualCreditCardDatabase virtualCreditCardDatabase;
    private final OwnershipDatabase ownershipDatabase;

    WithdrawService(VirtualCreditCardDatabase virtualCreditCardDatabase, OwnershipDatabase ownershipDatabase) {
        this.virtualCreditCardDatabase = virtualCreditCardDatabase;
        this.ownershipDatabase = ownershipDatabase;
    }

    Result withdraw(CardId cardId, Money amount, OwnerId ownerId) {
        if (ownershipDatabase.find(cardId).hasAccess(ownerId)) {
            VirtualCreditCard card = virtualCreditCardDatabase.find(cardId);
            Result result = card.withdraw(amount);
            virtualCreditCardDatabase.save(card);
            return result;
        }
        return Result.Failure;
    }
}


