package com.wesan.antispam;

import net.dv8tion.jda.api.entities.Member;

public class ModerationService {
    private static final String REASON = "You have been kicked from the server for violating the rules.";
    public static void kick(Member member) {
        member.kick()
                .reason(REASON)
                .queue(
                ignored -> System.out.println("Kicked: " + member.getEffectiveName() + " (" + member.getId() + ")"),
                error -> {
                    System.err.println("Failed to kick: " + member.getId());
                    error.printStackTrace();
                }
        );
    }
}
