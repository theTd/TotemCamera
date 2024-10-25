package org.totemcraft.camera.director

import com.mineclay.lib.Loader
import com.mineclay.lib.delegateMetadata
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import org.totemcraft.camera.Camera
import org.totemcraft.camera.PrimaryThreadSynchronizedPositionSender

object Driver : Loader.Loadable, Listener {
    internal var plugin: Camera = JavaPlugin.getProvidingPlugin(this::class.java) as Camera

    init {
        load()
    }

    internal var Player.currentPath by plugin.delegateMetadata<PrimaryThreadSynchronizedPositionSender>("current-path")

    override fun load() {
        directorCommand.register(plugin)
        Bukkit.getPluginManager().registerEvents(this, plugin)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach {
                it.editSession?.tick()
                it.currentPath?.syncTick()
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
            session.camScript.commands += PathCommand().apply {
                path = it
            }
            session.recordingPath = null
            session.dirty = true
            e.player.sendMessage("Path recorded")
        }
    }
}
