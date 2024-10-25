package org.totemcraft.camera.director

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.await
import org.bson.Document
import org.bukkit.entity.Player
import org.totemcraft.camera.Camera.Point
import org.totemcraft.camera.Camera.PointSequence
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender
import org.totemcraft.camera.director.Driver.currentPath
import org.totemcraft.camera.director.Driver.plugin
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class PathCommand : ScriptCommand {
    override val type: String = "path"

    var path: CamPath = CamPath.EMPTY
    var transform: PathTransform? = null

    override fun Document.write() {
        append("path", path.toDocument())
        transform?.let {
            append("transform", it.toDocument())
        }
    }

    override fun Document.read() {
        path = CamPath.fromDocument(get("path", Document::class.java))
        transform = get("transform", Document::class.java)?.let { PathTransform.fromDocument(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun exec(player: Player, session: CamScript.PlaySession) {
        val seq = PointSequence()

        path.keyframes.forEach {
            seq.addPoints(Point(it.x, it.y, it.z, it.yaw.toDouble(), it.pitch.toDouble()))
        }

        val result = plugin.catmullRomConnect(seq, seq.first, seq.last, 1.0)

        val teleportPoints: Array<Point> = result.array()
        val points = Arrays.stream(teleportPoints).collect(Collectors.toList())
        val finishFuture = CompletableFuture<Unit>()
        val task = object : PrimaryThreadSynchronizedPositionSender(player, points) {
            override fun onFinish() {
                finishFuture.complete(Unit)
            }
        }
        val reuseCamera = session.executedCommands.any { it is PathCommand }

        if (reuseCamera) task.reuseCamera(session.originalGameMode)
        val keepCamera = session.futureCommands.any { it is PathCommand }
        if (keepCamera) {
            task.keepCamera()
        }

        val frameRate: Long = 30
        val intervalMs = 1000 / frameRate
        task.schedule = plugin.positionScheduler.scheduleAtFixedRate(
            task,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        )

        player.currentPath = task

        finishFuture.await()
        player.currentPath = null
    }

    fun transform() = transform?.transform(path) ?: path
}
