package mce.graph

import java.util.*

/**
 * A unique identifier for a node.
 */
typealias Id = UUID

/**
 * Creates a fresh [Id].
 */
fun freshId(): Id = UUID.randomUUID()
