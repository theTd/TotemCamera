package org.totemcraft.camera.director

import com.mineclay.lib.Loader
import com.mineclay.lib.delegateMetadata
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import org.totemcraft.camera.Camera
import java.util.concurrent.TimeUnit

object Driver : Loader.Loadable, Listener {
    internal var plugin: Camera = JavaPlugin.getProvidingPlugin(this::class.java) as Camera

    @Suppress("OPT_IN_USAGE")
    suspend fun delay(time: Long, timeUnit: TimeUnit, async: Boolean = false) {
        suspendCancellableCoroutine { cont ->
            plugin.positionScheduler.schedule({
                if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    cont.resume(Unit) {}
                }) else Bukkit.getScheduler().runTask(plugin, Runnable {
                    cont.resume(Unit) {}
                })
            }, time, timeUnit)
        }
    }

    internal var Player.currentPlaying by plugin.delegateMetadata<CamScript.PlaySession>("current-playing")

    private val dialogFormDriver = DialogFormDriver()

    override fun load() {
        dialogFormDriver.load()
        directorCommand.register(plugin)
        Bukkit.getPluginManager().registerEvents(this, plugin)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach {
                it.editSession?.tick()
                it.currentPlaying?.currentPlayTask?.syncTick()
            }
        }, 0, 1)
        plugin.logger.info("director loaded")
    }

    @EventHandler
    fun e(e: PlayerInteractEvent) {
        if (e.action == Action.PHYSICAL) return
        val session = e.player.editSession ?: return
        if (!session.recording) return

        session.recordingPath?.let {
            session.recordingPath = null
            it.recordFinished(session)
        }
    }

    init {
        load()
    }
}
