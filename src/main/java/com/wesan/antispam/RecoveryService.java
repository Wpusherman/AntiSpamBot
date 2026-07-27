package com.wesan.antispam;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import java.util.*;
import java.time.OffsetDateTime;

public class RecoveryService {
    private final JDA jda;
    private final DeadlineScheduler scheduler;
    private final int deadlineHours;

    public RecoveryService(JDA jda, DeadlineScheduler scheduler, int deadlineHours) {
        this.jda = jda;
        this.scheduler = scheduler;
        this.deadlineHours = deadlineHours;
    }

    public void recover() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        OffsetDateTime from = startedAt.minusHours(deadlineHours + 1);
        for (Guild guild : jda.getGuilds()) {
            guild.loadMembers().onSuccess(
                    members -> recoverGuild(guild, members, startedAt, from)
            )
                    .onError(error -> {
                        System.err.println("Failed to load members: " + guild.getName());
                        error.printStackTrace();
                    });
        }

    }

    private void recoverGuild(Guild guild, List<Member> members, OffsetDateTime startedAt, OffsetDateTime from) {
        Map<MemberKey, PendingMember> candidates = new HashMap<>();

        for (Member member : members) {
            if (member.getUser().isBot()) {
                continue;
            }

            OffsetDateTime joinedAt = member.getTimeJoined();
            if (!joinedAt.isAfter(from)) {
                continue;
            }

            OffsetDateTime deadlineAt = joinedAt.plusHours(deadlineHours);
            MemberKey key = new MemberKey(
                    guild.getId(),
                    member.getId()
            );

            PendingMember pendingMember = new PendingMember(
                    guild.getId(),
                    member.getId(),
                    deadlineAt,
                    joinedAt
            );

            candidates.put(key, pendingMember);

            System.out.println(
                    "Recovery candidate: "
                            + member.getEffectiveName()
                            + " / joined=" + joinedAt
                            + " / deadline=" + deadlineAt
            );
        }
        System.out.println(
                "Recovery candidates in " + guild.getName()
                        + ": " + candidates.size()
        );

        scanMessageHistory(guild, candidates, startedAt);
    }

    public void scanMessageHistory(Guild guild, Map<MemberKey, PendingMember> candidates, OffsetDateTime startedAt) {
        for (PendingMember pendingMember : candidates.values()) {
            if (pendingMember.deadline().isAfter(startedAt)) {
                scheduler.register(
                        pendingMember.guildId(),
                        pendingMember.userId(),
                        pendingMember.deadline(),
                        pendingMember.joinedAt()
                );
            } else {
                guild.retrieveMemberById(pendingMember.userId()).queue(
                        member -> {
                            ModerationService.kick(member);
                        },
                        error -> {
                            System.err.println("Failed to retrieve member: " + pendingMember.userId());
                            error.printStackTrace();
                        }
                );
            }
        }
    }
}
