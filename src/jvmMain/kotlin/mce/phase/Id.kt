package mce.phase

import java.security.SecureRandom

private val generator: SecureRandom = SecureRandom()

/**
 * Creates a fresh [Id].
 */
fun freshId(): Id = Id(generator.nextLong(), generator.nextLong())
