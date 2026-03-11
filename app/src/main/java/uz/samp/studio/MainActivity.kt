package uz.samp.studio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SampMarketTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SampMarketApp(this)
                }
            }
        }
    }
}

@Composable
private fun SampMarketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF005B4F),
            onPrimary = Color.White,
            secondary = Color(0xFFDB7C26),
            background = Color(0xFFF6F1E8),
            surface = Color(0xFFFFFBF6),
            onSurface = Color(0xFF1C1B1A)
        ),
        content = content
    )
}

private enum class AppStage { SPLASH, LANGUAGE, AUTH, HOME }
private enum class AppLanguage { UZ, RU }
private enum class MainTab { NEWS, SEARCH, HOME, CATEGORIES, FEED }

private data class CatalogItem(
    val title: String,
    val description: String,
    val category: String,
    val author: String,
    val rating: Float,
    val isApk: Boolean = false
)

private data class ProfileState(
    val email: String = "",
    val nickname: String = "SAMP Player",
    val avatarUrl: String = "",
    val baseUrl: String = "https://localhost:8080"
)

@Composable
private fun SampMarketApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("samp_market_prefs", Context.MODE_PRIVATE) }
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
                nickname = prefs.getString("nickname", "SAMP Player") ?: "SAMP Player",
                avatarUrl = prefs.getString("avatar_url", "") ?: "",
                baseUrl = prefs.getString("base_url", "https://localhost:8080") ?: "https://localhost:8080"
            )
        )
    }
    val latestItems = remember {
        mutableStateListOf(
            CatalogItem("Arizona Mobile Pack", "APK va mod to'plami", "APK", "Admin", 4.8f, true),
            CatalogItem("SAMP HUD Neon", "Yorqin interfeys to'plami", "Mod", "Studio", 4.6f),
            CatalogItem("Turan RP Launcher", "Serverga ulanish uchun launcher", "Launcher", "Turan", 4.9f, true)
        )
    }
    val newsItems = remember {
        listOf(
            "Yangi publikasiyalar lentaga qo'shildi",
            "APK yuklash interfeysi tayyor",
            "O'zbek va rus tillari lokal rejimda ishlayapti"
        )
    }
    val currentTexts = texts(language ?: AppLanguage.UZ)

    LaunchedEffect(Unit) {
        delay(1800)
        stage = when {
            language == null -> AppStage.LANGUAGE
            prefs.getBoolean("is_logged_in", false) -> AppStage.HOME
            else -> AppStage.AUTH
        }
    }

    AnimatedContent(targetState = stage, label = "stage") { currentStage ->
        when (currentStage) {
            AppStage.SPLASH -> SplashScreen(currentTexts)
            AppStage.LANGUAGE -> LanguageScreen(
                onSelect = {
                    language = it
                    prefs.edit().putString("language", if (it == AppLanguage.UZ) "uz" else "ru").apply()
                    stage = AppStage.AUTH
                }
            )
            AppStage.AUTH -> AuthScreen(
                t = currentTexts,
                initialEmail = profile.email,
                onAuth = { email, password ->
                    if (email.isBlank() || password.length < 4) {
                        Toast.makeText(context, currentTexts.invalidLogin, Toast.LENGTH_SHORT).show()
                    } else {
                        profile = profile.copy(email = email)
                        prefs.edit()
                            .putString("email", email)
                            .putBoolean("is_logged_in", true)
                            .apply()
                        stage = AppStage.HOME
                    }
                }
            )
            AppStage.HOME -> HomeScreen(
                t = currentTexts,
                profile = profile,
                latestItems = latestItems,
                newsItems = newsItems,
                onSaveProfile = { updated ->
                    profile = updated
                    prefs.edit()
                        .putString("email", updated.email)
                        .putString("nickname", updated.nickname)
                        .putString("avatar_url", updated.avatarUrl)
                        .putString("base_url", updated.baseUrl)
                        .apply()
                },
                onLanguageChange = {
                    language = it
                    prefs.edit().putString("language", if (it == AppLanguage.UZ) "uz" else "ru").apply()
                },
                onLogout = {
                    prefs.edit().putBoolean("is_logged_in", false).apply()
                    stage = AppStage.AUTH
                },
                onUploadCompleted = { latestItems.add(0, it) }
            )
        }
    }
}

