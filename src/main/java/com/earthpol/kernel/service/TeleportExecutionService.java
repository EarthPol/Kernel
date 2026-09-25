package com.earthpol.kernel.service;

import com.europamc.europalib.entity.EntitySchedulerUtil;
import com.europamc.europalib.teleport.TeleportContext;
import com.europamc.europalib.teleport.TeleportOutcome;
import com.europamc.europalib.teleport.TeleportOutcomeHandler;
import com.europamc.europalib.teleport.Teleporter;
import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.config.KernelSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.function.Consumer;

public final class TeleportExecutionService {

    public static final String BYPASS_WARMUP_PERMISSION = "kernel.command.teleport.bypass-warmup";

    private final Kernel plugin;
    private final KernelSettings settings;
    private final BackService backService;
    private final Teleporter instantTeleporter;
    private final Teleporter instantUnsafeTeleporter;
    private final Teleporter standardWarmupTeleporter;
    private final Teleporter standardWarmupUnsafeTeleporter;
    private final Teleporter requestWarmupTeleporter;

    public TeleportExecutionService(Kernel plugin, KernelSettings settings, BackService backService) {
        this.plugin = plugin;
        this.settings = settings;
        this.backService = backService;

        TeleportOutcomeHandler outcomeHandler = new TeleportOutcomeHandler()
            .setOnSuccess(this::handleSuccess)
            .setOnFailedWarmupMoved(this::handleWarmupMoved)
            .setOnFailedDestUnsafe(this::handleFailure)
            .setOnFailedError(this::handleFailure)
            .setActionOnOutcome(TeleportOutcome.FAILED_NO_PERMISSION, this::handleFailure)
            .setOnFailedUnaffordable(this::handleFailure);

        this.instantTeleporter = Teleporter.builder(plugin)
            .enableDestinationSafety()
            .cause(PlayerTeleportEvent.TeleportCause.COMMAND)
            .setOutcomeHandler(outcomeHandler)
            .build();
        this.instantUnsafeTeleporter = Teleporter.builder(plugin)
            .cause(PlayerTeleportEvent.TeleportCause.COMMAND)
            .setOutcomeHandler(outcomeHandler)
            .build();

        this.standardWarmupTeleporter = buildWarmupTeleporter(settings.teleportWarmupTicks(), outcomeHandler, true);
        this.standardWarmupUnsafeTeleporter = buildWarmupTeleporter(settings.teleportWarmupTicks(), outcomeHandler, false);
        this.requestWarmupTeleporter = buildWarmupTeleporter(settings.teleportRequestWarmupTicks(), outcomeHandler);
    }

    public void teleport(Player player, Location destination, Consumer<Player> onSuccess) {
        teleport(player, destination, ignored -> {}, onSuccess, ignored -> {}, ignored -> {}, WarmupType.STANDARD, SafetyMode.SAFE);
    }

    public void teleportUnsafe(Player player, Location destination, Consumer<Player> onSuccess) {
        teleport(player, destination, ignored -> {}, onSuccess, ignored -> {}, ignored -> {}, WarmupType.STANDARD, SafetyMode.UNSAFE);
    }

    public void teleportToPlayer(Player player, Player destinationPlayer, Consumer<Player> onSuccess) {
        teleportToPlayer(player, destinationPlayer, ignored -> {}, onSuccess, ignored -> {}, ignored -> {}, WarmupType.STANDARD, SafetyMode.SAFE);
    }

    public void teleportToPlayerUnsafe(Player player, Player destinationPlayer, Consumer<Player> onSuccess) {
        teleportToPlayer(player, destinationPlayer, ignored -> {}, onSuccess, ignored -> {}, ignored -> {}, WarmupType.STANDARD, SafetyMode.UNSAFE);
    }

    public void teleportToPlayerWithRequestWarmup(Player player, Player destinationPlayer, Consumer<Player> onSuccess) {
        teleportToPlayer(player, destinationPlayer, ignored -> {}, onSuccess, ignored -> {}, ignored -> {}, WarmupType.REQUEST, SafetyMode.SAFE);
    }

    public void teleportToPlayerWithRequestWarmup(
        Player player,
        Player destinationPlayer,
        Consumer<Player> beforeWarmup,
        Consumer<Player> onSuccess
    ) {
        teleportToPlayer(player, destinationPlayer, beforeWarmup, onSuccess, ignored -> {}, ignored -> {}, WarmupType.REQUEST, SafetyMode.SAFE);
    }

    public void teleportToPlayerWithRequestWarmup(
        Player player,
        Player destinationPlayer,
        Consumer<Player> beforeWarmup,
        Consumer<Player> onSuccess,
        Consumer<Player> onWarmupMoved,
        Consumer<Player> onFailure
    ) {
        teleportToPlayer(player, destinationPlayer, beforeWarmup, onSuccess, onWarmupMoved, onFailure, WarmupType.REQUEST, SafetyMode.SAFE);
    }

