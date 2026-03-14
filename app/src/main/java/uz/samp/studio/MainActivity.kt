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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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
            SampStoreTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SampStoreApp(this)
                }
            }
        }
    }
}

private enum class AppStage { SPLASH, LANGUAGE, AUTH, HOME }
private enum class AppLanguage { UZ, RU }
private enum class MainTab { HOME, NEWS, SEARCH, PROFILE }

private data class RemoteFile(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val version: String,
    val size: String,
    val authorName: String,
    val downloads: Int,
    val rating: Double
)

private data class RemoteNews(
    val title: String,
    val body: String
)

private data class ProfileState(
    val email: String = "",
    val nickname: String = "SAMP STUDIO & AZIZ",
    val avatarUrl: String = "",
    val siteUrl: String = "https://turan-rp.uz",
    val token: String = ""
)

private data class HomePayload(
    val latestFiles: List<RemoteFile>,
    val topFiles: List<RemoteFile>,
    val news: List<RemoteNews>,
    val categories: List<String>
)

private object SampStoreApi {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun home(siteUrl: String, language: AppLanguage): HomePayload = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${siteUrl.trimEnd('/')}/api/home")
            .header("x-lang", if (language == AppLanguage.RU) "ru" else "uz")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Home load failed: ${response.code}")
            response.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        HomePayload(
            latestFiles = json.getJSONArray("latestFiles").toFiles(),
            topFiles = json.getJSONArray("topFiles").toFiles(),
            news = json.getJSONArray("news").toNews(language),
            categories = json.getJSONArray("categories").toCategories()
        )
    }

    suspend fun login(siteUrl: String, email: String, password: String): ProfileState = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${siteUrl.trimEnd('/')}/api/auth/login")
            .post(payload)
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Login failed: ${response.code}")
            response.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        val user = json.getJSONObject("user")
        ProfileState(
            email = user.optString("email"),
            nickname = user.optString("nickname", "SAMP STUDIO & AZIZ"),
            avatarUrl = user.optString("avatarUrl"),
            siteUrl = siteUrl,
            token = json.optString("token")
        )
    }

    suspend fun register(siteUrl: String, email: String, password: String, nickname: String): ProfileState =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("nickname", nickname)
                .toString()
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${siteUrl.trimEnd('/')}/api/auth/register")
                .post(payload)
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Register failed: ${response.code}")
                response.body?.string().orEmpty()
            }
            val json = JSONObject(body)
            val user = json.getJSONObject("user")
            ProfileState(
                email = user.optString("email"),
                nickname = user.optString("nickname", nickname),
                avatarUrl = user.optString("avatarUrl"),
                siteUrl = siteUrl,
                token = json.optString("token")
            )
        }

    suspend fun updateProfile(profile: ProfileState): ProfileState = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("nickname", profile.nickname)
            .put("avatarUrl", profile.avatarUrl)
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${profile.siteUrl.trimEnd('/')}/api/profile")
            .put(payload)
            .header("Authorization", "Bearer ${profile.token}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Profile save failed: ${response.code}")
        }
        profile
    }
}

@Composable
private fun SampStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF0C6B57),
            onPrimary = Color.White,
            secondary = Color(0xFFE6942E),
            background = Color(0xFFF3EFE8),
            surface = Color(0xFFFFFBF6),
            onSurface = Color(0xFF1B1A18)
        ),
        content = content
    )
}

