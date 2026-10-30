package com.rustcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val native = NativeBridge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9CCC65),
                    onPrimary = Color(0xFF17210F),
                    secondary = Color(0xFFB0C99A),
                    background = Color(0xFF101410),
                    surface = Color(0xFF1B211B),
                    surfaceVariant = Color(0xFF293329),
                    onSurface = Color(0xFFE3E8DE),
                ),
            ) {
                RustCraftApp(native)
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private enum class Screen { MENU, WORLD, SETTINGS }

@Composable
private fun RustCraftApp(native: NativeBridge) {
    var screen by remember { mutableStateOf(Screen.MENU) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.MENU -> MainMenu(
                native = native,
                onPlay = { screen = Screen.WORLD },
                onSettings = { screen = Screen.SETTINGS },
            )
            Screen.WORLD -> WorldPrototype(native = native, onBack = { screen = Screen.MENU })
            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.MENU })
        }
    }
}

@Composable
private fun MainMenu(native: NativeBridge, onPlay: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(38.dp),
    ) {
        Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RUSTCRAFT", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color(0xFFAED581))
            Text("A voxel sandbox built with Rust", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill("ANDROID ONLY")
                StatusPill("SINGLEPLAYER")
                StatusPill("DEV 0.1.0")
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth(0.72f).height(54.dp)) {
                Text("PLAY PROTOTYPE", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth(0.72f).height(48.dp)) {
                Text("SETTINGS")
            }
        }

        Card(
            modifier = Modifier.weight(0.85f).fillMaxHeight(0.78f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PROJECT STATUS", fontWeight = FontWeight.Bold, color = Color(0xFFAED581), letterSpacing = 1.2.sp)
                    Text("Phase 0 · Android foundation", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Rust world core connected to an Android app through JNI.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NATIVE ENGINE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(native.engineVersion(), fontSize = 13.sp)
                    Text("Java Edition target: 1.21.11", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Box(
        modifier = Modifier.background(Color(0xFF293629), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 9.sp, color = Color(0xFFC5E1A5), fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun WorldPrototype(native: NativeBridge, onBack: () -> Unit) {
    val worldSeed = remember { Random.nextLong() }
    val width = 64
    val depth = 64
    val map = remember { native.generateSurfaceMap(width, depth, worldSeed) }
    var playerX by remember { mutableIntStateOf(width / 2) }
    var playerZ by remember { mutableIntStateOf(depth / 2) }

    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack) { Text("EXIT") }
                Column {
                    Text("WORLD PREVIEW", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Seed: $worldSeed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
                Spacer(Modifier.weight(1f))
                StatusPill("TOP-DOWN PROTOTYPE")
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0A0E0A),
            ) {
                Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                    val cellW = size.width / width
                    val cellH = size.height / depth
                    val colors = mapOf(
                        1 to Color(0xFF70A94D),
                        2 to Color(0xFF8B6544),
                        3 to Color(0xFF777D82),
                        4 to Color(0xFF397FC1),
                        5 to Color(0xFFD5C17A),
                        6 to Color(0xFF34383C),
                    )
                    for (z in 0 until depth) {
                        for (x in 0 until width) {
                            val block = map[z * width + x]
                            drawRect(
                                color = colors[block] ?: Color.DarkGray,
                                topLeft = Offset(x * cellW, z * cellH),
                                size = androidx.compose.ui.geometry.Size(cellW + 0.35f, cellH + 0.35f),
                            )
                        }
                    }
                    val center = Offset((playerX + 0.5f) * cellW, (playerZ + 0.5f) * cellH)
                    drawCircle(Color(0xFFFFF176), radius = minOf(cellW, cellH) * 0.8f, center = center)
                    drawCircle(Color(0xFF263238), radius = minOf(cellW, cellH) * 0.8f, center = center, style = Stroke(width = 2f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Prototype only: terrain preview and movement. 3D rendering and Java gameplay are not implemented yet.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
        }

        Card(
            modifier = Modifier.width(210.dp).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYER", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("X $playerX  ·  Z $playerZ", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(14.dp))
                DirectionPad(
                    onUp = { playerZ = (playerZ - 1).coerceAtLeast(0) },
                    onDown = { playerZ = (playerZ + 1).coerceAtMost(depth - 1) },
                    onLeft = { playerX = (playerX - 1).coerceAtLeast(0) },
                    onRight = { playerX = (playerX + 1).coerceAtMost(width - 1) },
                )
                Spacer(Modifier.weight(1f))
                Text("HOTBAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(5) { index ->
                        Box(
                            Modifier.size(30.dp)
                                .background(if (index == 0) Color(0xFF536B43) else Color(0xFF303830), RoundedCornerShape(5.dp))
                                .border(if (index == 0) 2.dp else 1.dp, if (index == 0) Color(0xFFAED581) else Color(0xFF536053), RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((index + 1).toString(), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PadButton("▲", onUp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PadButton("◀", onLeft)
            PadButton("▼", onDown)
            PadButton("▶", onRight)
        }
    }
}

@Composable
private fun PadButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF354334)),
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SETTINGS", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFAED581))
        Text("Initial build settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Render distance, graphics backend, touch layout, resource packs, and world options will be added in later phases.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onBack) { Text("BACK TO MENU") }
    }
}
