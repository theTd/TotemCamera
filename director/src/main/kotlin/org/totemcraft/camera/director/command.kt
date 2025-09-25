package org.totemcraft.camera.director

import com.google.common.cache.CacheBuilder
import com.mineclay.lib.*
import com.mineclay.lib.command.ArgParser
import com.mineclay.lib.command.ArgToken
import com.mineclay.lib.command.Args
import com.mineclay.lib.command.command
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.totemcraft.camera.director.Driver.currentPlaying
import org.totemcraft.camera.director.Driver.plugin
import org.totemcraft.camera.director.ScriptCommand.Companion.currentCommandToken
import org.totemcraft.camera.director.ScriptCommand.Companion.editSession
import org.totemcraft.camera.director.ScriptCommand.Companion.info
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
            if (exists != null) error("script ${scriptName()} already exists")
            player.editSession = EditSession(player, CamScript(scriptName(), -1)).also {
                it.listView()
            }
        }
    }
    command("load") {
        val scriptName = requiredArg(scriptNameArg)
        execSuspend {
            val player = player ?: error("player only")
            val editing = player.editSession
            if (editing?.dirty == true) error("editing ${editing.camScript.name}, save or abort first")
            val load = Database.loadScript(scriptName()) ?: error("script $scriptName not found")
            player.editSession = EditSession(player, load).also {
                it.listView()
            }
        }
    }
    command("delete-command") {
        val index = requiredArg(Args.INT) { token = "index" }
        exec {
            val session = editSession
            session.camScript.run {
                commands = commands.toMutableList().also { it.removeAt(index()) }
            }
            editSession.dirty()
            editSession.listView()
        }
    }
    command("edit-command") {
        arg { idx ->
            grafter {
                val command = editSession.camScript.commands.getOrNull(idx().toIntOrNull() ?: error("invalid command"))
                    ?: error("invalid command")
                command::class.info.editor
            }.run {
                catchToken(currentCommandToken) {
                    editSession.camScript.commands.getOrNull(idx().toIntOrNull() ?: error("invalid command"))
                        ?: error("invalid command")
                }
            }
        }
    }
    command("insert-command-prompt") {
        val index = requiredArg(Args.INT) { token = "index" }
        exec {
            editSession.insertCommandPrompt(index())
        }
    }
    command("insert-command") {
        val index = requiredArg(Args.INT) { token = "index" }
        val type = requireArg("command-type").parserOrAsync {
            ScriptCommand.typeNameToType[it] ?: error("invalid command type")
        }.completorOrAsync { pre ->
            ScriptCommand.typeNameToType.keys.filter { it.contains(pre, true) }
        }
        exec {
            editSession.insertCommand(index(), type())
        }
    }

    command("save") {
        grafter { saveCommand }.set(Force, false)
    }
    command("__forceSave") {
        grafter { saveCommand }.set(Force, false)
    }

    command("save-prompt").exec {
        DialogFormDriver.create().run {
            val comment = textField("备注")
            open(player!!) {
                Bukkit.dispatchCommand(sender, "director save " + comment.result)
            }
        }
    }

    command("play").execSuspend {
        editSession.camScript.play(player!!)
        editSession.listView()
    }
    command("stop-playing").execSuspend {
        player?.currentPlaying?.cancel() ?: error("not playing")
        player?.editSession?.listView()
    }
    command("abort").exec {
        val editing = editSession
        player?.editSession = null
        echo("aborted editing ${editing.camScript.name}")
    }
    command("copy-to") {
        val newName = requireArg("new-script-name")
        execSuspend {
            val script = player?.editSession?.camScript ?: error("not editing")
            val exist = directorDatabase.loadScript(newName())
            if (exist != null) error("script ${newName()} already exists")
            val newScript = script.copy(name = newName())
            if (!directorDatabase.saveScript(newScript)) error("failed to save new script")
            player?.editSession = EditSession(player!!, newScript)
            player?.editSession?.listView()
        }
    }
    val delete = command("delete") {
        val scriptName = requiredArg(scriptNameArg)
        execSuspend {
            if (args.getOrNull(1) == "confirm") {
                if (Database.deleteScript(scriptName())) {
                    echo("deleted ${scriptName()} successfully")
                } else {
                    error("failed to delete ${scriptName()}")
                }
            }
        }
    }
    command("delete-prompt").execSuspend {
        val scriptName = editSession.camScript.name
        DialogFormDriver.create().run {
            title("确定要删除脚本 $scriptName 吗？")
            textField(".").apply {
                result = "."
            }
            open(player!!) {
                delete.call("$scriptName confirm")
            }
        }
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
