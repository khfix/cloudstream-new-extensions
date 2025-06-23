package com.witanime

/**
 * Test Configuration for WiteAnime Provider
 * 
 * This file contains test URLs and expected patterns for validating
 * the provider functionality without requiring a full Android environment.
 */

object WitanimeTestConfig {
    
    // Test URLs for different sections
    const val MAIN_URL = "https://witanime.uno"
    const val CURRENTLY_AIRING_URL = "$MAIN_URL/anime-status/يعرض-الان/"
    const val COMPLETED_URL = "$MAIN_URL/anime-status/مكتمل/"
    const val TV_SERIES_URL = "$MAIN_URL/anime-type/tv/"
    const val MOVIES_URL = "$MAIN_URL/anime-type/movie/"
    
    // Sample anime URLs for testing
    val TEST_ANIME_URLS = listOf(
        "$MAIN_URL/anime/attack-on-titan/",
        "$MAIN_URL/anime/demon-slayer/",
        "$MAIN_URL/anime/one-piece/"
    )
    
    // Sample episode URLs for testing
    val TEST_EPISODE_URLS = listOf(
        "$MAIN_URL/episode/attack-on-titan-episode-1/",
        "$MAIN_URL/episode/demon-slayer-episode-1/"
    )
    
    // Search test queries
    val TEST_SEARCH_QUERIES = listOf(
        "هجوم العمالقة",  // Attack on Titan in Arabic
        "قاتل الشياطين", // Demon Slayer in Arabic
        "ون بيس",        // One Piece in Arabic
        "attack on titan",
        "demon slayer",
        "one piece"
    )
    
    // Expected HTML patterns for validation
    object ExpectedPatterns {
        val ANIME_TITLE_SELECTORS = listOf(
            "h1.entry-title",
            "h1",
            ".anime-title"
        )
        
        val EPISODE_LINK_SELECTORS = listOf(
            "a[href*='/episode/']",
            ".episode-link",
            ".episodes-list a"
        )
        
        val POSTER_IMAGE_SELECTORS = listOf(
            "div.anime-poster img",
            ".poster img",
            ".thumb img"
        )
        
        val EPISODE_NUMBER_PATTERNS = listOf(
            Regex("الحلقة\\s+(\\d+)"),          // Arabic: Episode X
            Regex("حلقة\\s+(\\d+)"),           // Arabic: Episode X (short)
            Regex("episode\\s+(\\d+)"),        // English: Episode X
            Regex("episode[-/](\\d+)")         // URL pattern: episode-X or episode/X
        )
        
        val VIDEO_URL_PATTERNS = listOf(
            Regex("(?:file|src|url)\\s*[:=]\\s*[\"']([^\"']+\\.(?:mp4|m3u8|mkv))[\"']"),
            Regex("https?://[^\\s\"']+\\.(?:mp4|m3u8|mkv)"),
            Regex("drive\\.google\\.com/[^\\s\"']+"),
            Regex("(?:workupload|hexload|gofile|mediafire)\\.com/[^\\s\"']+")
        )
    }
    
    // Quality mappings for testing
    val QUALITY_MAPPINGS = mapOf(
        "1080" to "1080p",
        "720" to "720p",
        "480" to "480p",
        "360" to "360p",
        "FHD" to "1080p",
        "HD" to "720p",
        "SD" to "480p"
    )
    
    // Expected anime types
    val ANIME_TYPE_KEYWORDS = mapOf(
        "فيلم" to "AnimeMovie",
        "movie" to "AnimeMovie",
        "OVA" to "OVA",
        "ONA" to "OVA"
    )
    
    // Status keywords
    val STATUS_KEYWORDS = mapOf(
        "يعرض الان" to "Ongoing",
        "مستمر" to "Ongoing",
        "ongoing" to "Ongoing",
        "مكتمل" to "Completed",
        "completed" to "Completed"
    )
    
    /**
     * Validate if a URL matches expected WitAnime patterns
     */
    fun isValidWitanimeUrl(url: String): Boolean {
        return url.startsWith(MAIN_URL) && (
            url.contains("/anime/") || 
            url.contains("/episode/") ||
            url.contains("/anime-type/") ||
            url.contains("/anime-status/") ||
            url.contains("?s=")
        )
    }
    
    /**
     * Extract expected episode number from various text patterns
     */
    fun extractEpisodeNumber(text: String): Int? {
        return ExpectedPatterns.EPISODE_NUMBER_PATTERNS
            .firstNotNullOfOrNull { pattern ->
                pattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
            }
    }
    
    /**
     * Determine expected anime type from title or metadata
     */
    fun determineAnimeType(title: String, metadata: String = ""): String {
        val combinedText = "$title $metadata".lowercase()
        return ANIME_TYPE_KEYWORDS.entries
            .firstOrNull { (keyword, _) -> combinedText.contains(keyword, ignoreCase = true) }
            ?.value ?: "Anime"
    }
    
    /**
     * Determine expected status from text
     */
    fun determineStatus(text: String): String? {
        return STATUS_KEYWORDS.entries
            .firstOrNull { (keyword, _) -> text.contains(keyword, ignoreCase = true) }
            ?.value
    }
}
