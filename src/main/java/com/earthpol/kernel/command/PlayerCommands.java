package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlayerCommands extends BaseCommandHandler {

    private static final float DEFAULT_WALK_SPEED = 0.2F;
    private static final float DEFAULT_FLY_SPEED = 0.1F;
    private static final String DEFAULT_SPEED_INPUT = "default";
    private static final List<String> SPEED_TYPES = List.of("walk", "fly");
    private static final List<String> SPEED_VALUES = List.of(
        "1.0", "2.0", "3.0", "4.0", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0"
    );

    public PlayerCommands(Kernel plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "heal" -> handleHeal(sender, args);
            case "fly" -> handleFly(sender, args);
            case "god" -> handleGod(sender, args);
            case "speed" -> handleSpeed(sender, args);
            case "feed" -> handleFeed(sender);
            case "clearinventory" -> handleClearInventory(sender);
            case "gamemode", "gmc", "gms", "gma", "gmsp" -> handleGamemode(sender, command.getName(), args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if ((commandName.equals("heal") || commandName.equals("fly") || commandName.equals("god")) && args.length == 1) {
            return completeOnlinePlayers(args[0]);
        }

        if (commandName.equals("speed")) {
            if (args.length == 1) {
                List<String> options = completeOptions(args[0], SPEED_TYPES);
                options.addAll(completeOptions(args[0], List.of(DEFAULT_SPEED_INPUT)));
                options.addAll(completeOptions(args[0], SPEED_VALUES));
                return options.stream().distinct().toList();
            }
            if (args.length == 2 && SPEED_TYPES.contains(args[0].toLowerCase(Locale.ROOT))) {
                List<String> options = completeOptions(args[1], SPEED_VALUES);
                options.addAll(completeOptions(args[1], List.of(DEFAULT_SPEED_INPUT)));
                return options.stream().distinct().toList();
            }
        }

        if (commandName.equals("gamemode")) {
            if (args.length == 1) {
                List<String> options = completeOptions(args[0], List.of("survival", "creative", "adventure", "spectator"));
                options.addAll(completeOnlinePlayers(args[0]));
                return options.stream().distinct().toList();
            }
            if (args.length == 2) {
                List<String> options = completeOnlinePlayers(args[1]);
                options.addAll(completeOptions(args[1], List.of("survival", "creative", "adventure", "spectator")));
                return options.stream().distinct().toList();
            }
        }

        if (switch (commandName) {
            case "gmc", "gms", "gma", "gmsp" -> args.length == 1;
            default -> false;
        }) {
            return completeOnlinePlayers(args[0]);
        }

        return Collections.emptyList();
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.heal")) {
            return true;
        }

        Player target;
        boolean selfTarget;
        if (args.length == 0) {
            target = requirePlayer(sender);
            if (target == null) {
                return true;
            }
            selfTarget = true;
        } else if (args.length == 1) {
            if (!requirePermission(sender, "kernel.command.heal.others")) {
                return true;
            }

            target = findOnlinePlayer(args[0]);
            if (target == null) {
                message(sender, "error.player-not-online");
                return true;
            }

            selfTarget = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        } else {
            return false;
        }

        String targetName = target.getName();
        runFor(target, () -> {
            heal(target);
            message(target, selfTarget ? "player.heal.success" : "player.heal.other.target");
        });

        if (!selfTarget) {
            message(sender, "player.heal.other.sender", targetName);
        }
        return true;
    }

    private void heal(Player player) {
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null
            ? 20D
            : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(maxHealth);
        player.setFireTicks(0);
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.speed")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        SpeedType speedType;
        String speedInput;
        if (args.length == 1) {
            speedType = player.isFlying() ? SpeedType.FLY : SpeedType.WALK;
            speedInput = args[0];
        } else if (args.length == 2) {
            speedType = parseSpeedType(args[0]);
            if (speedType == null) {
                return false;
            }
            speedInput = args[1];
        } else {
            return false;
        }

        if (speedType == SpeedType.FLY) {
            if (isDefaultSpeedInput(speedInput)) {
                player.setFlySpeed(DEFAULT_FLY_SPEED);
                message(sender, "player.speed.fly-reset");
                return true;
            }

            Float speed = parseBukkitSpeed(speedInput);
            if (speed == null) {
                message(sender, "player.speed.invalid");
                return true;
            }

            player.setFlySpeed(speed);
            message(sender, "player.speed.fly-set", normalizeSpeedInput(speedInput));
            return true;
        }

        if (isDefaultSpeedInput(speedInput)) {
            player.setWalkSpeed(DEFAULT_WALK_SPEED);
            message(sender, "player.speed.walk-reset");
            return true;
        }

        Float speed = parseBukkitSpeed(speedInput);
        if (speed == null) {
            message(sender, "player.speed.invalid");
            return true;
        }

        player.setWalkSpeed(speed);
        message(sender, "player.speed.walk-set", normalizeSpeedInput(speedInput));
        return true;
    }

    private boolean handleFeed(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.feed")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setExhaustion(0F);
        message(sender, "player.feed.success");
        return true;
    }

    private boolean handleClearInventory(CommandSender sender) {
        if (!requirePermission(sender, "kernel.command.clearinventory")) {
            return true;
        }

        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        message(sender, "player.clearinventory.success");
        return true;
    }

    private boolean handleFly(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.fly")) {
            return true;
        }

        Player target;
        boolean selfTarget;
        if (args.length == 0) {
            target = requirePlayer(sender);
            if (target == null) {
                return true;
            }
            selfTarget = true;
        } else if (args.length == 1) {
            if (!requirePermission(sender, "kernel.command.fly.others")) {
                return true;
            }

            target = findOnlinePlayer(args[0]);
            if (target == null) {
                message(sender, "error.player-not-online");
                return true;
            }

            selfTarget = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        } else {
            return false;
        }

        String targetName = target.getName();
        boolean enabled = !stateCache.isFlyEnabled(target.getUniqueId());
        runFor(target, () -> {
            repository.updateFlyMode(target.getUniqueId(), enabled);
            stateCache.setFlyEnabled(target.getUniqueId(), enabled);
            plugin.applyFlightState(target, enabled);
            message(target, flyTargetKey(enabled, selfTarget));
        });

        if (!selfTarget) {
            message(sender, flySenderKey(enabled), targetName);
        }
        return true;
    }

    private boolean handleGod(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "kernel.command.god")) {
            return true;
        }

        Player target;
        boolean selfTarget;
        if (args.length == 0) {
            target = requirePlayer(sender);
            if (target == null) {
                return true;
            }
            selfTarget = true;
        } else if (args.length == 1) {
            if (!requirePermission(sender, "kernel.command.god.others")) {
                return true;
            }

            target = findOnlinePlayer(args[0]);
            if (target == null) {
                message(sender, "error.player-not-online");
                return true;
            }

            selfTarget = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        } else {
            return false;
        }

        String targetName = target.getName();
        boolean enabled = !stateCache.isGodEnabled(target.getUniqueId());
        runFor(target, () -> {
            repository.updateGodMode(target.getUniqueId(), enabled);
            stateCache.setGodEnabled(target.getUniqueId(), enabled);
            target.setFireTicks(0);
            message(target, godTargetKey(enabled, selfTarget));
        });

        if (!selfTarget) {
            message(sender, godSenderKey(enabled), targetName);
        }
        return true;
    }

    private String flySenderKey(boolean enabled) {
        return enabled ? "player.fly.other.sender-enabled" : "player.fly.other.sender-disabled";
    }

    private String flyTargetKey(boolean enabled, boolean selfTarget) {
        if (selfTarget) {
            return enabled ? "player.fly.enabled" : "player.fly.disabled";
        }
        return enabled ? "player.fly.other.target-enabled" : "player.fly.other.target-disabled";
    }

    private String godSenderKey(boolean enabled) {
        return enabled ? "player.god.other.sender-enabled" : "player.god.other.sender-disabled";
    }

    private String godTargetKey(boolean enabled, boolean selfTarget) {
        if (selfTarget) {
            return enabled ? "player.god.enabled" : "player.god.disabled";
        }
        return enabled ? "player.god.other.target-enabled" : "player.god.other.target-disabled";
    }

    private boolean handleGamemode(CommandSender sender, String commandName, String[] args) {
        GameMode aliasMode = aliasMode(commandName);
        GameMode mode;
        Player target;

        if (aliasMode != null) {
            if (!requirePermission(sender, "kernel.command.gamemode." + permissionSuffix(aliasMode))) {
                return true;
            }

            if (args.length == 0) {
                target = requirePlayer(sender);
                if (target == null) {
                    return true;
                }
            } else if (args.length == 1) {
                if (!requirePermission(sender, "kernel.command.gamemode.others")) {
                    return true;
                }
                target = findOnlinePlayer(args[0]);
                if (target == null) {
                    message(sender, "error.player-not-online");
                    return true;
                }
            } else {
                return false;
            }

            return applyGamemode(sender, target, aliasMode);
        }

        if (!requirePermission(sender, "kernel.command.gamemode")) {
            return true;
        }

        if (args.length == 0 || args.length > 2) {
            return false;
        }

        if (args.length == 1) {
            mode = parseGameMode(args[0]);
            if (mode == null) {
                return false;
            }

            if (!requirePermission(sender, "kernel.command.gamemode." + permissionSuffix(mode))) {
                return true;
            }

            target = requirePlayer(sender);
            if (target == null) {
                return true;
            }
            return applyGamemode(sender, target, mode);
        }

        GameMode first = parseGameMode(args[0]);
        GameMode second = parseGameMode(args[1]);

        if (first != null && second == null) {
            mode = first;
            target = findOnlinePlayer(args[1]);
        } else if (second != null && first == null) {
            mode = second;
            target = findOnlinePlayer(args[0]);
        } else {
            return false;
        }

        if (!requirePermission(sender, "kernel.command.gamemode." + permissionSuffix(mode))) {
            return true;
        }

        if (!requirePermission(sender, "kernel.command.gamemode.others")) {
            return true;
        }

        if (target == null) {
            message(sender, "error.player-not-online");
            return true;
        }

        return applyGamemode(sender, target, mode);
    }

    private boolean applyGamemode(CommandSender sender, Player target, GameMode mode) {
        String modeName = mode.name().toLowerCase(Locale.ROOT);

        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            target.setGameMode(mode);
            plugin.applyFlightState(target, stateCache.isFlyEnabled(target.getUniqueId()));
            message(sender, "player.gamemode.self", modeName);
            return true;
        }

        String targetName = target.getName();
        runFor(target, () -> {
            target.setGameMode(mode);
            plugin.applyFlightState(target, stateCache.isFlyEnabled(target.getUniqueId()));
            message(target, "player.gamemode.other.target", modeName);
        });

        message(sender, "player.gamemode.other.sender", targetName, modeName);
        return true;
    }

    private GameMode aliasMode(String commandName) {
        return switch (commandName.toLowerCase(Locale.ROOT)) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gma" -> GameMode.ADVENTURE;
            case "gmsp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private String permissionSuffix(GameMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    private boolean isDefaultSpeedInput(String input) {
        return DEFAULT_SPEED_INPUT.equalsIgnoreCase(input);
    }

    private SpeedType parseSpeedType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "walk" -> SpeedType.WALK;
            case "fly" -> SpeedType.FLY;
            default -> null;
        };
    }

    private Float parseBukkitSpeed(String input) {
        try {
            double speed = Double.parseDouble(input);
            if (speed < 1.0D || speed > 10.0D) {
                return null;
            }
            return (float) (speed / 10.0D);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeSpeedInput(String input) {
        try {
            double speed = Double.parseDouble(input);
            if (speed == Math.rint(speed)) {
                return String.format(Locale.ROOT, "%.1f", speed);
            }
        } catch (NumberFormatException ignored) {
            return input;
        }
        return input;
    }

    private enum SpeedType {
        WALK,
        FLY
    }
}
