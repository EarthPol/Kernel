package com.earthpol.kernel.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationService {

    private final Map<UUID, ConversationPartner> lastConversationPartner = new ConcurrentHashMap<>();

    public void link(UUID firstUuid, String firstName, UUID secondUuid, String secondName) {
        lastConversationPartner.put(firstUuid, new ConversationPartner(secondUuid, secondName));
        lastConversationPartner.put(secondUuid, new ConversationPartner(firstUuid, firstName));
    }

    public Optional<ConversationPartner> partnerOf(UUID uuid) {
        return Optional.ofNullable(lastConversationPartner.get(uuid));
    }

    public void clear(UUID uuid) {
        lastConversationPartner.remove(uuid);
    }

    public record ConversationPartner(UUID uuid, String name) {
    }
}
