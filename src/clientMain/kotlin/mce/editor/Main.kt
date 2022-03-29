package mce.editor

import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.dom.append
import kotlinx.html.js.textArea
import kotlinx.html.spellCheck
import kotlinx.html.style

fun main() {
    document.body!!.append {
        textArea {
            spellCheck = false
            style = css {
                height = 100.vh
                width = 100.pct
                resize = Resize.none
                whiteSpace = WhiteSpace.pre
                overflowWrap = OverflowWrap.normal
                borderWidth = 0.px
                outline = Outline.none
                fontFamily = "Iosevka Term"
            }
        }
    }
}

inline fun css(set: RuleSet): String = CssBuilder().apply(set).toString()
