package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import com.mineclay.lib.fullscreen
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class FadeOutCommand : ScriptCommand {
    override val lengthMs: Int = 10 * 50
    override val leadTimeMs: Int = 10 * 50

    override suspend fun exec0(
        player: Player,
        session: CamScript.PlaySession,
    ) {
        player.fullscreen(fadeIn = 10, stay = 0, fadeOut = 10)
    }

    companion object : ScriptCommand.Registry {
        override val type: String = "fade-out"
        override val displayName: Component = "淡出".adv()
        override val editor: CommandHandler = command("") {}
    }
}