package com.tavern.lite.ui.components

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

@Composable
fun MarkdownText(
    markwon: Markwon,
    markdown: String,
    textColor: Int,
    textSize: TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textView = remember {
        TextView(context).apply {
            this.setTextColor(textColor)
            this.textSize = textSize.value
        }
    }

    AndroidView(
        factory = { textView },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.textSize = textSize.value
            markwon.setMarkdown(tv, markdown)
        },
        modifier = modifier
    )
}
