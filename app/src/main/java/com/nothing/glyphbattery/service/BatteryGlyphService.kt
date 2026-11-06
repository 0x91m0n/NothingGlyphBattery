package com.nothing.glyphbattery.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.nothing.glyphbattery.MainActivity
import com.nothing.glyphbattery.R
import com.nothing.glyphbattery.data.SettingsStore
import com.nothing.glyphbattery.glyph.GlyphConnectionError
import com.nothing.glyphbattery.glyph.GlyphConnectionState
import com.nothing.glyphbattery.glyph.GlyphConnectionSnapshot
import com.nothing.glyphbattery.model.ChargingSessionRecovery
import com.nothing.glyphbattery.glyph.GlyphController
import com.nothing.glyphbattery.model.AutoOffTimer
import com.nothing.glyphbattery.model.BatterySnapshot
import com.nothing.glyphbattery.model.ChargeCompleteTimerCoordinator
import com.nothing.glyphbattery.model.ChargeCompleteTimerPersistence
import com.nothing.glyphbattery.model.ChargeCompleteTimerRuntime
import com.nothing.glyphbattery.model.ChargeCompleteTimerUpdate
import com.nothing.glyphbattery.model.GlyphBlockReason
import com.nothing.glyphbattery.model.GlyphDecision
import com.nothing.glyphbattery.model.GlyphRenderTarget
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.glyphbattery.model.GlyphStateReducer
import com.nothing.glyphbattery.model.ServiceMode
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class ServiceHealth {
    STOPPED,
    STARTING,
    READY,
    DISCONNECTED,
    AUTHORIZATION_FAILED,
    UNSUPPORTED_DEVICE,
    ERROR
}

enum class DiagnosticRenderTarget {
    UNKNOWN,
    OFF,
    BATTERY,
    CHARGE_COMPLETE
}

enum class DiagnosticRenderResult {
    NEVER,
    SUCCESS,
    FAILED
}

data class BatteryGlyphServiceStatus(
    val isRunning: Boolean = false,
    val health: ServiceHealth = ServiceHealth.STOPPED,
    val sdkConnection: GlyphConnectionState = GlyphConnectionState.Idle,
    val target: DiagnosticRenderTarget = DiagnosticRenderTarget.UNKNOWN,
    val blockReason: GlyphBlockReason? = null,
    val batteryPercent: Int? = null,
    val isPluggedIn: Boolean? = null,
    val generalTimerDeadlineEpochMillis: Long? = null,
    val chargeCompleteTimerDeadlineEpochMillis: Long? = null,
    val chargeCompleteTimerExpired: Boolean = false,
    val lastRenderResult: DiagnosticRenderResult = DiagnosticRenderResult.NEVER,
    val lastRenderEpochMillis: Long? = null,
    val errorCount: Int = 0,
    val developerAction: String? = null,
    val recentEvents: List<String> = emptyList()
)

