package com.earthpol.kernel.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyVaultBridgeTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final ServicesManager services = mock(ServicesManager.class);
    private final List<RegisteredServiceProvider<Economy>> registrations = new ArrayList<>();

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(services.getRegistrations(Economy.class)).thenAnswer(call -> List.copyOf(registrations));
        doAnswer(call -> {
            registrations.add(new RegisteredServiceProvider<>(Economy.class, call.getArgument(1),
                call.getArgument(3), call.getArgument(2)));
            return null;
        }).when(services).register(eq(Economy.class), any(Economy.class), eq(plugin), any(ServicePriority.class));
        doAnswer(call -> {
            registrations.removeIf(entry -> entry.getProvider() == call.getArgument(1));
            return null;
        }).when(services).unregister(eq(Economy.class), any(Economy.class));
    }

    @Test
    void existingProviderWinsEvenAtLowestPriorityAndFormatsItsBalance() {
        Economy external = mock(Economy.class);
        registrations.add(new RegisteredServiceProvider<>(Economy.class, external, ServicePriority.Lowest, plugin));
        UUID id = UUID.randomUUID();
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(server.getOfflinePlayer(id)).thenReturn(player);
        when(external.getName()).thenReturn("EconomyPol");
        when(external.getBalance(player)).thenReturn(576D);
        when(external.format(576D)).thenReturn("576 Gold Coins");
        try (LegacyVaultBridge bridge = new LegacyVaultBridge(plugin, null, null)) {
            assertEquals("EconomyPol", bridge.externalProviderName());
            assertEquals("576 Gold Coins", bridge.formatBalance(id));
            verify(services, never()).register(eq(Economy.class), any(), any(), any());
        }
    }

    @Test
    void lateProviderReplacesFallbackAndRemovalRestoresIt() {
        try (LegacyVaultBridge bridge = new LegacyVaultBridge(plugin, null, null)) {
            assertNull(bridge.externalProviderName());
            assertEquals(ServicePriority.Lowest, registrations.getFirst().getPriority());
            Economy fallback = registrations.getFirst().getProvider();
            Economy external = mock(Economy.class);
            when(external.getName()).thenReturn("External");
            var registration = new RegisteredServiceProvider<>(Economy.class, external, ServicePriority.Lowest, plugin);
            registrations.add(registration);
            ServiceRegisterEvent added = mock(ServiceRegisterEvent.class);
            doReturn(registration).when(added).getProvider();
            bridge.onServiceRegister(added);
            assertEquals("External", bridge.externalProviderName());
            assertEquals(List.of(registration), registrations);
            registrations.remove(registration);
            ServiceUnregisterEvent removed = mock(ServiceUnregisterEvent.class);
            doReturn(registration).when(removed).getProvider();
            bridge.onServiceUnregister(removed);
            assertNull(bridge.externalProviderName());
            assertSame(fallback, registrations.getFirst().getProvider());
        }
    }

    @Test
    void highestPriorityExternalWinsAndFallsBackToOtherExternal() {
        Economy low = mock(Economy.class);
        Economy high = mock(Economy.class);
        when(low.getName()).thenReturn("Low");
        when(high.getName()).thenReturn("High");
        registrations.add(new RegisteredServiceProvider<>(Economy.class, low, ServicePriority.Low, plugin));
        var highest = new RegisteredServiceProvider<>(Economy.class, high, ServicePriority.High, plugin);
        registrations.add(highest);
        try (LegacyVaultBridge bridge = new LegacyVaultBridge(plugin, null, null)) {
            assertEquals("High", bridge.externalProviderName());
            registrations.remove(highest);
            ServiceUnregisterEvent removed = mock(ServiceUnregisterEvent.class);
            doReturn(highest).when(removed).getProvider();
            bridge.onServiceUnregister(removed);
            assertEquals("Low", bridge.externalProviderName());
            verify(services, never()).register(eq(Economy.class), any(), any(), any());
        }
    }
}
