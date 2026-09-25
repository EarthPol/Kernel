package com.earthpol.kernel.listener;

import com.europamc.europalib.entity.EntitySchedulerUtil;
import com.earthpol.kernel.Kernel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EnderChestViewListener implements Listener {

    private final Kernel plugin;
    private final Map<UUID, EnderChestSession> sessions = new ConcurrentHashMap<>();

    public EnderChestViewListener(Kernel plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Player target, boolean mutable) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            sessions.remove(viewer.getUniqueId());
            EntitySchedulerUtil.run(plugin, viewer, () -> viewer.openInventory(viewer.getEnderChest()));
            return;
        }

        EntitySchedulerUtil.run(
            plugin,
            target,
            () -> {
                Inventory enderChest = target.getEnderChest();
                EnderChestSession session = new EnderChestSession(mutable);

                EntitySchedulerUtil.run(plugin, viewer, () -> {
                    sessions.put(viewer.getUniqueId(), session);
                    viewer.openInventory(enderChest);
                });
            },
            () -> EntitySchedulerUtil.run(plugin, viewer, () -> plugin.messageNow(viewer, "error.player-not-online"))
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Player viewer = (Player) event.getWhoClicked();
        EnderChestSession session = sessions.get(viewer.getUniqueId());
        if (session == null || session.mutable() || event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        event.setCancelled(true);
        EntitySchedulerUtil.runDelayed(plugin, viewer, 1L, viewer::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Player viewer = (Player) event.getWhoClicked();
        EnderChestSession session = sessions.get(viewer.getUniqueId());
        if (session == null || session.mutable() || event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        event.setCancelled(true);
        EntitySchedulerUtil.runDelayed(plugin, viewer, 1L, viewer::updateInventory);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player viewer) {
            sessions.remove(viewer.getUniqueId());
        }
    }

    private record EnderChestSession(boolean mutable) {
    }
}
