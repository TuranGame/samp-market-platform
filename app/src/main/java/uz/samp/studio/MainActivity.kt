
package uz.samp.studio

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = Color(0xFF1A73E8),
                    secondary = Color(0xFF34A853),
                    tertiary = Color(0xFFFB8C00),
                    background = Color(0xFFF3F7FF),
                    surface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SampStoreApp(this)
                }
            }
        }
    }
}

private enum class Tab { HOME, CHAT, PROFILE, ADMIN }
private enum class Role(val value: String) { USER("user"), DEVELOPER("developer") }

private data class CommentItem(val id: String, val authorName: String, val message: String)
private data class NotificationItem(val id: String, val kind: String, val message: String)
private data class UserItem(val id: String, val username: String, val nickname: String, val role: String)
private data class ChatItem(val id: String, val fromUserId: String, val fromNickname: String, val toUserId: String, val message: String)
private data class FileItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val version: String,
    val size: String,
    val authorName: String,
    val status: String,
    val rating: Double,
    val downloads: Int,
    val comments: List<CommentItem>
)

private data class ProfileState(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val role: String = "user",
    val badge: String = "USER",
    val token: String = "",
    val notifications: List<NotificationItem> = emptyList(),
    val uploads: List<FileItem> = emptyList()
)

private data class HomePayload(val latest: List<FileItem>, val top: List<FileItem>, val categories: List<String>)
private data class AdminComment(val fileId: String, val commentId: String, val fileTitle: String, val message: String)
private data class AdminOverview(val files: List<FileItem>, val users: List<UserItem>, val comments: List<AdminComment>)

private object Api {
    private const val BASE = "http://185.204.3.106"
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun home(): HomePayload = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/api/home").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Home failed")
            it.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        HomePayload(
            latest = json.getJSONArray("latestFiles").toFiles(),
            top = json.getJSONArray("topFiles").toFiles(),
            categories = json.getJSONArray("categories").toCategories()
        )
    }

    suspend fun files(query: String = "", category: String = ""): List<FileItem> = withContext(Dispatchers.IO) {
        val qp = mutableListOf<String>()
        if (query.isNotBlank()) qp += "q=$query"
        if (category.isNotBlank()) qp += "category=$category"
        val suffix = if (qp.isEmpty()) "" else "?${qp.joinToString("&")}"
        val request = Request.Builder().url("$BASE/api/files$suffix").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Files failed")
            it.body?.string().orEmpty()
        }
        JSONArray(body).toFiles()
    }

    suspend fun login(identity: String, password: String): ProfileState = auth("/api/auth/login", JSONObject().put("email", identity).put("password", password))

    suspend fun checkIdentity(identity: String): Boolean = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(identity, StandardCharsets.UTF_8.toString())
        val request = Request.Builder().url("$BASE/api/auth/check-identity?identity=$encoded").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) return@withContext false
            it.body?.string().orEmpty()
        }
        JSONObject(body).optBoolean("exists", false)
    }

    suspend fun register(email: String, password: String, nickname: String, username: String, role: Role): ProfileState {
        val payload = JSONObject().put("email", email).put("password", password).put("nickname", nickname).put("username", username).put("role", role.value)
        return auth("/api/auth/register", payload)
    }

    private suspend fun auth(path: String, payload: JSONObject): ProfileState = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE$path").post(payload.toString().toRequestBody(jsonType)).build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Auth failed")
            it.body?.string().orEmpty()
        }
        JSONObject(body).toAuthProfile()
    }

    suspend fun profile(token: String): ProfileState = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/api/profile").header("Authorization", "Bearer $token").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Profile failed")
            it.body?.string().orEmpty()
        }
        JSONObject(body).toProfile(token)
    }

    suspend fun updateProfile(token: String, nickname: String, avatarUrl: String): ProfileState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("nickname", nickname).put("avatarUrl", avatarUrl).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE/api/profile").header("Authorization", "Bearer $token").put(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Save failed") }
        profile(token)
    }

    suspend fun users(token: String): List<UserItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/api/users").header("Authorization", "Bearer $token").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Users failed")
            it.body?.string().orEmpty()
        }
        JSONArray(body).toUsers()
    }

    suspend fun threads(token: String): List<ChatItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/api/chat/threads").header("Authorization", "Bearer $token").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Threads failed")
            it.body?.string().orEmpty()
        }
        JSONArray(body).toThreads()
    }

    suspend fun sendMessage(token: String, toUserId: String, message: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("toUserId", toUserId).put("message", message).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE/api/chat/send").header("Authorization", "Bearer $token").post(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Send failed") }
    }

    suspend fun upload(token: String, title: String, description: String, category: String, version: String, size: String, link: String) = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("title", title)
            .addFormDataPart("description", description)
            .addFormDataPart("category", category)
            .addFormDataPart("version", version)
            .addFormDataPart("size", size)
            .addFormDataPart("downloadUrl", link)
            .build()
        val request = Request.Builder().url("$BASE/api/files/upload").header("Authorization", "Bearer $token").post(body).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Upload failed") }
    }

    suspend fun comment(token: String, fileId: String, message: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("message", message).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE/api/files/$fileId/comments").header("Authorization", "Bearer $token").post(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Comment failed") }
    }

    suspend fun rate(token: String, fileId: String, value: Int) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("value", value).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE/api/files/$fileId/rating").header("Authorization", "Bearer $token").post(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Rate failed") }
    }

    suspend fun adminOverview(token: String): AdminOverview = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/api/admin/overview").header("Authorization", "Bearer $token").build()
        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) error("Admin failed")
            it.body?.string().orEmpty()
        }
        JSONObject(body).toAdminOverview()
    }

    suspend fun setFileStatus(token: String, fileId: String, status: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("status", status).toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$BASE/api/admin/files/$fileId/status").header("Authorization", "Bearer $token").patch(payload).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Moderation failed") }
    }

    suspend fun deleteComment(token: String, fileId: String, commentId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/api/admin/comments/$fileId/$commentId").header("Authorization", "Bearer $token").delete().build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Delete comment failed") }
    }
}

