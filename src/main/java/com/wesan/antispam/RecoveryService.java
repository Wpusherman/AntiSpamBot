package com.wesan.antispam;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RecoveryService {
    private final JDA jda;
    private final DeadlineScheduler scheduler;
    private final int deadlineHours;
    private final int recoveryHours;
    private final Set<String> recoveringGuilds = ConcurrentHashMap.newKeySet();
    private final Set<MemberKey> completedDuringRecovery = ConcurrentHashMap.newKeySet();

    public RecoveryService(
            JDA jda,
            DeadlineScheduler scheduler,
            int deadlineHours,
            int recoveryHours
    ) {
        this.jda = jda;
        this.scheduler = scheduler;
        this.deadlineHours = deadlineHours;
        this.recoveryHours = recoveryHours;
    }

    public void recover() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        OffsetDateTime from = startedAt.minusHours(recoveryHours);

        System.out.println(
                "Starting recovery for members who joined after " + from
                        + " (lookback=" + recoveryHours + "h)"
        );

        for (Guild guild : jda.getGuilds()) {
            recoveringGuilds.add(guild.getId());
            guild.loadMembers()
                    .onSuccess(members -> recoverGuild(guild, members, startedAt, from))
                    .onError(error -> failRecovery(guild, "Failed to load members", error));
        }
    }

    public synchronized boolean complete(String guildId, String userId) {
        MemberKey key = new MemberKey(guildId, userId);
        boolean completed = scheduler.complete(guildId, userId);

        if (recoveringGuilds.contains(guildId)) {
            completedDuringRecovery.add(key);
        }

        return completed;
    }

    private void recoverGuild(
            Guild guild,
            List<Member> members,
            OffsetDateTime startedAt,
            OffsetDateTime from
    ) {
        Map<MemberKey, PendingMember> candidates = findCandidates(
                guild.getId(),
                members,
                startedAt,
                from
        );

        System.out.println(
                "Recovery candidates in " + guild.getName() + ": " + candidates.size()
        );

        if (candidates.isEmpty()) {
            finishRecovery(guild, candidates, startedAt);
            return;
        }

        scanMessageHistory(guild, candidates, startedAt, from)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        failRecovery(
                                guild,
                                "Failed to scan message history; no recovered members will be moderated",
                                error
                        );
                        return;
                    }
                    finishRecovery(guild, candidates, startedAt);
                });
    }

    Map<MemberKey, PendingMember> findCandidates(
            String guildId,
            List<Member> members,
            OffsetDateTime startedAt,
            OffsetDateTime from
    ) {
        Map<MemberKey, PendingMember> candidates = new HashMap<>();

        for (Member member : members) {
            if (member.getUser().isBot()) {
                continue;
            }

            OffsetDateTime joinedAt = member.getTimeJoined();
            if (joinedAt.isBefore(from) || joinedAt.isAfter(startedAt)) {
                continue;
            }

            PendingMember pendingMember = new PendingMember(
                    guildId,
                    member.getId(),
                    joinedAt.plusHours(deadlineHours),
                    joinedAt
            );
            candidates.put(new MemberKey(guildId, member.getId()), pendingMember);
        }

        return candidates;
    }

    CompletableFuture<Void> scanMessageHistory(
            Guild guild,
            Map<MemberKey, PendingMember> candidates,
            OffsetDateTime startedAt,
            OffsetDateTime from
    ) {
        Set<MemberKey> completed = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<?>> scans = new ArrayList<>();

        List<GuildMessageChannel> messageChannels = messageChannels(guild);
        for (GuildMessageChannel channel : readableMessageChannels(guild, messageChannels)) {
            CompletableFuture<?> scan = channel.getIterableHistory()
                    .cache(false)
                    .forEachAsync(message -> {
                        if (message.getTimeCreated().isBefore(from)) {
                            return false;
                        }

                        MemberKey key = new MemberKey(guild.getId(), message.getAuthor().getId());
                        PendingMember candidate = candidates.get(key);
                        if (candidate != null && isQualifyingRecoveryMessage(
                                message,
                                candidate,
                                startedAt
                        )) {
                            completed.add(key);
                        }

                        return completed.size() < candidates.size();
                    });
            scans.add(scan);
        }

        if (scans.isEmpty()) {
            if (!messageChannels.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No readable message-history channels in guild")
                );
            }
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(scans.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    for (MemberKey key : completed) {
                        candidates.remove(key);
                    }
                    System.out.println(
                            "Recovered completed members in " + guild.getName()
                                    + ": " + completed.size()
                    );
                });
    }

    List<GuildMessageChannel> messageChannels(Guild guild) {
        return guild.getChannelCache()
                .ofType(GuildMessageChannel.class)
                .asList();
    }

    List<GuildMessageChannel> readableMessageChannels(
            Guild guild,
            List<GuildMessageChannel> messageChannels
    ) {
        Member selfMember = guild.getSelfMember();
        return messageChannels.stream()
                .filter(channel -> selfMember.hasPermission(
                        channel,
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_HISTORY
                ))
                .toList();
    }

    static boolean isQualifyingRecoveryMessage(
            Message message,
            PendingMember candidate,
            OffsetDateTime startedAt
    ) {
        OffsetDateTime createdAt = message.getTimeCreated();
        return !createdAt.isBefore(candidate.joinedAt())
                && !createdAt.isAfter(startedAt)
                && MessageRule.isQualifyingMessage(message);
    }

    private synchronized void finishRecovery(
            Guild guild,
            Map<MemberKey, PendingMember> candidates,
            OffsetDateTime startedAt
    ) {
        int registered = 0;
        int expired = 0;

        for (Map.Entry<MemberKey, PendingMember> entry : candidates.entrySet()) {
            if (completedDuringRecovery.remove(entry.getKey())) {
                continue;
            }

            PendingMember pendingMember = entry.getValue();
            if (pendingMember.deadline().isAfter(startedAt)) {
                scheduler.register(
                        pendingMember.guildId(),
                        pendingMember.userId(),
                        pendingMember.deadline(),
                        pendingMember.joinedAt()
                );
                registered++;
            } else {
                ModerationService.kick(guild, pendingMember.userId());
                expired++;
            }
        }

        clearRecoveryState(guild.getId());
        System.out.println(
                "Recovery complete in " + guild.getName()
                        + ": registered=" + registered
                        + ", expired=" + expired
        );
    }

    private synchronized void failRecovery(Guild guild, String message, Throwable error) {
        clearRecoveryState(guild.getId());
        System.err.println(message + ": " + guild.getName());
        error.printStackTrace();
    }

    private void clearRecoveryState(String guildId) {
        recoveringGuilds.remove(guildId);
        completedDuringRecovery.removeIf(key -> key.guildId().equals(guildId));
    }
}
