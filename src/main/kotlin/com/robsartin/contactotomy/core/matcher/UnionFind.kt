package com.robsartin.contactotomy.core.matcher

class UnionFind<T>(items: Collection<T>) {
    private val parent = HashMap<T, T>().apply { items.forEach { put(it, it) } }

    fun find(x: T): T {
        var root = x
        while (parent[root] != root) root = parent.getValue(root)
        var cur = x
        while (parent[cur] != root) {
            val next = parent.getValue(cur)
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: T, b: T) {
        parent[find(a)] = find(b)
    }

    fun groups(): Map<T, List<T>> = parent.keys.groupBy { find(it) }
}
