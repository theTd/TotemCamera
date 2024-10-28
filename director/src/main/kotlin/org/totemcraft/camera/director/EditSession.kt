package org.totemcraft.camera.director

import com.mineclay.lib.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import org.totemcraft.camera.director.Driver.currentPlaying
import org.totemcraft.camera.director.ScriptCommand.Companion.info
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class EditSession(
    val player: Player,
    val camScript: CamScript,
    var dirty: Boolean = false,
) {

    fun dirty() {
        dirty = true
    }

    var recordingPath: PathCommand? = null
    var recording = false

    fun tick() {
        if (!recording) return
        val path = recordingPath?.path ?: return
        val loc = player.location
        loc.run {
            path.keyframes += Keyframe(x, y, z, yaw, pitch)
        }
    }

    private fun echo(content: Component) = player.sendMessage(content)

    private fun insertCommandButton(index: Int) {
        echo(
            "    ".adv() + "[+]".green()
                .clickEvent(
                    ClickEvent.clickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/director insert-command-prompt $index"
                    )
                )
                .hoverEvent("在此插入指令".adv())
        )
    }

    fun listView(playingCommandIndex: Int? = null) {
        echo("".adv())
        echo("".adv())
        echo("".adv())
        echo("正在编辑 ${camScript.name}".adv())
        echo("${camScript.commands.size} 条指令:".adv())

        insertCommandButton(0)

        camScript.commands.forEachIndexed { index, scriptCommand ->
            val prefix =
                if (playingCommandIndex == index) "▶".red() + " ${index + 1}. ".adv()
                else "  ${index + 1}. ".adv()
            scriptCommand.listView(player, prefix, index)
            insertCommandButton(index + 1)
        }
        var buttons = "".adv()

        if (player.currentPlaying == null) {
            val playAllButton = if (camScript.commands.isEmpty()) {
                "[播放]".gray()
            } else {
                "[播放]".green().clickEvent(
                    ClickEvent.clickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/director play"
                    )
                ).hoverEvent("全部播放".adv())
            }

            buttons += playAllButton + " ".adv()
        } else {
            val stopPlayingButton = "[停止播放]".red().clickEvent(
                ClickEvent.clickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/director stop-playing"
                )
            ).hoverEvent("停止播放".adv())

            buttons += stopPlayingButton + " ".adv()
        }


        val deleteButton = "[删除]".red().clickEvent(
            ClickEvent.clickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/director delete-prompt"
            )
        ).hoverEvent("删除脚本".adv())

        if (dirty) {
            buttons += "[保存]".green().clickEvent(
                ClickEvent.clickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/director save-prompt"
                )
            ).hoverEvent("保存".adv())
            buttons += " ".adv()
            buttons += "[撤销更改]".yellow().clickEvent(
                ClickEvent.clickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/director abort"
                )
            ).hoverEvent("撤销更改".adv())
            buttons += " ".adv() + deleteButton
        } else {
            buttons += "[保存]".gray()
            buttons += " ".adv()
            buttons += "[撤销更改]".gray()
            buttons += " ".adv() + deleteButton
        }
        echo(buttons)
    }

    fun insertCommandPrompt(index: Int) {
        if (index > camScript.commands.size + 1) {
            echo("插入位置不合法".adv())
        } else {
            // select command type
            var typeSelector = "".adv()
            val ite = ScriptCommand.types.values.iterator()

            while (ite.hasNext()) {
                val next = ite.next()
                typeSelector += ("[".adv() + next.info.displayName.red() + "]".adv())
                    .clickEvent(
                        ClickEvent.clickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/director insert-command $index ${next.info.type}"
                        )
                    )
                    .hoverEvent("插入 ".adv() + next.info.displayName + "指令")
                if (ite.hasNext()) {
                    typeSelector += " ".adv()
                }
            }
            echo(typeSelector)
        }
    }

    fun insertCommand(index: Int, cmdType: KClass<out ScriptCommand>) {
        val inst = cmdType.createInstance()
        camScript.commands = camScript.commands.toMutableList().also {
            it.add(index, inst)
        }
        dirty()
        listView()
    }
}
