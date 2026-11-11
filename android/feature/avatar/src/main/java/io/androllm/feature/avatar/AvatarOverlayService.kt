package io.androllm.feature.avatar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.androllm.core.soul.AlexaSettings
import io.androllm.core.soul.AlexaSettingsStore
import io.androllm.core.soul.AvatarCommandBus
import io.androllm.core.soul.Point
import io.androllm.core.soul.Rect as AvatarRect
import io.androllm.core.soul.SoulEngine
import io.androllm.core.voice.tts.ReadAloudSpeaker
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AvatarOverlayEntryPoint {
    fun soulEngine(): SoulEngine
    fun speaker(): ReadAloudSpeaker
    fun settingsStore(): AlexaSettingsStore
    fun commandBus(): AvatarCommandBus
}

/**
 * Foreground servis koji crta Alexu preko svih aplikacija (TYPE_APPLICATION_OVERLAY).
 * Prozor je velik tačno koliko i lik, pa dodiri van lika idu drugim aplikacijama.
 * Lik se prevlači prstom; zadnje mjesto se pamti u [AlexaSettingsStore].
 */
class AvatarOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var store: AlexaSettingsStore
    private lateinit var session: AvatarSession
    private val renderer: AvatarRenderer = PlaceholderAvatarRenderer()

    private var owner: OverlayLifecycleOwner? = null
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var posX = 0f
    private var posY = 0f
    private var widthPx = 1
    private var heightPx = 1
    private var cancelTick by mutableIntStateOf(0)
    private var positioned = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ep = EntryPointAccessors.fromApplication(applicationContext, AvatarOverlayEntryPoint::class.java)
        store = ep.settingsStore()
        session = AvatarSession(scope, ep.soulEngine(), ep.speaker(), store, ep.commandBus())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        scope.launch {
            store.settings.collect { onSettings(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action == ACTION_STOP) {
            // Korisnik je isključio lik iz obavijesti: zapamti, da ga aplikacija ne vrati sama.
            CoroutineScope(Dispatchers.IO).launch { store.update { it.copy(overlayEnabled = false) } }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun onSettings(s: AlexaSettings) {
        if (!s.overlayEnabled) {
            stopSelf()
            return
        }
        val density = resources.displayMetrics.density
        widthPx = (s.avatarSize.dp * density).roundToInt().coerceAtLeast(1)
        heightPx = (s.avatarSize.dp * 1.2f * density).roundToInt().coerceAtLeast(1)
        if (!positioned) {
            positioned = true
            val dm = resources.displayMetrics
            val margin = 12 * density
            posX = if (s.overlayX >= 0) s.overlayX.toFloat() else dm.widthPixels - widthPx - margin
            posY = if (s.overlayY >= 0) s.overlayY.toFloat() else margin
        }
        if (s.showAvatar && Settings.canDrawOverlays(this)) {
            if (root == null) addOverlay() else applyLayout()
        } else {
            removeOverlay()
        }
    }

    private fun screenRect(): AvatarRect {
        val dm = resources.displayMetrics
        return AvatarRect(0f, 0f, dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
    }

    private fun addOverlay() {
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        owner = lifecycleOwner
        val frame = DragFrameLayout(
            this,
            onDrag = { dx, dy ->
                cancelTick++
                moveTo(posX + dx, posY + dy)
            },
            onDragEnd = { persistPosition() },
        )
        frame.setViewTreeLifecycleOwner(lifecycleOwner)
        frame.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        frame.setViewTreeViewModelStoreOwner(lifecycleOwner)
        val compose = ComposeView(this).apply {
            setContent {
                val density = resources.displayMetrics.density
                SystemOverlayAvatar(
                    session = session,
                    widthPx = widthPx.toFloat(),
                    heightPx = heightPx.toFloat(),
                    marginPx = 12 * density,
                    screen = { screenRect() },
                    position = { Point(posX, posY) },
                    moveTo = { x, y -> moveTo(x, y) },
                    onSettled = { persistPosition() },
                    cancelTick = cancelTick,
                    renderer = renderer,
                )
            }
        }
        frame.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val lp = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        params = lp
        root = frame
        clampPosition()
        lp.x = posX.roundToInt()
        lp.y = posY.roundToInt()
        try {
            windowManager.addView(frame, lp)
        } catch (e: Exception) {
            root = null
            params = null
            lifecycleOwner.destroy()
            owner = null
            stopSelf()
        }
    }

    private fun removeOverlay() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        params = null
        owner?.destroy()
        owner = null
    }

    private fun clampPosition() {
        val dm = resources.displayMetrics
        val (x, y) = clampOverlayPosition(posX.roundToInt(), posY.roundToInt(), widthPx, heightPx, dm.widthPixels, dm.heightPixels)
        // Zadrži razlomke dok je unutar ekrana, da hod bude gladak.
        if (posX.roundToInt() != x) posX = x.toFloat()
        if (posY.roundToInt() != y) posY = y.toFloat()
    }

    private fun moveTo(x: Float, y: Float) {
        posX = x
        posY = y
        applyLayout()
    }

    private fun applyLayout() {
        val view = root ?: return
        val lp = params ?: return
        clampPosition()
        lp.width = widthPx
        lp.height = heightPx
        lp.x = posX.roundToInt()
        lp.y = posY.roundToInt()
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: IllegalArgumentException) {
            // Prozor je već uklonjen.
        }
    }

    private fun persistPosition() {
        val x = posX.roundToInt()
        val y = posY.roundToInt()
        scope.launch { store.update { it.copy(overlayX = x, overlayY = y) } }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alexa preko drugih aplikacija", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, AvatarOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Alexa je aktivna")
            .setContentText("Lik se prikazuje preko drugih aplikacija.")
            .setOngoing(true)
            .setContentIntent(launch)
            .addAction(0, "Zaustavi", stop)
            .build()
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alexa_overlay"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "io.androllm.feature.avatar.OVERLAY_STOP"

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AvatarOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AvatarOverlayService::class.java))
        }
    }
}
