package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.regex.Pattern

class SyntaxHighlightingTransformation(private val language: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlightCode(text.text, language), OffsetMapping.Identity)
    }
}

fun highlightCode(text: String, language: String): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    if (text.isEmpty()) return builder.toAnnotatedString()

    val length = text.length
    val styled = BooleanArray(length) { false }

    // Vibrant Cyberpunk-inspired code palette
    val commentColor = Color(0xFF6B7280) // Gray
    val stringColor = Color(0xFF34D399)  // Mint green
    val keywordColor = Color(0xFFF472B6) // Vibrant Pink
    val functionColor = Color(0xFFFBBF24)// Amber yellow
    val numberColor = Color(0xFFFB923C)  // Orange
    val typeColor = Color(0xFF38BDF8)    // Light blue
    val tagColor = Color(0xFFF472B6)     // Pink for tags
    val attrColor = Color(0xFFFB923C)    // Attribute color

    fun applyStyle(pattern: Pattern, color: Color, bold: Boolean = false) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            
            // Ensure we don't style already-styled ranges (e.g. keywords in comments)
            var overlaps = false
            for (i in start until end) {
                if (styled[i]) {
                    overlaps = true
                    break
                }
            }
            if (!overlaps) {
                builder.addStyle(
                    SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
                    start,
                    end
                )
                for (i in start until end) {
                    styled[i] = true
                }
            }
        }
    }

    when (language.lowercase()) {
        "python" -> {
            applyStyle(Pattern.compile("#.*"), commentColor)
            applyStyle(Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"[^\"]*\"|'[^']*'"), stringColor)
            applyStyle(
                Pattern.compile("\\b(def|class|if|else|elif|for|while|return|import|from|as|in|is|and|or|not|try|except|finally|pass|break|continue|lambda|None|True|False)\\b"),
                keywordColor,
                true
            )
            applyStyle(Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\()"), functionColor)
            applyStyle(Pattern.compile("\\b\\d+(\\.\\d+)?\\b"), numberColor)
        }
        "kotlin", "java" -> {
            applyStyle(Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"), commentColor)
            applyStyle(Pattern.compile("\"[^\"]*\"|'[^']*'"), stringColor)
            applyStyle(
                Pattern.compile("\\b(package|import|class|interface|object|fun|val|var|if|else|for|while|return|when|is|in|try|catch|finally|throw|private|protected|public|internal|override|companion|suspend|data|sealed)\\b"),
                keywordColor,
                true
            )
            applyStyle(Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\()"), functionColor)
            applyStyle(Pattern.compile("\\b(Int|String|Boolean|Long|Double|Float|Char|Byte|Short|Any|Unit|Flow|List|Set|Map|HashMap|ArrayList)\\b"), typeColor)
            applyStyle(Pattern.compile("\\b\\d+f?|0x[0-9a-fA-F]+\\b"), numberColor)
        }
        "html", "xml" -> {
            applyStyle(Pattern.compile("<!--[\\s\\S]*?-->"), commentColor)
            applyStyle(Pattern.compile("\"[^\"]*\"|'[^']*'"), stringColor)
            applyStyle(Pattern.compile("</?[a-zA-Z0-9:-]+>?"), tagColor, true)
            applyStyle(Pattern.compile("\\s[a-zA-Z0-9-]+(?=\\=)"), attrColor)
        }
        "css" -> {
            applyStyle(Pattern.compile("/\\*[\\s\\S]*?\\*/"), commentColor)
            applyStyle(Pattern.compile("\\b[a-zA-Z0-9-]+(?=\\s*:)"), keywordColor)
            applyStyle(Pattern.compile("(?<=:\\s*)[^;]+(?=;)"), stringColor)
            applyStyle(Pattern.compile("[a-zA-Z0-9.-_#\\s]+(?=\\s*\\{)"), typeColor, true)
        }
        "javascript", "js", "ts", "typescript" -> {
            applyStyle(Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"), commentColor)
            applyStyle(Pattern.compile("\"[^\"]*\"|'[^']*'|`[^`]*`"), stringColor)
            applyStyle(
                Pattern.compile("\\b(function|const|let|var|if|else|for|while|return|import|from|export|default|class|extends|new|this|typeof|true|false|null|undefined|async|await)\\b"),
                keywordColor,
                true
            )
            applyStyle(Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\()"), functionColor)
            applyStyle(Pattern.compile("\\b\\d+(\\.\\d+)?\\b"), numberColor)
        }
        "sql" -> {
            applyStyle(Pattern.compile("--.*"), commentColor)
            applyStyle(Pattern.compile("'[^']*'"), stringColor)
            applyStyle(
                Pattern.compile("(?i)\\b(SELECT|FROM|WHERE|JOIN|INNER|LEFT|RIGHT|ON|GROUP\\s+BY|ORDER\\s+BY|HAVING|LIMIT|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|DATABASE|INDEX|ALTER|DROP|AS|AND|OR|IN|NOT|NULL|IS|LIKE)\\b"),
                keywordColor,
                true
            )
            applyStyle(Pattern.compile("\\b\\d+\\b"), numberColor)
        }
    }

    return builder.toAnnotatedString()
}
