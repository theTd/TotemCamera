package org.totemcraft.camera.director

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
