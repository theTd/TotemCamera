package org.totemcraft.camera.director

import com.mineclay.lib.Loader
import com.trychen.clay.spigot.api.Dialog
import com.trychen.clay.spigot.api.Dialog.FormResultEvent
import com.trychen.clay.spigot.api.DialogForm
import com.trychen.clay.spigot.api.DialogForm.TextField
import com.trychen.clay.spigot.network.cloudfunction.CloudFunctionParameters
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class DialogFormDriver : Loader.Loadable, Listener {

    abstract class FieldProcessor<T> {
        var result: T? = null
        var placeholder: String? = null
        var regex: String? = null
        var censor: Boolean = false

        abstract val resolver: (CloudFunctionParameters) -> T?
        abstract fun build(): DialogForm.Field
    }

    interface FormHandler {
        fun formError(message: String): Nothing
    }

    class FormInfo(private val id: String) {
        private var note: String? = null
        private var confirmText: String? = null
        private var dismissText: String? = null
        private var title: String = ""

        fun title(title: String) {
            this.title = title
        }

        fun note(note: String) {
            this.note = note
        }

        fun confirmText(confirmText: String) {
            this.confirmText = confirmText
        }

        fun dismissText(dismissText: String) {
            this.dismissText = dismissText
        }

        internal val fields = mutableListOf<FieldProcessor<*>>()

        fun textField(title: String): FieldProcessor<String> {
            val id = randomId()
            return object : FieldProcessor<String>() {
                override val resolver: (CloudFunctionParameters) -> String? = { result -> result.getString(id) }

                override fun build(): DialogForm.Field {
                    return TextField(id, title).also { f ->
                        placeholder?.let { f.placeholder(it) }
                        regex?.let { f.regex(it) }
                        f.filter(censor)
                    }
                }
            }.also { fields += it }
        }

        internal var handler: ((FormHandler) -> Unit) = {
        }

        fun open(player: Player, handler: (FormHandler).() -> Unit) {
            this.handler = handler
            val form = DialogForm(id, title)
            note?.let { form.note(note) }
            confirmText?.let { form.confirmText(confirmText) }
            dismissText?.let { form.dismissText(dismissText) }
            form.fields(fields.map { it.build() })
            player.setMetadata(METADATA_KEY, FixedMetadataValue(plugin, this))
            Dialog.CLIENT.open(player, form)
        }
    }

    companion object {
        private val METADATA_KEY = "CLAYCORE_DIALOG_FORM_DRIVER"
        private lateinit var plugin: JavaPlugin

        fun create(): FormInfo = FormInfo(randomId())

        private fun getFormFromPlayer(player: Player): FormInfo? {
            return player.getMetadata(METADATA_KEY).firstOrNull()?.value() as? FormInfo
        }

        private fun randomId(): String {
            return UUID.randomUUID().toString().substring(0, 8)
        }
    }

    object InterruptSignal : Throwable() {
        override fun fillInStackTrace(): Throwable? = null
    }

    @EventHandler
    fun event(e: FormResultEvent) {
        val form = getFormFromPlayer(e.player) ?: return
        form.fields.forEach {
            @Suppress("UNCHECKED_CAST")
            (it as FieldProcessor<Any?>).result = it.resolver(e.result)
        }
        try {
            form.handler(object : FormHandler {
                override fun formError(message: String): Nothing {
                    e.setProcessError(message)
                    throw InterruptSignal
                }
            })
        } catch (ignored: InterruptSignal) {
            return
        }
        e.setProcessSuccess()
    }

    override fun load() {
        plugin = JavaPlugin.getProvidingPlugin(javaClass)
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    override fun unload() {
        HandlerList.unregisterAll(this)
    }
}
