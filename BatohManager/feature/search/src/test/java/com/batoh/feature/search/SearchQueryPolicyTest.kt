package com.batoh.feature.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryPolicyTest {
    @Test
    fun blankQueriesDoNotHitTheNetwork() {
        assertFalse(requiresRemoteSearch(""))
        assertFalse(requiresRemoteSearch("   "))
    }

    @Test
    fun nonBlankQueriesSearchRemotely() {
        assertTrue(requiresRemoteSearch("cat"))
        assertTrue(requiresRemoteSearch(" cat "))
    }
}
