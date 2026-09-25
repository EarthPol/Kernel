package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class KernelCommands extends BaseCommandHandler {

    public KernelCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("kernel")) {
            return false;
        }

        if (!requirePermission(sender, "kernel.command.kernel")) {
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            return false;
        }

        if (!requirePermission(sender, "kernel.command.kernel.reload")) {
            return true;
        }

        message(sender, "kernel.reload.started");
        plugin.reloadRuntime(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("kernel")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return completeOptions(args[0].toLowerCase(Locale.ROOT), List.of("reload"));
        }

        return Collections.emptyList();
    }
}
