package com.wesan.antispam;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

public class DeadlineScheduler {
    private final Queue<PendingMember> queue =
            new PriorityQueue<>(Comparator.comparing(PendingMember::deadline));
    private final Map<MemberKey, PendingMember> map = new HashMap<>();
    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private final JDA jda;
    private ScheduledFuture<?> nextTask;

    public DeadlineScheduler(JDA jda) {
        this.jda = jda;
    }

    // (JoinListener ->)
    public synchronized void register(String guildId, String userId, OffsetDateTime deadline, OffsetDateTime joinedAt) {
        PendingMember pendingMember = new PendingMember(guildId, userId, deadline, joinedAt);
        MemberKey key = new MemberKey(guildId, userId);

        queue.add(pendingMember);
        map.put(key, pendingMember);

        System.out.println("Registered: " + pendingMember);
        scheduleNextDeadline();
    }

    // compare
    public synchronized boolean complete(String guildId, String userId) {
        MemberKey key = new MemberKey(guildId, userId);
        PendingMember removed = map.remove(key);
        if (removed != null) {
            scheduleNextDeadline();
            return true;
        }
        return false;
    }

    public synchronized void scheduleNextDeadline() {
        if (nextTask != null && !nextTask.isDone()) {
            nextTask.cancel(false);
        }
        while (!queue.isEmpty()) {
            PendingMember next = queue.peek();
            MemberKey key = new MemberKey(
                    next.guildId(),
                    next.userId()
            );
            if (map.get(key) == next) {
                break;
            }
            queue.poll();
        }

        if (queue.isEmpty()) {
            nextTask = null;
            return;
        }
        PendingMember member = queue.peek();
        long delayMillis = Math.max(0,
                Duration.between(
                    OffsetDateTime.now(),
                    member.deadline()
                ).toMillis()
        );
        nextTask = ses.schedule(
                this::handleDeadline,
                delayMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public void handleDeadline() {
        PendingMember expired;

        synchronized (this) {
            expired = queue.poll();

            if (expired == null) { return; }
            MemberKey key = new MemberKey(
                    expired.guildId(),
                    expired.userId()
            );

            if (map.get(key) != expired) {
                scheduleNextDeadline();
                return;
            }

            map.remove(key);
            scheduleNextDeadline();
        }

        System.out.println("EXPIRED: " + expired);
        Guild guild = jda.getGuildById(expired.guildId());

        if (guild == null) {
            System.err.println("Guild not found: " + expired.guildId());
            return;
        }

        guild.retrieveMemberById(expired.userId()).queue(
                member -> {
                    ModerationService.kick(member);
                },
                error -> {
                    System.err.println("Failed to retrieve member: " + expired.userId());
                    error.printStackTrace();
                }
        );
    }
}
