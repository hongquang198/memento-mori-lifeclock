package dev.mementomori.lifeclock.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object StoreKeys {
    val CONFIGS = stringPreferencesKey("configs")
    val BINDINGS = stringPreferencesKey("bindings")
}

/** JSON is the on-disk format; the model tests pin it, this only moves bytes. */
internal object StoreCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val configs = ListSerializer(WidgetConfigRecord.serializer())
    private val bindings = ListSerializer(WidgetInstanceBinding.serializer())

    fun encodeConfigs(value: List<WidgetConfigRecord>): String =
        json.encodeToString(configs, value)

    fun decodeConfigs(raw: String?): List<WidgetConfigRecord> =
        raw?.let { json.decodeFromString(configs, it) } ?: emptyList()

    fun encodeBindings(value: List<WidgetInstanceBinding>): String =
        json.encodeToString(bindings, value)

    fun decodeBindings(raw: String?): List<WidgetInstanceBinding> =
        raw?.let { json.decodeFromString(bindings, it) } ?: emptyList()
}

internal fun Preferences.configs(): List<WidgetConfigRecord> =
    StoreCodec.decodeConfigs(this[StoreKeys.CONFIGS])

internal fun Preferences.bindings(): List<WidgetInstanceBinding> =
    StoreCodec.decodeBindings(this[StoreKeys.BINDINGS])
