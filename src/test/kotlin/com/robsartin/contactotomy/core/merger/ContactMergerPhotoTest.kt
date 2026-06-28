package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.testsupport.contact
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactMergerPhotoTest {
    private val merger = ContactMerger()

    private fun cluster(vararg members: Contact) = Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    @Test
    fun `primary member photo is used when primary has a photo`() {
        val primaryPhoto = ContactPhoto(base64 = "primarydata", contentType = "image/jpeg")
        val otherPhoto = ContactPhoto(base64 = "otherdata", contentType = "image/jpeg")
        // primary = "b" because it has newer modifiedAt -> sorted first
        val primary =
            contact(
                "b",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
                photo = primaryPhoto,
            )
        val other =
            contact(
                "a",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
                photo = otherPhoto,
            )
        val merged = merger.merge(cluster(primary, other)).merged
        assertEquals(primaryPhoto, merged.photo)
    }

    @Test
    fun `first member with photo is used when primary has no photo`() {
        val otherPhoto = ContactPhoto(url = "https://example.com/photo.jpg")
        val primary =
            contact(
                "b",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
                photo = null,
            )
        val other =
            contact(
                "a",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
                photo = otherPhoto,
            )
        val merged = merger.merge(cluster(primary, other)).merged
        assertEquals(otherPhoto, merged.photo)
    }

    @Test
    fun `merged photo is null when no member has a photo`() {
        val a = contact("a", given = "Alice", family = "Smith", photo = null)
        val b = contact("b", given = "Alice", family = "Smith", photo = null)
        val merged = merger.merge(cluster(a, b)).merged
        assertNull(merged.photo)
    }
}
