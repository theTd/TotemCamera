package org.totemcraft.camera.director

import com.mineclay.lib.launchCoroutine
import kotlinx.coroutines.delay
import org.bson.Document
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.spigotmc.AsyncCatcher
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender
import org.totemcraft.camera.director.Driver.currentPlaying
import org.totemcraft.camera.director.Driver.plugin
import java.util.*
import javax.script.ScriptEngineManager

data class CamScript(
    val name: String,
    var version: Int,
    var comment: String = "",
    var commands: List<ScriptCommand> = emptyList(),
    var configureScript: String? = null,
    var saveTime: Date? = null,
) {
    fun toDocument(): Document = Document("name", name)
        .append("version", version)
        .append("comment", comment)
        .append("commands", commands.map { it.toDocument() })
        .append("saveTime", saveTime)

    fun configure(args: List<String>) {
        val script = configureScript ?: return
        // get js script engine

        val engine = ScriptEngineManager().getEngineByName("nashorn")
        engine.eval(script, engine.createBindings().apply {
            put("args", args.toTypedArray())
            put("commands", commands)
        })
    }

    class PlaySession(
        val player: Player,
        val originalGameMode: GameMode,
        val commandList: List<ScriptCommand>,
        var currentCommandIndex: Int = 0,
    ) {
        val executedCommands: List<ScriptCommand> get() = commandList.subList(0, currentCommandIndex)
        val futureCommands: List<ScriptCommand> get() = commandList.subList(currentCommandIndex + 1, commandList.size)

        var currentPlayTask: PrimaryThreadSynchronizedPositionSender? = null

        fun cancel() {
            if (player.currentPlaying === this) {
                player.currentPlaying = null
                if (currentPlayTask != null) {
                    currentPlayTask?.schedule?.cancel(false)
                    player.gameMode = originalGameMode
                    PrimaryThreadSynchronizedPositionSender.unmountCamera(player)
                    PrimaryThreadSynchronizedPositionSender.removeCamera(player)
                }
            }
        }
    }

    suspend fun play(player: Player) {
        AsyncCatcher.catchOp("CamScript.play")

        val session = PlaySession(player, player.gameMode, commands)

        player.currentPlaying?.cancel()
        player.currentPlaying = session

        class TrackElement(
            val cmd: ScriptCommand,
            val cmdIdx: Int,
            val startTime: Int,
            val endTime: Int,
        ) {
            override fun toString(): String {
                return "TrackElement(cmd=${cmd.type}, startTime=$startTime, endTime=$endTime)"
            }
        }

        val tracks = mutableListOf<MutableList<TrackElement>>(mutableListOf())

        fun List<TrackElement>.canFit(e: TrackElement): Boolean {
            fun TrackElement.noConflictWith(other: TrackElement): Boolean =
                (endTime <= other.startTime && startTime <= other.startTime) ||
                        (startTime >= other.endTime && endTime >= other.endTime)
            return all { it.noConflictWith(e) }
        }

        fun appendCommand(cmd: TrackElement) {
            tracks.forEach { track ->
                if (track.canFit(cmd)) {
                    track += cmd
                    return
                }
            }
            // If no track can fit, create a new one
            tracks += mutableListOf(cmd)
        }

        var startTime = 0
        commands.forEachIndexed { idx, cmd ->
            val effectiveStartTime = (startTime - cmd.leadTimeMs).coerceAtLeast(0)
            val endTime = effectiveStartTime + cmd.lengthMs
            appendCommand(TrackElement(cmd, idx, effectiveStartTime, endTime))
            startTime += cmd.lengthMs - cmd.leadTimeMs
        }

        suspend fun playTrack(sortedElements: List<TrackElement>, mainTrack: Boolean = false) {
            var now = 0
            for (element in sortedElements) {
                val await = element.startTime - now
                if (await > 0) {
                    delay(await.toLong())
                }
                if (mainTrack) {
                    session.currentCommandIndex = element.cmdIdx
                }
                element.cmd.exec(player, session)
                now = element.endTime
            }
        }

        val mainTrack = tracks.first()
        val additionalTracks = tracks.drop(1)

        additionalTracks.forEach { track ->
            plugin.launchCoroutine {
                playTrack(track.sortedBy { it.startTime })
            }
        }
        playTrack(mainTrack.sortedBy { it.startTime }, mainTrack = true)

        player.currentPlaying = null
    }

    companion object {
        fun fromDocument(name: String, version: Int, doc: Document): CamScript {
            val comment = doc.getString("comment") ?: ""
            val commands = doc.getList("commands", Document::class.java) ?: emptyList()
            val saveDate = doc.getDate("saveTime")
            val configureScript = doc.getString("configureScript")
            return CamScript(
                name,
                version,
                comment,
                commands.mapNotNull { ScriptCommand.fromDocument(it) },
                configureScript,
                saveDate
            )
        }
    }
}
