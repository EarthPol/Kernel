package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.KernelRepository;
import com.earthpol.kernel.data.model.PlayerLookup;
import com.earthpol.kernel.service.BackService;
import com.earthpol.kernel.service.ConversationService;
import com.earthpol.kernel.service.PlayerStateCache;
import com.earthpol.kernel.service.SocialSpyService;
import com.earthpol.kernel.service.TeleportExecutionService;
import com.earthpol.kernel.service.TeleportService;
import com.europamc.europalib.entity.EntitySchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public abstract class BaseCommandHandler implements TabExecutor {

    protected final Kernel plugin;
    protected final KernelRepository repository;
    protected final KernelSettings settings;
    protected final BackService backService;
    protected final TeleportService teleportService;
    protected final TeleportExecutionService teleportExecutionService;
    protected final ConversationService conversationService;
    protected final PlayerStateCache stateCache;
    protected final SocialSpyService socialSpyService;

    protected BaseCommandHandler(Kernel plugin) {
        this.plugin = plugin;
        this.repository = plugin.repository();
        this.settings = plugin.settings();
        this.backService = plugin.backService();
        this.teleportService = plugin.teleportService();
        this.teleportExecutionService = plugin.teleportExecutionService();
        this.conversationService = plugin.conversationService();
        this.stateCache = plugin.stateCache();
        this.socialSpyService = plugin.socialSpyService();
    }

    protected boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }

        plugin.message(sender, "error.no-permission");
        return false;
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }

        plugin.message(sender, "error.player-only");
        return null;
    }

    protected void message(CommandSender sender, String key, Object... args) {
        plugin.message(sender, key, args);
    }

    protected void rawMessage(CommandSender sender, String key, Object... args) {
        plugin.rawMessage(sender, key, args);
    }

    protected String text(CommandSender sender, String key, Object... args) {
        return plugin.text(sender, key, args);
    }

    protected void runFor(Player player, Runnable task) {
        EntitySchedulerUtil.run(plugin, player, task);
    }

    protected void runLaterFor(Player player, long delayTicks, Runnable task) {
        EntitySchedulerUtil.runDelayed(plugin, player, delayTicks, task);
    }

    protected Player findOnlinePlayer(String input) {
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }

        return Bukkit.getPlayer(input);
    }

    protected PlayerLookup resolveKnownPlayer(String input) {
        PlayerLookup lookup = repository.findPlayerByName(input).orElse(null);
        if (lookup != null) {
            return lookup;
        }

        Player online = findOnlinePlayer(input);
        if (online == null) {
            return null;
        }

        repository.ensurePlayer(online);
        return new PlayerLookup(online.getUniqueId(), online.getName());
    }

    protected BigDecimal parseAmount(String input) {
        try {
            return settings.normalizeAmount(new BigDecimal(input));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    protected String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    protected List<String> completeOnlinePlayers(String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    protected List<String> completeOptions(String prefix, Collection<String> options) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(option);
            }
        }

        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    protected GameMode parseGameMode(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    protected boolean isNumeric(String input) {
        try {
            Double.parseDouble(input);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
