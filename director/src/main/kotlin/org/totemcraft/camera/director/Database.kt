package org.totemcraft.camera.director

import com.mineclay.circle_property.CircleProperty
import com.mineclay.circle_property.PropertyService
import kotlinx.coroutines.future.await
import org.bson.Document
import java.util.concurrent.CompletableFuture

internal object Database : IDatabase {
    private suspend fun latestVersion(scriptName: String): Int? {
        val versions =
            PropertyService.get().listKeys(scriptName, "director_scripts.versions").await()
                .mapNotNull { it.toIntOrNull() }
        return versions.maxOrNull()
    }

    private suspend fun property(scriptName: String, version: Int): CircleProperty<Document> = PropertyService.get()
        .access("director_scripts.versions", scriptName, version.toString(), Document::class.java, false).await()

    override suspend fun loadScript(scriptName: String): CamScript? {
        val latestVersion = latestVersion(scriptName) ?: return null
        val body = property(scriptName, latestVersion).get() ?: return null
        return CamScript.fromDocument(scriptName, latestVersion, body)
    }

    override suspend fun listScripts(): List<String> = CompletableFuture.supplyAsync {
        val names = mutableListOf<String>()
        PropertyService.get().listEntities("director_scripts").forEach { names += it }
        names
    }.await()

    override suspend fun saveScript(script: CamScript): Boolean {
        val doc = script.toDocument()

        val latestVersion = latestVersion(script.name)
        if (latestVersion != null && latestVersion == script.version) return false

        val nextVersion = if (latestVersion == null) 0 else latestVersion + 1

        val container = property(script.name, nextVersion)
        container.set(doc).await()
        return true
    }

    override suspend fun deleteScript(scriptName: String): Boolean {
        latestVersion(scriptName) ?: return false
        PropertyService.get().delete(scriptName, "director_scripts").await()
        return true
    }
}
