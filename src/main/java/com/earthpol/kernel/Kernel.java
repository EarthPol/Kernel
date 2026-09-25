package com.earthpol.kernel;

import com.earthpol.kernel.api.KernelApi;
import com.earthpol.kernel.api.KernelHome;
import com.earthpol.kernel.api.KernelPlayerReference;
import com.earthpol.kernel.api.KernelStoredPlayer;
import com.earthpol.kernel.api.internal.KernelApiImpl;
import com.earthpol.kernel.command.AdminCommands;
import com.earthpol.kernel.command.CommunicationCommands;
import com.earthpol.kernel.command.EconomyCommands;
import com.earthpol.kernel.command.HomeCommands;
import com.earthpol.kernel.command.KernelCommands;
import com.earthpol.kernel.command.PlayerCommands;
import com.earthpol.kernel.command.SocialSpyCommands;
import com.earthpol.kernel.command.TeleportCommands;
import com.earthpol.kernel.command.TrimCommands;
import com.earthpol.kernel.command.UtilityMenuCommands;
import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.KernelDatabase;
import com.earthpol.kernel.data.KernelRepository;
import com.earthpol.kernel.data.model.StoredPlayer;
import com.earthpol.kernel.economy.LegacyVaultBridge;
import com.earthpol.kernel.listener.EnderChestViewListener;
import com.earthpol.kernel.listener.KernelPlayerListener;
import com.earthpol.kernel.service.BackService;
import com.earthpol.kernel.service.ConversationService;
import com.earthpol.kernel.service.PlayerStateCache;
import com.earthpol.kernel.service.SocialSpyService;
import com.earthpol.kernel.service.TeleportExecutionService;
import com.earthpol.kernel.service.TeleportService;
import com.europamc.europalib.entity.EntitySchedulerUtil;
import com.europamc.europalib.translation.TranslationService;
import com.europamc.europalib.translation.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class Kernel extends JavaPlugin {

    private KernelSettings settings;
    private KernelDatabase database;
    private KernelRepository repository;
    private BackService backService;
    private TeleportService teleportService;
    private TeleportExecutionService teleportExecutionService;
    private ConversationService conversationService;
    private PlayerStateCache stateCache;
    private SocialSpyService socialSpyService;
    private AutoCloseable economyBridge;
    private TranslationService translationService;
    private KernelPlayerListener playerListener;
    private EnderChestViewListener enderChestViewListener;
    private KernelApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.translationService = new TranslationService(this, Kernel.class);
        this.translationService.load();
        this.settings = KernelSettings.load(this);
        this.database = new KernelDatabase(settings.databasePath());

        try {
            this.database.open();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "Unable to open the Kernel database.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.repository = new KernelRepository(database, settings);
        this.backService = new BackService();
        this.teleportService = new TeleportService(settings.teleportRequestTimeout());
        this.teleportExecutionService = new TeleportExecutionService(this, settings, backService);
        this.conversationService = new ConversationService();
        this.stateCache = new PlayerStateCache();
        this.socialSpyService = new SocialSpyService(this, settings);
        this.api = new KernelApiImpl(this);

        registerCommands();
        registerPlayerListener();
        registerInventoryListeners();
        registerApiService();

        this.economyBridge = registerEconomyBridge();
    }

    @Override
    public void onDisable() {
        unregisterApiService();
        closeQuietly(economyBridge);
        closeQuietly(database);
    }

    public KernelSettings settings() {
        return settings;
    }

    public KernelRepository repository() {
        return repository;
    }

    public BackService backService() {
        return backService;
    }

    public TeleportService teleportService() {
        return teleportService;
    }

    public TeleportExecutionService teleportExecutionService() {
        return teleportExecutionService;
    }

    public ConversationService conversationService() {
        return conversationService;
    }

    public PlayerStateCache stateCache() {
        return stateCache;
    }

    public SocialSpyService socialSpyService() {
        return socialSpyService;
    }

    public KernelApi api() {
        return api;
    }

    public Optional<KernelPlayerReference> findPlayerByName(String playerName) {
        return api.findPlayerByName(playerName);
    }

    public Optional<UUID> findPlayerUuidByName(String playerName) {
        return api.findPlayerUuidByName(playerName);
    }

    public Optional<KernelStoredPlayer> getStoredPlayer(UUID playerUuid) {
        return api.getStoredPlayer(playerUuid);
    }

    public Optional<String> getPlayerNickname(UUID playerUuid) {
        return api.getPlayerNickname(playerUuid);
    }

    public List<String> getHomeNames(UUID playerUuid) {
        return api.getHomeNames(playerUuid);
    }

    public Optional<KernelHome> getPlayerHome(UUID playerUuid) {
        return api.getPlayerHome(playerUuid);
    }

    public Optional<KernelHome> getPlayerHome(UUID playerUuid, String homeName) {
        return api.getPlayerHome(playerUuid, homeName);
    }

    public boolean isSeenBefore(UUID playerUuid) {
        return api.isSeenBefore(playerUuid);
    }

    public List<KernelPlayerReference> getIgnored(UUID playerUuid) {
        return api.getIgnored(playerUuid);
    }

    public List<KernelPlayerReference> getIgnoredBy(UUID playerUuid) {
        return api.getIgnoredBy(playerUuid);
    }

    public boolean isIgnoring(UUID ownerUuid, UUID targetUuid) {
        return api.isIgnoring(ownerUuid, targetUuid);
    }

    public TranslationService translationService() {
        return translationService;
    }

    public EnderChestViewListener enderChestViewListener() {
        return enderChestViewListener;
    }

    public void message(CommandSender sender, String key, Object... args) {
        if (sender instanceof Player player) {
            EntitySchedulerUtil.run(this, player, () -> player.sendMessage(
                Translations.prefixed(translationService, player, key, args)
            ));
            return;
        }

        Translations.sendPrefixed(translationService, sender, key, args);
    }

    public void messageNow(CommandSender sender, String key, Object... args) {
        if (sender instanceof Player player) {
            player.sendMessage(Translations.prefixed(translationService, player, key, args));
            return;
        }

        Translations.sendPrefixed(translationService, sender, key, args);
    }

    public void rawMessage(CommandSender sender, String key, Object... args) {
        if (sender instanceof Player player) {
            EntitySchedulerUtil.run(this, player, () -> player.sendMessage(
                Translations.text(translationService, player, key, args)
            ));
            return;
        }

        sender.sendMessage(text(sender, key, args));
    }

    public String text(CommandSender sender, String key, Object... args) {
        return Translations.text(translationService, sender, key, args);
    }

    public void applyStoredState(Player player) {
        applyNickname(player, stateCache.nickname(player.getUniqueId()).orElse(null));
        applyFlightState(player, stateCache.isFlyEnabled(player.getUniqueId()));
    }

    public void applyNickname(Player player, String nickname) {
        String visibleName = nickname == null || nickname.isBlank() ? player.getName() : nickname;
        Component component = Component.text(visibleName);
        player.displayName(component);
        player.playerListName(component);
    }

    public void applyFlightState(Player player, boolean flyEnabled) {
        boolean allowFlight = flyEnabled
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR;

        if (!allowFlight && player.isFlying()) {
            player.setFlying(false);
        }

        player.setAllowFlight(allowFlight);

        if (flyEnabled && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(true);
        }
    }

    public void reloadRuntime(CommandSender sender) {
        getServer().getGlobalRegionScheduler().execute(this, () -> {
            try {
                reloadRuntimeState();
                message(sender, "kernel.reload.success");
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Unable to reload Kernel.", exception);
                message(sender, "kernel.reload.failed");
            }
        });
    }

    private void registerCommands() {
        register(
            new KernelCommands(this),
            "kernel"
        );
        register(
            new HomeCommands(this),
            "home", "sethome", "delhome"
        );
        register(
            new TeleportCommands(this),
            "tpa", "tpaccept", "tpdeny", "back", "tp", "tppos", "tphere", "tptoggle", "tpo"
        );
        register(
            new CommunicationCommands(this),
            "msg", "r", "ignore", "mail", "nick", "seen"
        );
        register(
            new SocialSpyCommands(this),
            "socialspy"
        );
        register(
            new TrimCommands(this),
            "trim"
        );
        register(
            new AdminCommands(this),
            "sudo", "smite"
        );
        register(
            new UtilityMenuCommands(this),
            "hat", "workbench", "stonecutter", "cartographytable", "smithingtable", "grindstone", "loom", "enderchest", "anvil"
        );
        register(
            new EconomyCommands(this),
            "bal", "pay", "baltop", "eco"
        );
        register(
            new PlayerCommands(this),
            "heal", "feed", "clearinventory", "gamemode", "gmc", "gms", "gma", "gmsp", "fly", "god", "speed"
        );
    }

    private void registerPlayerListener() {
        this.playerListener = new KernelPlayerListener(this, repository, settings, stateCache, backService, socialSpyService);
        getServer().getPluginManager().registerEvents(playerListener, this);
    }

    private void registerInventoryListeners() {
        this.enderChestViewListener = new EnderChestViewListener(this);
        getServer().getPluginManager().registerEvents(enderChestViewListener, this);
    }

    private void register(TabExecutor executor, String... commandNames) {
        for (String commandName : commandNames) {
            Objects.requireNonNull(getCommand(commandName), "Missing command: " + commandName)
                .setExecutor(executor);
            Objects.requireNonNull(getCommand(commandName), "Missing command: " + commandName)
                .setTabCompleter(executor);
        }
    }

    private void registerApiService() {
        if (api == null) {
            return;
        }

        getServer().getServicesManager().register(KernelApi.class, api, this, ServicePriority.Normal);
    }

    private void unregisterApiService() {
        if (api == null) {
            return;
        }

        getServer().getServicesManager().unregister(KernelApi.class, api);
    }

    private AutoCloseable registerEconomyBridge() {
        if (!isClassPresent("net.milkbowl.vault.economy.Economy")) {
            return null;
        }

        try {
            AutoCloseable bridge = new LegacyVaultBridge(this, repository, settings);
            getLogger().info("Registered a Vault/VaultUnlocked economy provider.");
            return bridge;
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "Unable to register the Vault/VaultUnlocked economy bridge.", throwable);
            return null;
        }
    }

    private synchronized void reloadRuntimeState() throws SQLException {
        reloadConfig();

        TranslationService reloadedTranslationService = new TranslationService(this, Kernel.class);
        reloadedTranslationService.load();

        KernelSettings reloadedSettings = KernelSettings.load(this);
        KernelDatabase previousDatabase = this.database;
        KernelDatabase reloadedDatabase = previousDatabase;
        boolean replaceDatabase = previousDatabase != null
            && !previousDatabasePath().equals(reloadedSettings.databasePath());

        try {
            if (database == null || replaceDatabase) {
                reloadedDatabase = new KernelDatabase(reloadedSettings.databasePath());
                reloadedDatabase.open();
            }
        } catch (SQLException exception) {
            if (reloadedDatabase != null && reloadedDatabase != previousDatabase) {
                closeQuietly(reloadedDatabase);
            }
            throw exception;
        }

        KernelRepository reloadedRepository = new KernelRepository(reloadedDatabase, reloadedSettings);
        TeleportService reloadedTeleportService = new TeleportService(reloadedSettings.teleportRequestTimeout());
        TeleportExecutionService reloadedTeleportExecutionService =
            new TeleportExecutionService(this, reloadedSettings, backService);
        SocialSpyService previousSocialSpyService = this.socialSpyService;
        SocialSpyService reloadedSocialSpyService = new SocialSpyService(
            this,
            reloadedSettings,
            previousSocialSpyService == null ? java.util.Set.of() : previousSocialSpyService.enabledPlayers()
        );

        AutoCloseable previousEconomyBridge = this.economyBridge;
        KernelPlayerListener previousPlayerListener = this.playerListener;
        EnderChestViewListener previousEnderChestViewListener = this.enderChestViewListener;

        this.translationService = reloadedTranslationService;
        this.settings = reloadedSettings;
        this.database = reloadedDatabase;
        this.repository = reloadedRepository;
        this.teleportService = reloadedTeleportService;
        this.teleportExecutionService = reloadedTeleportExecutionService;
        this.socialSpyService = reloadedSocialSpyService;

        registerCommands();

        if (previousPlayerListener != null) {
            HandlerList.unregisterAll(previousPlayerListener);
        }
        registerPlayerListener();
        if (previousEnderChestViewListener != null) {
            HandlerList.unregisterAll(previousEnderChestViewListener);
        }
        registerInventoryListeners();

        closeQuietly(previousEconomyBridge);
        this.economyBridge = registerEconomyBridge();

        if (replaceDatabase && previousDatabase != null) {
            closeQuietly(previousDatabase);
        }

        refreshOnlinePlayerState();
    }

    private java.nio.file.Path previousDatabasePath() {
        return settings == null ? null : settings.databasePath();
    }

    private void refreshOnlinePlayerState() {
        for (Player player : getServer().getOnlinePlayers()) {
            EntitySchedulerUtil.run(this, player, () -> {
                StoredPlayer storedPlayer = repository.ensurePlayer(player);
                stateCache.load(storedPlayer);
                applyStoredState(player);
            });
        }
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Unable to close a Kernel resource cleanly.", exception);
        }
    }
}
