const { spawn } = require("child_process")
const path = require("path")
const { platform } = require("process")
const { app, BrowserWindow, nativeImage, Menu, dialog } = require("electron")

const launchServer = () => {
    const extension = platform === "win32" ? ".bat" : ""
    const mce = path.join(__dirname, "install", "bin", `mce${extension}`)
    spawn(mce, ["launch"])
}

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

const main = async () => {
    launchServer()

    await app.whenReady()
    createWindow()

    app.on("activate", () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow()
        }
    })
}

main()

app.on("window-all-closed", () => {
    if (platform !== "darwin") {
        app.quit()
    }
})