@Composable
private fun SampStoreApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("samp_store", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var token by rememberSaveable { mutableStateOf(prefs.getString("token", "") ?: "") }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var home by remember { mutableStateOf(HomePayload(emptyList(), emptyList(), emptyList())) }
    var files by remember { mutableStateOf(emptyList<FileItem>()) }
    var profile by remember { mutableStateOf(ProfileState()) }
    var users by remember { mutableStateOf(emptyList<UserItem>()) }
    var threads by remember { mutableStateOf(emptyList<ChatItem>()) }
    var admin by remember { mutableStateOf(AdminOverview(emptyList(), emptyList(), emptyList())) }
    var msg by rememberSaveable { mutableStateOf("") }
    var selectedApp by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun refreshPublic(q: String = "", category: String = "") {
        home = Api.home()
        files = Api.files(q, category)
    }

    suspend fun refreshPrivate() {
        if (token.isBlank()) return
        profile = Api.profile(token)
        users = Api.users(token)
        threads = Api.threads(token)
        if (profile.role == "admin" && profile.username == "Daler_Baltaev") {
            admin = Api.adminOverview(token)
        }
    }

    LaunchedEffect(token) {
        runCatching { refreshPublic() }.onFailure { msg = it.message ?: "Network error" }
        if (token.isNotBlank()) {
            runCatching { refreshPrivate() }.onFailure { msg = it.message ?: "Auth error" }
        }
    }

    if (token.isBlank()) {
        AuthScreen(onLogin = { identity, password ->
            scope.launch {
                runCatching {
                    val auth = Api.login(identity, password)
                    token = auth.token
                    prefs.edit().putString("token", auth.token).apply()
                }.onFailure {
                    msg = it.message ?: "Login failed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }, onRegister = { email, password, nickname, username, role ->
            scope.launch {
                runCatching {
                    val auth = Api.register(email, password, nickname, username, role)
                    token = auth.token
                    prefs.edit().putString("token", auth.token).apply()
                }.onFailure {
                    msg = it.message ?: "Register failed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }, message = msg)
        return
    }

    Scaffold(
        topBar = {
            if (selectedApp == null) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SAMP STORE", fontWeight = FontWeight.Black, color = Color(0xFF0A3D91))
                        Text("Web + APK parity", color = Color(0xFF607D8B))
                    }
                    IconButton(onClick = {
                        token = ""
                        prefs.edit().remove("token").apply()
                    }) { Text("Out", color = Color(0xFF0A3D91)) }
                }
            }
        },
        bottomBar = {
            if (selectedApp == null) {
                NavigationBar {
                    NavigationBarItem(selected = tab == Tab.HOME, onClick = { tab = Tab.HOME }, icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = tab == Tab.CHAT, onClick = { tab = Tab.CHAT }, icon = { Icon(Icons.Outlined.Chat, null) }, label = { Text("Chat") })
                    NavigationBarItem(selected = tab == Tab.PROFILE, onClick = { tab = Tab.PROFILE }, icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("Profile") })
                    if (profile.role == "admin" && profile.username == "Daler_Baltaev") {
                        NavigationBarItem(selected = tab == Tab.ADMIN, onClick = { tab = Tab.ADMIN }, icon = { Icon(Icons.Outlined.AdminPanelSettings, null) }, label = { Text("Admin") })
                    }
                }
            }
        }
    ) { padding ->
        if (selectedApp != null) {
            val appId = selectedApp
            val app = files.find { it.id == appId }
            if (app != null) {
                AppDetailsScreen(
                    app = app,
                    onBack = { selectedApp = null },
                    onComment = { fileId, text ->
                        scope.launch {
                            runCatching {
                                Api.comment(token, fileId, text)
                                refreshPublic()
                                refreshPrivate()
                            }.onFailure { msg = it.message ?: "Comment failed" }
                        }
                    },
                    onRate = { fileId, value ->
                        scope.launch {
                            runCatching {
                                Api.rate(token, fileId, value)
                                refreshPublic()
                            }.onFailure { msg = it.message ?: "Rate failed" }
                        }
                    }
                )
            }
        } else {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    files = files,
                    categories = home.categories,
                    onRefresh = { q, cat ->
                        scope.launch { runCatching { refreshPublic(q, cat) }.onFailure { msg = it.message ?: "Load failed" } }
                    },
                    onSelectApp = { appId ->
                        selectedApp = appId
                    }
                )
                Tab.CHAT -> ChatScreen(
                    modifier = Modifier.padding(padding),
                    users = users,
                    threads = threads,
                    selfId = profile.id,
                    onSend = { to, text ->
                        scope.launch {
                            runCatching {
                                Api.sendMessage(token, to, text)
                                refreshPrivate()
                            }.onFailure { msg = it.message ?: "Send failed" }
                        }
                    }
                )
                Tab.PROFILE -> ProfileScreen(
                    modifier = Modifier.padding(padding),
                    profile = profile,
                    onSave = { nickname, avatar ->
                        scope.launch { runCatching { profile = Api.updateProfile(token, nickname, avatar) }.onFailure { msg = it.message ?: "Save failed" } }
                    },
                    onUpload = { title, desc, cat, ver, size, link ->
                        scope.launch {
                            runCatching {
                                Api.upload(token, title, desc, cat, ver, size, link)
                                refreshPublic()
                                refreshPrivate()
                            }.onFailure { msg = it.message ?: "Upload failed" }
                        }
                    }
                )
                Tab.ADMIN -> AdminScreen(
                    modifier = Modifier.padding(padding),
                    overview = admin,
                    onApprove = { id ->
                        scope.launch {
                            runCatching {
                                Api.setFileStatus(token, id, "approved")
                                refreshPublic()
                                refreshPrivate()
                            }.onFailure { msg = it.message ?: "Approve failed" }
                        }
                    },
                    onReject = { id ->
                        scope.launch {
                            runCatching {
                                Api.setFileStatus(token, id, "rejected")
                                refreshPublic()
                                refreshPrivate()
                            }.onFailure { msg = it.message ?: "Reject failed" }
                        }
                    },
                    onDeleteComment = { fileId, commentId ->
                        scope.launch {
                            runCatching {
                                Api.deleteComment(token, fileId, commentId)
                                refreshPrivate()
                            }.onFailure { msg = it.message ?: "Delete comment failed" }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(onLogin: (String, String) -> Unit, onRegister: (String, String, String, String, Role) -> Unit, message: String) {
    var register by rememberSaveable { mutableStateOf(false) }
    var identity by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.USER) }
    var identityExists by rememberSaveable { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(identity, register) {
        if (register) return@LaunchedEffect
        val clean = identity.trim()
        if (clean.length < 3) {
            identityExists = null
            return@LaunchedEffect
        }
        delay(300)
        identityExists = runCatching { Api.checkIdentity(clean) }.getOrNull()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF001B3D), Color(0xFF003D7A), Color(0xFF005AC1)), startY = 0f, endY = 1000f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF64B5F6), Color(0xFF1976D2))), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SS", fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("SAMP STORE", fontWeight = FontWeight.Black, color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineMedium)
                Text(if (register) "Yangi akkaunt yaratish" else "Akkauntingizga kirish", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Tab buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { register = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (register) Color(0xFF1565C0) else Color(0x4D1565C0),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ro'yxatdan o'tish", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { register = false },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (!register) Color(0xFF1565C0) else Color(0x4D1565C0),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kirish", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (register) {
                        // Registration Form
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Login") },
                            leadingIcon = { Icon(Icons.Outlined.Person, null, tint = Color(0xFF1565C0)) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("To'liq ism/Taqqallus") },
                            leadingIcon = { Icon(Icons.Outlined.Person, null, tint = Color(0xFF1565C0)) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Outlined.Email, null, tint = Color(0xFF1565C0)) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Role Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Rol tanlang", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0), style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = { role = Role.USER },
                                    label = { Text("Foydalanuvchi") },
                                    modifier = Modifier.weight(1f),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (role == Role.USER) Color(0xFF1565C0) else Color(0xFFE3F2FD),
                                        labelColor = if (role == Role.USER) Color.White else Color(0xFF0D47A1)
                                    )
                                )
                                AssistChip(
                                    onClick = { role = Role.DEVELOPER },
                                    label = { Text("Developer") },
                                    modifier = Modifier.weight(1f),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (role == Role.DEVELOPER) Color(0xFF1565C0) else Color(0xFFE3F2FD),
                                        labelColor = if (role == Role.DEVELOPER) Color.White else Color(0xFF0D47A1)
                                    )
                                )
                            }
                        }
                    } else {
                        // Login Form
                        OutlinedTextField(
                            value = identity,
                            onValueChange = { identity = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Login yoki Email") },
                            leadingIcon = { Icon(Icons.Outlined.Person, null, tint = Color(0xFF1565C0)) },
                            trailingIcon = {
                                when (identityExists) {
                                    true -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(24.dp))
                                    false -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(24.dp))
                                    null -> {}
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        if (identityExists == false && identity.trim().length >= 3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text("Bu login ro'yxatdan o'tmagan", color = Color(0xFFC62828), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Parol") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = Color(0xFF1565C0)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Submit button
                    Button(
                        onClick = {
                            if (register) {
                                onRegister(email, password, nickname, username, role)
                            } else {
                                onLogin(identity, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (register) "Ro'yxatdan o'tish" else "Kirish", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }

                    // Error message
                    if (message.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFCDD2), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(message, color = Color(0xFFC62828), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    files: List<FileItem>,
    categories: List<String>,
    onRefresh: (String, String) -> Unit,
    onSelectApp: (String) -> Unit
) {
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF34A853), Color(0xFF1A73E8), Color(0xFFFB8C00)))).padding(16.dp)) {
                    Text("Barcha oyinlar va dasturlar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            OutlinedTextField(value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Qidirish") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
        }
        item(span = { GridItemSpan(2) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CategoryChip("Barchasi", cat.isBlank()) { cat = ""; onRefresh(q, "") } }
                items(categories) { c -> CategoryChip(c, cat.equals(c, true)) { cat = c; onRefresh(q, c) } }
            }
        }
        items(files) { file ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clickable { onSelectApp(file.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // App icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF4FC3F7), Color(0xFFAB47BC))),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(file.title.take(1), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge)
                    }

                    // App info
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(file.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        Text(file.category, color = Color(0xFF607D8B), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)

                        // Rating and downloads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${file.rating}★", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFB8C00))
                            Text("${file.downloads}+", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3949AB))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDetailsScreen(
    app: FileItem,
    onBack: () -> Unit,
    onComment: (String, String) -> Unit,
    onRate: (String, Int) -> Unit
) {
    var comment by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("← ", color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
            }
            Text(app.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }

        // Details content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App icon
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF4FC3F7), Color(0xFFAB47BC))),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.title.take(1), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displaySmall)
                }
            }

            // App info
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Title and developer
                        Column {
                            Text(app.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                            Text("Razrabi: ${app.authorName}", color = Color(0xFF607D8B), style = MaterialTheme.typography.labelMedium)
                        }

                        // Rating
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${app.rating}★", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFB8C00))
                            Text("(${app.downloads} o'rnatish)", color = Color(0xFF607D8B), style = MaterialTheme.typography.labelSmall)
                        }

                        // Downloads
                        Text("${app.downloads}+ ta foydalanuvchi o'rnatgan", color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Info cards
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(onClick = {}, label = { Text(app.version) }, modifier = Modifier.weight(1f), colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE3F2FD)))
                            AssistChip(onClick = {}, label = { Text(app.size) }, modifier = Modifier.weight(1f), colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE3F2FD)))
                        }
                        AssistChip(onClick = {}, label = { Text(app.category) }, modifier = Modifier.fillMaxWidth(), colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE3F2FD)))
                        if (app.status.isNotBlank()) {
                            AssistChip(onClick = {}, label = { Text(app.status, color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold) }, modifier = Modifier.fillMaxWidth(), colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFC8E6C9)))
                        }
                    }
                }
            }

            // Description
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tavsifi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(app.description, color = Color(0xFF455A64), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Ratings
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Baho berish", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { v -> AssistChip(onClick = { onRate(app.id, v) }, label = { Text("$v★") }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE3F2FD))) }
                        }
                    }
                }
            }

            // Comments
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sharhlar (${app.comments.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            if (app.comments.size > 10) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.height(32.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD)),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    Text("Barcha sharhni ko'rish", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                                }
                            }
                        }

                        // Last 10 comments
                        app.comments.take(10).forEach { c ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(c.authorName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text(c.message, color = Color(0xFF455A64), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Add comment
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Sharh yozish") },
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 3
                        )
                        Button(
                            onClick = { if (comment.isNotBlank()) { onComment(app.id, comment); comment = "" } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Text("Yuborish", color = Color.White)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun ChatScreen(modifier: Modifier, users: List<UserItem>, threads: List<ChatItem>, selfId: String, onSend: (String, String) -> Unit) {
    var target by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val visible = threads.filter { (it.fromUserId == selfId && it.toUserId == target) || (it.fromUserId == target && it.toUserId == selfId) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Chat", fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users) { u -> CategoryChip("${u.nickname} (${u.role})", target == u.id) { target = u.id } }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visible) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = if (m.fromUserId == selfId) Color(0xFFE8F5E9) else Color.White)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(m.fromNickname, fontWeight = FontWeight.Bold)
                        Text(m.message)
                    }
                }
            }
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
        Button(onClick = { if (target.isNotBlank() && text.isNotBlank()) { onSend(target, text); text = "" } }) { Text("Send") }
    }
}

@Composable
private fun ProfileScreen(modifier: Modifier, profile: ProfileState, onSave: (String, String) -> Unit, onUpload: (String, String, String, String, String, String) -> Unit) {
    var nickname by rememberSaveable(profile.nickname) { mutableStateOf(profile.nickname) }
    var avatar by rememberSaveable(profile.avatarUrl) { mutableStateOf(profile.avatarUrl) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("apk") }
    var version by rememberSaveable { mutableStateOf("1.0.0") }
    var size by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${profile.nickname} • ${profile.badge}", fontWeight = FontWeight.Bold)
                Text("ID ${profile.id} • @${profile.username}", color = Color(0xFF607D8B))
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname") })
                OutlinedTextField(value = avatar, onValueChange = { avatar = it }, label = { Text("Avatar URL") })
                Button(onClick = { onSave(nickname, avatar) }) { Text("Save profile") }
            }
        }
        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Notifications", fontWeight = FontWeight.Bold)
                if (profile.notifications.isEmpty()) Text("No notifications", color = Color(0xFF607D8B))
                profile.notifications.forEach { n -> Text("${n.kind}: ${n.message}") }
            }
        }
        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Upload", fontWeight = FontWeight.Bold)
                if (profile.role == "developer" || profile.role == "admin") {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                    OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text("Version") })
                    OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Size") })
                    OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Download link") })
                    Button(onClick = { onUpload(title, description, category, version, size, link) }) { Text("Publish") }
                } else {
                    Text("Upload faqat developer/admin uchun.", color = Color(0xFFEF6C00))
                }
            }
        }
    }
}

