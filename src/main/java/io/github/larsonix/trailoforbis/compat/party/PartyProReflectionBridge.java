package io.github.larsonix.trailoforbis.compat.party;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Reflection-based bridge to PartyPro's API.
 *
 * <p>Accesses {@code me.tsumori.partypro.api.PartyProAPI} via cached {@link Method}
 * objects. Zero compile-time dependency on PartyPro — the plugin compiles and runs
 * without it. When PartyPro is present, all party queries delegate to its API.
 *
 * <p>Event listening uses {@link Proxy} to implement PartyPro's
 * {@code PartyEventListener} interface at runtime without compile-time reference.
 *
 * @see PartyBridge
 * @see NoOpPartyBridge
 */
public final class PartyProReflectionBridge implements PartyBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Cached API class + singleton ──
    private final Class<?> apiClass;
    private final Object apiInstance;

    // ── Cached API methods ──
    private final Method isInPartyMethod;
    private final Method areInSamePartyMethod;
    private final Method getPartyByPlayerMethod;
    private final Method getPartyMembersMethod;     // on API: getPartyMembers(UUID partyId)
    private final Method getOnlinePartyMembersMethod;
    private final Method getPartyLeaderMethod;
    private final Method setPlayerCustomTextMethod;
    private final Method clearPlayerCustomTextMethod;
    private final Method registerListenerMethod;
    private final Method unregisterListenerMethod;

    // ── Cached PartySnapshot methods (resolved on first use) ──
    private volatile Method snapshotIdMethod;
    private volatile Method snapshotLeaderMethod;
    private volatile Method snapshotGetAllMembersMethod;
    private volatile Method snapshotPvpEnabledMethod;

    // ── Event listener proxy ──
    private final Class<?> eventListenerInterface;
    private Object proxyListener;

    // ── Our listeners ──
    private final List<PartyChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates the bridge by resolving PartyPro's API via reflection.
     *
     * @param apiClassName The fully qualified API class name
     * @throws ReflectiveOperationException if the API cannot be resolved
     */
    public PartyProReflectionBridge(@Nonnull String apiClassName) throws ReflectiveOperationException {
        // Resolve API class and singleton
        apiClass = Class.forName(apiClassName);
        Method isAvailableMethod = apiClass.getMethod("isAvailable");
        Boolean available = (Boolean) isAvailableMethod.invoke(null);
        if (!Boolean.TRUE.equals(available)) {
            throw new IllegalStateException("PartyProAPI.isAvailable() returned false");
        }

        Method getInstanceMethod = apiClass.getMethod("getInstance");
        apiInstance = getInstanceMethod.invoke(null);
        if (apiInstance == null) {
            throw new IllegalStateException("PartyProAPI.getInstance() returned null");
        }

        // Cache all API methods
        isInPartyMethod = apiClass.getMethod("isInParty", UUID.class);
        areInSamePartyMethod = apiClass.getMethod("areInSameParty", UUID.class, UUID.class);
        getPartyByPlayerMethod = apiClass.getMethod("getPartyByPlayer", UUID.class);
        getPartyMembersMethod = apiClass.getMethod("getPartyMembers", UUID.class);
        getOnlinePartyMembersMethod = apiClass.getMethod("getOnlinePartyMembers", UUID.class);
        getPartyLeaderMethod = apiClass.getMethod("getPartyLeader", UUID.class);
        setPlayerCustomTextMethod = apiClass.getMethod("setPlayerCustomText", UUID.class, String.class, String.class);
        clearPlayerCustomTextMethod = apiClass.getMethod("clearPlayerCustomText", UUID.class);
        registerListenerMethod = apiClass.getMethod("registerListener",
            Class.forName("me.tsumori.partypro.api.PartyEventListener"));
        unregisterListenerMethod = apiClass.getMethod("unregisterListener",
            Class.forName("me.tsumori.partypro.api.PartyEventListener"));

        // Resolve event listener interface for Proxy
        eventListenerInterface = Class.forName("me.tsumori.partypro.api.PartyEventListener");

        LOGGER.at(Level.INFO).log("PartyPro reflection bridge initialized — all %d methods cached", 10);
    }

    /**
     * Registers our event proxy with PartyPro to receive party lifecycle events.
     */
    public void registerEventProxy() {
        if (proxyListener != null) {
            return; // Already registered
        }

        proxyListener = Proxy.newProxyInstance(
            eventListenerInterface.getClassLoader(),
            new Class<?>[] { eventListenerInterface },
            new PartyEventProxyHandler()
        );

        try {
            registerListenerMethod.invoke(apiInstance, proxyListener);
            LOGGER.at(Level.INFO).log("PartyPro event listener proxy registered");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to register PartyPro event proxy: %s", e.getMessage());
            proxyListener = null;
        }
    }

    /**
     * Unregisters our event proxy from PartyPro.
     */
    public void unregisterEventProxy() {
        if (proxyListener == null) {
            return;
        }
        try {
            unregisterListenerMethod.invoke(apiInstance, proxyListener);
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Failed to unregister PartyPro event proxy: %s", e.getMessage());
        }
        proxyListener = null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PartyBridge IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isInParty(@Nonnull UUID playerId) {
        try {
            Object result = isInPartyMethod.invoke(apiInstance, playerId);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean areInSameParty(@Nonnull UUID player1, @Nonnull UUID player2) {
        try {
            Object result = areInSamePartyMethod.invoke(apiInstance, player1, player2);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<UUID> getPartyId(@Nonnull UUID playerId) {
        Object snapshot = getSnapshot(playerId);
        if (snapshot == null) return Optional.empty();
        return Optional.ofNullable(invokeSnapshotUuid(snapshot, "id"));
    }

    @Nonnull
    @Override
    public List<UUID> getPartyMembers(@Nonnull UUID playerId) {
        Object snapshot = getSnapshot(playerId);
        if (snapshot == null) return List.of(playerId);
        return invokeSnapshotUuidList(snapshot, "getAllMembers", playerId);
    }

    @Nonnull
    @Override
    public List<UUID> getOnlinePartyMembers(@Nonnull UUID playerId) {
        Object snapshot = getSnapshot(playerId);
        if (snapshot == null) return List.of(playerId);

        UUID partyId = invokeSnapshotUuid(snapshot, "id");
        if (partyId == null) return List.of(playerId);

        try {
            Object result = getOnlinePartyMembersMethod.invoke(apiInstance, partyId);
            return castUuidList(result, playerId);
        } catch (Exception e) {
            return List.of(playerId);
        }
    }

    @Override
    public Optional<UUID> getPartyLeader(@Nonnull UUID playerId) {
        Object snapshot = getSnapshot(playerId);
        if (snapshot == null) return Optional.empty();
        return Optional.ofNullable(invokeSnapshotUuid(snapshot, "leader"));
    }

    @Override
    public int getPartySize(@Nonnull UUID playerId) {
        return getPartyMembers(playerId).size();
    }

    @Override
    public boolean isPvpEnabledInParty(@Nonnull UUID playerId) {
        Object snapshot = getSnapshot(playerId);
        if (snapshot == null) return true;
        try {
            resolveSnapshotMethods(snapshot);
            if (snapshotPvpEnabledMethod == null) return true;
            Object result = snapshotPvpEnabledMethod.invoke(snapshot);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void setCustomText(@Nonnull UUID playerId, String text1, String text2) {
        try {
            setPlayerCustomTextMethod.invoke(apiInstance, playerId, text1, text2);
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Failed to set custom text for %s: %s",
                playerId.toString().substring(0, 8), e.getMessage());
        }
    }

    @Override
    public void clearCustomText(@Nonnull UUID playerId) {
        try {
            clearPlayerCustomTextMethod.invoke(apiInstance, playerId);
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Failed to clear custom text for %s: %s",
                playerId.toString().substring(0, 8), e.getMessage());
        }
    }

    @Override
    public void registerEventListener(@Nonnull PartyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterEventListener(@Nonnull PartyChangeListener listener) {
        listeners.remove(listener);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SNAPSHOT HELPERS
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private Object getSnapshot(@Nonnull UUID playerId) {
        try {
            return getPartyByPlayerMethod.invoke(apiInstance, playerId);
        } catch (Exception e) {
            return null;
        }
    }

    private void resolveSnapshotMethods(@Nonnull Object snapshot) {
        if (snapshotIdMethod != null) return; // Already resolved
        Class<?> type = snapshot.getClass();
        try {
            snapshotIdMethod = type.getMethod("id");
            snapshotLeaderMethod = type.getMethod("leader");
            snapshotGetAllMembersMethod = type.getMethod("getAllMembers");
            snapshotPvpEnabledMethod = type.getMethod("pvpEnabled");
        } catch (NoSuchMethodException e) {
            LOGGER.at(Level.WARNING).log("Failed to resolve PartySnapshot methods: %s", e.getMessage());
        }
    }

    @Nullable
    private UUID invokeSnapshotUuid(@Nonnull Object snapshot, @Nonnull String methodName) {
        try {
            resolveSnapshotMethods(snapshot);
            Method method = switch (methodName) {
                case "id" -> snapshotIdMethod;
                case "leader" -> snapshotLeaderMethod;
                default -> null;
            };
            if (method == null) return null;
            Object result = method.invoke(snapshot);
            return result instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private List<UUID> invokeSnapshotUuidList(@Nonnull Object snapshot, @Nonnull String methodName,
                                               @Nonnull UUID fallback) {
        try {
            resolveSnapshotMethods(snapshot);
            if (snapshotGetAllMembersMethod == null) return List.of(fallback);
            Object result = snapshotGetAllMembersMethod.invoke(snapshot);
            return castUuidList(result, fallback);
        } catch (Exception e) {
            return List.of(fallback);
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private List<UUID> castUuidList(@Nullable Object result, @Nonnull UUID fallback) {
        if (result instanceof List<?> list) {
            List<UUID> uuids = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof UUID uuid) {
                    uuids.add(uuid);
                }
            }
            return uuids.isEmpty() ? List.of(fallback) : Collections.unmodifiableList(uuids);
        }
        return List.of(fallback);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT PROXY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * InvocationHandler for the dynamic Proxy implementing PartyPro's
     * PartyEventListener interface. Dispatches events to our listeners.
     */
    private class PartyEventProxyHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();

            // Handle Object methods
            if ("toString".equals(name)) return "TrailOfOrbis-PartyEventProxy";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == args[0];

            try {
                switch (name) {
                    case "onMemberJoin" -> dispatchMemberJoin(args[0]);
                    case "onMemberLeave" -> dispatchMemberLeave(args[0]);
                    case "onPartyDisband" -> dispatchPartyDisband(args[0]);
                    case "onLeaderChange" -> dispatchLeaderChange(args[0]);
                    case "onSettingsChange" -> dispatchSettingsChange(args[0]);
                    // onPartyCreate — no action needed
                }
            } catch (Exception e) {
                LOGGER.at(Level.FINE).log("Error dispatching PartyPro event %s: %s", name, e.getMessage());
            }
            return null;
        }

        private void dispatchMemberJoin(Object event) throws Exception {
            UUID partyId = getEventUuid(event, "getPartyId");
            UUID playerId = getEventUuid(event, "getPlayerId");
            if (partyId != null && playerId != null) {
                for (PartyChangeListener l : listeners) {
                    l.onMemberJoined(partyId, playerId);
                }
            }
        }

        private void dispatchMemberLeave(Object event) throws Exception {
            UUID partyId = getEventUuid(event, "getPartyId");
            UUID playerId = getEventUuid(event, "getPlayerId");
            if (partyId != null && playerId != null) {
                for (PartyChangeListener l : listeners) {
                    l.onMemberLeft(partyId, playerId);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void dispatchPartyDisband(Object event) throws Exception {
            UUID partyId = getEventUuid(event, "getPartyId");
            List<UUID> members = List.of();
            try {
                Method getMembers = event.getClass().getMethod("getFormerMembers");
                Object result = getMembers.invoke(event);
                if (result instanceof List<?> list) {
                    List<UUID> uuids = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof UUID uuid) uuids.add(uuid);
                    }
                    members = Collections.unmodifiableList(uuids);
                }
            } catch (Exception ignored) {}

            if (partyId != null) {
                for (PartyChangeListener l : listeners) {
                    l.onPartyDisbanded(partyId, members);
                }
            }
        }

        private void dispatchLeaderChange(Object event) throws Exception {
            UUID partyId = getEventUuid(event, "getPartyId");
            UUID oldLeader = getEventUuid(event, "getOldLeader");
            UUID newLeader = getEventUuid(event, "getNewLeader");
            if (partyId != null && oldLeader != null && newLeader != null) {
                for (PartyChangeListener l : listeners) {
                    l.onLeaderChanged(partyId, oldLeader, newLeader);
                }
            }
        }

        private void dispatchSettingsChange(Object event) throws Exception {
            UUID partyId = getEventUuid(event, "getPartyId");
            if (partyId == null) return;

            try {
                Method getSetting = event.getClass().getMethod("getSetting");
                Object setting = getSetting.invoke(event);
                if (setting != null && "PVP_ENABLED".equals(setting.toString())) {
                    Method getNewValue = event.getClass().getMethod("getNewValue");
                    Object newValue = getNewValue.invoke(event);
                    boolean pvp = Boolean.TRUE.equals(newValue);
                    for (PartyChangeListener l : listeners) {
                        l.onPvpSettingChanged(partyId, pvp);
                    }
                }
            } catch (Exception ignored) {}
        }

        @Nullable
        private UUID getEventUuid(@Nonnull Object event, @Nonnull String methodName) throws Exception {
            Method method = event.getClass().getMethod(methodName);
            Object result = method.invoke(event);
            return result instanceof UUID uuid ? uuid : null;
        }
    }
}
