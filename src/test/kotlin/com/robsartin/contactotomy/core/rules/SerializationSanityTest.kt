package com.robsartin.contactotomy.core.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationSanityTest {
    @Serializable
    data class Foo(
        val x: Int,
    )

    @Test
    fun `kotlinx serialization round-trips`() {
        val text = Json.encodeToString(Foo.serializer(), Foo(7))
        assertEquals(Foo(7), Json.decodeFromString(Foo.serializer(), text))
    }
}
