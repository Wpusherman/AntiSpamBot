package com.wesan.antispam;

import java.time.OffsetDateTime;

public record PendingMember(
        String guildId,
        String userId,
        OffsetDateTime deadline,
        OffsetDateTime joinedAt
        ) {}