@Composable
private fun SampStoreApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("samp_store_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var language by rememberSaveable {
        mutableStateOf(
            when (prefs.getString("language", null)) {
                "ru" -> AppLanguage.RU
                "uz" -> AppLanguage.UZ
                else -> null
            }
        )
    }
    var stage by rememberSaveable { mutableStateOf(AppStage.SPLASH) }
    var profile by remember {
        mutableStateOf(
            ProfileState(
                email = prefs.getString("email", "") ?: "",
                nickname = prefs.getString("nickname", "SAMP STUDIO & AZIZ") ?: "SAMP STUDIO & AZIZ",
                avatarUrl = prefs.getString("avatar_url", "") ?: "",
                siteUrl = prefs.getString("site_url", "https://turan-rp.uz") ?: "https://turan-rp.uz",
                token = prefs.getString("token", "") ?: ""
            )
        )
    }
    var home by remember { mutableStateOf<HomePayload?>(null) }
    val files = remember { mutableStateListOf<RemoteFile>() }
    var message by rememberSaveable { mutableStateOf("") }
    val t = texts(language ?: AppLanguage.UZ)

    LaunchedEffect(Unit) {
        stage = when {
            language == null -> AppStage.LANGUAGE
            profile.token.isBlank() -> AppStage.AUTH
            else -> AppStage.HOME
        }
        if (profile.token.isNotBlank()) {
            runCatching {
                val payload = SampStoreApi.home(profile.siteUrl, language ?: AppLanguage.UZ)
                home = payload
                files.clear()
                files.addAll(payload.latestFiles)
            }.onFailure {
                message = it.message ?: t.networkError
            }
        }
    }

    when (stage) {
        AppStage.SPLASH -> SplashScreen(t)
        AppStage.LANGUAGE -> LanguageScreen {
            language = it
            prefs.edit().putString("language", if (it == AppLanguage.RU) "ru" else "uz").apply()
            stage = if (profile.token.isBlank()) AppStage.AUTH else AppStage.HOME
        }
        AppStage.AUTH -> AuthScreen(
            t = t,
            profile = profile,
            message = message,
            onAuth = { email, password, nickname, isRegister ->
                scope.launch {
                    runCatching {
                        val nextProfile = if (isRegister) {
                            SampStoreApi.register(profile.siteUrl, email, password, nickname)
                        } else {
                            SampStoreApi.login(profile.siteUrl, email, password)
                        }
                        profile = nextProfile
                        prefs.edit()
                            .putString("email", nextProfile.email)
                            .putString("nickname", nextProfile.nickname)
                            .putString("avatar_url", nextProfile.avatarUrl)
                            .putString("site_url", nextProfile.siteUrl)
                            .putString("token", nextProfile.token)
                            .apply()
                        val payload = SampStoreApi.home(nextProfile.siteUrl, language ?: AppLanguage.UZ)
                        home = payload
                        files.clear()
                        files.addAll(payload.latestFiles)
                        message = ""
                        stage = AppStage.HOME
                    }.onFailure {
                        message = it.message ?: t.networkError
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onChangeUrl = {
                profile = profile.copy(siteUrl = it)
                prefs.edit().putString("site_url", it).apply()
            }
        )
        AppStage.HOME -> HomeScreen(
            t = t,
            profile = profile,
            home = home,
            files = files,
            message = message,
            onReload = {
                scope.launch {
                    runCatching {
                        val payload = SampStoreApi.home(profile.siteUrl, language ?: AppLanguage.UZ)
                        home = payload
                        files.clear()
                        files.addAll(payload.latestFiles)
                    }.onFailure {
                        message = it.message ?: t.networkError
                    }
                }
            },
            onSaveProfile = { updated ->
                scope.launch {
                    runCatching {
                        val next = SampStoreApi.updateProfile(updated)
                        profile = next
                        prefs.edit()
                            .putString("nickname", next.nickname)
                            .putString("avatar_url", next.avatarUrl)
                            .putString("site_url", next.siteUrl)
                            .apply()
                        message = t.saved
                    }.onFailure {
                        message = it.message ?: t.networkError
                    }
                }
            },
            onLanguageChange = {
                language = it
                prefs.edit().putString("language", if (it == AppLanguage.RU) "ru" else "uz").apply()
            },
            onLogout = {
                profile = profile.copy(token = "", email = "")
                prefs.edit().remove("token").remove("email").apply()
                stage = AppStage.AUTH
            }
        )
    }
}

@Composable
private fun SplashScreen(t: Strings) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF082822), Color(0xFF0E564B), Color(0xFFE6942E)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(110.dp).background(Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(16.dp))
            Text("SAMP STORE", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(t.owner, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

@Composable
private fun LanguageScreen(onSelect: (AppLanguage) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFF2EEE7), Color(0xFFDDE8DF), Color(0xFFFFD3A2))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = Color(0xFF0C6B57), modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(16.dp))
                Text("Tilni tanlang / Выберите язык", fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onSelect(AppLanguage.UZ) }, modifier = Modifier.fillMaxWidth()) { Text("O'zbekcha") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { onSelect(AppLanguage.RU) }, modifier = Modifier.fillMaxWidth()) { Text("Русский") }
            }
        }
    }
}

