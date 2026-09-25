package com.earthpol.kernel.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public record KernelSettings(
    Path databasePath,
    BigDecimal startingBalance,
    String currencySymbol,
    String currencySingular,
    String currencyPlural,
    int baltopSize,
    String defaultHomeName,
    Duration teleportRequestTimeout,
    Duration teleportWarmup,
    Duration teleportRequestWarmup,
    boolean deliverMailOnJoin,
    int nicknameMaxLength,
    List<String> socialSpyCommands
) {

    public static KernelSettings load(JavaPlugin plugin) {
        String configuredFile = plugin.getConfig().getString("storage.file", "kernel.db");
        Path databasePath = Path.of(configuredFile);
        List<String> socialSpyCommands = plugin.getConfig().getStringList("social-spy.commands");

        if (!databasePath.isAbsolute()) {
            databasePath = plugin.getDataFolder().toPath().resolve(configuredFile);
        }

        if (socialSpyCommands.isEmpty()) {
            socialSpyCommands = List.of("*");
        }

        return new KernelSettings(
            databasePath,
            normalize(plugin.getConfig().getString("economy.starting-balance", "0")),
            plugin.getConfig().getString("economy.symbol", "$"),
            plugin.getConfig().getString("economy.currency-singular", "Coin"),
            plugin.getConfig().getString("economy.currency-plural", "Coins"),
            Math.max(1, plugin.getConfig().getInt("economy.baltop-size", 10)),
            normalizeDefaultHomeName(plugin.getConfig().getString("homes.default-name", "home")),
            Duration.ofSeconds(Math.max(5L, plugin.getConfig().getLong("teleport.request-timeout-seconds", 60L))),
            Duration.ofSeconds(Math.max(0L, plugin.getConfig().getLong("teleport.warmup-seconds", 0L))),
            Duration.ofSeconds(Math.max(0L, plugin.getConfig().getLong("teleport.tpa-warmup-seconds", 5L))),
            plugin.getConfig().getBoolean("mail.deliver-on-join", true),
            Math.max(3, plugin.getConfig().getInt("nickname.max-length", 32)),
            List.copyOf(socialSpyCommands)
        );
    }

    public BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public String format(BigDecimal amount) {
        return currencySymbol + normalizeAmount(amount).toPlainString();
    }

    public String normalizeHomeName(String input) {
        if (input == null || input.isBlank()) {
            return defaultHomeName;
        }

        return normalizeConfiguredHomeName(input);
    }

    public long teleportWarmupTicks() {
        return teleportWarmup.toSeconds() * 20L;
    }

    public long teleportRequestWarmupTicks() {
        return teleportRequestWarmup.toSeconds() * 20L;
    }

    private static BigDecimal normalize(String amount) {
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeDefaultHomeName(String input) {
        return normalizeConfiguredHomeName(input == null ? "home" : input);
    }

    private static String normalizeConfiguredHomeName(String input) {
        return input.trim().toLowerCase(Locale.ROOT);
    }
}
