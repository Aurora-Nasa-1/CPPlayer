package cp.player.util


    /**
     * Appends CP image resizing parameters to the URL.
     * @param size The desired size (e.g., 140 for 140x140).
     * @return The resized image URL.
     */
    fun String?.resized(size: Int): String? {
        if (this.isNullOrEmpty()) return this
        // CP images usually support ?param=XxY or ?imageView&thumbnail=XxY
        return if (this.contains("?")) {
            "$this&param=${size}y${size}"
        } else {
            "$this?param=${size}y${size}"
        }
    }
