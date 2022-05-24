const { contextBridge, ipcRenderer } = require("electron")

contextBridge.exposeInMainWorld("electron", {
    onNewPack: callback => ipcRenderer.on("new-pack", callback),
    onExit: callback => ipcRenderer.on("exit", callback)
})

window.addEventListener("DOMContentLoaded", () => { /* TODO */ })
