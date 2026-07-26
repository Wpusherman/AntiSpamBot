package com.wesan.antispam;

import net.dv8tion.jda.api.entities.Message;

public class MessageRule {
    private MessageRule() {}

    public static boolean isQualifyingMessage(Message message) {
        if (message.getAuthor().isBot()) {
            return false;
        }

        if (message.isWebhookMessage()) {
            return false;
        }

        if (message.getType().isSystem()) {
            return false;
        } // welcome msg

        return true;
    }
}
