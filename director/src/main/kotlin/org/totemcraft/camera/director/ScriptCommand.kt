package org.totemcraft.camera.director

import org.bson.Document
import org.bukkit.entity.Player

interface ScriptCommand {

    val type: String

    fun toDocument(): Document = Document("type", type).apply { write() }

    fun Document.write()

    fun Document.read()

    suspend fun exec(player: Player, session: CamScript.PlaySession)

    companion object {
        fun fromDocument(doc: Document): ScriptCommand? {
            val type = doc.getString("type") ?: return null
            val container = when (type) {
                "await" -> AwaitCommand()
                "path" -> PathCommand()
                else -> return null
            }
            container.run {
                doc.read()
            }
            return container
        }
    }
}
