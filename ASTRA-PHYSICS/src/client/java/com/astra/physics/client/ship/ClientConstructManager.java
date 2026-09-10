package com.astra.physics.client.ship;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.astra.physics.network.ConstructSpawnPayload;
import com.astra.physics.network.ConstructTransformPayload;

public final class ClientConstructManager {
    private static final Map<UUID, ClientPhysicsConstruct> CONSTRUCTS = new HashMap<>();

    private ClientConstructManager() {}

    public static void spawn(ConstructSpawnPayload payload) {
        CONSTRUCTS.put(payload.id(), new ClientPhysicsConstruct(payload));
    }

    public static void transform(ConstructTransformPayload payload) {
        ClientPhysicsConstruct construct = CONSTRUCTS.get(payload.id());
        if (construct != null) {
            construct.updateTransform(payload.x(), payload.y(), payload.z());
        }
    }

    public static Collection<ClientPhysicsConstruct> all() {
        return CONSTRUCTS.values();
    }

    public static ClientPhysicsConstruct get(UUID id) {
        return CONSTRUCTS.get(id);
    }

    public static void remove(java.util.UUID id) {
        CONSTRUCTS.remove(id);
    }

    public static void clear() {
        CONSTRUCTS.clear();
    }
}
