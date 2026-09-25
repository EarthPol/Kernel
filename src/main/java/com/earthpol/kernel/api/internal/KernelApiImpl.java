package com.earthpol.kernel.api.internal;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.api.KernelApi;
import com.earthpol.kernel.api.KernelHome;
import com.earthpol.kernel.api.KernelPlayerReference;
import com.earthpol.kernel.api.KernelStoredPlayer;
import com.earthpol.kernel.data.model.HomeRecord;
import com.earthpol.kernel.data.model.PlayerLookup;
import com.earthpol.kernel.data.model.StoredPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class KernelApiImpl implements KernelApi {

    private final Kernel plugin;

    public KernelApiImpl(Kernel plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<KernelPlayerReference> findPlayerByName(String playerName) {
        Objects.requireNonNull(playerName, "playerName");

        return plugin.repository().findPlayerByName(playerName)
            .or(() -> onlineLookup(playerName))
            .map(this::toReference);
    }

    @Override
    public Optional<KernelStoredPlayer> getStoredPlayer(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return plugin.repository().findPlayer(playerUuid).map(this::toStoredPlayer);
    }

    @Override
    public Optional<String> getPlayerNickname(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        return plugin.stateCache().nickname(playerUuid)
            .filter(nickname -> !nickname.isBlank())
            .or(() -> plugin.repository().findPlayer(playerUuid)
                .map(StoredPlayer::nickname)
                .filter(nickname -> nickname != null && !nickname.isBlank()));
    }

    @Override
    public List<String> getHomeNames(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return List.copyOf(plugin.repository().listHomes(playerUuid));
    }

    @Override
    public Optional<KernelHome> getPlayerHome(UUID playerUuid, String homeName) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(homeName, "homeName");

        return plugin.repository()
            .findHome(playerUuid, plugin.settings().normalizeHomeName(homeName))
            .map(this::toHome);
    }

    @Override
    public boolean isSeenBefore(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return plugin.repository().findPlayer(playerUuid).isPresent();
    }

    @Override
    public List<KernelPlayerReference> getIgnored(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return plugin.repository().getIgnored(playerUuid).stream()
            .map(this::toReference)
            .toList();
    }

    @Override
    public List<KernelPlayerReference> getIgnoredBy(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return plugin.repository().getIgnoredBy(playerUuid).stream()
            .map(this::toReference)
            .toList();
    }

    @Override
    public boolean isIgnoring(UUID ownerUuid, UUID targetUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(targetUuid, "targetUuid");
        return plugin.repository().isIgnoring(ownerUuid, targetUuid);
    }

    @Override
    public String getDefaultHomeName() {
        return plugin.settings().defaultHomeName();
    }

    private Optional<PlayerLookup> onlineLookup(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            player = Bukkit.getPlayer(playerName);
        }

        if (player == null) {
            return Optional.empty();
        }

        plugin.repository().ensurePlayer(player);
        return Optional.of(new PlayerLookup(player.getUniqueId(), player.getName()));
    }

    private KernelPlayerReference toReference(PlayerLookup lookup) {
        return new KernelPlayerReference(lookup.uuid(), lookup.name());
    }

    private KernelStoredPlayer toStoredPlayer(StoredPlayer storedPlayer) {
        return new KernelStoredPlayer(
            storedPlayer.uuid(),
            storedPlayer.lastKnownName(),
            storedPlayer.lastKnownIp(),
            storedPlayer.balance(),
            storedPlayer.nickname(),
            storedPlayer.godMode(),
            storedPlayer.flyMode(),
            storedPlayer.teleportBlocked(),
            storedPlayer.lastSeenEpochMillis()
        );
    }

    private KernelHome toHome(HomeRecord homeRecord) {
        return new KernelHome(
            homeRecord.name(),
            homeRecord.worldName(),
            homeRecord.x(),
            homeRecord.y(),
            homeRecord.z(),
            homeRecord.yaw(),
            homeRecord.pitch()
        );
    }
}
