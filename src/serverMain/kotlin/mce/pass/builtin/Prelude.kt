package mce.pass.builtin

import mce.pass.builtin.src.*

val prelude: Set<String> = listOf(
    identity,
    `+`,
    `-`,
    `×`,
    `÷`,
    `%`,
    `≡`,
    `≢`,
).map { it.name }.toSet()
