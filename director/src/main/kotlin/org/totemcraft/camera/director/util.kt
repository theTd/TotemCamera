package org.totemcraft.camera.director

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.Ticks
import org.bukkit.entity.Player

val directorDatabase: IDatabase get() = Database

internal fun normalizeYaw(yaw: Float): Float {
    var newYaw = yaw
    while (newYaw < -180) newYaw += 360
    while (newYaw > 180) newYaw -= 360
    return newYaw
}

internal fun normalizePitch(pitch: Float): Float {
    return pitch.coerceIn(-90f, 90f)
}

internal fun Player.title(
    title: Component = Component.empty(),
    subtitle: Component = Component.empty(),
    fadeIn: Long = 0,
    stay: Long = 20,
    fadeOut: Long = 0,
) = showTitle(
    Title.title(
        title,
        subtitle,
        Title.Times.times(Ticks.duration(fadeIn), Ticks.duration(stay), Ticks.duration(fadeOut))
    )
)
