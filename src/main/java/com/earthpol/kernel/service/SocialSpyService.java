package com.earthpol.kernel.service;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.config.KernelSettings;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialSpyService {

    public static final String PERMISSION = "kernel.command.socialspy";

    private static final Set<String> MESSAGE_COMMANDS = Set.of(
        "msg", "tell", "whisper", "w", "r", "reply", "mail"
    );

    private final Kernel plugin;
    private final boolean spyAllCommands;
    private final Set<String> watchedCommands;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    public SocialSpyService(Kernel plugin, KernelSettings settings) {
        this(plugin, settings, Set.of());
    }

    public SocialSpyService(Kernel plugin, KernelSettings settings, Collection<UUID> enabledPlayers) {
        this.plugin = plugin;

        Set<String> normalizedCommands = ConcurrentHashMap.newKeySet();
        boolean allCommands = false;
        for (String configuredCommand : settings.socialSpyCommands()) {
            String normalized = normalizeCommand(configuredCommand);
            if (normalized == null) {
                continue;
            }
            if (normalized.equals("*")) {
                allCommands = true;
                continue;
            }
            normalizedCommands.add(normalized);
        }

        this.spyAllCommands = allCommands;
        this.watchedCommands = Set.copyOf(normalizedCommands);
        this.enabledPlayers.addAll(enabledPlayers);
    }

    public boolean toggle(UUID uuid) {
        if (enabledPlayers.remove(uuid)) {
            return false;
        }

        enabledPlayers.add(uuid);
        return true;
    }

    public void disable(UUID uuid) {
        enabledPlayers.remove(uuid);
    }

    public Set<UUID> enabledPlayers() {
        return Set.copyOf(enabledPlayers);
    }

    public boolean shouldSpyCommand(String rawCommandLine) {
        String commandName = extractCommandName(rawCommandLine);
        if (commandName == null || MESSAGE_COMMANDS.contains(commandName)) {
            return false;
        }

        return spyAllCommands || watchedCommands.contains(commandName);
    }

    public void spyCommand(Player actor, String rawCommandLine) {
        dispatch(Set.of(actor.getUniqueId()), "socialspy.command", actor.getName(), rawCommandLine);
    }

    public void spyDirectMessage(
        UUID senderUuid,
        String senderName,
        UUID targetUuid,
        String targetName,
        String message
    ) {
        dispatch(Set.of(senderUuid, targetUuid), "socialspy.message", senderName, targetName, message);
    }

    public void spyMail(
        UUID senderUuid,
        UUID targetUuid,
        String senderName,
        String targetName,
        String message
    ) {
        dispatch(Set.of(senderUuid, targetUuid), "socialspy.mail", senderName, targetName, message);
    }

    private void dispatch(Set<UUID> excludedPlayers, String key, Object... args) {
        if (enabledPlayers.isEmpty()) {
            return;
        }

        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = onlinePlayer.getUniqueId();
            if (!enabledPlayers.contains(uuid) || excludedPlayers.contains(uuid) || !onlinePlayer.hasPermission(PERMISSION)) {
                continue;
            }

            plugin.rawMessage(onlinePlayer, key, args);
        }
    }

    private String extractCommandName(String rawCommandLine) {
        if (rawCommandLine == null || rawCommandLine.isBlank()) {
            return null;
        }

        String withoutSlash = rawCommandLine.startsWith("/") ? rawCommandLine.substring(1) : rawCommandLine;
        int separatorIndex = withoutSlash.indexOf(' ');
        String commandName = separatorIndex >= 0 ? withoutSlash.substring(0, separatorIndex) : withoutSlash;
        return normalizeCommand(commandName);
    }

    private String normalizeCommand(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? null : normalized;
    }
}
