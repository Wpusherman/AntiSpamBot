package com.wesan.antispam;

import java.time.OffsetDateTime;
import java.util.*;

public class DeadlineScheduler {
    private final Queue<PendingMember> queue =
            new PriorityQueue<>(Comparator.comparing(PendingMember::deadline));
    private final Map<MemberKey, PendingMember> map = new HashMap<>();

    // (JoinListener ->)
    public void register(String guildId, String userId, OffsetDateTime deadline) {
        PendingMember pendingMember = new PendingMember(guildId, userId, deadline);
        MemberKey key = new MemberKey(guildId, userId);

        queue.add(pendingMember);
        map.put(key, pendingMember);

        System.out.println("Registered: " + pendingMember);
    }

    // compare
    public boolean complete(String guildId, String userId) {
        MemberKey key = new MemberKey(guildId, userId);
        PendingMember removed = map.remove(key);
        return removed != null;
    }
}
