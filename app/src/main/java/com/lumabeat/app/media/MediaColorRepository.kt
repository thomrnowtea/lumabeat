package com.lumabeat.app.media

import com.lumabeat.app.wiz.LightColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaColorRepository {
    private val mutableColors = MutableStateFlow<List<LightColor>>(emptyList())
    val colors: StateFlow<List<LightColor>> = mutableColors.asStateFlow()

    fun publish(colors: List<LightColor>) {
        mutableColors.value = colors.take(MAX_COLORS)
    }

    private const val MAX_COLORS = 3
}
