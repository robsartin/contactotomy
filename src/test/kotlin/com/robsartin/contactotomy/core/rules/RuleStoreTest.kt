package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuleStoreTest {
    private val sample =
        RuleSet(
            listOf(
                Rule("old job", TextMatch(TextField.EMAIL, "*@indeed.com")),
                Rule("austin", PhoneMatch("512-???-????")),
                Rule(
                    "complex",
                    And(
                        listOf(
                            Or(listOf(Predicate(PredicateKind.NO_EMAIL), Predicate(PredicateKind.SOURCE_IS, source = Source.GOOGLE))),
                            Not(Predicate(PredicateKind.CREATED_BEFORE, before = Instant.parse("2015-01-01T00:00:00Z"))),
                        ),
                    ),
                ),
            ),
        )

    @Test fun `round-trips through json`() {
        val text = RuleStore.toJson(sample)
        assertEquals(sample, RuleStore.fromJson(text))
    }

    @Test fun `discriminator is type`() {
        assertEquals(true, RuleStore.toJson(sample).contains("\"type\": \"text\""))
    }

    @Test fun `malformed json throws`() {
        assertFailsWith<Exception> { RuleStore.fromJson("{ not valid") }
    }

    @Test fun `save then load round-trips through a file`(
        @org.junit.jupiter.api.io.TempDir tempDir: java.nio.file.Path,
    ) {
        val path = tempDir.resolve("rules.json")
        RuleStore.save(path, sample)
        assertEquals(sample, RuleStore.load(path))
    }
}
