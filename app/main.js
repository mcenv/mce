const path = require("path")
const { app, BrowserWindow, nativeImage, Menu, dialog } = require("electron")

const createWindow = () => {
    const win = new BrowserWindow({
        title: "mce",
        icon: nativeImage.createFromPath(path.join(__dirname, "icon.png")),
        width: 800,
        height: 600,
        webPreferences: {
            preload: path.join(__dirname, "preload.js"),
        }
    })

    Menu.setApplicationMenu(Menu.buildFromTemplate([
        {
            label: "File",
            submenu: [
                {
                    label: "Open File",
                    click: async () => {
                        const { canceled, filePaths } = await dialog.showOpenDialog({})
                        if (!canceled) {
                            win.webContents.send("open-file", filePaths[0])
                        }
                    }
                }
            ]
        }
    ]))

    win.loadFile("index.html")
    win.webContents.openDevTools()
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
