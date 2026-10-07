package com.batoh.core.storage.repository

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifRenameRulesTest {
    private val dir = "/storage/emulated/0/Pictures/GifPack"
    private fun row(id: Long, name: String, path: String) = GifRenameRules.Sibling(id, name, path)

    @Test fun clashIsCaseInsensitive() {
        val rows = listOf(row(2, "foo.gif", "$dir/foo.gif"))
        assertTrue(GifRenameRules.isNameTaken(rows, 1, "Foo.gif", dir, modern = false))
        assertTrue(GifRenameRules.isNameTaken(listOf(row(2, "foo.gif", "Pictures/GifPack/")), 1, "FOO.GIF", "Pictures/GifPack/", modern = true))
    }

    @Test fun ownRowIsNotAClash() {
        assertFalse(GifRenameRules.isNameTaken(listOf(row(1, "foo.gif", "$dir/foo.gif")), 1, "Foo.gif", dir, modern = false))
    }

    @Test fun subfolderFileIsNotAClash() {
        val rows = listOf(row(2, "foo.gif", "$dir/sub/foo.gif"))
        assertFalse(GifRenameRules.isNameTaken(rows, 1, "foo.gif", dir, modern = false))
    }

    @Test fun differentNameIsNotAClash() {
        assertFalse(GifRenameRules.isNameTaken(listOf(row(2, "bar.gif", "$dir/bar.gif")), 1, "foo.gif", dir, modern = false))
    }

    @Test fun likePatternEscapesWildcards() {
        assertEquals("/a/b\\_c\\%d\\\\e/%", GifRenameRules.legacyFolderLikePattern("/a/b_c%d\\e/file.gif"))
    }

    @Test fun commitInsideSuspendFunctionSurvivesCallerCancellation() = runBlocking {
        val started = Job()
        val release = Job()
        var returned: String? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            returned = commitIgnoringCancellation {
                started.complete()
                release.join()
                "uri"
            }
        }
        started.join()
        job.cancel()
        release.complete()
        job.join()
        assertEquals("uri", returned)
    }
}
