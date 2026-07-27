package com.mojang.authlib.yggdrasil.request;

import com.mojang.authlib.yggdrasil.response.PresenceStatus;

public record PresenceRequest(PresenceStatus status) {
}

