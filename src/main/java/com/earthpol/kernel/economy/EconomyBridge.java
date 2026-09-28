package com.earthpol.kernel.economy;

import java.util.UUID;

/** Keeps the optional Vault API out of Kernel's command classes. */
public interface EconomyBridge extends AutoCloseable {
    String externalProviderName();

    String formatBalance(UUID playerId);
}
