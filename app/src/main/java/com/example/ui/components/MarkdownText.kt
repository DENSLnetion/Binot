package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * A single saved highlight.
 */
data class HighlightItem(
    val text: String,
    val note: String,
    val line: Int = -1,
    val start: Int = -1,
    val end: Int = -1
)

/** Snapshot of one rendered line's text layout, window position, and how it maps back to the raw line. */
data class LineLayoutInfo(
    val layoutResult: TextLayoutResult,
    val boundsInWindow: Rect,
    val renderedText: String,
    val prefixLen: Int
)

/**
 * Sealed class to handle chunking between Native Compose Text, KaTeX WebViews, and Mermaid WebViews
 */
sealed class MarkdownItem {
    data class NativeLine(val text: String, val lineIndex: Int) : MarkdownItem()
    data class MathBlock(val rawText: String, val startLineIndex: Int) : MarkdownItem()
    data class MermaidBlock(val rawText: String, val startLineIndex: Int) : MarkdownItem()
}

private fun resolveRectToPosition(
    rect: Rect,
    selectedText: String,
    registry: Map<Int, LineLayoutInfo>,
    rawLines: List<String>
): Triple<Int, Int, Int>? {
    if (selectedText.isBlank()) return null
    val anchor = Offset(rect.left, rect.center.y)
    val info = registry.values.firstOrNull { it.boundsInWindow.let { b -> anchor.y in b.top..b.bottom } }
        ?: registry.values.minByOrNull { lineInfo ->
            val b = lineInfo.boundsInWindow
            when {
                anchor.y < b.top -> b.top - anchor.y
                anchor.y > b.bottom -> anchor.y - b.bottom
                else -> 0f
            }
        }
        ?: return null

    val lineIndex = registry.entries.firstOrNull { it.value === info }?.key ?: return null
    val rawLine = rawLines.getOrNull(lineIndex) ?: return null

    val localX = (anchor.x - info.boundsInWindow.left).coerceIn(0f, info.boundsInWindow.width)
    val approxLocalOffset = info.layoutResult.getOffsetForPosition(
        Offset(localX, info.boundsInWindow.height / 2f)
    ).coerceIn(0, info.renderedText.length)
    val approxRawOffset = (approxLocalOffset + info.prefixLen).coerceIn(0, rawLine.length)

    val lowerLine = rawLine.lowercase()
    val lowerSel = selectedText.lowercase()
    var bestStart = -1
    var bestDist = Int.MAX_VALUE
    var searchFrom = 0
    while (true) {
        val idx = lowerLine.indexOf(lowerSel, searchFrom)
        if (idx == -1) break
        val dist = kotlin.math.abs(idx - approxRawOffset)
        if (dist < bestDist) {
            bestDist = dist
            bestStart = idx
        }
        searchFrom = idx + 1
    }
    if (bestStart == -1) return null
    return Triple(lineIndex, bestStart, bestStart + selectedText.length)
}

// Data class untuk membawa KaTeX + mhchem assets
data class KaTeXAssets(val css: String, val js: String, val autoRender: String, val mhchem: String) {
    val isReady get() = js.isNotEmpty() && mhchem.isNotEmpty()
}

// Data class untuk membawa Mermaid.js assets
data class MermaidAssets(val js: String) {
    val isReady get() = js.isNotEmpty()
}

