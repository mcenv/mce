package mce.editor

interface Electron {
    @JsName("onNewPack")
    fun onNewPack(callback: (event: dynamic) -> Unit)

    @JsName("onExit")
    fun onExit(callback: (event: dynamic) -> Unit)
}

val electron: Electron = js("window.electron")
