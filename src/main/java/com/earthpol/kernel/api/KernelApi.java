package com.earthpol.kernel.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Kernel API exposed through Bukkit's {@code ServicesManager}.
 */
public interface KernelApi {

    /**
     * Resolves a player from Kernel's local player data and currently online players.
     * This does not perform Mojang profile lookups.
     */
    Optional<KernelPlayerReference> findPlayerByName(String playerName);

    default Optional<UUID> findPlayerUuidByName(String playerName) {
        Objects.requireNonNull(playerName, "playerName");
        return findPlayerByName(playerName).map(KernelPlayerReference::uuid);
    }

    Optional<KernelStoredPlayer> getStoredPlayer(UUID playerUuid);

    Optional<String> getPlayerNickname(UUID playerUuid);

    List<String> getHomeNames(UUID playerUuid);

    default Optional<KernelHome> getPlayerHome(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return getPlayerHome(playerUuid, getDefaultHomeName());
    }

    Optional<KernelHome> getPlayerHome(UUID playerUuid, String homeName);

    boolean isSeenBefore(UUID playerUuid);

    default boolean hasSeenBefore(UUID playerUuid) {
        return isSeenBefore(playerUuid);
    }

    List<KernelPlayerReference> getIgnored(UUID playerUuid);

    List<KernelPlayerReference> getIgnoredBy(UUID playerUuid);

    boolean isIgnoring(UUID ownerUuid, UUID targetUuid);

    String getDefaultHomeName();
}