@Composable
private fun AuthScreen(
    t: Strings,
    profile: ProfileState,
    message: String,
    onAuth: (String, String, String, Boolean) -> Unit,
    onChangeUrl: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf(profile.email) }
    var password by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var siteUrl by rememberSaveable { mutableStateOf(profile.siteUrl) }
    var isRegister by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF8F0E4), Color(0xFFE8F0E7))))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isRegister) t.register else t.login, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(t.authSubtitle, color = Color(0xFF5D5751))
                OutlinedTextField(value = siteUrl, onValueChange = { siteUrl = it; onChangeUrl(it) }, modifier = Modifier.fillMaxWidth(), label = { Text(t.siteUrl) })
                if (isRegister) {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.nickname) })
                }
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.email) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.password) }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { onAuth(email, password, nickname, isRegister) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isRegister) t.register else t.login)
                }
                OutlinedButton(onClick = { isRegister = !isRegister }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isRegister) t.haveAccount else t.needAccount)
                }
                if (message.isNotBlank()) {
                    Text(message, color = Color(0xFFB12A2A))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    t: Strings,
    profile: ProfileState,
    home: HomePayload?,
    files: List<RemoteFile>,
    message: String,
    onReload: () -> Unit,
    onSaveProfile: (ProfileState) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onLogout: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var avatarUrl by rememberSaveable { mutableStateOf(profile.avatarUrl) }
    var siteUrl by rememberSaveable { mutableStateOf(profile.siteUrl) }

    Scaffold(
        containerColor = Color(0xFFF3EFE8),
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SAMP STORE", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(t.owner, color = Color(0xFF6D6661), fontSize = 13.sp)
                }
                IconButton(onClick = onReload) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(MainTab.HOME, MainTab.NEWS, MainTab.SEARCH, MainTab.PROFILE).forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    MainTab.HOME -> Icons.Outlined.Home
                                    MainTab.NEWS -> Icons.Outlined.Newspaper
                                    MainTab.SEARCH -> Icons.Outlined.Search
                                    MainTab.PROFILE -> Icons.Outlined.AccountCircle
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(t.tabLabel(item)) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.HOME -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { HeroCard(t, profile.siteUrl, home?.categories ?: emptyList()) }
                item { SectionTitle(t.latestFiles) }
                items(files) { file -> RemoteFileCard(file) }
            }
            MainTab.NEWS -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SectionTitle(t.news) }
                items(home?.news ?: emptyList()) {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(it.title, fontWeight = FontWeight.Bold)
                            Text(it.body, color = Color(0xFF5A5651))
                        }
                    }
                }
            }
            MainTab.SEARCH -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                SectionTitle(t.topFiles)
                Spacer(Modifier.height(12.dp))
                (home?.topFiles ?: emptyList()).forEach { file ->
                    RemoteFileCard(file)
                    Spacer(Modifier.height(12.dp))
                }
            }
            MainTab.PROFILE -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle(t.profile)
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.nickname) })
                OutlinedTextField(value = avatarUrl, onValueChange = { avatarUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.avatarUrl) })
                OutlinedTextField(value = siteUrl, onValueChange = { siteUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.siteUrl) })
                Button(onClick = { onSaveProfile(profile.copy(nickname = nickname, avatarUrl = avatarUrl, siteUrl = siteUrl)) }, modifier = Modifier.fillMaxWidth()) { Text(t.save) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onLanguageChange(AppLanguage.UZ) }, modifier = Modifier.weight(1f)) { Text("UZ") }
                    OutlinedButton(onClick = { onLanguageChange(AppLanguage.RU) }, modifier = Modifier.weight(1f)) { Text("RU") }
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(t.logout) }
                if (message.isNotBlank()) Text(message, color = Color(0xFF0C6B57))
            }
        }
    }
}

@Composable
private fun HeroCard(t: Strings, siteUrl: String, categories: List<String>) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF0B5A49), Color(0xFF104250), Color(0xFFE6942E))))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.heroTitle, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(siteUrl, color = Color.White.copy(alpha = 0.88f))
                Text(categories.joinToString(" • "), color = Color.White.copy(alpha = 0.82f))
            }
        }
    }
}

