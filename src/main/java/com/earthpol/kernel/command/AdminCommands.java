package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AdminCommands extends BaseCommandHandler {

    public AdminCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sudo" -> handleSudo(sender, args);
            case "smite", "lightning" -> handleSmite(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("sudo") && args.length == 1) {
            List<String> options = completeOnlinePlayers(args[0]);
            options.addAll(completeOptions(args[0], List.of("*")));
            return options.stream().distinct().toList();
        }

        if ((commandName.equals("smite") || commandName.equals("lightning")) && args.length == 1) {
            return completeOnlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }

    private boolean handleSudo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.sudo")) {
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String forcedCommand = normalizeCommand(join(args, 1));
        if (forcedCommand.isBlank()) {
            return false;
        }

        boolean consoleOverride = sender instanceof ConsoleCommandSender;
        if (args[0].equals("*")) {
            Collection<Player> targets = resolveWildcardTargets(consoleOverride);
            if (targets.isEmpty()) {
                message(sender, Bukkit.getOnlinePlayers().isEmpty() ? "admin.sudo.none" : "admin.sudo.all-exempt");
                return true;
            }

            for (Player target : targets) {
                runFor(target, () -> target.performCommand(forcedCommand));
            }
            message(sender, "admin.sudo.executed-all", targets.size(), forcedCommand);
            return true;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            message(sender, "admin.sudo.none");
            return true;
        }

        if (!consoleOverride && isSudoExempt(target)) {
            message(sender, "admin.sudo.exempt", target.getName());
            return true;
        }

        runFor(target, () -> target.performCommand(forcedCommand));
        message(sender, "admin.sudo.executed", target.getName(), forcedCommand);
        return true;
    }

    private boolean handleSmite(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.smite")) {
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

        String targetName = target.getName();
        runFor(target, () -> {
            Location location = target.getLocation().clone();
            target.getWorld().strikeLightning(location);
        });
        message(sender, "admin.smite.success", targetName);
        return true;
    }

    private Collection<Player> resolveWildcardTargets(boolean consoleOverride) {
        if (consoleOverride) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }

        return new ArrayList<>(Bukkit.getOnlinePlayers().stream()
            .filter(target -> !isSudoExempt(target))
            .toList());
    }

    private boolean isSudoExempt(Player target) {
        return target.hasPermission("kernel.command.sudo.exempt");
    }

    private String normalizeCommand(String input) {
        String normalized = input.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }
}
