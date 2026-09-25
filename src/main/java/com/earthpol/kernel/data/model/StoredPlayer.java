package com.earthpol.kernel.data.model;

import java.math.BigDecimal;
import java.util.UUID;

public record StoredPlayer(
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
