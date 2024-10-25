package org.totemcraft.camera.director

import org.bson.Document

data class Keyframe(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
) {
    fun toDocument(): Document = Document(
        mapOf(
            "x" to x,
            "y" to y,
            "z" to z,
            "yaw" to yaw,
            "pitch" to pitch,
        )
    )

    companion object {
        fun fromDocument(doc: Document): Keyframe {
            val x = doc.getDouble("x") ?: 0.0
            val y = doc.getDouble("y") ?: 0.0
            val z = doc.getDouble("z") ?: 0.0
            val yaw = doc.getDouble("yaw")?.toFloat() ?: 0.0f
            val pitch = doc.getDouble("pitch")?.toFloat() ?: 0.0f
            return Keyframe(x, y, z, yaw, pitch)
        }
    }
}
