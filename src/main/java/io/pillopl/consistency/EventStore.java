package io.pillopl.consistency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class EventStore {
    public final List<Consumer<Object>> subscribers = new ArrayList<>();
    private final DatabaseCollection<EventStream> streams = Database.collection(EventStream.class);

    <T> List<T> readEvents(Class<T> eventType, String streamId) {
        return existingEventStreamOrEmpty(streamId)
            .eventsOfType(eventType);
    }

    <T> Result appendToStream(String streamId, List<T> events, int expectedVersion) {
        var version = new AtomicInteger(expectedVersion);

        var stream = existingEventStreamOrEmpty(streamId);

        var newEvents = events.stream().map(e ->
            EventEnvelope.from(streamId, e, version.incrementAndGet())
        ).toList();

        var result = streams.save(streamId, stream.append(newEvents), expectedVersion);

        if(result == Result.Success) {
            // Note: this typically happens asynchronously
            // to not impact accidentally storing events
            publish(newEvents);
        }

        return result;
    }

    public void subscribe(Consumer<Object> subscriber) {
        subscribers.add(subscriber);
    }

    private void publish(List<EventEnvelope> events){
        for (Consumer<Object> handler : subscribers) {
            for (var event : events) {
                handler.accept(event.data());
            }
        }
    }

    private EventStream existingEventStreamOrEmpty(String streamId) {
        return streams.find(streamId)
            .orElseGet(() -> EventStream.empty(streamId));
    }
}

record EventStream(String id, List<EventEnvelope> events) {
    static EventStream empty(String id) {
        return new EventStream(null, new ArrayList<>());
    }

    EventStream append(List<EventEnvelope> events) {
        return new EventStream(id, Stream.concat(this.events.stream(), events.stream()).toList());
    }

    <Event> List<Event > eventsOfType(Class<Event> eventType) {
        return events().stream()
            .map(EventEnvelope::data)
            .filter(eventType::isInstance)
            .map(event -> (Event) event)
            .toList();
    }

    static <State, Event> State aggregateStream(
        List<Event> events,
        BiFunction<State, Event, State> evolve,
        Supplier<State> initial
    ) {
        return events.stream()
            .reduce(
                initial.get(),
                evolve,
                (prev, next) -> prev
            );
    }
}

record EventMetadata(
    String streamId,
    String eventType,
    UUID eventId,
    int version,
    Instant occurredAt
) {
    public static <T> EventMetadata from(Class<T> eventType, String streamId, int version) {
        return new EventMetadata(
            streamId,
            eventType.getTypeName(),
            UUID.randomUUID(),
            version,
            Instant.now()
        );
    }
}

record EventEnvelope(
    Object data,
    EventMetadata metadata
) {

    public static EventEnvelope from(String streamId, Object event, int version) {
        return new EventEnvelope(
            event,
            EventMetadata.from(event.getClass(), streamId, version)
        );
    }
}