class BatteryGlyphService : Service() {
    companion object {
        private const val TAG = "BatteryGlyphService"
        private const val CHANNEL_ID = "glyph_battery_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.nothing.glyphbattery.START"
        private const val ACTION_STOP = "com.nothing.glyphbattery.STOP"
        private const val ACTION_RECONNECT = "com.nothing.glyphbattery.RECONNECT"
        private const val ACTION_ZONE_SELF_TEST = "com.nothing.glyphbattery.ZONE_SELF_TEST"
        private const val MAX_RENDER_RETRIES = 5
        private const val MAX_RENDER_RETRY_DELAY_MS = 4_000L
        private const val POWER_SETTLE_MS = 1_000L

        private fun journal(context: Context) = RuntimeJournal(
            java.io.File(context.noBackupFilesDir, "glyph-runtime.log")
        )

        private val _status = MutableStateFlow(BatteryGlyphServiceStatus())
        val status: StateFlow<BatteryGlyphServiceStatus> = _status.asStateFlow()
        @Volatile
        private var stopRequested = false

        fun start(context: Context, userInitiated: Boolean = true): Boolean {
            val runState = ServiceRunState(context)
            if (userInitiated) runState.request()
            if (!runState.requested) return false
            ServiceRecovery.ensureScheduled(context)
            if (_status.value.isRunning) return true
            stopRequested = false
            return try {
                context.startForegroundService(
                    Intent(context, BatteryGlyphService::class.java).setAction(ACTION_START)
                )
                true
            } catch (error: RuntimeException) {
                runState.lastStartFailure = error.javaClass.simpleName
                Log.e(TAG, "Unable to request foreground service", error)
                false
            }
        }

        fun stop(context: Context) {
            // Persist Stop even if Android has already killed the service.
            ServiceRunState(context).stop()
            ServiceRecovery.cancel(context)
            stopRequested = true
            if (!_status.value.isRunning) return
            context.startService(
                Intent(context, BatteryGlyphService::class.java).setAction(ACTION_STOP)
            )
        }

        fun requestGlyphReconnect(context: Context) {
            if (_status.value.isRunning) {
                context.startService(
                    Intent(context, BatteryGlyphService::class.java).setAction(ACTION_RECONNECT)
                )
            }
        }

        fun requestZoneSelfTest(context: Context) {
            if (_status.value.isRunning) {
                context.startService(
                    Intent(context, BatteryGlyphService::class.java).setAction(ACTION_ZONE_SELF_TEST)
                )
            }
        }

        fun diagnosticReport(context: Context): String {
            val current = _status.value
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return buildString {
                appendLine("Glyph Battery diagnostic report")
                appendLine("App: ${packageInfo.versionName} (${packageInfo.longVersionCode})")
                appendLine("OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Service: ${current.health}; running=${current.isRunning}")
                appendLine("SDK: ${current.sdkConnection}")
                appendLine("Target: ${current.target}; block=${current.blockReason ?: "none"}")
                appendLine("Battery: ${current.batteryPercent ?: "unknown"}%; plugged=${current.isPluggedIn ?: "unknown"}")
                appendLine("General timer deadline: ${current.generalTimerDeadlineEpochMillis ?: "none"}")
                appendLine("Charge-complete deadline: ${current.chargeCompleteTimerDeadlineEpochMillis ?: "none"}; expired=${current.chargeCompleteTimerExpired}")
                appendLine("Last render: ${current.lastRenderResult}; at=${current.lastRenderEpochMillis ?: "never"}")
                appendLine("Errors: ${current.errorCount}")
                appendLine("Current action: ${current.developerAction ?: "none"}")
                appendLine("Background: ${BackgroundSettings.read(context)}")
                appendLine("Start failure: ${ServiceRunState(context).lastStartFailure.ifEmpty { "none" }}")
                ServiceRecovery.latestExit(context)?.let { exit ->
                    appendLine("Last process exit: reason=${exit.reason}; at=${exit.timestamp}; importance=${exit.importance}")
                }
                appendLine("Recent process events:")
                current.recentEvents.forEach(::appendLine)
                appendLine("Persisted local events (survive Stop/Start and process recreation):")
                runCatching { journal(context).read() }
                    .onSuccess { lines -> lines.forEach(::appendLine) }
                    .onFailure { appendLine("Journal unavailable: ${it.javaClass.simpleName}") }
            }.trimEnd()
        }
    }

    private sealed interface ServiceEvent {
        data class SettingsChanged(val settings: GlyphSettings) : ServiceEvent
        data class BatteryChanged(val battery: BatterySnapshot) : ServiceEvent
        data class ConnectionChanged(val snapshot: GlyphConnectionSnapshot) : ServiceEvent
        data class RenewChargingSession(val generation: Long) : ServiceEvent
        data class RuntimeLoaded(val runtime: ChargeCompleteTimerRuntime) : ServiceEvent
        data class GeneralTimerExpired(val generation: Long) : ServiceEvent
        data class ChargeCompleteDeadlineReached(val deadlineEpochMillis: Long) : ServiceEvent
        data class RetryRender(val failureCount: Int) : ServiceEvent
        data class RetryConnection(val generation: Long) : ServiceEvent
        data object RuntimeRenderFailed : ServiceEvent
        data object UserStop : ServiceEvent
        data object ReconnectGlyph : ServiceEvent
        data object ZoneSelfTest : ServiceEvent
    }

