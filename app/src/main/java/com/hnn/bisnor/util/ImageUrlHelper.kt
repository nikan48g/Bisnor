package com.hnn.bisnor.util

object ImageUrlHelper {
    /**
     * Fixes image URLs that are hosted on servers with SSL issues, domain timeouts,
     * or blocked inside certain Iranian ISPs by routing through a global high-speed CDN cache.
     */
    fun getOptimizedImageUrl(rawUrl: String): String {
        if (rawUrl.isEmpty()) return ""

        val cleaned = rawUrl.trim().replace("\\/", "/")
        
        // If image is on the troubled server-hi-speed-iran.info or hostinnegar.com
        if (cleaned.contains("server-hi-speed-iran.info") || 
            cleaned.contains("hostinnegar.com") || 
            cleaned.contains("windowsdiba.info")) {
            val stripped = cleaned
                .replace("https://", "")
                .replace("http://", "")
            return "https://wsrv.nl/?url=$stripped&output=webp&q=85"
        }

        return cleaned
    }
}
