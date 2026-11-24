package com.example.vagonboshqaruvi

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class GlassThemePalette(
    val isNight: Boolean,
    val bgBrush: Brush,
    val glassCardBg: Color,
    val glassSurfaceBg: Color,
    val glassBorder: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val onAccentText: Color
)

fun glassPalette(isNight: Boolean): GlassThemePalette {
    return if (isNight) {
        GlassThemePalette(
            isNight = true,
            bgBrush = Brush.radialGradient(
                colors = listOf(Color(0xFF1D2944), Color(0xFF0A0F1D), Color(0xFF05070E)),
                center = Offset(480f, 260f),
                radius = 1400f
            ),
            glassCardBg = Color(0xCC131C31),
            glassSurfaceBg = Color(0x991C2842),
            glassBorder = Color(0x66FFD700),
            accentPrimary = Color(0xFFFFD700),
            accentSecondary = Color(0xFFFFB703),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFFF3E5AB),
            onAccentText = Color(0xFF070B14)
        )
    } else {
        GlassThemePalette(
            isNight = false,
            bgBrush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFF3E0), Color(0xFFE0F2FE), Color(0xFFFCE7F3))
            ),
            glassCardBg = Color(0xB8FFFFFF),
            glassSurfaceBg = Color(0x8CF8FAFC),
            glassBorder = Color(0x99FFFFFF),
            accentPrimary = Color(0xFFB45309),
            accentSecondary = Color(0xFF0284C7),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF334155),
            onAccentText = Color.White
        )
    }
}

val LocalGlassPalette = compositionLocalOf { glassPalette(true) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = LocalStorage(this)
        val cloudSync = CloudSyncManager(storage)
        setContent {
            VagonBoshqaruviApp(storage, cloudSync)
        }
    }
}

@Composable
fun GlassBackdrop(
    isNight: Boolean,
    content: @Composable () -> Unit
) {
    val palette = glassPalette(isNight)
    val infiniteTransition = rememberInfiniteTransition(label = "glass_ambience")
    val orbShift by infiniteTransition.animateFloat(
        initialValue = -35f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_shift"
    )

    CompositionLocalProvider(LocalGlassPalette provides palette) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bgBrush)
        ) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .offset(x = (20 + orbShift).dp, y = 50.dp)
                    .background(
                        if (isNight) Color(0x26FFD700) else Color(0x40FBBF24),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10 - orbShift).dp, y = (-60).dp)
                    .background(
                        if (isNight) Color(0x2638BDF8) else Color(0x4538BDF8),
                        CircleShape
                    )
            )
            content()
        }
    }
}

