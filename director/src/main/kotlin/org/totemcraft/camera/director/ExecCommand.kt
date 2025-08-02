package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import com.mineclay.lib.plus
import com.mineclay.lib.yellow
import net.kyori.adventure.text.Component
import org.bson.Document
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.totemcraft.camera.director.ScriptCommand.Companion.currentCommand
import org.totemcraft.camera.director.ScriptCommand.Companion.editSession

class ExecCommand : ScriptCommand {
    companion object : ScriptCommand.Registry {
        override val type: String = "exec"
        override val displayName: Component = "执行命令".adv()
        override val editor: CommandHandler = command("") {
            command("edit").execSuspend {
                val session = editSession
                val command = currentCommand as ExecCommand
                DialogFormDriver.create().run {
                    title("执行命令")
                    val f = textField("命令").apply {
                        placeholder = "tell %player% 123"
                    }
                    open(session.player) {
                        command.command = f.result
                        session.dirty()
                        session.listView()
                    }
                }
            }
        }
    }

    var command: String? = null

    override fun Document.read() {
        command = getString("command")
    }

    override val lengthMs: Int = 0

    override fun Document.write() {
        append("command", command)
    }

    override suspend fun exec0(player: Player, session: CamScript.PlaySession) {
        val cmd = command ?: return
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.name))
    }

    override fun listView(player: Player, prefix: Component, index: Int) {
        val editButton = "[✎]".yellow()
            .clickEvent(buildClickCommand(index, "edit"))
            .hoverEvent("编辑命令".adv())

        return player.sendMessage(prefix + "执行命令: ${command ?: ""} " + editButton + " " + deleteButton(index))
    }
}
