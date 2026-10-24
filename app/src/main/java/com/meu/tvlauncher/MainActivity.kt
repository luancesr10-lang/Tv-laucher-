package com.meu.tvlauncher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {

    private val refreshTick = mutableIntStateOf(0)
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Quando o botão Home é apertado com o launcher já aberto, volta para o Início.
        addOnNewIntentListener { homeSignal.intValue = homeSignal.intValue + 1 }
        setContent { LauncherApp(refreshTick.intValue, homeSignal.intValue) }
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue = refreshTick.intValue + 1 // recarrega a lista de apps
    }
}

// ---------------------------------------------------------------------------
// Dados
// ---------------------------------------------------------------------------

data class AppEntry(
    val label: String,
    val packageName: String,
    val intent: Intent,
    val icon: ImageBitmap?,
)

data class Fav(val name: String, val packages: List<String>, val colors: List<Color>)

val FAVS = listOf(
    Fav("Netflix", listOf("com.netflix.ninja"), listOf(Color(0xFF3A0A12), Color(0xFF8A1424))),
    Fav("YouTube", listOf("com.google.android.youtube.tv"), listOf(Color(0xFF2A1A3A), Color(0xFF5A3A6A))),
    Fav("Prime Video", listOf("com.amazon.amazonvideo.livingroom"), listOf(Color(0xFF0A2A4A), Color(0xFF1F6FB5))),
    Fav("Disney+", listOf("com.disney.disneyplus"), listOf(Color(0xFF1A1050), Color(0xFF5A3AD0))),
    Fav("Max", listOf("com.wbd.stream", "com.hbo.hbonow"), listOf(Color(0xFF2A1A9A), Color(0xFF4A2AF0))),
    Fav("Spotify", listOf("com.spotify.tv.android"), listOf(Color(0xFF0A2A1A), Color(0xFF1A5A3A))),
)

// Regiões do cenário usadas como miniaturas em "Em destaque" (x, y de 0 a 1)
val THUMB_ORIGINS = listOf(
    0.62f to 0.30f, 0.72f to 0.62f, 0.25f to 0.30f,
    0.50f to 0.72f, 0.85f to 0.25f, 0.40f to 0.55f,
)

val STREAMING_PACKAGES: Set<String> =
    FAVS.flatMap { it.packages }.toSet() + setOf(
        "com.globo.globotv",
        "com.apple.atve.androidtv.appletv",
    )

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Início", Icons.Filled.Home),
    APPS("Apps", Icons.Filled.Apps),
    MOVIES("Filmes e Séries", Icons.Filled.Movie),
    LIVE("TV ao vivo", Icons.Filled.LiveTv),
    MINE("Meus conteúdos", Icons.Filled.Bookmark),
    SETTINGS("Configurações", Icons.Filled.SettingsIcon),
}

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val result = LinkedHashMap<String, AppEntry>()
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(query, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == ctx.packageName || result.containsKey(pkg)) continue
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: continue
            val label = info.loadLabel(pm).toString()
            val icon = runCatching { info.loadIcon(pm).toBitmap(160, 160).asImageBitmap() }.getOrNull()
            result[pkg] = AppEntry(label, pkg, launch, icon)
        }
    }
    return result.values.sortedBy { it.label.lowercase() }
}

fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(this, "Não foi possível abrir", Toast.LENGTH_SHORT).show()
    }
}

fun Context.openStore(packageName: String) {
    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
}

fun Context.openLiveTv() {
    val pm = packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage("com.google.android.tv")
        ?: pm.getLaunchIntentForPackage("com.google.android.tv")
        ?: Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
    safeStart(intent)
}

// ---------------------------------------------------------------------------
// Componentes básicos
// ---------------------------------------------------------------------------

@Composable
fun T(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    idleBorder: Color = Color(0x33FFFFFF),
    idleWidth: Dp = 1.dp,
    glow: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "scale")
    Box(
        modifier
            .then(
                if (glow != null) Modifier.shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
                else Modifier,
            )
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .border(if (focused) 3.dp else idleWidth, if (focused) Color.White else idleBorder, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        content(focused)
    }
}

@Composable
fun Wallpaper(modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    val ctx = LocalContext.current
    // Se existir res/drawable-nodpi/wallpaper.jpg (ou .png), ele é usado como fundo.
    val id = remember { ctx.resources.getIdentifier("wallpaper", "drawable", ctx.packageName) }
    if (id != 0) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = alignment,
        )
    } else {
        Box(
            modifier.background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0418), Color(0xFF2B0F63), Color(0xFF12072E)),
                ),
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Tela principal
// ---------------------------------------------------------------------------