@Composable
fun VagonBoshqaruviApp(storage: LocalStorage, cloudSync: CloudSyncManager) {
    val users = remember { mutableStateListOf<User>().apply { addAll(storage.loadUsers()) } }
    val records = remember { mutableStateListOf<WagonRecord>().apply { addAll(storage.loadRecords()) } }
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(storage.loadMessages()) } }

    var isNightGlass by remember { mutableStateOf(storage.loadThemeMode()) }
    var isCloudOnline by remember { mutableStateOf(false) }
    var firebaseUrl by remember { mutableStateOf(storage.getFirebaseUrl()) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var showWhiteExcel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun syncFromCloud() {
        val remoteUsers = cloudSync.fetchUsers()
        if (remoteUsers != null) {
            isCloudOnline = true
            val merged = (storage.loadUsers() + remoteUsers)
                .associateBy { it.username }
                .values
                .toList()
            users.clear()
            users.addAll(merged)
            storage.saveUsers(users)
        } else {
            isCloudOnline = false
        }

        val remoteRecords = cloudSync.fetchRecords()
        if (remoteRecords != null) {
            isCloudOnline = true
            records.clear()
            records.addAll(remoteRecords)
            storage.saveRecords(records)
        }

        val remoteMessages = cloudSync.fetchMessages()
        if (remoteMessages != null) {
            isCloudOnline = true
            if (remoteMessages.isNotEmpty()) {
                messages.clear()
                messages.addAll(remoteMessages)
                storage.saveMessages(messages)
            }
        }
    }

    LaunchedEffect(firebaseUrl) {
        while (true) {
            syncFromCloud()
            delay(4000)
        }
    }

    val toggleTheme: () -> Unit = {
        isNightGlass = !isNightGlass
        storage.saveThemeMode(isNightGlass)
    }

    GlassBackdrop(isNight = isNightGlass) {
        Crossfade(targetState = Triple(currentUser, selectedRole, showWhiteExcel), label = "app_nav") { (user, role, isExcel) ->
            when {
                user == null -> {
                    AuthScreen(
                        users = users,
                        isNightGlass = isNightGlass,
                        isCloudOnline = isCloudOnline,
                        onToggleTheme = toggleTheme,
                        onSyncNow = { scope.launch { syncFromCloud() } },
                        onAuthSuccess = { loggedUser ->
                            currentUser = loggedUser
                            if (loggedUser.role == "Admin") selectedRole = "Admin"
                        },
                        onRegisterUser = { newUser ->
                            users.add(newUser)
                            storage.saveUsers(users)
                            scope.launch {
                                cloudSync.pushUser(newUser)
                                syncFromCloud()
                            }
                        }
                    )
                }
                role == null -> {
                    RoleSelectionScreen(
                        isNightGlass = isNightGlass,
                        onToggleTheme = toggleTheme,
                        onRoleSelected = { chosenRole ->
                            selectedRole = chosenRole
                            val idx = users.indexOfFirst { it.id == user.id }
                            if (idx != -1) {
                                val updated = users[idx].copy(role = chosenRole)
                                users[idx] = updated
                                currentUser = updated
                                storage.saveUsers(users)
                                scope.launch {
                                    cloudSync.pushUser(updated)
                                    syncFromCloud()
                                }
                            }
                        }
                    )
                }
                isExcel -> {
                    WhiteExcelTableScreen(records = records, onBack = { showWhiteExcel = false })
                }
                role == "Admin" -> {
                    AdminPanelScreen(
                        users = users,
                        isNightGlass = isNightGlass,
                        isCloudOnline = isCloudOnline,
                        onToggleTheme = toggleTheme,
                        onSyncNow = { scope.launch { syncFromCloud() } },
                        onLogout = { currentUser = null; selectedRole = null }
                    )
                }
                else -> {
                    MainWorkspaceScreen(
                        user = user,
                        role = role,
                        users = users,
                        records = records,
                        messages = messages,
                        isNightGlass = isNightGlass,
                        isCloudOnline = isCloudOnline,
                        firebaseUrl = firebaseUrl,
                        onUpdateFirebaseUrl = { newUrl ->
                            storage.saveFirebaseUrl(newUrl)
                            firebaseUrl = storage.getFirebaseUrl()
                            scope.launch { syncFromCloud() }
                        },
                        onSyncNow = { scope.launch { syncFromCloud() } },
                        onToggleTheme = toggleTheme,
                        onRecordUpsert = { rec ->
                            storage.saveRecords(records)
                            scope.launch {
                                cloudSync.pushRecord(rec)
                                syncFromCloud()
                            }
                        },
                        onRecordDelete = { rec ->
                            records.remove(rec)
                            storage.saveRecords(records)
                            scope.launch {
                                cloudSync.deleteRecord(rec.id)
                                syncFromCloud()
                            }
                        },
                        onMessageUpsert = { msg ->
                            storage.saveMessages(messages)
                            scope.launch {
                                cloudSync.pushMessage(msg)
                                syncFromCloud()
                            }
                        },
                        onMessageDelete = { msgId ->
                            messages.removeAll { it.id == msgId }
                            storage.saveMessages(messages)
                            scope.launch {
                                cloudSync.deleteMessage(msgId)
                                syncFromCloud()
                            }
                        },
                        onOpenWhiteExcel = { showWhiteExcel = true },
                        onLogout = { currentUser = null; selectedRole = null }
                    )
                }
            }
        }
    }
}

