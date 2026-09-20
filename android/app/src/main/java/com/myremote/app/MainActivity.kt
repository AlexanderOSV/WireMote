package com.myremote.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.myremote.app.data.ProfileStore
import com.myremote.app.data.RemoteProfile
import com.myremote.app.network.RemoteClient
import com.myremote.app.network.WakeOnLan
import com.myremote.app.ui.MyRemoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyRemoteTheme { RemoteApp() } }
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

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { selectedTab = 0 }) { Text("Trackpad") }
                TextButton(onClick = { selectedTab = 1 }) { Text("Remote") }
                TextButton(onClick = { selectedTab = 2 }) { Text("Profiles") }
                TextButton(onClick = { selectedTab = 3 }) { Text("Settings") }
            }
            when (selectedTab) {
                0 -> TrackpadView(
                    profile = selectedProfile,
                    connectionState = connectionState,
                    client = client,
                ) { profile ->
                    scope.launch(Dispatchers.IO) {
                        profile.macAddress?.let { WakeOnLan.wake(it, profile.broadcastAddress) }
                    }
                }
                1 -> RemoteView(client)
                2 -> ProfilesView(
                    profiles = profiles,
                    selectedProfileId = selectedProfile?.id,
                    connectionState = connectionState,
                    onSelect = { selectedProfileId = it },
                    onConnect = ::connect,
                ) { profile -> scope.launch { store.saveProfiles(profiles + profile) } }
                3 -> SettingsView(
                    profile = selectedProfile,
                    onSensitivityChange = { updated ->
                        selectedProfile?.let { saveProfile(it.copy(mouseSensitivity = updated)) }
                    },
                )
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
    onWake: (RemoteProfile) -> Unit,
) {
    val sensitivity = profile?.mouseSensitivity ?: 1f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                profile?.name ?: "No PC selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (connectionState == "Connected") Color(0xFF4CAF50) else Color(0xFFF44336),
                    ),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { profile?.let(onWake) },
                enabled = profile?.macAddress != null,
            ) {
                Text("⏻", style = MaterialTheme.typography.titleLarge)
            }
            IconButton(
                onClick = { client.send("system.sleep") },
                enabled = connectionState == "Connected",
            ) {
                Text("⏼", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .pointerInput(sensitivity) {
                        awaitEachGesture {
                            val downEvent = awaitFirstDown(requireUnconsumed = false)
                            var maxFingers = 1
                            var hasMovedSignificantDistance = false
                            var previousPosition = downEvent.position
                            val activePointerIds = mutableSetOf(downEvent.id)

                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        activePointerIds.add(change.id)
                                    } else {
                                        activePointerIds.remove(change.id)
                                    }
                                }
                                maxFingers = maxOf(maxFingers, activePointerIds.size)
                                val primaryChange = event.changes.firstOrNull { it.id == downEvent.id }

                                if (primaryChange != null) {
                                    val delta = primaryChange.position - previousPosition
                                    previousPosition = primaryChange.position
                                    if (delta.getDistance() > 0f) {
                                        if (delta.getDistance() > 15f) {
                                            hasMovedSignificantDistance = true
                                        }
                                        if (hasMovedSignificantDistance) {
                                            primaryChange.consume()
                                            client.send(
                                                "mouse.move",
                                                JSONObject().apply {
                                                    put("dx", delta.x * sensitivity)
                                                    put("dy", delta.y * sensitivity)
                                                },
                                            )
                                        }
                                    }
                                }

                                if (activePointerIds.isEmpty()) break
                            }

                            if (!hasMovedSignificantDistance) {
                                val button = when (maxFingers) {
                                    1 -> "left"
                                    2 -> "right"
                                    else -> null
                                }
                                button?.let {
                                    client.send("mouse.button", JSONObject().apply { put("button", it) })
                                }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("Touch area") }
            Box(
                Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val amount = (-dragAmount / 40f).toInt().let {
                                if (it == 0) (-dragAmount).toInt().coerceIn(-1, 1) else it
                            }
                            if (amount != 0) {
                                client.send(
                                    "mouse.scroll",
                                    JSONObject().apply {
                                        put("amount", amount)
                                        put("axis", "vertical")
                                    },
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Scroll")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { client.send("mouse.button", JSONObject().apply { put("button", "left") }) },
            ) {
                Text("Left Click")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { client.send("mouse.button", JSONObject().apply { put("button", "right") }) },
            ) {
                Text("Right Click")
            }
        }

        Spacer(Modifier.height(12.dp))
        var textFieldState by remember {
            mutableStateOf(TextFieldValue(text = " ", selection = TextRange(1)))
        }
        val keyboardController = LocalSoftwareKeyboardController.current
        TextField(
            value = textFieldState,
            onValueChange = { newState ->
                val currentText = newState.text
                val oldText = textFieldState.text

                if (currentText.contains("\n")) {
                    client.send("keyboard.key", JSONObject().apply { put("key", "Enter") })
                    keyboardController?.hide()
                    textFieldState = TextFieldValue(text = " ", selection = TextRange(1))
                } else if (currentText.length > oldText.length) {
                    val typedText = currentText.substring(oldText.length)
                    client.send("keyboard.text", JSONObject().apply { put("text", typedText) })
                    textFieldState = TextFieldValue(text = " ", selection = TextRange(1))
                } else if (currentText.length < oldText.length || currentText.isEmpty()) {
                    client.send("keyboard.key", JSONObject().apply { put("key", "Backspace") })
                    textFieldState = TextFieldValue(text = " ", selection = TextRange(1))
                } else {
                    textFieldState = newState
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type here to send keys...") },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    client.send("keyboard.key", JSONObject().apply { put("key", "Enter") })
                    keyboardController?.hide()
                    textFieldState = TextFieldValue(text = " ", selection = TextRange(1))
                },
            ),
        )

    }
}

