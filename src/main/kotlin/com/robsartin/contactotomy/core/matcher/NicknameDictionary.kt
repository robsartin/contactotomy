package com.robsartin.contactotomy.core.matcher

class NicknameDictionary(groups: List<Set<String>>) {
    // A name may appear in several groups (e.g. unisex "sam" in both samuel & samantha),
    // so each name maps to the SET of group indices it belongs to.
    private val groupsOf: Map<String, Set<Int>> = buildMap<String, MutableSet<Int>> {
        groups.forEachIndexed { index, group ->
            group.forEach { getOrPut(it.lowercase()) { mutableSetOf() }.add(index) }
        }
    }

    /** True if both names are the same, or share at least one nickname group. */
    fun areEquivalent(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        if (x == y) return true
        val gx = groupsOf[x] ?: return false
        val gy = groupsOf[y] ?: return false
        return gx.any { it in gy }
    }

    companion object {
        fun fromResource(path: String = "/nicknames.csv"): NicknameDictionary {
            val text = NicknameDictionary::class.java.getResourceAsStream(path)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("nickname resource not found: $path")
            val groups = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line -> line.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet() }
                .filter { it.size >= 2 }
                .toList()
            return NicknameDictionary(groups)
        }
    }
}
