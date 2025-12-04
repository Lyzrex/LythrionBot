package net.lyzrex.lythrionbot.ticket;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TicketService {

    public static final String MENU_ID = "ticket:category";
    public static final String CLOSE_ID = "ticket:close";

    private final long ticketCategoryId;
    private final long staffRoleId;
    private final long logChannelId;

    public TicketService(String ticketCategoryId, String staffRoleId, String logChannelId) {
        this.ticketCategoryId = parseId(ticketCategoryId);
        this.staffRoleId = parseId(staffRoleId);
        this.logChannelId = parseId(logChannelId);
    }

    private long parseId(String value) {
        if (value == null) return 0L;
        value = value.trim();
        if (value.isEmpty() || value.equals("0")) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // /ticketpanel
    public void sendTicketPanel(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎟️ Lythrion Ticket Support")
                .setColor(0x22c55e)
                .setDescription("""
                        Need help, want to report a player or appeal a punishment?

                        • Select a category below
                        • A private ticket channel will be created for you
                        • Staff will respond as soon as possible
                        """.trim())
                .setFooter("Lythrion Support • Tickets")
                .setTimestamp(Instant.now());

        StringSelectMenu.Builder menu = StringSelectMenu.create(MENU_ID)
                .setPlaceholder("Select a ticket category...");

        for (TicketCategory cat : TicketCategory.values()) {
            menu.addOption(cat.getLabel(), cat.getId(), cat.getDescription());
        }

        event.replyEmbeds(eb.build())
                .addActionRow(menu.build())
                .queue();
    }

    // category select -> create ticket
    public void handleCategorySelect(StringSelectInteractionEvent event) {
        if (!MENU_ID.equals(event.getComponentId())) {
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("❌ This menu can only be used inside a guild.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (event.getValues().isEmpty()) {
            event.reply("❌ Invalid selection.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String value = event.getValues().get(0);
        TicketCategory cat = TicketCategory.fromId(value);
        if (cat == null) {
            event.reply("❌ Unknown ticket category.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Category parent = (ticketCategoryId != 0)
                ? guild.getCategoryById(ticketCategoryId)
                : null;

        String base = "ticket-" + cat.getId() + "-" +
                member.getUser().getName()
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-");
        if (base.length() > 90) base = base.substring(0, 90);

        EnumSet<Permission> allowUser = EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_EMBED_LINKS
        );
        EnumSet<Permission> denyEveryone = EnumSet.of(Permission.VIEW_CHANNEL);

        guild.createTextChannel(base)
                .setParent(parent)
                .addPermissionOverride(member, allowUser, EnumSet.noneOf(Permission.class))
                .addPermissionOverride(guild.getPublicRole(), EnumSet.noneOf(Permission.class), denyEveryone)
                .queue(channel -> {
                    String topic = "Ticket for " + member.getIdLong() + " | category=" + cat.getId();
                    channel.getManager().setTopic(topic).queue();

                    String staffMention = (staffRoleId != 0)
                            ? "<@&" + staffRoleId + ">"
                            : "@here";

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("🎟️ Ticket – " + cat.getLabel())
                            .setColor(0x22c55e)
                            .setDescription(
                                    member.getAsMention() + ", thanks for opening a ticket.\n\n" +
                                            "**Category:** " + cat.getLabel() + "\n\n" +
                                            "Please describe your issue in as much detail as possible.\n" +
                                            "A team member will be with you shortly."
                            )
                            .setTimestamp(Instant.now());

                    channel.sendMessage(staffMention + " | New ticket from " + member.getAsMention())
                            .setEmbeds(eb.build())
                            .setComponents(ActionRow.of(Button.danger(CLOSE_ID, "Close ticket")))
                            .queue();

                    event.reply("✅ Your ticket has been created: " + channel.getAsMention())
                            .setEphemeral(true)
                            .queue();
                }, error -> {
                    event.reply("❌ Ticket could not be created. Please contact staff.")
                            .setEphemeral(true)
                            .queue();
                });
    }

    // close + transcript
    public void handleCloseButton(ButtonInteractionEvent event) {
        if (!CLOSE_ID.equals(event.getComponentId())) {
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This button can only be used inside a guild.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!(event.getChannel() instanceof TextChannel textChannel)) {
            event.reply("❌ This button can only be used in ticket text channels.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member closer = event.getMember();
        String closerTag = closer != null ? closer.getUser().getAsTag() : "Unknown";
        long closerId = closer != null ? closer.getIdLong() : 0L;

        String topic = textChannel.getTopic();
        long ownerId = parseOwnerFromTopic(topic);
        String categoryId = parseCategoryFromTopic(topic);

        event.deferReply(true).queue();

        textChannel.getHistory().retrievePast(100).queue(messages -> {
            Collections.reverse(messages);

            String transcript = buildTranscript(textChannel, messages);

            TextChannel logChannel = (logChannelId != 0)
                    ? guild.getTextChannelById(logChannelId)
                    : null;

            if (logChannel != null) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("🎟️ Ticket closed")
                        .setColor(0xf97316)
                        .setTimestamp(Instant.now())
                        .setDescription("A ticket has been closed and archived.")
                        .addField("Channel", "#" + textChannel.getName() +
                                " (`" + textChannel.getId() + "`)", false)
                        .addField("Closed by",
                                closerTag + " (`" + closerId + "`)", true)
                        .addField("Owner",
                                ownerId != 0
                                        ? "<@" + ownerId + "> (`" + ownerId + "`)"
                                        : "Unknown",
                                true)
                        .addField("Category",
                                categoryId != null ? categoryId : "Unknown",
                                true);

                byte[] data = transcript.getBytes(StandardCharsets.UTF_8);
                FileUpload upload = FileUpload.fromData(
                        data, textChannel.getName() + "-transcript.txt"
                );

                logChannel.sendMessageEmbeds(eb.build())
                        .addFiles(upload)
                        .queue();
            }

            event.getHook().editOriginal("🔒 Ticket will be closed in 5 seconds...")
                    .queue();

            textChannel.delete().queueAfter(5, TimeUnit.SECONDS);
        }, error -> {
            event.getHook()
                    .editOriginal("❌ Failed to fetch messages for transcript, but the ticket will be closed.")
                    .queue();
            textChannel.delete().queueAfter(5, TimeUnit.SECONDS);
        });
    }

    private String buildTranscript(TextChannel channel, List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        sb.append("Transcript for #").append(channel.getName()).append("\n");
        if (channel.getTopic() != null) {
            sb.append("Topic: ").append(channel.getTopic()).append("\n");
        }
        sb.append("Channel ID: ").append(channel.getId()).append("\n");
        sb.append("Exported at: ").append(Instant.now()).append("\n");
        sb.append("====================================================\n\n");

        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;

        for (Message m : messages) {
            String time = fmt.format(m.getTimeCreated().toInstant());
            String author = m.getAuthor().getAsTag() + " (" + m.getAuthor().getId() + ")";
            String content = m.getContentDisplay();

            sb.append("[").append(time).append("] ")
                    .append(author).append(": ")
                    .append(content)
                    .append("\n");

            if (!m.getAttachments().isEmpty()) {
                sb.append("  Attachments:\n");
                m.getAttachments().forEach(att ->
                        sb.append("    - ")
                                .append(att.getFileName())
                                .append(" (")
                                .append(att.getUrl())
                                .append(")\n")
                );
            }
        }

        return sb.toString();
    }

    private long parseOwnerFromTopic(String topic) {
        if (topic == null) return 0L;
        try {
            String marker = "Ticket for ";
            int idx = topic.indexOf(marker);
            if (idx < 0) return 0L;
            int start = idx + marker.length();
            int end = topic.indexOf(' ', start);
            if (end < 0) end = topic.length();
            String idStr = topic.substring(start, end).trim();
            return Long.parseLong(idStr);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String parseCategoryFromTopic(String topic) {
        if (topic == null) return null;
        String marker = "category=";
        int idx = topic.indexOf(marker);
        if (idx < 0) return null;
        int start = idx + marker.length();
        int end = topic.indexOf(' ', start);
        if (end < 0) end = topic.length();
        return topic.substring(start, end).trim();
    }
}
