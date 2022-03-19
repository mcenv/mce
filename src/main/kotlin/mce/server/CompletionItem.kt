package mce.server

import mce.ast.surface.Term

data class CompletionItem(
    val name: String,
    val type: Term,
)
