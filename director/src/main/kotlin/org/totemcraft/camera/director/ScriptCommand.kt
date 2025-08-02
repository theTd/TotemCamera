package org.totemcraft.camera.director

import com.mineclay.lib.adv
import com.mineclay.lib.command.ArgToken
import com.mineclay.lib.command.CommandExecutor
import com.mineclay.lib.command.CommandHandler
import com.mineclay.lib.plus
import com.mineclay.lib.red
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bson.Document
import org.bukkit.entity.Player
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createInstance

interface ScriptCommand {

    interface Registry {
        val type: String
        val displayName: Component
        val editor: CommandHandler
    }

    val type: String get() = this::class.info.type

    val lengthMs: Int

    val leadTimeMs: Int get() = 0

    fun toDocument(): Document = Document("type", type).apply { write() }

    fun Document.write() {}

    fun Document.read() {}

    suspend fun exec(player: Player, session: CamScript.PlaySession) {
        player.editSession?.listView(session.currentCommandIndex)
        exec0(player, session)
    }

    suspend fun exec0(player: Player, session: CamScript.PlaySession)

    fun listView(player: Player, prefix: Component, index: Int) =
        player.sendMessage(prefix + this::class.info.displayName + " " + deleteButton(index))

    fun deleteButton(index: Int): Component = "[-]".red()
        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/director delete-command $index"))
        .hoverEvent("删除".adv())

    fun buildClickCommand(index: Int, command: String): ClickEvent =
        ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/director edit-command $index $command")

    companion object {
        val currentCommandToken = object : ArgToken<ScriptCommand> {}

        val CommandExecutor.editSession: EditSession get() = player?.editSession ?: error("not editing")
        val CommandExecutor.currentCommand: ScriptCommand get() = Companion.currentCommandToken()

        val types = listOf(
            PathCommand::class,
            AwaitCommand::class,
            ExecCommand::class,
            HardTransitionCommand::class
        )

        val typeNameToType = types.associateBy { it.info.type }

        val KClass<out ScriptCommand>.info: Registry
            get() = companionObjectInstance as? Registry
                ?: error("class $qualifiedName must have a companion object implementing ScriptCommand.Registry")

        fun fromDocument(doc: Document): ScriptCommand? {
            val type = doc.getString("type") ?: return null
            val container = typeNameToType[type]?.createInstance() ?: return null
            container.run {
                doc.read()
            }
            return container
        }
    }
}