@Composable
private fun SplashScreen(t: Strings) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF062925), Color(0xFF0E4F46), Color(0xFFDB7C26))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Text("SAMP MARKET", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(t.loading, color = Color.White, modifier = Modifier.alpha(alpha), fontSize = 16.sp)
        }
    }
}

@Composable
private fun LanguageScreen(onSelect: (AppLanguage) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFF6F1E8), Color(0xFFD7E7D8), Color(0xFFFFD4A7))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f))) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = Color(0xFF005B4F), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(14.dp))
                Text("Tilni tanlang / Выберите язык", fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onSelect(AppLanguage.UZ) }, modifier = Modifier.fillMaxWidth()) {
                    Text("O'zbekcha")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { onSelect(AppLanguage.RU) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Русский")
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(t: Strings, initialEmail: String, onAuth: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var isRegister by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF7F0E4), Color(0xFFE8EFE7))))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(t.welcomeTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(t.authSubtitle, color = Color(0xFF57514C))
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(t.email) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(t.password) },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = { onAuth(email, password) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isRegister) t.register else t.login)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { isRegister = !isRegister }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (isRegister) t.haveAccount else t.needAccount)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    t: Strings,
    profile: ProfileState,
    latestItems: List<CatalogItem>,
    newsItems: List<String>,
    onSaveProfile: (ProfileState) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onLogout: () -> Unit,
    onUploadCompleted: (CatalogItem) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showProfile by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFFF6F1E8),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.NEWS -> Icons.Outlined.Newspaper
                                    MainTab.SEARCH -> Icons.Outlined.Search
                                    MainTab.HOME -> Icons.Outlined.Home
                                    MainTab.CATEGORIES -> Icons.Outlined.Dashboard
                                    MainTab.FEED -> Icons.Outlined.Article
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(t.tabLabel(tab)) }
                    )
                }
            }
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SAMP MARKET", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text(profile.baseUrl, color = Color(0xFF6D655E), fontSize = 13.sp)
                }
                IconButton(onClick = { showProfile = !showProfile }) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showProfile) {
                ProfileScreen(
                    t = t,
                    profile = profile,
                    onSaveProfile = onSaveProfile,
                    onLanguageChange = onLanguageChange,
                    onLogout = onLogout
                )
            } else {
                when (selectedTab) {
                    MainTab.HOME -> HomeTab(t, latestItems, newsItems)
                    MainTab.NEWS -> NewsTab(t, newsItems)
                    MainTab.SEARCH -> SearchTab(t, latestItems)
                    MainTab.CATEGORIES -> CategoriesTab(latestItems)
                    MainTab.FEED -> FeedTab(t, latestItems, onUploadCompleted)
                }
            }
        }
    }
}

@Composable
private fun HomeTab(t: Strings, latestItems: List<CatalogItem>, newsItems: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroCard(t) }
        item { SectionTitle(t.categories) }
        item { CategoryRow(listOf("APK", "Mods", "Launchers", "News")) }
        item { SectionTitle(t.latestPublications) }
        items(latestItems) { CatalogCard(item = it) }
        item { SectionTitle(t.news) }
        items(newsItems) { InfoCard(it) }
    }
}

@Composable
private fun HeroCard(t: Strings) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF005B4F), Color(0xFF0E4F46), Color(0xFFDB7C26))))
                .padding(20.dp)
        ) {
            Column {
                Text(t.heroTitle, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(t.heroSubtitle, color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
}

@Composable
private fun CategoriesTab(latestItems: List<CatalogItem>) {
    val grouped = latestItems.groupBy { it.category }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (category, itemsInCategory) ->
            item { SectionTitle(category) }
            items(itemsInCategory) { CatalogCard(it) }
        }
    }
}

@Composable
private fun NewsTab(t: Strings, newsItems: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle(t.news) }
        items(newsItems) { InfoCard(it) }
    }
}

@Composable
private fun SearchTab(t: Strings, latestItems: List<CatalogItem>) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = latestItems.filter {
        it.title.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(t.searchHint) },
            leadingIcon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) }
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered) { CatalogCard(it) }
        }
    }
}

