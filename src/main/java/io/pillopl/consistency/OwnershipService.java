package io.pillopl.consistency;

class OwnershipService {

    private final OwnershipDatabase ownershipDatabase;

    OwnershipService(OwnershipDatabase ownershipDatabase) {
        this.ownershipDatabase = ownershipDatabase;
    }

    Result addAccess(CardId cardId, OwnerId ownerId) {
        Ownership ownership = ownershipDatabase.find(cardId);
        if (ownership.size() >= 2) {
            return Result.Failure;
        }
        ownership = ownership.addAccess(ownerId);
        ownershipDatabase.save(cardId, ownership);
        return Result.Success;
    }

    Result revokeAccess(CardId cardId, OwnerId ownerId) {
        Ownership ownership = ownershipDatabase.find(cardId);
        ownership = ownership.revoke(ownerId);
        ownershipDatabase.save(cardId, ownership);
        return Result.Success;
    }
}