@Composable
private fun SettingsView(
    profile: RemoteProfile?,
    onSensitivityChange: (Float) -> Unit,
) {
    Column {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Text("Mouse sensitivity: %.1fx".format(profile?.mouseSensitivity ?: 1f))
        Slider(
            value = profile?.mouseSensitivity ?: 1f,
            onValueChange = onSensitivityChange,
            valueRange = 0.25f..3f,
        )
    }
}

@Composable
private fun RemoteView(client: RemoteClient) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HoldKeyButton(
            client,
            "ArrowUp",
            "ArrowUp",
            "↑",
            Modifier
                .size(width = 150.dp, height = 96.dp)
                .offset(y = 85.dp),
            WedgeShape(WedgeDirection.Up),
        )
        Spacer(Modifier.height(0.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldKeyButton(
                client,
                "ArrowLeft",
                "ArrowLeft",
                "←",
                Modifier
                    .size(width = 96.dp, height = 150.dp)
                    .offset(x = 55.dp),
                WedgeShape(WedgeDirection.Left),
            )
            Button(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                onClick = {
                    client.send("keyboard.key", JSONObject().put("key", "Enter"))
                },
            ) {
                Text("OK", style = MaterialTheme.typography.titleMedium)
            }
            HoldKeyButton(
                client,
                "ArrowRight",
                "ArrowRight",
                "→",
                Modifier
                    .size(width = 96.dp, height = 150.dp)
                    .offset(x = (-55).dp),
                WedgeShape(WedgeDirection.Right),
            )
        }
        Spacer(Modifier.height(0.dp))
        HoldKeyButton(
            client,
            "ArrowDown",
            "ArrowDown",
            "↓",
            Modifier
                .size(width = 150.dp, height = 96.dp)
                .offset(y = (-85).dp),
            WedgeShape(WedgeDirection.Down),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                modifier = Modifier.size(width = 112.dp, height = 56.dp),
                shape = RoundedCornerShape(20.dp),
                onClick = { client.send("remote.back") },
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            Button(
                modifier = Modifier.size(width = 160.dp, height = 56.dp),
                shape = RoundedCornerShape(20.dp),
                onClick = { client.send("remote.play_pause") },
            ) {
                Text("▶", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text("Play / Pause")
            }
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101418,
    widthDp = 360,
    heightDp = 640,
)
@Composable
private fun RemotePreview() {
    MyRemoteTheme {
        RemoteView(RemoteClient())
    }
}

@Composable
private fun HoldKeyButton(
    client: RemoteClient,
    label: String,
    key: String,
    icon: String,
    modifier: Modifier,
    shape: Shape,
) {
    Button(
        onClick = {},
        modifier = modifier
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    client.send(
                        "keyboard.key",
                        JSONObject().apply {
                            put("key", key)
                            put("direction", "press")
                        },
                    )
                    try {
                        var isPressed = true
                        while (isPressed) {
                            val event = withTimeoutOrNull(60) { awaitPointerEvent() }
                            if (event == null) {
                                client.send(
                                    "keyboard.key",
                                    JSONObject().apply {
                                        put("key", key)
                                        put("direction", "press")
                                    },
                                )
                            } else {
                                isPressed = event.changes.any { it.id == down.id && it.pressed }
                            }
                        }
                    } finally {
                        client.send(
                            "keyboard.key",
                            JSONObject().apply {
                                put("key", key)
                                put("direction", "release")
                            },
                        )
                    }
                }
        },
        shape = shape,
    ) { Text(icon, style = MaterialTheme.typography.headlineSmall) }
}

