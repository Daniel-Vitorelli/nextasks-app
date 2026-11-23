package com.daniel.nextasks.ui.gamification

import androidx.compose.ui.graphics.Color
import com.daniel.nextasks.data.AchievementTier

/** Cor de cada tier, compartilhada entre a lista e a celebração. */
internal fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.Bronze -> Color(0xFFCD7F32)
    AchievementTier.Silver -> Color(0xFFB0BEC5)
    AchievementTier.Gold -> Color(0xFFFFC107)
    AchievementTier.Platinum -> Color(0xFF64B5F6)
}
