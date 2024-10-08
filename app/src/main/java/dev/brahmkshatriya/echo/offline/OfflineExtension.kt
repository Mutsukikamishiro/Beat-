package dev.brahmkshatriya.echo.offline

import android.content.Context
import androidx.media3.common.MediaItem
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditorListenerClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.TrackLikeClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearch
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Audio.Companion.toAudio
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.addSongToPlaylist
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.createPlaylist
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.deletePlaylist
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.editPlaylist
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.moveSongInPlaylist
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.removeSongFromPlaylist
import dev.brahmkshatriya.echo.offline.MediaStoreUtils.searchBy
import dev.brahmkshatriya.echo.plugger.ExtensionMetadata
import dev.brahmkshatriya.echo.plugger.ImportType
import dev.brahmkshatriya.echo.utils.getFromCache
import dev.brahmkshatriya.echo.utils.saveToCache
import dev.brahmkshatriya.echo.utils.toData
import dev.brahmkshatriya.echo.utils.toJson

class OfflineExtension(val context: Context) : ExtensionClient, HomeFeedClient, TrackClient,
    AlbumClient, ArtistClient, PlaylistClient, RadioClient, SearchClient, LibraryClient,
    TrackLikeClient, PlaylistEditorListenerClient {

    companion object {
        val metadata = ExtensionMetadata(
            className = "OfflineExtension",
            path = "",
            importType = ImportType.Inbuilt,
            id = "echo_offline",
            name = "Offline",
            description = "Offline extension",
            version = "1.0.0",
            author = "Echo",
            iconUrl = null
        )
    }

    override val settingItems: List<Setting> = listOf(
        SettingSwitch(
            "Refresh Library on Reload",
            "refresh_library",
            "Scan the device every time the feed is loaded",
            false
        )
    )

    private val refreshLibrary
        get() = setting.getBoolean("refresh_library") ?: true

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        this.setting = settings
    }

    lateinit var library: MediaStoreUtils.LibraryStoreClass
    private fun find(artist: Artist) =
        library.artistMap[artist.id.toLongOrNull()]

    private fun find(album: Album) =
        library.albumList.find { it.id == album.id.toLong() }

    private fun find(playlist: Playlist) =
        library.playlistList.find { it.id == playlist.id.toLong() }

    override suspend fun onExtensionSelected() {
        library = MediaStoreUtils.getAllSongs(context)
    }

    override suspend fun getHomeTabs() = listOf(
        "All", "Songs", "Albums", "Artists", "Genres"
    ).map { Tab(it, it) }

    private fun List<Shelf>.toPaged() = PagedData.Single { this }

    override fun getHomeFeed(tab: Tab?): PagedData<Shelf> {
        if (refreshLibrary) library = MediaStoreUtils.getAllSongs(context)
        fun List<EchoMediaItem>.sorted() = sortedBy { it.title.lowercase() }
            .map { it.toShelf() }.toPaged()
        return when (tab?.id) {
            "Songs" -> library.songList.map { it.toTrack().toMediaItem() }.sorted()
            "Albums" -> library.albumList.map { it.toAlbum().toMediaItem() }.sorted()
            "Artists" -> library.artistMap.values.map { it.toArtist().toMediaItem() }.sorted()
            "Genres" -> library.genreList.map { it.toShelf() }.toPaged()
            else -> run {
                val recentlyAdded = library.songList.sortedByDescending {
                    it.mediaMetadata.extras!!.getLong("ModifiedDate")
                }.map { it.toTrack() }
                val albums = library.albumList.map {
                    it.toAlbum().toMediaItem()
                }.shuffled()
                val artists = library.artistMap.values.map {
                    it.toArtist().toMediaItem()
                }.shuffled()
                listOf(
                    Shelf.Lists.Tracks(
                        context.getString(R.string.recently_added),
                        recentlyAdded.take(6),
                        more = PagedData.Single { recentlyAdded }.takeIf { albums.size > 6 }),
                    Shelf.Lists.Items(
                        context.getString(R.string.albums),
                        albums.take(10),
                        more =
                        PagedData.Single<EchoMediaItem> { albums }.takeIf { albums.size > 10 }),
                    Shelf.Lists.Items(
                        context.getString(R.string.artists),
                        artists.take(10),
                        more =
                        PagedData.Single<EchoMediaItem> { artists }.takeIf { albums.size > 10 })
                ) + library.songList.map {
                    it.toTrack().toMediaItem().toShelf()
                }
            }.toPaged()
        }
    }

    override suspend fun loadTrack(track: Track) = track
    override suspend fun getStreamableMedia(streamable: Streamable): Streamable.Media {
        return streamable.id.toAudio().toMedia()
    }

    override fun getShelves(track: Track): PagedData<Shelf> =
        PagedData.Single { listOf() }

    override suspend fun loadAlbum(album: Album) =
        find(album)!!.toAlbum()

    override fun loadTracks(album: Album): PagedData<Track> = PagedData.Single {
        find(album)!!.songList.sortedBy { it.mediaMetadata.trackNumber }.map { it.toTrack() }
    }

    private fun getArtistsWithCategories(
        artists: List<Artist>, filter: (MediaItem) -> Boolean
    ) = artists.map { small ->
        val artist = find(small)
        val category = artist?.songList?.filter {
            filter(it)
        }?.map { it.toTrack().toMediaItem() }?.ifEmpty { null }?.let { tracks ->
            val items = tracks as List<EchoMediaItem>
            Shelf.Lists.Items(
                context.getString(R.string.more_by_artist, small.name), items,
                more = PagedData.Single { items }
            )
        }
        listOfNotNull(artist.toArtist().toMediaItem().toShelf(), category)
    }.flatten()

    override fun getShelves(album: Album): PagedData<Shelf> = PagedData.Single {
        getArtistsWithCategories(album.artists) {
            it.mediaMetadata.extras?.getLong("AlbumId") != album.id.toLong()
        }
    }

    override suspend fun loadArtist(small: Artist) =
        find(small)!!.toArtist()

    override fun getShelves(artist: Artist) = PagedData.Single<Shelf> {
        find(artist)?.run {
            val tracks = songList.map { it.toTrack().toMediaItem() }.ifEmpty { null }
            val albums = albumList.map { it.toAlbum().toMediaItem() }.ifEmpty { null }
            listOfNotNull(
                tracks?.let {
                    Shelf.Lists.Items(
                        context.getString(R.string.songs), it, more = PagedData.Single { tracks }
                    )
                },
                albums?.let {
                    Shelf.Lists.Items(
                        context.getString(R.string.albums), it, more = PagedData.Single { albums }
                    )
                }
            )
        } ?: listOf()
    }

    override suspend fun loadPlaylist(playlist: Playlist) =
        find(playlist)!!.toPlaylist()

    override fun loadTracks(playlist: Playlist): PagedData<Track> = PagedData.Single {
        find(playlist)!!.songList.map { it.toTrack() }
    }

    override fun getShelves(playlist: Playlist) = PagedData.Single<Shelf> {
        emptyList()
    }

    private fun createRadioPlaylist(mediaItem: EchoMediaItem): Radio {
        val id = "radio_${mediaItem.hashCode()}"
        val title = mediaItem.title
        return Radio(
            id = id,
            title = context.getString(R.string.item_radio, title),
            extras = mapOf("mediaItem" to mediaItem.toJson())
        )
    }

    override fun loadTracks(radio: Radio): PagedData<Track> = PagedData.Single {
        val mediaItem = radio.extras["mediaItem"]!!.toData<EchoMediaItem>()
        when (mediaItem) {
            is EchoMediaItem.Lists.AlbumItem -> {
                val album = mediaItem.album
                val tracks = loadTracks(album).loadAll().asSequence()
                    .map { it.artists }.flatten()
                    .map { artist -> find(artist)?.songList?.map { it.toTrack() }!! }.flatten()
                    .filter { it.album?.id != album.id }.take(25)

                val randomTracks = library.songList.shuffled().take(25).map { it.toTrack() }
                (tracks + randomTracks).distinctBy { it.id }.toMutableList()
            }

            is EchoMediaItem.Lists.PlaylistItem -> {
                val playlist = mediaItem.playlist
                val tracks = loadTracks(playlist).loadAll()
                val randomTracks = library.songList.shuffled().take(25).map { it.toTrack() }
                (tracks + randomTracks).distinctBy { it.id }.toMutableList()
            }

            is EchoMediaItem.Profile.ArtistItem -> {
                val artist = mediaItem.artist
                val tracks = find(artist)?.songList?.map { it.toTrack() } ?: emptyList()
                val randomTracks = library.songList.shuffled().take(25).map { it.toTrack() }
                (tracks + randomTracks).distinctBy { it.id }.toMutableList()
            }

            is EchoMediaItem.TrackItem -> {
                val track = mediaItem.track
                val albumTracks = track.album?.let { loadTracks(loadAlbum(it)).loadAll() }
                val artistTracks = track.artists.map { artist ->
                    find(artist)?.songList ?: emptyList()
                }.flatten().map { it.toTrack() }
                val randomTracks = library.songList.shuffled().take(25).map { it.toTrack() }
                val allTracks =
                    listOfNotNull(albumTracks, artistTracks, randomTracks).flatten()
                        .distinctBy { it.id }
                        .toMutableList()
                allTracks.removeIf { it.id == track.id }
                allTracks
            }

            else -> throw IllegalAccessException()
        }.shuffled()
    }

    override suspend fun radio(track: Track, context: EchoMediaItem?) =
        createRadioPlaylist(track.toMediaItem())

    override suspend fun radio(album: Album) = createRadioPlaylist(album.toMediaItem())
    override suspend fun radio(artist: Artist) = createRadioPlaylist(artist.toMediaItem())
    override suspend fun radio(playlist: Playlist) = createRadioPlaylist(playlist.toMediaItem())
    override suspend fun radio(user: User): Radio = throw IllegalAccessException()

    override suspend fun quickSearch(query: String?): List<QuickSearch> {
        return if (query.isNullOrBlank()) {
            getHistory().map { QuickSearch.QueryItem(it, true) }
        } else listOf()
    }

    override suspend fun deleteSearchHistory(query: QuickSearch.QueryItem) {
        val history = getHistory().toMutableList()
        history.remove(query.query)
        context.saveToCache("search_history", history, "offline")
    }

    private fun getHistory() = context.getFromCache<List<String>>("search_history", "offline")
        ?.distinct()?.take(5)
        ?: emptyList()

    private fun saveInHistory(query: String) {
        val history = getHistory().toMutableList()
        history.add(0, query)
        context.saveToCache("search_history", history, "offline")
    }

    override suspend fun searchTabs(query: String?) =
        listOf("All", "Tracks", "Albums", "Artists").map { Tab(it, it) }

    override fun searchFeed(query: String?, tab: Tab?) = run {
        query ?: return@run emptyList()
        saveInHistory(query)
        val tracks = library.songList.map { it.toTrack() }.searchBy(query) {
            listOf(it.title, it.album?.title) + it.artists.map { artist -> artist.name }
        }.map { it.first to it.second.toMediaItem() }
        val albums = library.albumList.map { it.toAlbum() }.searchBy(query) {
            listOf(it.title) + it.artists.map { artist -> artist.name }
        }.map { it.first to it.second.toMediaItem() }
        val artists = library.artistMap.values.map { it.toArtist() }.searchBy(query) {
            listOf(it.name)
        }.map { it.first to it.second.toMediaItem() }

        when (tab?.id) {
            "Tracks" -> tracks.map { it.second.toShelf() }
            "Albums" -> albums.map { it.second.toShelf() }
            "Artists" -> artists.map { it.second.toShelf() }
            else -> {
                val items = listOf(
                    "Tracks" to tracks, "Albums" to albums, "Artist" to artists
                ).sortedBy { it.second.firstOrNull()?.first ?: 20 }
                    .map { it.first to it.second.map { pair -> pair.second } }
                    .filter { it.second.isNotEmpty() }

                val exactMatch = items.firstNotNullOfOrNull {
                    it.second.find { item -> item.title.contains(query, true) }
                }?.toShelf()

                val containers = items.map { (title, items) ->
                    Shelf.Lists.Items(title, items, more = PagedData.Single { items })
                }

                listOf(listOfNotNull(exactMatch), containers).flatten()
            }
        }
    }.toPaged()

    override suspend fun getLibraryTabs(): List<Tab> = listOf(
        "Playlists", "Folders"
    ).map { Tab(it, it) }

    override fun getLibraryFeed(tab: Tab?): PagedData<Shelf> {
        if (refreshLibrary) library = MediaStoreUtils.getAllSongs(context)
        return when (tab?.id) {
            "Folders" -> library.folderStructure.folderList.entries.first().value.toShelf(null).items!!
            else -> {
                library.playlistList.map { it.toPlaylist().toMediaItem().toShelf() }
                    .toPaged()
            }
        }
    }

    override suspend fun listEditablePlaylists() = library.playlistList.map { it.toPlaylist() }

    override suspend fun likeTrack(track: Track, isLiked: Boolean) {
        val playlist = library.likedPlaylist.id
        if (isLiked) context.addSongToPlaylist(playlist, track.id.toLong(), 0)
        else {
            val index = library.likedPlaylist.songList.indexOfFirst { it.mediaId == track.id }
            context.removeSongFromPlaylist(playlist, index)
        }
        library = MediaStoreUtils.getAllSongs(context)
    }

    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        val id = context.createPlaylist(title)
        library = MediaStoreUtils.getAllSongs(context)
        return library.playlistList.find { it.id == id }!!.toPlaylist()
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        context.deletePlaylist(playlist.id.toLong())
        library = MediaStoreUtils.getAllSongs(context)
    }

    override suspend fun editPlaylistMetadata(
        playlist: Playlist, title: String, description: String?
    ) {
        context.editPlaylist(playlist.id.toLong(), title)
    }

    override suspend fun addTracksToPlaylist(
        playlist: Playlist, tracks: List<Track>, index: Int, new: List<Track>
    ) {
        new.forEach {
            context.addSongToPlaylist(playlist.id.toLong(), it.id.toLong(), index)
        }
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist, tracks: List<Track>, indexes: List<Int>
    ) {
        indexes.forEach { index ->
            context.removeSongFromPlaylist(playlist.id.toLong(), index)
        }
    }

    override suspend fun moveTrackInPlaylist(
        playlist: Playlist, tracks: List<Track>, fromIndex: Int, toIndex: Int
    ) {
        context.moveSongInPlaylist(playlist.id.toLong(), fromIndex, toIndex)
    }

    override suspend fun onEnterPlaylistEditor(playlist: Playlist, tracks: List<Track>) {}
    override suspend fun onExitPlaylistEditor(playlist: Playlist, tracks: List<Track>) {
        library = MediaStoreUtils.getAllSongs(context)
    }

    fun getDownloads(): PagedData<Shelf> {
        library = MediaStoreUtils.getAllSongs(context)
        return library.folderStructure.folderList["storage"]
            ?.folderList?.get("emulated")
            ?.folderList?.get("0")
            ?.folderList?.get("Download")
            ?.folderList?.get("Echo")?.toShelf(null)?.items
            ?: PagedData.Single { listOf() }
    }
}