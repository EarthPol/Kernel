package com.earthpol.kernel.economy;

import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.KernelRepository;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class LegacyVaultBridge implements EconomyBridge, Listener {

    private final JavaPlugin plugin;
    private final Economy provider;
    private volatile Economy externalProvider;
    private boolean registered;

    public LegacyVaultBridge(JavaPlugin plugin, KernelRepository repository, KernelSettings settings) {
        this.plugin = plugin;
        this.provider = new KernelEconomyProvider(plugin, repository, settings);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshProvider();
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class && event.getProvider().getProvider() != provider) {
            refreshProvider();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == Economy.class && event.getProvider().getProvider() != provider) {
            refreshProvider();
        }
    }

    private void refreshProvider() {
        var services = plugin.getServer().getServicesManager();
        RegisteredServiceProvider<Economy> selected = null;
        for (var registration : services.getRegistrations(Economy.class)) {
            if (registration.getProvider() != provider
                && (selected == null || registration.compareTo(selected) < 0)) {
                selected = registration;
            }
        }
        externalProvider = selected == null ? null : selected.getProvider();
        if (externalProvider != null && registered) {
            registered = false;
            services.unregister(Economy.class, provider);
        } else if (externalProvider == null && !registered) {
            registered = true;
            services.register(Economy.class, provider, plugin, ServicePriority.Lowest);
        }
    }

    @Override
    public String externalProviderName() {
        Economy external = externalProvider;
        return external == null ? null : external.getName();
    }

    @Override
    public String formatBalance(UUID playerId) {
        Economy active = externalProvider;
        if (active == null) {
            active = provider;
        }
        return active.format(active.getBalance(plugin.getServer().getOfflinePlayer(playerId)));
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        plugin.getServer().getServicesManager().unregister(provider);
        registered = false;
    }
}
