package mce.editor

interface Electron {
    @JsName("onOpenFile")
    fun onOpenFile(callback: (event: dynamic, path: String) -> Unit)
}

val electron: Electron = js("window.electron")
