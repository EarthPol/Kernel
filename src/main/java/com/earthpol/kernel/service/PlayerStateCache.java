package com.earthpol.kernel.service;

import com.earthpol.kernel.data.model.StoredPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateCache {

    private final Set<UUID> godModePlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> flyEnabledPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> teleportBlockedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> nicknames = new ConcurrentHashMap<>();

    public void load(StoredPlayer player) {
        setGodEnabled(player.uuid(), player.godMode());
        setFlyEnabled(player.uuid(), player.flyMode());
        setTeleportBlocked(player.uuid(), player.teleportBlocked());
        setNickname(player.uuid(), player.nickname());
    }

    public void unload(UUID uuid) {
        godModePlayers.remove(uuid);
        flyEnabledPlayers.remove(uuid);
        teleportBlockedPlayers.remove(uuid);
        nicknames.remove(uuid);
    }

    public boolean isGodEnabled(UUID uuid) {
        return godModePlayers.contains(uuid);
    }

    public boolean isFlyEnabled(UUID uuid) {
        return flyEnabledPlayers.contains(uuid);
    }

    public boolean isTeleportBlocked(UUID uuid) {
        return teleportBlockedPlayers.contains(uuid);
    }

    public Optional<String> nickname(UUID uuid) {
        return Optional.ofNullable(nicknames.get(uuid));
    }

    public void setGodEnabled(UUID uuid, boolean enabled) {
        update(godModePlayers, uuid, enabled);
    }

    public void setFlyEnabled(UUID uuid, boolean enabled) {
        update(flyEnabledPlayers, uuid, enabled);
    }

    public void setTeleportBlocked(UUID uuid, boolean enabled) {
        update(teleportBlockedPlayers, uuid, enabled);
    }

    public void setNickname(UUID uuid, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            nicknames.remove(uuid);
            return;
        }

        nicknames.put(uuid, nickname);
    }

    private void update(Set<UUID> set, UUID uuid, boolean enabled) {
        if (enabled) {
            set.add(uuid);
        } else {
            set.remove(uuid);
        }
    }
}
