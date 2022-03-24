package mce.server

import mce.phase.frontend.parse.Term

data class CompletionItem(
    val name: String,
    val type: Term,
)
