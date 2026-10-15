package com.dolanan.usbscreenmirror

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dolanan.usbscreenmirror.model.*
import com.dolanan.usbscreenmirror.usb.UsbAdbDevice

private val Ink = Color(0xFF07131B)
private val Panel = Color(0xFF10232E)
private val Panel2 = Color(0xFF17313D)
private val Cyan = Color(0xFF45E0D0)
private val White = Color(0xFFEAF5F7)
private val Muted = Color(0xFF9AB0BA)

class MainActivity : ComponentActivity() {
    companion object { const val ACTION_USB_PERMISSION = "com.dolanan.usbscreenmirror.USB_PERMISSION" }
    private lateinit var usbManager: UsbManager
    private var usbReceiverRegistered = false
    private var usbResult: ((UsbDevice?, Boolean) -> Unit)? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted.value = granted
        statusText.value = if (granted) "Izin kamera diberikan" else "Izin kamera ditolak; mirroring USB tidak terpengaruh"
    }
    private val cameraGranted = mutableStateOf(false)
    private val statusText = mutableStateOf("Siap memindai perangkat USB")
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            @Suppress("DEPRECATION")
            val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            usbResult?.invoke(device, granted)
            statusText.value = when {
                granted && device != null -> "Izin USB diberikan. Periksa status ADB di CLIENT."
                device != null -> "Izin USB ditolak oleh sistem atau pengguna. Coba cabut-pasang kabel dan minta izin lagi."
                else -> "Respons izin USB tidak menyertakan perangkat."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(this, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        usbReceiverRegistered = true
        val prefs = getSharedPreferences("mirror_v1", MODE_PRIVATE)
        val initialVideo = VideoSettings(
            Resolution.entries.firstOrNull { it.name == prefs.getString("resolution", "P480") } ?: Resolution.P480,
            prefs.getInt("fps", 24), prefs.getFloat("bitrate", 1f), Preset.CUSTOM
        )
        val initialMirror = LayerSettings(prefs.getFloat("mw", 1f), prefs.getFloat("mh", 1f), prefs.getFloat("mx", 0f), prefs.getFloat("my", 0f), prefs.getBoolean("ma", true), ScaleMode.entries.firstOrNull { it.name == prefs.getString("mm", "FIT") } ?: ScaleMode.FIT, prefs.getBoolean("mv", true), prefs.getBoolean("ml", false))
        val initialCamera = LayerSettings(prefs.getFloat("cw", .28f), prefs.getFloat("ch", .28f), prefs.getFloat("cx", .58f), prefs.getFloat("cy", .58f), prefs.getBoolean("ca", true), ScaleMode.entries.firstOrNull { it.name == prefs.getString("cm", "FILL") } ?: ScaleMode.FILL, prefs.getBoolean("cv", true), prefs.getBoolean("cl", false))
        setContent {
            var video by remember { mutableStateOf(initialVideo) }
            var mirror by remember { mutableStateOf(initialMirror) }
            var camera by remember { mutableStateOf(initialCamera) }
            var devices by remember { mutableStateOf(usbManager.deviceList.values.toList()) }
            var selectedTab by remember { mutableIntStateOf(0) }
            var appliedVideo by remember { mutableStateOf(initialVideo) }
            var appliedMirror by remember { mutableStateOf(initialMirror) }
            var appliedCamera by remember { mutableStateOf(initialCamera) }
            var applyMessage by remember { mutableStateOf("Pengaturan tersimpan") }
            val status by statusText
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Ink, surface = Panel, onPrimary = Ink, onBackground = White, onSurface = White)) {
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Cyan), contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, null, tint = Ink, modifier = Modifier.size(27.dp)) }
                            Column(Modifier.weight(1f)) { Text("USB Screen MirrorV1", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White); Text("MIRRORING LANGSUNG VIA USB / OTG", fontSize = 10.sp, color = Cyan, letterSpacing = 1.sp) }
                            IconButton(onClick = { devices = usbManager.deviceList.values.toList(); statusText.value = "Pemindaian USB selesai: ${devices.size} perangkat" }) { Icon(Icons.Default.Refresh, "Pindai ulang", tint = Cyan) }
                        }
                        StatusCard(status = status, deviceCount = devices.size, permissionGranted = devices.any { usbManager.hasPermission(it) })
                        // Explicitly do not report streaming without received and rendered frames.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("KANVAS", "KUALITAS", "LAYER").forEachIndexed { index, title ->
                                FilterChip(selected = selectedTab == index, onClick = { selectedTab = index }, label = { Text(title) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Ink))
                            }
                        }
                        when (selectedTab) {
                            0 -> {
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("KANVAS MIRROR", Modifier.weight(1f), color = White, fontWeight = FontWeight.SemiBold); Text("LIVE • BELUM AKTIF", color = Color(0xFFFFC36A), fontSize = 10.sp) }
                                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF03090D)), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Settings, null, tint = Muted, modifier = Modifier.size(42.dp))
                                            Text("Belum ada frame dari CLIENT", color = White, fontWeight = FontWeight.SemiBold)
                                            Text("Status USB saja bukan bukti mirroring.\nFrame akan ditampilkan setelah ADB dan decoder aktif.", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            devices = usbManager.deviceList.values.toList()
                                            val d = devices.firstOrNull { UsbAdbDevice.find(it) != null }
                                            if (d == null) statusText.value = "Interface ADB tidak ditemukan. Aktifkan USB debugging di CLIENT, gunakan kabel data/OTG yang sesuai."
                                            else if (usbManager.hasPermission(d)) statusText.value = "Izin USB sudah tersedia; transport ADB dan capture belum dimulai."
                                            else requestUsbPermission(d) { _, granted -> statusText.value = if (granted) "Izin USB diberikan; ADB belum terautentikasi." else "Izin USB ditolak. Periksa dialog sistem dan peran OTG." }
                                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Text("MINTA AKSES USB") }
                                        OutlinedButton(onClick = { devices = usbManager.deviceList.values.toList(); statusText.value = "Ditemukan ${devices.size} perangkat USB" }, modifier = Modifier.weight(1f)) { Text("PINDAI USB") }
                                    }
                                }
                                Text("PERANGKAT USB", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (devices.isEmpty()) InfoCard("Belum ada perangkat USB terdeteksi", "Sambungkan CLIENT melalui kabel data dan adapter OTG. Pastikan HOST berperan sebagai USB host.")
                                devices.forEach { device ->
                                    val adb = UsbAdbDevice.find(device) != null
                                    InfoCard("${device.manufacturerName ?: "USB"} ${device.productName ?: device.deviceName}", "VID ${device.vendorId} • PID ${device.productId} • ${if (adb) "Interface ADB terdeteksi" else "Interface bukan ADB yang dikenali"} • ${if (usbManager.hasPermission(device)) "Izin tersedia" else "Izin belum diberikan"}")
                                }
                            }
                            1 -> VideoSettingsPanel(video, onChange = { video = it })
                            else -> LayerPanel(mirror, camera, onMirror = { mirror = it }, onCamera = { camera = it }, cameraGranted = cameraGranted.value, onRequestCamera = { cameraPermission.launch(android.Manifest.permission.CAMERA) })
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("TERAPKAN PERUBAHAN", color = Cyan, fontWeight = FontWeight.Bold)
                            Text("Satu APPLY menyimpan konfigurasi kualitas dan layout. Streaming belum dinyatakan aktif sampai frame nyata diterima, didekode, dan dirender.", color = Muted, fontSize = 12.sp)
                            Button(onClick = {
                                video = video.validate(); mirror = mirror.normalized(); camera = camera.normalized()
                                appliedVideo = video; appliedMirror = mirror; appliedCamera = camera
                                prefs.edit().putString("resolution", video.resolution.name).putInt("fps", video.fps).putFloat("bitrate", video.bitrateMbps)
                                    .putFloat("mw", mirror.width).putFloat("mh", mirror.height).putFloat("mx", mirror.x).putFloat("my", mirror.y).putBoolean("ma", mirror.lockAspect).putString("mm", mirror.scaleMode.name).putBoolean("mv", mirror.visible).putBoolean("ml", mirror.locked)
                                    .putFloat("cw", camera.width).putFloat("ch", camera.height).putFloat("cx", camera.x).putFloat("cy", camera.y).putBoolean("ca", camera.lockAspect).putString("cm", camera.scaleMode.name).putBoolean("cv", camera.visible).putBoolean("cl", camera.locked).apply()
                                applyMessage = "Tersimpan • ${video.resolution.label}, ${video.fps} FPS, ${video.bitrateBitsPerSecond()/1000} kbps"
                                statusText.value = "Konfigurasi tersimpan. Belum diterapkan ke capture karena sesi streaming belum tersedia."
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Text("APPLY", fontWeight = FontWeight.Bold) }
                            Text(applyMessage, color = Muted, fontSize = 11.sp)
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp)) {
                            Text("DIAGNOSTIK", color = Cyan, fontWeight = FontWeight.Bold)
                            Text("USB: ${if (devices.isNotEmpty()) "terdeteksi" else "belum terdeteksi"}   •   ADB: belum terautentikasi", color = Muted, fontSize = 12.sp)
                            Text("Capture: berhenti   •   Decoder: tidak aktif   •   Frame diterima: 0", color = Muted, fontSize = 12.sp)
                            Text("Resolusi target ${appliedVideo.resolution.label} • ${appliedVideo.fps} FPS • ${appliedVideo.bitrateBitsPerSecond()} bit/s", color = Muted, fontSize = 12.sp)
                        }
                        Text("USB Screen MirrorV1 • Alpha • status tidak dibuat-buat", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }

    private fun requestUsbPermission(device: UsbDevice, callback: (UsbDevice?, Boolean) -> Unit) {
        usbResult = callback
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(this, device.deviceId, intent, flags)
        statusText.value = "Meminta izin USB dari sistem Android…"
        usbManager.requestPermission(device, pendingIntent)
    }

    override fun onDestroy() { if (usbReceiverRegistered) runCatching { unregisterReceiver(permissionReceiver) }; super.onDestroy() }
}