@Composable
private fun AdminScreen(
    modifier: Modifier,
    overview: AdminOverview,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onDeleteComment: (String, String) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Admin Panel", fontWeight = FontWeight.Black, color = Color(0xFF0A3D91)) }
        item { Text("Users: ${overview.users.size} • Files: ${overview.files.size} • Comments: ${overview.comments.size}") }
        items(overview.files.take(30)) { f ->
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.title, fontWeight = FontWeight.Bold)
                    Text("${f.authorName} • ${f.status}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(f.id) }) { Text("Approve") }
                        OutlinedButton(onClick = { onReject(f.id) }) { Text("Reject") }
                    }
                }
            }
        }
        item { Text("Comments moderation", fontWeight = FontWeight.Bold) }
        items(overview.comments.take(30)) { c ->
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.fileTitle, fontWeight = FontWeight.Bold)
                    Text(c.message, color = Color(0xFF607D8B))
                    OutlinedButton(onClick = { onDeleteComment(c.fileId, c.commentId) }) { Text("Delete comment") }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(title) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) Color(0xFF1E88E5) else Color(0xFFE3F2FD),
            labelColor = if (selected) Color.White else Color(0xFF0D47A1)
        )
    )
}

private fun JSONObject.toAuthProfile(): ProfileState {
    val user = getJSONObject("user")
    return ProfileState(
        id = user.optString("id"),
        email = user.optString("email"),
        username = user.optString("username"),
        nickname = user.optString("nickname"),
        avatarUrl = user.optString("avatarUrl"),
        role = user.optString("role"),
        badge = user.optString("badge"),
        token = optString("token")
    )
}

