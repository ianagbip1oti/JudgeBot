package me.ddivad.judgebot.commands

import me.ddivad.judgebot.arguments.LowerMemberArg
import me.ddivad.judgebot.arguments.LowerUserArg
import me.ddivad.judgebot.dataclasses.*
import me.ddivad.judgebot.embeds.createHistoryEmbed
import me.ddivad.judgebot.embeds.createSelfHistoryEmbed
import me.ddivad.judgebot.services.DatabaseService
import me.ddivad.judgebot.services.LoggingService
import me.ddivad.judgebot.services.PermissionLevel
import me.ddivad.judgebot.services.infractions.BanService
import me.ddivad.judgebot.services.requiredPermissionLevel
import me.jakejmattson.discordkt.api.arguments.EitherArg
import me.jakejmattson.discordkt.api.arguments.EveryArg
import me.jakejmattson.discordkt.api.arguments.IntegerArg
import me.jakejmattson.discordkt.api.arguments.UserArg
import me.jakejmattson.discordkt.api.dsl.commands
import me.jakejmattson.discordkt.api.extensions.sendPrivateMessage
import java.awt.Color

@Suppress("unused")
fun createUserCommands(databaseService: DatabaseService,
                       config: Configuration,
                       loggingService: LoggingService,
                       banService: BanService) = commands("User") {
    guildCommand("history", "h") {
        description = "Use this to view a user's record."
        requiredPermissionLevel = PermissionLevel.Moderator
        execute(UserArg) {
            val user = databaseService.users.getOrCreateUser(args.first, guild)
            databaseService.users.incrementUserHistory(user, guild)
            respondMenu {
                createHistoryEmbed(args.first, user, guild, config, databaseService)
            }
        }
    }

    guildCommand("whatpfp") {
        description = "Perform a reverse image search of a User's profile picture"
        requiredPermissionLevel = PermissionLevel.Moderator
        execute(UserArg) {
            val user = args.first
            val reverseSearchUrl = "<https://www.google.com/searchbyimage?&image_url=${user.avatar.url}>"
            respond {
                title = "${user.tag}'s pfp"
                color = Color.MAGENTA
                description = "[Reverse Search]($reverseSearchUrl)"
                image = "${user.avatar.url}?size=512"
            }
        }
    }

    guildCommand("ban") {
        description = "Ban a member from this guild."
        requiredPermissionLevel = PermissionLevel.Staff
        execute(LowerUserArg, IntegerArg("Delete message days").makeOptional(1), EveryArg) {
            val (target, deleteDays, reason) = args
            val ban = Punishment(target.id.value, InfractionType.Ban , reason, author.id.value)
            banService.banUser(target, guild, ban, deleteDays).also {
                loggingService.userBanned(guild, target, ban)
                respond("User ${target.mention} banned")
            }
        }
    }

    guildCommand("unban") {
        description = "Unban a banned member from this guild."
        requiredPermissionLevel = PermissionLevel.Staff
        execute(UserArg) {
            val user = args.first
            guild.getBanOrNull(user.id)?.let {
                banService.unbanUser(guild, user)
                respond("${user.tag} unbanned")
                return@execute
            }
            respond("${user.mention} isn't banned from this guild.")
        }
    }

    guildCommand("setBanReason") {
        description = "Set a ban reason for a banned user"
        requiredPermissionLevel = PermissionLevel.Staff
        execute(UserArg, EveryArg("Reason")) {
            val (user, reason) = args
            val ban = Ban(user.id.value, author.id.value, reason)
            if (guild.getBanOrNull(user.id) != null) {
                if (!databaseService.guilds.checkBanExists(guild, user.id.value)) {
                    databaseService.guilds.addBan(guild, ban)
                } else {
                    databaseService.guilds.editBanReason(guild, user.id.value, reason)
                }
                respond("Ban reason for ${user.username} set to: $reason")
            } else respond("User ${user.username} isn't banned")

        }
    }

    guildCommand("getBanReason") {
        description = "Get a ban reason for a banned user"
        requiredPermissionLevel = PermissionLevel.Staff
        execute(UserArg) {
            val user = args.first
            guild.getBanOrNull(user.id)?.let {
                val reason = databaseService.guilds.getBanOrNull(guild, user.id.value)?.reason ?: it.reason
                respond(reason ?: "No reason logged")
                return@execute
            }
            respond("${user.username} isn't banned from this guild.")
        }
    }

    guildCommand("selfHistory") {
        description = "View your infraction history (contents will be DM'd)"
        requiredPermissionLevel = PermissionLevel.Everyone
        execute {
            val user = author
            val guildMember = databaseService.users.getOrCreateUser(user, guild)

            user.sendPrivateMessage {
                createSelfHistoryEmbed(user, guildMember, guild, config)
            }
        }
    }
}