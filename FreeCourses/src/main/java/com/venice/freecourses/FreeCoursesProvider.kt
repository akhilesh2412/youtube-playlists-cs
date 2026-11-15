package com.venice.freecourses

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

data class PlaylistInfo(val id: String, val title: String)

data class InvidiousVideo(
    val title: String?,
    val videoId: String?,
    val thumbUrls: List<String>
)

data class InvidiousPlaylist(
    val title: String?,
    val videos: List<InvidiousVideo>,
    val playlistThumbnail: String? = null
)

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

    // Piped public API — sirf stream nikaalne ke liye (loadLinks me).
    // Har host pe /api/streams/{videoId} se "hls" (.m3u8) milta hai.
    private val pipedHosts = listOf(
        "pipedapi.kavin.rocks",
        "pipedapi.adminforge.de",
        "pipedapi.leptons.xyz",
        "piped.video"
    )

    // Relative thumbnail URL ko absolute https URL banao.
    // Invidious kabhi "/vi/VIDEOID/hqdefault.jpg" jaisi relative URL deta
    // hai — wo CloudStream me load nahi hoti, isiliye normalize zaroori hai.
    private fun absThumb(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isBlank()) return null
        return when {
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> "https://i.ytimg.com$u"
            else -> u
        }
    }

    // YouTube thumbnail ka sabse bharosemand tareeka: i.ytimg.com ka
    // direct URL. Har video ke liye hqdefault.jpg hota hai, isiliye
    // Invidious ke thumbnail format pe depend nahi karna padta.
    private fun ytThumb(videoId: String): String =
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private fun bestThumb(video: InvidiousVideo): String? {
        val id = video.videoId
        if (!id.isNullOrBlank()) return ytThumb(id)
        return video.thumbUrls.firstNotNullOfOrNull { absThumb(it) }
    }

    private fun videoIdFromUrl(url: String): String? {
        val m = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)
        if (m != null) return m.groupValues[1]
        val last = url.substringAfterLast("/").substringBefore("?").substringBefore("&")
        return last.takeIf { it.length == 11 }
    }

    private fun parseInvidious(text: String): InvidiousPlaylist? {
        return try {
            val root = JSONObject(text)
            val title = if (root.has("title") && !root.isNull("title")) root.optString("title") else null
            val playlistThumb = absThumb(
                if (root.has("playlistThumbnail") && !root.isNull("playlistThumbnail"))
                    root.optString("playlistThumbnail") else null
            )
            val videosArray = root.optJSONArray("videos") ?: return null
            val videos = mutableListOf<InvidiousVideo>()
            for (i in 0 until videosArray.length()) {
                val v = videosArray.optJSONObject(i) ?: continue
                val videoId = if (v.has("videoId") && !v.isNull("videoId")) v.optString("videoId") else null
                if (videoId.isNullOrBlank()) continue
                val vTitle = if (v.has("title") && !v.isNull("title")) v.optString("title") else null
                val thumbs = mutableListOf<String>()
                val thumbsArray = v.optJSONArray("videoThumbnails")
                if (thumbsArray != null) {
                    for (j in 0 until thumbsArray.length()) {
                        val t = thumbsArray.optJSONObject(j) ?: continue
                        val url = t.optString("url")
                        if (url.isNotBlank()) thumbs.add(url)
                    }
                }
                videos.add(InvidiousVideo(vTitle, videoId, thumbs))
            }
            if (videos.isEmpty()) null else InvidiousPlaylist(title, videos, playlistThumb)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchViaInvidious(id: String): InvidiousPlaylist? {
        for (host in invidiousHosts) {
            try {
                val text = app.get("https://$host/api/v1/playlists/$id").text
                val data = parseInvidious(text)
                if (data != null) return data
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
                videos = ids.mapIndexed { index, videoId ->
                    InvidiousVideo(
                        title = "Lecture ${index + 1}",
                        videoId = videoId,
                        thumbUrls = listOf("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = playlists.map { p ->
            // Playlist card ka thumbnail: pehle playlistThumbnail, nahi to
            // pehle video ka thumbnail. Pehle yahan poster hi nahi lagta
            // tha, isiliye playlist ke thumbnails dikhte nahi the.
            val data = try { fetchPlaylist(p.id) } catch (e: Exception) { null }
            val poster = data?.playlistThumbnail
                ?: data?.videos?.firstOrNull()?.let { bestThumb(it) }
            newMovieSearchResponse(p.title, playlistUrl(p.id), TvType.Others) {
                this.posterUrl = poster
            }
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
            data.videos
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

        val videos = data.videos
        val episodes = videos.mapIndexedNotNull { index, v ->
            val videoId = v.videoId ?: return@mapIndexedNotNull null
            newEpisode("https://www.youtube.com/watch?v=$videoId") {
                this.name = v.title ?: "Lecture ${index + 1}"
                this.episode = index + 1
                this.posterUrl = bestThumb(v)
            }
        }

        val poster = data.playlistThumbnail
            ?: videos.firstOrNull()?.let { bestThumb(it) }

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

    // Invidious video API se direct streams: hlsUrl (.m3u8, ExoPlayer
    // isko seedha chala sakta hai) aur formatStreams (audio+video wale
    // progressive mp4). adaptiveFormats nahi lete — wo video-only ya
    // audio-only hote hain, bina audio ke video chalega.
    private suspend fun tryInvidiousStreams(
        videoId: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        for (host in invidiousHosts) {
            try {
                val text = app.get("https://$host/api/v1/videos/$videoId").text
                if (text.isBlank() || text.trimStart().startsWith("<")) continue
                val root = JSONObject(text)
                val hls = root.optString("hlsUrl").takeIf { it.isNotBlank() }
                if (hls != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "HLS",
                            url = hls,
                            type = ExtractorLinkType.M3U8
                        ) { this.quality = Qualities.Unknown.value }
                    )
                    found = true
                }
                val formats = root.optJSONArray("formatStreams")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val f = formats.optJSONObject(i) ?: continue
                        val url = f.optString("url")
                        if (url.isBlank()) continue
                        val label = f.optString("qualityLabel")
                            .ifBlank { f.optString("quality") }
                        val q = label.filter { it.isDigit() }.toIntOrNull()
                            ?: Qualities.Unknown.value
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = label.ifBlank { "YouTube" },
                                url = url,
                                type = ExtractorLinkType.VIDEO
                            ) { this.quality = q }
                        )
                        found = true
                    }
                }
                if (found) return true
            } catch (e: Exception) {
                continue
            }
        }
        return found
    }

    // Piped API se direct streams: "hls" (.m3u8) aur combined
    // (videoOnly=false) mp4 streams. Video-only formats skip karte hain.
    private suspend fun tryPipedStreams(
        videoId: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        for (host in pipedHosts) {
            try {
                val text = app.get("https://$host/api/streams/$videoId").text
                if (text.isBlank() || text.trimStart().startsWith("<")) continue
                val root = JSONObject(text)
                val hls = root.optString("hls").takeIf { it.isNotBlank() }
                if (hls != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "HLS",
                            url = hls,
                            type = ExtractorLinkType.M3U8
                        ) { this.quality = Qualities.Unknown.value }
                    )
                    found = true
                }
                val streams = root.optJSONArray("videoStreams")
                if (streams != null) {
                    for (i in 0 until streams.length()) {
                        val s = streams.optJSONObject(i) ?: continue
                        if (s.optBoolean("videoOnly", false)) continue
                        val url = s.optString("url")
                        if (url.isBlank()) continue
                        val mime = s.optString("mimeType")
                        if (mime.isNotBlank() && !mime.contains("video/mp4")) continue
                        val label = s.optString("quality")
                        val q = label.filter { it.isDigit() }.toIntOrNull()
                            ?: Qualities.Unknown.value
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = label.ifBlank { "YouTube" },
                                url = url,
                                type = ExtractorLinkType.VIDEO
                            ) { this.quality = q }
                        )
                        found = true
                    }
                }
                if (found) return true
            } catch (e: Exception) {
                continue
            }
        }
        return found
    }

    // YouTube watch page se seedha streams: ytInitialPlayerResponse ke
    // andar "streamingData" hota hai — usme hlsManifestUrl (live ki .m3u8)
    // aur formats[] (audio+video wale progressive mp4, direct URL) hote
    // hain. Ye Invidious/Piped pe depend nahi karta, isiliye teesra
    // fallback hai. signatureCipher wale formats skip karte hain.
    private fun extractStreamingData(html: String): JSONObject? {
        val key = "\"streamingData\":"
        val start = html.indexOf(key)
        if (start < 0) return null
        var i = html.indexOf('{', start + key.length)
        if (i < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        var j = i
        while (j < html.length) {
            val c = html[j]
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return try {
                                JSONObject(html.substring(i, j + 1))
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            }
            j++
        }
        return null
    }

    private suspend fun tryYouTubeWatchPage(
        videoId: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val html = app.get(
                "https://www.youtube.com/watch?v=$videoId",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            ).text
            val streaming = extractStreamingData(html) ?: return false
            var found = false
            val hls = streaming.optString("hlsManifestUrl").takeIf { it.isNotBlank() }
            if (hls != null) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "HLS",
                        url = hls,
                        type = ExtractorLinkType.M3U8
                    ) { this.quality = Qualities.Unknown.value }
                )
                found = true
            }
            val formats = streaming.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.optJSONObject(i) ?: continue
                    val url = f.optString("url")
                    if (url.isBlank()) continue
                    val mime = f.optString("mimeType")
                    if (mime.isNotBlank() && !mime.contains("video/mp4")) continue
                    val label = f.optString("qualityLabel")
                    val q = label.filter { it.isDigit() }.toIntOrNull()
                        ?: Qualities.Unknown.value
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = label.ifBlank { "YouTube" },
                            url = url,
                            type = ExtractorLinkType.VIDEO
                        ) { this.quality = q }
                    )
                    found = true
                }
            }
            found
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        val videoId = videoIdFromUrl(data)
        if (videoId != null) {
            // Pehle Invidious, phir Piped, phir YouTube watch page se
            // seedha — teeno se direct .m3u8 / mp4 milta hai jo ExoPlayer
            // seedha chala sakta hai.
            if (tryInvidiousStreams(videoId, callback)) return true
            if (tryPipedStreams(videoId, callback)) return true
            if (tryYouTubeWatchPage(videoId, callback)) return true
        }
        // Kuch na mila to purana tareeka: watch URL CloudStream ke
        // built-in YouTube extractor ko de do, playback toote nahi.
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
