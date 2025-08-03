package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import com.mineclay.lib.plus
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import org.bson.Document
import org.bukkit.Location
import org.bukkit.entity.Player
import org.totemcraft.camera.Camera
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender.*
import org.totemcraft.camera.director.ScriptCommand.Companion.currentCommand

class TeleportCommand : ScriptCommand {
    override val lengthMs: Int = 500

    override suspend fun exec0(
        player: Player,
        session: CamScript.PlaySession,
    ) {
        position?.run {
            unmountCamera(player)
            val point = Camera.Point(x, y, z, yaw.toDouble(), pitch.toDouble())
            teleportCamera(player, point)
            player.teleport(
                Location(
                    player.world,
                    x,
                    y - 2.0,
                    z,
                    yaw, pitch
                )
            )
            delay(10 * 50L)
            mountCamera(player)
        }
    }

    var position: Location? = null

    override fun listView(player: Player, prefix: Component, index: Int) {
        if (position == null) {
            player.sendMessage(prefix + "传送 " + setPositionButton(index))
        } else {
            player.sendMessage(prefix + "传送 " + teleportButton(index) + " " + setPositionButton(index))
        }
    }

    fun setPositionButton(index: Int): Component {
        return "[设置位置]".adv().clickEvent(buildClickCommand(index, "set"))
    }

    fun teleportButton(index: Int): Component {
        return "[传送]".adv().clickEvent(buildClickCommand(index, "teleport"))
    }

    override fun Document.read() {
        val x = getDouble("x")
        val y = getDouble("y")
        val z = getDouble("z")
        val yaw = getDouble("yaw")
        val pitch = getDouble("pitch")
        position = Location(null, x, y, z, yaw.toFloat(), pitch.toFloat())
    }

    override fun Document.write() {
        position?.let {
            append("x", it.x)
            append("y", it.y)
            append("z", it.z)
            append("yaw", it.yaw.toDouble())
            append("pitch", it.pitch.toDouble())
        }
    }

    companion object : ScriptCommand.Registry {
        override val type: String = "teleport"
        override val displayName: Component = "传送".adv()
        override val editor: CommandHandler = command("") {
            command("set").exec {
                val cmd = currentCommand as TeleportCommand
                cmd.position = player?.location?.clone()
            }
            command("teleport").exec {
                val cmd = currentCommand as TeleportCommand
                val player = player ?: return@exec
                cmd.position?.let {
                    player.teleport(it.clone().also {
                        it.world = player.world
                    })
                }
            }
        }
    }
}