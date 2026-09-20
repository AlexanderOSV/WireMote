package com.myremote.app.data

import kotlinx.serialization.Serializable

@Serializable
data class RemoteProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 39394,
    val macAddress: String? = null,
    val broadcastAddress: String = "255.255.255.255",
    val mouseSensitivity: Float = 1f,
    val commands: List<RemoteCommand> = emptyList()
)

@Serializable
data class RemoteCommand(
    val id: String,
    val label: String
)
