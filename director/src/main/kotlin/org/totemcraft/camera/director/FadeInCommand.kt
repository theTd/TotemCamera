package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import com.mineclay.lib.fullscreen
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.totemcraft.camera.Camera
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender.createCamera
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender.mountCamera

class FadeInCommand : ScriptCommand {
    override val lengthMs: Int = 30 * 50

    override suspend fun exec0(
        player: Player,
        session: CamScript.PlaySession,
    ) {
        val p = session.futureCommands.first { it is PathCommand } as PathCommand
        val loc = p.path.keyframes.first()


        player.fullscreen(fadeIn = 10, stay = 20, fadeOut = 10)

        // wait for fullscreen fade in
        delay(5 * 50L)

        // create and mount cam
        loc.run {
            createCamera(
                player, Camera.Point(
                    x, y, z, yaw.toDouble(), pitch.toDouble()
                )
            )
        }
        mountCamera(player)

        // screen out cam rotate
        delay(25 * 50L)
    }

    companion object : ScriptCommand.Registry {
        override val type: String = "fade-in"
        override val displayName: Component = "淡入".adv()
        override val editor: CommandHandler = command("") {}
    }
}
