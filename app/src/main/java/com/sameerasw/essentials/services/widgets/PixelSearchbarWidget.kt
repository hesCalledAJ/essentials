package com.sameerasw.essentials.services.widgets

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.appwidget.cornerRadius
import com.sameerasw.essentials.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity

class PixelSearchbarWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settingsRepository = SettingsRepository(context)
        val type = settingsRepository.getPixelSearchbarType()
        val tapActionEnabled = settingsRepository.getPixelSearchbarTapActionEnabled()
        val paddingH = settingsRepository.getPixelSearchbarWidgetPaddingH().dp
        val paddingV = settingsRepository.getPixelSearchbarWidgetPaddingV().dp
        val revision = settingsRepository.getPixelSearchbarWidgetRevision()

        val globalTapAction = if (tapActionEnabled)
            actionStartActivity(com.sameerasw.essentials.ui.activities.PixelSearchbarTapActivity::class.java)
        else null

        provideContent {
            GlanceTheme {
                val boxModifier = GlanceModifier
                    .fillMaxSize()
                    .background(android.graphics.Color.TRANSPARENT)
                    .padding(horizontal = paddingH, vertical = paddingV)
                    .then(
                        if (globalTapAction != null) {
                            GlanceModifier.clickable(globalTapAction)
                        } else {
                            GlanceModifier
                        }
                    )

                when (type) {
                    "empty" -> {
                        Box(
                            modifier = boxModifier
                        ) {}
                    }

                    "widget" -> {
                        val remoteViews = WidgetScraperService.currentRemoteViews
                        Box(
                            modifier = boxModifier.cornerRadius(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (remoteViews != null) {
                                AndroidRemoteViews(
                                    remoteViews = remoteViews,
                                    modifier = GlanceModifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "—",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = androidx.glance.text.FontFamily("google_sans_flex_round"),
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                    }

                    else -> { // "date" (default)
                        val dateFormat = settingsRepository.getPixelSearchbarDateFormat()
                        val hasPill = settingsRepository.getPixelSearchbarBackgroundPill()
                        val dateStr = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
                        Box(
                            modifier = boxModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasPill) {
                                Box(
                                    modifier = GlanceModifier
                                        .background(GlanceTheme.colors.background)
                                        .cornerRadius(28.dp)
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dateStr,
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onSecondaryContainer,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = androidx.glance.text.FontFamily("google_sans_flex_round"),
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }
                            } else {
                                Text(
                                    text = dateStr,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = androidx.glance.text.FontFamily("google_sans_flex_round"),
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
