package mce.builtin.src

import mce.ast.core.VTerm
import mce.phase.Normalizer

fun Normalizer.identity(): VTerm = lookup(size - 1)
