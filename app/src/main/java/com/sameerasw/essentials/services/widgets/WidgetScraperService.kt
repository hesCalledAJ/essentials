package com.sameerasw.essentials.services.widgets

import android.app.Service
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RemoteViews
import com.sameerasw.essentials.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WidgetScraperService : Service() {

    private inner class ScrapingHostView(context: Context) : AppWidgetHostView(context) {
        
        private val drawListener = ViewTreeObserver.OnDrawListener {
            notifyWidgetChanged()
        }

        private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            notifyWidgetChanged()
        }

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) onRemoteViewsReceived(remoteViews)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            viewTreeObserver.addOnDrawListener(drawListener)
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    notifyWidgetChanged()
                }

                override fun onChildViewRemoved(parent: View?, child: View?) {
                    notifyWidgetChanged()
                }
            })
        }

        override fun onDetachedFromWindow() {
            viewTreeObserver.removeOnDrawListener(drawListener)
            viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            setOnHierarchyChangeListener(null)
            super.onDetachedFromWindow()
        }
    }

    private inner class ScrapingWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = ScrapingHostView(context)
    }

    companion object {
        const val HOST_ID = 1025

        @Volatile
        var currentRemoteViews: RemoteViews? = null
            private set

        fun start(context: Context) {
            context.startService(Intent(context, WidgetScraperService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WidgetScraperService::class.java))
        }
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

        handler.post { host.createView(this, widgetId, info) }
    }

    private fun onRemoteViewsReceived(remoteViews: RemoteViews) {
        currentRemoteViews = remoteViews
        notifyWidgetChanged()
    }

    private var updatePending = false
    private fun notifyWidgetChanged() {
        if (updatePending) return
        updatePending = true
        
        handler.postDelayed({
            updatePending = false
            settingsRepository.incrementPixelSearchbarWidgetRevision()

            serviceScope.launch {
                runCatching {
                    val manager = androidx.glance.appwidget.GlanceAppWidgetManager(this@WidgetScraperService)
                    val widget = PixelSearchbarWidget()
                    val glanceIds = manager.getGlanceIds(PixelSearchbarWidget::class.java)
                    for (glanceId in glanceIds) widget.update(this@WidgetScraperService, glanceId)
                }
            }
        }, 100L) // 100ms debounce window
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        appWidgetHost?.stopListening()
        appWidgetHost = null
        currentRemoteViews = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
