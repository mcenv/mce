const path = require("path")
const { app, BrowserWindow, nativeImage } = require("electron")

const createWindow = () => {
    const win = new BrowserWindow({
        title: "mce",
        icon: nativeImage.createFromPath(path.join(__dirname, "icon.png")),
        width: 800,
        height: 600,
        webPreferences: {
            preload: path.join(__dirname, "preload.js"),
        },
    })

    win.loadFile("index.html")
}

(async () => {
    await app.whenReady()
    createWindow()

    app.on("activate", () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow()
        }
    })
})()

app.on("window-all-closed", () => {
    if (process.platform !== "darwin") {
        app.quit()
    }
})
