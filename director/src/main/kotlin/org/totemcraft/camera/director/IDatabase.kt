package org.totemcraft.camera.director

interface IDatabase {
    suspend fun loadScript(scriptName: String): CamScript?
    suspend fun listScripts(): List<String>
    suspend fun saveScript(script: CamScript): Boolean
    suspend fun deleteScript(scriptName: String): Boolean
}