package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.data.KernelRepository.NicknameUpdateResult;
import com.earthpol.kernel.data.model.PlayerLookup;
import com.earthpol.kernel.data.model.StoredPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CommunicationCommands extends BaseCommandHandler {

    private static final DateTimeFormatter SEEN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CommunicationCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "msg" -> handleMessage(sender, args);
            case "r" -> handleReply(sender, args);
            case "ignore" -> handleIgnore(sender, args);
            case "mail" -> handleMail(sender, args);
            case "nick" -> handleNick(sender, args);
            case "seen" -> handleSeen(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        return switch (commandName) {
            case "msg", "ignore", "seen" -> args.length == 1 ? completeOnlinePlayers(args[0]) : Collections.emptyList();
            case "nick" -> completeNickTargets(sender, args);
            case "mail" -> {
                if (args.length == 1) {
                    yield completeOptions(args[0], List.of("send"));
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
                    yield completeOnlinePlayers(args[1]);
                }
                yield Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    private boolean handleMessage(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.msg")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            message(sender, "error.player-not-online");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            message(sender, "communication.msg.self");
            return true;
        }

        if (repository.isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            message(sender, "error.player-ignoring-you");
            return true;
        }

        if (repository.isIgnoring(player.getUniqueId(), target.getUniqueId())) {
            message(sender, "error.you-ignore-player");
            return true;
        }

        String message = join(args, 1);
        String targetName = target.getName();
        String senderName = player.getName();
        rawMessage(player, "communication.msg.outgoing", targetName, message);
        rawMessage(target, "communication.msg.incoming", senderName, message);
        conversationService.link(player.getUniqueId(), senderName, target.getUniqueId(), targetName);
        socialSpyService.spyDirectMessage(player.getUniqueId(), senderName, target.getUniqueId(), targetName, message);
        return true;
    }

    private boolean handleReply(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.reply")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        var partner = conversationService.partnerOf(player.getUniqueId()).orElse(null);
        Player target = Optional.ofNullable(partner)
            .map(value -> plugin.getServer().getPlayer(value.uuid()))
            .orElse(null);

        if (target == null) {
            message(sender, "communication.reply.none");
            return true;
        }

        if (repository.isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            message(sender, "error.player-ignoring-you");
            return true;
        }

        if (repository.isIgnoring(player.getUniqueId(), target.getUniqueId())) {
            message(sender, "error.you-ignore-player");
            return true;
        }

        String message = join(args, 0);
        String targetName = target.getName();
        String senderName = player.getName();
        rawMessage(player, "communication.msg.outgoing", targetName, message);
        rawMessage(target, "communication.msg.incoming", senderName, message);
        conversationService.link(player.getUniqueId(), senderName, target.getUniqueId(), targetName);
        socialSpyService.spyDirectMessage(player.getUniqueId(), senderName, target.getUniqueId(), targetName, message);
        return true;
    }

    private boolean handleIgnore(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.ignore")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        PlayerLookup lookup = resolveKnownPlayer(args[0]);
        if (lookup == null) {
            message(sender, "error.player-not-seen");
            return true;
        }

        if (lookup.uuid().equals(player.getUniqueId())) {
            message(sender, "communication.ignore.self");
            return true;
        }

        boolean ignored = repository.toggleIgnore(player.getUniqueId(), lookup.uuid());
        message(sender, ignored ? "communication.ignore.enabled" : "communication.ignore.disabled", lookup.name());
        return true;
    }

    private boolean handleMail(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.mail")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("send")) {
            return false;
        }

        PlayerLookup lookup = resolveKnownPlayer(args[1]);
        if (lookup == null) {
            message(sender, "error.player-not-seen");
            return true;
        }

        if (lookup.uuid().equals(player.getUniqueId())) {
            message(sender, "communication.mail.self");
            return true;
        }

        if (repository.isIgnoring(lookup.uuid(), player.getUniqueId())) {
            message(sender, "error.player-ignoring-you");
            return true;
        }

        if (repository.isIgnoring(player.getUniqueId(), lookup.uuid())) {
            message(sender, "error.you-ignore-player");
            return true;
        }

        String message = join(args, 2);
        long mailId = repository.saveMail(lookup.uuid(), player.getUniqueId(), player.getName(), message);
        Player onlineTarget = plugin.getServer().getPlayer(lookup.uuid());

        if (onlineTarget != null) {
            rawMessage(onlineTarget, "communication.mail.incoming-live", player.getName(), message);
            repository.markMailDelivered(List.of(mailId));
        }

        socialSpyService.spyMail(player.getUniqueId(), lookup.uuid(), player.getName(), lookup.name(), message);
        message(sender, "communication.mail.sent", lookup.name());
        return true;
    }

    private boolean handleNick(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.nick")) {
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        if (args.length >= 2) {
            Player target = findOnlinePlayer(args[0]);
            if (target != null) {
                if (!requirePermission(sender, "kernel.command.nick.others")) {
                    return true;
                }
                return updateNickname(sender, target, join(args, 1).trim());
            }

            if (!(sender instanceof Player)) {
                message(sender, "error.player-not-online");
                return true;
            }
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        return updateNickname(sender, player, join(args, 0).trim());
    }

    private boolean updateNickname(CommandSender sender, Player target, String value) {
        if (value.length() > settings.nicknameMaxLength()) {
            message(sender, "communication.nick.too-long", settings.nicknameMaxLength());
            return true;
        }

        boolean selfTarget = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        boolean cleared = value.equalsIgnoreCase("clear") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("reset");
        String normalizedValue = cleared ? null : value;
        String targetName = target.getName();

        NicknameUpdateResult updateResult = repository.updateNickname(target.getUniqueId(), normalizedValue);
        if (updateResult == NicknameUpdateResult.CONFLICTS_WITH_PLAYER_NAME) {
            message(sender, "communication.nick.matches-player-name");
            return true;
        }
        if (updateResult == NicknameUpdateResult.CONFLICTS_WITH_OTHER_NICKNAME) {
            message(sender, "communication.nick.in-use");
            return true;
        }

        stateCache.setNickname(target.getUniqueId(), normalizedValue);

        if (selfTarget) {
            plugin.applyStoredState(target);
            if (cleared) {
                message(sender, "communication.nick.cleared");
            } else {
                message(sender, "communication.nick.set", value);
            }
            return true;
        }

        runFor(target, () -> {
            plugin.applyStoredState(target);
            if (cleared) {
                message(target, "communication.nick.cleared");
            } else {
                message(target, "communication.nick.set", value);
            }
        });
        if (cleared) {
            message(sender, "communication.nick.other.cleared", targetName);
        } else {
            message(sender, "communication.nick.other.set", targetName, value);
        }
        return true;
    }

    private List<String> completeNickTargets(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = completeOptions(args[0], List.of("clear", "off", "reset"));
            if (sender.hasPermission("kernel.command.nick.others")) {
                options.addAll(completeOnlinePlayers(args[0]));
            }
            return options.stream().distinct().toList();
        }

        if (args.length == 2 && sender.hasPermission("kernel.command.nick.others") && findOnlinePlayer(args[0]) != null) {
            return completeOptions(args[1], List.of("clear", "off", "reset"));
        }

        return Collections.emptyList();
    }

    private boolean handleSeen(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.seen")) {
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        Player online = findOnlinePlayer(args[0]);
        PlayerLookup lookup = repository.findPlayerByName(args[0]).orElse(null);

        if (online != null) {
            String playerName = lookup == null ? args[0] : lookup.name();
            message(sender, "communication.seen.online", playerName);
            sendSeenIp(sender, lookup);
            return true;
        }

        if (lookup == null) {
            message(sender, "error.player-not-seen");
            return true;
        }

        StoredPlayer storedPlayer = repository.findPlayer(lookup.uuid()).orElse(null);
        if (storedPlayer == null || storedPlayer.lastSeenEpochMillis() <= 0L) {
            message(sender, "communication.seen.none");
            return true;
        }

        Instant seenAt = Instant.ofEpochMilli(storedPlayer.lastSeenEpochMillis());
        String timestamp = SEEN_FORMAT.format(seenAt.atZone(ZoneId.systemDefault()));
        message(sender, "communication.seen.result", lookup.name(), timestamp, relativeTime(sender, seenAt));
        sendSeenIp(sender, lookup, storedPlayer);
        return true;
    }

    private String relativeTime(CommandSender sender, Instant instant) {
        Duration duration = Duration.between(instant, Instant.now()).abs();

        if (duration.toMinutes() < 1) {
            return text(sender, "time.moments");
        }
        if (duration.toHours() < 1) {
            return text(sender, "time.minutes", duration.toMinutes());
        }
        if (duration.toDays() < 1) {
            return text(sender, "time.hours", duration.toHours());
        }
        return text(sender, "time.days", duration.toDays());
    }

    private void sendSeenIp(CommandSender sender, PlayerLookup lookup) {
        if (lookup == null) {
            return;
        }

        StoredPlayer storedPlayer = repository.findPlayer(lookup.uuid()).orElse(null);
        sendSeenIp(sender, lookup, storedPlayer);
    }

    private void sendSeenIp(CommandSender sender, PlayerLookup lookup, StoredPlayer storedPlayer) {
        if (lookup == null || storedPlayer == null || !sender.hasPermission("kernel.command.seen.ip")) {
            return;
        }

        if (storedPlayer.lastKnownIp() == null || storedPlayer.lastKnownIp().isBlank()) {
            return;
        }

        rawMessage(sender, "communication.seen.ip", storedPlayer.lastKnownIp());
    }
}
