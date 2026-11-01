package asia.aaii.cinema

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

private val bg = Color(0xFF0B0811)
private val surface = Color(0xFF201626)
private val fg = Color(0xFFF7F5F5)
private val muted = Color(0xFFAEA8B5)
private val red = Color(0xFFFF2148)
private val gold = Color(0xFFFFC761)

private data class CinemaItem(
    val id: String, val title: String, val subtitle: String, val category: String,
    val genre: String, val url: String, val kind: String, val accent: String,
    val badge: String, val language: String, val rights: String, val featured: Boolean,
    val posterUrl: String? = null, val year: String? = null, val credits: String? = null
)

private fun readItems(context: Context): List<CinemaItem> = runCatching {
    val json = context.assets.open("catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    val array = JSONArray(json)
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        CinemaItem(
            id = obj.getString("id"), title = obj.getString("title"),
            subtitle = obj.getString("subtitle"), category = obj.getString("category"),
            genre = obj.getString("genre"), url = obj.getString("url"),
            kind = obj.getString("kind"), accent = obj.getString("accent"),
            badge = obj.getString("badge"), language = obj.getString("language"),
            rights = obj.getString("rights"), featured = obj.getBoolean("featured"),
            posterUrl = obj.optString("posterUrl").takeIf { it.startsWith("https://") },
            year = obj.optString("year").takeIf { it.isNotBlank() && it != "null" },
            credits = obj.optString("credits").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}.getOrDefault(emptyList())

// Public channel/post URLs only; invite links and private /c/ links are intentionally excluded.
private fun safeTelegramUrl(value: String): String? = runCatching {
    val uri = Uri.parse(value.trim())
    if (uri.scheme != "https" || uri.host !in setOf("t.me", "telegram.me") ||
        !uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) return@runCatching null
    val parts = uri.pathSegments
    if (parts.size !in 1..2 ||
        !Regex("[A-Za-z][A-Za-z0-9_]{4,31}").matches(parts[0]) ||
        parts[0].lowercase() in setOf("joinchat", "addlist", "share", "proxy", "login", "boost", "iv") ||
        (parts.size == 2 && !Regex("[0-9]{1,12}").matches(parts[1]))) return@runCatching null
    "https://t.me/" + parts.joinToString("/")
}.getOrNull()

private val tgGenres = listOf("Movies", "Short Films", "Documentary", "Discovery", "History", "TV", "Other")

private data class TelegramImportBatch(
    val items: List<CinemaItem>,
    val duplicates: Int,
    val rejected: Int
)

private fun readLimitedText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > 1_000_000) return@runCatching null
            output.write(buffer, 0, count)
        }
        String(output.toByteArray(), Charsets.UTF_8)
    }
}.getOrNull()

private fun parseCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when (c) {
            '"' -> {
                if (quoted && i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else {
                    quoted = !quoted
                }
            }
            ',' -> if (quoted) cell.append(c) else {
                cells.add(cell.toString().trim())
                cell.setLength(0)
            }
            '\n', '\r' -> if (quoted) cell.append(c) else {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                cells.add(cell.toString().trim())
                if (cells.any { it.isNotBlank() }) rows.add(cells)
                cells = mutableListOf()
                cell.setLength(0)
            }
            else -> cell.append(c)
        }
        i++
    }
    cells.add(cell.toString().trim())
    if (cells.any { it.isNotBlank() }) rows.add(cells)
    return rows
}

private fun firstJsonText(obj: JSONObject, vararg keys: String): String {
    for (key in keys) {
        val value = obj.optString(key).trim()
        if (value.isNotEmpty() && value != "null") return value
    }
    return ""
}

/**
 * Imports public Telegram channel/post references only.
 * Supported CSV headers: title,url,genre (name/link/category aliases also work).
 * Supported JSON: an array, { "items": [...] }, { "links": [...] }, or one record.
 * Private t.me/c links and invite links are rejected by safeTelegramUrl().
 */
