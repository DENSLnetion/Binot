package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

@Composable
fun MarkdownText(
    text: String, 
    listState: LazyListState,
    highlightsInfo: String? = null,
    onSavedHighlightClick: (String, String) -> Unit = { _, _ -> },
    highlightQuery: String = "", 
    fontFamily: FontFamily = FontFamily.SansSerif, 
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    
    val savedHighlightsMap = remember(highlightsInfo) {
        val map = mutableMapOf<String, String>()
        if (!highlightsInfo.isNullOrBlank() && highlightsInfo != "[]") {
            try {
                val array = JSONArray(highlightsInfo)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    map[obj.getString("text")] = obj.getString("note")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        map
    }

    val highlightBgColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer
    
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        itemsIndexed(lines) { _, line ->
            val indentSpaces = line.takeWhile { it == ' ' || it == '\t' }.length
            val trimmedLine = line.trimStart()

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
                    Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            text = if (indentSpaces > 0) "◦" else "•",
                            modifier = Modifier.width(24.dp),
                            style = MaterialTheme.typography.bodyLarge, 
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        BasicMarkdownLine(
                            text = trimmedLine.substring(2).trim(), 
                            highlightQuery = highlightQuery,
                            savedHighlightsMap = savedHighlightsMap,
                            onSavedHighlightClick = onSavedHighlightClick,
                            highlightBgColor = highlightBgColor,
                            highlightTextColor = highlightTextColor,
                            fontFamily = fontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                trimmedLine.matches(Regex("^[0-9]+\\.\\s.*")) -> {
                    val dotIndex = trimmedLine.indexOf(".")
                    val number = trimmedLine.substring(0, dotIndex + 1)
                    val content = trimmedLine.substring(dotIndex + 1).trim()
                    val paddingStart = 16.dp + (indentSpaces * 6).dp
                    
                    Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            text = number, 
                            modifier = Modifier.width(32.dp), 
                            style = MaterialTheme.typography.bodyLarge, 
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        BasicMarkdownLine(
                            text = content, 
                            highlightQuery = highlightQuery,
                            savedHighlightsMap = savedHighlightsMap,
                            onSavedHighlightClick = onSavedHighlightClick,
                            highlightBgColor = highlightBgColor,
                            highlightTextColor = highlightTextColor,
                            fontFamily = fontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                trimmedLine.isBlank() -> Spacer(modifier = Modifier.height(16.dp))
                else -> BasicMarkdownLine(
                    text = trimmedLine, 
                    highlightQuery = highlightQuery,
                    savedHighlightsMap = savedHighlightsMap,
                    onSavedHighlightClick = onSavedHighlightClick,
                    highlightBgColor = highlightBgColor,
                    highlightTextColor = highlightTextColor,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun BasicMarkdownLine(
    text: String, 
    highlightQuery: String,
    savedHighlightsMap: Map<String, String>,
    onSavedHighlightClick: (String, String) -> Unit,
    highlightBgColor: Color,
    highlightTextColor: Color,
    fontFamily: FontFamily,
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
        
        val plainString = this.toAnnotatedString().text.lowercase()

        savedHighlightsMap.forEach { (word, _) ->
            val wordLower = word.lowercase()
            var startIndex = plainString.indexOf(wordLower)
            while (startIndex >= 0) {
                addStyle(
                    style = SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold),
                    start = startIndex,
                    end = startIndex + wordLower.length
                )
                addStringAnnotation(
                    tag = "SAVED_HIGHLIGHT",
                    annotation = word,
                    start = startIndex,
                    end = startIndex + wordLower.length
                )
                startIndex = plainString.indexOf(wordLower, startIndex + 1)
            }
        }
        
        if (highlightQuery.isNotBlank()) {
            val queryLower = highlightQuery.lowercase()
            var startIndex = plainString.indexOf(queryLower)
            
            while (startIndex >= 0) {
                addStyle(
                    style = SpanStyle(background = Color.Yellow, color = Color.Black),
                    start = startIndex,
                    end = startIndex + queryLower.length
                )
                startIndex = plainString.indexOf(queryLower, startIndex + 1)
            }
        }
    }
    
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, fontFamily = fontFamily),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures { pos ->
                textLayoutResult?.let { layoutResult ->
                    val offset = layoutResult.getOffsetForPosition(pos)
                    annotatedString.getStringAnnotations(tag = "SAVED_HIGHLIGHT", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            val word = annotation.item
                            val note = savedHighlightsMap[word] ?: ""
                            onSavedHighlightClick(word, note)
                        }
                }
            }
        },
        onTextLayout = { textLayoutResult = it }
    )
}
