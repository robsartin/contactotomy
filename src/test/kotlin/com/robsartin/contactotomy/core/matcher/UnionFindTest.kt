package com.robsartin.contactotomy.core.matcher

import kotlin.test.Test
import kotlin.test.assertEquals

class UnionFindTest {
    @Test
    fun `unions form connected components`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("b", "c")
        val groups =
            uf
                .groups()
                .values
                .map { it.toSet() }
                .toSet()
        assertEquals(setOf(setOf("a", "b", "c"), setOf("d")), groups)
    }
}
