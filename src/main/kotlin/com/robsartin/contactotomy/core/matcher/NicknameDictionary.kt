package com.robsartin.contactotomy.core.matcher

class NicknameDictionary(groups: List<Set<String>>) {
    private val groupOf: Map<String, Int> = buildMap {
        groups.forEachIndexed { index, group ->
            group.forEach { put(it.lowercase(), index) }
        }
    }

    /** True if both names are the same, or belong to the same nickname group. */
    fun areEquivalent(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        if (x == y) return true
        val gx = groupOf[x] ?: return false
        val gy = groupOf[y] ?: return false
        return gx == gy
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
