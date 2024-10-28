package org.totemcraft.camera.director

import org.bson.Document

data class CamPath(
    var keyframes: List<Keyframe>,
    var speed: Double,
) {

    constructor() : this(emptyList(), 1.0)

    fun toDocument(): Document = Document("keyframes", keyframes.map { it.toDocument() })
        .append("speed", speed)

    val isEmpty: Boolean get() = keyframes.isEmpty()

    companion object {
        val EMPTY get() = CamPath(emptyList(), 1.0)
        fun fromDocument(doc: Document): CamPath {
            val speed = doc.getDouble("speed") ?: 1.0
            val keyframes =
                doc.getList("keyframes", Document::class.java)?.mapNotNull { Keyframe.fromDocument(it) } ?: emptyList()
            return CamPath(keyframes, speed)
        }
    }
}
