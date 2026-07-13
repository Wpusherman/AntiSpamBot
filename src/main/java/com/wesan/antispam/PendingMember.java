package com.wesan.antispam;

import java.time.OffsetDateTime;

public record PendingMember(
        String guildId,
        String userid,
        OffsetDateTime deadline) {}
