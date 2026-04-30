package cp.player.util

object ImageUtils {
    /**
     * Appends CP image resizing parameters to the URL.
     * @param url The original image URL.
     * @param size The desired size (e.g., 140 for 140x140).
     * @return The resized image URL.
     */
    fun getResizedImageUrl(url: String?, size: Int): String? {
        if (url.isNullOrEmpty()) return url
        // CP images usually support ?param=XxY or ?imageView&thumbnail=XxY
        return if (url.contains("?")) {
            "$url&param=${size}y${size}"
        } else {
            "$url?param=${size}y${size}"
        }
    }
}
