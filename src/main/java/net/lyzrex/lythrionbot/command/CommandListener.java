package net.lyzrex.lythrionbot.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.lyzrex.lythrionbot.ConfigManager;
import net.lyzrex.lythrionbot.db.DatabaseManager;
import net.lyzrex.lythrionbot.db.SyntrixRepository;
import net.lyzrex.lythrionbot.profile.UserProfileRepository;
import net.lyzrex.lythrionbot.status.MaintenanceManager;
import net.lyzrex.lythrionbot.status.ServiceStatus;
import net.lyzrex.lythrionbot.status.StatusService;
import net.lyzrex.lythrionbot.ticket.TicketService;
import net.lyzrex.lythrionbot.game.GameService;
import net.lyzrex.lythrionbot.i18n.Language;
import net.lyzrex.lythrionbot.language.LanguageService;


import de.murmelmeister.murmelapi.group.Group;
import de.murmelmeister.murmelapi.group.GroupProvider;
import de.murmelmeister.murmelapi.punishment.PunishmentService;
import de.murmelmeister.murmelapi.punishment.audit.PunishmentLogProvider;
import de.murmelmeister.murmelapi.punishment.type.PunishmentType;
import de.murmelmeister.murmelapi.punishment.user.PunishmentCurrentUserProvider;
import de.murmelmeister.murmelapi.user.User;
import de.murmelmeister.murmelapi.user.UserProvider;
import de.murmelmeister.murmelapi.user.UserService;
import de.murmelmeister.murmelapi.user.parent.UserParent;
import de.murmelmeister.murmelapi.user.playtime.UserPlayTime;
import de.murmelmeister.murmelapi.user.playtime.UserPlayTimeProvider;