@Composable private fun StatusCard(status: String, deviceCount: Int, permissionGranted: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (permissionGranted) Cyan else Color(0xFFFFC36A)))
        Column(Modifier.weight(1f)) { Text("STATUS SISTEM", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(status, color = White, fontSize = 13.sp) }
        Column(horizontalAlignment = Alignment.End) { Text("USB $deviceCount", color = Cyan, fontWeight = FontWeight.Bold); Text(if (permissionGranted) "IZIN ADA" else "MENUNGGU", color = Muted, fontSize = 9.sp) }
    }
}
@Composable private fun InfoCard(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, color = White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 11.sp) } }

@Composable private fun VideoSettingsPanel(settings: VideoSettings, onChange: (VideoSettings) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("KUALITAS VIDEO", color = Cyan, fontWeight = FontWeight.Bold)
        Text("Preset", color = White)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Preset.entries.forEach { p -> FilterChip(settings.preset == p, onClick = { onChange(VideoSettings.forPreset(p)) }, label = { Text(p.label, fontSize = 10.sp) }) } }
        Text("Resolusi target", color = White)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Resolution.entries.forEach { r -> FilterChip(settings.resolution == r, onClick = { onChange(settings.copy(resolution = r, preset = Preset.CUSTOM)) }, label = { Text(r.label) }) } }
        Text("FPS: ${settings.fps}", color = White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15,24,30).forEach { fps -> FilterChip(settings.fps == fps, onClick = { onChange(settings.copy(fps = fps, preset = Preset.CUSTOM)) }, label = { Text("$fps FPS") }) } }
        Text("Bitrate: ${"%.1f".format(settings.bitrateMbps)} Mbps", color = White)
        Slider(value = settings.bitrateMbps, onValueChange = { onChange(settings.copy(bitrateMbps = it, preset = Preset.CUSTOM)) }, valueRange = .5f..8f, steps = 14)
        Text("Target bitrate ${settings.bitrateBitsPerSecond()} bit/s. Dukungan resolusi/FPS aktual harus dikonfirmasi oleh capture CLIENT.", color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun LayerPanel(mirror: LayerSettings, camera: LayerSettings, onMirror: (LayerSettings) -> Unit, onCamera: (LayerSettings) -> Unit, cameraGranted: Boolean, onRequestCamera: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val layer = if (selected == 0) mirror else camera
    val update: (LayerSettings) -> Unit = if (selected == 0) onMirror else onCamera
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LAYOUT LAYER", color = Cyan, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected == 0, { selected = 0 }, label = { Text("Layar CLIENT") }); FilterChip(selected == 1, { selected = 1 }, label = { Text("Kamera wajah") }) }
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF03090D)), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0D202A)), contentAlignment = Alignment.Center) { Text("Kanvas pratinjau layout • bukan video CLIENT", color = Muted, fontSize = 11.sp) }
            if (layer.visible) Box(Modifier.fillMaxWidth(layer.width.coerceAtMost(1f)).fillMaxHeight(layer.height.coerceAtMost(1f)).offset(x = (layer.x * 40).dp, y = (layer.y * 30).dp).clip(RoundedCornerShape(8.dp)).background(Cyan.copy(alpha = .18f)).pointerInput(layer.locked) { detectDragGestures { change, drag -> change.consume(); if (!layer.locked) update(layer.copy(x = layer.x + drag.x / 220f, y = layer.y + drag.y / 220f).normalized()) } }, contentAlignment = Alignment.Center) { Text(if (selected == 0) "LAYER MIRROR" else "KAMERA", color = Cyan, fontSize = 10.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Tampilkan layer", Modifier.weight(1f), color = White); Switch(layer.visible, onCheckedChange = { update(layer.copy(visible = it)) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Kunci rasio aspek", Modifier.weight(1f), color = White); Switch(layer.lockAspect, onCheckedChange = { update(layer.copy(lockAspect = it)) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Kunci posisi", Modifier.weight(1f), color = White); Switch(layer.locked, onCheckedChange = { update(layer.copy(locked = it)) }) }
        Text("Lebar: ${(layer.width * 100).toInt()}%", color = White); Slider(layer.width, { value -> val ratio = if (layer.lockAspect) value / layer.width.coerceAtLeast(.01f) else 1f; update(layer.copy(width = value, height = if (layer.lockAspect) layer.height * ratio else layer.height).normalized()) }, valueRange = .08f..1.5f)
        Text("Tinggi: ${(layer.height * 100).toInt()}%", color = White); Slider(layer.height, { value -> update(layer.copy(height = value, width = if (layer.lockAspect) layer.width * (value / layer.height.coerceAtLeast(.01f)) else layer.width).normalized()) }, valueRange = .08f..1.5f)
        Text("Posisi X: ${"%.2f".format(layer.x)}", color = White); Slider(layer.x, { update(layer.copy(x = it).normalized()) }, valueRange = -.75f.. .95f)
        Text("Posisi Y: ${"%.2f".format(layer.y)}", color = White); Slider(layer.y, { update(layer.copy(y = it).normalized()) }, valueRange = -.75f.. .95f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(layer.scaleMode == ScaleMode.FIT, { update(layer.copy(scaleMode = ScaleMode.FIT)) }, label = { Text("FIT") }); FilterChip(layer.scaleMode == ScaleMode.FILL, { update(layer.copy(scaleMode = ScaleMode.FILL)) }, label = { Text("FILL") }); OutlinedButton(onClick = { update(if (selected == 0) LayerSettings.DEFAULT_MIRROR else LayerSettings.DEFAULT_CAMERA) }) { Text("Reset layer") } }
        if (selected == 1) Button(onClick = onRequestCamera, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(if (cameraGranted) "Izin kamera diberikan" else "Minta izin kamera") }
        OutlinedButton(onClick = { onMirror(LayerSettings.DEFAULT_MIRROR); onCamera(LayerSettings.DEFAULT_CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("Reset seluruh layout") }
    }
}