// Komponen Shimmer Elegan
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val color1 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val color2 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

    val brush = Brush.linearGradient(
        colors = listOf(color1, color2, color1),
        start = Offset(offsetX, 0f),
        end = Offset(offsetX + 400f, 400f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXWebView(
    mathContent: String,
    assets: KaTeXAssets,
    textColor: Color,
    fontFamily: FontFamily,
    heightCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    modifier: Modifier = Modifier
) {
    if (!assets.isReady) return

    val hexColor = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
    val cssFont = when (fontFamily) {
        FontFamily.Serif -> "serif"
        FontFamily.Monospace -> "monospace"
        else -> "sans-serif"
    }

    val patchedCss = remember(assets.css) {
        assets.css.replace(Regex("""url\(['"]?(fonts/[^'"")]+)['"]?\)""")) { match ->
            "url('file:///android_asset/katex/${match.groupValues[1]}')"
        }
    }

    val htmlContent = remember(mathContent, hexColor, cssFont) {
        var html = mathContent
        html = html.replace(Regex("^### (.*)$", RegexOption.MULTILINE), "<h4>$1</h4>")
        html = html.replace(Regex("^## (.*)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        html = html.replace(Regex("^# (.*)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        html = html.replace(Regex("^- (.*)$", RegexOption.MULTILINE), "<li>$1</li>")
        html = html.replace(Regex("^[0-9]+\\. (.*)$", RegexOption.MULTILINE), "<li>$1</li>")

        """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
${patchedCss}
body {
    background-color: transparent;
    color: ${hexColor};
    font-family: ${cssFont};
    font-size: 16px;
    line-height: 1.6;
    margin: 0;
    padding: 8px 4px;
    word-wrap: break-word;
    overflow-x: hidden; 
    overflow-y: hidden;
}
.katex-display {
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 6px; 
    -webkit-overflow-scrolling: touch;
}
.katex-display::-webkit-scrollbar {
    height: 4px;
}
.katex-display::-webkit-scrollbar-thumb {
    background: #88888888;
    border-radius: 4px;
}
li { margin-bottom: 4px; }
</style>
</head>
<body>
<div id="math-content">${html}</div>
<script>${assets.js}</script>
<script>${assets.autoRender}</script>
<script>${assets.mhchem}</script>
<script>
document.addEventListener("DOMContentLoaded", function() {
    if (typeof renderMathInElement !== 'undefined') {
        renderMathInElement(document.getElementById('math-content'), {
            delimiters: [
                {left: "$$", right: "$$", display: true},
                {left: "\\[", right: "\\]", display: true},
                {left: "$", right: "$", display: false},
                {left: "\\(", right: "\\)", display: false}
            ],
            throwOnError: false
        });
    }
    setTimeout(function() {
        var el = document.getElementById('math-content');
        var h = el ? el.getBoundingClientRect().height : document.body.scrollHeight;
        if (window.HeightBridge) window.HeightBridge.onHeightReady(Math.ceil(h) + 30);
    }, 500);
});
</script>
</body>
</html>""".trimIndent()
    }

    // STATE CERDAS: Nahan shift layout dan nyalain animasi
    var isRendered by remember(htmlContent) { mutableStateOf(false) }
    var webViewHeightPx by remember(htmlContent) { mutableStateOf(heightCache[htmlContent] ?: -1) }

    val density = LocalDensity.current
    val targetHeightDp = remember(webViewHeightPx) {
        if (webViewHeightPx == -1) 60.dp // Tinggi box reservasi awal (Skeleton Box)
        else with(density) { webViewHeightPx.toDp() }.coerceAtLeast(1.dp)
    }

    // Melar mulus
    val animatedHeight by animateDpAsState(
        targetValue = targetHeightDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "katexHeight"
    )

    // Pudar halus (Crossfade)
    val webViewAlpha by animateFloatAsState(
        targetValue = if (isRendered) 1f else 0.01f, // 0.01f mencegah view mati 100% sehingga tetap ter-render
        animationSpec = tween(400),
        label = "webviewAlpha"
    )

    val capturedHtmlContent = htmlContent
    val capturedHeightCache = heightCache

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight) // Wadah dipatok dengan animasi tinggi melar halus
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(webViewAlpha),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false 
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.defaultTextEncodingName = "utf-8"
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onHeightReady(height: Int) {
                            // Wajib dieksekusi di Main Thread buat update UI
                            Handler(Looper.getMainLooper()).post {
                                val d = ctx.resources.displayMetrics.density
                                val px = (height * d).toInt().coerceAtLeast(1)
                                capturedHeightCache[capturedHtmlContent] = px
                                webViewHeightPx = px
                                isRendered = true // Memicu fade-out Shimmer dan pemanjangan wadah
                            }
                        }
                    }, "HeightBridge")
                    tag = ""
                }
            },
            update = { webView ->
                if (webView.tag != capturedHtmlContent) {
                    webView.tag = capturedHtmlContent
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/katex/",
                        capturedHtmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        // Shimmer Overlay yang numpuk pas di atas webview sebelum jadi
        AnimatedVisibility(
            visible = !isRendered,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            ShimmerBox(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(
    mermaidContent: String,
    assets: MermaidAssets,
    isDarkTheme: Boolean,
    heightCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    modifier: Modifier = Modifier
) {
    if (!assets.isReady) return

    val theme = if (isDarkTheme) "dark" else "default"

    val htmlContent = remember(mermaidContent, theme) {
        """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
body {
    background-color: transparent;
    margin: 0;
    padding: 8px 4px;
    overflow-x: auto;
    overflow-y: hidden;
}
.mermaid-container {
    display: flex;
    justify-content: flex-start;
    min-width: min-content;
}
::-webkit-scrollbar { height: 4px; }
::-webkit-scrollbar-thumb { background: #88888888; border-radius: 4px; }
</style>
</head>
<body>
<div class="mermaid-container">
<pre class="mermaid">${mermaidContent.trim().replace("<", "&lt;").replace(">", "&gt;")}</pre>
</div>
<script>${assets.js}</script>
<script>
document.addEventListener("DOMContentLoaded", function() {
    mermaid.initialize({
        startOnLoad: true,
        theme: '$theme',
        securityLevel: 'loose'
    });
    setTimeout(function() {
        var el = document.querySelector('.mermaid');
        var h = el ? el.getBoundingClientRect().height : document.body.scrollHeight;
        if (window.HeightBridge) window.HeightBridge.onHeightReady(Math.ceil(h) + 40);
    }, 600);
});
</script>
</body>
</html>""".trimIndent()
    }

    var isRendered by remember(htmlContent) { mutableStateOf(false) }
    var webViewHeightPx by remember(htmlContent) { mutableStateOf(heightCache[htmlContent] ?: -1) }

    val density = LocalDensity.current
    val targetHeightDp = remember(webViewHeightPx) {
        if (webViewHeightPx == -1) 120.dp // Tinggi box skeleton yang agak besar untuk diagram
        else with(density) { webViewHeightPx.toDp() }.coerceAtLeast(1.dp)
    }

    val animatedHeight by animateDpAsState(
        targetValue = targetHeightDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "mermaidHeight"
    )

    val webViewAlpha by animateFloatAsState(
        targetValue = if (isRendered) 1f else 0.01f,
        animationSpec = tween(400),
        label = "webviewAlpha"
    )

    val capturedHtmlContent = htmlContent
    val capturedHeightCache = heightCache

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(webViewAlpha),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false 
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.defaultTextEncodingName = "utf-8"
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onHeightReady(height: Int) {
                            Handler(Looper.getMainLooper()).post {
                                val d = ctx.resources.displayMetrics.density
                                val px = (height * d).toInt().coerceAtLeast(1)
                                capturedHeightCache[capturedHtmlContent] = px
                                webViewHeightPx = px
                                isRendered = true
                            }
                        }
                    }, "HeightBridge")
                    tag = ""
                }
            },
            update = { webView ->
                if (webView.tag != capturedHtmlContent) {
                    webView.tag = capturedHtmlContent
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/mermaid/",
                        capturedHtmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        AnimatedVisibility(
            visible = !isRendered,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            ShimmerBox(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp))
        }
    }
}

