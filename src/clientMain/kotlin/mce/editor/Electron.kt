package mce.editor

interface Electron {
    @JsName("onOpenFile")
    fun onOpenFile(callback: (event: dynamic, path: String) -> Unit)

    @JsName("onExit")
    fun onExit(callback: (event: dynamic) -> Unit)
}

val electron: Electron = js("window.electron")
