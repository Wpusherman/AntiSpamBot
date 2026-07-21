package com.wesan.antispam;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Main extends ListenerAdapter {

    public static void main(String[] args) throws Exception { // setup
        try {
            Config config = new Config();
            String token = config.getToken();
            try {
                int deadline = config.getDeadline();
                JDA api = JDABuilder.createDefault(token)
                        .enableIntents(
                                GatewayIntent.GUILD_MEMBERS,
                                GatewayIntent.GUILD_MESSAGES
                                )
                        .build();
                DeadlineScheduler scheduler = new DeadlineScheduler(api);

                api.addEventListener(new Main(),
                        new JoinListener(deadline, scheduler),
                        new MessageListener(scheduler)
                );

            } catch (NumberFormatException e) {
                return;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Setup
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("Bot is ready!");

    }
}
