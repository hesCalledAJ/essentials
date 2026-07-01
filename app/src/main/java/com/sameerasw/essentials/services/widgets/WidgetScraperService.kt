package com.sameerasw.essentials.services.widgets

import android.app.Service
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.sameerasw.essentials.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class WidgetScraperService : Service() {

    companion object {
        const val HOST_ID = 1025
        const val SNAPSHOT_FILE = "widget_scraper_snapshot.png"

        fun start(context: Context) {
            context.startService(Intent(context, WidgetScraperService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WidgetScraperService::class.java))
        }

        /** Walk an inflated view tree to collect all non-empty TextView text values. */
        fun collectTexts(view: View, out: MutableList<String>) {
            when (view) {
                is TextView -> {
                    val t = view.text?.toString()?.trim() ?: ""
                    if (t.isNotEmpty()) out.add(t)
                }
                is ViewGroup -> for (i in 0 until view.childCount) collectTexts(view.getChildAt(i), out)
            }
        }
    }

    /**
     * Intercepts RemoteViews updates from the bound widget provider.
     */
    private inner class ScrapingHostView(context: Context) : AppWidgetHostView(context) {
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) onRemoteViewsReceived(remoteViews)
        }
    }

    private inner class ScrapingWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = ScrapingHostView(context)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsRepository: SettingsRepository
    private var appWidgetHost: ScrapingWidgetHost? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bindAndListen()
        return START_STICKY
    }

    private fun bindAndListen() {
        val widgetId = settingsRepository.getPixelSearchbarWidgetId()
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { stopSelf(); return }

        val awm = AppWidgetManager.getInstance(this)
        val host = ScrapingWidgetHost(this, HOST_ID)
        appWidgetHost = host
        host.startListening()

        val info = awm.getAppWidgetInfo(widgetId) ?: run { stopSelf(); return }

        // createView on main thread wires up update callbacks
        handler.post { host.createView(this, widgetId, info) }
    }

    /**
     * Called on the main thread whenever the bound widget provider pushes new RemoteViews.
     * Inflates the RemoteViews into a real view tree, measures and lays it out at the device
     * width, draws it to a Bitmap, saves as PNG, extracts text, then updates the Glance widget.
     */
    private fun onRemoteViewsReceived(remoteViews: RemoteViews) {
        try {
            val dm = resources.displayMetrics
            val widthPx = dm.widthPixels
            // Use the widget info's minHeight as the render height, with a sensible default
            val heightPx = (80 * dm.density).toInt()

            // Inflate RemoteViews into a real view hierarchy using the widget's own resources
            val parent = FrameLayout(this)
            val inflated = remoteViews.apply(this, parent)

            // Measure + layout
            val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            inflated.measure(wSpec, hSpec)
            inflated.layout(0, 0, inflated.measuredWidth, inflated.measuredHeight)

            // Render to Bitmap (same as Smartspacer's headless render approach)
            val bitmap = Bitmap.createBitmap(inflated.measuredWidth, inflated.measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            inflated.draw(canvas)

            // Save PNG to filesDir (persists across restarts)
            val snapshotFile = File(filesDir, SNAPSHOT_FILE)
            FileOutputStream(snapshotFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            // Extract text as a fallback / supplementary metadata
            val texts = mutableListOf<String>()
            collectTexts(inflated, texts)
            val sorted = texts.filter { it.length > 1 }.sortedByDescending { it.length }
            settingsRepository.setPixelSearchbarScrapedLine1(sorted.getOrElse(0) { "" })
            settingsRepository.setPixelSearchbarScrapedLine2(sorted.getOrElse(1) { "" })

        } catch (_: Exception) {}

        // Push Glance widget refresh on IO thread
        serviceScope.launch {
            runCatching {
                val manager = androidx.glance.appwidget.GlanceAppWidgetManager(this@WidgetScraperService)
                val widget = PixelSearchbarWidget()
                val glanceIds = manager.getGlanceIds(PixelSearchbarWidget::class.java)
                for (glanceId in glanceIds) widget.update(this@WidgetScraperService, glanceId)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        appWidgetHost?.stopListening()
        appWidgetHost = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
