package com.batoh.core.storage.repository

/** Pure checks that a MediaStore row lives in the app's own `Pictures/GifPack` folder. */
internal object GifPackLocation {
    const val RELATIVE_PATH = "Pictures/GifPack/"

    /** Android 10+: MediaStore RELATIVE_PATH, e.g. `Pictures/GifPack/` (subfolders excluded). */
    fun isCollectionRelativePath(relativePath: String?): Boolean =
        relativePath != null && relativePath.trimEnd('/') + "/" == RELATIVE_PATH

    /** Android 8–9: MediaStore DATA (absolute path) must be a direct child of `<Pictures>/GifPack`. */
    fun isCollectionFilePath(dataPath: String?, picturesDir: String): Boolean {
        if (dataPath.isNullOrEmpty()) return false
        val parent = dataPath.substringBeforeLast('/', missingDelimiterValue = "")
        val name = dataPath.substringAfterLast('/')
        return name.isNotEmpty() && name != "." && name != ".." &&
            parent == picturesDir.trimEnd('/') + "/GifPack"
    }
}
