package org.totemcraft.camera.director

import org.bson.Document
import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.util.*
import javax.script.ScriptEngineManager

data class CamScript(
    val name: String,
    var version: Int,
    var comment: String = "",
    var commands: List<ScriptCommand> = emptyList(),
    var configureScript: String? = null,
    var saveTime: Date? = null,
) {
    fun toDocument(): Document = Document("name", name)
        .append("version", version)
        .append("comment", comment)
        .append("commands", commands.map { it.toDocument() })
        .append("saveTime", saveTime)

    fun configure(args: List<String>) {
        val script = configureScript ?: return
        // get js script engine

        val engine = ScriptEngineManager().getEngineByName("nashorn")
        engine.eval(script, engine.createBindings().apply {
            put("args", args.toTypedArray())
            put("commands", commands)
        })
    }

    class PlaySession(
        val player: Player,
        val originalGameMode: GameMode,
        val commandList: List<ScriptCommand>,
        var currentCommandIndex: Int = 0,
    ) {
        val executedCommands: List<ScriptCommand> get() = commandList.subList(0, currentCommandIndex)
        val futureCommands: List<ScriptCommand> get() = commandList.subList(currentCommandIndex + 1, commandList.size)
    }

    suspend fun play(player: Player) {
        val session = PlaySession(player, player.gameMode, commands)
        for (command in commands) {
            command.exec(player, session)
            session.currentCommandIndex += 1
        }
    }

    companion object {
        fun fromDocument(name: String, version: Int, doc: Document): CamScript {
            val comment = doc.getString("comment") ?: ""
            val commands = doc.getList("commands", Document::class.java) ?: emptyList()
            val saveDate = doc.getDate("saveTime")
            val configureScript = doc.getString("configureScript")
            return CamScript(
                name,
                version,
                comment,
                commands.mapNotNull { ScriptCommand.fromDocument(it) },
                configureScript,
                saveDate
            )
        }
    }
}
