package com.myremote.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myremote.app.data.ProfileStore
import com.myremote.app.data.RemoteProfile
import com.myremote.app.network.RemoteClient
import com.myremote.app.network.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RemoteApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteApp() {
    val context = LocalContext.current
    val store = remember { ProfileStore(context.applicationContext) }
    val profiles by store.profiles.collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val client = remember { RemoteClient() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var connectionState by remember { mutableStateOf("Disconnected") }
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()

    LaunchedEffect(profiles) {
        if ((selectedProfileId == null) && profiles.isNotEmpty()) selectedProfileId = profiles.first().id
    }

    fun saveProfile(updated: RemoteProfile) {
        scope.launch { store.saveProfiles(profiles.map { if (it.id == updated.id) updated else it }) }
    }

    fun connect() {
        val profile = selectedProfile ?: return
        Log.d("RemoteApp", "Connecting to ${profile.host}:${profile.port}")
        connectionState = "Connecting..."
        try {
            client.connect(
                profile.host,
                profile.port,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d("RemoteApp", "WebSocket connected successfully")
                        scope.launch { connectionState = "Connected" }
                        try {
                            client.send("daemon.status")
                        } catch (e: Exception) {
                            Log.e("RemoteApp", "Error sending daemon status", e)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("RemoteApp", "WebSocket closed: $reason ($code)")
                        scope.launch { connectionState = "Disconnected" }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("RemoteApp", "WebSocket failure", t)
                        scope.launch { connectionState = "Connection failed" }
                    }
                },
            )
        } catch (e: Exception) {
            Log.e("RemoteApp", "Exception in connect method", e)
            connectionState = "Connection failed"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("My Remote") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { selectedTab = 0 }) { Text("Trackpad") }
                TextButton(onClick = { selectedTab = 1 }) { Text("Remote") }
                TextButton(onClick = { selectedTab = 2 }) { Text("Profiles") }
            }
            when (selectedTab) {
                0 -> TrackpadView(
                    profile = selectedProfile,
                    connectionState = connectionState,
                    client = client,
                    onSensitivityChange = { updated -> selectedProfile?.let { saveProfile(it.copy(mouseSensitivity = updated)) } },
                ) { profile ->
                    scope.launch(Dispatchers.IO) {
                        profile.macAddress?.let { WakeOnLan.wake(it, profile.broadcastAddress) }
                    }
                }
                1 -> RemoteView(client)
                else -> ProfilesView(
                    profiles = profiles,
                    selectedProfileId = selectedProfile?.id,
                    connectionState = connectionState,
                    onSelect = { selectedProfileId = it },
                    onConnect = ::connect,
                ) { profile -> scope.launch { store.saveProfiles(profiles + profile) } }
            }
        }
    }
}

@Composable
private fun TrackpadView(
    profile: RemoteProfile?,
    connectionState: String,
    client: RemoteClient,
    onSensitivityChange: (Float) -> Unit,
    onWake: (RemoteProfile) -> Unit,
) {
    val sensitivity = profile?.mouseSensitivity ?: 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(profile?.name ?: "No PC selected", style = MaterialTheme.typography.headlineSmall)
        Text(connectionState)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(sensitivity) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        client.send(
                            "mouse.move",
                            JSONObject().apply {
                                put("dx", dragAmount.x * sensitivity)
                                put("dy", dragAmount.y * sensitivity)
                            },
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text("Touch area") }
        
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { client.send("mouse.click", JSONObject().apply { put("button", "left") }) }
            ) {
                Text("Left Click")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { client.send("mouse.click", JSONObject().apply { put("button", "right") }) }
            ) {
                Text("Right Click")
            }
        }

        Spacer(Modifier.height(12.dp))
        var keyboardText by remember { mutableStateOf("") }
        TextField(
            value = keyboardText,
            onValueChange = { currentText ->
                if (currentText.length > keyboardText.length) {
                    val typedChar = currentText.last().toString()
                    client.send("keyboard.type", JSONObject().put("key", typedChar))
                } else if (currentText.length < keyboardText.length) {
                    client.send("keyboard.press", JSONObject().put("key", "backspace"))
                }
                keyboardText = currentText
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type here to send keys...") },
        )

        Spacer(Modifier.height(12.dp))
        Text("Mouse sensitivity: %.1fx".format(sensitivity))
        Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0.25f..3f)
        Button(onClick = { profile?.let(onWake) }, enabled = profile?.macAddress != null) { Text("Wake PC") }
    }
}

@Composable
private fun RemoteView(client: RemoteClient) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { client.send("remote.dpad", JSONObject().put("direction", "up")) }) { Text("Up") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { client.send("remote.back") }) { Text("Back") }
            Button(onClick = { client.send("remote.select") }) { Text("OK") }
            Button(onClick = { client.send("remote.play_pause") }) { Text("Play / Pause") }
        }
        Button(onClick = { client.send("remote.dpad", JSONObject().put("direction", "down")) }) { Text("Down") }
        Button(onClick = { client.send("system.sleep") }) { Text("Sleep PC") }
    }
}

@Composable
private fun ProfilesView(
    profiles: List<RemoteProfile>,
    selectedProfileId: String?,
    connectionState: String,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onAdd: (RemoteProfile) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    Column {
        Text("PC profiles", style = MaterialTheme.typography.headlineSmall)
        profiles.forEach { profile ->
            TextButton(onClick = { onSelect(profile.id) }) {
                Text(if (profile.id == selectedProfileId) "* ${profile.name}" else profile.name)
            }
        }
        Text(connectionState)
        Button(onClick = onConnect, enabled = selectedProfileId != null) { Text("Connect") }
        Spacer(Modifier.height(12.dp))
        TextField(value = name, onValueChange = { name = it }, label = { Text("PC name") })
        TextField(value = host, onValueChange = { host = it }, label = { Text("IP address") })
        TextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC address (optional)") })
        Button(
            onClick = {
                if (name.isNotBlank() && host.isNotBlank()) {
                    onAdd(
                        RemoteProfile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            host = host,
                            macAddress = mac.ifBlank { null },
                        ),
                    )
                    name = ""
                    host = ""
                    mac = ""
                }
            },
        ) { Text("Add PC") }
    }
}
