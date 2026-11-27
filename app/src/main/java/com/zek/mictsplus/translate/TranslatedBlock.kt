package com.zek.mictsplus.translate

import android.graphics.Rect

data class TranslatedBlock(
    val rect: Rect,
    val originalText: String,
    val translatedText: String
)
