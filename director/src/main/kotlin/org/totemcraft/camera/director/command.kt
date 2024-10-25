package org.totemcraft.camera.director

import com.google.common.cache.CacheBuilder
import com.mineclay.lib.*
import com.mineclay.lib.command.ArgParser
import com.mineclay.lib.command.ArgToken
import com.mineclay.lib.command.command
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.Ticks
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.totemcraft.camera.director.Driver.plugin
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal var Player.editSession by plugin.delegateMetadata<EditSession>("director-edit-session")

private val scriptNameCache =
    CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build<Unit, List<String>>()

private val scriptNameArg = ArgParser("script-name", { it }, asyncCompleter = { s ->
    var names = scriptNameCache.getIfPresent(Unit)
    if (names == null) {
        names = CompletableFuture.supplyAsync {
            scriptNameCache.get(Unit) {
                runBlocking {
                    Database.listScripts().filter { it.contains(s, true) }
                }
            }
        }.await()
    }
    names?.filter { it.contains(s, true) } ?: emptyList()
})

private object Force : ArgToken<Boolean>

private var Player.commentWarning: Boolean by plugin.delegateMetadata<Boolean>("director-save-comment-warning")
    .defaultsTo { false }

private val saveCommand = command("") {
    argsPrompt { "[comment]".adv() }

    execSuspend {
        val player = player ?: error("player only")
        val editing = player.editSession ?: error("not editing")
        if (!editing.dirty) error("nothing to save")
        val comment = args.joinToString(" ").takeIf { it.isNotEmpty() }

        if (comment == null && !player.commentWarning) {
            player.commentWarning = true
            error("are you sure to save without comment?".yellow() + " say again to confirm".gray())
        }
        player.commentWarning = false

        val script = editing.camScript
        script.version++
        script.comment = "${player.name}: ${comment ?: "no comment"}"
        script.saveTime = Date()
        if (Database.saveScript(script)) {
            editing.dirty = false
            echo("saved ${script.name} v${script.version} successfully")
        } else {
            echo("concurrent change detected".red())
            val latest = Database.loadScript(script.name) ?: error("invalid script")
            if (!Force()) {
                echo("saved at ${latest.saveTime ?: "unknown"} by ${latest.comment}")
                error("please reload or use __forceSave")
            } else {
                script.version = ++latest.version
                if (Database.saveScript(script)) {
                    editing.dirty = false
                    echo("saved ${script.name} v${script.version} successfully (force)".yellow())
                } else error("failed to force save")
            }
        }
    }
}

internal val directorCommand = command("director") {
    command("create") {
        val scriptName = requireArg("script-name")
        execSuspend {
            val player = player ?: error("player only")
            val editing = player.editSession
            if (editing?.dirty == true) error("editing ${editing.camScript.name}, save or abort first")
            val exists = Database.loadScript(scriptName())
            if (exists != null) error("script $scriptName already exists")
            player.editSession = EditSession(player, CamScript(scriptName(), -1))
            echo("editing ${scriptName()}")
        }
    }
    command("load") {
        val scriptName = requiredArg(scriptNameArg)
        execSuspend {
            val player = player ?: error("player only")
            val editing = player.editSession
            if (editing?.dirty == true) error("editing ${editing.camScript.name}, save or abort first")
            val load = Database.loadScript(scriptName()) ?: error("script $scriptName not found")
            player.editSession = EditSession(player, load)
            echo("editing ${load.name} v${load.version}")
        }
    }

    command("command") {
        command("path").exec {
            val session = player?.editSession ?: error("not editing")
            val player = player!!
            session.recordingPath = CamPath()
            session.recording = false
            object : BukkitRunnable() {
                var countdown = 3
                override fun run() {
                    if (!player.isOnline) return cancel()

                    if (countdown == 0) {
                        player.showTitle(
                            Title.title(
                                "".adv(),
                                "任意点击停止录制".adv(),
                                Title.Times.times(Ticks.duration(0), Ticks.duration(20), Ticks.duration(20))
                            )
                        )
                        session.recording = true
                        return cancel()
                    }

                    player.showTitle(
                        Title.title(
                            countdown.toString().red(),
                            "准备开始".adv(),
                            Title.Times.times(Ticks.duration(0), Ticks.duration(40), Ticks.duration(0))
                        )
                    )
                    countdown--
                }
            }.runTaskTimer(plugin, 0, 20)
        }

        command("await") {
            val awaitMs = requireArg("await-milliseconds")
            exec {
                val session = player?.editSession ?: error("not editing")
                session.camScript.commands += AwaitCommand().apply {
                    timeMs = awaitMs().toLongOrNull() ?: error("invalid await milliseconds")
                }
                session.dirty = true
                echo("ok")
            }
        }
    }

    command("save") {
        grafter { saveCommand }.set(Force, false)
    }
    command("__forceSave") {
        grafter { saveCommand }.set(Force, false)
    }

    command("play").execSuspend {
        val session = player?.editSession ?: error("not editing")
        session.camScript.play(player!!)
        echo("done")
    }
    command("abort").exec {
        val editing = player?.editSession ?: error("not editing")
        player?.editSession = null
        echo("aborted editing ${editing.camScript.name}")
    }

    command("play-to") {
        val scriptName = requiredArg(scriptNameArg)

        argsPrompt { "[target-player]... [\"onfinish\"] [finish hook]...".adv() }
        completer { pre ->
            val finishHookMet = args.any { it == "onfinish" }
            if (finishHookMet) {
                emptyList()
            } else {
                Bukkit.getOnlinePlayers().map { it.name }.filter { it.contains(pre, true) } + "onfinish"
            }
        }

        execSuspend {
            val script = Database.loadScript(scriptName()) ?: error("script $scriptName not found")
            val finishIdx = args.indexOf("onfinish")
            val players: List<Player>
            val finishHook: String?
            if (finishIdx == -1) {
                players = args.mapNotNull { Bukkit.getPlayerExact(it) }
                finishHook = null
            } else {
                players = args.subList(0, finishIdx).mapNotNull { Bukkit.getPlayerExact(it) }
                finishHook = args.subList(finishIdx + 1, args.size).joinToString(" ")
            }

            if (players.isEmpty()) error("at least one player required")
            var anyFinish = false
            players.forEach {
                plugin.launchCoroutine {
                    script.play(it)
                    if (anyFinish) return@launchCoroutine
                    anyFinish = true
                    finishHook?.let {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), it)
                    }
                }
            }
        }
    }
}
