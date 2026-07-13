package com.wesan.antispam;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public class JoinListener extends ListenerAdapter {
    private final int deadline;
    private final DeadlineScheduler scheduler;

    public JoinListener(int deadline, DeadlineScheduler scheduler) {
        this.deadline = deadline;
        this.scheduler = scheduler;
    }

    // join -> pending (to DeadlineScheduler)
    @Override
    public void onGuildMemberJoin (@NotNull GuildMemberJoinEvent event) {
        User user = event.getUser();
        Member member = event.getMember();
        System.out.println(user.getName() + " (" + user.getId() + ") joined!");
        OffsetDateTime time = member.getTimeJoined();
        System.out.println("Time: " + time);
        OffsetDateTime deadLineTime = time.plusHours(deadline);
        System.out.println("Deadline Time: " + deadLineTime);

        scheduler.register(event.getGuild().getId(), user.getId(), deadLineTime);
    }
}
