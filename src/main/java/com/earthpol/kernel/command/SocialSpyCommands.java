package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class SocialSpyCommands extends BaseCommandHandler {

    public SocialSpyCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePermission(sender, "kernel.command.socialspy")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 0) {
            return false;
        }

        boolean enabled = socialSpyService.toggle(player.getUniqueId());
        message(player, enabled ? "socialspy.enabled" : "socialspy.disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
