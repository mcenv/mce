package mce

fun <T> topologicalSort(graph: Map<T, List<T>>): List<T> {
    val result: MutableList<T> = mutableListOf()
    val permanent: MutableSet<T> = mutableSetOf()
    val temporary: MutableSet<T> = mutableSetOf()

    fun visit(node: T) {
        if (permanent.contains(node)) return
        if (temporary.contains(node)) TODO()

        temporary.add(node)

        graph[node]?.forEach(::visit)

        temporary.remove(node)
        permanent.add(node)
        result.add(node)
    }

    graph.keys.forEach(::visit)

    return result.reversed()
}
