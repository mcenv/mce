package mce.builtin.src

import mce.ast.Core
import mce.phase.Normalizer

fun Normalizer.identity(): Core.VTerm = lookup(size - 1)
