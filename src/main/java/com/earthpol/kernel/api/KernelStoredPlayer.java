package com.earthpol.kernel.api;

import java.math.BigDecimal;
import java.util.UUID;

public record KernelStoredPlayer(
    UUID uuid,
    String lastKnownName,
    String lastKnownIp,
    BigDecimal balance,
    String nickname,
    boolean godMode,
    boolean flyMode,
    boolean teleportBlocked,
    long lastSeenEpochMillis
) {
}
