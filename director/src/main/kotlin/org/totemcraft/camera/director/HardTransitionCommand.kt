package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import com.mineclay.lib.fullscreen
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.totemcraft.camera.Camera
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender.*
import org.totemcraft.camera.director.Driver.delay
import java.util.concurrent.TimeUnit

class HardTransitionCommand : ScriptCommand {
    override val lengthMs: Int = 1000
    override val leadTimeMs: Int = 20 * 50 // 20 ticks

    companion object : ScriptCommand.Registry {
        override val type: String = "hard-transition"
        override val displayName: Component = "黑屏转场".adv()
        override val editor: CommandHandler = command("") {}
    }

    override suspend fun exec0(player: Player, session: CamScript.PlaySession) {
        val nextPath = session.futureCommands.firstOrNull { it is PathCommand } as? PathCommand ?: return
        val nextPoint = nextPath.path.keyframes.firstOrNull() ?: return

        player.fullscreen(fadeIn = 10, stay = 20, fadeOut = 10)

        delay(500, TimeUnit.MILLISECONDS)
        unmountCamera(player)
        teleportCamera(
            player,
            Camera.Point(nextPoint.x, nextPoint.y, nextPoint.z, nextPoint.yaw.toDouble(), nextPoint.pitch.toDouble())
        )
        player.teleport(
            Location(
                player.world,
                nextPoint.x,
                nextPoint.y - 2.0,
                nextPoint.z,
                nextPoint.yaw,
                nextPoint.pitch
            )
        )
        delay(50, TimeUnit.MILLISECONDS)
        mountCamera(player)
    }
}
