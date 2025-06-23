package com.witanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.net.URLEncoder

class WitanimeProvider : MainAPI() {
    override var mainUrl = "https://witanime.uno"
    override var name = "WitAnime"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override var lang = "ar"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false

    // Main page categories based on website structure
    override val mainPage = mainPageOf(
        "$mainUrl/anime-status/%d9%8a%d8%b9%d8%b1%d8%b6-%d8%a7%d9%84%d8%a7%d9%86/" to "الأنميات المعروضة حاليا",
        "$mainUrl/anime-status/%d9%85%d9%83%d8%aa%d9%85%d9%84/" to "الأنميات المكتملة", 
        "$mainUrl/anime-type/tv/" to "أنميات TV",
        "$mainUrl/anime-type/movie/" to "أفلام الأنمي",
        "$mainUrl/anime-type/ova/" to "OVA",
        "$mainUrl/anime-type/ona/" to "ONA"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "ar,en-US;q=0.7,en;q=0.3",
        "Accept-Encoding" to "gzip, deflate",
        "Upgrade-Insecure-Requests" to "1"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val doc = app.get(url, headers = headers, referer = mainUrl).document
        
        // Look for anime items using multiple selectors for robustness
        val animeItems = doc.select("article.anime-item, .anime-card, .anime-block").ifEmpty {
            // Fallback selectors
            doc.select("article[class*='anime'], div[class*='anime-item'], .post-item")
        }
        
        val home = animeItems.mapNotNull { element ->
            element.toSearchResult()
        }
        
        return newHomePageResponse(
            list = listOf(HomePageList(request.name, home)),
            hasNext = home.size >= 12 // Typical page size
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"
        val doc = app.get(searchUrl, headers = headers, referer = mainUrl).document
        
        return doc.select("article.anime-item, .anime-card, .anime-block, article.post").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Multiple selectors for title to handle different layouts
        val titleElement = this.selectFirst("h3 a, h2 a, .anime-title a, .entry-title a, h1 a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = titleElement.attr("href")
        
        if (href.isEmpty() || href == mainUrl) return null
        val url = fixUrl(href)
        
        // Get poster image with multiple fallbacks
        val posterUrl = this.selectFirst("img")?.let { img ->
            // Try different image attributes
            listOf("data-src", "src", "data-lazy-src", "data-original").firstNotNullOfOrNull { attr ->
                val imgUrl = img.attr(attr)
                if (imgUrl.isNotEmpty() && !imgUrl.contains("placeholder") && !imgUrl.contains("default")) {
                    fixUrl(imgUrl)
                } else null
            }
        }
        
        // Determine content type from URL or content indicators
        val tvType = when {
            url.contains("/movie/") || title.contains("فيلم") || title.contains("Movie") -> TvType.AnimeMovie
            url.contains("/ova/") || title.contains("OVA") -> TvType.OVA
            url.contains("/ona/") || title.contains("ONA") -> TvType.OVA
            else -> TvType.Anime
        }
        
        return when (tvType) {
            TvType.AnimeMovie -> {
                newMovieSearchResponse(title, url, TvType.AnimeMovie) {
                    this.posterUrl = posterUrl
                }
            }
            else -> {
                newAnimeSearchResponse(title, url, tvType) {
                    this.posterUrl = posterUrl
                    addDubStatus(isDub = false) // Arabic subtitles
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers, referer = mainUrl).document
        
        // Extract title with multiple selectors
        val title = doc.selectFirst("h1.entry-title, h1.anime-title, h1, .anime-title")?.text()?.trim()
            ?: throw ErrorLoadingException("Could not find anime title")
        
        // Extract poster image
        val poster = doc.selectFirst("div.anime-image img, .anime-thumb img, .wp-post-image, .poster img")?.let { img ->
            listOf("data-src", "src", "data-lazy-src", "data-original").firstNotNullOfOrNull { attr ->
                val posterUrl = img.attr(attr)
                if (posterUrl.isNotEmpty()) fixUrl(posterUrl) else null
            }
        }
        
        // Extract year from various possible locations
        val year = doc.selectFirst("span:contains(سنة الإنتاج), .anime-year, .year")?.text()?.let { yearText ->
            Regex("(19|20)\\d{2}").find(yearText)?.value?.toIntOrNull()
        }
        
        // Extract synopsis/description
        val synopsis = doc.selectFirst("div.anime-story, div.anime-description, .entry-content p, .anime-summary")?.text()?.trim()
        
        // Extract genres/tags
        val genres = doc.select("div.anime-genres a, .anime-info a, .genre a, .genres a").map { 
            it.text().trim() 
        }.filter { it.isNotEmpty() }
        
        // Determine if this is a movie or series
        val isMovie = url.contains("/movie/") || 
                     title.contains("فيلم") || 
                     title.contains("Movie") ||
                     doc.selectFirst("span:contains(فيلم), .movie-indicator") != null
        
        val tvType = when {
            isMovie -> TvType.AnimeMovie
            url.contains("/ova/") || title.contains("OVA") -> TvType.OVA
            url.contains("/ona/") || title.contains("ONA") -> TvType.OVA
            else -> TvType.Anime
        }
        
        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = synopsis
                this.tags = genres
            }
        } else {
            val episodes = getEpisodes(url, doc)
            
            newAnimeLoadResponse(title, url, tvType) {
                this.posterUrl = poster
                this.year = year
                this.plot = synopsis
                this.tags = genres
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }
    
    private suspend fun getEpisodes(animeUrl: String, doc: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        // Look for episodes list in multiple ways
        var episodeElements = doc.select("div.episodes-list a, ul.episodes li a, .episode-link a")
        
        if (episodeElements.isEmpty()) {
            // Try alternative selectors for episode links
            episodeElements = doc.select("a[href*='/episode/']")
        }
        
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEachIndexed { index, element ->
                val episodeUrl = fixUrl(element.attr("href"))
                val episodeName = element.text().trim()
                
                // Extract episode number with improved regex
                val episodeNumber = extractEpisodeNumber(episodeName) ?: (index + 1)
                
                episodes.add(
                    newEpisode(episodeUrl) {
                        this.name = episodeName.ifEmpty { "الحلقة $episodeNumber" }
                        this.episode = episodeNumber
                    }
                )
            }
        } else {
            // If no episodes found, check if there are episode buttons or pagination
            val currentEpisodeNumber = extractEpisodeNumber(doc.selectFirst("h1")?.text() ?: "") ?: 1
            
            // Create episode for current page
            episodes.add(
                newEpisode(animeUrl) {
                    this.name = "الحلقة $currentEpisodeNumber"
                    this.episode = currentEpisodeNumber
                }
            )
            
            // Look for other episodes by checking navigation or episode selection
            val episodeNavigation = doc.select("a[href*='/episode/']:contains(الحلقة)")
            episodeNavigation.forEach { navElement ->
                val navUrl = fixUrl(navElement.attr("href"))
                val navText = navElement.text().trim()
                val navEpisodeNumber = extractEpisodeNumber(navText)
                
                if (navEpisodeNumber != null && navEpisodeNumber != currentEpisodeNumber) {
                    episodes.add(
                        newEpisode(navUrl) {
                            this.name = navText
                            this.episode = navEpisodeNumber
                        }
                    )
                }
            }
        }
        
        return episodes.distinctBy { it.episode }.sortedBy { it.episode }
    }
    
    private fun extractEpisodeNumber(text: String): Int? {
        // Multiple regex patterns to extract episode numbers
        val patterns = listOf(
            Regex("الحلقة\\s*(\\d+)"),      // Arabic episode
            Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("الأونا\\s*(\\d+)"),      // ONA
            Regex("(\\d+)"),               // Any number
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        try {
            val doc = app.get(data, headers = headers, referer = mainUrl).document
            
            // Strategy 1: Look for direct download links (common on WitAnime)
            val downloadLinks = doc.select("a[href*='workupload'], a[href*='hexload'], a[href*='gofile'], a[href*='drive.google.com']")
            downloadLinks.forEach { link ->
                val href = link.attr("href")
                val quality = extractQualityFromText(link.text())
                
                if (href.isNotEmpty()) {
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = "${this.name} - ${link.text().trim()}",
                            url = href,
                            referer = data,
                            quality = quality,
                            type = ExtractorLinkType.VIDEO
                        )
                    )
                    foundLinks = true
                }
            }
            
            // Strategy 2: Look for embedded video players
            val iframes = doc.select("iframe[src]")
            for (iframe in iframes) {
                val iframeSrc = iframe.attr("src")
                if (iframeSrc.isNotEmpty()) {
                    val fullIframeUrl = fixUrl(iframeSrc)
                    
                    try {
                        val iframeDoc = app.get(fullIframeUrl, headers = headers, referer = data).document
                        
                        // Look for video sources in iframe
                        val videoSources = iframeDoc.select("video source, video[src]")
                        videoSources.forEach { video ->
                            val videoSrc = video.attr("src")
                            if (videoSrc.isNotEmpty()) {
                                val quality = extractQualityFromText(video.attr("quality") ?: video.attr("label") ?: "Unknown")
                                
                                callback.invoke(
                                    ExtractorLink(
                                        source = this.name,
                                        name = "${this.name} Player",
                                        url = fixUrl(videoSrc),
                                        referer = fullIframeUrl,
                                        quality = quality,
                                        type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    )
                                )
                                foundLinks = true
                            }
                        }
                        
                        // Look for JavaScript-based video sources
                        val scriptText = iframeDoc.toString()
                        extractVideoUrlsFromScript(scriptText).forEach { videoUrl ->
                            callback.invoke(
                                ExtractorLink(
                                    source = this.name,
                                    name = "${this.name} Stream",
                                    url = videoUrl,
                                    referer = fullIframeUrl,
                                    quality = extractQualityFromUrl(videoUrl),
                                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                )
                            )
                            foundLinks = true
                        }
                        
                    } catch (e: Exception) {
                        // If iframe loading fails, still add it as a potential source
                        callback.invoke(
                            ExtractorLink(
                                source = this.name,
                                name = "${this.name} External Player",
                                url = fullIframeUrl,
                                referer = data,
                                quality = Qualities.Unknown.value,
                                type = ExtractorLinkType.VIDEO
                            )
                        )
                        foundLinks = true
                    }
                }
            }
            
            // Strategy 3: Look for embedded video elements
            val directVideos = doc.select("video[src], video source[src]")
            directVideos.forEach { video ->
                val videoSrc = video.attr("src")
                if (videoSrc.isNotEmpty()) {
                    val quality = extractQualityFromText(video.attr("quality") ?: video.attr("label") ?: "")
                    
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = "${this.name} Direct",
                            url = fixUrl(videoSrc),
                            referer = data,
                            quality = quality,
                            type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    foundLinks = true
                }
            }
            
            // Strategy 4: Look in JavaScript for video URLs
            val scriptElements = doc.select("script:not([src])")
            scriptElements.forEach { script ->
                val scriptContent = script.html()
                extractVideoUrlsFromScript(scriptContent).forEach { videoUrl ->
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = "${this.name} JS Source",
                            url = videoUrl,
                            referer = data,
                            quality = extractQualityFromUrl(videoUrl),
                            type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                    foundLinks = true
                }
            }
            
            // Strategy 5: Look for AJAX endpoints that might provide video data
            val episodeId = extractEpisodeId(data)
            if (episodeId != null) {
                foundLinks = tryAjaxVideoSources(episodeId, data, callback) || foundLinks
            }
            
        } catch (e: Exception) {
            // Log error but don't fail completely
            println("WitAnime: Error loading links from $data: ${e.message}")
        }
        
        return foundLinks
    }
    
    private fun extractVideoUrlsFromScript(scriptContent: String): List<String> {
        val videoUrls = mutableListOf<String>()
        
        // Common patterns for video URLs in JavaScript
        val patterns = listOf(
            Regex("file\\s*:\\s*[\"'](https?://[^\"']+\\.(?:mp4|m3u8|webm|mkv)[^\"']*)[\"']"),
            Regex("src\\s*:\\s*[\"'](https?://[^\"']+\\.(?:mp4|m3u8|webm|mkv)[^\"']*)[\"']"),
            Regex("source\\s*:\\s*[\"'](https?://[^\"']+\\.(?:mp4|m3u8|webm|mkv)[^\"']*)[\"']"),
            Regex("url\\s*:\\s*[\"'](https?://[^\"']+\\.(?:mp4|m3u8|webm|mkv)[^\"']*)[\"']"),
            Regex("[\"'](https?://[^\"'\\s]+\\.(?:mp4|m3u8|webm|mkv)(?:\\?[^\"'\\s]*)?)[\"']")
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(scriptContent).forEach { match ->
                val url = match.groupValues[1]
                if (url.isNotEmpty() && isValidVideoUrl(url)) {
                    videoUrls.add(url)
                }
            }
        }
        
        return videoUrls.distinct()
    }
    
    private suspend fun tryAjaxVideoSources(episodeId: String, referer: String, callback: (ExtractorLink) -> Unit): Boolean {
        val possibleEndpoints = listOf(
            "$mainUrl/wp-admin/admin-ajax.php",
            "$mainUrl/ajax/episode",
            "$mainUrl/api/episode/$episodeId"
        )
        
        for (endpoint in possibleEndpoints) {
            try {
                val response = app.post(
                    url = endpoint,
                    data = mapOf(
                        "action" to "get_episode_links",
                        "episode_id" to episodeId
                    ),
                    headers = headers + mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to referer
                    )
                )
                
                if (response.isSuccessful) {
                    val responseText = response.text
                    extractVideoUrlsFromScript(responseText).forEach { videoUrl ->
                        callback.invoke(
                            ExtractorLink(
                                source = this.name,
                                name = "${this.name} AJAX",
                                url = videoUrl,
                                referer = referer,
                                quality = extractQualityFromUrl(videoUrl),
                                type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            )
                        )
                    }
                    return true
                }
            } catch (e: Exception) {
                // Continue to next endpoint
            }
        }
        
        return false
    }
    
    private fun extractEpisodeId(url: String): String? {
        return Regex("episode/([^/]+)/?").find(url)?.groupValues?.get(1)
    }
    
    private fun extractQualityFromText(text: String): Int {
        return when {
            text.contains("FHD", true) || text.contains("1080", true) -> Qualities.P1080.value
            text.contains("HD", true) || text.contains("720", true) -> Qualities.P720.value
            text.contains("SD", true) || text.contains("480", true) -> Qualities.P480.value
            text.contains("360", true) -> Qualities.P360.value
            text.contains("240", true) -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }
    }
    
    private fun extractQualityFromUrl(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.P1080.value
            url.contains("720") -> Qualities.P720.value
            url.contains("480") -> Qualities.P480.value
            url.contains("360") -> Qualities.P360.value
            url.contains("240") -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }
    }
    
    private fun isValidVideoUrl(url: String): Boolean {
        return url.matches(Regex(".*\\.(mp4|m3u8|webm|mkv)($|\\?)")) ||
               url.contains("blob:") ||
               url.contains("manifest") ||
               url.contains("playlist")
    }
    
    private fun fixUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> mainUrl + url
            else -> "$mainUrl/$url"
        }
    }
}