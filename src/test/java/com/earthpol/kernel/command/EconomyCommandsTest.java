package com.earthpol.kernel.command;

import com.earthpol.kernel.Kernel;
import com.earthpol.kernel.data.KernelRepository;
import com.earthpol.kernel.economy.EconomyBridge;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EconomyCommandsTest {
    @Test
    void balanceUsesExternalFormattingInsteadOfStoredBalance() {
        Kernel plugin = mock(Kernel.class);
        KernelRepository repository = mock(KernelRepository.class);
        EconomyBridge bridge = mock(EconomyBridge.class);
        when(plugin.repository()).thenReturn(repository);
        when(plugin.economyBridge()).thenReturn(bridge);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("0xBit");
        when(bridge.formatBalance(id)).thenReturn("576 Gold Coins");
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("bal");
        new EconomyCommands(plugin).onCommand(player, command, "bal", new String[0]);
        verify(plugin).message(player, "economy.balance.result", "0xBit", "576 Gold Coins");
        verify(repository, never()).getBalance(any());
    }

    @Test
    void otherEconomyCommandsDoNotTouchKernelAccountsWhenExternalIsActive() {
        Kernel plugin = mock(Kernel.class);
        KernelRepository repository = mock(KernelRepository.class);
        EconomyBridge bridge = mock(EconomyBridge.class);
        when(plugin.repository()).thenReturn(repository);
        when(plugin.economyBridge()).thenReturn(bridge);
        when(bridge.externalProviderName()).thenReturn("EconomyPol");
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        EconomyCommands commands = new EconomyCommands(plugin);
        for (String name : new String[] {"pay", "eco", "baltop"}) {
            Command command = mock(Command.class);
            when(command.getName()).thenReturn(name);
            commands.onCommand(player, command, name, new String[0]);
        }
        verifyNoInteractions(repository);
        verify(plugin, times(3)).message(player, "economy.external-provider", "EconomyPol");
    }
}
