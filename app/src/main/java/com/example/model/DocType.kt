package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.DocPillColor
import com.example.ui.theme.DocPillTintDark
import com.example.ui.theme.DocPillTintLight
import com.example.ui.theme.PdfPillColor
import com.example.ui.theme.PdfPillTintDark
import com.example.ui.theme.PdfPillTintLight
import com.example.ui.theme.PptPillColor
import com.example.ui.theme.PptPillTintDark
import com.example.ui.theme.PptPillTintLight
import com.example.ui.theme.TextPillColor
import com.example.ui.theme.TextPillTintDark
import com.example.ui.theme.TextPillTintLight
import com.example.ui.theme.XmlPillColor
import com.example.ui.theme.XmlPillTintDark
import com.example.ui.theme.XmlPillTintLight

enum class DocType(
    val label: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val pillColor: Color,
    val tintLight: Color,
    val tintDark: Color
) {
    PDF(
        label = "PDF",
        extensions = setOf("pdf"),
        mimeTypes = setOf("application/pdf"),
        pillColor = PdfPillColor,
        tintLight = PdfPillTintLight,
        tintDark = PdfPillTintDark
    ),
    DOC(
        label = "DOC",
        extensions = setOf("doc", "docx", "rtf"),
        mimeTypes = setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/rtf"
        ),
        pillColor = DocPillColor,
        tintLight = DocPillTintLight,
        tintDark = DocPillTintDark
    ),
    XML(
        label = "XML",
        extensions = setOf("xml"),
        mimeTypes = setOf("text/xml", "application/xml"),
        pillColor = XmlPillColor,
        tintLight = XmlPillTintLight,
        tintDark = XmlPillTintDark
    ),
    PPT(
        label = "PPT",
        extensions = setOf("ppt", "pptx"),
        mimeTypes = setOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ),
        pillColor = PptPillColor,
        tintLight = PptPillTintLight,
        tintDark = PptPillTintDark
    ),
    TEXT(
        label = "TEXT",
        extensions = setOf("txt", "log", "md", "csv", "json", "ini", "cfg"),
        mimeTypes = setOf("text/plain", "text/csv", "text/markdown", "application/json"),
        pillColor = TextPillColor,
        tintLight = TextPillTintLight,
        tintDark = TextPillTintDark
    );

    companion object {
        fun fromExtension(ext: String): DocType? {
            val clean = ext.lowercase().trimStart('.')
            return entries.firstOrNull { clean in it.extensions }
        }

        fun fromFileName(name: String): DocType? {
            val dotIdx = name.lastIndexOf('.')
            if (dotIdx < 0 || dotIdx == name.length - 1) return null
            return fromExtension(name.substring(dotIdx + 1))
        }

        fun fromMimeType(mime: String?): DocType? {
            if (mime.isNullOrBlank()) return null
            val lower = mime.lowercase()
            return entries.firstOrNull { lower in it.mimeTypes }
        }
    }
}
