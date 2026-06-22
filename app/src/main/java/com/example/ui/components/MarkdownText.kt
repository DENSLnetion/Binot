package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
 * Sealed class to handle chunking between Native Compose Text and KaTeX WebViews
 */
sealed class MarkdownItem {
    data class NativeLine(val text: String, val lineIndex: Int) : MarkdownItem()
    data class MathBlock(val rawText: String, val startLineIndex: Int) : MarkdownItem()
}

private fun resolveRectToPosition(
    rect: Rect,
    selectedText: String,
    registry: Map<Int, LineLayoutInfo>,
    rawLines: List<String>
): Triple<Int, Int, Int>? {
    if (selectedText.isBlank()) return null
    val anchor = androidx.compose.ui.geometry.Offset(rect.left, rect.center.y)
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
        androidx.compose.ui.geometry.Offset(localX, info.boundsInWindow.height / 2f)
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

@Composable
fun KaTeXWebView(
    mathContent: String,
    textColor: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val hexColor = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
    val cssFont = when (fontFamily) {
        FontFamily.Serif -> "serif"
        FontFamily.Monospace -> "monospace"
        else -> "sans-serif"
    }

    val htmlContent = remember(mathContent, hexColor, cssFont) {
        var html = mathContent
        
        // Basic Markdown Support
        html = html.replace(Regex("^### (.*)$", RegexOption.MULTILINE), "<h4>$1</h4>")
        html = html.replace(Regex("^## (.*)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        html = html.replace(Regex("^# (.*)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        html = html.replace(Regex("^- (.*)$", RegexOption.MULTILINE), "<li>$1</li>")
        html = html.replace(Regex("^[0-9]+\\. (.*)$", RegexOption.MULTILINE), "<li>$1</li>")
        
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="katex.min.css">
            <script src="katex.min.js"></script>
            <script src="auto-render.min.js"></script>
            <style>
                body {
                    background-color: transparent;
                    color: $hexColor;
                    font-family: $cssFont;
                    font-size: 16px;
                    line-height: 1.6;
                    margin: 0;
                    padding: 4px 0px;
                    word-wrap: break-word;
                    white-space: pre-wrap; /* Mengamankan spasi antar teks dan rumus */
                }
                li { margin-bottom: 4px; }
                #debug-console {
                    background: #fff3cd; color: #856404;
                    padding: 8px; border-radius: 6px; border: 1px solid #ffeeba;
                    font-family: monospace; font-size: 11px;
                    margin-bottom: 8px; display: none; word-wrap: break-word;
                }
            </style>
        </head>
        <body>
            <div id="debug-console"></div>
            <div id="math-content">$html</div>
            
            <script>
                var dbg = document.getElementById('debug-console');
                function logDebug(msg) {
                    dbg.style.display = 'block';
                    dbg.innerHTML += "⚠️ " + msg + "<br>";
                }
                
                window.onerror = function(msg, url, line) { 
                    logDebug("JS Crash: " + msg + " (Line: " + line + ")"); 
                    return false;
                };

                document.addEventListener("DOMContentLoaded", function() {
                    try {
                        if (typeof katex === 'undefined') { logDebug("Fatal: katex object missing. Cek file katex.min.js di assets!"); return; }
                        if (typeof renderMathInElement === 'undefined') { logDebug("Fatal: renderMathInElement missing. Cek file auto-render.min.js!"); return; }
                        
                        var el = document.getElementById('math-content');
                        renderMathInElement(el, {
                            delimiters: [
                                {left: "$$", right: "$$", display: true},
                                {left: "\\[", right: "\\]", display: true},
                                {left: "$", right: "$", display: false},
                                {left: "\\(", right: "\\)", display: false}
                            ],
                            throwOnError: false,
                            errorCallback: function(msg, err) {
                                logDebug("KaTeX Syntax Error: " + msg);
                            }
                        });
                        
                        // Deteksi visual: kalau nggak ada tag class 'katex' yang tercipta, berarti gagal parse
                        if (el.innerHTML.indexOf('class="katex"') === -1 && (el.innerHTML.indexOf('$$') !== -1 || el.innerHTML.indexOf('$') !== -1)) {
                            logDebug("Script jalan, tapi KaTeX gagal nemuin rumus. Cek spasi di antara $$ lo.");
                        }
                    } catch(e) {
                        logDebug("System Exception: " + e.message);
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                
                // Mencegah OS mengambil alih loading asset
                webViewClient = android.webkit.WebViewClient()
                webChromeClient = android.webkit.WebChromeClient()
                
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true 
                settings.allowContentAccess = true
                settings.domStorageEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                
                try {
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                } catch (e: Exception) {
                    // Safe catch
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("file:///android_asset/katex/", htmlContent, "text/html", "UTF-8", null)
        }
    )
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
    val lines = text.split("\n")
    
    // Chunking the markdown into Native lines and WebView math blocks
    val markdownItems = remember(text) {
        val items = mutableListOf<MarkdownItem>()
        var inMathBlock = false
        var mathBlockContent = ""
        var mathBlockStartIndex = -1

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
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

            val hasInlineMath = Regex("""\$.+?\$""").containsMatchIn(line)
            if (hasInlineMath) {
                items.add(MarkdownItem.MathBlock(line, i))
            } else {
                items.add(MarkdownItem.NativeLine(line, i))
            }
            i++
        }
        if (inMathBlock) {
            items.add(MarkdownItem.MathBlock(mathBlockContent, mathBlockStartIndex))
        }
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

        items(markdownItems.size) { index ->
            when (val item = markdownItems[index]) {
                is MarkdownItem.MathBlock -> {
                    KaTeXWebView(
                        mathContent = item.rawText,
                        textColor = MaterialTheme.colorScheme.onBackground,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(bottom = 8.dp)
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
