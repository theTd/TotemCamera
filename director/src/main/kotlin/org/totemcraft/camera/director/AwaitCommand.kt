package org.totemcraft.camera.director

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bson.Document
import org.bukkit.entity.Player
import java.util.concurrent.Executors

class AwaitCommand : ScriptCommand {
    override val type: String = "await"

    var timeMs: Long = 0L

    override fun Document.write() {
        append("timeMs", timeMs)
    }

    override fun Document.read() {
        timeMs = getLong("timeMs") ?: 0L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun exec(player: Player, session: CamScript.PlaySession) =
        suspendCancellableCoroutine { cont ->
        scheduler.schedule({
            cont.resume(Unit) {}
        }, timeMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    companion object {
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
    }
}