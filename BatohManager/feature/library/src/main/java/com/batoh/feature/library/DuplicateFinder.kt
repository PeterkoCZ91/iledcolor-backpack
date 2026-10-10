package com.batoh.feature.library

import java.io.InputStream
import java.security.MessageDigest

/** One file considered for duplicate detection. [size] is the MediaStore byte size. */
data class DuplicateCandidate(val id: String, val name: String, val size: Long)

/** Files with identical content. [keep] is the suggested original, [extras] are the removable copies. */
data class DuplicateGroup(val files: List<DuplicateCandidate>) {
    val keep: DuplicateCandidate get() = files.first()
    val extras: List<DuplicateCandidate> get() = files.drop(1)
}

/**
 * Groups files with byte-identical content (JVM unit-tested). Only files sharing a size are hashed
 * (SHA-256, streamed), so most of the collection is never read. Unreadable files are skipped.
 */
class DuplicateFinder(private val open: suspend (DuplicateCandidate) -> InputStream?) {

    /** Groups of 2+ identical files, each ordered by name then id; groups ordered by their first file. */
    suspend fun find(candidates: List<DuplicateCandidate>): List<DuplicateGroup> {
        val groups = ArrayList<DuplicateGroup>()
        candidates.filter { it.size > 0 }.groupBy { it.size }.values.filter { it.size > 1 }.forEach { sameSize ->
            val byHash = LinkedHashMap<String, MutableList<DuplicateCandidate>>()
            for (candidate in sameSize) {
                val hash = hashOf(candidate) ?: continue
                byHash.getOrPut(hash) { ArrayList() }.add(candidate)
            }
            byHash.values.filter { it.size > 1 }.forEach { files ->
                groups.add(DuplicateGroup(files.sortedWith(compareBy({ it.name }, { it.id }))))
            }
        }
        return groups.sortedWith(compareBy({ it.keep.name }, { it.keep.id }))
    }

    private suspend fun hashOf(candidate: DuplicateCandidate): String? = try {
        open(candidate)?.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}
