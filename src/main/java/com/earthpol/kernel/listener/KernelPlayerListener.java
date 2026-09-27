package com.earthpol.kernel.listener;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.KernelRepository;
import com.earthpol.kernel.data.model.MailRecord;
import com.earthpol.kernel.data.model.StoredPlayer;
import com.earthpol.kernel.service.BackService;
import com.earthpol.kernel.service.PlayerStateCache;
import com.earthpol.kernel.service.SocialSpyService;
import com.earthpol.earthpollib.entity.EntitySchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class KernelPlayerListener implements Listener {

    private static final DateTimeFormatter MAIL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Kernel plugin;
    private final KernelRepository repository;
    private final KernelSettings settings;
    private final PlayerStateCache stateCache;
    private final BackService backService;
    private final SocialSpyService socialSpyService;

    public KernelPlayerListener(
        Kernel plugin,
        KernelRepository repository,
        KernelSettings settings,
        PlayerStateCache stateCache,
        BackService backService,
        SocialSpyService socialSpyService
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.settings = settings;
        this.stateCache = stateCache;
        this.backService = backService;
        this.socialSpyService = socialSpyService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        StoredPlayer storedPlayer = repository.ensurePlayer(player);
        stateCache.load(storedPlayer);
        plugin.applyStoredState(player);
        deliverMail(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        repository.updateLastSeen(event.getPlayer());
        stateCache.unload(event.getPlayer().getUniqueId());
        backService.clear(event.getPlayer().getUniqueId());
        socialSpyService.disable(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!socialSpyService.shouldSpyCommand(event.getMessage())) {
            return;
        }

        socialSpyService.spyCommand(event.getPlayer(), event.getMessage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (stateCache.isGodEnabled(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        backService.capture(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        EntitySchedulerUtil.runDelayed(plugin, event.getPlayer(), 1L, () -> plugin.applyFlightState(
            event.getPlayer(),
            stateCache.isFlyEnabled(event.getPlayer().getUniqueId())
        ));
    }

    private void deliverMail(Player player) {
        if (!settings.deliverMailOnJoin()) {
            return;
        }

        List<MailRecord> pendingMail = repository.getUndeliveredMail(player.getUniqueId());
        if (pendingMail.isEmpty()) {
            return;
        }

        plugin.rawMessage(player, "communication.mail.summary", pendingMail.size());

        List<Long> delivered = new ArrayList<>();
        ZoneId zoneId = ZoneId.systemDefault();

        for (MailRecord mailRecord : pendingMail) {
            String timestamp = MAIL_FORMAT.format(Instant.ofEpochMilli(mailRecord.sentEpochMillis()).atZone(zoneId));
            plugin.rawMessage(player, "communication.mail.entry", timestamp, mailRecord.senderName(), mailRecord.message());
            delivered.add(mailRecord.id());
        }

        repository.markMailDelivered(delivered);
    }
}
