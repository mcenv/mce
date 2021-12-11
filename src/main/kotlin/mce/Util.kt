package mce

fun <T> topologicalSort(graph: Map<T, List<T>>): List<T> {
    val permanent: MutableSet<T> = mutableSetOf()
    val temporary: MutableSet<T> = mutableSetOf()
    val result: MutableList<T> = mutableListOf()
    val stack: MutableList<T> = graph.keys.toMutableList()

    while (stack.isNotEmpty()) {
        val node = stack.removeLast()

        if (permanent.contains(node)) continue
        if (temporary.contains(node)) throw Exception("cyclic")

        temporary += node

        stack.addAll(graph[node]!!)

        temporary -= node
        permanent += node
        result += node
    }

    return result
}
