package mce.util.rcon

typealias PacketType = Int

const val AUTH: PacketType = 3
const val AUTH_RESPONSE: PacketType = 2
const val EXECCOMMAND: PacketType = 2
const val RESPONSE_VALUE: PacketType = 0
const val AUTH_FAILURE: PacketType = -1
