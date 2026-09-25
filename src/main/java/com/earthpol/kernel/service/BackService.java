package com.earthpol.kernel.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackService {

    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();

    public void capture(Player player) {
        capture(player.getUniqueId(), player.getLocation());
    }

    public void capture(Player player, Location location) {
        capture(player.getUniqueId(), location);
    }

    public void capture(UUID uuid, Location location) {
        if (location != null && location.getWorld() != null) {
            backLocations.put(uuid, location.clone());
        }
    }

    public Optional<Location> take(UUID uuid) {
        Location location = backLocations.remove(uuid);
        return Optional.ofNullable(location == null ? null : location.clone());
    }

    public Optional<Location> peek(UUID uuid) {
        Location location = backLocations.get(uuid);
        return Optional.ofNullable(location == null ? null : location.clone());
    }

    public void clear(UUID uuid) {
        backLocations.remove(uuid);
    }
}