import de.murmelmeister.murmelapi.MurmelAPI;
import de.murmelmeister.murmelapi.utils.TimeUtil;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public class CommandListener extends ListenerAdapter {

    private final JDA jda;
    private final StatusService statusService;
    private final MaintenanceManager maintenanceManager;
    private final TicketService ticketService;
    private final UserProfileRepository userRepo;
    private final DatabaseManager databaseManager;
    private final GameService gameService;
    private final PunishmentService punishmentService;
    private final GroupProvider groupProvider;
    private final PunishmentLogProvider punishmentLogProvider;
    private final UserProvider userProvider;
    private final UserService userService;
    private final UserPlayTimeProvider playTimeProvider;
    private final PunishmentCurrentUserProvider punishmentCurrentUserProvider;
    private final LanguageService languageService;
    private final SyntrixRepository syntrixRepository;

    public CommandListener(JDA jda, StatusService statusService, MaintenanceManager maintenanceManager,
                           TicketService ticketService, UserProfileRepository userRepo, DatabaseManager databaseManager,
                           GameService gameService, PunishmentService punishmentService, GroupProvider groupProvider,
                           PunishmentLogProvider punishmentLogProvider, UserProvider userProvider, UserService userService,
                           UserPlayTimeProvider playTimeProvider, PunishmentCurrentUserProvider punishmentCurrentUserProvider,
                           LanguageService languageService, SyntrixRepository syntrixRepository) {
        this.jda = jda;
        this.statusService = statusService;
        this.maintenanceManager = maintenanceManager;
        this.ticketService = ticketService;
        this.userRepo = userRepo;
        this.databaseManager = databaseManager;
        this.gameService = gameService;
        this.punishmentService = punishmentService;
        this.groupProvider = groupProvider;
        this.punishmentLogProvider = punishmentLogProvider;
        this.userProvider = userProvider;
        this.userService = userService;
        this.playTimeProvider = playTimeProvider;
        this.punishmentCurrentUserProvider = punishmentCurrentUserProvider;
        this.languageService = languageService;
        this.syntrixRepository = syntrixRepository;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            String channelId = ConfigManager.getString("channels.welcome", "0");
            if (channelId.equals("0")) return;

            TextChannel channel = event.getGuild().getTextChannelById(channelId);
            if (channel != null) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("👋 Welcome to Lythrion!")
                        .setDescription("Hello " + event.getMember().getAsMention() + ", welcome to the **Lythrion Network** Discord!")
                        .setColor(0x22c55e)
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                        .addField("📜 Rules", "Please check our rules channel.", true)
                        .addField("🎮 Join", "Use `/ip` to get the server address.", true)
                        .setFooter("Member #" + event.getGuild().getMemberCount())
                        .setTimestamp(Instant.now());
                channel.sendMessageEmbeds(eb.build()).queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmd = event.getName();
        Member member = event.getMember();
        boolean admin = member != null && member.hasPermission(Permission.ADMINISTRATOR);

        try {
            switch (cmd) {
                case "status" -> handleStatus(event);
                case "maintenance" -> handleMaintenance(event, admin);
                case "botinfo" -> handleBotInfo(event);
                case "latency", "ping" -> handleLatency(event);
                case "ticketpanel" -> handleTicketPanel(event, admin);
                case "profile" -> handleProfile(event);
                case "rps" -> gameService.handleRpsPlay(event);
                case "roll" -> gameService.handleDiceRoll(event);
                case "group" -> handleGroup(event);
                case "punish" -> handlePunishMinecraft(event, admin);
                case "language" -> handleLanguage(event);
                case "ip" -> handleIp(event);
                case "help" -> handleHelp(event);
                case "tutorial" -> handleTutorial(event);
                case "leaderboard" -> handleLeaderboard(event);
                case "clear" -> handleClear(event, member);
                case "kick" -> handleKick(event, member);
                case "timeout" -> handleTimeout(event, member);
                case "announce" -> handleAnnounce(event, admin);
                case "suggest" -> handleSuggest(event);
                case "feedback" -> handleFeedback(event);
                default -> event.reply("❌ Unknown command.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (!event.isAcknowledged()) {
                event.reply("❌ Ein interner Fehler ist aufgetreten: " + e.getMessage()).setEphemeral(true).queue();
            } else {
                event.getHook().sendMessage("❌ Ein interner Fehler ist aufgetreten: " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }

    // --- COMMAND HANDLERS ---

    private void handleIp(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🌍 Lythrion Network Address")
                .setColor(0x00bcd4)
                .addField("Java Edition", "`Lythrion.net` (1.20+)", false)
                .addField("Bedrock Edition", "`bedrock.Lythrion.net`\nPort: `19132`", false)
                .setFooter("Join us now!");
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🤖 Bot Commands")
                .setColor(0x5865F2)
                .setDescription("Here is a list of available commands:")
                .addField("General", "`/ip` - Server IP\n`/status` - Network Status\n`/tutorial` - How to join\n`/leaderboard` - Global Level Top 10", false)
                .addField("User", "`/profile <name>` - Player Stats\n`/language` - Set Bot Language\n`/suggest` - Make a suggestion\n`/feedback` - Send feedback", false)
                .addField("Games", "`/rps play` - Rock Paper Scissors\n`/roll` - Dice Game", false);

        if (event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            eb.addField("Moderation", "`/punish` - Ban/Mute MC Player\n`/kick` - Kick Discord User\n`/timeout` - Timeout Discord User\n`/clear` - Delete messages\n`/announce` - Send announcement", false);
        }
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleTutorial(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📚 How to join Lythrion")
                .setColor(0xFFA500)
                .addField("🖥️ Java Edition", "1. Multiplayer > Add Server\n2. IP: `Lythrion.net`\n3. Connect!", false)
                .addField("📱 Bedrock / Pocket Edition", "1. Servers > Add Server\n2. IP: `bedrock.Lythrion.net`\n3. Port: `19132`\n4. Save & Join!", false)
                .setFooter("We support versions 1.20.1 and newer!");
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleLatency(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        CompletableFuture.supplyAsync(() -> databaseManager.ping())
                .thenAccept(dbPing -> {
                    long wsPing = jda.getGatewayPing();

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("📶 Latency")
                            .setColor(0x22c55e)
                            .addField("Gateway (Discord)", wsPing + "ms", true)
                            .addField("Database (Murmel)", (dbPing >= 0 ? dbPing + "ms" : "❌ Timeout"), true);

                    event.getHook().editOriginalEmbeds(eb.build()).queue();
                })
                .exceptionally(e -> {
                    event.getHook().editOriginal("❌ Fehler beim Abrufen der Latenz: " + e.getMessage()).queue();
                    return null;
                });
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        CompletableFuture.supplyAsync(() -> syntrixRepository.getGlobalLevelLeaderboard(10))
                .thenAccept(list -> {
                    if (list.isEmpty()) {
                        event.getHook().editOriginal("❌ No data available.").queue();
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (SyntrixRepository.LeaderboardEntry e : list) {
                        String medal = rank == 1 ? "🥇" : (rank == 2 ? "🥈" : (rank == 3 ? "🥉" : "▪️"));
                        sb.append(medal).append(" `").append(rank).append(".` **").append(e.username())
                                .append("** — Level ").append((int) e.value()).append("\n");
                        rank++;
                    }
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("🏆 Lythrion Global Level Leaderboard")
                            .setColor(0xFFD700)
                            .setDescription(sb.toString())
                            .setFooter("Top 10 Players");
                    event.getHook().editOriginalEmbeds(eb.build()).queue();
                })
                .exceptionally(e -> {
                    event.getHook().editOriginal("❌ Database Error: " + e.getMessage()).queue();
                    return null;
                });
    }

    private void handleProfile(SlashCommandInteractionEvent event) {
        String input = event.getOption("input").getAsString();
        event.deferReply().queue(); // Verhindert Timeout

        CompletableFuture.runAsync(() -> {
            try {
                // 1. User finden
                User user = userProvider.findByUsername(input);
                if (user == null) {
                    try { user = userProvider.findByMojangId(UUID.fromString(input)); } catch (Exception ignored) {}
                }

                if (user == null) {
                    event.getHook().editOriginal("❌ Profile not found for `" + input + "`.").queue();
                    return;
                }

                int userId = user.id();

                // 2. Daten parallel abrufen
                UserPlayTime playtime = playTimeProvider.findByUserId(userId);
                double wallet = syntrixRepository.getBalance(userId);
                SyntrixRepository.BankDetails bank = syntrixRepository.getBankDetails(userId);
                SyntrixRepository.PlayerStats stats = syntrixRepository.getStats(userId);
                SyntrixRepository.SkillStats skills = syntrixRepository.getSkillStats(userId);

                // 3. Rang Logik (Höchste Priorität)
                List<UserParent> parents = MurmelAPI.getUserParentProvider().getParents(userId);
                Group highestGroup = null;

                if (parents != null && !parents.isEmpty()) {
                    highestGroup = parents.stream()
                            .map(p -> groupProvider.findById(p.parentId()))
                            .filter(Objects::nonNull)
                            .max(Comparator.comparingInt(Group::priority))
                            .orElse(null);
                }

                if (highestGroup == null) {
                    highestGroup = groupProvider.findById(1); // Default
                }

                String groupName = (highestGroup != null) ? highestGroup.groupName() : "default";
                groupName = groupName.substring(0, 1).toUpperCase() + groupName.substring(1);

                // 4. Formatierung
                long pTime = playtime != null ? playtime.getPlayTime() : 0;
                String pTimeStr = formatDuration(pTime * 1000L);
                String firstJoin = user.firstLogin() != null ? user.firstLogin().toString().split("T")[0] : "N/A";
                int logins = playtime != null ? playtime.getLoginCount() : 0;

                // 5. Embed bauen
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("👤 Full Profile: " + user.username())
                        .setColor(0x00bcd4)
                        .setThumbnail("https://mc-heads.net/avatar/" + user.mojangId());

                // General Section
                eb.addField("📜 General",
                        "**Rank:** " + groupName + "\n" +
                                "**Playtime:** " + pTimeStr + "\n" +
                                "**Logins:** " + logins + "\n" +
                                "**First Join:** " + firstJoin, true);

                // Economy Section
                String bankInfo = (bank != null)
                        ? String.format("Balance: %,.2f$\nLevel: %d\nStatus: %s\nLoan: %,.2f$",
                        bank.balance(), bank.level(), bank.frozen() ? "FROZEN" : "Active", bank.loan())
                        : "No Account";

                eb.addField("💰 Economy",
                        "**Wallet:** " + String.format("%,.2f$", wallet) + "\n" +
                                "**Bank:**\n" + bankInfo, true);

                // Combat Stats
                double kd = (stats.deaths() == 0) ? stats.kills() : (double) stats.kills() / stats.deaths();
                eb.addField("⚔️ Combat Stats",
                        "**Kills:** " + stats.kills() + "\n" +
                                "**Deaths:** " + stats.deaths() + "\n" +
                                "**Mob Kills:** " + stats.mobKills() + "\n" +
                                "**K/D Ratio:** " + String.format("%.2f", kd), true);

                // Skills Section
                eb.addField("🎓 Skills (Global Level: " + (int)skills.global() + ")",
                        "⚔️ Combat: " + formatXp(skills.combat()) + "\n" +
                                "⛏️ Mining: " + formatXp(skills.mining()) + "\n" +
                                "🌾 Farming: " + formatXp(skills.farming()) + "\n" +
                                "🪓 Foraging: " + formatXp(skills.foraging()) + "\n" +
                                "🎣 Fishing: " + formatXp(skills.fishing()) + "\n" +
                                "✨ Enchanting: " + formatXp(skills.enchanting()) + "\n" +
                                "🏹 Archery: " + formatXp(skills.archery()), false);

                eb.setFooter("ID: " + user.id() + " | UUID: " + user.mojangId());

                event.getHook().editOriginalEmbeds(eb.build()).queue();

            } catch (Exception e) {
                event.getHook().editOriginal("❌ Error loading profile (DB): " + e.getMessage()).queue();
                e.printStackTrace();
            }
        });
    }

    private void handleClear(SlashCommandInteractionEvent event, Member member) {
        if (!member.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("❌ No permission.").setEphemeral(true).queue();
            return;
        }
        int amount = event.getOption("amount").getAsInt();
        if (amount < 1 || amount > 100) {
            event.reply("❌ Amount must be between 1 and 100.").setEphemeral(true).queue();
            return;
        }
        event.getChannel().getIterableHistory().takeAsync(amount).thenAccept(event.getChannel()::purgeMessages);
        event.reply("✅ Deleted " + amount + " messages.").setEphemeral(true).queue();
    }

    private void handleKick(SlashCommandInteractionEvent event, Member moderator) {
        if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("❌ No permission.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("user").getAsMember();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        if (target == null) {
            event.reply("❌ User not found.").setEphemeral(true).queue();
            return;
        }
        if (!moderator.canInteract(target)) {
            event.reply("❌ You cannot kick this user.").setEphemeral(true).queue();
            return;
        }

        target.kick(reason).queue(
                success -> event.reply("✅ Kicked **" + target.getUser().getAsTag() + "** | Reason: " + reason).queue(),
                error -> event.reply("❌ Failed to kick user.").setEphemeral(true).queue()
        );
    }

    private void handleTimeout(SlashCommandInteractionEvent event, Member moderator) {
        if (!moderator.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ No permission.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("user").getAsMember();
        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason";

        if (target == null) {
            event.reply("❌ User not found.").setEphemeral(true).queue();
            return;
        }

        long seconds = TimeUtil.parseDurationInSeconds(durationStr);
        if (seconds <= 0) {
            event.reply("❌ Invalid duration (e.g., 10m, 1h).").setEphemeral(true).queue();
            return;
        }

        target.timeoutFor(Duration.ofSeconds(seconds)).reason(reason).queue(
                success -> event.reply("✅ Timeout for **" + target.getUser().getAsTag() + "** (" + durationStr + ")").queue(),
                error -> event.reply("❌ Failed to timeout user.").setEphemeral(true).queue()
        );
    }

    private void handleAnnounce(SlashCommandInteractionEvent event, boolean admin) {
        if (!admin) {
            event.reply("❌ Only admins.").setEphemeral(true).queue();
            return;
        }
        String msg = event.getOption("message").getAsString().replace("\\n", "\n");
        var chOption = event.getOption("channel");

        TextChannel channel;
        if (chOption != null) {
            channel = chOption.getAsChannel().asTextChannel();
        } else {
            String announceId = ConfigManager.getString("channels.announcements", "0");
            channel = event.getGuild().getTextChannelById(announceId);
        }

        if (channel == null) {
            event.reply("❌ Target channel not found or not configured.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📢 Announcement")
                .setDescription(msg)
                .setColor(0x0099ff)
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(eb.build()).queue();
        event.reply("✅ Announcement sent to " + channel.getAsMention()).setEphemeral(true).queue();
    }

    private void handleSuggest(SlashCommandInteractionEvent event) {
        String idea = event.getOption("idea").getAsString();
        String channelId = ConfigManager.getString("channels.suggestions", "0");
        TextChannel channel = event.getGuild().getTextChannelById(channelId);

        if (channel == null) {
            event.reply("❌ Suggestion channel not configured.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(event.getUser().getAsTag(), null, event.getUser().getEffectiveAvatarUrl())
                .setTitle("💡 New Suggestion")
                .setDescription(idea)
                .setColor(0xfacc15)
                .setFooter("User ID: " + event.getUser().getId())
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(eb.build()).queue(msg -> {
            msg.addReaction(Emoji.fromUnicode("👍")).queue();
            msg.addReaction(Emoji.fromUnicode("👎")).queue();
        });
        event.reply("✅ Suggestion submitted!").setEphemeral(true).queue();
    }

    private void handleFeedback(SlashCommandInteractionEvent event) {
        String text = event.getOption("text").getAsString();
        String channelId = ConfigManager.getString("channels.feedback", "0");
        TextChannel channel = event.getGuild().getTextChannelById(channelId);

        if (channel == null) {
            event.reply("❌ Feedback channel not configured.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📝 Feedback Received")
                .setDescription(text)
                .setColor(0x9b59b6)
                .setFooter("From: " + event.getUser().getAsTag());

        channel.sendMessageEmbeds(eb.build()).queue();
        event.reply("✅ Thank you for your feedback!").setEphemeral(true).queue();
    }

    private void handlePunishMinecraft(SlashCommandInteractionEvent event, boolean admin) {
        if (!admin) {
            event.reply("❌ Admins only.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getOption("player").getAsString();
        String typeStr = event.getOption("type").getAsString().toUpperCase();
        String reasonText = event.getOption("reason").getAsString();
        String durStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : null;

        event.deferReply().queue();

        CompletableFuture.runAsync(() -> {
            try {
                User user = userProvider.findByUsername(targetName);
                if (user == null) {
                    event.getHook().editOriginal("❌ Player `" + targetName + "` not found in database.").queue();
                    return;
                }

                PunishmentType type;
                try {
                    type = PunishmentType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    event.getHook().editOriginal("❌ Invalid punishment type. Use BAN, MUTE, KICK.").queue();
                    return;
                }

                long duration = -1; // Permanent
                if (durStr != null) {
                    duration = TimeUtil.parseDurationInSeconds(durStr);
                    if (duration <= 0) {
                        event.getHook().editOriginal("❌ Invalid duration format (e.g. 1d, 12h).").queue();
                        return;
                    }
                }



                // Random ID Generation to prevent duplicates
                int reasonId = (int) (System.currentTimeMillis() / 1000) + ThreadLocalRandom.current().nextInt(1000, 9999);

                var reasonObj = MurmelAPI.getPunishmentReasonProvider().create(
                        reasonId,
                        type.getId(),
                        reasonText,
                        (duration == -1 ? null : duration),
                        type.isIpType(),
                        true,
                        -1 // Console/System ID
                );

                if (reasonObj == null) {
                    event.getHook().editOriginal("❌ Failed to create punishment reason object in DB.").queue();
                    return;
                }

                punishmentService.punishedUser(user.id(), reasonObj.id(), -1);
                String durTxt = duration == -1 ? "Permanent" : durStr;
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("⚖️ Punishment Applied")
                        .setColor(0xef4444)
                        .addField("Player", user.username(), true)
                        .addField("Type", type.getName(), true)
                        .addField("Reason", reasonText, false)
                        .addField("Duration", durTxt, true)
                        .setTimestamp(Instant.now());
                event.getHook().editOriginalEmbeds(eb.build()).queue();

            } catch (Exception e) {
                event.getHook().editOriginal("❌ Error applying punishment: " + e.getMessage()).queue();
                e.printStackTrace();
            }
        });
    }

    public void checkPendingVerifications() {
        String channelId = "1474181492098732275";
        String roleId = "1474181547350425641";

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        // Fetch pending requests from the database where discord_id is not yet set
        databaseManager.getDatabase().queryList(
                "SELECT v.discord_name, v.verify_code, u.username FROM discord_verify v JOIN users u ON v.user_id = u.id WHERE v.discord_id IS NULL",
                rs -> {
                    String dName = rs.getString("discord_name");
                    String code = rs.getString("verify_code");
                    String mcName = rs.getString("username");

                    // Search user cache for the Discord name
                    List<net.dv8tion.jda.api.entities.User> users = jda.getUsersByName(dName, true);

                    if (!users.isEmpty()) {
                        net.dv8tion.jda.api.entities.User dUser = users.get(0);

                        // --- CHECK: Is this Discord ID or Minecraft ID already verified? ---
                        boolean isAlreadyLinked = databaseManager.getDatabase().exists(
                                "SELECT 1 FROM discord_verify WHERE (discord_id = ? OR discord_name = ?) AND verified = TRUE",
                                s -> {
                                    s.setString(1, dUser.getId());
                                    s.setString(2, dName);
                                });

                        if (isAlreadyLinked) {
                            // Cancel the request if already linked
                            databaseManager.getDatabase().update("DELETE FROM discord_verify WHERE verify_code = ?", s -> s.setString(1, code));
                            return null;
                        }

                        EmbedBuilder eb = new EmbedBuilder()
                                .setTitle("🔐 Account Verification")
                                .setColor(0x2ecc71)
                                .setThumbnail("https://mc-heads.net/avatar/" + mcName)
                                .setDescription("Hello " + dUser.getAsMention() + "!\n\nYou started a verification for: **" + mcName + "**.")
                                .addField("Your Code", "```/link " + code + "```", false)
                                .setFooter("Use this command in Minecraft to receive your 10,000$ reward!");

                        // Send message to the channel (Ping user)
                        channel.sendMessage(dUser.getAsMention()).setEmbeds(eb.build()).queue();

                        // Update database with the confirmed Discord ID
                        databaseManager.getDatabase().update("UPDATE discord_verify SET discord_id = ? WHERE verify_code = ?", s -> {
                            s.setString(1, dUser.getId());
                            s.setString(2, code);
                        });

                        // Assign the verification role to the user
                        channel.getGuild().addRoleToMember(UserSnowflake.fromId(dUser.getId()), channel.getGuild().getRoleById(roleId)).queue();
                    }
                    return true;
                },
                null
        );
    }

    private void sendVerificationEmbed(net.dv8tion.jda.api.entities.User dUser, String mcName, String code, TextChannel channel, String roleId) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔐 Account Verification")
                .setColor(0x2ecc71)
                .setThumbnail("https://mc-heads.net/avatar/" + mcName)
                .setDescription("Hello " + dUser.getAsMention() + "!\n\nYou started verification for: **" + mcName + "**")
                .addField("Your Code", "```/link " + code + "```", false)
                .setFooter("Type this command in Minecraft to receive your 10,000$ reward!");

        // 1. Nachricht in den Kanal senden (für den Nutzer sichtbar durch Ping)
        channel.sendMessage(dUser.getAsMention() + " check your verification code below:")
                .setEmbeds(eb.build())
                .queue();

        // 2. Optional: Code zusätzlich per DM senden, damit er privat bleibt
        dUser.openPrivateChannel().queue(pc -> pc.sendMessageEmbeds(eb.build()).queue(null, err -> {
            // Falls DMs aus sind, wurde es bereits im Kanal gepostet
        }));

        // Datenbank-Update und Rolle vergeben (IDs aus deinen Screenshots)
        databaseManager.getDatabase().update("UPDATE discord_verify SET discord_id = ? WHERE verify_code = ?", s -> {
            s.setString(1, dUser.getId());
            s.setString(2, code);
        });

        // Rolle 1474181547350425641 vergeben
        channel.getGuild().addRoleToMember(UserSnowflake.fromId(dUser.getId()),
                channel.getGuild().getRoleById(roleId)).queue();
    }

    private static String extractDiscordId(String raw) {
        String value = raw.trim();
        String digitsOnly = value.replaceAll("\\D", "");
        if (!digitsOnly.isBlank() && digitsOnly.length() >= 17) {
            return digitsOnly;
        }
        return null;
    }

    private void handlePendingVerification(Member member, String mcName, String code, TextChannel channel, net.dv8tion.jda.api.entities.Role role) {
        var dUser = member.getUser();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔐 Verification Request")
                .setColor(0x2ecc71)
                .setDescription("Hello " + dUser.getAsMention() + "!\n\nYou requested verification for Minecraft account: **" + mcName + "**")
                .addField("Instruction", "Please type the following command in Minecraft:", false)
                .addField("Command", "`/link " + code + "`", false)
                .setFooter("Lythrion Security");

        channel.sendMessage(dUser.getAsMention()).setEmbeds(eb.build()).queue();

        databaseManager.getDatabase().update("UPDATE discord_verify SET discord_id = ? WHERE verify_code = ?", s -> {
            s.setString(1, dUser.getId());
            s.setString(2, code);
        });

        channel.getGuild().addRoleToMember(member, role).queue(
                success -> System.out.println("Role added to " + dUser.getName()),
                error -> System.err.println("Failed to add role: " + error.getMessage())
        );
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        long start = System.currentTimeMillis();
        event.deferReply().queue();


        CompletableFuture<ServiceStatus> mainF = CompletableFuture.supplyAsync(statusService::fetchMainStatus);
        CompletableFuture<ServiceStatus> lobF = CompletableFuture.supplyAsync(statusService::fetchLobbyStatus);
        CompletableFuture<ServiceStatus> cbF = CompletableFuture.supplyAsync(statusService::fetchCitybuildStatus);

        CompletableFuture.allOf(mainF, lobF, cbF).orTimeout(10, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            if (ex != null) {
                event.getHook().editOriginal("❌ Timeout fetching status.").queue();
                return;
            }
            ServiceStatus m = mainF.join();
            ServiceStatus l = lobF.join();
            ServiceStatus c = cbF.join();

            statusService.updatePresenceFromData(jda, m, l, c);
            event.getHook().editOriginalEmbeds(statusService.buildStatusEmbed(m, l, c)).queue();
        });
    }

    private void handleMaintenance(SlashCommandInteractionEvent event, boolean admin) {
        if ("status".equals(event.getSubcommandName())) {
            event.reply("Maintenance Status: Main=" + maintenanceManager.isMain()).setEphemeral(true).queue();
        } else if ("set".equals(event.getSubcommandName()) && admin) {
            String s = event.getOption("service").getAsString();
            boolean b = event.getOption("enabled").getAsBoolean();
            if (s.equals("main")) maintenanceManager.setMain(b);
            else if (s.equals("lobby")) maintenanceManager.setLobby(b);
            else if (s.equals("citybuild")) maintenanceManager.setCitybuild(b);
            event.reply("✅ Maintenance for " + s + " set to " + b).setEphemeral(true).queue();
        } else {
            event.reply("❌ Invalid subcommand or no permission.").setEphemeral(true).queue();
        }
    }

    private void handleBotInfo(SlashCommandInteractionEvent event) {
        long ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        event.replyEmbeds(new EmbedBuilder().setTitle("🤖 Bot Info").addField("RAM", ram + " MB", true).addField("Ping", jda.getGatewayPing() + "ms", true).build()).setEphemeral(true).queue();
    }

    private void handleTicketPanel(SlashCommandInteractionEvent event, boolean admin) {
        if (admin) ticketService.sendTicketPanel(event);
        else event.reply("❌ Admins only.").setEphemeral(true).queue();
    }

    private void handleGroup(SlashCommandInteractionEvent event) {
        String name = event.getOption("name").getAsString();
        Group g = groupProvider.findByName(name);
        if (g == null) event.reply("❌ Group not found.").setEphemeral(true).queue();
        else event.reply("Group: " + g.groupName() + " (ID: " + g.id() + ")").setEphemeral(true).queue();
    }
    private void handleEmbed(SlashCommandInteractionEvent event, boolean admin) {
        if (!admin) {
            event.reply("❌ Only administrators are allowed to create embeds.").setEphemeral(true).queue();
            return;
        }

        String title = event.getOption("title").getAsString();
        // Ersetzt eingegebene \n durch echte Zeilenumbrüche für die Formatierung
        String description = event.getOption("description").getAsString().replace("\\n", "\n");
        String colorHex = event.getOption("color") != null ? event.getOption("color").getAsString() : "#22c55e";

        try {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setColor(Color.decode(colorHex))
                    .setFooter("Lythrion Network")
                    .setTimestamp(Instant.now());

            event.getChannel().sendMessageEmbeds(eb.build()).queue();
            event.reply("✅ Embed has been sent!").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("❌ Invalid color hex code (use e.g., #ffffff).").setEphemeral(true).queue();
        }
    }
    private void handleLanguage(SlashCommandInteractionEvent event) {
        String choice = event.getOption("choice").getAsString();
        Optional<Language> targetLang = Language.fromString(choice);

        if (targetLang.isEmpty()) {
            event.reply("❌ Invalid choice.").setEphemeral(true).queue();
            return;
        }

        User user = userProvider.findByUsername(event.getUser().getName());
        if (user != null) {
            languageService.setLanguage(user.id(), targetLang.get());
            event.reply("✅ Language set to " + targetLang.get().name()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Please link your Minecraft account first (Same username required).").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        ticketService.handleCategorySelect(event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ticketService.handleCloseButton(event);
    }

    private String formatDuration(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
    }

    private String formatXp(double xp) {
        return String.format("%,.0f XP", xp);
    }
}
