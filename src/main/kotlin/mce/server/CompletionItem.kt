package mce.server

import mce.graph.Surface as S

data class CompletionItem(
    val name: String,
    val type: S.Term,
)