private fun parseTelegramImport(source: String, existingUrls: Set<String>): TelegramImportBatch {
    val candidates = mutableListOf<Triple<String, String, String>>()
    var rejected = 0
    val trimmed = source.trim().removePrefix("\uFEFF").trim()
    if (trimmed.isEmpty()) return TelegramImportBatch(emptyList(), 0, 0)

    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("items") ?: root.optJSONArray("links") ?: JSONArray().apply {
                if (root.has("url") || root.has("link") || root.has("telegramUrl")) put(root)
            }
        }
        val limit = minOf(array.length(), 500)
        if (array.length() > limit) rejected += array.length() - limit
        for (index in 0 until limit) {
            val obj = array.optJSONObject(index)
            if (obj == null) {
                rejected++
                continue
            }
            candidates.add(
                Triple(
                    firstJsonText(obj, "title", "name", "channel", "channel_title"),
                    firstJsonText(obj, "url", "link", "telegramUrl", "telegram_url"),
                    firstJsonText(obj, "genre", "category")
                )
            )
        }
    } else {
        val rows = parseCsvRows(trimmed)
        if (rows.isEmpty()) return TelegramImportBatch(emptyList(), 0, 0)
        val headers = rows.first().map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        val titleIndex = headers.indexOfFirst { it in setOf("title", "name", "channel", "channel_title") }
        val urlIndex = headers.indexOfFirst { it in setOf("url", "link", "telegramurl", "telegram_url") }
        val genreIndex = headers.indexOfFirst { it in setOf("genre", "category") }
        if (titleIndex < 0 || urlIndex < 0) {
            return TelegramImportBatch(emptyList(), 0, (rows.size - 1).coerceAtLeast(1))
        }
        val dataRows = rows.drop(1)
        val limit = minOf(dataRows.size, 500)
        if (dataRows.size > limit) rejected += dataRows.size - limit
        for (row in dataRows.take(limit)) {
            candidates.add(
                Triple(
                    row.getOrNull(titleIndex).orEmpty(),
                    row.getOrNull(urlIndex).orEmpty(),
                    row.getOrNull(genreIndex).orEmpty()
                )
            )
        }
    }

    val seen = existingUrls.toMutableSet()
    val imported = mutableListOf<CinemaItem>()
    var duplicates = 0
    for ((rawTitle, rawUrl, rawGenre) in candidates) {
        val title = rawTitle.trim().take(85)
        val url = safeTelegramUrl(rawUrl)
        if (title.isBlank() || url == null) {
            rejected++
            continue
        }
        if (!seen.add(url)) {
            duplicates++
            continue
        }
        val genre = tgGenres.firstOrNull { it.equals(rawGenre.trim(), ignoreCase = true) } ?: "Other"
        imported.add(
            CinemaItem(
                id = "tg-" + UUID.randomUUID(),
                title = title,
                subtitle = "Imported public Telegram channel / post",
                category = "Telegram",
                genre = genre,
                url = url,
                kind = "link",
                accent = "#2796B2",
                badge = "TELEGRAM",
                language = "မြန်မာ / English",
                rights = "External link only · streaming rights not verified",
                featured = false
            )
        )
    }
    return TelegramImportBatch(imported, duplicates, rejected)
}

private fun normalizeSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun readTelegramLinks(context: Context): List<CinemaItem> = runCatching {
    val prefs = context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE)
    val records = JSONArray(prefs.getString("telegram_links", "[]") ?: "[]")
    (0 until records.length()).mapNotNull { i ->
        val o = records.optJSONObject(i) ?: return@mapNotNull null
        val url = safeTelegramUrl(o.optString("url")) ?: return@mapNotNull null
        val title = o.optString("title").trim().take(85)
        if (title.isEmpty()) return@mapNotNull null
        val rawGenre = o.optString("genre")
        CinemaItem(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = title, subtitle = "Public Telegram channel / post",
            category = "Telegram", genre = rawGenre.takeIf { it in tgGenres } ?: "Other",
            url = url, kind = "link", accent = "#2796B2", badge = "TELEGRAM",
            language = "မြန်မာ / English", rights = "External link only · streaming rights not verified",
            featured = false
        )
    }
}.getOrDefault(emptyList())


private fun readRecentIds(context: Context): List<String> = runCatching {
    val prefs = context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE)
    val arr = JSONArray(prefs.getString("recent_ids", "[]") ?: "[]")
    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }.distinct().take(12)
}.getOrDefault(emptyList())

private fun persistRecentIds(context: Context, ids: List<String>) {
    val arr = JSONArray()
    ids.distinct().take(12).forEach { arr.put(it) }
    context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE)
        .edit().putString("recent_ids", arr.toString()).apply()
}

private fun readWatchProgress(context: Context): Map<String, Long> = runCatching {
    val prefs = context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE)
    val obj = JSONObject(prefs.getString("watch_progress", "{}") ?: "{}")
    val out = mutableMapOf<String, Long>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = obj.optLong(key, 0L)
        if (value > 0L) out[key] = value
    }
    out
}.getOrDefault(emptyMap())

private fun persistWatchProgress(context: Context, progress: Map<String, Long>) {
    val obj = JSONObject()
    progress.forEach { (id, position) -> if (position > 0L) obj.put(id, position) }
    context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE)
        .edit().putString("watch_progress", obj.toString()).apply()
}

private fun persistTelegramLinks(context: Context, links: List<CinemaItem>) {
    val arr = JSONArray()
    links.forEach { item ->
        arr.put(JSONObject().put("id", item.id).put("title", item.title)
            .put("genre", item.genre).put("url", item.url))
    }
    context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE)
        .edit().putString("telegram_links", arr.toString()).apply()
}