private enum class WedgeDirection {
    Up,
    Down,
    Left,
    Right,
}

private class WedgeShape(
    private val direction: WedgeDirection,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        fun point(x: Float, y: Float): androidx.compose.ui.geometry.Offset =
            when (direction) {
                WedgeDirection.Up -> androidx.compose.ui.geometry.Offset(x * size.width, y * size.height)
                WedgeDirection.Down -> androidx.compose.ui.geometry.Offset(
                    (1f - x) * size.width,
                    (1f - y) * size.height,
                )
                WedgeDirection.Left -> androidx.compose.ui.geometry.Offset(
                    y * size.width,
                    (1f - x) * size.height,
                )
                WedgeDirection.Right -> androidx.compose.ui.geometry.Offset(
                    (1f - y) * size.width,
                    x * size.height,
                )
            }

        val outerRadiusX = size.width * 0.46f
        val outerRadiusY = size.height * 0.46f
        val innerRadiusX = size.width * 0.16f
        val innerRadiusY = size.height * 0.16f
        fun arcPoint(
            radiusX: Float,
            radiusY: Float,
            degrees: Double,
        ): androidx.compose.ui.geometry.Offset {
            val radians = Math.toRadians(degrees)
            return point(
                0.5f + (radiusX * cos(radians).toFloat() / size.width),
                0.5f + (radiusY * sin(radians).toFloat() / size.height),
            )
        }

        val path = Path().apply {
            // Start at the left side of the outer arc
            moveTo(
                arcPoint(outerRadiusX, outerRadiusY, 220.0).x,
                arcPoint(outerRadiusX, outerRadiusY, 220.0).y,
            )

            // Outer curve
            for (step in 1..20) {
                val position = arcPoint(outerRadiusX, outerRadiusY, 220.0 + step * 5.0)
                lineTo(position.x, position.y)
            }

            // Right edge of the segment
            lineTo(
                arcPoint(innerRadiusX, innerRadiusY, 320.0).x,
                arcPoint(innerRadiusX, innerRadiusY, 320.0).y,
            )

            // Inner curve, travelling back
            for (step in 1..20) {
                val position = arcPoint(innerRadiusX, innerRadiusY, 320.0 - step * 5.0)
                lineTo(position.x, position.y)
            }

            // Left edge
            close()
        }
        return Outline.Generic(path)
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
