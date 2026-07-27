package com.mojang.authlib.yggdrasil.response;

import java.time.Instant;
import java.util.UUID;

public record PresenceStatusDto(
    UUID profileId,
    UUID pmid,
    PresenceStatus status,
    Instant lastUpdated
) {
}
