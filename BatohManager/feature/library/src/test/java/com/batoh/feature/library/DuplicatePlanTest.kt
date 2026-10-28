package com.batoh.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatePlanTest {
    @Test fun keepsFirstOfEachGroup() {
        val groups = listOf(listOf("a1", "a2", "a3"), listOf("b1", "b2"))
        assertEquals(listOf("a2", "a3", "b2"), DuplicatePlan.extras(groups))
    }

    @Test fun ignoresSingletonsAndEmpty() {
        assertEquals(emptyList<String>(), DuplicatePlan.extras(listOf(listOf("x"), emptyList())))
        assertEquals(emptyList<String>(), DuplicatePlan.extras(emptyList<List<String>>()))
    }
}
