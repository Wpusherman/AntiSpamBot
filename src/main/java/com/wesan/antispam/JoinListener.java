package com.wesan.antispam;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class JoinListener extends ListenerAdapter {
    @Override
    public void onGuildMemberJoin (@NotNull GuildMemberJoinEvent event) {
        User user = event.getUser();
        System.out.println(user.getName() + " (" + user.getId() + ") joined!");
    }
}
