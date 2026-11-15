package com.venice.freecourses

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.Serializable

data class PlaylistInfo(val id: String, val title: String)

class FreeCoursesProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "Free Courses"
    override var lang = "hi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Others)

    // Nayi playlist add karni ho to bas yahan ek line badha do.
    // Ye 4 playlists user ne di hain — sab public free YouTube playlists hain.
    private val playlists = listOf(
        PlaylistInfo(
            "PL-CL6RQ4EUMaSvMpCq3ySkt63UfLGcmVW",
            "CUET MCA Preparation 2026"
        ),
        PlaylistInfo(
            "PLzlQDutMQpIgfi6TFtUMMkWcgVHPboczh",
            "CUET PG MCA 2027 - Complete Computer Science | Adda247"
        ),
        PlaylistInfo(
            "PLxgZQoSe9cg1drBnejUaDD9GEJBGQ5hMt",
            "C Programming Course"
        ),
        PlaylistInfo(
            "PLxCzCOWd7aiGmiGl_DOuRMJYG8tOVuapB",
            "C Programming"
        )
    )

    override val mainPage = mainPageOf("playlists" to "Playlists")

    private fun playlistUrl(id: String) = "$mainUrl/playlist?list=$id"

    // Invidious public API se playlist JSON milta hai — YouTube HTML parse
    // karne se zyada stable hai. Ek instance down ho to dusra try hota hai.
    private val invidiousHosts = listOf(
        "inv.nadeko.net",
        "invidious.privacyredirect.com",
        "yewtu.be",
        "vid.puffyan.us",
        "inv.tux.pizza"
    )

    private suspend fun fetchViaInvidious(id: String): InvidiousPlaylist? {
        for (host in invidiousHosts) {
            try {
                val data = app.get("https://$host/api/v1/playlists/$id")
                    .parsedSafe<InvidiousPlaylist>()
                if (data?.videos?.isNotEmpty() == true) return data
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    // Fallback: YouTube playlist page se videoId nikaalo.
    private suspend fun fetchViaYouTubeHtml(id: String): InvidiousPlaylist? {
        return try {
            val html = app.get(
                playlistUrl(id),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            ).text
            val ids = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
                .findAll(html)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            if (ids.isEmpty()) return null
            val title = playlists.firstOrNull { it.id == id }?.title
            InvidiousPlaylist(
                title = title,
                playlistId = id,
                videos = ids.mapIndexed { index, videoId ->
                    InvidiousVideo(
                        title = "Lecture ${index + 1}",
                        videoId = videoId,
                        videoThumbnails = listOf(
                            InvidiousThumb(url = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                        )
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchPlaylist(id: String): InvidiousPlaylist? {
        return fetchViaInvidious(id) ?: fetchViaYouTubeHtml(id)
    }

    private fun bestThumb(video: InvidiousVideo): String? {
        val thumbs = video.videoThumbnails.orEmpty()
        return thumbs.maxByOrNull { it.width ?: 0 }?.url
            ?: thumbs.lastOrNull()?.url
            ?: video.videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = playlists.map { p ->
            newMovieSearchResponse(p.title, playlistUrl(p.id), TvType.Others)
        }
        return newHomePageResponse("Playlists", items, false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<SearchResponse>()
        for (p in playlists) {
            val data = fetchPlaylist(p.id) ?: continue
            data.videos.orEmpty()
                .filter { it.title?.lowercase()?.contains(q) == true }
                .forEach { v ->
                    val videoId = v.videoId ?: return@forEach
                    out.add(
                        newMovieSearchResponse(
                            v.title ?: "Untitled",
                            "https://www.youtube.com/watch?v=$videoId",
                            TvType.Others
                        ) { this.posterUrl = bestThumb(v) }
                    )
                }
        }
        return out
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfter("list=", "").substringBefore("&")
            .ifBlank { url.substringAfterLast("/") }
        val info = playlists.firstOrNull { it.id == id }
        val data = fetchPlaylist(id) ?: throw ErrorLoadingException("Playlist load nahi hui")

        val videos = data.videos.orEmpty()
        val episodes = videos.mapIndexedNotNull { index, v ->
            val videoId = v.videoId ?: return@mapIndexedNotNull null
            newEpisode("https://www.youtube.com/watch?v=$videoId") {
                this.name = v.title ?: "Lecture ${index + 1}"
                this.episode = index + 1
                this.posterUrl = bestThumb(v)
            }
        }

        val poster = videos.firstOrNull()?.let { bestThumb(it) }

        return newTvSeriesLoadResponse(
            data.title ?: info?.title ?: id,
            url,
            TvType.Others,
            episodes
        ) {
            this.posterUrl = poster
            this.plot = "${data.title ?: info?.title ?: id} — ${videos.size} free YouTube videos."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        // YouTube watch URL CloudStream ke built-in YouTube extractor ko diya
        // jata hai — wahi video stream nikaalta hai.
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = data,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}

@Serializable
data class InvidiousPlaylist(
    val title: String? = null,
    val playlistId: String? = null,
    val videos: List<InvidiousVideo>? = null
)

@Serializable
data class InvidiousVideo(
    val title: String? = null,
    val videoId: String? = null,
    val author: String? = null,
    val lengthSeconds: Int? = null,
    val videoThumbnails: List<InvidiousThumb>? = null
)

@Serializable
data class InvidiousThumb(
    val quality: String? = null,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