private fun accent(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(red)

private fun openOfficial(context: Context, url: String) {
    // Never pass a link reference into the native video player. External links
    // are opened by an Android browser/Telegram intent only.
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull()
    if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
        Toast.makeText(context, "This source link is invalid", Toast.LENGTH_LONG).show()
        return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure {
            Toast.makeText(context, "No app available to open this link", Toast.LENGTH_LONG).show()
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 8, 17)
        window.navigationBarColor = android.graphics.Color.rgb(11, 8, 17)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = red, onPrimary = Color.White,
                background = bg, surface = surface, onSurface = fg,
                onBackground = fg
            )) { CinemaApp() }
        }
    }
}

@Composable
private fun CinemaApp() {
    val context = LocalContext.current
    val builtInCatalog = remember { readItems(context) }
    val prefs = remember { context.getSharedPreferences("aaii_cinema_v1", Context.MODE_PRIVATE) }
    val telegramSources = remember { mutableStateListOf<CinemaItem>().apply { addAll(readTelegramLinks(context)) } }
    val catalog = remember(builtInCatalog, telegramSources.toList()) { builtInCatalog + telegramSources }
    val saved = remember {
        mutableStateListOf<String>().apply { addAll(prefs.getStringSet("saved", emptySet()).orEmpty()) }
    }
    val recentIds = remember { mutableStateListOf<String>().apply { addAll(readRecentIds(context)) } }
    val watchProgress = remember { mutableStateMapOf<String, Long>().apply { putAll(readWatchProgress(context)) } }
    val favoriteGenres = remember {
        mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favorite_genres", emptySet()).orEmpty()) }
    }
    val availableGenres = remember(catalog) { catalog.map { it.genre }.distinct().filter { it.isNotBlank() }.sorted() }
    var tab by remember { mutableStateOf("Home") }
    var preferMyanmar by remember { mutableStateOf(prefs.getBoolean("preferMyanmar", true)) }
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("All") }
    var detail by remember { mutableStateOf<CinemaItem?>(null) }
    var playing by remember { mutableStateOf<CinemaItem?>(null) }
    var showTelegramEditor by remember { mutableStateOf(false) }
    var telegramTitle by remember { mutableStateOf("") }
    var telegramUrl by remember { mutableStateOf("") }
    var telegramGenre by remember { mutableStateOf("Movies") }
    var telegramError by remember { mutableStateOf("") }
    var telegramImportStatus by remember { mutableStateOf("") }

    val telegramImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = readLimitedText(context, uri)
            if (raw == null) {
                telegramImportStatus = "File မဖတ်နိုင်ပါ သို့မဟုတ် 1 MB ထက်ကြီးနေပါသည်။"
            } else {
                val batch = runCatching {
                    parseTelegramImport(raw, telegramSources.map { it.url }.toSet())
                }.getOrElse { TelegramImportBatch(emptyList(), 0, 1) }
                if (batch.items.isNotEmpty()) {
                    telegramSources.addAll(batch.items)
                    persistTelegramLinks(context, telegramSources)
                }
                telegramImportStatus = "Added ${batch.items.size} · Duplicate ${batch.duplicates} · Rejected ${batch.rejected}"
            }
        }
    }

    fun toggle(item: CinemaItem) {
        if (saved.contains(item.id)) saved.remove(item.id) else saved.add(item.id)
        prefs.edit().putStringSet("saved", saved.toSet()).apply()
    }
    fun recordRecent(item: CinemaItem) {
        recentIds.remove(item.id)
        recentIds.add(0, item.id)
        while (recentIds.size > 12) recentIds.removeAt(recentIds.lastIndex)
        persistRecentIds(context, recentIds)
    }
    fun openDetail(item: CinemaItem) {
        recordRecent(item)
        detail = item
    }
    fun toggleFavoriteGenre(value: String) {
        if (favoriteGenres.contains(value)) favoriteGenres.remove(value) else favoriteGenres.add(value)
        prefs.edit().putStringSet("favorite_genres", favoriteGenres.toSet()).apply()
    }
    fun updateWatchProgress(item: CinemaItem, positionMs: Long, durationMs: Long) {
        if (durationMs > 0L && positionMs > 10_000L && positionMs < durationMs - 10_000L) {
            watchProgress[item.id] = positionMs
        } else if (durationMs > 0L && positionMs >= durationMs - 10_000L) {
            watchProgress.remove(item.id)
        }
        persistWatchProgress(context, watchProgress.toMap())
    }
    fun play(item: CinemaItem) {
        recordRecent(item)
        if (item.kind == "stream") playing = item else openOfficial(context, item.url)
        detail = null
    }

    BackHandler(enabled = playing != null) { playing = null }
    BackHandler(enabled = playing == null && detail == null && tab != "Home") {
        tab = "Home"; query = ""; genre = "All"
    }
    if (playing != null) {
        val currentItem = playing!!
        PlayerScreen(
            item = currentItem,
            initialPositionMs = watchProgress[currentItem.id] ?: 0L,
            onProgress = { position, duration -> updateWatchProgress(currentItem, position, duration) },
            onBack = { playing = null }
        )
        return
    }

    Scaffold(containerColor = bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF11131D), tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth()) {
                listOf(
                    Triple("Home", "Home", Icons.Filled.Home),
                    Triple("Explore", "Explore", Icons.Filled.GridView),
                    Triple("Live TV", "TV", Icons.Filled.Tv),
                    Triple("Telegram", "Telegram", Icons.Filled.Send),
                    Triple("Saved", "My List", Icons.Filled.Bookmark),
                    Triple("Profile", "Profile", Icons.Filled.Person)
                ).forEach { (key, label, icon) ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = tab == key,
                        onClick = { tab = key; query = ""; genre = "All" },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                        label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, selectedTextColor = Color.White,
                            indicatorColor = red,
                            unselectedIconColor = muted, unselectedTextColor = muted
                        )
                    )
                }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.cinema_mark),
                        contentDescription = "AAII CINEMA Logo",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("AAII CINEMA", color = gold, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp)
                        Text("MOVIES · TV · STORIES", fontSize = 8.sp, color = muted, letterSpacing = 1.7.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { tab = "Search"; query = ""; genre = "All" }) {
                        Icon(Icons.Filled.Search, contentDescription = "Open Search", tint = fg)
                    }
                    Text("V2.4", color = gold, fontSize = 10.sp)
                }
            }

            if (tab == "Explore") {
                ExploreNativeScreen(onChoose = { next ->
                    val parts = next.split(":", limit = 2)
                    tab = parts.first(); query = ""; genre = parts.getOrNull(1) ?: "All"
                })
            } else if (tab == "Profile") {
                ProfileNativeScreen(
                    savedCount = saved.size, itemCount = catalog.size, preferMyanmar = preferMyanmar,
                    favoriteGenres = favoriteGenres.toList(), availableGenres = availableGenres,
                    onToggleGenre = ::toggleFavoriteGenre,
                    onLang = { preferMyanmar = it; prefs.edit().putBoolean("preferMyanmar", it).apply() },
                    onSaved = { tab = "Saved" }
                )
            } else {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = muted) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                },
                placeholder = { Text("Movies, TV, History, Discovery ရှာရန်", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(55.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = surface, unfocusedContainerColor = surface,
                    focusedBorderColor = red, unfocusedBorderColor = Color(0xFF252736)
                ), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = fg)
            )
            Spacer(Modifier.height(12.dp))
            if (tab == "Telegram") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Public channel references · private /c/ links are not imported", color = muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                telegramImportStatus = ""
                                telegramImportLauncher.launch(arrayOf("text/*", "application/json"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Import CSV / JSON", fontSize = 10.sp, maxLines = 1) }
                        OutlinedButton(
                            onClick = { showTelegramEditor = true; telegramError = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Icon(Icons.Filled.Add, null, Modifier.size(15.dp)); Text(" Add Link", fontSize = 10.sp) }
                    }
                    if (telegramImportStatus.isNotBlank()) {
                        Text(telegramImportStatus, color = gold, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }

            val source = when (tab) {
                "Home" -> catalog
                "Saved" -> catalog.filter { saved.contains(it.id) }
                "Search" -> catalog
                "Movies" -> catalog.filter { it.category == "Movies" || (it.category == "Telegram" && it.genre.endsWith("Series")) }
                else -> catalog.filter { it.category == tab }
            }
            val searchIndex = remember(catalog) {
                catalog.associate { item ->
                    item.id to normalizeSearch(
                        listOf(item.title, item.subtitle, item.genre, item.language, item.category).joinToString(" ")
                    )
                }
            }
            val normalizedQuery = remember(query) { normalizeSearch(query) }
            val queryTerms = remember(normalizedQuery) { normalizedQuery.split(" ").filter { it.isNotBlank() } }
            val searched = source.filter { item ->
                if (normalizedQuery.isBlank()) true else {
                    val haystack = searchIndex[item.id].orEmpty()
                    haystack.contains(normalizedQuery) || queryTerms.all { haystack.contains(it) }
                }
            }
            val genres = listOf("All") + searched.map { it.genre }.distinct()
            if (tab != "Home" || query.isNotBlank()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres) { label ->
                        FilterChip(
                            selected = genre == label,
                            onClick = { genre = label },
                            label = { Text(label, fontSize = 11.sp) },
                            shape = RoundedCornerShape(30.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = red, selectedLabelColor = Color.White,
                                containerColor = surface, labelColor = muted
                            ), border = BorderStroke(1.dp, if (genre == label) red else Color(0xFF30313D))
                        )
                    }
                }
            }
            val visible = searched.filter { genre == "All" || it.genre == genre }
            if (query.isNotBlank() || tab != "Home") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    item {
                        Text(
                            when (tab) {
                                "Movies" -> "ရုပ်ရှင်နှင့် ဇာတ်လမ်းတွဲများ"
                                "Live TV" -> "TV Channel Directory"
                                "Shorts" -> "Short Films & Movie Recap"
                                "YouTube" -> "Documentary • Discovery • History"
                                "Telegram" -> "Telegram Series & Channel References"
                                "Saved" -> "ကျွန်ုပ်၏ စာရင်း"
                                "Search" -> "Movie & Channel Search"
                                else -> "Search Results"
                            }, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        Text("${visible.size} channels & titles", color = muted, fontSize = 11.sp)
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text("မတွေ့ရှိပါ။ အခြားအမည်တစ်ခုဖြင့် ရှာကြည့်ပါ။",
                                color = muted, modifier = Modifier.padding(vertical = 60.dp))
                        }
                    } else {
                        items(visible.chunked(2)) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { item ->
                                    Box(Modifier.weight(1f)) {
                                        Poster(item, saved.contains(item.id),
                                            onClick = { openDetail(item) },
                                            onSave = { toggle(item) })
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                val recentlyViewed = recentIds.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
                val continueWatching = recentlyViewed.filter { it.kind == "stream" && (watchProgress[it.id] ?: 0L) > 0L }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 25.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    item {
                        Hero(
                            onExplore = { tab = "Movies" },
                            onLive = { tab = "Live TV" }
                        )
                    }
                    item { FavoriteGenreRail(availableGenres, favoriteGenres.toList(), ::toggleFavoriteGenre) }
                    if (continueWatching.isNotEmpty()) {
                        item { HomeRail("Continue Watching · ဆက်လက်ကြည့်ရန်", continueWatching, saved, { openDetail(it) }, ::toggle) }
                    } else {
                        item { ContinueWatchingEmpty() }
                    }
                    if (recentlyViewed.isNotEmpty()) {
                        item { HomeRail("Recently Viewed · မကြာသေးမီက ကြည့်ခဲ့သည်", recentlyViewed, saved, { openDetail(it) }, ::toggle) }
                    }
                    item { HomeRail("မြန်မာရုပ်ရှင်နှင့် Recap Channels", catalog.filter { it.genre == "Myanmar" || it.genre == "Myanmar Recaps" }, saved, { openDetail(it) }, ::toggle) }
                    item { HomeRail("LIVE TV · Official Channels", catalog.filter { it.category == "Live TV" }, saved, { openDetail(it) }, ::toggle) }
                    item { HomeRail("Short Films & Movie Recaps", catalog.filter { it.category == "Shorts" }, saved, { openDetail(it) }, ::toggle) }
                    item { HomeRail("Discovery • History • Nature", catalog.filter { it.category == "YouTube" }, saved, { openDetail(it) }, ::toggle) }
                    if (catalog.any { it.category == "Telegram" }) {
                        item { HomeRail("Korea Series · Telegram References", catalog.filter { it.category == "Telegram" && it.genre == "Korea Series" }.take(14), saved, { openDetail(it) }, ::toggle) }
                        item { HomeRail("English Series · Telegram References", catalog.filter { it.category == "Telegram" && it.genre == "English Series" }.take(14), saved, { openDetail(it) }, ::toggle) }
                        item { HomeRail("Japan Series · Telegram References", catalog.filter { it.category == "Telegram" && it.genre == "Japan Series" }.take(14), saved, { openDetail(it) }, ::toggle) }
                    }
                    item { HomeRail("Open Films & Player Test", catalog.filter { it.category == "Movies" && it.genre != "Myanmar" }, saved, { openDetail(it) }, ::toggle) }
                    item {
                        Text("All video rights belong to their respective creators. TV/YouTube entries open official source pages. In-app playback is for licensed streams only.",
                            color = muted, fontSize = 11.sp, lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
            }
        }
    }
    detail?.let { item ->
        Dialog(onDismissRequest = { detail = null }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1D1D29)).padding(20.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(168.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(accent(item.accent), surface))),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.posterUrl.isNullOrBlank()) {
                        AsyncImage(model = item.posterUrl, contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.cinema_logo), error = painterResource(R.drawable.cinema_logo))
                    } else {
                        Icon(Icons.Filled.PlayCircle, null, modifier = Modifier.size(60.dp), tint = Color.White.copy(alpha = .85f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(item.title, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Spacer(Modifier.height(7.dp))
                Text(item.subtitle, color = muted, fontSize = 13.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(12.dp))
                Text("${item.genre}  ·  ${item.language}  ·  ${item.badge}", color = red, fontSize = 11.sp)
                item.year?.let { Text("Year · $it", color = muted, fontSize = 11.sp) }
                item.credits?.let { Text("Credits · $it", color = muted, fontSize = 11.sp) }
                Spacer(Modifier.height(8.dp))
                Text("Rights / Source: ${item.rights}", color = muted, fontSize = 11.sp)
                if (item.id.startsWith("hub-")) {
                    Text("Imported source reference only. t.me/c is often members-only; video availability and distribution rights have not been verified.", color = Color(0xFFFFCBAA), fontSize = 11.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(21.dp))
                Button(
                    onClick = { play(item) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (item.kind == "stream") Icons.Filled.PlayArrow else Icons.Filled.OpenInNew, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (item.kind == "stream") "Play in App" else if (item.id.startsWith("hub-")) "Open Telegram (member access)" else "Open Source")
                }
                if (item.category == "Telegram" && telegramSources.any { it.id == item.id }) {
                    TextButton(onClick = {
                        telegramSources.removeAll { it.id == item.id }
                        saved.remove(item.id)
                        persistTelegramLinks(context, telegramSources)
                        prefs.edit().putStringSet("saved", saved.toSet()).apply()
                        detail = null
                    }) { Text("Remove Telegram Link", color = Color(0xFFFFB3B3)) }
                }
                OutlinedButton(onClick = { toggle(item) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(if (saved.contains(item.id)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (saved.contains(item.id)) "Remove from My List" else "Add to My List")
                }
                TextButton(onClick = { detail = null }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
    if (showTelegramEditor) {
        Dialog(onDismissRequest = { showTelegramEditor = false }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1D1D29)).padding(18.dp)
            ) {
                Text("Add Public Telegram Link", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text("Only t.me/publicchannel or t.me/publicchannel/123. Links open in Telegram/browser; no video copying.",
                    fontSize = 11.sp, color = muted, lineHeight = 17.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = telegramTitle, onValueChange = { telegramTitle = it.take(85) },
                    label = { Text("Channel / Post Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = telegramUrl, onValueChange = { telegramUrl = it.take(300) },
                    label = { Text("https://t.me/channelname") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Category", fontSize = 12.sp, color = muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(tgGenres) { g ->
                        FilterChip(selected = telegramGenre == g,
                            onClick = { telegramGenre = g }, label = { Text(g, fontSize = 10.sp) })
                    }
                }
                if (telegramError.isNotEmpty()) {
                    Text(telegramError, fontSize = 11.sp, color = Color(0xFFFFA6AD))
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val url = safeTelegramUrl(telegramUrl)
                    val title = telegramTitle.trim()
                    telegramError = when {
                        title.isEmpty() -> "Enter a title."
                        url == null -> "Use a valid PUBLIC Telegram channel/post HTTPS link."
                        telegramSources.any { it.url == url } -> "This link is already in your list."
                        else -> ""
                    }
                    if (telegramError.isEmpty() && url != null) {
                        telegramSources.add(CinemaItem(
                            id = "tg-" + UUID.randomUUID(), title = title,
                            subtitle = "Public Telegram channel / post", category = "Telegram",
                            genre = telegramGenre, url = url, kind = "link", accent = "#2796B2",
                            badge = "TELEGRAM", language = "မြန်မာ / English",
                            rights = "External link only · streaming rights not verified", featured = false
                        ))
                        persistTelegramLinks(context, telegramSources)
                        telegramTitle = ""; telegramUrl = ""; showTelegramEditor = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save & Connect") }
                TextButton(onClick = { showTelegramEditor = false }, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }

}

@Composable
private fun FavoriteGenreRail(availableGenres: List<String>, favoriteGenres: List<String>, onToggle: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 19.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Favorite Categories · နှစ်သက်ရာအမျိုးအစား", color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(favoriteGenres.size.toString() + " saved", color = muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(availableGenres) { label ->
                FilterChip(selected = favoriteGenres.contains(label), onClick = { onToggle(label) },
                    label = { Text(label, fontSize = 10.sp) },
                    leadingIcon = {
                        Icon(if (favoriteGenres.contains(label)) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorite " + label, modifier = Modifier.size(14.dp))
                    })
            }
        }
    }
}

@Composable
private fun ContinueWatchingEmpty() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(16.dp))
        .background(surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = gold)
            Spacer(Modifier.width(8.dp))
            Text("Continue Watching", color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text("Pause a licensed in-app film and your playback position will appear here. External YouTube / Telegram links are not tracked as video playback.",
            color = muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun Hero(onExplore: () -> Unit, onLive: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(248.dp).padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(22.dp))
    ) {
        Image(painterResource(R.drawable.cinema_banner), null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color(0xF20B0711), Color(0xB31B0A25), Color(0x22000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("AAII CINEMA • ORIGINAL EXPERIENCE", color = gold, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(7.dp))
            Text("Explore a World of Stories", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)
            Text("ရုပ်ရှင် • တိုက်ရိုက် TV • Documentary", fontSize = 12.sp, color = Color(0xFFF6D7D9))
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExplore, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = red)) {
                    Icon(Icons.Filled.Movie, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Explore", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onLive, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
                    Icon(Icons.Filled.Tv, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("Live TV", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeRail(
    heading: String, entries: List<CinemaItem>, saved: List<String>,
    onOpen: (CinemaItem) -> Unit, onSave: (CinemaItem) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 19.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(heading, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${entries.size} items", color = muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.id }) { item ->
                Box(Modifier.width(145.dp)) {
                    Poster(item, saved.contains(item.id), { onOpen(item) }, { onSave(item) })
                }
            }
        }
    }
}

@Composable
private fun Poster(item: CinemaItem, isSaved: Boolean, onClick: () -> Unit, onSave: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(Brush.linearGradient(listOf(accent(item.accent), Color(0xFF151827))))) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(model = item.posterUrl, contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.cinema_logo), error = painterResource(R.drawable.cinema_logo))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .78f)))))
            } else {
                Icon(when (item.category) {
                    "Live TV" -> Icons.Filled.Tv
                    "Shorts" -> Icons.Filled.SmartDisplay
                    "YouTube" -> Icons.Filled.Public
                    else -> Icons.Filled.Movie
                }, contentDescription = null, tint = Color.White.copy(alpha = .20f),
                    modifier = Modifier.size(76.dp).align(Alignment.Center))
            }
            Text(item.badge.ifBlank { item.genre.uppercase() }, color = Color.White, fontSize = 8.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .45f))
                    .padding(horizontal = 7.dp, vertical = 4.dp))
            Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = .9f),
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(24.dp))
            Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Save", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = .35f)).clickable(onClick = onSave).padding(6.dp).size(16.dp))
            if (item.kind == "stream") {
                Text("PLAY", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(5.dp))
                        .background(red).padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(item.genre + " · " + item.language, fontSize = 10.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlayerScreen(
    item: CinemaItem,
    initialPositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var playbackError by remember(item.url) { mutableStateOf<String?>(null) }
    var showSettings by remember(item.id) { mutableStateOf(false) }
    var subtitleChoice by remember(item.id) { mutableStateOf("Off") }
    var audioChoice by remember(item.id) { mutableStateOf("Auto") }
    var resizeMode by remember(item.id) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val player = remember(item.url) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = "Video could not be played. Check your connection or source rights."
                }
            })
            setMediaItem(MediaItem.fromUri(item.url))
            prepare()
            if (initialPositionMs > 0L) seekTo(initialPositionMs)
            playWhenReady = true
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            onProgress(player.currentPosition, player.duration)
        }
    }
    DisposableEffect(player) {
        onDispose {
            onProgress(player.currentPosition, player.duration)
            player.pause()
            player.release()
        }
    }
    fun applySubtitle(choice: String) {
        subtitleChoice = choice
        val code = when (choice) { "Myanmar" -> "my"; "English" -> "en"; "Japanese" -> "ja"; else -> null }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, code == null)
            .apply { if (code != null) setPreferredTextLanguage(code) }.build()
    }
    fun applyAudio(choice: String) {
        audioChoice = choice
        val code = when (choice) { "Myanmar" -> "my"; "English" -> "en"; "Japanese" -> "ja"; else -> null }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(code).build()
    }
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = fg) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Text("Continue watching · " + item.genre, color = muted, fontSize = 10.sp)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Playback settings", tint = gold) }
        }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; this.resizeMode = resizeMode } },
            update = { it.player = player; it.resizeMode = resizeMode },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
        if (playbackError != null) {
            Text(playbackError.orEmpty(), color = Color(0xFFFFAAB4), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        Text("LICENSED MEDIA · " + item.rights, color = red, fontSize = 10.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        item.credits?.let {
            Text("Attribution: " + it, color = muted, fontSize = 11.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        Text(item.subtitle + "\n" + item.rights, color = muted, fontSize = 13.sp, lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Back to Cinema") }
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Playback Settings") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text("Subtitles", color = gold, fontWeight = FontWeight.Bold)
                    listOf("Off", "Myanmar", "English", "Japanese").forEach { choice ->
                        Row(Modifier.fillMaxWidth().clickable { applySubtitle(choice) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = subtitleChoice == choice, onClick = { applySubtitle(choice) })
                            Text(choice, color = fg, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Audio language", color = gold, fontWeight = FontWeight.Bold)
                    listOf("Auto", "Myanmar", "English", "Japanese").forEach { choice ->
                        Row(Modifier.fillMaxWidth().clickable { applyAudio(choice) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = audioChoice == choice, onClick = { applyAudio(choice) })
                            Text(choice, color = fg, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Screen ratio", color = gold, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
                            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM).forEach { (label, mode) ->
                            FilterChip(selected = resizeMode == mode, onClick = { resizeMode = mode },
                                label = { Text(label, fontSize = 11.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Subtitle/audio options work when the selected stream contains those tracks.",
                        color = muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
        )
    }
}

// SCREEN 02: Explore — category navigation matching the approved 10-screen concept.
@Composable
private fun ExploreNativeScreen(onChoose: (String) -> Unit) {
    val routes = listOf(
        Triple("Movies", "ရုပ်ရှင်များ", Icons.Filled.Movie),
        Triple("Telegram:Korea Series", "Korea Series", Icons.Filled.Send),
        Triple("Telegram:English Series", "English Series", Icons.Filled.Language),
        Triple("Telegram:Japan Series", "Japan Series", Icons.Filled.Language),
        Triple("Live TV", "Live TV", Icons.Filled.Tv),
        Triple("YouTube", "YouTube Channels", Icons.Filled.SmartDisplay),
        Triple("Shorts", "Short Films", Icons.Filled.PlayCircle),
        Triple("YouTube:Documentary", "Documentary", Icons.Filled.Public),
        Triple("YouTube:Discovery", "Discovery · History", Icons.Filled.Explore),
        Triple("Telegram", "Telegram Movie Links", Icons.Filled.Link)
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Explore", color = gold, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("အမျိုးအစားအလိုက် ရုပ်ရှင်နှင့် ချန်နယ်များ ရှာဖွေပါ", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
        }
        items(routes.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (target, label, glyph) ->
                    Column(Modifier.weight(1f).height(129.dp).clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF451831), surface, Color(0xFF111D34))))
                        .clickable { onChoose(target) }.padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(glyph, contentDescription = label, tint = gold, modifier = Modifier.size(34.dp))
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Text("Official channels and Telegram references open in their original apps. Availability may vary.",
                fontSize = 11.sp, color = muted)
        }
    }
}

// SCREEN 10: Account/Profile. Subscription is visually reserved, not a real paid entitlement.
@Composable
private fun ProfileNativeScreen(
    savedCount: Int, itemCount: Int, preferMyanmar: Boolean,
    favoriteGenres: List<String>, availableGenres: List<String>,
    onToggleGenre: (String) -> Unit, onLang: (Boolean) -> Unit, onSaved: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Profile & Settings", color = gold, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("အကောင့်နှင့် App ဆက်တင်များ", color = muted, fontSize = 12.sp)
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF64203D), Color(0xFF16172B)))).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = gold, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("AAII CINEMA Guest", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("FREE • Preview account", color = gold, fontSize = 12.sp)
                    Text("Sign-in service not connected", color = muted, fontSize = 10.sp)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(surface).padding(18.dp)) {
                Text("Membership", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(7.dp))
                Text("Free Preview", color = gold, fontSize = 16.sp)
                Text("Subscription/payment backend not connected. No purchase is available in this build.",
                    color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(surface).padding(16.dp)) {
                Text("Favorite Categories", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("အကြိုက်ဆုံးအမျိုးအစားများကို ရွေးချယ်ပါ", color = muted, fontSize = 11.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(availableGenres) { label ->
                        FilterChip(selected = favoriteGenres.contains(label), onClick = { onToggleGenre(label) },
                            label = { Text(label, fontSize = 10.sp) },
                            leadingIcon = {
                                Icon(if (favoriteGenres.contains(label)) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = null, modifier = Modifier.size(14.dp))
                            })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(surface)
                .clickable(onClick = onSaved).padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = gold)
                    Spacer(Modifier.width(12.dp))
                    Text("My List", color = Color.White)
                }
                Text("$savedCount saved ❯", color = muted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(surface).padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Myanmar UI preference", color = Color.White, fontSize = 14.sp)
                Switch(checked = preferMyanmar, onCheckedChange = onLang)
            }
            Text("UI language toggle is stored locally; full translation requires additional localization resources.",
                color = muted, fontSize = 10.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(surface).padding(18.dp)) {
                Text("Offline catalog · $itemCount items", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("External Telegram / YouTube / TV require connectivity. Offline media downloads are not enabled.",
                    color = muted, fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))
                Text("AAII CINEMA  V2.4", color = gold, fontSize = 12.sp)
            }
        }
    }
}