@Composable
private fun FeedTab(t: Strings, latestItems: List<CatalogItem>, onUploadCompleted: (CatalogItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var selectedFileName by rememberSaveable { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var itemTitle by rememberSaveable { mutableStateOf("") }
    var itemCategory by rememberSaveable { mutableStateOf("APK") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryFileName(uri, context)
            itemTitle = selectedFileName.substringBeforeLast(".")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle(t.uploadTitle)
        InfoCard(t.uploadInfo)
        OutlinedTextField(value = itemTitle, onValueChange = { itemTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.fileTitle) })
        OutlinedTextField(value = itemCategory, onValueChange = { itemCategory = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.category) })
        Button(
            onClick = { launcher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005B4F))
        ) {
            Icon(Icons.Outlined.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (selectedFileName.isBlank()) t.pickFile else selectedFileName)
        }
        if (uploadProgress > 0f) {
            LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
            Text("${(uploadProgress * 100).toInt()}%")
        }
        Button(
            enabled = selectedUri != null && itemTitle.isNotBlank(),
            onClick = {
                scope.launch {
                    uploadProgress = 0f
                    repeat(20) {
                        delay(90)
                        uploadProgress += 0.05f
                    }
                    selectedUri?.let { uri ->
                        onUploadCompleted(
                            CatalogItem(
                                title = itemTitle,
                                description = t.localPublication,
                                category = itemCategory,
                                author = "Local user",
                                rating = 5f,
                                isApk = selectedFileName.endsWith(".apk", ignoreCase = true)
                            )
                        )
                        if (selectedFileName.endsWith(".apk", ignoreCase = true)) {
                            openApkInstaller(context, uri, t.installUnavailable)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(t.publish)
        }
        if (selectedFileName.endsWith(".apk", ignoreCase = true) && selectedUri != null) {
            OutlinedButton(onClick = { openApkInstaller(context, selectedUri!!, t.installUnavailable) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(t.installApk)
            }
        }
        SectionTitle(t.latestPublications)
        latestItems.forEach { CatalogCard(it) }
    }
}

@Composable
private fun ProfileScreen(
    t: Strings,
    profile: ProfileState,
    onSaveProfile: (ProfileState) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onLogout: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf(profile.email) }
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var avatarUrl by rememberSaveable { mutableStateOf(profile.avatarUrl) }
    var baseUrl by rememberSaveable { mutableStateOf(profile.baseUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle(t.profile)
        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.email) })
        OutlinedTextField(value = nickname, onValueChange = { nickname = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.nickname) })
        OutlinedTextField(value = avatarUrl, onValueChange = { avatarUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.avatarUrl) })
        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(t.baseUrl) })
        Button(onClick = { onSaveProfile(ProfileState(email, nickname, avatarUrl, baseUrl)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(t.save)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onLanguageChange(AppLanguage.UZ) }, modifier = Modifier.weight(1f)) {
                Text("UZ")
            }
            OutlinedButton(onClick = { onLanguageChange(AppLanguage.RU) }, modifier = Modifier.weight(1f)) {
                Text("RU")
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text(t.logout)
        }
    }
}

