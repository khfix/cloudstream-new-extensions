package com.witanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

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

    // Updated main page categories based on website structure
    override val mainPage = mainPageOf(
        "$mainUrl/anime-status/%d9%8a%d8%b9%d8%b1%d8%b6-%d8%a7%d9%84%d8%a7%d9%86/" to "الأنميات المعروضة حاليا",
        "$mainUrl/anime-status/%d9%85%d9%83%d8%aa%d9%85%d9%84/" to "الأنميات المكتملة",
        "$mainUrl/anime-type/tv/" to "أنميات TV",
        "$mainUrl/anime-type/movie/" to "أفلام الأنمي",
        "$mainUrl/anime-type/ova/" to "OVA",
        "$mainUrl/anime-type/ona/" to "ONA"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val doc = app.get(url, referer = mainUrl).document
        
        // Updated selectors based on website structure
        val home = doc.select("div.anime-block, div.anime-card, article.anime-item").mapNotNull {
            it.toSearchResult()
        }.ifEmpty {
            // Fallback to different selectors
            doc.select("div.anime-item, article.post, div.item").mapNotNull {
                it.toSearchResult()
            }
        }
        
        return newHomePageResponse(request.name, home, hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"
        val doc = app.get(searchUrl, referer = mainUrl).document
        
        return doc.select("div.anime-block, div.anime-card, article.anime-item, article.post").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h3 a, h2 a, .anime-title a, .entry-title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrl(titleElement.attr("href"))
        
        if (href.isEmpty() || href == mainUrl) return null
        
        // Get poster with multiple fallbacks
        val posterUrl = this.selectFirst("img")?.let { img ->
            val srcOptions = listOf("data-src", "src", "data-lazy-src")
            srcOptions.firstNotNullOfOrNull { attr ->
                val url = img.attr(attr)
                if (url.isNotEmpty() && !url.contains("placeholder")) fixUrl(url) else null
            }
        }
        
        // Determine type from URL or content
        val tvType = when {
            href.contains("/movie/") || title.contains("فيلم") -> TvType.AnimeMovie
            href.contains("/ova/") || title.contains("OVA") -> TvType.OVA
            else -> TvType.Anime
        }
        
        return newAnimeSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
            addDubStatus(isDub = false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = mainUrl).document
        
        // Get title with multiple selectors
        val title = doc.selectFirst("h1.entry-title, h1.anime-title, h1")?.text()?.trim()
            ?: throw ErrorLoadingException("Could not find title")
        
        // Get poster with multiple selectors
        val poster = doc.selectFirst("div.anime-image img, div.anime-thumb img, .wp-post-image")?.let { img ->
            val srcOptions = listOf("data-src", "src", "data-lazy-src")
            srcOptions.firstNotNullOfOrNull { attr ->
                val posterUrl = img.attr(attr)
                if (posterUrl.isNotEmpty()) fixUrl(posterUrl) else null
            }
        }
        
        // Get year from various locations
        val year = doc.selectFirst("span:contains(سنة الإنتاج), .anime-info")?.text()?.let { yearText ->
            Regex("\\d{4}").find(yearText)?.value?.toIntOrNull()
        }
        
        // Get synopsis
        val synopsis = doc.selectFirst("div.anime-story, div.anime-description, .entry-content p")?.text()?.trim()
        
        // Get genres
        val genres = doc.select("div.anime-genres a, .anime-info a").map { it.text().trim() }
        
        // Determine if it's a movie based on URL or content
        val isMovie = url.contains("/movie/") || title.contains("فيلم") || 
                     doc.selectFirst("span:contains(فيلم), .movie-indicator") != null
        
        val tvType = when {
            isMovie -> TvType.AnimeMovie
            url.contains("/ova/") -> TvType.OVA
            else -> TvType.Anime
        }
        
        return if (isMovie) {
            // For movies, create a single episode and return movie response
            newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = synopsis
                this.tags = genres
            }
        } else {
            // For series, get episodes
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
    
    private suspend fun getEpisodes(animeUrl: String, doc: org.jsoup.nodes.Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        // Try to find episodes list or links
        val episodeElements = doc.select("div.episodes-list a, ul.episodes li a, .episode-link")
        
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { element ->
                val episodeUrl = fixUrl(element.attr("href"))
                val episodeName = element.text().trim()
                
                // Extract episode number
                val episodeNumber = Regex("\\d+").findAll(episodeName).lastOrNull()?.value?.toIntOrNull() ?: 1
                
                episodes.add(
                    newEpisode(episodeUrl) {
                        this.name = episodeName
                        this.episode = episodeNumber
                    }
                )
            }
        } else {
            // If no episodes found in main page, create a single episode (might be a movie or single episode)
            episodes.add(
                newEpisode(animeUrl) {
                    this.name = doc.selectFirst("h1")?.text() ?: "الحلقة 1"
                    this.episode = 1
                }
            )
        }
        
        return episodes.sortedBy { it.episode }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        try {
            val doc = app.get(data, referer = mainUrl).document
            
            // Look for video sources in multiple ways
            
            // 1. Check for direct video elements
            doc.select("video source, video").forEach { videoElement ->
                val src = videoElement.attr("src")
                if (src.isNotEmpty()) {
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "Direct Video",
                            url = fixUrl(src)
                        ) {
                            this.referer = data
                            this.quality = Qualities.Unknown.value
                            this.type = if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        }
                    )
                    foundLinks = true
                }
            }
            
            // 2. Check for iframe sources
            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty() && src.contains("http")) {
                    try {
                        val playerDoc = app.get(src, referer = data).document
                        
                        // Look for video sources in the iframe
                        playerDoc.select("video source, video").forEach { video ->
                            val videoSrc = video.attr("src")
                            if (videoSrc.isNotEmpty()) {
                                callback(
                                    newExtractorLink(
                                        source = this.name,
                                        name = "Player",
                                        url = fixUrl(videoSrc)
                                    ) {
                                        this.referer = src
                                        this.quality = getQualityFromName(video.attr("quality") ?: "Unknown")
                                        this.type = if (videoSrc.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                    }
                                )
                                foundLinks = true
                            }
                        }
                        
                        // Look for JavaScript-based sources
                        val scriptText = playerDoc.toString()
                        val m3u8Regex = Regex("(https?://[^\\s\"']+\\.m3u8[^\\s\"']*)")
                        val mp4Regex = Regex("(https?://[^\\s\"']+\\.mp4[^\\s\"']*)")
                        
                        m3u8Regex.findAll(scriptText).forEach { match ->
                            val url = match.value
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "M3U8 Stream",
                                    url = url
                                ) {
                                    this.referer = src
                                    this.quality = Qualities.Unknown.value
                                    this.type = ExtractorLinkType.M3U8
                                }
                            )
                            foundLinks = true
                        }
                        
                        mp4Regex.findAll(scriptText).forEach { match ->
                            val url = match.value
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "MP4 Stream",
                                    url = url
                                ) {
                                    this.referer = src
                                    this.quality = getQualityFromUrl(url)
                                    this.type = ExtractorLinkType.VIDEO
                                }
                            )
                            foundLinks = true
                        }
                        
                    } catch (e: Exception) {
                        // If iframe fails, add it as a potential source
                        callback(
                            newExtractorLink(
                                source = this.name,
                                name = "External Player",
                                url = src
                            ) {
                                this.referer = data
                                this.quality = Qualities.Unknown.value
                                this.type = ExtractorLinkType.VIDEO
                            }
                        )
                        foundLinks = true
                    }
                }
            }
            
            // 3. Look for download links
            doc.select("a[href*='.mp4'], a[href*='.m3u8'], a[href*='drive.google.com'], a[href*='workupload'], a[href*='hexload'], a[href*='gofile']").forEach { downloadLink ->
                val href = downloadLink.attr("href")
                val linkText = downloadLink.text().trim()
                
                if (href.isNotEmpty()) {
                    val quality = when {
                        linkText.contains("FHD") || linkText.contains("1080") -> Qualities.P1080.value
                        linkText.contains("HD") || linkText.contains("720") -> Qualities.P720.value
                        linkText.contains("SD") || linkText.contains("480") -> Qualities.P480.value
                        else -> Qualities.Unknown.value
                    }
                    
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = linkText.ifEmpty { "Download Link" },
                            url = fixUrl(href)
                        ) {
                            this.referer = data
                            this.quality = quality
                            this.type = if (href.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        }
                    )
                    foundLinks = true
                }
            }
            
            // 4. Look in JavaScript for encoded sources
            val scriptElements = doc.select("script:not([src])")
            scriptElements.forEach { script ->
                val scriptContent = script.html()
                
                // Look for various patterns that might contain video URLs
                val patterns = listOf(
                    Regex("file\\s*:\\s*[\"'](https?://[^\"']+)[\"']"),
                    Regex("src\\s*:\\s*[\"'](https?://[^\"']+)[\"']"),
                    Regex("source\\s*:\\s*[\"'](https?://[^\"']+)[\"']"),
                    Regex("url\\s*:\\s*[\"'](https?://[^\"']+)[\"']")
                )
                
                patterns.forEach { pattern ->
                    pattern.findAll(scriptContent).forEach { match ->
                        val url = match.groupValues[1]
                        if (url.contains(".mp4") || url.contains(".m3u8")) {
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "JS Source",
                                    url = url
                                ) {
                                    this.referer = data
                                    this.quality = getQualityFromUrl(url)
                                    this.type = if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            // Log error but don't fail completely
            println("Error loading links: ${e.message}")
        }
        
        return foundLinks
    }
    
    private fun getQualityFromName(quality: String): Int {
        return when (quality.lowercase()) {
            "1080p", "fhd", "full hd" -> Qualities.P1080.value
            "720p", "hd" -> Qualities.P720.value
            "480p", "sd" -> Qualities.P480.value
            "360p" -> Qualities.P360.value
            "240p" -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }
    }
    
    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.P1080.value
            url.contains("720") -> Qualities.P720.value
            url.contains("480") -> Qualities.P480.value
            url.contains("360") -> Qualities.P360.value
            url.contains("240") -> Qualities.P240.value
            else -> Qualities.Unknown.value
        }
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