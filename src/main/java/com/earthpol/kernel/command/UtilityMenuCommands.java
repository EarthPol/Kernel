package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UtilityMenuCommands extends BaseCommandHandler {

    public UtilityMenuCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (!commandName.equals("enderchest") && args.length != 0) {
            return false;
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "hat" -> wearHat(player);
            case "workbench" -> openWorkbench(player);
            case "stonecutter" -> openStonecutter(player);
            case "cartographytable" -> openCartographyTable(player);
            case "smithingtable" -> openSmithingTable(player);
            case "grindstone" -> openGrindstone(player);
            case "loom" -> openLoom(player);
            case "enderchest" -> openEnderChest(player, args);
            case "anvil" -> openAnvil(player);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("enderchest")
            && args.length == 1
            && sender.hasPermission("kernel.command.enderchest.others")) {
            return completeOnlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }

    private boolean wearHat(Player player) {
        if (!requirePermission(player, "kernel.command.hat")) {
            return true;
        }

        runFor(player, () -> {
            PlayerInventory inventory = player.getInventory();
            ItemStack handItem = inventory.getItemInMainHand();
            if (handItem.getType().isAir()) {
                message(player, "utility.hat.empty-hand");
                return;
            }

            ItemStack equippedHelmet = inventory.getHelmet();
            ItemStack newHelmet = handItem.clone();
            newHelmet.setAmount(1);

            if (handItem.getAmount() == 1) {
                inventory.setItemInMainHand(equippedHelmet == null ? new ItemStack(Material.AIR) : equippedHelmet);
            } else {
                handItem.setAmount(handItem.getAmount() - 1);
                inventory.setItemInMainHand(handItem);

                if (equippedHelmet != null && !equippedHelmet.getType().isAir()) {
                    Map<Integer, ItemStack> leftovers = inventory.addItem(equippedHelmet);
                    leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
            }

            inventory.setHelmet(newHelmet);
            message(player, "utility.hat.success");
        });
        return true;
    }

    private boolean openWorkbench(Player player) {
        if (!requirePermission(player, "kernel.command.workbench")) {
            return true;
        }

        runFor(player, () -> player.openWorkbench(null, true));
        return true;
    }

    private boolean openStonecutter(Player player) {
        if (!requirePermission(player, "kernel.command.stonecutter")) {
            return true;
        }

        runFor(player, () -> player.openStonecutter(null, true));
        return true;
    }

    private boolean openCartographyTable(Player player) {
        if (!requirePermission(player, "kernel.command.cartographytable")) {
            return true;
        }

        runFor(player, () -> player.openCartographyTable(null, true));
        return true;
    }

    private boolean openSmithingTable(Player player) {
        if (!requirePermission(player, "kernel.command.smithingtable")) {
            return true;
        }

        runFor(player, () -> player.openSmithingTable(null, true));
        return true;
    }

    private boolean openGrindstone(Player player) {
        if (!requirePermission(player, "kernel.command.grindstone")) {
            return true;
        }

        runFor(player, () -> player.openGrindstone(null, true));
        return true;
    }

    private boolean openLoom(Player player) {
        if (!requirePermission(player, "kernel.command.loom")) {
            return true;
        }

        runFor(player, () -> player.openLoom(null, true));
        return true;
    }

    private boolean openEnderChest(Player player, String[] args) {
        if (!requirePermission(player, "kernel.command.enderchest")) {
            return true;
        }

        if (args.length == 0) {
            runFor(player, () -> player.openInventory(player.getEnderChest()));
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        Player target = findOnlinePlayer(args[0]);
        if (target == null) {
            message(player, "error.player-not-online");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            runFor(player, () -> player.openInventory(player.getEnderChest()));
            return true;
        }

        if (!requirePermission(player, "kernel.command.enderchest.others")) {
            return true;
        }

        boolean mutable = player.hasPermission("kernel.command.enderchest.modify.others");
        plugin.enderChestViewListener().open(player, target, mutable);
        return true;
    }

    private boolean openAnvil(Player player) {
        if (!requirePermission(player, "kernel.command.anvil")) {
            return true;
        }

        runFor(player, () -> player.openAnvil(null, true));
        return true;
    }
}
