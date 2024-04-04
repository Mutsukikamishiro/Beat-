package dev.brahmkshatriya.echo.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.di.ExtensionModule
import dev.brahmkshatriya.echo.ui.common.ClientLoadingAdapter
import dev.brahmkshatriya.echo.ui.common.ClientNotSupportedAdapter
import dev.brahmkshatriya.echo.utils.catchWith
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tel.jeelpa.plugger.PluginRepo
import javax.inject.Inject

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    throwableFlow: MutableSharedFlow<Throwable>,
    val extensionListFlow: ExtensionModule.ExtensionListFlow,
    private val mutableExtensionFlow: ExtensionModule.MutableFlow,
    private val pluginRepo: PluginRepo<ExtensionClient>,
    private val preferences: SharedPreferences,
) : CatchingViewModel(throwableFlow) {

    val currentExtension
        get() = mutableExtensionFlow.flow.value
    val extensionFlow = mutableExtensionFlow.flow.asStateFlow()

    override fun onInitialize() {
        viewModelScope.launch {
            tryWith {
                pluginRepo.getAllPlugins {
                    viewModelScope.launch { throwableFlow.emit(it) }
                }.catchWith(throwableFlow).collect(extensionListFlow.flow)
            }
            extensionListFlow.flow.collectLatest { list ->
                list ?: return@collectLatest
                val id = preferences.getString(LAST_EXTENSION_KEY, null)
                mutableExtensionFlow.flow.value =
                    list.find { it.metadata.id == id } ?: list.firstOrNull()
            }
        }
    }

    fun setExtension(client: ExtensionClient) {
        preferences.edit().putString(LAST_EXTENSION_KEY, client.metadata.id).apply()
        mutableExtensionFlow.flow.value = client
    }

    companion object {
        const val LAST_EXTENSION_KEY = "last_extension"

        fun Context.noClient() = SnackBarViewModel.Message(
            getString(R.string.error_no_client)
        )

        fun Context.searchNotSupported(client: String) = SnackBarViewModel.Message(
            getString(R.string.is_not_supported, getString(R.string.search), client)
        )

        fun Context.trackNotSupported(client: String) = SnackBarViewModel.Message(
            getString(R.string.is_not_supported, getString(R.string.track), client)
        )

        fun Context.radioNotSupported(client: String) = SnackBarViewModel.Message(
            getString(R.string.is_not_supported, getString(R.string.radio), client)
        )

        inline fun <reified T> getAdapterForExtension(
            it: ExtensionClient?,
            name: Int,
            adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>,
            block: ((T?) -> Unit) = {}
        ): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
            return if (it != null) {
                if (it is T) {
                    block(it)
                    adapter
                } else {
                    block(null)
                    ClientNotSupportedAdapter(name, it.metadata.name)
                }
            } else {
                block(null)
                ClientLoadingAdapter()
            }
        }

    }
}