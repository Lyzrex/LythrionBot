package net.lyzrex.lythrionbot.listener;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Instant;

public class JoinListener extends ListenerAdapter {

    private final String WELCOME_CHANNEL_ID = "1440446760907440128";
    private final String AUTO_ROLE_ID = "1440446619853000855";

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        // 1. Automatische Rolle vergeben
        Role role = event.getGuild().getRoleById(AUTO_ROLE_ID);
        if (role != null) {
            event.getGuild().addRoleToMember(event.getMember(), role).queue();
        }

        // 2. Willkommensnachricht (entsprechend deinem Screenshot)
        TextChannel channel = event.getGuild().getTextChannelById(WELCOME_CHANNEL_ID);
        if (channel != null) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("👋 Welcome to Lythrion.net!")
                    .setColor(new Color(0x2ecc71)) // Grüner Rand
                    .setDescription("Hey " + event.getMember().getAsMention() + " 💎\n\n" +
                            "Welcome to **Lythrion • CityBuild Network**  we're thrilled to have you join us! 🎉\n\n" +
                            "✨ Please take a moment to check our <#1440446765475303575> and remember to stay kind and respectful to everyone.\n" +
                            "❓ Need help? Our team and community are always ready to assist!\n\n" +
                            "🌟 Explore the server, meet new friends, and most importantly. **have fun!** 🧡")
                    .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                    .setFooter("Lythrion • CityBuild Network")
                    .setTimestamp(Instant.now());

            // Falls du das Bild aus dem Screenshot hast, hier die URL einfügen:
            eb.setImage("https://deine-bild-url.de/welcome.png");

            channel.sendMessageEmbeds(eb.build()).queue();
        }
    }
}