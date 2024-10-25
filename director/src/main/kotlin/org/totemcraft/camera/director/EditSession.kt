package org.totemcraft.camera.director

import org.bukkit.entity.Player

class EditSession(
    val player: Player,
    val camScript: CamScript,
    var dirty: Boolean = false,
) {

    var recordingPath: CamPath? = null
    var recording = false

    fun tick() {
        if (!recording) return
        val path = recordingPath ?: return
        val loc = player.location
        loc.run {
            path.keyframes += Keyframe(x, y, z, yaw, pitch)
        }
    }
}
