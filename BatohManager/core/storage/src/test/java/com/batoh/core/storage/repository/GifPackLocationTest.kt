package com.batoh.core.storage.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifPackLocationTest {
    @Test fun relativePathMustBeTheCollectionFolder() {
        assertTrue(GifPackLocation.isCollectionRelativePath("Pictures/GifPack/"))
        assertTrue(GifPackLocation.isCollectionRelativePath("Pictures/GifPack"))
        assertFalse(GifPackLocation.isCollectionRelativePath(null))
        assertFalse(GifPackLocation.isCollectionRelativePath("Pictures/"))
        assertFalse(GifPackLocation.isCollectionRelativePath("Pictures/GifPack/sub/"))
        assertFalse(GifPackLocation.isCollectionRelativePath("Download/GifPack/"))
        assertFalse(GifPackLocation.isCollectionRelativePath("Pictures/GifPackOther/"))
    }

    @Test fun dataPathMustBeDirectChildOfCollectionFolder() {
        val pictures = "/storage/emulated/0/Pictures"
        assertTrue(GifPackLocation.isCollectionFilePath("$pictures/GifPack/cat.gif", pictures))
        assertTrue(GifPackLocation.isCollectionFilePath("$pictures/GifPack/cat.gif", "$pictures/"))
        assertFalse(GifPackLocation.isCollectionFilePath(null, pictures))
        assertFalse(GifPackLocation.isCollectionFilePath("$pictures/cat.gif", pictures))
        assertFalse(GifPackLocation.isCollectionFilePath("$pictures/GifPack/sub/cat.gif", pictures))
        assertFalse(GifPackLocation.isCollectionFilePath("$pictures/GifPack/", pictures))
        assertFalse(GifPackLocation.isCollectionFilePath("/storage/emulated/0/Download/GifPack/cat.gif", pictures))
    }
}
