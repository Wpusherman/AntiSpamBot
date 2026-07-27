package com.wesan.antispam;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryServiceTest {
    private static final OffsetDateTime STARTED_AT = OffsetDateTime.of(
            2026,
            7,
            27,
            12,
            0,
            0,
            0,
            ZoneOffset.UTC
    );

    @Test
    void findsOnlyHumanMembersInsideFourHourRecoveryWindow() {
        RecoveryService service = new RecoveryService(null, null, 3, 4);
        Member atStartOfWindow = member("included", STARTED_AT.minusHours(4), false);
        Member tooOld = member("old", STARTED_AT.minusHours(4).minusNanos(1), false);
        Member joinedInFuture = member("future", STARTED_AT.plusNanos(1), false);
        Member bot = member("bot", STARTED_AT.minusHours(1), true);

        Map<MemberKey, PendingMember> candidates = service.findCandidates(
                "guild",
                List.of(atStartOfWindow, tooOld, joinedInFuture, bot),
                STARTED_AT,
                STARTED_AT.minusHours(4)
        );

        assertEquals(1, candidates.size());
        PendingMember candidate = candidates.get(new MemberKey("guild", "included"));
        assertEquals(STARTED_AT.minusHours(1), candidate.deadline());
    }

    @Test
    void acceptsNormalMessagesBetweenJoinAndRecoveryStart() {
        PendingMember candidate = new PendingMember(
                "guild",
                "user",
                STARTED_AT.plusHours(2),
                STARTED_AT.minusHours(1)
        );

        assertTrue(RecoveryService.isQualifyingRecoveryMessage(
                message(STARTED_AT.minusMinutes(30), false),
                candidate,
                STARTED_AT
        ));
        assertTrue(RecoveryService.isQualifyingRecoveryMessage(
                message(candidate.joinedAt(), false),
                candidate,
                STARTED_AT
        ));
    }

    @Test
    void rejectsMessagesOutsideMembershipAndRecoveryPeriod() {
        PendingMember candidate = new PendingMember(
                "guild",
                "user",
                STARTED_AT.plusHours(2),
                STARTED_AT.minusHours(1)
        );

        assertFalse(RecoveryService.isQualifyingRecoveryMessage(
                message(candidate.joinedAt().minusNanos(1), false),
                candidate,
                STARTED_AT
        ));
        assertFalse(RecoveryService.isQualifyingRecoveryMessage(
                message(STARTED_AT.plusNanos(1), false),
                candidate,
                STARTED_AT
        ));
        assertFalse(RecoveryService.isQualifyingRecoveryMessage(
                message(STARTED_AT.minusMinutes(30), true),
                candidate,
                STARTED_AT
        ));
    }

    private static Member member(String id, OffsetDateTime joinedAt, boolean isBot) {
        User user = mock(User.class);
        when(user.isBot()).thenReturn(isBot);

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getUser()).thenReturn(user);
        when(member.getTimeJoined()).thenReturn(joinedAt);
        return member;
    }

    private static Message message(OffsetDateTime createdAt, boolean isBot) {
        User author = mock(User.class);
        when(author.isBot()).thenReturn(isBot);

        Message message = mock(Message.class);
        when(message.getAuthor()).thenReturn(author);
        when(message.getTimeCreated()).thenReturn(createdAt);
        when(message.getType()).thenReturn(MessageType.DEFAULT);
        when(message.isWebhookMessage()).thenReturn(false);
        return message;
    }
}
