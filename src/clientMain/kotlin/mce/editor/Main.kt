package mce.editor

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.*
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.css.*
import kotlinx.html.dom.append
import kotlinx.html.js.textArea
import kotlinx.html.spellCheck
import kotlinx.html.style
import mce.protocol.HoverResponse
import mce.serialization.Mce
import kotlin.coroutines.CoroutineContext

fun main(): Unit = Application.start()

object Application : CoroutineScope {
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext = job

    private val client = HttpClient(Js) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Mce)
        }
    }

    fun start() {
        launch {
            client.webSocket(host = "localhost", port = 51130) {
                while (true) {
                    val response = receiveDeserialized<HoverResponse>()
                    // TODO
                }
            }
        }

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
}

inline fun css(set: RuleSet): String = CssBuilder().apply(set).toString()