    private data class TimerConfiguration(
        val timer: AutoOffTimer,
        val customMinutes: Int,
        val serviceMode: ServiceMode
    )

    private lateinit var glyphController: GlyphController
    private lateinit var settingsStore: SettingsStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = Channel<ServiceEvent>(Channel.UNLIMITED)
    private var receiverRegistered = false
    private var runtimeStarted = false

    @Volatile
    private var lastPublishedBattery: BatterySnapshot? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> publishBattery(intent)
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    // Read the system snapshot rather than inventing a battery level from a
                    // power-only broadcast. BATTERY_CHANGED also follows and is deduplicated.
                    registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        ?.let(::publishBattery)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val previousStatus = _status.value
        _status.value = BatteryGlyphServiceStatus(
            isRunning = true,
            health = ServiceHealth.STARTING,
            lastRenderResult = previousStatus.lastRenderResult,
            lastRenderEpochMillis = previousStatus.lastRenderEpochMillis,
            errorCount = previousStatus.errorCount,
            recentEvents = previousStatus.recentEvents
        )
        createNotificationChannel()
    }

    // No SDK binding or frame may precede Android accepting foreground promotion.
    private fun startRuntime() {
        if (runtimeStarted) return
        runtimeStarted = true
        settingsStore = SettingsStore(this)
        glyphController = GlyphController(this)

        serviceScope.launch { eventLoop() }
        serviceScope.launch {
            val runtime = runCatching { settingsStore.readChargeCompleteTimerRuntime() }
                .onFailure { Log.e(TAG, "Unable to restore charge-complete timer", it) }
                .getOrDefault(ChargeCompleteTimerRuntime())
            events.send(ServiceEvent.RuntimeLoaded(runtime))
        }
        serviceScope.launch {
            settingsStore.settings
                .catch { error ->
                    Log.e(TAG, "Unable to read settings; using safe defaults", error)
                    emit(GlyphSettings())
                }
                .collect { settings -> events.send(ServiceEvent.SettingsChanged(settings)) }
        }
        serviceScope.launch {
            glyphController.connectionState.collect { connection ->
                events.send(ServiceEvent.ConnectionChanged(connection))
            }
        }
        serviceScope.launch {
            glyphController.runtimeFailures.collect {
                events.send(ServiceEvent.RuntimeRenderFailed)
            }
        }
        registerBatteryReceiver()
        glyphController.init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (runtimeStarted) events.trySend(ServiceEvent.UserStop) else stopSelfResult(startId)
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                events.trySend(ServiceEvent.ReconnectGlyph)
                return START_NOT_STICKY
            }
            ACTION_ZONE_SELF_TEST -> {
                events.trySend(ServiceEvent.ZoneSelfTest)
                return START_NOT_STICKY
            }
        }
        if (intent?.action != ACTION_START) {
            Log.w(TAG, "Ignoring non-explicit foreground-service restart")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        return try {
            startForeground(NOTIFICATION_ID, buildNotification())
            if (stopRequested || !ServiceRunState(this).requested) {
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            ServiceRunState(this).lastStartFailure = ""
            startRuntime()
            if (_status.value.health == ServiceHealth.STOPPED) {
                _status.value = BatteryGlyphServiceStatus(true, ServiceHealth.STARTING)
            }
            START_NOT_STICKY
        } catch (error: ForegroundServiceStartNotAllowedException) {
            ServiceRunState(this).lastStartFailure = error.javaClass.simpleName
            Log.e(TAG, "Android rejected the foreground-service start", error)
            stopSelfResult(startId)
            START_NOT_STICKY
        } catch (error: SecurityException) {
            ServiceRunState(this).lastStartFailure = error.javaClass.simpleName
            Log.e(TAG, "Foreground service permission rejected", error)
            stopSelfResult(startId)
            START_NOT_STICKY
        }
    }

    private suspend fun eventLoop() {
        var settings: GlyphSettings? = null
        var battery: BatterySnapshot? = null
        var connection: GlyphConnectionState = GlyphConnectionState.Idle
        var sdkReady = false
        var runtimeLoaded = false
        var serviceStopping = false

        var generalTimerExpired = false
        var generalTimerGeneration = 0L
        var generalTimerSessionActive = false
        var generalTimerJob: Job? = null
        var generalTimerConfiguration: TimerConfiguration? = null
        var generalTimerDeadlineEpochMillis: Long? = null

        val chargeTimer = ChargeCompleteTimerCoordinator()
        var chargeTimerJob: Job? = null
        var scheduledChargeDeadline: Long? = null

        var lastRenderedTarget: GlyphRenderTarget? = null
        var currentDecision: GlyphDecision? = null
        var consecutiveRenderFailures = 0
        var totalErrorCount = _status.value.errorCount
        var lastRenderResult = _status.value.lastRenderResult
        var lastRenderEpochMillis: Long? = _status.value.lastRenderEpochMillis
        var developerAction: String? = null
        val runtimeJournal = journal(this)
        val recentEvents = ArrayDeque<String>(20).apply {
            runCatching { runtimeJournal.read() }.getOrDefault(emptyList())
                .takeLast(20).forEach(::addLast)
        }

        fun recordEvent(message: String) {
            val entry = "${System.currentTimeMillis()} $message"
            if (recentEvents.size == 20) recentEvents.removeFirst()
            recentEvents.addLast(entry)
            // Only state transitions, not animation frames. Bounded file lives in noBackupFilesDir.
            runCatching { runtimeJournal.append(entry) }
                .onFailure { Log.w(TAG, "Unable to persist runtime event", it) }
            Log.d(TAG, message)
        }
        recordEvent("Runtime started; pid=${android.os.Process.myPid()}; background=${BackgroundSettings.read(this)}")

        fun targetType(target: GlyphRenderTarget?): DiagnosticRenderTarget = when (target) {
            null -> DiagnosticRenderTarget.UNKNOWN
            GlyphRenderTarget.Off -> DiagnosticRenderTarget.OFF
            is GlyphRenderTarget.Battery -> DiagnosticRenderTarget.BATTERY
            is GlyphRenderTarget.ChargeComplete -> DiagnosticRenderTarget.CHARGE_COMPLETE
        }

        fun publishStatus() {
            val health = if (consecutiveRenderFailures > 0) {
                ServiceHealth.ERROR
            } else {
                connection.toServiceHealth()
            }
            val chargeSnapshot = chargeTimer.snapshot()
            _status.value = BatteryGlyphServiceStatus(
                isRunning = true,
                health = health,
                sdkConnection = connection,
                target = targetType(currentDecision?.target),
                blockReason = currentDecision?.blockReason,
                batteryPercent = battery?.percent,
                isPluggedIn = battery?.isPluggedIn,
                generalTimerDeadlineEpochMillis = generalTimerDeadlineEpochMillis,
                chargeCompleteTimerDeadlineEpochMillis = chargeSnapshot.deadlineEpochMillis,
                chargeCompleteTimerExpired = chargeSnapshot.expired,
                lastRenderResult = lastRenderResult,
                lastRenderEpochMillis = lastRenderEpochMillis,
                errorCount = totalErrorCount,
                developerAction = developerAction,
                recentEvents = recentEvents.toList()
            )
        }

        val reconnectBackoff = com.nothing.glyphbattery.model.ReconnectBackoff()
        var reconnectGeneration = 0L
        var reconnectJob: Job? = null
        val chargingRecovery = ChargingSessionRecovery()
        var chargingRecoveryJob: Job? = null

        fun scheduleReconnect() {
            if (stopRequested || serviceStopping || reconnectJob?.isActive == true) return
            val generation = ++reconnectGeneration
            val wait = reconnectBackoff.nextDelayMillis()
            recordEvent("SDK reconnect scheduled in ${wait}ms")
            reconnectJob = serviceScope.launch {
                delay(wait)
                events.send(ServiceEvent.RetryConnection(generation))
            }
        }

        suspend fun reconnectGlyph(reason: String) {
            if (stopRequested || serviceStopping) return
            reconnectGeneration++
            reconnectJob?.cancel()
            reconnectJob = null
            sdkReady = false
            connection = GlyphConnectionState.Connecting
            lastRenderedTarget = null
            recordEvent("Glyph reconnect: $reason")
            publishStatus()
            if (!glyphController.reconnect()) scheduleReconnect()
        }

        fun recordRenderFailure() {
            lastRenderedTarget = null
            consecutiveRenderFailures++
            totalErrorCount++
            lastRenderResult = DiagnosticRenderResult.FAILED
            lastRenderEpochMillis = System.currentTimeMillis()
            recordEvent("Frame write failed (attempt $consecutiveRenderFailures)")
            if (consecutiveRenderFailures <= MAX_RENDER_RETRIES) {
                val failedAttempt = consecutiveRenderFailures
                val retryDelay = (250L * (1L shl (failedAttempt - 1)))
                    .coerceAtMost(MAX_RENDER_RETRY_DELAY_MS)
                serviceScope.launch {
                    delay(retryDelay)
                    events.send(ServiceEvent.RetryRender(failedAttempt))
                }
            } else {
                scheduleReconnect()
            }
        }

        fun generalTimerMinutes(value: GlyphSettings): Int {
            return if (value.autoOffTimer == AutoOffTimer.CUSTOM) {
                value.customAutoOffMinutes.coerceIn(1, 1440)
            } else {
                value.autoOffTimer.minutes
            }
        }

        fun shouldRunGeneralTimer(): Boolean {
            val value = settings ?: return false
            return value.serviceMode != ServiceMode.CHARGING_ONLY || battery?.isPluggedIn == true
        }

        fun synchronizeGeneralTimer(forceRestart: Boolean) {
            val value = settings ?: return
            // Wait for the first battery snapshot before clearing a restored charging session.
            if (value.serviceMode == ServiceMode.CHARGING_ONLY && battery == null) return
            val shouldRun = shouldRunGeneralTimer()
            val runState = ServiceRunState(this@BatteryGlyphService)

            if (!shouldRun) {
                runState.saveGeneralTimer(com.nothing.glyphbattery.model.GeneralTimerRuntime())
                generalTimerGeneration++
                generalTimerJob?.cancel()
                generalTimerJob = null
                generalTimerExpired = false
                generalTimerSessionActive = false
                generalTimerDeadlineEpochMillis = null
                return
            }
            if (generalTimerSessionActive && !forceRestart) return

            generalTimerGeneration++
            val generation = generalTimerGeneration
            generalTimerJob?.cancel()
            generalTimerJob = null
            generalTimerExpired = false
            generalTimerSessionActive = true

            val now = System.currentTimeMillis()
            val restored = com.nothing.glyphbattery.model.GeneralTimerRecovery.restoreOrStart(
                stored = runState.readGeneralTimer(),
                configuration = "${value.serviceMode}:${value.autoOffTimer}:${value.customAutoOffMinutes}",
                minutes = generalTimerMinutes(value),
                now = now
            )
            runState.saveGeneralTimer(restored)
            generalTimerDeadlineEpochMillis = restored.deadline.takeIf { it > 0L }
            generalTimerExpired = generalTimerDeadlineEpochMillis?.let { it <= now } ?: false
            generalTimerDeadlineEpochMillis?.takeUnless { generalTimerExpired }?.let { deadline ->
                generalTimerJob = serviceScope.launch {
                    delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
                    events.send(ServiceEvent.GeneralTimerExpired(generation))
                }
            }
        }

        suspend fun persistChargeTimer(update: ChargeCompleteTimerUpdate) {
            try {
                when (val persistence = update.persistence) {
                    ChargeCompleteTimerPersistence.None -> Unit
                    ChargeCompleteTimerPersistence.Clear -> settingsStore.clearChargeCompleteTimerRuntime()
                    is ChargeCompleteTimerPersistence.Save ->
                        settingsStore.saveChargeCompleteTimerRuntime(persistence.runtime)
                }
            } catch (error: Exception) {
                totalErrorCount++
                recordEvent("Charge-complete timer persistence failed: ${error.javaClass.simpleName}")
                Log.e(TAG, "Unable to persist charge-complete timer", error)
            }
        }

        fun scheduleChargeDeadline(update: ChargeCompleteTimerUpdate) {
            val deadline = update.deadlineEpochMillis
            if (deadline == scheduledChargeDeadline && chargeTimerJob?.isActive == true) return
            chargeTimerJob?.cancel()
            chargeTimerJob = null
            scheduledChargeDeadline = deadline
            if (deadline != null && !update.expired) {
                chargeTimerJob = serviceScope.launch {
                    delay((deadline - System.currentTimeMillis()).coerceAtLeast(0L))
                    events.send(ServiceEvent.ChargeCompleteDeadlineReached(deadline))
                }
            }
        }

        suspend fun synchronizeChargeTimer() {
            if (!runtimeLoaded) return
            val currentSettings = settings ?: return
            val currentBattery = battery ?: return
            val update = chargeTimer.synchronize(
                eligible = currentSettings.isChargeCompleteTimerEligible(currentBattery),
                preset = currentSettings.chargeCompleteTimerPreset,
                customMinutes = currentSettings.customChargeCompleteTimerMinutes
            )
            persistChargeTimer(update)
            scheduleChargeDeadline(update)
        }

        suspend fun renderIfNeeded(force: Boolean = false) {
            val currentSettings = settings ?: return
            val currentBattery = battery ?: return
            if (!runtimeLoaded) return

            val decision = GlyphStateReducer.reduce(
                settings = currentSettings,
                battery = currentBattery,
                timerExpired = generalTimerExpired,
                chargeCompleteTimerExpired = chargeTimer.snapshot().expired,
                serviceStopping = serviceStopping
            )
            currentDecision = decision
            publishStatus()
            if (!sdkReady) return
            if (!force && decision.target == lastRenderedTarget) return

            if (glyphController.render(decision.target)) {
                lastRenderedTarget = decision.target
                reconnectBackoff.reset()
                consecutiveRenderFailures = 0
                lastRenderResult = DiagnosticRenderResult.SUCCESS
                lastRenderEpochMillis = System.currentTimeMillis()
                recordEvent(
                    "Applied target ${targetType(decision.target)}" +
                        decision.blockReason?.let { " ($it)" }.orEmpty()
                )
            } else {
                recordRenderFailure()
            }
            publishStatus()
        }

        for (event in events) {
            var forceRender = false
            when (event) {
                is ServiceEvent.SettingsChanged -> {
                    val nextConfiguration = TimerConfiguration(
                        event.settings.autoOffTimer,
                        event.settings.customAutoOffMinutes,
                        event.settings.serviceMode
                    )
                    val timerChanged = generalTimerConfiguration != nextConfiguration
                    settings = event.settings
                    generalTimerConfiguration = nextConfiguration
                    synchronizeGeneralTimer(forceRestart = timerChanged)
                    synchronizeChargeTimer()
                    recordEvent("Settings updated")
                }

                is ServiceEvent.BatteryChanged -> {
                    val wasPluggedIn = battery?.isPluggedIn
                    battery = event.battery
                    val renewal = chargingRecovery.observe(event.battery.isPluggedIn)
                    if (wasPluggedIn != null && wasPluggedIn != event.battery.isPluggedIn) {
                        lastRenderedTarget = null
                        forceRender = true
                        chargingRecoveryJob?.cancel()
                        chargingRecoveryJob = null
                        recordEvent("Power edge: plugged=${event.battery.isPluggedIn}")
                    }
                    if (renewal != null) {
                        recordEvent("Charging session renewal scheduled")
                        chargingRecoveryJob = serviceScope.launch {
                            delay(POWER_SETTLE_MS)
                            events.send(ServiceEvent.RenewChargingSession(renewal))
                        }
                    }
                    if (settings?.serviceMode == ServiceMode.CHARGING_ONLY) {
                        val pluggedIn = event.battery.isPluggedIn
                        val chargingSessionChanged = wasPluggedIn != null && wasPluggedIn != pluggedIn
                        val timerNeedsStart = pluggedIn && !generalTimerSessionActive
                        val timerNeedsStop = !pluggedIn && generalTimerSessionActive
                        if (chargingSessionChanged || timerNeedsStart || timerNeedsStop) {
                            synchronizeGeneralTimer(forceRestart = pluggedIn)
                        }
                    }
                    synchronizeChargeTimer()
                    recordEvent(
                        "Battery ${event.battery.percent}%; plugged=${event.battery.isPluggedIn}; full=${event.battery.isFullStatus}"
                    )
                }

                is ServiceEvent.ConnectionChanged -> {
                    // Discard events queued by the previous binding during a reconnect.
                    if (event.snapshot.generation != glyphController.connectionState.value.generation) continue
                    connection = event.snapshot.state
                    sdkReady = connection == GlyphConnectionState.Ready
                    if (
                        developerAction == "RECONNECT" &&
                        connection != GlyphConnectionState.Idle &&
                        connection != GlyphConnectionState.Connecting
                    ) {
                        developerAction = null
                    }
                    recordEvent("SDK connection: $connection; generation=${event.snapshot.generation}")
                    if (sdkReady) {
                        reconnectGeneration++
                        reconnectJob?.cancel()
                        reconnectJob = null
                        // Binding alone is not a successful recovery; reset backoff on a frame.
                        lastRenderedTarget = null
                        forceRender = true
                    } else if (connection == GlyphConnectionState.Disconnected ||
                        connection == GlyphConnectionState.Error(GlyphConnectionError.BIND_FAILED) ||
                        connection == GlyphConnectionState.Error(GlyphConnectionError.SESSION_OPEN_FAILED)
                    ) {
                        scheduleReconnect()
                    } else if (connection is GlyphConnectionState.Error) {
                        // Authorization and unsupported hardware require user action, not retries.
                        reconnectGeneration++
                        reconnectJob?.cancel()
                        reconnectJob = null
                    }
                }

                is ServiceEvent.RuntimeLoaded -> {
                    runtimeLoaded = true
                    val restored = chargeTimer.restore(event.runtime)
                    scheduleChargeDeadline(restored)
                    synchronizeChargeTimer()
                    recordEvent(
                        "Charge-complete runtime restored; deadline=" +
                            (event.runtime.deadlineEpochMillis.takeIf { it > 0L } ?: "none")
                    )
                }

                is ServiceEvent.GeneralTimerExpired -> {
                    if (event.generation == generalTimerGeneration) {
                        generalTimerJob = null
                        generalTimerExpired = true
                        recordEvent("General auto-off expired")
                    }
                }

                is ServiceEvent.ChargeCompleteDeadlineReached -> {
                    val update = chargeTimer.deadlineReached(event.deadlineEpochMillis)
                    scheduleChargeDeadline(update)
                    if (update.expired) recordEvent("Charge-complete timer expired")
                }

                is ServiceEvent.RetryRender -> {
                    forceRender = event.failureCount == consecutiveRenderFailures && sdkReady
                    if (forceRender) recordEvent("Retrying frame write")
                }

                is ServiceEvent.RenewChargingSession -> {
                    if (stopRequested || serviceStopping || !chargingRecovery.consume(event.generation)) continue
                    chargingRecoveryJob = null
                    if (connection == GlyphConnectionState.Error(GlyphConnectionError.AUTHORIZATION_FAILED) ||
                        connection == GlyphConnectionState.Error(GlyphConnectionError.UNSUPPORTED_DEVICE)
                    ) {
                        recordEvent("Charging renewal skipped: $connection")
                    } else {
                        // Repair a silently stale session even when the cached target is equal.
                        // Do not restart the service or reset either timer's absolute deadline.
                        reconnectGlyph("power connected")
                    }
                }

                is ServiceEvent.RetryConnection -> {
                    if (event.generation != reconnectGeneration || stopRequested || serviceStopping) continue
                    reconnectGlyph("automatic retry")
                }

                ServiceEvent.RuntimeRenderFailed -> recordRenderFailure()

                ServiceEvent.ReconnectGlyph -> {
                    if (stopRequested || serviceStopping) continue
                    reconnectBackoff.reset()
                    developerAction = "RECONNECT"
                    reconnectGlyph("developer request")
                }

                ServiceEvent.ZoneSelfTest -> {
                    if (stopRequested || serviceStopping) continue
                    developerAction = "ZONE_SELF_TEST"
                    publishStatus()
                    recordEvent("Developer zone self-test started")
                    val succeeded = glyphController.runZoneSelfTest()
                    recordEvent("Developer zone self-test ${if (succeeded) "finished" else "failed"}")
                    if (!succeeded) totalErrorCount++
                    developerAction = null
                    if (!stopRequested) {
                        lastRenderedTarget = null
                        forceRender = true
                    }
                }

                ServiceEvent.UserStop -> {
                    serviceStopping = true
                    chargingRecoveryJob?.cancel()
                    chargingRecoveryJob = null
                    reconnectGeneration++
                    reconnectJob?.cancel()
                    reconnectJob = null
                    generalTimerGeneration++
                    generalTimerJob?.cancel()
                    generalTimerJob = null
                    generalTimerDeadlineEpochMillis = null
                    chargeTimerJob?.cancel()
                    chargeTimerJob = null
                    scheduledChargeDeadline = null
                    chargeTimer.reset()
                    try {
                        settingsStore.clearChargeCompleteTimerRuntime()
                    } catch (error: Exception) {
                        totalErrorCount++
                        Log.e(TAG, "Unable to clear timer during manual stop", error)
                    }
                    recordEvent("Manual service stop")
                    renderIfNeeded(force = true)
                    stopSelf()
                    continue
                }
            }
            renderIfNeeded(forceRender)
            publishStatus()
        }
    }

    private fun GlyphConnectionState.toServiceHealth(): ServiceHealth {
        return when (this) {
            GlyphConnectionState.Idle,
            GlyphConnectionState.Connecting -> ServiceHealth.STARTING
            GlyphConnectionState.Ready -> ServiceHealth.READY
            GlyphConnectionState.Disconnected -> ServiceHealth.DISCONNECTED
            GlyphConnectionState.Released -> ServiceHealth.STOPPED
            is GlyphConnectionState.Error -> when (reason) {
                GlyphConnectionError.AUTHORIZATION_FAILED -> ServiceHealth.AUTHORIZATION_FAILED
                GlyphConnectionError.UNSUPPORTED_DEVICE -> ServiceHealth.UNSUPPORTED_DEVICE
                GlyphConnectionError.SESSION_OPEN_FAILED,
                GlyphConnectionError.BIND_FAILED -> ServiceHealth.ERROR
            }
        }
    }

    private fun registerBatteryReceiver() {
        val stickyIntent = registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            Context.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        stickyIntent?.let(::publishBattery)
    }

    private fun publishBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        val fallback = (getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
            ?: 0
        val percent = if (level >= 0 && scale > 0) {
            (level * 100 / scale).coerceIn(0, 100)
        } else {
            fallback
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

        val snapshot = BatterySnapshot(
            percent = percent,
            isPluggedIn = plugged,
            isFullStatus = status == BatteryManager.BATTERY_STATUS_FULL
        )
        if (snapshot == lastPublishedBattery) return
        lastPublishedBattery = snapshot

        val enqueueResult = events.trySend(ServiceEvent.BatteryChanged(snapshot))
        if (enqueueResult.isFailure) {
            lastPublishedBattery = null
            Log.e(TAG, "Unable to enqueue battery update", enqueueResult.exceptionOrNull())
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = getString(R.string.notification_channel_description)
            }
        )
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(batteryReceiver) }
            receiverRegistered = false
        }
        serviceScope.cancel()
        if (::glyphController.isInitialized) glyphController.releaseBlocking()
        runCatching {
            journal(this).append("${System.currentTimeMillis()} Runtime destroyed; userStop=$stopRequested")
        }.onFailure { Log.w(TAG, "Unable to persist runtime shutdown", it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopRequested = false
        _status.value = _status.value.copy(
            isRunning = false,
            health = ServiceHealth.STOPPED,
            sdkConnection = GlyphConnectionState.Released,
            target = DiagnosticRenderTarget.OFF,
            blockReason = GlyphBlockReason.SERVICE_STOPPING,
            developerAction = null
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
