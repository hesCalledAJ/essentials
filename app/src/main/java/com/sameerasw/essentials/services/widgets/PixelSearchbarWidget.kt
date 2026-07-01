package com.sameerasw.essentials.services.widgets

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.appwidget.cornerRadius
import com.sameerasw.essentials.data.repository.SettingsRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PixelSearchbarWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settingsRepository = SettingsRepository(context)
        val type = settingsRepository.getPixelSearchbarType()

        // Load snapshot bitmap before entering provideContent (IO-safe suspend context)
        val widgetBitmap = if (type == "widget") {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.filesDir, WidgetScraperService.SNAPSHOT_FILE)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }.getOrNull()
            }
        } else null

        provideContent {
            GlanceTheme {
                when (type) {
                    "empty" -> {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(android.graphics.Color.TRANSPARENT)
                                .clickable(actionStartActivity(com.sameerasw.essentials.ui.activities.PixelSearchbarTapActivity::class.java))
                        ) {}
                    }

                    "widget" -> {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(android.graphics.Color.TRANSPARENT)
                                .clickable(actionStartActivity(com.sameerasw.essentials.ui.activities.PixelSearchbarTapActivity::class.java)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (widgetBitmap != null) {
                                Image(
                                    provider = ImageProvider(widgetBitmap),
                                    contentDescription = null,
                                    modifier = GlanceModifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                // Fallback text while snapshot is not yet available
                                val line1 = settingsRepository.getPixelSearchbarScrapedLine1()
                                val line2 = settingsRepository.getPixelSearchbarScrapedLine2()
                                if (line1.isEmpty() && line2.isEmpty()) {
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
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (line1.isNotEmpty()) {
                                            Text(
                                                text = line1,
                                                style = TextStyle(
                                                    color = GlanceTheme.colors.onSurface,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = androidx.glance.text.FontFamily("google_sans_flex_round"),
                                                    textAlign = TextAlign.Center
                                                )
                                            )
                                        }
                                        if (line2.isNotEmpty()) {
                                            Text(
                                                text = line2,
                                                style = TextStyle(
                                                    color = GlanceTheme.colors.onSurfaceVariant,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Normal,
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

                    else -> { // "date" (default)
                        val dateFormat = settingsRepository.getPixelSearchbarDateFormat()
                        val hasPill = settingsRepository.getPixelSearchbarBackgroundPill()
                        val dateStr = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(android.graphics.Color.TRANSPARENT)
                                .padding(horizontal = 16.dp)
                                .clickable(actionStartActivity(com.sameerasw.essentials.ui.activities.PixelSearchbarTapActivity::class.java)),
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
