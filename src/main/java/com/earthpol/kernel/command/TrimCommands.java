package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TrimCommands extends BaseCommandHandler {

    private static final List<ArmorSlot> ARMOR_SLOTS = List.of(
        ArmorSlot.HEAD,
        ArmorSlot.CHEST,
        ArmorSlot.LEGS,
        ArmorSlot.FEET
    );

    public TrimCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePermission(sender, "kernel.command.trim")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length != 2 && args.length != 3) {
            return false;
        }

        TrimPattern pattern = resolvePattern(args[0]);
        if (pattern == null) {
            message(player, "trim.invalid.type");
            return true;
        }

        if (!requirePermission(player, "kernel.command.trim.type." + registryKey(pattern, Registry.TRIM_PATTERN))) {
            return true;
        }

        TrimMaterial material = resolveMaterial(args[1]);
        if (material == null) {
            message(player, "trim.invalid.material");
            return true;
        }

        if (!requirePermission(player, "kernel.command.trim.material." + registryKey(material, Registry.TRIM_MATERIAL))) {
            return true;
        }

        ArmorSlot slot = args.length == 3 ? ArmorSlot.match(args[2]) : null;
        if (args.length == 3 && slot == null) {
            message(player, "trim.invalid.slot");
            return true;
        }

        if (slot != null && !requirePermission(player, "kernel.command.trim.slot." + slot.permissionKey())) {
            return true;
        }

        ArmorTrim trim = new ArmorTrim(material, pattern);
        if (slot != null) {
            applyTrimToSlot(player, slot, trim);
            return true;
        }

        List<ArmorSlot> allowedSlots = permittedSlots(player);
        if (allowedSlots.isEmpty()) {
            message(player, "error.no-permission");
            return true;
        }

        applyTrimToAll(player, trim, allowedSlots);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("kernel.command.trim")) {
            return Collections.emptyList();
        }

        return switch (args.length) {
            case 1 -> completeOptions(args[0], availablePatterns(sender));
            case 2 -> completeOptions(args[1], availableMaterials(sender));
            case 3 -> completeOptions(args[2], availableSlots(sender));
            default -> Collections.emptyList();
        };
    }

    private void applyTrimToSlot(Player player, ArmorSlot slot, ArmorTrim trim) {
        runFor(player, () -> {
            PlayerInventory inventory = player.getInventory();
            ItemStack item = inventory.getItem(slot.equipmentSlot());
            if (!canTrim(item)) {
                message(player, "trim.slot.missing", slot.displayName());
                return;
            }

            setTrim(item, trim);
            inventory.setItem(slot.equipmentSlot(), item);
            message(
                player,
                "trim.slot.applied",
                displayName(registryKey(trim.getPattern(), Registry.TRIM_PATTERN)),
                displayName(registryKey(trim.getMaterial(), Registry.TRIM_MATERIAL)),
                slot.displayName()
            );
        });
    }

    private void applyTrimToAll(Player player, ArmorTrim trim, List<ArmorSlot> allowedSlots) {
        runFor(player, () -> {
            PlayerInventory inventory = player.getInventory();
            int applied = 0;

            for (ArmorSlot slot : allowedSlots) {
                ItemStack item = inventory.getItem(slot.equipmentSlot());
                if (!canTrim(item)) {
                    continue;
                }

                setTrim(item, trim);
                inventory.setItem(slot.equipmentSlot(), item);
                applied++;
            }

            if (applied == 0) {
                message(player, "trim.none-equipped");
                return;
            }

            message(
                player,
                "trim.all.applied",
                displayName(registryKey(trim.getPattern(), Registry.TRIM_PATTERN)),
                displayName(registryKey(trim.getMaterial(), Registry.TRIM_MATERIAL)),
                applied
            );
        });
    }

    private boolean canTrim(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        return item.getItemMeta() instanceof ArmorMeta;
    }

    private void setTrim(ItemStack item, ArmorTrim trim) {
        ItemMeta itemMeta = Objects.requireNonNull(item.getItemMeta(), "Missing item meta");
        ArmorMeta armorMeta = (ArmorMeta) itemMeta;
        armorMeta.setTrim(trim);
        item.setItemMeta(armorMeta);
    }

    private TrimPattern resolvePattern(String input) {
        return Registry.TRIM_PATTERN.get(resolveRegistryKey(input));
    }

    private TrimMaterial resolveMaterial(String input) {
        return Registry.TRIM_MATERIAL.get(resolveRegistryKey(input));
    }

    private List<String> availablePatterns(CommandSender sender) {
        return Registry.TRIM_PATTERN.keyStream()
            .map(NamespacedKey::getKey)
            .filter(key -> sender.hasPermission("kernel.command.trim.type.*")
                || sender.hasPermission("kernel.command.trim.type." + key))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<String> availableMaterials(CommandSender sender) {
        return Registry.TRIM_MATERIAL.keyStream()
            .map(NamespacedKey::getKey)
            .filter(key -> sender.hasPermission("kernel.command.trim.material.*")
                || sender.hasPermission("kernel.command.trim.material." + key))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<String> availableSlots(CommandSender sender) {
        return permittedSlots(sender).stream()
            .map(ArmorSlot::permissionKey)
            .filter(key -> sender.hasPermission("kernel.command.trim.slot.*")
                || sender.hasPermission("kernel.command.trim.slot." + key))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<ArmorSlot> permittedSlots(CommandSender sender) {
        return ARMOR_SLOTS.stream()
            .filter(slot -> sender.hasPermission("kernel.command.trim.slot.*")
                || sender.hasPermission("kernel.command.trim.slot." + slot.permissionKey()))
            .toList();
    }

    private String displayName(String key) {
        String[] parts = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private NamespacedKey resolveRegistryKey(String input) {
        NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT));
        if (key != null) {
            return key;
        }

        return NamespacedKey.minecraft(input.toLowerCase(Locale.ROOT));
    }

    private <T extends org.bukkit.Keyed> String registryKey(T value, Registry<T> registry) {
        return Objects.requireNonNull(registry.getKey(value), "Missing registry key").getKey();
    }

    private enum ArmorSlot {
        HEAD(EquipmentSlot.HEAD),
        CHEST(EquipmentSlot.CHEST),
        LEGS(EquipmentSlot.LEGS),
        FEET(EquipmentSlot.FEET);

        private final EquipmentSlot equipmentSlot;

        ArmorSlot(EquipmentSlot equipmentSlot) {
            this.equipmentSlot = equipmentSlot;
        }

        public EquipmentSlot equipmentSlot() {
            return equipmentSlot;
        }

        public String permissionKey() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String displayName() {
            return permissionKey();
        }

        public static ArmorSlot match(String input) {
            for (ArmorSlot slot : values()) {
                if (slot.permissionKey().equalsIgnoreCase(input)) {
                    return slot;
                }
            }
            return null;
        }
    }
}