@Composable
fun LauncherApp(tick: Int, homeSignal: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var pins by remember { mutableStateOf(prefs.getStringSet("pins", emptySet()) ?: emptySet()) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(tick) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    LaunchedEffect(homeSignal) { screen = Screen.HOME }
    LaunchedEffect(Unit) {
        delay(400)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler(enabled = true) { screen = Screen.HOME }

    fun openApp(app: AppEntry) = ctx.safeStart(Intent(app.intent))
    fun togglePin(app: AppEntry) {
        val updated = pins.toMutableSet()
        val pinned = if (updated.remove(app.packageName)) false else updated.add(app.packageName)
        pins = updated
        prefs.edit().putStringSet("pins", updated).apply()
        Toast.makeText(
            ctx,
            if (pinned) "${app.label} fixado em Meus conteúdos" else "${app.label} removido de Meus conteúdos",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Wallpaper(Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x22000000), Color(0x77000000))),
            ),
        )
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)) {
            TopBar()
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Sidebar(screen, firstFocus) { target ->
                    when (target) {
                        Screen.LIVE -> ctx.openLiveTv()
                        Screen.SETTINGS -> ctx.safeStart(Intent(Settings.ACTION_SETTINGS))
                        else -> screen = target
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (screen) {
                        Screen.HOME -> HomeContent(
                            apps = apps,
                            onOpen = ::openApp,
                            onAllApps = { screen = Screen.APPS },
                        )
                        Screen.APPS -> AppGrid(
                            title = "Apps",
                            list = apps,
                            pins = pins,
                            emptyMessage = "Carregando apps...",
                            onOpen = ::openApp,
                            onPin = ::togglePin,
                        )
                        Screen.MOVIES -> AppGrid(
                            title = "Filmes e Séries",
                            list = apps.filter { it.packageName in STREAMING_PACKAGES },
                            pins = pins,
                            emptyMessage = "Nenhum app de streaming encontrado.",
                            onOpen = ::openApp,
                            onPin = ::togglePin,
                        )
                        Screen.MINE -> AppGrid(
                            title = "Meus conteúdos",
                            list = apps.filter { it.packageName in pins },
                            pins = pins,
                            emptyMessage = "Vazio por enquanto. Em Apps, segure o botão OK sobre um app para fixá-lo aqui.",
                            onOpen = ::openApp,
                            onPin = ::togglePin,
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar() {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            time = fmt.format(Date())
            delay(15_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusCard(Modifier.size(32.dp), CircleShape, onClick = {
            ctx.safeStart(Intent(Intent.ACTION_ASSIST))
        }) {
            Image(
                Icons.Filled.Search, null,
                Modifier.align(Alignment.Center).size(18.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        Spacer(Modifier.width(10.dp))
        FocusCard(Modifier.size(32.dp), CircleShape, onClick = {
            ctx.safeStart(Intent(Settings.ACTION_SETTINGS))
        }) {
            Image(
                Icons.Filled.SettingsIcon, null,
                Modifier.align(Alignment.Center).size(18.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        Spacer(Modifier.width(14.dp))
        T(time, 18.sp, weight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Menu lateral
// ---------------------------------------------------------------------------

@Composable
fun Sidebar(selected: Screen, firstFocus: FocusRequester, onSelect: (Screen) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(170.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0x88120828))
            .border(1.dp, Color(0x668A3DFF), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Screen.values().forEach { s ->
            MenuItem(
                screen = s,
                selected = s == selected,
                focusRequester = if (s == Screen.HOME) firstFocus else null,
                onClick = { onSelect(s) },
            )
        }
    }
}

@Composable
fun MenuItem(screen: Screen, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val bg: Brush = when {
        selected -> Brush.horizontalGradient(listOf(Color(0xFF7B2FFF), Color(0xFF4A1FA8)))
        focused -> SolidColor(Color(0x44FFFFFF))
        else -> SolidColor(Color.Transparent)
    }
    val base = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        base
            .fillMaxWidth()
            .height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .combinedClickableSimple(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            screen.icon, null,
            Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(Color.White),
        )
        Spacer(Modifier.width(10.dp))
        T(screen.title, 13.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

// ---------------------------------------------------------------------------
// Início
// ---------------------------------------------------------------------------

@Composable
fun HomeContent(apps: List<AppEntry>, onOpen: (AppEntry) -> Unit, onAllApps: () -> Unit) {
    val ctx = LocalContext.current
    val favPackages = remember { FAVS.flatMap { it.packages }.toSet() }
    val others = apps.filter { it.packageName !in favPackages }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hero(onClick = {
            val firstInstalled = FAVS.firstNotNullOfOrNull { fav ->
                apps.firstOrNull { it.packageName in fav.packages }
            }
            if (firstInstalled != null) onOpen(firstInstalled) else onAllApps()
        })

        Row(
            Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FAVS.forEach { fav ->
                val installed = apps.firstOrNull { it.packageName in fav.packages }
                FavCard(
                    fav = fav,
                    installed = installed,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {
                        if (installed != null) onOpen(installed) else ctx.openStore(fav.packages.first())
                    },
                )
            }
            FocusCard(
                Modifier.weight(1f).fillMaxHeight(),
                onClick = onAllApps,
            ) {
                Column(
                    Modifier.fillMaxSize().background(Color(0x55FFFFFF)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        Icons.Filled.Apps, null, Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                    Spacer(Modifier.height(4.dp))
                    T("Todos", 11.sp)
                }
            }
        }

        T("Em destaque", 17.sp, weight = FontWeight.Bold)

        LazyRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            itemsIndexed(others, key = { _, app -> app.packageName }) { index, app ->
                val o = THUMB_ORIGINS[index % THUMB_ORIGINS.size]
                FocusCard(Modifier.width(150.dp).height(90.dp), onClick = { onOpen(app) }) {
                    Wallpaper(
                        Modifier.fillMaxSize().graphicsLayer(
                            scaleX = 2.6f,
                            scaleY = 2.6f,
                            transformOrigin = TransformOrigin(o.first, o.second),
                        ),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0x11000000), Color(0xCC0A0418))),
                        ),
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let { Image(it, null, Modifier.size(28.dp)) }
                        Spacer(Modifier.width(6.dp))
                        T(app.label, 11.sp, modifier = Modifier.weight(1f), weight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun Hero(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    FocusCard(
        Modifier.fillMaxWidth().height(190.dp),
        shape = shape,
        idleBorder = Color(0xFFA855FF),
        idleWidth = 2.dp,
        glow = Color(0xFF9B4DFF),
        onClick = onClick,
    ) {
        Wallpaper(Modifier.fillMaxSize(), alignment = BiasAlignment(0.3f, 0f))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x00000000))),
            ),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
            T("Seu entretenimento", 26.sp, weight = FontWeight.Bold)
            T("em outro nível", 26.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            T("Seus apps e conteúdos favoritos", 13.sp, color = Color(0xDDFFFFFF))
            T("em um só lugar.", 13.sp, color = Color(0xDDFFFFFF))
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 26.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x66FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                Icons.Filled.PlayArrow, null, Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

@Composable
fun FavCard(fav: Fav, installed: AppEntry?, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier, onClick = onClick) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(fav.colors))
                .graphicsLayer(alpha = if (installed != null) 1f else 0.55f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val icon = installed?.icon
            if (icon != null) {
                Image(icon, null, Modifier.size(34.dp))
                Spacer(Modifier.height(4.dp))
            }
            T(fav.name, 11.sp, weight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Grade de apps (Apps, Filmes e Séries, Meus conteúdos)
// ---------------------------------------------------------------------------

@Composable
fun AppGrid(
    title: String,
    list: List<AppEntry>,
    pins: Set<String>,
    emptyMessage: String,
    onOpen: (AppEntry) -> Unit,
    onPin: (AppEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        T(title, 20.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            T(emptyMessage, 14.sp, color = Color(0xCCFFFFFF), maxLines = 3)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                items(list, key = { it.packageName }) { app ->
                    FocusCard(
                        Modifier.fillMaxWidth().height(98.dp),
                        onLongClick = { onPin(app) },
                        onClick = { onOpen(app) },
                    ) {
                        Column(
                            Modifier.fillMaxSize().background(Color(0x55140A2E)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            app.icon?.let { Image(it, null, Modifier.size(44.dp)) }
                            Spacer(Modifier.height(4.dp))
                            T(app.label, 12.sp)
                            if (app.packageName in pins) {
                                T("fixado", 10.sp, color = Color(0xFFBFA2FF))
                            }
                        }
                    }
                }
            }
        }
    }
}
