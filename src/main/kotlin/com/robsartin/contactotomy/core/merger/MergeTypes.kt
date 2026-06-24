package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import java.time.Instant

data class FieldProvenance(
    val field: String,
    val value: String,
    val sourceContactIds: List<String>,
)

data class ConflictCandidate(
    val value: String,
    val sourceContactId: String,
    val modifiedAt: Instant?,
)

data class FieldConflict(
    val field: String,
    val candidates: List<ConflictCandidate>,
    val chosen: String,
)

data class MergeProposal(
    val cluster: Cluster,
    val merged: Contact,
    val provenance: List<FieldProvenance>,
    val conflicts: List<FieldConflict>,
)