@Composable
fun CloudStatusBadge(
    isOnline: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlassPalette.current
    Surface(
        onClick = onSyncClick,
        color = p.glassSurfaceBg,
        border = BorderStroke(1.dp, if (isOnline) Color(0xFF22C55E) else Color(0xFFF59E0B)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isOnline) Color(0xFF22C55E) else Color(0xFFF59E0B), CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isOnline) "Bulut: Ulangan" else "Sinxron...",
                color = p.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GlassThemeToggleChip(
    isNightGlass: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalGlassPalette.current
    Surface(
        onClick = onToggleTheme,
        color = p.glassSurfaceBg,
        border = BorderStroke(1.dp, p.glassBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isNightGlass) Icons.Default.WbSunny else Icons.Default.NightsStay,
                contentDescription = "Fon rejimi",
                tint = p.accentPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isNightGlass) "Tongi" else "Tungi",
                color = p.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AuthScreen(
    users: List<User>,
    isNightGlass: Boolean,
    isCloudOnline: Boolean,
    onToggleTheme: () -> Unit,
    onSyncNow: () -> Unit,
    onAuthSuccess: (User) -> Unit,
    onRegisterUser: (User) -> Unit
) {
    val p = LocalGlassPalette.current
    var isLogin by remember { mutableStateOf(true) }

    var phoneNumber by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "luxury_train")
    val trainOffset by infiniteTransition.animateFloat(
        initialValue = -85f,
        targetValue = 85f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "train_move"
    )

    val accentGradient = Brush.horizontalGradient(
        listOf(p.accentPrimary, p.accentSecondary, p.accentPrimary)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CloudStatusBadge(isOnline = isCloudOnline, onSyncClick = onSyncNow)
            GlassThemeToggleChip(isNightGlass = isNightGlass, onToggleTheme = onToggleTheme)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp)
                .shadow(24.dp, RoundedCornerShape(30.dp))
                .border(1.5.dp, p.glassBorder, RoundedCornerShape(30.dp)),
            colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
            shape = RoundedCornerShape(30.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(3.dp)
                                .background(accentGradient, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(0.8f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(10) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(4.dp)
                                        .background(p.accentPrimary.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.offset(x = trainOffset.dp, y = (-8).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsTransit,
                            contentDescription = "Vagon",
                            tint = p.accentSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.DirectionsRailway,
                            contentDescription = "Lokomotiv",
                            tint = p.accentPrimary,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "VAGON BOSHQARUVI",
                    color = p.accentPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "HAMMA TELEFONDA YAGONA BAZA",
                    color = p.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.glassSurfaceBg, RoundedCornerShape(16.dp))
                        .border(1.dp, p.glassBorder, RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isLogin) p.accentPrimary else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { isLogin = true; errorMsg = "" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "KIRISH",
                            color = if (isLogin) p.onAccentText else p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (!isLogin) p.accentPrimary else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { isLogin = false; errorMsg = "" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RO'YXATDAN O'TISH",
                            color = if (!isLogin) p.onAccentText else p.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                AnimatedVisibility(visible = !isLogin) {
                    Column {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it; errorMsg = "" },
                            label = { Text("Telefon nomer (+998...)", color = p.textSecondary) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = p.accentPrimary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = glassTextFieldColors()
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMsg = "" },
                    label = { Text("Login", color = p.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = p.accentPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = glassTextFieldColors()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = "" },
                    label = { Text("Parol", color = p.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = p.accentPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Parolni ko'rish",
                                tint = p.accentPrimary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = glassTextFieldColors()
                )

                AnimatedVisibility(visible = !isLogin) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorMsg = "" },
                            label = { Text("Parolni tasdiqlang", color = p.textSecondary) },
                            leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = p.accentPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Parolni ko'rish",
                                        tint = p.accentPrimary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = glassTextFieldColors()
                        )
                    }
                }

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (isLogin) {
                            if (username.isBlank() || password.isBlank()) {
                                errorMsg = "Login va parolni kiriting!"
                                return@Button
                            }
                            if (username.trim() == "admin" && password.trim() == "admin123") {
                                onAuthSuccess(User("admin", "", "Admin", "admin123", "Admin"))
                            } else {
                                val found = users.find { it.username == username.trim() && it.password == password.trim() }
                                if (found != null) onAuthSuccess(found)
                                else errorMsg = "Login yoki parol noto'g'ri!"
                            }
                        } else {
                            if (phoneNumber.isBlank() || username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                                errorMsg = "Barcha maydonlarni to'ldiring (Nomer, Login, Parol)!"
                                return@Button
                            }
                            if (password != confirmPassword) {
                                errorMsg = "Parollar bir-biriga mos kelmadi!"
                                return@Button
                            }
                            if (users.any { it.username == username.trim() }) {
                                errorMsg = "Bu login band, boshqa login tanlang!"
                                return@Button
                            }
                            val newUser = User(
                                id = System.currentTimeMillis().toString(),
                                phoneNumber = phoneNumber.trim(),
                                username = username.trim(),
                                password = password.trim(),
                                role = "Tanlanmagan"
                            )
                            onRegisterUser(newUser)
                            onAuthSuccess(newUser)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(10.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(accentGradient, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isLogin) Icons.Default.Login else Icons.Default.HowToReg,
                                contentDescription = null,
                                tint = p.onAccentText
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isLogin) "TIZIMGA KIRISH" else "RO'YXATDAN O'TISH",
                                color = p.onAccentText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(
    isNightGlass: Boolean,
    onToggleTheme: () -> Unit,
    onRoleSelected: (String) -> Unit
) {
    val p = LocalGlassPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "role_anim")
    val trainBounce by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            GlassThemeToggleChip(isNightGlass = isNightGlass, onToggleTheme = onToggleTheme)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Train,
                contentDescription = null,
                tint = p.accentPrimary,
                modifier = Modifier
                    .size(86.dp)
                    .offset(x = trainBounce.dp)
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, p.accentPrimary, Color.Transparent)))
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "LAVOZIMINGIZNI TANLANG",
                color = p.accentPrimary,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Davom etish uchun o'z vazifangizni belgilang",
                color = p.textSecondary,
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .clickable { onRoleSelected("Xodim") }
                    .border(1.5.dp, p.glassBorder, RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Engineering, contentDescription = null, tint = p.accentPrimary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("XODIM", color = p.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Vagon ma'lumotlari", color = p.textSecondary, fontSize = 11.sp)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .clickable { onRoleSelected("Rahbar") }
                    .border(1.5.dp, p.glassBorder, RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = p.accentPrimary),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = p.onAccentText, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("RAHBAR", color = p.onAccentText, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Jadval va nazorat", color = p.onAccentText.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun MainWorkspaceScreen(
    user: User,
    role: String,
    users: List<User>,
    records: MutableList<WagonRecord>,
    messages: MutableList<ChatMessage>,
    isNightGlass: Boolean,
    isCloudOnline: Boolean,
    firebaseUrl: String,
    onUpdateFirebaseUrl: (String) -> Unit,
    onSyncNow: () -> Unit,
    onToggleTheme: () -> Unit,
    onRecordUpsert: (WagonRecord) -> Unit,
    onRecordDelete: (WagonRecord) -> Unit,
    onMessageUpsert: (ChatMessage) -> Unit,
    onMessageDelete: (String) -> Unit,
    onOpenWhiteExcel: () -> Unit,
    onLogout: () -> Unit
) {
    val p = LocalGlassPalette.current
    var selectedTab by remember { mutableIntStateOf(if (role == "Xodim") 0 else 1) }
    var editingRecord by remember { mutableStateOf<WagonRecord?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.glassCardBg)
                    .border(0.8.dp, p.glassBorder)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DirectionsRailway, contentDescription = null, tint = p.accentPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Vagon Boshqaruvi",
                        color = p.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CloudStatusBadge(isOnline = isCloudOnline, onSyncClick = onSyncNow)
                    GlassThemeToggleChip(isNightGlass = isNightGlass, onToggleTheme = onToggleTheme)
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = p.glassCardBg,
                modifier = Modifier.border(0.8.dp, p.glassBorder)
            ) {
                if (role == "Xodim") {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; editingRecord = null },
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                        label = { Text("Kiritish", fontSize = 11.sp) },
                        colors = navItemColors()
                    )
                }
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.TableView, contentDescription = null) },
                    label = { Text("Jadval", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                    label = { Text("Tahrir", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                    label = { Text("Chat", fontSize = 11.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profil", fontSize = 11.sp) },
                    colors = navItemColors()
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> EmployeeFormScreen(
                    user = user,
                    existingRecord = editingRecord,
                    onSaveRecords = { newRecs ->
                        if (editingRecord != null) {
                            val idx = records.indexOfFirst { it.id == editingRecord!!.id }
                            if (idx != -1 && newRecs.isNotEmpty()) {
                                val updated = newRecs.first().copy(id = editingRecord!!.id, trackNumber = editingRecord!!.trackNumber)
                                records[idx] = updated
                                onRecordUpsert(updated)
                            }
                            editingRecord = null
                        } else {
                            records.addAll(newRecs)
                            newRecs.forEach { onRecordUpsert(it) }
                        }
                        selectedTab = 1
                    }
                )
                1 -> ScheduleTableScreen(records = records, onOpenWhiteExcel = onOpenWhiteExcel)
                2 -> EditListScreen(
                    records = records,
                    onSelectToEdit = { rec ->
                        editingRecord = rec
                        selectedTab = 0
                    },
                    onDeleteRecord = { rec ->
                        onRecordDelete(rec)
                    }
                )
                3 -> ChatScreen(
                    user = user,
                    role = role,
                    messages = messages,
                    onMessageUpsert = onMessageUpsert,
                    onMessageDelete = onMessageDelete
                )
                4 -> ProfileScreen(
                    user = user,
                    role = role,
                    users = users,
                    records = records,
                    messages = messages,
                    isNightGlass = isNightGlass,
                    isCloudOnline = isCloudOnline,
                    firebaseUrl = firebaseUrl,
                    onUpdateFirebaseUrl = onUpdateFirebaseUrl,
                    onSyncNow = onSyncNow,
                    onToggleTheme = onToggleTheme,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
fun EmployeeFormScreen(
    user: User,
    existingRecord: WagonRecord?,
    onSaveRecords: (List<WagonRecord>) -> Unit
) {
    val p = LocalGlassPalette.current
    var wagonNum by remember(existingRecord) { mutableStateOf(existingRecord?.wagonNumber ?: "") }
    var arrDate by remember(existingRecord) { mutableStateOf(existingRecord?.arrivalDate ?: "") }
    var arrTime by remember(existingRecord) { mutableStateOf(existingRecord?.arrivalTime ?: "") }
    var trainIn by remember(existingRecord) { mutableStateOf(existingRecord?.trainInNumber ?: "") }
    var givenDate by remember(existingRecord) { mutableStateOf(existingRecord?.givenToUnloadDate ?: "") }
    var givenTime by remember(existingRecord) { mutableStateOf(existingRecord?.givenToUnloadTime ?: "") }
    var untilGivenPreview by remember { mutableStateOf("") }
    var takenDate by remember(existingRecord) { mutableStateOf(existingRecord?.takenFromUnloadDate ?: "") }
    var takenTime by remember(existingRecord) { mutableStateOf(existingRecord?.takenFromUnloadTime ?: "") }
    var depDate by remember(existingRecord) { mutableStateOf(existingRecord?.departureDate ?: "") }
    var depTime by remember(existingRecord) { mutableStateOf(existingRecord?.departureTime ?: "") }
    var afterTakenPreview by remember { mutableStateOf("") }
    var trainOut by remember(existingRecord) { mutableStateOf(existingRecord?.trainOutNumber ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existingRecord == null) "YANGI VAGON MA'LUMOTLARI" else "VAGON MA'LUMOTINI TAHRIRLASH",
            color = p.accentPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )

        Text("1. Vagon nomeri (vergul bilan yozilsa 1, 2, 3-yo'lga bo'linadi)", color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = wagonNum,
            onValueChange = { wagonNum = it },
            placeholder = { Text("Masalan: 625813,657383", color = p.textSecondary.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = p.accentPrimary) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = glassTextFieldColors()
        )

        DateInputField("2. Kirib kelgan sanasi", arrDate) { arrDate = it }
        TimeInputField("3. Vagon kirib kelgan vaqti", arrTime) { arrTime = it }

        Text("4. Poezd nomeri", color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = trainIn,
            onValueChange = { trainIn = it },
            placeholder = { Text("Kirib kelgan poyezd raqami", color = p.textSecondary.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Train, contentDescription = null, tint = p.accentPrimary) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = glassTextFieldColors()
        )

        DateInputField("5. Vagon tushirishga berilgan sana", givenDate) { givenDate = it }
        TimeInputField("6. Vagon tushirishga berilgan vaqti", givenTime) { givenTime = it }

        Button(
            onClick = {
                untilGivenPreview = TimeCalculator.calculateDifference(arrDate, arrTime, givenDate, givenTime)
            },
            colors = ButtonDefaults.buttonColors(containerColor = p.accentSecondary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = p.onAccentText)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (untilGivenPreview.isEmpty()) "Kirish -> Tushirishga berilgan vaqt" else "Keyingi yo'lga: $untilGivenPreview",
                color = p.onAccentText,
                fontWeight = FontWeight.Bold
            )
        }

        DateInputField("7. Vagon tushirish yo'lidan olingan sana", takenDate) { takenDate = it }
        TimeInputField("8. Vagon tushirish yo'lidan olingan vaqti", takenTime) { takenTime = it }
        DateInputField("9. Jo'nagan sanasi", depDate) { depDate = it }
        TimeInputField("10. Jo'nash vaqti", depTime) { depTime = it }

        Button(
            onClick = {
                afterTakenPreview = TimeCalculator.calculateDifference(takenDate, takenTime, depDate, depTime)
            },
            colors = ButtonDefaults.buttonColors(containerColor = p.accentSecondary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = p.onAccentText)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (afterTakenPreview.isEmpty()) "Olingan sana -> Jo'nash vaqti" else "Pastga (Olingan -> Jo'nash): $afterTakenPreview",
                color = p.onAccentText,
                fontWeight = FontWeight.Bold
            )
        }

        Text("11. Poezd raqami (Jo'nash)", color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = trainOut,
            onValueChange = { trainOut = it },
            placeholder = { Text("Jo'nab ketgan poyezd raqami", color = p.textSecondary.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.DirectionsRailway, contentDescription = null, tint = p.accentPrimary) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = glassTextFieldColors()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (wagonNum.isNotBlank()) {
                    val base = WagonRecord(
                        wagonNumber = wagonNum,
                        arrivalDate = arrDate,
                        arrivalTime = arrTime,
                        trainInNumber = trainIn,
                        givenToUnloadDate = givenDate,
                        givenToUnloadTime = givenTime,
                        takenFromUnloadDate = takenDate,
                        takenFromUnloadTime = takenTime,
                        departureDate = depDate,
                        departureTime = depTime,
                        trainOutNumber = trainOut,
                        createdBy = user.username
                    )
                    val splitList = TimeCalculator.splitWagonsAndCreateRecords(wagonNum, base)
                    onSaveRecords(splitList)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = p.accentPrimary),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = p.onAccentText)
            Spacer(Modifier.width(8.dp))
            Text("SAQLASH VA JADVALGA YUBORISH", color = p.onAccentText, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DateInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    val p = LocalGlassPalette.current
    val context = LocalContext.current
    val cal = Calendar.getInstance()
    Column {
        Text(label, color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Rushnoy yozing yoki kalendarni bosing", color = p.textSecondary.copy(alpha = 0.6f), fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val formatted = String.format(Locale.getDefault(), "%02d.%02d.%04d", day, month + 1, year)
                            onValueChange(formatted)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Kalendar", tint = p.accentPrimary)
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = glassTextFieldColors()
        )
    }
}

@Composable
fun TimeInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    val p = LocalGlassPalette.current
    val context = LocalContext.current
    val cal = Calendar.getInstance()
    Column {
        Text(label, color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Soatni yozing (14:30) yoki ikonkani bosing", color = p.textSecondary.copy(alpha = 0.6f), fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val formatted = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                            onValueChange(formatted)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                }) {
                    Icon(Icons.Default.Schedule, contentDescription = "Soat", tint = p.accentPrimary)
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = glassTextFieldColors()
        )
    }
}

/**
 * Jadval ko'rinishi:
 * - Kirib kelgan sanasidan vagon tushirishga berilgan vaqtigacha hisoblanib KEYINGI YO'LGA yoziladi
 * - Vagon tushirish yo'lidan olingan sanadan jo'nash vaqtigacha hisoblanib PASTGA yoziladi
 */
@Composable
fun ScheduleTableScreen(records: List<WagonRecord>, onOpenWhiteExcel: () -> Unit) {
    val p = LocalGlassPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("VAGONLAR HARAKATI JADVALI", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text("Jami qaydlar soni: ${records.size} ta vagon", color = p.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        if (records.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Hozircha vagonlar kiritilmagan", color = p.textSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(records) { rec ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
                        border = BorderStroke(1.2.dp, p.glassBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Vagon №: ${rec.wagonNumber}", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Surface(color = p.accentPrimary, shape = RoundedCornerShape(6.dp)) {
                                    Text(rec.trackNumber, color = p.onAccentText, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 1-bosqich: Kirgan sana/vaqt -> Tushirishga berilgan sana/vaqt
                            Text(
                                text = "Kirgan: ${rec.arrivalDate} ${rec.arrivalTime} (Poyezd: ${rec.trainInNumber})",
                                color = p.textPrimary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Tushirishga berildi: ${rec.givenToUnloadDate} ${rec.givenToUnloadTime}",
                                color = p.textSecondary,
                                fontSize = 12.sp
                            )

                            // KEYINGI YO'LGA: Kirish -> Tushirishga berilgan vaqt
                            Surface(
                                color = p.glassSurfaceBg,
                                border = BorderStroke(1.dp, p.accentPrimary.copy(alpha = 0.55f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Keyingi yo'l (Kirish -> Tushirish):",
                                        color = p.accentPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = rec.durationUntilGiven,
                                        color = p.textPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // 2-bosqich: Yo'ldan olingan sana/vaqt -> Jo'nagan sana/vaqt
                            Text(
                                text = "Yo'ldan olindi: ${rec.takenFromUnloadDate} ${rec.takenFromUnloadTime}",
                                color = p.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Jo'nadi: ${rec.departureDate} ${rec.departureTime} (Poyezd: ${rec.trainOutNumber})",
                                color = p.textPrimary,
                                fontSize = 12.sp
                            )

                            // PASTGA: Tushirish yo'lidan olingan vaqtdan Jo'nash vaqtigacha
                            Surface(
                                color = p.glassSurfaceBg,
                                border = BorderStroke(1.dp, p.accentSecondary.copy(alpha = 0.55f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Pastki qator (Olingan -> Jo'nash):",
                                            color = p.accentSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = rec.durationAfterTaken,
                                            color = p.textPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Jami turgan vaqt: ${rec.totalDuration}",
                                        color = if (p.isNight) Color(0xFF80ED99) else Color(0xFF15803D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onOpenWhiteExcel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(54.dp).border(1.dp, Color(0xFF1B5E20), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF1B5E20))
            Spacer(Modifier.width(8.dp))
            Text("OCHIQ BETDA KO'RISH (EXCEL)", color = Color.Black, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun EditListScreen(
    records: List<WagonRecord>,
    onSelectToEdit: (WagonRecord) -> Unit,
    onDeleteRecord: (WagonRecord) -> Unit
) {
    val p = LocalGlassPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("MA'LUMOTLARNI TAHRIRLASH", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text("Tahrirlash uchun vagon ustiga bosing:", color = p.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(records) { rec ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
                    border = BorderStroke(1.2.dp, p.glassBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectToEdit(rec) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vagon: ${rec.wagonNumber} (${rec.trackNumber})", color = p.accentPrimary, fontWeight = FontWeight.Bold)
                            Text("Kelgan: ${rec.arrivalDate} ${rec.arrivalTime} | Poyezd: ${rec.trainInNumber}", color = p.textPrimary, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onSelectToEdit(rec) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Tahrirlash", tint = p.accentPrimary)
                        }
                        IconButton(onClick = { onDeleteRecord(rec) }) {
                            Icon(Icons.Default.Delete, contentDescription = "O'chirish", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Excel ochiq bet ekrani + "Jadvalni yuklash" tugmasi
 */
@Composable
fun WhiteExcelTableScreen(records: List<WagonRecord>, onBack: () -> Unit) {
    val context = LocalContext.current
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    var exportStatus by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B5E20))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Orqaga", tint = Color.White)
                }
                Text(
                    "Vagonlar Excel Jadvali",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }

            Button(
                onClick = {
                    val fileName = ExcelExporter.exportAndShareCsv(context, records)
                    exportStatus = "Tayyor: $fileName"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Jadvalni yuklash",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Jadvalni yuklash",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }

        if (exportStatus.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(exportStatus, color = Color(0xFF1B5E20), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(modifier = Modifier.horizontalScroll(hScroll).verticalScroll(vScroll)) {
            Row(modifier = Modifier.background(Color(0xFFDCEFD8))) {
                ExcelCell("№", 45, true)
                ExcelCell("Yo'l", 70, true)
                ExcelCell("Vagon №", 105, true)
                ExcelCell("Kirgan sana", 105, true)
                ExcelCell("Kirgan vaqt", 85, true)
                ExcelCell("Kirish poyezd", 105, true)
                ExcelCell("Tushirishga berilgan sana", 125, true)
                ExcelCell("Berilgan vaqt", 90, true)
                ExcelCell("Keyingi yo'l (Kirish -> Tushirish)", 165, true)
                ExcelCell("Yo'ldan olingan sana", 125, true)
                ExcelCell("Olingan vaqt", 90, true)
                ExcelCell("Jo'nagan sana", 105, true)
                ExcelCell("Jo'nash vaqti", 85, true)
                ExcelCell("Pastki qator (Olingan -> Jo'nash)", 165, true)
                ExcelCell("Jo'nash poyezd", 105, true)
                ExcelCell("JAMI TURGAN VAQTI", 155, true)
            }

            records.forEachIndexed { idx, r ->
                Row(modifier = Modifier.background(if (idx % 2 == 0) Color.White else Color(0xFFF9FBF9))) {
                    ExcelCell("${idx + 1}", 45)
                    ExcelCell(r.trackNumber, 70)
                    ExcelCell(r.wagonNumber, 105, true)
                    ExcelCell(r.arrivalDate, 105)
                    ExcelCell(r.arrivalTime, 85)
                    ExcelCell(r.trainInNumber, 105)
                    ExcelCell(r.givenToUnloadDate, 125)
                    ExcelCell(r.givenToUnloadTime, 90)
                    ExcelCell(r.durationUntilGiven, 165, true)
                    ExcelCell(r.takenFromUnloadDate, 125)
                    ExcelCell(r.takenFromUnloadTime, 90)
                    ExcelCell(r.departureDate, 105)
                    ExcelCell(r.departureTime, 85)
                    ExcelCell(r.durationAfterTaken, 165, true)
                    ExcelCell(r.trainOutNumber, 105)
                    ExcelCell(r.totalDuration, 155, true)
                }
            }
        }
    }
}

@Composable
fun ExcelCell(text: String, widthDp: Int, bold: Boolean = false) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(52.dp)
            .border(0.7.dp, Color(0xFF9E9E9E))
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2
        )
    }
}

fun copyPickedFileToCache(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        var fileName = "biriktirilgan_fayl"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex) ?: fileName
            }
        }
        val dir = File(context.cacheDir, "chat_files").apply { mkdirs() }
        val destFile = File(dir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        Pair(fileName, destFile.absolutePath)
    } catch (e: Exception) {
        null
    }
}

fun openChatFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) return
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)
        val mime = context.contentResolver.getType(contentUri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Faylni ochish"))
    } catch (_: Exception) {
    }
}

@Composable
fun ChatScreen(
    user: User,
    role: String,
    messages: MutableList<ChatMessage>,
    onMessageUpsert: (ChatMessage) -> Unit,
    onMessageDelete: (String) -> Unit
) {
    val p = LocalGlassPalette.current
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var attachedFile by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedFile = copyPickedFileToCache(context, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("XODIM VA RAHBAR CHATI (UMUMIY)", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderName == user.username
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isMe) p.accentPrimary else p.glassCardBg),
                        border = BorderStroke(1.dp, p.glassBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${msg.senderName} • ${msg.senderRole} (${msg.time})" + if (msg.isEdited) " [tahrirlangan]" else "",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) p.onAccentText else p.accentPrimary
                            )

                            if (msg.fileName != null && msg.localFilePath != null) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = if (isMe) p.onAccentText.copy(alpha = 0.14f) else p.glassSurfaceBg,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openChatFile(context, msg.localFilePath) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "Fayl",
                                            tint = if (isMe) p.onAccentText else p.accentPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = msg.fileName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isMe) p.onAccentText else p.textPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Ochish",
                                            tint = if (isMe) p.onAccentText else p.accentSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (msg.text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.text,
                                    fontSize = 14.sp,
                                    color = if (isMe) p.onAccentText else p.textPrimary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = {
                                        editingMessage = msg
                                        text = msg.text
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Tahrirlash",
                                        tint = if (isMe) p.onAccentText else p.accentPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (editingMessage?.id == msg.id) {
                                            editingMessage = null
                                            text = ""
                                        }
                                        onMessageDelete(msg.id)
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "O'chirish",
                                        tint = if (isMe) Color(0xFF9B111E) else Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(p.glassCardBg, RoundedCornerShape(10.dp))
                    .border(1.dp, p.accentSecondary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Xabar tahrirlanmoqda...", color = p.accentSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { editingMessage = null; text = "" },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Bekor qilish", tint = p.textPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (attachedFile != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(p.glassCardBg, RoundedCornerShape(10.dp))
                    .border(1.dp, p.glassBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = p.accentPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(attachedFile!!.first, color = p.textPrimary, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(
                    onClick = { attachedFile = null },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Faylni olib tashlash", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .size(48.dp)
                    .background(p.glassCardBg, CircleShape)
                    .border(1.dp, p.glassBorder, CircleShape)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Fayl tashlash", tint = p.accentPrimary)
            }

            Spacer(Modifier.width(6.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(if (editingMessage != null) "Yangi matn..." else "Xabar yozing...", color = p.textSecondary.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                colors = glassTextFieldColors()
            )

            Spacer(Modifier.width(6.dp))

            IconButton(
                onClick = {
                    val currentEdit = editingMessage
                    if (currentEdit != null) {
                        if (text.isNotBlank() || currentEdit.fileName != null) {
                            val idx = messages.indexOfFirst { it.id == currentEdit.id }
                            if (idx != -1) {
                                val updatedMsg = currentEdit.copy(text = text.trim(), isEdited = true)
                                messages[idx] = updatedMsg
                                onMessageUpsert(updatedMsg)
                            }
                        }
                        editingMessage = null
                        text = ""
                    } else {
                        if (text.isNotBlank() || attachedFile != null) {
                            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            val newMsg = ChatMessage(
                                id = System.currentTimeMillis().toString(),
                                senderName = user.username,
                                senderRole = role,
                                text = text.trim(),
                                time = time,
                                fileName = attachedFile?.first,
                                localFilePath = attachedFile?.second
                            )
                            messages.add(newMsg)
                            onMessageUpsert(newMsg)
                            text = ""
                            attachedFile = null
                        }
                    }
                },
                modifier = Modifier.size(50.dp).background(p.accentPrimary, CircleShape)
            ) {
                Icon(
                    imageVector = if (editingMessage != null) Icons.Default.Check else Icons.Default.Send,
                    contentDescription = "Yuborish",
                    tint = p.onAccentText
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    user: User,
    role: String,
    users: List<User>,
    records: List<WagonRecord>,
    messages: List<ChatMessage>,
    isNightGlass: Boolean,
    isCloudOnline: Boolean,
    firebaseUrl: String,
    onUpdateFirebaseUrl: (String) -> Unit,
    onSyncNow: () -> Unit,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    val p = LocalGlassPalette.current
    var inspectedUser by remember { mutableStateOf<User?>(null) }
    var dbUrlInput by remember(firebaseUrl) { mutableStateOf(firebaseUrl) }
    var showServerSettings by remember { mutableStateOf(false) }

    val targetUser = inspectedUser ?: user
    val userWagonCount = records.count { it.createdBy == targetUser.username }
    val userMsgCount = messages.count { it.senderName == targetUser.username }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
            border = BorderStroke(1.2.dp, p.glassBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GLASS OYNA FONI",
                        color = p.accentPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (isNightGlass) "Hozir: Tungi Glass" else "Hozir: Tongi Glass",
                        color = p.textSecondary,
                        fontSize = 12.sp
                    )
                }
                GlassThemeToggleChip(isNightGlass = isNightGlass, onToggleTheme = onToggleTheme)
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
            border = BorderStroke(1.5.dp, p.glassBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (inspectedUser != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        TextButton(onClick = { inspectedUser = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = p.accentPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("O'z profilimga qaytish", color = p.accentPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = p.accentPrimary,
                    modifier = Modifier.size(86.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(targetUser.username, color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                if (targetUser.phoneNumber.isNotBlank()) {
                    Text(targetUser.phoneNumber, color = p.textSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Surface(color = p.accentPrimary, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = "Lavozim: ${if (targetUser.id == user.id) role else targetUser.role}",
                        color = p.onAccentText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = p.glassSurfaceBg),
                        border = BorderStroke(1.dp, p.glassBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$userWagonCount ta", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Kiritgan vagon", color = p.textSecondary, fontSize = 11.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = p.glassSurfaceBg),
                        border = BorderStroke(1.dp, p.glassBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$userMsgCount ta", color = p.accentSecondary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Chat xabari", color = p.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BARCHA FOYDALANUVCHILAR (${users.count { it.role != "Admin" }})",
                color = p.accentPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
            TextButton(onClick = onSyncNow) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = p.accentSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Yangilash", color = p.accentSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        users.filter { it.role != "Admin" }.forEach { member ->
            val activeView = targetUser.id == member.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (activeView) p.accentPrimary.copy(alpha = 0.22f) else p.glassCardBg
                ),
                border = BorderStroke(1.dp, p.glassBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { inspectedUser = if (member.id == user.id) null else member }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = p.accentPrimary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(member.username, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${member.role} • ${member.phoneNumber.ifBlank { "Raqam ko'rsatilmagan" }}", color = p.textSecondary, fontSize = 11.sp)
                        }
                    }
                    Icon(Icons.Default.Visibility, contentDescription = "Profilni ko'rish", tint = p.accentPrimary)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
            border = BorderStroke(1.dp, p.glassBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showServerSettings = !showServerSettings },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = p.accentPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Bulut baza sozlamalari (Firebase)", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Icon(
                        imageVector = if (showServerSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = p.textSecondary
                    )
                }

                AnimatedVisibility(visible = showServerSettings) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = if (isCloudOnline) "Holat: Umumiy baza bilan ulangan" else "Holat: Baza URL manzilini tekshiring",
                            color = if (isCloudOnline) Color(0xFF22C55E) else Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = dbUrlInput,
                            onValueChange = { dbUrlInput = it },
                            label = { Text("Firebase Realtime DB URL", color = p.textSecondary, fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = glassTextFieldColors()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onUpdateFirebaseUrl(dbUrlInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = p.accentPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("BAZA MANZILINI SAQLASH", color = p.onAccentText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD90429)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("CHIQISH", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun AdminPanelScreen(
    users: List<User>,
    isNightGlass: Boolean,
    isCloudOnline: Boolean,
    onToggleTheme: () -> Unit,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit
) {
    val p = LocalGlassPalette.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ADMIN PANEL", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CloudStatusBadge(isOnline = isCloudOnline, onSyncClick = onSyncNow)
                GlassThemeToggleChip(isNightGlass = isNightGlass, onToggleTheme = onToggleTheme)
                TextButton(onClick = onLogout) {
                    Text("Chiqish", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Text("Barcha telefonlardan ro'yxatdan o'tgan foydalanuvchilar:", color = p.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(users) { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = p.glassCardBg),
                    border = BorderStroke(1.2.dp, p.glassBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Login: ${u.username}", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (u.phoneNumber.isNotBlank()) {
                            Text("Telefon: ${u.phoneNumber}", color = p.textSecondary, fontSize = 13.sp)
                        }
                        Text("Parol: ${u.password}", color = p.accentPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("Lavozim: ${u.role}", color = p.accentSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun glassTextFieldColors(): TextFieldColors {
    val p = LocalGlassPalette.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.accentPrimary,
        unfocusedBorderColor = p.glassBorder,
        focusedContainerColor = p.glassSurfaceBg,
        unfocusedContainerColor = p.glassSurfaceBg,
        focusedTextColor = p.textPrimary,
        unfocusedTextColor = p.textPrimary,
        cursorColor = p.accentPrimary
    )
}

@Composable
fun navItemColors(): NavigationBarItemColors {
    val p = LocalGlassPalette.current
    return NavigationBarItemDefaults.colors(
        selectedIconColor = p.onAccentText,
        selectedTextColor = p.accentPrimary,
        indicatorColor = p.accentPrimary,
        unselectedIconColor = p.textSecondary,
        unselectedTextColor = p.textSecondary
    )
}
