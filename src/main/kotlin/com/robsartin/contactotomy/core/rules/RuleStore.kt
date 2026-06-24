package com.robsartin.contactotomy.core.rules

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Loads and saves rule sets as pretty-printed JSON with a `type` discriminator. */
object RuleStore {
    private val json =
        Json {
            prettyPrint = true
            classDiscriminator = "type"
            encodeDefaults = false
        }

    fun toJson(ruleSet: RuleSet): String = json.encodeToString(RuleSet.serializer(), ruleSet)

    fun fromJson(text: String): RuleSet = json.decodeFromString(RuleSet.serializer(), text)

    fun load(path: Path): RuleSet = fromJson(path.readText())

    fun save(
        path: Path,
        ruleSet: RuleSet,
    ) = path.writeText(toJson(ruleSet))
}
