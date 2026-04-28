package io.github.larsonix.trailoforbis.api;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for service instances.
 *
 * <p>Provides a decoupled way to access services without going through
 * the main plugin singleton. Services are registered during plugin startup
 * and cleared during shutdown.
 *
 * <p>Usage:
 * <pre>
 * // Registration (in plugin start())
 * ServiceRegistry.register(AttributeService.class, attributeManager);
 *
 * // Access (defensive - returns Optional)
 * Optional&lt;AttributeService&gt; service = ServiceRegistry.get(AttributeService.class);
 *
 * // Access (fail-fast - throws if not found)
 * AttributeService service = ServiceRegistry.require(AttributeService.class);
 * </pre>
 *
 * <p>Thread safety: All operations are thread-safe using ConcurrentHashMap.
 */
public final class ServiceRegistry {
    private static final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    private ServiceRegistry() {
        // Static utility class - prevent instantiation
    }

    /**
     * @throws IllegalArgumentException if serviceClass or implementation is null
     */
    public static <T> void register(@Nonnull Class<T> serviceClass, @Nonnull T implementation) {
        if (serviceClass == null) {
            throw new IllegalArgumentException("serviceClass cannot be null");
        }
        if (implementation == null) {
            throw new IllegalArgumentException("implementation cannot be null");
        }
        services.put(serviceClass, implementation);
    }

    /**
     * Defensive lookup for code that handles missing services gracefully.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static <T> Optional<T> get(@Nonnull Class<T> serviceClass) {
        if (serviceClass == null) {
            return Optional.empty();
        }
        Object service = services.get(serviceClass);
        if (service == null) {
            return Optional.empty();
        }
        return Optional.of((T) service);
    }

    /**
     * Fail-fast lookup; absence indicates a bug (service should have been registered).
     *
     * @throws IllegalStateException if the service is not registered
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static <T> T require(@Nonnull Class<T> serviceClass) {
        if (serviceClass == null) {
            throw new IllegalArgumentException("serviceClass cannot be null");
        }
        Object service = services.get(serviceClass);
        if (service == null) {
            throw new IllegalStateException(
                "Service not registered: " + serviceClass.getName() +
                ". Ensure the plugin has started and registered all services."
            );
        }
        return (T) service;
    }

    /** Checks if a service is registered. */
    public static boolean isRegistered(@Nonnull Class<?> serviceClass) {
        return serviceClass != null && services.containsKey(serviceClass);
    }

    /** Called during plugin shutdown to clean up. */
    public static void clear() {
        services.clear();
    }

    /** @return the removed service, or null if not registered */
    @SuppressWarnings("unchecked")
    public static <T> T unregister(@Nonnull Class<T> serviceClass) {
        if (serviceClass == null) {
            return null;
        }
        return (T) services.remove(serviceClass);
    }
}
