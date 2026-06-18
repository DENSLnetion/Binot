package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
        for (line in lines) {
            // Hitung jumlah spasi di awal buat nentuin ini poin utama atau sub-poin
            val indentSpaces = line.takeWhile { it == ' ' || it == '\t' }.length
            val trimmedLine = line.trimStart()

            when {
                trimmedLine.startsWith("# ") -> {
                    Text(
                        text = trimmedLine.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                    )
                }
                trimmedLine.startsWith("## ") -> {
                    Text(
                        text = trimmedLine.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                    )
                }
                trimmedLine.startsWith("### ") -> {
                    Text(
                        text = trimmedLine.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    // Kalkulasi jarak menjorok ke dalam berdasarkan spasi dari Gemini
                    val paddingStart = 16.dp + (indentSpaces * 6).dp
                    Row(modifier = Modifier.padding(start = paddingStart, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            text = if (indentSpaces > 0) "◦" else "•", // Poin dalem pake bullet beda
                            modifier = Modifier.width(24.dp), // Lebar tetap biar sejajar
                            style = MaterialTheme.typography.bodyLarge, 
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // weight(1f) memastikan teks melipat dengan rapi ke bawah, bukan nyerobot tempat bullet
                        BasicMarkdownLine(
                            text = trimmedLine.substring(2).trim(), 
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                trimmedLine.isBlank() -> Spacer(modifier = Modifier.height(16.dp))
                else -> BasicMarkdownLine(text = trimmedLine, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
fun BasicMarkdownLine(text: String, modifier: Modifier = Modifier) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        // Perbaikan Regex biar ga nyaplok bintang yang posisinya nempel-nempel
        val pattern = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
        val matches = pattern.findAll(text)
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            if (match.groups[1] != null) { // **bold**
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groups[1]!!.value)
                }
            } else if (match.groups[2] != null) { // *italic*
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groups[2]!!.value)
                }
            } else if (match.groups[3] != null) { // _italic_
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groups[3]!!.value)
                }
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex))
    }
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

