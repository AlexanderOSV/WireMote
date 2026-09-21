package com.wiremote.app

import android.graphics.Rect
import android.graphics.Region
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.wiremote.app.data.ProfileStore
import com.wiremote.app.data.RemoteProfile
import com.wiremote.app.network.RemoteClient
import com.wiremote.app.network.DiscoveredDevice
import com.wiremote.app.network.Discovery
import com.wiremote.app.network.WakeOnLan
import com.wiremote.app.ui.WireMoteTheme
import com.wiremote.app.ui.icons.PowerSettingsNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        setContent { WireMoteTheme { RemoteApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { ProfileStore(context.applicationContext) }
    val profiles by store.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val client = remember { RemoteClient() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var connectionState by remember { mutableStateOf("Disconnected") }
    var isVisible by remember { mutableStateOf(true) }
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
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            scope.launch { connectionState = "Connected" }
                            try {
                                client.send("daemon.status")
                            } catch (e: Exception) {
                                Log.e("RemoteApp", "Error sending daemon status", e)
                            }
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("RemoteApp", "WebSocket closed: $reason ($code)")
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            scope.launch { connectionState = "Disconnected" }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("RemoteApp", "WebSocket failure", t)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            scope.launch { connectionState = "Connection failed" }
                        }
                    }
                },
            )
        } catch (e: Exception) {
            Log.e("RemoteApp", "Exception in connect method", e)
            connectionState = "Connection failed"
        }
    }

    fun wakeAndReconnect(profile: RemoteProfile) {
        scope.launch(Dispatchers.IO) {
            profile.macAddress?.let { WakeOnLan.wake(it, profile.broadcastAddress) }
            delay(5000)
            withContext(Dispatchers.Main) {
                if (selectedProfileId == profile.id) connect()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    isVisible = false
                    scope.coroutineContext.cancelChildren()
                    client.close()
                    connectionState = "Disconnected"
                }

                Lifecycle.Event.ON_START -> isVisible = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.coroutineContext.cancelChildren()
            client.close()
        }
    }

    LaunchedEffect(isVisible, selectedProfile?.id) {
        if (isVisible && selectedProfile != null && connectionState == "Disconnected") {
            connect()
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                IconButton(onClick = { selectedTab = 0 }) {
                    Icon(
                        painter = painterResource(com.wiremote.app.R.drawable.mouse_24),
                        contentDescription = "Trackpad",
                    )
                }
                IconButton(onClick = { selectedTab = 1 }) {
                    Icon(
                        painter = painterResource(com.wiremote.app.R.drawable.tv_remote_24),
                        contentDescription = "Remote",
                    )
                }
                IconButton(onClick = { selectedTab = 2 }) {
                    Icon(
                        painter = painterResource(com.wiremote.app.R.drawable.settings_24),
                        contentDescription = "Settings",
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (connectionState == "Connected") {
                            client.send("system.sleep")
                            connectionState = "Disconnected"
                        } else {
                            selectedProfile?.let(::wakeAndReconnect)
                        }
                    },
                    enabled = selectedProfile != null,
                ) {
                    Icon(
                        imageVector = PowerSettingsNew,
                        contentDescription = if (connectionState == "Connected") "Sleep" else "Wake",
                    )
                }
            }
            when (selectedTab) {
                0 -> TrackpadView(
                    profile = selectedProfile,
                    connectionState = connectionState,
                    client = client,
                )
                1 -> RemoteView(
                    profile = selectedProfile,
                    connectionState = connectionState,
                    client = client,
                )
                2 -> SettingsView(
                    profile = selectedProfile,
                    connectionState = connectionState,
                    onSensitivityChange = { updated ->
                        selectedProfile?.let { saveProfile(it.copy(mouseSensitivity = updated)) }
                    },
                    profiles = profiles,
                    selectedProfileId = selectedProfile?.id,
                    onSelect = { selectedProfileId = it },
                    onConnect = ::connect,
                    onAddProfile = { profile ->
                        selectedProfileId = profile.id
                        scope.launch { store.saveProfiles(profiles + profile) }
                    },
                    onUpdateProfile = { updated ->
                        scope.launch { store.saveProfiles(profiles.map { if (it.id == updated.id) updated else it }) }
                    },
                    onDeleteProfile = { id ->
                        val remaining = profiles.filterNot { it.id == id }
                        selectedProfileId = remaining.firstOrNull()?.id
                        scope.launch { store.saveProfiles(remaining) }
                    },
                )
                else -> SettingsView(
                    profile = selectedProfile,
                    connectionState = connectionState,
                    onSensitivityChange = { updated ->
                        selectedProfile?.let { saveProfile(it.copy(mouseSensitivity = updated)) }
                    },
                    profiles = profiles,
                    selectedProfileId = selectedProfile?.id,
                    onSelect = { selectedProfileId = it },
                    onConnect = ::connect,
                    onAddProfile = { profile ->
                        selectedProfileId = profile.id
                        scope.launch { store.saveProfiles(profiles + profile) }
                    },
                    onUpdateProfile = { updated ->
                        scope.launch { store.saveProfiles(profiles.map { if (it.id == updated.id) updated else it }) }
                    },
                    onDeleteProfile = { id ->
                        val remaining = profiles.filterNot { it.id == id }
                        selectedProfileId = remaining.firstOrNull()?.id
                        scope.launch { store.saveProfiles(remaining) }
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackpadView(
    profile: RemoteProfile?,
    connectionState: String,
    client: RemoteClient,
) {
    val sensitivity = profile?.mouseSensitivity ?: 1f
    Column {
        ConnectionBar(profile, connectionState)
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
private fun ConnectionBar(
    profile: RemoteProfile?,
    connectionState: String,
) {
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
    }
}

@Composable
private fun SettingsView(
    profile: RemoteProfile?,
    connectionState: String,
    onSensitivityChange: (Float) -> Unit,
    profiles: List<RemoteProfile>,
    selectedProfileId: String?,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onAddProfile: (RemoteProfile) -> Unit,
    onUpdateProfile: (RemoteProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
) {
    var addingProfile by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ConnectionBar(profile, connectionState)
        Spacer(Modifier.height(12.dp))
        Text("Mouse sensitivity: %.1fx".format(profile?.mouseSensitivity ?: 1f))
        Slider(
            value = profile?.mouseSensitivity ?: 1f,
            onValueChange = onSensitivityChange,
            valueRange = 0.25f..3f,
        )
        Spacer(Modifier.height(24.dp))
        if (addingProfile) {
            AddProfileView(
                onBack = { addingProfile = false },
                onAdd = {
                    onAddProfile(it)
                    addingProfile = false
                },
            )
        } else {
            ProfilesView(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                connectionState = connectionState,
                onSelect = onSelect,
                onConnect = onConnect,
                onUpdate = onUpdateProfile,
                onDelete = onDeleteProfile,
                onAddRequested = { addingProfile = true },
            )
        }
    }
}

@Composable
private fun RemoteView(
    profile: RemoteProfile?,
    connectionState: String,
    client: RemoteClient,
) {
    var textFieldState by remember {
        mutableStateOf(TextFieldValue(text = " ", selection = TextRange(1)))
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ConnectionBar(profile, connectionState)
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(72.dp))
        Box(
            Modifier
                .size(width = 300.dp, height = 172.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val direction = dpadDirection(down.position, size)
                        if (direction == null) return@awaitEachGesture
                        down.consume()
                        val key = when (direction) {
                            WedgeDirection.Up -> "ArrowUp"
                            WedgeDirection.Down -> "ArrowDown"
                            WedgeDirection.Left -> "ArrowLeft"
                            WedgeDirection.Right -> "ArrowRight"
                        }
                        client.send("keyboard.key", JSONObject().apply {
                            put("key", key)
                            put("direction", "press")
                        })
                        try {
                            var pressed = true
                            while (pressed) {
                                val event = withTimeoutOrNull(75) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                }
                                if (event == null) {
                                    client.send("keyboard.key", JSONObject().apply {
                                        put("key", key)
                                        put("direction", "press")
                                    })
                                } else {
                                    pressed = event.changes.any { it.id == down.id && it.pressed }
                                }
                            }
                        } finally {
                            client.send("keyboard.key", JSONObject().apply {
                                put("key", key)
                                put("direction", "release")
                            })
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize()) {
            HoldKeyButton(
                client,
                "ArrowUp",
                "ArrowUp",
                painterResource(com.wiremote.app.R.drawable.keyboard_arrow_up_24),
                Modifier
                    .size(width = 150.dp, height = 96.dp)
                    .align(Alignment.TopCenter),
                WedgeShape(WedgeDirection.Up),
            )
        Spacer(Modifier.height(0.dp))
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldKeyButton(
                client,
                "ArrowLeft",
                "ArrowLeft",
                painterResource(com.wiremote.app.R.drawable.keyboard_arrow_left_24),
                Modifier
                    .size(width = 96.dp, height = 150.dp)
                    .offset(x = 55.dp),
                WedgeShape(WedgeDirection.Left),
            )
            Button(
                modifier = Modifier
                    .size(88.dp)
                    .zIndex(1f),
                shape = CircleShape,
                onClick = {
                    client.send(
                        "keyboard.key",
                        JSONObject().apply {
                            put("key", "Enter")
                            put("direction", "click")
                        },
                    )
                },
            ) {
                Text("OK", style = MaterialTheme.typography.titleMedium)
            }
            HoldKeyButton(
                client,
                "ArrowRight",
                "ArrowRight",
                painterResource(com.wiremote.app.R.drawable.keyboard_arrow_right_24),
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
            painterResource(com.wiremote.app.R.drawable.keyboard_arrow_down_24),
            Modifier
                .size(width = 150.dp, height = 96.dp)
                .align(Alignment.BottomCenter),
            WedgeShape(WedgeDirection.Down),
        )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            Button(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                onClick = { client.send("remote.back") },
            ) {
                Icon(
                    painter = painterResource(com.wiremote.app.R.drawable.undo_24),
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
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

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101418,
    widthDp = 360,
    heightDp = 640,
)
@Composable
private fun RemotePreview() {
    WireMoteTheme {
        RemoteView(null, "Disconnected", RemoteClient())
    }
}

private fun dpadDirection(
    point: androidx.compose.ui.geometry.Offset,
    size: IntSize,
): WedgeDirection? {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val dx = point.x - centerX
    val dy = point.y - centerY
    if (kotlin.math.abs(dx) < 48f && kotlin.math.abs(dy) < 42f) return null
    return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
        if (dx < 0f) WedgeDirection.Left else WedgeDirection.Right
    } else {
        if (dy < 0f) WedgeDirection.Up else WedgeDirection.Down
    }
}

@Composable
private fun HoldKeyButton(
    client: RemoteClient,
    label: String,
    key: String,
    icon: Painter,
    modifier: Modifier,
    shape: WedgeShape,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(
            Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                modifier = Modifier.offset(
                    x = when (key) {
                        "ArrowLeft" -> (-40).dp
                        "ArrowRight" -> 40.dp
                        else -> 0.dp
                    },
                    y = when (key) {
                        "ArrowUp" -> (-40).dp
                        "ArrowDown" -> 40.dp
                        else -> 0.dp
                    },
                ),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
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
    fun contains(point: androidx.compose.ui.geometry.Offset, size: Size): Boolean {
        val androidPath = createPath(size).asAndroidPath()
        val bounds = android.graphics.RectF()
        androidPath.computeBounds(bounds, true)
        val clip = Region(
            Rect(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt() + 1,
                bounds.bottom.toInt() + 1,
            ),
        )
        return Region().apply { setPath(androidPath, clip) }.contains(
            point.x.toInt(),
            point.y.toInt(),
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(createPath(size))

    fun path(size: Size): Path = createPath(size)

    private fun createPath(size: Size): Path {
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

        val outerRadiusX = size.width * 0.50f
        val outerRadiusY = size.height * 0.60f
        val innerRadiusX = size.width * 0.28f
        val innerRadiusY = size.height * 0.20f
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
            // Keep the outer arc unchanged while extending the inner-facing edges.
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
        return path
    }
}

@Composable
private fun ProfilesView(
    profiles: List<RemoteProfile>,
    selectedProfileId: String?,
    connectionState: String,
    onSelect: (String) -> Unit,
    onConnect: () -> Unit,
    onUpdate: (RemoteProfile) -> Unit,
    onDelete: (String) -> Unit,
    onAddRequested: () -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var expandedProfileId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<RemoteProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("39394") }
    var mac by remember { mutableStateOf("") }
    var broadcast by remember { mutableStateOf("255.255.255.255") }

    fun loadProfile(profile: RemoteProfile) {
        editingId = profile.id
        name = profile.name
        host = profile.host
        port = profile.port.toString()
        mac = profile.macAddress.orEmpty()
        broadcast = profile.broadcastAddress
    }

    fun clearForm() {
        editingId = null
        name = ""
        host = ""
        port = "39394"
        mac = ""
        broadcast = "255.255.255.255"
    }

    Column {
        Text("PC profiles", style = MaterialTheme.typography.headlineSmall)
        profiles.forEach { profile ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onSelect(profile.id)
                        expandedProfileId =
                            if (expandedProfileId == profile.id) null else profile.id
                    },
                ) {
                    Text(if (profile.id == selectedProfileId) "* ${profile.name}" else profile.name)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { loadProfile(profile) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}")
                }
                IconButton(onClick = { pendingDelete = profile }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.name}")
                }
            }
            if (expandedProfileId == profile.id) {
                Text(
                    "Host: ${profile.host}:${profile.port}\n" +
                        "MAC: ${profile.macAddress ?: "Not set"}\n" +
                        "Broadcast: ${profile.broadcastAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
        }
        Text(connectionState)
        Button(onClick = onConnect, enabled = selectedProfileId != null) { Text("Connect") }
        if (editingId != null) {
            Spacer(Modifier.height(12.dp))
            Text("Edit profile", style = MaterialTheme.typography.titleMedium)
            TextField(value = name, onValueChange = { name = it }, label = { Text("PC name") })
            TextField(value = host, onValueChange = { host = it }, label = { Text("IP address") })
            TextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
            TextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC address (optional)") })
            TextField(value = broadcast, onValueChange = { broadcast = it }, label = { Text("Broadcast address") })
            Button(
                onClick = {
                    val parsedPort = port.toIntOrNull()
                    if (name.isNotBlank() && host.isNotBlank() && parsedPort != null) {
                        onUpdate(
                            RemoteProfile(
                                id = editingId!!,
                                name = name,
                                host = host,
                                port = parsedPort,
                                macAddress = mac.ifBlank { null },
                                broadcastAddress = broadcast,
                            ),
                        )
                        clearForm()
                    }
                },
            ) { Text("Save changes") }
        }
        if (editingId != null) {
            TextButton(onClick = ::clearForm) { Text("Cancel") }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAddRequested) { Text("+ profile") }
    }
    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete profile?") },
            text = { Text("Remove ${profile.name} from this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profile.id)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddProfileView(
    onBack: () -> Unit,
    onAdd: (RemoteProfile) -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("39394") }
    var mac by remember { mutableStateOf("") }
    var broadcast by remember { mutableStateOf("255.255.255.255") }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Add profile", style = MaterialTheme.typography.titleMedium)
        }
        Text("Automatic", style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = {
                discovering = true
                scope.launch(Dispatchers.IO) {
                    val devices = Discovery.listen(context)
                    withContext(Dispatchers.Main) {
                        discoveredDevices = devices
                        discovering = false
                    }
                }
            },
            enabled = !discovering,
        ) {
            Text(if (discovering) "Scanning..." else "Detect PCs")
        }
        discoveredDevices.forEach { device ->
            TextButton(
                onClick = {
                    onAdd(
                        RemoteProfile(
                            id = UUID.randomUUID().toString(),
                            name = device.name,
                            host = device.host,
                            port = device.port,
                            macAddress = device.macAddress,
                        ),
                    )
                },
            ) {
                Text("${device.name} (${device.host}:${device.port})")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Manual", style = MaterialTheme.typography.titleSmall)
        TextField(value = name, onValueChange = { name = it }, label = { Text("PC name") })
        TextField(value = host, onValueChange = { host = it }, label = { Text("IP address") })
        TextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
        TextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC address (optional)") })
        TextField(value = broadcast, onValueChange = { broadcast = it }, label = { Text("Broadcast address") })
        Button(
            onClick = {
                val parsedPort = port.toIntOrNull()
                if (name.isNotBlank() && host.isNotBlank() && parsedPort != null) {
                    onAdd(
                        RemoteProfile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            host = host,
                            port = parsedPort,
                            macAddress = mac.ifBlank { null },
                            broadcastAddress = broadcast,
                        ),
                    )
                }
            },
        ) {
            Text("Add profile")
        }
    }
}
