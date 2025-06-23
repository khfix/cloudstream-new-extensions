package com.witanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
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
        "$mainUrl/" to "الأنميات المثبتة",
        "$mainUrl/anime-status/%d9%8a%d8%b9%d8%b1%d8%b6-%d8%a7%d9%84%d8%a7%d9%86/" to "الأنميات المعروضة حاليا",
        "$mainUrl/anime-status/%d9%85%d9%83%d8%aa%d9%85%d9%84/" to "الأنميات المكتملة", 
        "$mainUrl/anime-type/tv/" to "أنميات TV",
        "$mainUrl/anime-type/movie/" to "أفلام الأنمي",
        "$mainUrl/anime-type/ova/" to "OVA",
        "$mainUrl/anime-type/ona/" to "ONA"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/jxl,*/*;q=0.8",
        "Accept-Language" to "ar,en-US;q=0.9,en;q=0.8",
        "Accept-Encoding" to "gzip, deflate, br",
        "Referer" to "https://witanime.uno/",
        "DNT" to "1",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Cache-Control" to "max-age=0"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val document = app.get(url, headers = headers).document
        
        val home = mutableListOf<SearchResponse>()
        
        // Strategy 1: Extract from episodes cards for latest episodes
        if (request.data == mainUrl + "/") {
            val episodeCards = document.select("div.episodes-card-container")
            episodeCards.forEach { episodeCard ->
                val episodeLink = episodeCard.selectFirst("a[href*='/episode/']")
                val animeLink = episodeCard.selectFirst("div.ep-card-anime-title a")
                
                if (episodeLink != null && animeLink != null) {
                    val title = animeLink.text().trim()
                    val animeUrl = animeLink.attr("href")
                    val posterUrl = episodeCard.selectFirst("img")?.let { img ->
                        img.attr("src").takeIf { it.isNotEmpty() }
                    }
                    
                    if (title.isNotEmpty() && animeUrl.isNotEmpty()) {
                        val searchResponse = newAnimeSearchResponse(title, animeUrl, TvType.Anime) {
                            this.posterUrl = posterUrl
                            addDubStatus(isDub = false)
                        }
                        if (!home.any { it.url == animeUrl }) {
                            home.add(searchResponse)
                        }
                    }
                }
            }
        }
        
        // Strategy 2: Extract from anime cards for category pages
        val animeCards = document.select("div.anime-card-container")
        animeCards.forEach { animeCard ->
            val titleElement = animeCard.selectFirst("h3 a, div.anime-card-title a")
            val title = titleElement?.text()?.trim()
            val animeUrl = titleElement?.attr("href")
            
            val posterUrl = animeCard.selectFirst("img")?.let { img ->
                img.attr("src").takeIf { it.isNotEmpty() }
            }
            
            if (!title.isNullOrEmpty() && !animeUrl.isNullOrEmpty()) {
                // Determine type from URL or category indicators
                val typeText = animeCard.select("div.anime-card-type a").text().lowercase()
                val isMovie = typeText.contains("movie") || typeText.contains("فيلم") || 
                             animeUrl.contains("/movie/") || animeUrl.contains("film")
                val isOVA = typeText.contains("ova") || animeUrl.contains("/ova/")
                val isONA = typeText.contains("ona") || animeUrl.contains("/ona/")
                
                val tvType = when {
                    isMovie -> TvType.AnimeMovie
                    isOVA || isONA -> TvType.OVA
                    else -> TvType.Anime
                }
                
                val searchResponse = if (tvType == TvType.AnimeMovie) {
                    newMovieSearchResponse(title, animeUrl, TvType.AnimeMovie) {
                        this.posterUrl = posterUrl
                    }
                } else {
                    newAnimeSearchResponse(title, animeUrl, tvType) {
                        this.posterUrl = posterUrl
                        addDubStatus(isDub = false)
                    }
                }
                
                if (!home.any { it.url == animeUrl }) {
                    home.add(searchResponse)
                }
            }
        }
        
        // Check if there's a "next page" link
        val hasNextPage = document.select("a.next.page-numbers, a:contains(الصفحة التالية), a:contains(Next)").isNotEmpty()
        
        return newHomePageResponse(
            list = listOf(HomePageList(request.name, home)),
            hasNext = home.isNotEmpty() && hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"
        val document = app.get(searchUrl, headers = headers).document
        
        val searchResults = mutableListOf<SearchResponse>()
        
        // Search results appear in anime cards
        val animeCards = document.select("div.anime-card-container")
        animeCards.forEach { animeCard ->
            val titleElement = animeCard.selectFirst("h3 a, div.anime-card-title a")
            val title = titleElement?.text()?.trim()
            val animeUrl = titleElement?.attr("href")
            
            val posterUrl = animeCard.selectFirst("img")?.let { img ->
                img.attr("src").takeIf { it.isNotEmpty() }
            }
            
            if (!title.isNullOrEmpty() && !animeUrl.isNullOrEmpty()) {
                // Determine type
                val typeText = animeCard.select("div.anime-card-type a").text().lowercase()
                val isMovie = typeText.contains("movie") || typeText.contains("فيلم") || 
                             animeUrl.contains("/movie/") || animeUrl.contains("film")
                val isOVA = typeText.contains("ova") || animeUrl.contains("/ova/")
                val isONA = typeText.contains("ona") || animeUrl.contains("/ona/")
                
                val tvType = when {
                    isMovie -> TvType.AnimeMovie
                    isOVA || isONA -> TvType.OVA
                    else -> TvType.Anime
                }
                
                val searchResponse = if (tvType == TvType.AnimeMovie) {
                    newMovieSearchResponse(title, animeUrl, TvType.AnimeMovie) {
                        this.posterUrl = posterUrl
                    }
                } else {
                    newAnimeSearchResponse(title, animeUrl, tvType) {
                        this.posterUrl = posterUrl
                        addDubStatus(isDub = false)
                    }
                }
                
                searchResults.add(searchResponse)
            }
        }
        
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document
        
        // Extract title - try multiple selectors
        val title = document.selectFirst("h1, .anime-info h1, .anime-details-title")?.text()?.trim()
            ?: throw ErrorLoadingException("Could not find anime title")
        
        // Extract poster image - try multiple selectors
        val poster = document.selectFirst("div.anime-thumbnail img, div.anime-image img, .anime-poster img")?.let { img ->
            img.attr("src").takeIf { it.isNotEmpty() } ?: img.attr("data-src")
        }
        
        // Extract year - looking for patterns like 2023
        val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
        val bodyText = document.text()
        val year = yearRegex.find(bodyText)?.value?.toIntOrNull()
        
        // Extract synopsis/description
        val synopsis = document.selectFirst("div.anime-story, div.anime-description, .synopsis, .anime-summary")?.text()?.trim()
        
        // Extract genres/tags
        val genres = document.select("div.anime-genres a, .genres a, .anime-info a[href*='genre']").map { it.text().trim() }
        
        // Determine if this is a movie or series
        val typeText = document.select("div.anime-info-box:contains(النوع) a, .anime-type a").text().lowercase()
        val isMovie = url.contains("/movie/") || 
                     title.contains("فيلم", true) || 
                     title.contains("movie", true) ||
                     typeText.contains("movie") ||
                     typeText.contains("فيلم")
        
        val tvType = when {
            isMovie -> TvType.AnimeMovie
            typeText.contains("ova") -> TvType.OVA
            typeText.contains("ona") -> TvType.OVA
            else -> TvType.Anime
        }
        
        // Handle movies
        if (tvType == TvType.AnimeMovie) {
            return newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = synopsis
                this.tags = genres
            }
        } 
        
        // Handle series - get all episodes
        val episodes = getEpisodes(document, url)
        
        return newAnimeLoadResponse(title, url, tvType) {
            this.posterUrl = poster
            this.year = year
            this.plot = synopsis
            this.tags = genres
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }
    
    private suspend fun getEpisodes(document: Document, animeUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        // Strategy 1: Check if we're on an episode page and extract episode list from JavaScript
        val episodeListScript = document.select("script").find { script ->
            script.html().contains("openEpisode") || script.html().contains("episodes")
        }
        
        if (episodeListScript != null) {
            val scriptContent = episodeListScript.html()
            val episodeUrlPattern = Regex("openEpisode\\('([^']+)'\\)")
            val episodeTitlePattern = Regex(">الحلقة\\s+(\\d+(?:\\.\\d+)?)")
            
            val episodeMatches = episodeUrlPattern.findAll(scriptContent)
            val titleMatches = episodeTitlePattern.findAll(document.html())
            
            val episodeUrls = episodeMatches.map { 
                try {
                    // Try to decode base64 URL if it looks like base64
                    val encodedUrl = it.groupValues[1]
                    if (encodedUrl.matches(Regex("^[A-Za-z0-9+/]*={0,2}$")) && encodedUrl.length % 4 == 0) {
                        // Simple base64 decode without Android dependency
                        java.util.Base64.getDecoder().decode(encodedUrl).toString(Charsets.UTF_8)
                    } else {
                        encodedUrl
                    }
                } catch (e: Exception) {
                    it.groupValues[1] // If not base64, use as is
                }
            }.toList()
            
            val episodeNumbers = titleMatches.map { 
                it.groupValues[1].toDoubleOrNull() ?: 0.0 
            }.toList()
            
            episodeUrls.forEachIndexed { index, episodeUrl ->
                val episodeNumber = episodeNumbers.getOrNull(index) ?: (index + 1).toDouble()
                episodes.add(
                    newEpisode(data = episodeUrl) {
                        name = "الحلقة ${episodeNumber.toInt()}"
                        episode = episodeNumber.toInt()
                    }
                )
            }
        }
        
        // Strategy 2: Find episode list in anime page
        if (episodes.isEmpty()) {
            val episodesContainer = document.selectFirst("div.episodes-list-container, div.episode-list, div.anime-episodes-list, ul.episodes")
            
            episodesContainer?.let { container ->
                val episodeLinks = container.select("a[href*='/episode/']")
                
                episodeLinks.forEach { episodeLink ->
                    val episodeUrl = episodeLink.attr("href")
                    if (episodeUrl.isNotEmpty()) {
                        val episodeTitle = episodeLink.text().trim()
                        val episodeNumber = extractEpisodeNumber(episodeTitle) ?: 
                                            extractEpisodeNumber(episodeUrl) ?: 
                                            episodes.size + 1.0
                        
                        episodes.add(
                            newEpisode(data = fixUrl(episodeUrl)) {
                                name = if (episodeTitle.isNotEmpty()) episodeTitle else "الحلقة ${episodeNumber.toInt()}"
                                episode = episodeNumber.toInt()
                            }
                        )
                    }
                }
            }
        }
        
        // Strategy 3: Try to fetch episodes list page using anime slug
        if (episodes.isEmpty()) {
            try {
                val animeSlug = animeUrl.substringAfterLast("/anime/").removeSuffix("/")
                if (animeSlug.isNotEmpty()) {
                    val episodesPageUrl = "$mainUrl/$animeSlug-episodes/"
                    val episodesDocument = app.get(episodesPageUrl, headers = headers).document
                    
                    val episodeItems = episodesDocument.select("div.episodes-card-container, .episode-item")
                    
                    episodeItems.forEach { episodeItem ->
                        val episodeLink = episodeItem.selectFirst("a[href*='/episode/']")
                        if (episodeLink != null) {
                            val episodeUrl = episodeLink.attr("href")
                            val episodeTitle = episodeItem.selectFirst("h3, .episode-title")?.text()?.trim() ?: "حلقة"
                            
                            val episodeNumber = extractEpisodeNumber(episodeTitle) ?: 
                                                extractEpisodeNumber(episodeUrl) ?: 
                                                episodes.size + 1.0
                            
                            episodes.add(
                                newEpisode(data = fixUrl(episodeUrl)) {
                                    name = episodeTitle
                                    episode = episodeNumber.toInt()
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors and try other methods
            }
        }
        
        // Strategy 4: Pattern-based URL construction as a last resort
        if (episodes.isEmpty()) {
            val animeSlug = animeUrl.substringAfterLast("/anime/").removeSuffix("/")
            if (animeSlug.isNotEmpty()) {
                // Common URL patterns for episodes
                val episodeUrlPatterns = listOf(
                    "$mainUrl/episode/$animeSlug-الحلقة-",
                    "$mainUrl/episode/$animeSlug-%d8%a7%d9%84%d8%ad%d9%84%d9%82%d8%a9-",
                    "$mainUrl/episode/$animeSlug-episode-"
                )
                
                // Try each pattern for first few episodes
                for (pattern in episodeUrlPatterns) {
                    var foundEpisodes = 0
                    
                    // Try first 3 episodes with this pattern
                    for (i in 1..3) {
                        val testUrl = "$pattern$i/"
                        try {
                            val response = app.get(testUrl, headers = headers)
                            if (response.code == 200) {
                                episodes.add(
                                    newEpisode(data = testUrl) {
                                        name = "الحلقة $i"
                                        episode = i
                                    }
                                )
                                foundEpisodes++
                            }
                        } catch (e: Exception) {
                            // Ignore failed requests
                        }
                    }
                    
                    if (foundEpisodes > 0) break
                }
            }
        }
        
        // Last resort - If still no episodes, use the anime URL itself
        if (episodes.isEmpty()) {
            episodes.add(
                newEpisode(data = animeUrl) {
                    name = "مشاهدة"
                    episode = 1
                }
            )
        }
        
        return episodes.distinctBy { it.episode }.sortedBy { it.episode }
    }
    
    private fun extractEpisodeNumber(text: String): Double? {
        // Arabic and English patterns for episode numbers
        val patterns = listOf(
            Regex("الحلقة[\\s\\-]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),      // Arabic episode
            Regex("حلقة[\\s\\-]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),        // Alternative Arabic format
            Regex("Episode[\\s\\-]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("الأونا[\\s\\-]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),      // ONA in Arabic
            Regex("EP[\\s\\-]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("الحلقة\\s*الخاصة\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),   // Special episode in Arabic
            Regex("ep-(\\d+(?:\\.\\d+)?)/?$", RegexOption.IGNORE_CASE),               // URL pattern: /ep-123/
            Regex("episode-(\\d+(?:\\.\\d+)?)/?$", RegexOption.IGNORE_CASE),          // URL pattern: /episode-123/
            Regex("الحلقة-(\\d+(?:\\.\\d+)?)/?$", RegexOption.IGNORE_CASE),           // URL pattern: /الحلقة-123/
            Regex("[\\/-](\\d+(?:\\.\\d+)?)/?$", RegexOption.IGNORE_CASE),            // URL ending with number
            Regex("\\b(\\d+(?:\\.\\d+)?)\\b", RegexOption.IGNORE_CASE)                // Any standalone number
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val numberStr = match.groupValues[1]
                return numberStr.toDoubleOrNull()?.takeIf { it > 0 && it < 10000 }
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
            
            // Strategy 1: Look for video player iframes
            val iframes = doc.select("iframe[src]")
            iframes.forEach { iframe ->
                val iframeSrc = iframe.attr("src")
                if (iframeSrc.isNotEmpty() && !iframeSrc.contains("youtube") && !iframeSrc.contains("trailer")) {
                    val fullUrl = fixUrl(iframeSrc)
                    
                    // Try to extract from iframe content
                    try {
                        val iframeDoc = app.get(fullUrl, headers = headers, referer = data).document
                        
                        // Look for video sources in iframe
                        val videoSources = iframeDoc.select("video source[src], video[src]")
                        videoSources.forEach { video ->
                            val videoSrc = video.attr("src")
                            if (videoSrc.isNotEmpty()) {
                                val quality = extractQualityFromText(video.attr("data-quality") ?: video.attr("label") ?: "")
                                val isM3u8 = videoSrc.contains(".m3u8")
                                val fixedVideoUrl = fixUrl(videoSrc)
                                
                                if (isM3u8) {
                                    M3u8Helper.generateM3u8(
                                        this.name,
                                        fixedVideoUrl,
                                        referer = data,
                                        headers = headers
                                    ).forEach { m3u8Link ->
                                        callback.invoke(m3u8Link)
                                        foundLinks = true
                                    }
                                } else {
                                    callback.invoke(
                                        newExtractorLink(
                                            source = this.name,
                                            name = "${this.name} Player",
                                            url = fixedVideoUrl
                                        ) {
                                            this.referer = data
                                            this.quality = quality
                                        }
                                    )
                                    foundLinks = true
                                }
                            }
                        }
                        
                        // Look for JSON data or JavaScript variables containing video URLs
                        val scripts = iframeDoc.select("script:not([src])")
                        scripts.forEach { script ->
                            val scriptContent = script.html()
                            
                            // Look for common video URL patterns
                            val videoUrlPatterns = listOf(
                                Regex("\"file\"\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*?)\""),
                                Regex("\"url\"\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*?)\""),
                                Regex("'file'\\s*:\\s*'([^']+\\.m3u8[^']*?)'"),
                                Regex("source\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*?)[\"']"),
                                Regex("src\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*?)[\"']"),
                                Regex("https?://[^\\s\"']+\\.m3u8[^\\s\"']*"),
                                Regex("https?://[^\\s\"']+\\.mp4[^\\s\"']*")
                            )
                            
                            videoUrlPatterns.forEach { pattern ->
                                val matches = pattern.findAll(scriptContent)
                                matches.forEach { match ->
                                    val videoUrl = if (match.groups.size > 1) match.groupValues[1] else match.value
                                    val cleanUrl = videoUrl.replace("\\", "")
                                    
                                    if (cleanUrl.isNotEmpty() && (cleanUrl.contains(".m3u8") || cleanUrl.contains(".mp4"))) {
                                        val fixedVideoUrl = fixUrl(cleanUrl)
                                        val isM3u8 = cleanUrl.contains(".m3u8")
                                        
                                        if (isM3u8) {
                                            M3u8Helper.generateM3u8(
                                                this.name,
                                                fixedVideoUrl,
                                                referer = data,
                                                headers = headers
                                            ).forEach { m3u8Link ->
                                                callback.invoke(m3u8Link)
                                                foundLinks = true
                                            }
                                        } else {
                                            callback.invoke(
                                                newExtractorLink(
                                                    source = this.name,
                                                    name = "${this.name} Direct",
                                                    url = fixedVideoUrl
                                                ) {
                                                    this.referer = data
                                                    this.quality = Qualities.Unknown.value
                                                }
                                            )
                                            foundLinks = true
                                        }
                                    }
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        // If iframe fails, still add the iframe URL as a fallback
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} Player",
                                url = fullUrl
                            ) {
                                this.referer = data
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
            }
            
            // Strategy 2: Look for download links section
            val downloadSections = doc.select("div:contains(تحميل), div:contains(روابط), div.download-links, .download-section")
            
            downloadSections.forEach { section ->
                val links = section.select("a[href]").filter { link ->
                    val href = link.attr("href").lowercase()
                    href.contains("mediafire") || href.contains("workupload") || 
                    href.contains("hexload") || href.contains("gofile") ||
                    href.contains("mega.nz") || href.contains("drive.google") ||
                    href.contains("upload") || href.contains("down")
                }
                
                links.forEach { link ->
                    val href = link.attr("href")
                    val linkText = link.text().trim()
                    
                    // Extract quality from text
                    val quality = extractQualityFromText(linkText)
                    val serviceName = linkText.takeIf { it.isNotEmpty() } ?: when {
                        href.contains("mediafire") -> "MediaFire"
                        href.contains("workupload") -> "WorkUpload"
                        href.contains("hexload") -> "HexLoad" 
                        href.contains("gofile") -> "GoFile"
                        href.contains("mega.nz") -> "Mega"
                        href.contains("drive.google") -> "Google Drive"
                        else -> "Download"
                    }
                    
                    if (href.isNotEmpty() && href.startsWith("http")) {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$serviceName ${getQualityName(quality)}",
                                url = href
                            ) {
                                this.referer = data
                                this.quality = quality
                            }
                        )
                        foundLinks = true
                    }
                }
            }
            
            // Strategy 3: Look for direct video elements
            val videos = doc.select("video[src], video source[src]")
            videos.forEach { video ->
                val videoSrc = video.attr("src")
                if (videoSrc.isNotEmpty()) {
                    val quality = extractQualityFromText(video.attr("data-quality") ?: video.attr("label") ?: "")
                    val isM3u8 = videoSrc.contains(".m3u8")
                    val fixedUrl = fixUrl(videoSrc)
                    
                    if (isM3u8) {
                        M3u8Helper.generateM3u8(
                            this.name,
                            fixedUrl,
                            referer = data,
                            headers = headers
                        ).forEach { m3u8Link ->
                            callback.invoke(m3u8Link)
                            foundLinks = true
                        }
                    } else {
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} Direct",
                                url = fixedUrl
                            ) {
                                this.referer = data
                                this.quality = quality
                            }
                        )
                        foundLinks = true
                    }
                }
            }
            
            // Strategy 4: Look for JavaScript variables and API calls in page scripts
            val scripts = doc.select("script:not([src])")
            scripts.forEach { script ->
                val scriptContent = script.html()
                
                // Look for video URLs in JavaScript
                val videoUrlPatterns = listOf(
                    Regex("\"file\"\\s*:\\s*\"([^\"]+\\.(m3u8|mp4)[^\"]*?)\""),
                    Regex("'file'\\s*:\\s*'([^']+\\.(m3u8|mp4)[^']*?)'"),
                    Regex("source\\s*[=:]\\s*[\"']([^\"']+\\.(m3u8|mp4)[^\"']*?)[\"']"),
                    Regex("https?://[^\\s\"']+\\.(m3u8|mp4)[^\\s\"']*")
                )
                
                videoUrlPatterns.forEach { pattern ->
                    val matches = pattern.findAll(scriptContent)
                    matches.forEach { match ->
                        val videoUrl = if (match.groups.size > 1) match.groupValues[1] else match.value
                        val cleanUrl = videoUrl.replace("\\", "")
                        
                        if (cleanUrl.isNotEmpty()) {
                            val fixedVideoUrl = fixUrl(cleanUrl)
                            val isM3u8 = cleanUrl.contains(".m3u8")
                            
                            if (isM3u8) {
                                M3u8Helper.generateM3u8(
                                    this.name,
                                    fixedVideoUrl,
                                    referer = data,
                                    headers = headers
                                ).forEach { m3u8Link ->
                                    callback.invoke(m3u8Link)
                                    foundLinks = true
                                }
                            } else {
                                callback.invoke(
                                    newExtractorLink(
                                        source = this.name,
                                        name = "${this.name} Script Source",
                                        url = fixedVideoUrl
                                    ) {
                                        this.referer = data
                                        this.quality = Qualities.Unknown.value
                                    }
                                )
                                foundLinks = true
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            // Log error but don't fail completely
        }
        
        return foundLinks
    }
    
    private fun getQualityName(quality: Int): String {
        return when (quality) {
            Qualities.P1080.value -> "FHD"
            Qualities.P720.value -> "HD" 
            Qualities.P480.value -> "SD"
            else -> ""
        }
    }
    
    private fun extractQualityFromText(text: String): Int {
        return when {
            text.contains("FHD", true) || text.contains("1080", true) || 
            text.contains("Full HD", true) || text.contains("خارقة", true) -> Qualities.P1080.value
            
            text.contains("HD", true) || text.contains("720", true) || 
            text.contains("عالية", true) -> Qualities.P720.value
            
            text.contains("SD", true) || text.contains("480", true) || 
            text.contains("متوسطة", true) -> Qualities.P480.value
            
            text.contains("360", true) -> Qualities.P360.value
            text.contains("240", true) -> Qualities.P240.value
            
            else -> Qualities.Unknown.value
        }
    }
    
    private fun fixUrl(url: String): String {
        return when {
            url.isEmpty() -> ""
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> mainUrl + url
            else -> "$mainUrl/$url"
        }
    }
}