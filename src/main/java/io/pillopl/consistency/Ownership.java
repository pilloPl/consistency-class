package io.pillopl.consistency;

import java.util.HashSet;
import java.util.Set;

record Ownership(Set<OwnerId> owners, int version) implements Versioned {

    static Ownership of(OwnerId... owners) {
        return new Ownership(Set.of(owners), 0);
    }

    public static Ownership empty() {
        return new Ownership(Set.of(), 0);
    }

    boolean hasAccess(OwnerId ownerId) {
        return owners.contains(ownerId);
    }

    Ownership addAccess(OwnerId ownerId) {
        Set<OwnerId> newOwners = new HashSet<>(owners);
        newOwners.add(ownerId);
        return new Ownership(newOwners, version + 1);
    }

    Ownership revoke(OwnerId ownerId) {
        Set<OwnerId> newOwners = new HashSet<>(owners);
        newOwners.remove(ownerId);
        return new Ownership(newOwners, version + 1);
    }

    int size() {
        return owners.size();
    }
}