@Composable
private fun RemoteFileCard(file: RemoteFile) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(file.description, color = Color(0xFF5D5751))
            Text("${file.category} • ${file.version} • ${file.size}", color = Color(0xFF0C6B57), fontWeight = FontWeight.SemiBold)
            Text("${file.authorName} • ${file.downloads} downloads • ${file.rating}★", color = Color(0xFF5D5751))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
}

private fun JSONArray.toFiles(): List<RemoteFile> {
    val items = mutableListOf<RemoteFile>()
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        items += RemoteFile(
            id = json.optString("id"),
            title = json.optString("title"),
            description = json.optString("description"),
            category = json.optString("category"),
            version = json.optString("version"),
            size = json.optString("size"),
            authorName = json.optString("authorName"),
            downloads = json.optInt("downloads"),
            rating = json.optDouble("rating")
        )
    }
    return items
}

private fun JSONArray.toNews(language: AppLanguage): List<RemoteNews> {
    val items = mutableListOf<RemoteNews>()
    val key = if (language == AppLanguage.RU) "ru" else "uz"
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        items += RemoteNews(
            title = json.getJSONObject("title").optString(key),
            body = json.getJSONObject("body").optString(key)
        )
    }
    return items
}

private fun JSONArray.toCategories(): List<String> {
    val items = mutableListOf<String>()
    for (index in 0 until length()) items += getJSONObject(index).optString("name")
    return items
}

private data class Strings(
    val owner: String,
    val heroTitle: String,
    val latestFiles: String,
    val topFiles: String,
    val news: String,
    val profile: String,
    val email: String,
    val password: String,
    val nickname: String,
    val avatarUrl: String,
    val siteUrl: String,
    val login: String,
    val register: String,
    val logout: String,
    val save: String,
    val needAccount: String,
    val haveAccount: String,
    val authSubtitle: String,
    val networkError: String,
    val saved: String
) {
    fun tabLabel(item: MainTab): String = when (item) {
        MainTab.HOME -> if (login == "Kirish") "Bosh" else "Главная"
        MainTab.NEWS -> news
        MainTab.SEARCH -> if (login == "Kirish") "Top" else "Топ"
        MainTab.PROFILE -> profile
    }
}

private fun texts(language: AppLanguage): Strings {
    return when (language) {
        AppLanguage.UZ -> Strings(
            owner = "SAMP STUDIO & AZIZ",
            heroTitle = "Bir xil baza bilan ishlaydigan SAMP STORE",
            latestFiles = "Oxirgi fayllar",
            topFiles = "Top fayllar",
            news = "Yangiliklar",
            profile = "Profil",
            email = "Email",
            password = "Parol",
            nickname = "Nik",
            avatarUrl = "Avatar URL",
            siteUrl = "Sayt manzili",
            login = "Kirish",
            register = "Ro'yxatdan o'tish",
            logout = "Chiqish",
            save = "Saqlash",
            needAccount = "Akkaunt yo'qmi?",
            haveAccount = "Akkaunt bormi?",
            authSubtitle = "Telefon va sayt endi bir xil backend hamda bitta baza bilan ishlaydi.",
            networkError = "Tarmoq yoki server xatosi",
            saved = "Saqlandi"
        )
        AppLanguage.RU -> Strings(
            owner = "SAMP STUDIO & AZIZ",
            heroTitle = "SAMP STORE с единой базой данных",
            latestFiles = "Последние файлы",
            topFiles = "Популярные файлы",
            news = "Новости",
            profile = "Профиль",
            email = "Email",
            password = "Пароль",
            nickname = "Ник",
            avatarUrl = "URL аватара",
            siteUrl = "Адрес сайта",
            login = "Войти",
            register = "Регистрация",
            logout = "Выйти",
            save = "Сохранить",
            needAccount = "Нет аккаунта?",
            haveAccount = "Уже есть аккаунт?",
            authSubtitle = "Телефон и сайт теперь работают через один backend и одну базу.",
            networkError = "Ошибка сети или сервера",
            saved = "Сохранено"
        )
    }
}