    private void teleport(
        Player player,
        Location destination,
        Consumer<Player> beforeWarmup,
        Consumer<Player> onSuccess,
        Consumer<Player> onWarmupMoved,
        Consumer<Player> onFailure,
        WarmupType warmupType,
        SafetyMode safetyMode
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destination, "destination");

        EntitySchedulerUtil.run(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }

            beforeWarmup.accept(player);

            Teleporter teleporter = selectTeleporter(player, warmupType, safetyMode);
            long warmupSeconds = warmupSeconds(warmupType);
            if (teleporter != instantTeleporter && teleporter != instantUnsafeTeleporter && warmupSeconds > 0L) {
                plugin.messageNow(player, "teleport.warmup.start", warmupSeconds);
            }

            teleporter.teleport(
                player,
                destination.clone(),
                new TeleportOperation(player.getLocation().clone(), onSuccess, onWarmupMoved, onFailure)
            );
        });
    }

    private void teleportToPlayer(
        Player player,
        Player destinationPlayer,
        Consumer<Player> beforeWarmup,
        Consumer<Player> onSuccess,
        Consumer<Player> onWarmupMoved,
        Consumer<Player> onFailure,
        WarmupType warmupType,
        SafetyMode safetyMode
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destinationPlayer, "destinationPlayer");

        destinationPlayer.getScheduler().run(
            plugin,
            scheduledTask -> teleport(
                player,
                destinationPlayer.getLocation().clone(),
                beforeWarmup,
                onSuccess,
                onWarmupMoved,
                onFailure,
                warmupType,
                safetyMode
            ),
            () -> {
                if (player.isOnline()) {
                    plugin.message(player, "error.player-not-online");
                }
            }
        );
    }

    private Teleporter selectTeleporter(Player player, WarmupType warmupType, SafetyMode safetyMode) {
        if (player.hasPermission(BYPASS_WARMUP_PERMISSION)) {
            return safetyMode == SafetyMode.UNSAFE ? instantUnsafeTeleporter : instantTeleporter;
        }

        return switch (warmupType) {
            case REQUEST -> requestWarmupTeleporter;
            case STANDARD -> safetyMode == SafetyMode.UNSAFE ? standardWarmupUnsafeTeleporter : standardWarmupTeleporter;
        };
    }

    private void handleSuccess(TeleportContext context) {
        Object customData = context.getCustomData();
        if (!(customData instanceof TeleportOperation operation)) {
            return;
        }

        backService.capture(context.getPlayer(), operation.origin());
        operation.onSuccess().accept(context.getPlayer());
    }

    private void handleWarmupMoved(TeleportContext context) {
        Object customData = context.getCustomData();
        if (!(customData instanceof TeleportOperation operation)) {
            return;
        }

        operation.onWarmupMoved().accept(context.getPlayer());
    }

    private void handleFailure(TeleportContext context) {
        Object customData = context.getCustomData();
        if (!(customData instanceof TeleportOperation operation)) {
            return;
        }

        operation.onFailure().accept(context.getPlayer());
    }

    private record TeleportOperation(
        Location origin,
        Consumer<Player> onSuccess,
        Consumer<Player> onWarmupMoved,
        Consumer<Player> onFailure
    ) {

        private TeleportOperation {
            Objects.requireNonNull(origin, "origin");
            onSuccess = onSuccess == null ? player -> {} : onSuccess;
            onWarmupMoved = onWarmupMoved == null ? player -> {} : onWarmupMoved;
            onFailure = onFailure == null ? player -> {} : onFailure;
        }
    }

    private Teleporter buildWarmupTeleporter(long warmupTicks, TeleportOutcomeHandler outcomeHandler) {
        return buildWarmupTeleporter(warmupTicks, outcomeHandler, true);
    }

    private Teleporter buildWarmupTeleporter(long warmupTicks, TeleportOutcomeHandler outcomeHandler, boolean destinationSafety) {
        if (warmupTicks <= 0L) {
            return destinationSafety ? instantTeleporter : instantUnsafeTeleporter;
        }

        Teleporter.Builder builder = Teleporter.builder(plugin)
            .enableWarmup(warmupTicks)
            .disablePreTeleportMovement()
            .cause(PlayerTeleportEvent.TeleportCause.COMMAND)
            .setOutcomeHandler(outcomeHandler);

        if (destinationSafety) {
            builder.enableDestinationSafety();
        }

        return builder.build();
    }

    private long warmupSeconds(WarmupType warmupType) {
        return switch (warmupType) {
            case REQUEST -> settings.teleportRequestWarmup().toSeconds();
            case STANDARD -> settings.teleportWarmup().toSeconds();
        };
    }

    private enum WarmupType {
        STANDARD,
        REQUEST
    }

    private enum SafetyMode {
        SAFE,
        UNSAFE
    }
}
