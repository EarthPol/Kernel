package com.earthpol.kernel.service;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportService {

    private final Duration timeout;
    private final Map<UUID, TeleportRequest> requestsByTarget = new ConcurrentHashMap<>();

    public TeleportService(Duration timeout) {
        this.timeout = timeout;
    }

    public TeleportRequest create(Player requester, Player target) {
        TeleportRequest request = new TeleportRequest(
            requester.getUniqueId(),
            requester.getName(),
            target.getUniqueId(),
            Instant.now().plus(timeout).toEpochMilli()
        );

        requestsByTarget.put(target.getUniqueId(), request);
        return request;
    }

    public Optional<TeleportRequest> get(UUID targetUuid) {
        TeleportRequest request = requestsByTarget.get(targetUuid);
        if (request == null) {
            return Optional.empty();
        }

        if (request.isExpired()) {
            requestsByTarget.remove(targetUuid);
            return Optional.empty();
        }

        return Optional.of(request);
    }

    public Optional<TeleportRequest> take(UUID targetUuid) {
        Optional<TeleportRequest> request = get(targetUuid);
        request.ifPresent(value -> requestsByTarget.remove(targetUuid));
        return request;
    }

    public record TeleportRequest(UUID requesterUuid, String requesterName, UUID targetUuid, long expiresAtEpochMillis) {

        public boolean isExpired() {
            return Instant.now().toEpochMilli() > expiresAtEpochMillis;
        }
    }
}
