package com.wesan.antispam;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class MessageListener extends ListenerAdapter {
    private final DeadlineScheduler scheduler;
    public MessageListener(DeadlineScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();

        // catch message
        boolean completed = scheduler.complete(guildId, userId);
        if (completed) {
            System.out.println(guildId + ": " + userId + " completed the requirement.");
        }
    }
}
