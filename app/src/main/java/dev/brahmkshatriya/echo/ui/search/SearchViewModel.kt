package dev.brahmkshatriya.echo.ui.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchClient
import dev.brahmkshatriya.echo.common.models.QuickSearch
import dev.brahmkshatriya.echo.db.models.UserEntity
import dev.brahmkshatriya.echo.plugger.MusicExtension
import dev.brahmkshatriya.echo.ui.common.FeedViewModel
import dev.brahmkshatriya.echo.ui.paging.toFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    throwableFlow: MutableSharedFlow<Throwable>,
    override val extensionFlow: MutableStateFlow<MusicExtension?>,
    override val extensionListFlow: MutableStateFlow<List<MusicExtension>?>,
    override val userFlow: MutableSharedFlow<UserEntity?>,
) : FeedViewModel(throwableFlow, userFlow, extensionFlow, extensionListFlow) {

    var query: String? = null
    override suspend fun getTabs(client: ExtensionClient) =
        (client as? SearchClient)?.searchTabs(query)

    override fun getFeed(client: ExtensionClient) =
        (client as? SearchClient)?.searchFeed(query, tab)?.toFlow()

    val quickFeed = MutableStateFlow<List<QuickSearch>>(emptyList())
    fun quickSearch(query: String) {
        val extension = extensionFlow.value ?: return
        val client = extension.client
        if (client !is SearchClient) return
        viewModelScope.launch(Dispatchers.IO) {
            val list = tryWith(extension.info) { client.quickSearch(query) } ?: emptyList()
            quickFeed.value = list
        }
    }

    fun deleteSearchQuery(query: QuickSearch.QueryItem) {
        val extension = extensionFlow.value ?: return
        val client = extension.client
        if (client !is SearchClient) return
        viewModelScope.launch {
            tryWith(extension.info) { client.deleteSearchHistory(query) }
        }
    }

}