package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.data.model.HomeRecord;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HomeCommands extends BaseCommandHandler {

    public HomeCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "home" -> handleHome(sender, args);
            case "sethome" -> handleSetHome(sender, args);
            case "delhome" -> handleDeleteHome(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return Collections.emptyList();
        }

        return completeOptions(args[0], repository.listHomes(player.getUniqueId()));
    }

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.home")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String homeName = args.length == 0 ? settings.defaultHomeName() : settings.normalizeHomeName(args[0]);
        HomeRecord homeRecord = repository.findHome(player.getUniqueId(), homeName).orElse(null);

        if (homeRecord == null) {
            message(sender, "home.missing", homeName);
            return true;
        }

        Location location = homeRecord.toLocation();
        if (location == null) {
            message(sender, "home.world-unloaded");
            return true;
        }

        teleportExecutionService.teleport(player, location, teleported -> message(teleported, "home.teleported", homeName));
        return true;
    }

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.sethome")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String homeName = args.length == 0 ? settings.defaultHomeName() : settings.normalizeHomeName(args[0]);
        repository.upsertHome(player.getUniqueId(), homeName, player.getLocation());
        message(sender, "home.set", homeName);
        return true;
    }

    private boolean handleDeleteHome(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.delhome")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String homeName = args.length == 0 ? settings.defaultHomeName() : settings.normalizeHomeName(args[0]);
        if (!repository.deleteHome(player.getUniqueId(), homeName)) {
            message(sender, "home.missing", homeName);
            return true;
        }

        message(sender, "home.deleted", homeName);
        return true;
    }
}
