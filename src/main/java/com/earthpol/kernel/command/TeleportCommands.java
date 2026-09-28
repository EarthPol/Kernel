package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.service.TeleportService.TeleportRequest;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TeleportCommands extends BaseCommandHandler {

    public TeleportCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> handleTpa(sender, args);
            case "tpaccept" -> handleTpAccept(sender);
            case "tpdeny" -> handleTpDeny(sender);
            case "back" -> handleBack(sender);
            case "tp" -> handleTp(sender, args, false);
            case "tppos" -> handleTpPos(sender, args);
            case "tphere" -> handleTpHere(sender, args);
            case "tptoggle" -> handleTpToggle(sender);
            case "tpo" -> handleTp(sender, args, true);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("tp") && (args.length == 1 || args.length == 2)
            && !looksLikeCoordinateToken(args[0])) {
            String prefix = args[args.length - 1];
            List<String> matches = new ArrayList<>(completeOnlinePlayers(prefix));
            matches.addAll(completeOptions(prefix, List.of("@s", "@p", "@r", "@a")));
            return matches;
        }
        if (switch (commandName) {
            case "tpa", "tphere", "tpo" -> args.length == 1;
            default -> false;
        }) {
            return completeOnlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }

    private boolean handleTpa(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.tpa")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            message(sender, "error.player-not-online");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            message(sender, "teleport.request.self");
            return true;
        }

        if (stateCache.isTeleportBlocked(target.getUniqueId())) {
            message(sender, "teleport.request.blocked");
            return true;
        }

        String targetName = args[0];
        String requesterName = player.getName();
        teleportService.create(player, target);
        message(sender, "teleport.request.sent", targetName);
        message(target, "teleport.request.received", requesterName);
        message(target, "teleport.request.instructions", settings.teleportRequestTimeout().toSeconds());
        return true;
    }

    private boolean handleTpAccept(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.tpaccept")) {
            return true;
        }

        Player target = requirePlayer(sender);
        if (target == null) {
            return true;
        }

        TeleportRequest request = teleportService.take(target.getUniqueId()).orElse(null);
        if (request == null) {
            message(sender, "teleport.request.none");
            return true;
        }

        Player requester = plugin.getServer().getPlayer(request.requesterUuid());
        if (requester == null) {
            message(sender, "teleport.request.offline");
            return true;
        }

        String targetName = target.getName();
        String requesterName = request.requesterName();
        UUID targetUuid = target.getUniqueId();
        plugin.messageNow(target, "teleport.request.accepted.target", requesterName);
        teleportExecutionService.teleportToPlayerWithRequestWarmup(
            requester,
            target,
            teleported -> plugin.messageNow(teleported, "teleport.request.accepted.requester", targetName),
            ignored -> {},
            ignored -> notifyTeleportRequestCanceled(targetUuid, requesterName, true),
            ignored -> notifyTeleportRequestCanceled(targetUuid, requesterName, false)
        );
        return true;
    }

    private boolean handleTpDeny(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.tpdeny")) {
            return true;
        }

        Player target = requirePlayer(sender);
        if (target == null) {
            return true;
        }

        TeleportRequest request = teleportService.take(target.getUniqueId()).orElse(null);
        if (request == null) {
            message(sender, "teleport.request.none");
            return true;
        }

        Player requester = plugin.getServer().getPlayer(request.requesterUuid());
        if (requester != null) {
            message(requester, "teleport.request.denied.requester", target.getName());
        }

        message(target, "teleport.request.denied.target");
        return true;
    }

    private void notifyTeleportRequestCanceled(UUID targetUuid, String requesterName, boolean moved) {
        Player target = plugin.getServer().getPlayer(targetUuid);
        if (target == null) {
            return;
        }

        plugin.message(
            target,
            moved ? "teleport.request.canceled.target-moved" : "teleport.request.canceled.target",
            requesterName
        );
    }

    private boolean handleBack(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.back")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        Location location = backService.peek(player.getUniqueId()).orElse(null);
        if (location == null || location.getWorld() == null) {
            message(sender, "teleport.back.missing");
            return true;
        }

        teleportExecutionService.teleportUnsafe(player, location, teleported -> message(teleported, "teleport.back.success"));
        return true;
    }

    private boolean handleTp(CommandSender sender, String[] args, boolean overrideToggle) {
        String permission = overrideToggle ? "kernel.command.tpo" : "kernel.command.tp";
        if (!requirePermission(sender, permission)) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (overrideToggle) {
            if (args.length != 1) {
                return false;
            }
            return teleportToPlayer(player, args[0], true, true);
        }

        if (args.length == 1) {
            Player target = resolveSingleTpPlayer(player, args[0]);
            return target == null || teleportToPlayer(player, target, false, true);
        }

        if (args.length == 2) {
            if (!requirePermission(sender, "kernel.command.tp.others")) {
                return true;
            }
            List<Player> targets = resolveTpPlayers(player, args[0]);
            if (targets.isEmpty()) {
                return true;
            }
            Player destination = resolveSingleTpPlayer(player, args[1]);
            if (destination == null) {
                return true;
            }
            if (stateCache.isTeleportBlocked(destination.getUniqueId())) {
                message(sender, "teleport.tp.blocked");
                return true;
            }
            for (Player target : targets) {
                teleportToPlayer(target, destination, false, true);
            }
            return true;
        }

        if (args.length == 3) {
            if (!requirePermission(sender, "kernel.command.tp.coordinates")) {
                return true;
            }
            return teleportToCoordinates(player, args, true);
        }

        if (args.length == 4) {
            if (!requirePermission(sender, "kernel.command.tp.others")) {
                return true;
            }
            if (!requirePermission(sender, "kernel.command.tp.coordinates")) {
                return true;
            }
            return teleportOtherToCoordinates(player, args, true);
        }

        return false;
    }

    private boolean handleTpPos(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.tppos")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length != 3) {
            return false;
        }

        return teleportToCoordinates(player, args, true);
    }

    private boolean handleTpHere(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.tphere")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            message(sender, "error.player-not-online");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            message(sender, "teleport.tphere.self");
            return true;
        }

        String senderName = player.getName();
        String targetName = target.getName();
        UUID senderUuid = player.getUniqueId();
        teleportExecutionService.teleportToPlayerUnsafe(target, player, teleported -> {
            message(teleported, "teleport.tphere.target", senderName);

            Player senderPlayer = plugin.getServer().getPlayer(senderUuid);
            if (senderPlayer != null) {
                message(senderPlayer, "teleport.tphere.sender", targetName);
            }
        });
        return true;
    }

    private boolean handleTpToggle(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.tptoggle")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        boolean enabled = !stateCache.isTeleportBlocked(player.getUniqueId());
        repository.updateTeleportBlocked(player.getUniqueId(), enabled);
        stateCache.setTeleportBlocked(player.getUniqueId(), enabled);

        message(sender, enabled ? "teleport.toggle.on" : "teleport.toggle.off");
        return true;
    }

    private boolean teleportToPlayer(Player player, String targetName, boolean overrideToggle, boolean ignoreDestinationSafety) {
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            message(player, "error.player-not-online");
            return true;
        }

        return teleportToPlayer(player, target, overrideToggle, ignoreDestinationSafety);
    }

    private List<Player> resolveTpPlayers(Player sender, String input) {
        List<Player> targets;
        if (input.startsWith("@")) {
            try {
                targets = plugin.getServer().selectEntities(sender, input).stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .toList();
            } catch (IllegalArgumentException exception) {
                message(sender, "teleport.tp.selector-invalid");
                return List.of();
            }
        } else {
            Player target = findOnlinePlayer(input);
            targets = target == null ? List.of() : List.of(target);
        }
        if (targets.isEmpty()) {
            message(sender, "error.player-not-online");
        }
        return targets;
    }

    private Player resolveSingleTpPlayer(Player sender, String input) {
        List<Player> targets = resolveTpPlayers(sender, input);
        if (targets.size() > 1) {
            message(sender, "teleport.tp.selector-single");
        }
        return targets.size() == 1 ? targets.getFirst() : null;
    }

    private boolean teleportToPlayer(Player player, Player target, boolean overrideToggle, boolean ignoreDestinationSafety) {

        if (target.getUniqueId().equals(player.getUniqueId())) {
            message(player, "teleport.tp.self");
            return true;
        }

        if (!overrideToggle && stateCache.isTeleportBlocked(target.getUniqueId())) {
            message(player, "teleport.tp.blocked");
            return true;
        }

        String resolvedTargetName = target.getName();
        var onSuccess = (java.util.function.Consumer<Player>) teleported -> message(
            teleported,
            "teleport.tp.player",
            resolvedTargetName
        );

        if (ignoreDestinationSafety) {
            teleportExecutionService.teleportToPlayerUnsafe(player, target, onSuccess);
        } else {
            teleportExecutionService.teleportToPlayer(player, target, onSuccess);
        }
        return true;
    }

    private boolean teleportToCoordinates(Player player, String[] args, boolean ignoreDestinationSafety) {
        Location location = coordinateLocation(player, player, args[0], args[1], args[2]);
        if (location == null) {
            message(player, "teleport.tp.coords.invalid");
            return true;
        }

        String x = formatCoordinate(location.getX());
        String y = formatCoordinate(location.getY());
        String z = formatCoordinate(location.getZ());

        var onSuccess = (java.util.function.Consumer<Player>) teleported -> message(
            teleported,
            "teleport.tp.coords.success",
            x,
            y,
            z
        );

        if (ignoreDestinationSafety) {
            teleportExecutionService.teleportUnsafe(player, location, onSuccess);
        } else {
            teleportExecutionService.teleport(player, location, onSuccess);
        }
        return true;
    }

    private boolean teleportOtherToCoordinates(Player sender, String[] args, boolean ignoreDestinationSafety) {
        for (Player target : resolveTpPlayers(sender, args[0])) {
            if (!teleportOtherToCoordinates(sender, target, args, ignoreDestinationSafety)) {
                break;
            }
        }
        return true;
    }

    private boolean teleportOtherToCoordinates(Player sender, Player target, String[] args, boolean ignoreDestinationSafety) {
        Location location = coordinateLocation(sender, target, args[1], args[2], args[3]);
        if (location == null) {
            message(sender, "teleport.tp.coords.invalid");
            return false;
        }

        String x = formatCoordinate(location.getX());
        String y = formatCoordinate(location.getY());
        String z = formatCoordinate(location.getZ());
        String targetName = target.getName();
        UUID senderUuid = sender.getUniqueId();
        var onSuccess = (java.util.function.Consumer<Player>) teleported -> {
            if (teleported.getUniqueId().equals(senderUuid)) {
                message(teleported, "teleport.tp.coords.success", x, y, z);
                return;
            }

            message(teleported, "teleport.tp.coords.other.target", x, y, z);

            Player senderPlayer = plugin.getServer().getPlayer(senderUuid);
            if (senderPlayer != null) {
                message(senderPlayer, "teleport.tp.coords.other.sender", targetName, x, y, z);
            }
        };

        if (ignoreDestinationSafety) {
            teleportExecutionService.teleportUnsafe(target, location, onSuccess);
        } else {
            teleportExecutionService.teleport(target, location, onSuccess);
        }
        return true;
    }

    private Location coordinateLocation(Player sourcePlayer, Player targetPlayer, String x, String y, String z) {
        World world = sourcePlayer.getWorld();
        Location sourceLocation = sourcePlayer.getLocation();
        Location targetLocation = targetPlayer.getLocation();
        Double resolvedX = resolveCoordinate(sourceLocation.getX(), x);
        Double resolvedY = resolveCoordinate(sourceLocation.getY(), y);
        Double resolvedZ = resolveCoordinate(sourceLocation.getZ(), z);
        if (resolvedX == null || resolvedY == null || resolvedZ == null) {
            return null;
        }

        return new Location(
            world,
            resolvedX,
            resolvedY,
            resolvedZ,
            targetLocation.getYaw(),
            targetLocation.getPitch()
        );
    }

    private Double resolveCoordinate(double base, String input) {
        try {
            if (input.startsWith("~")) {
                if (input.length() == 1) {
                    return base;
                }

                return base + Double.parseDouble(input.substring(1));
            }

            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean looksLikeCoordinateToken(String input) {
        if (input.isEmpty()) {
            return false;
        }

        char first = input.charAt(0);
        return first == '~' || first == '-' || first == '+' || first == '.' || Character.isDigit(first);
    }

    private String formatCoordinate(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
