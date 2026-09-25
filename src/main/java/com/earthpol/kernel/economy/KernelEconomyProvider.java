package com.earthpol.kernel.economy;

import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.KernelRepository;
import com.earthpol.kernel.data.model.BalanceOperation;
import com.earthpol.kernel.data.model.PlayerLookup;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("deprecation")
public final class KernelEconomyProvider extends AbstractEconomy {

    private final JavaPlugin plugin;
    private final KernelRepository repository;
    private final KernelSettings settings;

    public KernelEconomyProvider(JavaPlugin plugin, KernelRepository repository, KernelSettings settings) {
        this.plugin = plugin;
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "Kernel";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return settings.format(BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural() {
        return settings.currencyPlural();
    }

    @Override
    public String currencyNameSingular() {
        return settings.currencySingular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return resolve(playerName).isPresent();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return repository.findPlayer(player.getUniqueId()).isPresent();
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return resolve(playerName)
            .map(PlayerLookup::uuid)
            .map(repository::getBalance)
            .orElse(BigDecimal.ZERO.setScale(2))
            .doubleValue();
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return repository.getBalance(player.getUniqueId()).doubleValue();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return resolve(playerName)
            .map(PlayerLookup::uuid)
            .map(uuid -> response(repository.withdraw(uuid, BigDecimal.valueOf(amount))))
            .orElse(failure(amount, "Account not found."));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return response(repository.withdraw(player.getUniqueId(), BigDecimal.valueOf(amount)));
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return resolve(playerName)
            .map(PlayerLookup::uuid)
            .map(uuid -> response(repository.deposit(uuid, BigDecimal.valueOf(amount))))
            .orElse(failure(amount, "Account not found."));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return response(repository.deposit(player.getUniqueId(), BigDecimal.valueOf(amount)));
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        Optional<PlayerLookup> lookup = resolve(playerName);
        if (lookup.isPresent()) {
            return true;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            return false;
        }

        repository.ensurePlayer(player);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player.getName() == null) {
            return false;
        }

        repository.ensurePlayer(player.getUniqueId(), player.getName());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private Optional<PlayerLookup> resolve(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            repository.ensurePlayer(online);
            return Optional.of(new PlayerLookup(online.getUniqueId(), online.getName()));
        }

        return repository.findPlayerByName(playerName);
    }

    private EconomyResponse response(BalanceOperation operation) {
        return new EconomyResponse(
            operation.amount().doubleValue(),
            operation.balance().doubleValue(),
            operation.success() ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
            operation.message()
        );
    }

    private EconomyResponse failure(double amount, String message) {
        return new EconomyResponse(amount, 0D, EconomyResponse.ResponseType.FAILURE, message);
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0D, 0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }
}