@Composable
fun MarkdownText(
    text: String, 
    listState: LazyListState,
    highlightsInfo: String? = null,
    onSavedHighlightClick: (text: String, note: String, line: Int, start: Int, end: Int) -> Unit = { _, _, _, _, _ -> },
    onResolveSelection: (resolver: (Rect, String) -> Triple<Int, Int, Int>?) -> Unit = {},
    highlightQuery: String = "", 
    fontFamily: FontFamily = FontFamily.SansSerif, 
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var katexAssets by remember { mutableStateOf(KaTeXAssets("", "", "", "")) }
    var mermaidAssets by remember { mutableStateOf(MermaidAssets("")) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Load KaTeX + mhchem
            val css = try { context.assets.open("katex/katex.min.css").bufferedReader().readText() } catch (e: Exception) { "" }
            val js = try { context.assets.open("katex/katex.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            val ar = try { context.assets.open("katex/auto-render.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            val mhchem = try { context.assets.open("katex/mhchem.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            katexAssets = KaTeXAssets(css, js, ar, mhchem)

            // Load Mermaid
            val mermaidJs = try { context.assets.open("mermaid/mermaid.min.js").bufferedReader().readText() } catch (e: Exception) { "" }
            mermaidAssets = MermaidAssets(mermaidJs)
        }
    }

    val lines = text.split("\n")
    
    val markdownItems = remember(text) {
        val items = mutableListOf<MarkdownItem>()
        var inMathBlock = false
        var mathBlockContent = ""
        var mathBlockStartIndex = -1

        var inMermaidBlock = false
        var mermaidBlockContent = ""
        var mermaidBlockStartIndex = -1

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 1. Logika Tangkap Mermaid Block
            if (inMermaidBlock) {
                if (line.trim().startsWith("```") && !line.trim().startsWith("```mermaid")) {
                    inMermaidBlock = false
                    items.add(MarkdownItem.MermaidBlock(mermaidBlockContent, mermaidBlockStartIndex))
                    mermaidBlockContent = ""
                } else {
                    mermaidBlockContent += if (mermaidBlockContent.isEmpty()) line else "\n" + line
                }
                i++
                continue
            }

            if (line.trim().lowercase().startsWith("```mermaid")) {
                inMermaidBlock = true
                mermaidBlockStartIndex = i
                mermaidBlockContent = "" 
                i++
                continue
            }

            // 2. Logika Tangkap KaTeX Block
            if (inMathBlock) {
                mathBlockContent += "\n" + line
                if (line.trim().endsWith("$$") || line.trim() == "$$") {
                    inMathBlock = false
                    items.add(MarkdownItem.MathBlock(mathBlockContent, mathBlockStartIndex))
                    mathBlockContent = ""
                }
                i++
                continue
            }

            if (line.trim().startsWith("$$")) {
                inMathBlock = true
                mathBlockStartIndex = i
                mathBlockContent = line
                if (line.trim().length > 2 && line.trim().endsWith("$$")) {
                    inMathBlock = false
                    items.add(MarkdownItem.MathBlock(mathBlockContent, mathBlockStartIndex))
                    mathBlockContent = ""
                }
                i++
                continue
            }

            // 3. Logika Teks Native dan KaTeX Inline
            val hasInlineMath = Regex("""\$.+?\$""").containsMatchIn(line)
            if (hasInlineMath) {
                items.add(MarkdownItem.MathBlock(line, i))
            } else {
                items.add(MarkdownItem.NativeLine(line, i))
            }
            i++
        }
        
        if (inMathBlock) items.add(MarkdownItem.MathBlock(mathBlockContent, mathBlockStartIndex))
        if (inMermaidBlock) items.add(MarkdownItem.MermaidBlock(mermaidBlockContent, mermaidBlockStartIndex))
        
        items
    }
    
    val savedHighlights = remember(highlightsInfo) {
        val list = mutableListOf<HighlightItem>()
        if (!highlightsInfo.isNullOrBlank() && highlightsInfo != "[]") {
            try {
                val array = JSONArray(highlightsInfo)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HighlightItem(
                            text = obj.getString("text"),
                            note = obj.getString("note"),
                            line = obj.optInt("line", -1),
                            start = obj.optInt("start", -1),
                            end = obj.optInt("end", -1)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list
    }

    val highlightBgColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer
    val lineRegistry = remember { mutableMapOf<Int, LineLayoutInfo>() }
    val webViewHeightCache = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>() }
    val isDarkTheme = isSystemInDarkTheme()

    LaunchedEffect(text) {
        onResolveSelection { rect, selectedText ->
            resolveRectToPosition(rect, selectedText, lineRegistry, lines)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        items(markdownItems.size, key = { index -> markdownItems[index].let {
            when (it) {
                is MarkdownItem.MathBlock -> "math_${it.startLineIndex}"
                is MarkdownItem.MermaidBlock -> "mermaid_${it.startLineIndex}"
                is MarkdownItem.NativeLine -> "line_${it.lineIndex}"
            }
        }}) { index ->
            when (val item = markdownItems[index]) {
                is MarkdownItem.MathBlock -> {
                    KaTeXWebView(
                        mathContent = item.rawText,
                        assets = katexAssets,
                        textColor = MaterialTheme.colorScheme.onBackground,
                        fontFamily = fontFamily,
                        heightCache = webViewHeightCache,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                is MarkdownItem.MermaidBlock -> {
                    MermaidWebView(
                        mermaidContent = item.rawText,
                        assets = mermaidAssets,
                        isDarkTheme = isDarkTheme,
                        heightCache = webViewHeightCache,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                is MarkdownItem.NativeLine -> {
                    val lineIndex = item.lineIndex
                    val line = item.text
                    val indentSpaces = line.takeWhile { it == ' ' || it == '\t' }.length
                    val trimmedLine = line.trimStart()

                    val lineHighlights = remember(savedHighlights, lineIndex) {
                        savedHighlights.filter { it.line == lineIndex }
                    }
                    val legacyHighlights = remember(savedHighlights) {
                        savedHighlights.filter { it.start < 0 }
                    }

                    when {
                        trimmedLine.startsWith("# ") -> {
                            Text(
                                text = trimmedLine.removePrefix("# ").trim(),
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = fontFamily),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                            )
                        }
                        trimmedLine.startsWith("## ") -> {
                            Text(
                                text = trimmedLine.removePrefix("## ").trim(),
                                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = fontFamily),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                            )
                        }
                        trimmedLine.startsWith("### ") -> {
                            Text(
                                text = trimmedLine.removePrefix("### ").trim(),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamily),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                            val paddingStart = 16.dp + (indentSpaces * 6).dp
                            val prefixLen = indentSpaces + 2
                            Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                                Text(
                                    text = if (indentSpaces > 0) "◦" else "•",
                                    modifier = Modifier.width(24.dp),
                                    style = MaterialTheme.typography.bodyLarge, 
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                BasicMarkdownLine(
                                    text = trimmedLine.substring(2).trim(), 
                                    lineIndex = lineIndex,
                                    prefixLen = prefixLen,
                                    highlightQuery = highlightQuery,
                                    lineHighlights = lineHighlights,
                                    legacyHighlights = legacyHighlights,
                                    onSavedHighlightClick = onSavedHighlightClick,
                                    highlightBgColor = highlightBgColor,
                                    highlightTextColor = highlightTextColor,
                                    fontFamily = fontFamily,
                                    lineRegistry = lineRegistry,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        trimmedLine.matches(Regex("^[0-9]+\\.\\s.*")) -> {
                            val dotIndex = trimmedLine.indexOf(".")
                            val number = trimmedLine.substring(0, dotIndex + 1)
                            val content = trimmedLine.substring(dotIndex + 1).trim()
                            val paddingStart = 16.dp + (indentSpaces * 6).dp
                            val contentStartInTrimmed = trimmedLine.indexOf(content, dotIndex + 1)
                            val prefixLen = indentSpaces + (if (contentStartInTrimmed >= 0) contentStartInTrimmed else dotIndex + 1)
                            
                            Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                                Text(
                                    text = number, 
                                    modifier = Modifier.width(32.dp), 
                                    style = MaterialTheme.typography.bodyLarge, 
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                BasicMarkdownLine(
                                    text = content, 
                                    lineIndex = lineIndex,
                                    prefixLen = prefixLen,
                                    highlightQuery = highlightQuery,
                                    lineHighlights = lineHighlights,
                                    legacyHighlights = legacyHighlights,
                                    onSavedHighlightClick = onSavedHighlightClick,
                                    highlightBgColor = highlightBgColor,
                                    highlightTextColor = highlightTextColor,
                                    fontFamily = fontFamily,
                                    lineRegistry = lineRegistry,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        trimmedLine.isBlank() -> Spacer(modifier = Modifier.height(16.dp))
                        else -> BasicMarkdownLine(
                            text = trimmedLine, 
                            lineIndex = lineIndex,
                            prefixLen = indentSpaces,
                            highlightQuery = highlightQuery,
                            lineHighlights = lineHighlights,
                            legacyHighlights = legacyHighlights,
                            onSavedHighlightClick = onSavedHighlightClick,
                            highlightBgColor = highlightBgColor,
                            highlightTextColor = highlightTextColor,
                            fontFamily = fontFamily,
                            lineRegistry = lineRegistry,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun BasicMarkdownLine(
    text: String, 
    lineIndex: Int,
    prefixLen: Int,
    highlightQuery: String,
    lineHighlights: List<HighlightItem>,
    legacyHighlights: List<HighlightItem>,
    onSavedHighlightClick: (text: String, note: String, line: Int, start: Int, end: Int) -> Unit,
    highlightBgColor: Color,
    highlightTextColor: Color,
    fontFamily: FontFamily,
    lineRegistry: MutableMap<Int, LineLayoutInfo>,
    modifier: Modifier = Modifier
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val pattern = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
        val matches = pattern.findAll(text)
        
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            if (match.groups[1] != null) { 
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groups[1]!!.value)
                }
            } else if (match.groups[2] != null) { 
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groups[2]!!.value)
                }
            } else if (match.groups[3] != null) { 
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groups[3]!!.value)
                }
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex))
        
        val plainString = this.toAnnotatedString().text
        val plainLength = plainString.length

        lineHighlights.forEach { item ->
            val localStart = item.start - prefixLen
            val localEnd = item.end - prefixLen
            if (localStart in 0 until plainLength && localEnd in (localStart + 1)..plainLength) {
                addStyle(
                    style = SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold),
                    start = localStart,
                    end = localEnd
                )
                addStringAnnotation(
                    tag = "SAVED_HIGHLIGHT",
                    annotation = "${item.text}@@KEY@@${item.line}:${item.start}:${item.end}",
                    start = localStart,
                    end = localEnd
                )
            }
        }

        val plainLower = plainString.lowercase()
        legacyHighlights.forEach { item ->
            val wordLower = item.text.lowercase()
            if (wordLower.isBlank()) return@forEach
            var startIndex = plainLower.indexOf(wordLower)
            while (startIndex >= 0) {
                addStyle(
                    style = SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold),
                    start = startIndex,
                    end = startIndex + wordLower.length
                )
                addStringAnnotation(
                    tag = "SAVED_HIGHLIGHT",
                    annotation = "${item.text}@@KEY@@legacy:${item.text}",
                    start = startIndex,
                    end = startIndex + wordLower.length
                )
                startIndex = plainLower.indexOf(wordLower, startIndex + 1)
            }
        }
        
        if (highlightQuery.isNotBlank()) {
            val queryLower = highlightQuery.lowercase()
            var startIndex = plainLower.indexOf(queryLower)
            
            while (startIndex >= 0) {
                addStyle(
                    style = SpanStyle(background = Color.Yellow, color = Color.Black),
                    start = startIndex,
                    end = startIndex + queryLower.length
                )
                startIndex = plainLower.indexOf(queryLower, startIndex + 1)
            }
        }
    }
    
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var windowBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(textLayoutResult, windowBounds, text) {
        val layout = textLayoutResult
        val bounds = windowBounds
        if (layout != null && bounds != null) {
            lineRegistry[lineIndex] = LineLayoutInfo(
                layoutResult = layout,
                boundsInWindow = bounds,
                renderedText = text,
                prefixLen = prefixLen
            )
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, fontFamily = fontFamily),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                windowBounds = coordinates.boundsInWindow()
            }
            .pointerInput(annotatedString) {
                detectTapGestures { pos ->
                    textLayoutResult?.let { layoutResult ->
                        val offset = layoutResult.getOffsetForPosition(pos)
                        val noteByKey = mutableMapOf<String, String>()
                        lineHighlights.forEach { noteByKey["${it.line}:${it.start}:${it.end}"] = it.note }
                        legacyHighlights.forEach { noteByKey["legacy:${it.text}"] = it.note }
                        annotatedString.getStringAnnotations(tag = "SAVED_HIGHLIGHT", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                val parts = annotation.item.split("@@KEY@@")
                                val word = parts.getOrElse(0) { "" }
                                val key = parts.getOrNull(1) ?: "legacy:$word"
                                val note = noteByKey[key] ?: ""
                                if (key.startsWith("legacy:")) {
                                    onSavedHighlightClick(word, note, -1, -1, -1)
                                } else {
                                    val keyParts = key.split(":")
                                    val kLine = keyParts.getOrNull(0)?.toIntOrNull() ?: -1
                                    val kStart = keyParts.getOrNull(1)?.toIntOrNull() ?: -1
                                    val kEnd = keyParts.getOrNull(2)?.toIntOrNull() ?: -1
                                    onSavedHighlightClick(word, note, kLine, kStart, kEnd)
                                }
                            }
                    }
                }
            },
        onTextLayout = { textLayoutResult = it }
    )
}
