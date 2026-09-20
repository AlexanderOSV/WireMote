package com.myremote.app.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class RemoteClient {
    private val httpClient = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect(host: String, port: Int, listener: WebSocketListener) {
        Log.d("RemoteClient", "Initiating WebSocket connection to $host:$port")
        val request = Request.Builder().url("ws://$host:$port/ws").build()
        socket = try {
            httpClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e("RemoteClient", "Failed to create WebSocket request", e)
            throw e
        }
    }

    fun send(type: String, payload: JSONObject = JSONObject(), requestId: String? = null): Boolean {
        Log.d("RemoteClient", "Sending message type: $type")
        val message = JSONObject().put("type", type).put("payload", payload)
        requestId?.let { message.put("request_id", it) }
        val result = socket?.send(message.toString()) == true
        if (!result) Log.w("RemoteClient", "Message send failed (socket null or error)")
        return result
    }

    @Suppress("unused")
    fun close() {
        Log.d("RemoteClient", "Closing WebSocket")
        socket?.close(1000, "client closed")
        socket = null
    }
}