private fun JSONObject.toProfile(token: String): ProfileState {
    return ProfileState(
        id = optString("id"),
        email = optString("email"),
        username = optString("username"),
        nickname = optString("nickname"),
        avatarUrl = optString("avatarUrl"),
        role = optString("role"),
        badge = optString("badge"),
        token = token,
        notifications = optJSONArray("notifications")?.toNotifications() ?: emptyList(),
        uploads = optJSONArray("uploads")?.toFiles() ?: emptyList()
    )
}

private fun JSONObject.toAdminOverview(): AdminOverview {
    val files = optJSONArray("files")?.toFiles() ?: emptyList()
    val users = optJSONArray("users")?.toUsers() ?: emptyList()
    val comments = mutableListOf<AdminComment>()
    val raw = optJSONArray("comments") ?: JSONArray()
    for (i in 0 until raw.length()) {
        val o = raw.getJSONObject(i)
        comments += AdminComment(
            fileId = o.optString("fileId"),
            commentId = o.optString("id"),
            fileTitle = o.optString("fileTitle"),
            message = o.optString("message")
        )
    }
    return AdminOverview(files, users, comments)
}

private fun JSONArray.toFiles(): List<FileItem> {
    val out = mutableListOf<FileItem>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += FileItem(
            id = o.optString("id"),
            title = o.optString("title"),
            description = o.optString("description"),
            category = o.optString("category"),
            version = o.optString("version"),
            size = o.optString("size"),
            authorName = o.optString("authorName"),
            status = o.optString("status", "approved"),
            rating = o.optDouble("rating"),
            downloads = o.optInt("downloads"),
            comments = o.optJSONArray("comments")?.toComments() ?: emptyList()
        )
    }
    return out
}

private fun JSONArray.toComments(): List<CommentItem> {
    val out = mutableListOf<CommentItem>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += CommentItem(o.optString("id"), o.optString("authorName"), o.optString("message"))
    }
    return out
}

private fun JSONArray.toNotifications(): List<NotificationItem> {
    val out = mutableListOf<NotificationItem>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += NotificationItem(o.optString("id"), o.optString("kind"), o.optString("message"))
    }
    return out
}

private fun JSONArray.toUsers(): List<UserItem> {
    val out = mutableListOf<UserItem>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += UserItem(o.optString("id"), o.optString("username"), o.optString("nickname"), o.optString("role"))
    }
    return out
}

private fun JSONArray.toThreads(): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    for (i in 0 until length()) {
        val o = getJSONObject(i)
        out += ChatItem(o.optString("id"), o.optString("fromUserId"), o.optString("fromNickname"), o.optString("toUserId"), o.optString("message"))
    }
    return out
}

private fun JSONArray.toCategories(): List<String> {
    val out = mutableListOf<String>()
    for (i in 0 until length()) out += getJSONObject(i).optString("name")
    return out
}
