const { contextBridge, ipcRenderer } = require("electron")

contextBridge.exposeInMainWorld("electron", {
    onOpenFile: callback => ipcRenderer.on("open-file", callback),
    onExit: callback => ipcRenderer.on("exit", callback)
})

window.addEventListener("DOMContentLoaded", () => { /* TODO */ })
