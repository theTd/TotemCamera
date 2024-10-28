package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.totemcraft.camera.Camera
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender.*
import org.totemcraft.camera.director.Driver.delay
import java.util.concurrent.TimeUnit

class HardTransitionCommand : ScriptCommand {
    companion object : ScriptCommand.Registry {
        override val type: String = "hard-transition"
        override val displayName: Component = "硬转场 (效果不好)".adv()
        override val editor: CommandHandler = command("") {}
    }

    override suspend fun exec0(player: Player, session: CamScript.PlaySession) {
        val nextPath = session.futureCommands.firstOrNull { it is PathCommand } as? PathCommand ?: return
        val nextPoint = nextPath.path.keyframes.firstOrNull() ?: return

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
