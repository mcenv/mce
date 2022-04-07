const { contextBridge, ipcRenderer } = require("electron")

contextBridge.exposeInMainWorld("electron", {
    onOpenFile: callback => ipcRenderer.on("open-file", callback)
})

window.addEventListener("DOMContentLoaded", () => { /* TODO */ })
