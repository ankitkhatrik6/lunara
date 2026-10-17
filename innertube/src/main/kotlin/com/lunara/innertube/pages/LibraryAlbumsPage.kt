package com.lunara.innertube.pages

import com.lunara.innertube.models.Album
import com.lunara.innertube.models.AlbumItem
import com.lunara.innertube.models.Artist
import com.lunara.innertube.models.ArtistItem
import com.lunara.innertube.models.MusicResponsiveListItemRenderer
import com.lunara.innertube.models.MusicTwoRowItemRenderer
import com.lunara.innertube.models.PlaylistItem
import com.lunara.innertube.models.SongItem
import com.lunara.innertube.models.YTItem
import com.lunara.innertube.models.oddElements
import com.lunara.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
