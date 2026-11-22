package io.roadie.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KeyboardBg = Color(0xFF141414)
private val KeyBg = Color(0xFF282828)
private val SecondaryKeyBg = Color(0xFF1F1F1F)
private val AccentBg = Color(0xFFFF0033)
private val EntryBg = Color(0xFF202020)

/**
 * Large-target, driver-friendly touch keyboard for car displays.
 * Supports English (QWERTY), Arabic (with Alef hamza variants), and Symbols.
 */
@Composable
fun CarTouchKeyboard(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search videos…"
) {
    var arabic by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var shifted by remember { mutableStateOf(false) }

    val rows: List<List<String>> = remember(arabic, symbols) {
        when {
            symbols && arabic -> listOf(
                listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"),
                listOf("@", "#", "$", "/", "&", "*", "(", ")", "-", "_"),
                listOf("،", ".", "؟", "!", "\"", ":", ";", "+", "=")
            )
            symbols -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "/", "&", "*", "(", ")", "-", "_"),
                listOf(".", ",", "?", "!", "'", "\"", ":", ";", "+", "=")
            )
            arabic -> listOf(
                listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"),
                listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
                listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ", "ذ")
            )
            else -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m")
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(KeyboardBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EntryBg)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFAAAAAA))
                Spacer(Modifier.width(10.dp))
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = text.ifEmpty { placeholder },
                        color = if (text.isEmpty()) Color(0xFF888888) else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (text.isNotEmpty()) {
                    IconButton(onClick = { onTextChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                    }
                }
            }

            rows.forEachIndexed { rowIndex, keys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (rowIndex == 2 && !symbols && !arabic) {
                        KeyCap(
                            label = null,
                            icon = {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Shift",
                                    tint = if (shifted) AccentBg else Color.White
                                )
                            },
                            background = SecondaryKeyBg,
                            modifier = Modifier.weight(1.35f),
                            onClick = { shifted = !shifted }
                        )
                    }

                    keys.forEach { ch ->
                        val display = if (shifted && !symbols && !arabic) ch.uppercase() else ch
                        var showAlefMenu by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.weight(1f)) {
                            KeyCap(
                                label = display,
                                background = KeyBg,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onTextChange(text + display)
                                    if (shifted) shifted = false
                                },
                                onLongClick = if (arabic && ch == "ا") {
                                    { showAlefMenu = true }
                                } else {
                                    null
                                }
                            )
                            if (arabic && ch == "ا") {
                                DropdownMenu(
                                    expanded = showAlefMenu,
                                    onDismissRequest = { showAlefMenu = false }
                                ) {
                                    listOf("أ", "إ", "آ").forEach { variant ->
                                        DropdownMenuItem(
                                            text = { Text(variant, fontSize = 20.sp) },
                                            onClick = {
                                                onTextChange(text + variant)
                                                showAlefMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (rowIndex == 2) {
                        KeyCap(
                            label = null,
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            },
                            background = SecondaryKeyBg,
                            modifier = Modifier.weight(1.35f),
                            onClick = {
                                if (text.isNotEmpty()) onTextChange(text.dropLast(1))
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyCap(
                    label = if (symbols) (if (arabic) "أبج" else "ABC") else "123",
                    background = SecondaryKeyBg,
                    modifier = Modifier.weight(1.3f),
                    onClick = { symbols = !symbols }
                )
                KeyCap(
                    label = if (arabic) "EN" else "عربي",
                    background = SecondaryKeyBg,
                    modifier = Modifier.weight(1.3f),
                    onClick = {
                        arabic = !arabic
                        shifted = false
                    }
                )
                KeyCap(
                    label = if (arabic) "مسافة" else "Space",
                    background = KeyBg,
                    modifier = Modifier.weight(3.8f),
                    onClick = { onTextChange("$text ") }
                )
                KeyCap(
                    label = if (arabic) "بحث" else "Search",
                    background = AccentBg,
                    modifier = Modifier.weight(1.7f),
                    onClick = onSubmit
                )
                KeyCap(
                    label = if (arabic) "تم" else "Done",
                    background = SecondaryKeyBg,
                    modifier = Modifier.weight(1.2f),
                    onClick = onClose
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyCap(
    label: String?,
    background: Color,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                icon()
            } else if (label != null) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
