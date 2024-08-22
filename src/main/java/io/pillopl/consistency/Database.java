package io.pillopl.consistency;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

interface Versioned {
    int version();
}

interface VersionedWithAutoIncrement extends Versioned {
    void setVersion(int version);
}

record RecordWithVersion(Object record, int version) {
    static RecordWithVersion noRecord = new RecordWithVersion(null, 0);
}

class Database {
    static <T> DatabaseCollection<T> collection(Class<T> entryClass) {
        return new DatabaseCollection<>(entryClass);
    }
}

class DatabaseCollection<T> {
    private final Class<T> entryClass;
    private final Map<String, RecordWithVersion> entries = new ConcurrentHashMap<>();

    DatabaseCollection(Class<T> entryClass) {
        this.entryClass = entryClass;
    }

    Result save(String id, T record) {
        return save(id, record, record instanceof Versioned versioned ?
            versioned.version()
            : entries.getOrDefault(id, RecordWithVersion.noRecord).version()
        );
    }

    Result save(String id, T record, int expectedVersion) {
        var newExpectedVersion = expectedVersion + 1;
        var wasUpdated = new AtomicBoolean(false);

        entries.compute(id, (key, currentValue) -> {
            var currentVersion = currentValue != null ? currentValue.version() : 0;

            if (currentVersion != expectedVersion) {
                // Version conflict, don't update the value
                return currentValue;  // Keep the currentValue in the map
            }

            if (record instanceof VersionedWithAutoIncrement versioned) {
                versioned.setVersion(newExpectedVersion);
            }
            wasUpdated.set(true);

            return new RecordWithVersion(record, newExpectedVersion);
        });

        return wasUpdated.get() ?
            Result.Success
            : Result.Failure;
    }

    Optional<T> find(String id) {
        return entries.containsKey(id) ?
            Optional.of(entryClass.cast(entries.get(id).record()))
            : Optional.empty();
    }

    Result handle(String id, Function<T, T> handle, Supplier<T> getDefault) {
        var entry = entries.getOrDefault(id, RecordWithVersion.noRecord);

        var result = handle.apply(Optional.ofNullable(entry.record())
            .map(entryClass::cast)
            .orElse(getDefault.get()));

        return save(id, result, entry.version());
    }
}
