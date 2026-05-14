package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.maps.event.RealmEvent;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Custom event dispatch for realm lifecycle events.
 *
 * <p>Supports typed listeners that receive specific event subclasses,
 * plus base {@link RealmEvent} listeners that receive all events.
 *
 * <p>Thread-safe: uses ConcurrentHashMap + CopyOnWriteArrayList.
 */
final class RealmEventBus {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<Class<? extends RealmEvent>, List<Consumer<? extends RealmEvent>>> listeners =
            new ConcurrentHashMap<>();

    /**
     * Registers a listener for a specific event type.
     *
     * @param eventClass The event class to listen for
     * @param listener The listener to call when the event fires
     * @param <T> The event type
     */
    <T extends RealmEvent> void addListener(
            @Nonnull Class<T> eventClass,
            @Nonnull Consumer<T> listener) {
        Objects.requireNonNull(eventClass, "Event class cannot be null");
        Objects.requireNonNull(listener, "Listener cannot be null");

        listeners
            .computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
            .add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param eventClass The event class
     * @param listener The listener to remove
     * @param <T> The event type
     * @return true if the listener was removed
     */
    <T extends RealmEvent> boolean removeListener(
            @Nonnull Class<T> eventClass,
            @Nonnull Consumer<T> listener) {
        List<Consumer<? extends RealmEvent>> list = listeners.get(eventClass);
        if (list != null) {
            return list.remove(listener);
        }
        return false;
    }

    /**
     * Fires an event to all registered listeners.
     *
     * <p>Dispatches to type-specific listeners first, then to base
     * {@link RealmEvent} listeners (if any are registered).
     *
     * @param event The event to fire
     * @param <T> The event type
     */
    @SuppressWarnings("unchecked")
    <T extends RealmEvent> void fireEvent(@Nonnull T event) {
        Class<?> eventClass = event.getClass();

        // Fire to specific listeners
        List<Consumer<? extends RealmEvent>> specific = listeners.get(eventClass);
        if (specific != null) {
            for (Consumer<? extends RealmEvent> listener : specific) {
                try {
                    ((Consumer<T>) listener).accept(event);
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error in realm event listener for %s",
                        eventClass.getSimpleName());
                }
            }
        }

        // Fire to base RealmEvent listeners (catch-all)
        if (eventClass != RealmEvent.class) {
            List<Consumer<? extends RealmEvent>> baseListeners = listeners.get(RealmEvent.class);
            if (baseListeners != null) {
                for (Consumer<? extends RealmEvent> listener : baseListeners) {
                    try {
                        ((Consumer<RealmEvent>) listener).accept(event);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Error in base realm event listener");
                    }
                }
            }
        }
    }

    /**
     * Clears all registered listeners. Called during shutdown.
     */
    void clear() {
        listeners.clear();
    }
}