@Composable
private fun CatalogCard(item: CatalogItem) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(item.description, color = Color(0xFF605A55))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F1EA))) {
                    Text(item.category, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Star, contentDescription = null, tint = Color(0xFFDB7C26), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(item.rating.toString())
            }
            Text(item.author, color = Color(0xFF005B4F), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(text, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
}

@Composable
private fun CategoryRow(items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { label ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2EA))
            ) {
                Text(label, modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

private fun queryFileName(uri: Uri, context: Context): String {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "selected_file"
    } ?: "selected_file"
}

private fun openApkInstaller(context: Context, uri: Uri, errorText: String) {
    runCatching {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }.onFailure {
        Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
    }
}

private data class Strings(
    val loading: String,
    val welcomeTitle: String,
    val authSubtitle: String,
    val email: String,
    val password: String,
    val login: String,
    val register: String,
    val needAccount: String,
    val haveAccount: String,
    val invalidLogin: String,
    val heroTitle: String,
    val heroSubtitle: String,
    val categories: String,
    val latestPublications: String,
    val news: String,
    val searchHint: String,
    val uploadTitle: String,
    val uploadInfo: String,
    val fileTitle: String,
    val category: String,
    val pickFile: String,
    val publish: String,
    val installApk: String,
    val localPublication: String,
    val profile: String,
    val nickname: String,
    val avatarUrl: String,
    val baseUrl: String,
    val save: String,
    val logout: String,
    val installUnavailable: String,
    val searchTab: String,
    val homeTab: String,
    val feedTab: String
) {
    fun tabLabel(tab: MainTab): String = when (tab) {
        MainTab.NEWS -> news
        MainTab.SEARCH -> searchTab
        MainTab.HOME -> homeTab
        MainTab.CATEGORIES -> categories
        MainTab.FEED -> feedTab
    }
}

private fun texts(language: AppLanguage): Strings {
    return when (language) {
        AppLanguage.UZ -> Strings(
            loading = "Yuklanmoqda...",
            welcomeTitle = "Xush kelibsiz",
            authSubtitle = "Email va parol orqali tizimga kiring yoki ro'yxatdan o'ting.",
            email = "Email",
            password = "Parol",
            login = "Kirish",
            register = "Ro'yxatdan o'tish",
            needAccount = "Akkaunt yo'qmi?",
            haveAccount = "Akkaunt bormi?",
            invalidLogin = "Email yoki parol noto'g'ri",
            heroTitle = "O'yin fayllari va APK'lar bir joyda",
            heroSubtitle = "Lokal server manzilini profil bo'limidan xohlagan payt o'zgartiring.",
            categories = "Kategoriyalar",
            latestPublications = "Oxirgi publikatsiyalar",
            news = "Yangiliklar",
            searchHint = "Fayl yoki kategoriya qidiring",
            uploadTitle = "Fayl yuklash",
            uploadInfo = "Lokal rejimda fayl tanlanadi, progress ko'rsatiladi va APK bo'lsa o'rnatish tugmasi chiqadi.",
            fileTitle = "Fayl nomi",
            category = "Kategoriya",
            pickFile = "Faylni tanlash",
            publish = "Publikatsiya qilish",
            installApk = "APK o'rnatish",
            localPublication = "Lokal publikatsiya qilindi",
            profile = "Profil",
            nickname = "Nik",
            avatarUrl = "Profil rasmi URL",
            baseUrl = "Server URL",
            save = "Saqlash",
            logout = "Chiqish",
            installUnavailable = "APK o'rnatishni ochib bo'lmadi",
            searchTab = "Qidiruv",
            homeTab = "Bosh",
            feedTab = "Lenta"
        )
        AppLanguage.RU -> Strings(
            loading = "Загрузка...",
            welcomeTitle = "Добро пожаловать",
            authSubtitle = "Вход или регистрация по email и паролю.",
            email = "Email",
            password = "Пароль",
            login = "Войти",
            register = "Регистрация",
            needAccount = "Нет аккаунта?",
            haveAccount = "Уже есть аккаунт?",
            invalidLogin = "Неверный email или пароль",
            heroTitle = "Игровые файлы и APK в одном месте",
            heroSubtitle = "Локальный адрес сервера можно менять в профиле в любой момент.",
            categories = "Категории",
            latestPublications = "Последние публикации",
            news = "Новости",
            searchHint = "Поиск файла или категории",
            uploadTitle = "Загрузка файла",
            uploadInfo = "В локальном режиме выбирается файл, показывается прогресс и для APK доступна установка.",
            fileTitle = "Название файла",
            category = "Категория",
            pickFile = "Выбрать файл",
            publish = "Опубликовать",
            installApk = "Установить APK",
            localPublication = "Локальная публикация завершена",
            profile = "Профиль",
            nickname = "Ник",
            avatarUrl = "URL аватара",
            baseUrl = "URL сервера",
            save = "Сохранить",
            logout = "Выйти",
            installUnavailable = "Не удалось открыть установку APK",
            searchTab = "Поиск",
            homeTab = "Главная",
            feedTab = "Лента"
        )
    }
}
