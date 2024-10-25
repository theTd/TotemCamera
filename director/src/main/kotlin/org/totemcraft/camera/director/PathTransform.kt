package org.totemcraft.camera.director

import org.bson.Document
import kotlin.math.cos
import kotlin.math.sin

interface PathTransform {

    fun toDocument(): Document = error("not supported")

    fun transform(path: CamPath): CamPath

    companion object {

        fun fromDocument(doc: Document): PathTransform {
            val type = doc.getString("type")
                ?: throw IllegalArgumentException("Missing 'type' field in PathTransform document")
            return when (type) {
                "shift" -> {
                    val x = doc.getDouble("x") ?: 0.0
                    val y = doc.getDouble("y") ?: 0.0
                    val z = doc.getDouble("z") ?: 0.0
                    shift(x, y, z)
                }

                "rotate" -> {
                    val centerX = doc.getDouble("centerX") ?: 0.0
                    val centerZ = doc.getDouble("centerZ") ?: 0.0
                    val byDeg = doc.getDouble("byDeg") ?: 0.0
                    rotate(centerX, centerZ, byDeg)
                }

                else -> throw IllegalArgumentException("Unknown PathTransform type: $type")
            }
        }

        fun shift(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): PathTransform = object : PathTransform {
            override fun toDocument(): Document = Document("type", "shift")
                .append("x", x)
                .append("y", y)
                .append("z", z)

            override fun transform(path: CamPath): CamPath =
                path.copy(keyframes = path.keyframes.map {
                    it.copy(x = it.x + x, y = it.y + y, z = it.z + z)
                })
        }

        fun rotate(centerX: Double, centerZ: Double, byDeg: Double): PathTransform = object : PathTransform {
            override fun toDocument(): Document = Document("type", "rotate")
                .append("centerX", centerX)
                .append("centerZ", centerZ)
                .append("byDeg", byDeg)

            override fun transform(path: CamPath): CamPath = path.copy(keyframes = path.keyframes.map { rot(it) })

            fun rot(keyframe: Keyframe): Keyframe {
                val angleRadians = Math.toRadians(byDeg)

                val cosTheta = cos(angleRadians)
                val sinTheta = sin(angleRadians)

                val translatedX = keyframe.x - centerX
                val translatedZ = keyframe.z - centerZ

                val rotatedX = translatedX * cosTheta - translatedZ * sinTheta
                val rotatedZ = translatedX * sinTheta + translatedZ * cosTheta

                return Keyframe(
                    rotatedX + centerX,
                    keyframe.y,
                    rotatedZ + centerZ,
                    normalizeYaw(keyframe.yaw + byDeg.toFloat()),
                    keyframe.pitch
                )
            }
        }

        fun mirrorX(byX: Double): PathTransform {
            TODO()
        }

        fun mirrorZ(byZ: Double): PathTransform {
            TODO()
        }
    }
}
