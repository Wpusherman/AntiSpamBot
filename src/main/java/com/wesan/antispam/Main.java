package com.wesan.antispam;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends ListenerAdapter {
    private final RecoveryService recoveryService;
    private final AtomicBoolean recovered = new AtomicBoolean(false);

    public Main(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    public static void main(String[] args) { // setup
        try {
            Config config = new Config();
            String token = config.getToken();
            int deadlineHours = config.getDeadlineHours();
            int recoveryHours = config.getRecoveryHours();

            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException(
                        "Bot token is not configured."
                );
            }

            if (deadlineHours <= 0) {
                throw new IllegalArgumentException(
                        "deadlineHours must be greater than 0."
                );
            }

            if (recoveryHours < deadlineHours) {
                throw new IllegalArgumentException(
                        "recoveryHours must be greater than or equal to deadlineHours."
                );
            }

            JDA api = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES
                    )
                    .build();
            DeadlineScheduler scheduler = new DeadlineScheduler(api);

            RecoveryService recoveryService = new RecoveryService(
                    api,
                    scheduler,
                    deadlineHours,
                    recoveryHours
            );

            api.addEventListener(new Main(recoveryService),
                    new JoinListener(deadlineHours, scheduler),
                    new MessageListener(recoveryService)
            );

            Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
        }
        catch (IOException e) {
            System.err.println("Failed to load config.properties");
            e.printStackTrace();
        }
        catch (NumberFormatException e) {
            System.err.println("deadlineHours and recoveryHours must be integers");
        }
        catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    // Setup
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("Bot is ready!");
        if (recovered.compareAndSet(false, true)) {
            recoveryService.recover();
        }
    }
}
