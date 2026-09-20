package com.myremote.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class RemoteClient {
    private val httpClient = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect(host: String, port: Int, listener: WebSocketListener) {
        val request = Request.Builder().url("ws://$host:$port/ws").build()
        socket = httpClient.newWebSocket(request, listener)
    }

    fun send(type: String, payload: JSONObject = JSONObject(), requestId: String? = null): Boolean {
        val message = JSONObject().put("type", type).put("payload", payload)
        requestId?.let { message.put("request_id", it) }
        return socket?.send(message.toString()) == true
    }

    fun close() {
        socket?.close(1000, "client closed")
        socket = null
    }
}
