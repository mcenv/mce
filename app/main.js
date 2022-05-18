// @ts-check

const { spawn } = require("child_process")
const http = require("http")
const path = require("path")
const { platform } = require("process")
const { app, BrowserWindow, nativeImage, Menu } = require("electron")

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
                    label: "New Pack",
                    click: () => {
                        win.webContents.send("new-pack")
                    }
                },
                {
                    label: "Exit",
                    role: "quit"
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

    app.on("window-all-closed", () => {
        if (platform !== "darwin") {
            app.quit()
        }
    })

    app.on("before-quit", () => {
        http.get("http://localhost:51130/shutdown")
    })
}

main()
