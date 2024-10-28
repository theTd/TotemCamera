package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.command.command
import com.mineclay.lib.plus
import com.mineclay.lib.yellow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.kyori.adventure.text.Component
import org.bson.Document
import org.bukkit.entity.Player
import org.totemcraft.camera.director.Driver.delay
import org.totemcraft.camera.director.ScriptCommand.Companion.currentCommand
import org.totemcraft.camera.director.ScriptCommand.Companion.editSession
import java.util.concurrent.TimeUnit

class AwaitCommand : ScriptCommand {
    companion object : ScriptCommand.Registry {
        override val type: String = "await"
        override val displayName: Component = "等候".adv()
        override val editor: CommandHandler = command("") {
            command("edit").exec {
                val cmd = currentCommand as AwaitCommand
                DialogFormDriver.create().apply {
                    title("等候指令")
                    val f = textField("等候时间(毫秒)").apply {
                        placeholder = "1000"
                    }
                    open(player!!) {
                        cmd.timeMs = f.result?.toLongOrNull() ?: formError("请输入数字")
                        editSession.dirty()
                        editSession.listView()
                    }
                }
            }
        }
    }

    var timeMs: Long = 0L

    override fun Document.write() {
        append("timeMs", timeMs)
    }

    override fun Document.read() {
        timeMs = getLong("timeMs") ?: 0L
    }

    override fun listView(player: Player, prefix: Component, index: Int) =
        player.sendMessage(prefix + "等候 $timeMs 毫秒 " + editButton(index) + " " + deleteButton(index))

    fun editButton(index: Int): Component = "[✎]".yellow()
        .clickEvent(buildClickCommand(index, "edit"))
        .hoverEvent("编辑等候时间".adv())

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun exec0(player: Player, session: CamScript.PlaySession) = delay(timeMs, TimeUnit.MILLISECONDS)
}
