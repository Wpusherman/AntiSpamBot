package com.wesan.antispam;

import net.dv8tion.jda.api.JDA;

import java.time.OffsetDateTime;

public class RecoveryService {
    private final JDA jda;
    private final DeadlineScheduler scheduler;
    private final int deadline;

    public RecoveryService(JDA jda, DeadlineScheduler scheduler, int deadline) {
        this.jda = jda;
        this.scheduler = scheduler;
        this.deadline = deadline;
    }

    public void recover() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime recoveryLimit = now.minusMinutes(deadline + 1);


    }
}
