package org.totemcraft.camera.director

import com.mineclay.lib.*
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.await
import net.kyori.adventure.text.Component
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.totemcraft.camera.Camera.Point
import org.totemcraft.camera.Camera.PointSequence
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender
import org.totemcraft.camera.director.CamScript.PlaySession
import org.totemcraft.camera.director.Driver.currentPlaying
import org.totemcraft.camera.director.Driver.plugin
import org.totemcraft.camera.director.ScriptCommand.Companion.currentCommand
import org.totemcraft.camera.director.ScriptCommand.Companion.editSession
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PathCommand : ScriptCommand {
    companion object : ScriptCommand.Registry {
        override val type: String = "path"
        override val displayName: Component = "路径".adv()
        override val editor: CommandHandler = command("") {
            command("record").execSuspend {
                val session = editSession
                val cmd = currentCommand as PathCommand
                cmd.path = CamPath()

                val player = session.player
                session.recordingPath = cmd
                session.recording = false
                object : BukkitRunnable() {
                    var countdown = 3
                    override fun run() {
                        if (!player.isOnline) return cancel()

                        if (countdown == 0) {
                            player.title(subtitle = "任意点击停止录制".adv(), fadeOut = 20)
                            session.recording = true
                            return cancel()
                        }

                        player.title(countdown.toString().red(), "准备录制".adv(), stay = 40)
                        countdown--
                    }
                }.runTaskTimer(plugin, 0, 20)
            }
            command("teleport-to-start").exec {
                val player = player!!
                val cmd = currentCommand as PathCommand
                val point = cmd.path.keyframes.firstOrNull() ?: error("path is empty")
                player.teleport(
                    Location(
                        player.world,
                        point.x,
                        point.y,
                        point.z,
                        point.yaw,
                        point.pitch
                    )
                )
            }
            command("teleport-to-end").exec {
                val player = player!!
                val cmd = currentCommand as PathCommand
                val point = cmd.path.keyframes.lastOrNull() ?: error("path is empty")
                player.teleport(
                    Location(
                        player.world,
                        point.x,
                        point.y,
                        point.z,
                        point.yaw,
                        point.pitch
                    )
                )
            }
            command("speed").exec {
                val player = player!!
                val cmd = currentCommand as PathCommand
                DialogFormDriver.create().run {
                    title("设置速度倍率(整数)")
                    val f = textField("速度倍率")
                    open(player) {
                        cmd.speed = f.result?.toIntOrNull() ?: formError("速度倍率必须为整数")
                        editSession.dirty()
                        editSession.listView()
                    }
                }
            }
            command("play").execSuspend {
                val player = player!!
                val cmd = currentCommand as PathCommand
                val s = PlaySession(player, player.gameMode, listOf(cmd))
                player.currentPlaying?.cancel()
                player.currentPlaying = s
                cmd.exec0(player, s)
                player.currentPlaying = null
            }
        }
    }

    var path: CamPath = CamPath.EMPTY
    var transform: PathTransform? = null
    var speed: Int = 1
    override val lengthMs: Int get() = path.keyframes.size * 50

    override fun Document.write() {
        append("path", path.toDocument())
        append("speed", speed)
        transform?.let {
            append("transform", it.toDocument())
        }
    }

    override fun Document.read() {
        path = CamPath.fromDocument(get("path", Document::class.java))
        speed = getInteger("speed", 1)
        transform = get("transform", Document::class.java)?.let { PathTransform.fromDocument(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun exec0(player: Player, session: PlaySession) {
        val seq = PointSequence()

        path.keyframes.forEachIndexed { index, it ->
            if (index % speed == 0) {
                seq.addPoints(Point(it.x, it.y, it.z, it.yaw.toDouble(), it.pitch.toDouble()))
            }
        }

        val finishFuture = CompletableFuture<Unit>()
        val task = object : PrimaryThreadSynchronizedPositionSender(player, seq.array().toList()) {
            override fun onFinish() {
                Bukkit.getScheduler().runTask(plugin, Runnable { finishFuture.complete(Unit) })
            }
        }

        if (session.executedCommands.any { it is PathCommand })
            task.reuseCamera(session.originalGameMode)

        if (session.futureCommands.any { it is PathCommand })
            task.keepCamera()

        val frameRate: Long = 20
        val intervalMs = 1000 / frameRate
        task.schedule = plugin.positionScheduler.scheduleAtFixedRate(
            task,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        )

        session.currentPlayTask = task
        finishFuture.await()
        session.currentPlayTask = null
    }

    override fun listView(player: Player, prefix: Component, index: Int) = player.sendMessage(
        if (path.isEmpty)
            prefix + "路径 (空) " +
                    recordButton(index) + " " +
                    deleteButton(index)
        else
            prefix + "路径 (${path.keyframes.size} ticks) " +
                    playButton(index) + " " +
                    teleportToStartPointButton(index) + " " +
                    teleportToEndPointButton(index) + " " +
                    rerecordButton(index) + " " +
                    speedButton(index) + " " +
                    deleteButton(index)
    )

    fun recordButton(index: Int): Component = "[●]".yellow().clickEvent(
        buildClickCommand(index, "record")
    ).hoverEvent("录制".adv())

    fun playButton(index: Int): Component = "[▶]".green().clickEvent(
        buildClickCommand(index, "play")
    ).hoverEvent("回放".adv())

    fun teleportToStartPointButton(index: Int): Component = "[←]".yellow().clickEvent(
        buildClickCommand(index, "teleport-to-start")
    ).hoverEvent("传送到起点".adv())

    fun teleportToEndPointButton(index: Int): Component = "[→]".yellow().clickEvent(
        buildClickCommand(index, "teleport-to-end")
    ).hoverEvent("传送到终点".adv())

    fun rerecordButton(index: Int): Component = "[↺]".yellow().clickEvent(
        buildClickCommand(index, "record")
    ).hoverEvent("重新录制".adv())

    fun speedButton(index: Int): Component = "[${speed}]".yellow().clickEvent(
        buildClickCommand(index, "speed")
    ).hoverEvent("速度".adv())

    fun transform() = transform?.transform(path) ?: path

    fun recordFinished(session: EditSession) {
        session.player.title(subtitle = "路径已录制".adv())
        session.dirty()
        session.listView()
    }
}
