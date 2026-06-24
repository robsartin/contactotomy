package com.robsartin.contactotomy.core.apply

enum class Action { ACCEPT, REJECT }

data class ExcludedValue(
    val field: String,
    val value: String,
)

data class MergeDecision(
    val clusterId: String,
    val action: Action,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
)
