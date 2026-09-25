package com.earthpol.kernel.economy;

import com.earthpol.kernel.config.KernelSettings;
import com.earthpol.kernel.data.KernelRepository;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class LegacyVaultBridge implements AutoCloseable {

    private final JavaPlugin plugin;
    private final Economy provider;

    public LegacyVaultBridge(JavaPlugin plugin, KernelRepository repository, KernelSettings settings) {
        this.plugin = plugin;
        this.provider = new KernelEconomyProvider(plugin, repository, settings);
        plugin.getServer().getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Normal);
    }

    @Override
    public void close() {
        plugin.getServer().getServicesManager().unregister(provider);
    }
}
