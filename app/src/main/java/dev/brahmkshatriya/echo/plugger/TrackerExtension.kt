package dev.brahmkshatriya.echo.plugger

import android.content.Context
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import kotlinx.coroutines.flow.StateFlow
import tel.jeelpa.plugger.PluginRepo

data class TrackerExtension(
    val metadata: ExtensionMetadata,
    val client: TrackerClient,
)

fun StateFlow<List<TrackerExtension>?>.getClient(id: String?) =
    value?.find { it.metadata.id == id }

class TrackerExtensionRepo(
    private val context: Context,
    private val pluginRepo: PluginRepo<ExtensionMetadata, TrackerClient>
) : PluginRepo<ExtensionMetadata, TrackerClient> {
    override fun getAllPlugins() = context.injectSettings<TrackerClient>(pluginRepo)
}