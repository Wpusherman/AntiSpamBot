package com.wesan.antispam;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class MessageListener extends ListenerAdapter {
    private final RecoveryService recoveryService;

    public MessageListener(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        if (!MessageRule.isQualifyingMessage(event.getMessage())) {
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();

        // catch message
        boolean completed = recoveryService.complete(guildId, userId);
        if (completed) {
            System.out.println(guildId + ": " + userId + " completed the requirement.");
        }
    }
}
