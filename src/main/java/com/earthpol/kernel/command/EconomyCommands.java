package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.data.model.BalanceEntry;
import com.earthpol.kernel.data.model.BalanceOperation;
import com.earthpol.kernel.data.model.BalanceTransfer;
import com.earthpol.kernel.data.model.PlayerLookup;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EconomyCommands extends BaseCommandHandler {

    public EconomyCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> handleBalance(sender, args);
            case "pay" -> handlePay(sender, args);
            case "baltop" -> handleBalTop(sender);
            case "eco" -> handleEconomy(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        return switch (commandName) {
            case "bal", "pay" -> args.length == 1 ? completeOnlinePlayers(args[0]) : Collections.emptyList();
            case "eco" -> {
                if (args.length == 1) {
                    yield completeOptions(args[0], List.of("give", "take", "set"));
                }
                if (args.length == 2) {
                    yield completeOnlinePlayers(args[1]);
                }
                yield Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.bal")) {
            return true;
        }

        PlayerLookup lookup;
        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return true;
            }
            repository.ensurePlayer(player);
            lookup = new PlayerLookup(player.getUniqueId(), player.getName());
        } else {
            if (!requirePermission(sender, "kernel.command.bal.others")) {
                return true;
            }

            lookup = resolveKnownPlayer(args[0]);
            if (lookup == null) {
                message(sender, "error.player-not-seen");
                return true;
            }
        }

        String balance = plugin.economyBridge() == null
            ? settings.format(repository.getBalance(lookup.uuid()))
            : plugin.economyBridge().formatBalance(lookup.uuid());
        message(sender, "economy.balance.result", lookup.name(), balance);
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.pay")) {
            return true;
        }
        if (deferToExternalEconomy(sender)) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length != 2) {
            return false;
        }

        PlayerLookup recipient = resolveKnownPlayer(args[0]);
        if (recipient == null) {
            message(sender, "error.player-not-seen");
            return true;
        }

        if (recipient.uuid().equals(player.getUniqueId())) {
            message(sender, "economy.pay.self");
            return true;
        }

        BigDecimal amount = parseAmount(args[1]);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            message(sender, "economy.error.positive");
            return true;
        }

        repository.ensurePlayer(player);
        BalanceTransfer transfer = repository.transfer(player.getUniqueId(), recipient.uuid(), amount);
        if (!transfer.success()) {
            message(sender, failureKey(transfer.message()));
            return true;
        }

        message(sender, "economy.pay.sent", settings.format(transfer.amount()), recipient.name());
        Player onlineRecipient = plugin.getServer().getPlayer(recipient.uuid());
        if (onlineRecipient != null) {
            message(onlineRecipient, "economy.pay.received", settings.format(transfer.amount()), player.getName());
        }
        return true;
    }

    private boolean handleBalTop(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.baltop")) {
            return true;
        }
        if (deferToExternalEconomy(sender)) {
            return true;
        }

        List<BalanceEntry> entries = repository.topBalances(settings.baltopSize());
        if (entries.isEmpty()) {
            message(sender, "economy.baltop.none");
            return true;
        }

        message(sender, "economy.baltop.header");
        for (int index = 0; index < entries.size(); index++) {
            BalanceEntry entry = entries.get(index);
            rawMessage(sender, "economy.baltop.entry", index + 1, entry.playerName(), settings.format(entry.balance()));
        }
        return true;
    }

    private boolean handleEconomy(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.eco")) {
            return true;
        }
        if (deferToExternalEconomy(sender)) {
            return true;
        }

        if (args.length != 3) {
            return false;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("give", "take", "set").contains(subcommand)) {
            return false;
        }

        if (!requirePermission(sender, "kernel.command.eco." + subcommand)) {
            return true;
        }

        PlayerLookup lookup = resolveKnownPlayer(args[1]);
        if (lookup == null) {
            message(sender, "error.player-not-seen");
            return true;
        }

        BigDecimal amount = parseAmount(args[2]);
        if (amount == null) {
            message(sender, "economy.error.numeric");
            return true;
        }

        BalanceOperation operation = switch (subcommand) {
            case "give" -> repository.deposit(lookup.uuid(), amount);
            case "take" -> repository.withdraw(lookup.uuid(), amount);
            case "set" -> {
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    message(sender, "economy.error.negative-balance");
                    yield null;
                }
                yield repository.setBalance(lookup.uuid(), amount);
            }
            default -> null;
        };

        if (operation == null) {
            return true;
        }

        if (!operation.success()) {
            message(sender, failureKey(operation.message()));
            return true;
        }

        message(sender, "economy.eco.updated", lookup.name(), settings.format(operation.balance()));
        Player onlineTarget = plugin.getServer().getPlayer(lookup.uuid());
        if (onlineTarget != null) {
            message(onlineTarget, "economy.eco.updated-notify", settings.format(operation.balance()));
        }
        return true;
    }

    private boolean deferToExternalEconomy(CommandSender sender) {
        String provider = plugin.economyBridge() == null ? null : plugin.economyBridge().externalProviderName();
        if (provider == null) {
            return false;
        }
        message(sender, "economy.external-provider", provider);
        return true;
    }

    private String failureKey(String message) {
        return switch (message) {
            case "Insufficient funds." -> "economy.error.insufficient-funds";
            case "Account not found." -> "economy.error.account-not-found";
            default -> "economy.error.generic";
        };
    }
}
