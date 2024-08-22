package io.pillopl.consistency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyEntity {
    private final String id;

    DummyEntity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

class DummyVersionedEntity implements VersionedWithAutoIncrement {
    private final String id;
    private int version;

    DummyVersionedEntity(String id, int version) {
        this.id = id;
        this.version = version;
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public void setVersion(int version) {
        this.version = version;
    }

    public String getId() {
        return id;
    }
}

public class DatabaseTest {
    @Test
    void findForNonExistingRecordReturnsEmptyOptional() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();

        // when
        var result = collection.find(id);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void findForExistingRecordReturnsEntity() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyVersionedEntity(id, 0);
        collection.save(id, entity);

        // when
        var result = collection.find(id);

        // then
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    void saveBumpsVersionForVersionedEntity() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyVersionedEntity(id, 0);

        // when
        var result = collection.save(id, entity);

        // then
        assertEquals(Result.Success, result);
        assertEquals(1, entity.version());
    }

    @Test
    void saveStoresNonVersionedEntity() {
        // given
        var collection = Database.collection(DummyEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyEntity(id);

        // when
        var result = collection.save(id, entity);

        // then
        assertEquals(Result.Success, result);
        assertEquals(id, entity.getId());
    }

    @Test
    void saveBumpsVersionInDatabaseForVersionedEntity() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyVersionedEntity(id, 0);

        // when
        collection.save(id, entity);

        // then
        var entityFromDb = collection.find(id);
        assertTrue(entityFromDb.isPresent());
        assertEquals(1, entityFromDb.get().version());
    }

    @Test
    void saveVersionedEntityCanBeRunMultipleTimesSequentially() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var currentVersion = 0;
        var entity = new DummyVersionedEntity(id, currentVersion);

        // when
        do {
            var result = collection.save(id, entity);

            // then
            assertEquals(Result.Success, result);
            assertEquals(++currentVersion, entity.version());
        } while(currentVersion < 5);
    }

    @Test
    void saveEntityCanBeRunMultipleTimesSequentiallyWithBumpedVersion() {
        // given
        var collection = Database.collection(DummyEntity.class);
        var id = UUID.randomUUID().toString();
        var currentVersion = 0;
        var entity = new DummyEntity(id);

        // when
        do {
            var result = collection.save(id, entity, currentVersion++);

            // then
            assertEquals(Result.Success, result);
        } while(currentVersion < 5);
    }

    @Test
    void saveVersionedEntityCanBeRunMultipleTimesSequentiallyWithBumpedVersion() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var currentVersion = 0;
        var entity = new DummyVersionedEntity(id, currentVersion);

        // when
        do {
            var result = collection.save(id, entity, currentVersion);

            // then
            assertEquals(Result.Success, result);
            assertEquals(++currentVersion, entity.version());
        } while(currentVersion < 5);
    }

    @Test
    void saveVersionedEntitySucceedsWhenExplicitExpectedVersionMatches() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyVersionedEntity(id, 0);

        collection.save(id, entity, 0);

        // when
        var result = collection.save(id, entity, 1);

        // then
        assertEquals(Result.Success, result);
        assertEquals(2, entity.version());
    }

    @Test
    void saveEntitySucceedsWhenExplicitExpectedVersionMatches() {
        // given
        var collection = Database.collection(DummyEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyEntity(id);

        collection.save(id, entity, 0);

        // when
        var result = collection.save(id, entity, 1);

        // then
        assertEquals(Result.Success, result);
    }

    @Test
    void saveFailsWhenExplicitExpectedVersionMatches() {
        // given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();
        var entity = new DummyVersionedEntity(id, 0);
        var oldEntity = new DummyVersionedEntity(id, 0);

        collection.save(id, entity);

        // when
        var result = collection.save(id, oldEntity);

        // then
        assertEquals(Result.Failure, result);
        assertEquals(1, entity.version());
    }

    @Test
    void cantUpdateConcurrently() throws InterruptedException {
        //given
        var collection = Database.collection(DummyVersionedEntity.class);
        var id = UUID.randomUUID().toString();

        List<Result> results = new ArrayList<>();
        //when
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i<10; i++) {
            executor.execute(()-> {
                try {
                    var entity = collection.find(id).orElse(new DummyVersionedEntity(id, 0));
                    results.add(collection.save(id, entity));
                } catch (Exception e) {
                    // ignore
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        //then
        assertTrue(results.contains(Result.Failure));
        assertTrue(collection.find(id).orElse(new DummyVersionedEntity(id, 0)).version() < 10);
    }
}
