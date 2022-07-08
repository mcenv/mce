package mce.util.rcon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mce.util.rcon.Packet.Companion.readPacket
import java.io.Closeable
import java.net.Socket

class RconClient private constructor(
    private val socket: Socket,
) : Closeable {
    private val mutex: Mutex = Mutex()

    private suspend fun request(packet: Packet): Packet =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                socket.outputStream.write(packet.toByteArray())
                socket.inputStream.readPacket()
            }
        }

    suspend fun auth(password: String) {
        val response = request(Packet(AUTH, password))
        if (response.type == AUTH_RESPONSE) {
            if (response.id == AUTH_FAILURE) {
                throw IllegalArgumentException("invalid password: '$password'")
            }
        } else {
            throw IllegalStateException("unexpected type: '${response.type}'")
        }
    }

    suspend fun exec(command: String): String = request(Packet(EXECCOMMAND, command)).body

    override fun close(): Unit = socket.close()

    companion object {
        operator fun invoke(hostname: String, port: Int): RconClient = RconClient(Socket(hostname, port))
    }
}
