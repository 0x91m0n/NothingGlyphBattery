package com.nothing.glyphbattery.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.glyphbattery.model.AnimationMode
import com.nothing.glyphbattery.model.GlyphRenderTarget
import com.nothing.glyphbattery.model.GlyphSettings
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

sealed interface GlyphConnectionState {
    data object Idle : GlyphConnectionState
    data object Connecting : GlyphConnectionState
    data object Ready : GlyphConnectionState
    data object Disconnected : GlyphConnectionState
    data object Released : GlyphConnectionState
    data class Error(val reason: GlyphConnectionError) : GlyphConnectionState
}

/** A fresh Ready must not be conflated with Ready from a previous SDK binding. */
data class GlyphConnectionSnapshot(val generation: Long, val state: GlyphConnectionState)

enum class GlyphConnectionError {
    UNSUPPORTED_DEVICE,
    AUTHORIZATION_FAILED,
    SESSION_OPEN_FAILED,
    BIND_FAILED
}

/**
 * The only owner of Glyph SDK commands.
 *
 * All frame writes run on one dedicated dispatcher. The bundled SDK's toggle()/turnOff()
 * methods enqueue cancellable Futures internally, so this controller intentionally uses the
 * synchronous setFrameColors() API and always cancels and joins an old animation before writing
 * the next state.
 */
class GlyphController(context: Context) {
    companion object {
        private const val TAG = "GlyphController"
    }

    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val connectionGeneration = AtomicLong(0L)
    private val rendererDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "GlyphRenderer").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    private val rendererScope = CoroutineScope(SupervisorJob() + rendererDispatcher)

    private val _connectionState = MutableStateFlow(GlyphConnectionSnapshot(0L, GlyphConnectionState.Idle))
    val connectionState: StateFlow<GlyphConnectionSnapshot> = _connectionState.asStateFlow()

    private fun publishConnection(generation: Long, state: GlyphConnectionState) {
        if (generation == connectionGeneration.get()) {
            _connectionState.value = GlyphConnectionSnapshot(generation, state)
        }
    }

    private val _runtimeFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val runtimeFailures: SharedFlow<Unit> = _runtimeFailures.asSharedFlow()

    @Volatile
    private var glyphManager: GlyphManager? = null

    @Volatile
    private var sessionReady = false

    private var animationJob: Job? = null

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        val generation = connectionGeneration.incrementAndGet()
        publishConnection(generation, GlyphConnectionState.Connecting)

        try {
            val manager = GlyphManager.getInstance(appContext)
            glyphManager = manager
            manager.init(object : GlyphManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    if (released.get() || generation != connectionGeneration.get()) return
                    if (!Common.is24111()) {
                        sessionReady = false
                        publishConnection(generation, GlyphConnectionState.Error(
                            GlyphConnectionError.UNSUPPORTED_DEVICE
                        ))
                        return
                    }

                    val authorized = runCatching {
                        manager.register(Glyph.DEVICE_24111)
                    }.onFailure { error ->
                        Log.e(TAG, "Glyph authorization failed", error)
                    }.getOrDefault(false)

                    if (!authorized) {
                        sessionReady = false
                        publishConnection(generation, GlyphConnectionState.Error(
                            GlyphConnectionError.AUTHORIZATION_FAILED
                        ))
                        return
                    }

                    try {
                        manager.openSession()
                        if (released.get() || generation != connectionGeneration.get()) {
                            runCatching { manager.closeSession() }
                            return
                        }
                        sessionReady = true
                        publishConnection(generation, GlyphConnectionState.Ready)
                        Log.i(TAG, "Glyph session is ready")
                    } catch (error: Exception) {
                        sessionReady = false
                        publishConnection(generation, GlyphConnectionState.Error(
                            GlyphConnectionError.SESSION_OPEN_FAILED
                        ))
                        Log.e(TAG, "Unable to open Glyph session", error)
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    if (generation != connectionGeneration.get()) return
                    sessionReady = false
                    rendererScope.launch { stopAnimationInternal() }
                    publishConnection(generation, if (released.get()) {
                        GlyphConnectionState.Released
                    } else {
                        GlyphConnectionState.Disconnected
                    })
                    Log.w(TAG, "Glyph system service disconnected")
                }
            })
            rendererScope.launch {
                delay(5_000L)
                if (
                    generation == connectionGeneration.get() &&
                    _connectionState.value.state == GlyphConnectionState.Connecting &&
                    !released.get()
                ) {
                    publishConnection(generation, GlyphConnectionState.Error(
                        GlyphConnectionError.BIND_FAILED
                    ))
                    Log.e(TAG, "Timed out while binding Glyph system service")
                }
            }
        } catch (error: Exception) {
            initialized.set(false)
            publishConnection(generation, GlyphConnectionState.Error(GlyphConnectionError.BIND_FAILED))
            Log.e(TAG, "Unable to bind Glyph system service", error)
        }
    }

    suspend fun render(target: GlyphRenderTarget): Boolean = withContext(rendererDispatcher) {
        stopAnimationInternal()
        if (!sessionReady || released.get()) return@withContext false

        when (target) {
            GlyphRenderTarget.Off -> writeFrame(GlyphFrames.off())
            is GlyphRenderTarget.ChargeComplete -> writeFrame(
                GlyphFrames.chargeComplete(target.settings)
            )
            is GlyphRenderTarget.Battery -> renderBattery(target.percent, target.settings)
        }
    }

    /**
     * Runs on the same renderer queue as every normal frame. It leaves re-applying the desired
     * target to the service event loop, which owns that state.
     */
    suspend fun runZoneSelfTest(): Boolean = withContext(rendererDispatcher) {
        stopAnimationInternal()
        if (!sessionReady || released.get()) return@withContext false

        val intensity = GlyphFrames.intensity(25)
        val groups = listOf(
            GlyphFrames.A_CHANNELS,
            GlyphFrames.B_CHANNELS,
            GlyphFrames.C_CHANNELS
        )
        for (group in groups) {
            if (!writeFrame(GlyphFrames.channels(group, intensity))) {
                writeOffBestEffort()
                return@withContext false
            }
            delay(700L)
        }
        true
    }

    /** Safely replaces the SDK binding without destroying the single renderer queue. */
    suspend fun reconnect(): Boolean = withContext(rendererDispatcher) {
        if (released.get()) return@withContext false

        stopAnimationInternal()
        writeOffBestEffort()
        val oldManager = glyphManager
        sessionReady = false
        connectionGeneration.incrementAndGet()
        runCatching { oldManager?.closeSession() }
            .onFailure { Log.e(TAG, "Closing Glyph session before reconnect failed", it) }
        glyphManager = null
        initialized.set(false)

        withContext(Dispatchers.Main.immediate) {
            runCatching { oldManager?.unInit() }
                .onFailure { Log.e(TAG, "Unbinding Glyph service before reconnect failed", it) }
            init()
        }
        true
    }

    private fun renderBattery(percent: Int, settings: GlyphSettings): Boolean {
        if (settings.animationMode == AnimationMode.NONE) {
            return writeFrame(GlyphFrames.static(percent, settings))
        }

        if (settings.animationMode.keepsFilledLeds) {
            val (_, next) = GlyphFrames.progressChannels(percent, settings)
            if (next == null) return writeFrame(GlyphFrames.progress(percent, settings, 0))
            if (!writeFrame(GlyphFrames.progress(percent, settings, 0))) return false
        }

        val activeChannels = GlyphFrames.activeChannels(percent, settings)
        if (activeChannels.isEmpty()) {
            return writeFrame(GlyphFrames.off())
        }

        // Confirm that the session can accept a complete frame before reporting success to the
        // service. Later animation frames still use the same serialized renderer thread.
        if (!settings.animationMode.keepsFilledLeds &&
            !writeFrame(GlyphFrames.static(percent, settings))) return false

        animationJob = rendererScope.launch {
            try {
                runAnimation(percent, settings)
            } catch (_: CancellationException) {
                // A newer render target owns the next frame.
            } catch (error: Exception) {
                Log.e(TAG, "Glyph animation failed", error)
                writeFrame(GlyphFrames.off())
                _runtimeFailures.tryEmit(Unit)
            }
        }
        return true
    }

    private suspend fun runAnimation(percent: Int, settings: GlyphSettings) {
        when (settings.animationMode) {
            AnimationMode.BREATHING -> animateBreathing(percent, settings)
            AnimationMode.BLINKING -> animateBlinking(percent, settings)
            AnimationMode.WAVE -> animateWave(percent, settings)
            AnimationMode.HOURGLASS -> animateHourglass(percent, settings)
            AnimationMode.SMOOTH_FILL -> animateSmoothFill(percent, settings)
            AnimationMode.NONE -> Unit
            AnimationMode.PROGRESS_BREATHING,
            AnimationMode.PROGRESS_BLINKING,
            AnimationMode.PROGRESS_WAVE,
            AnimationMode.PROGRESS_HOURGLASS,
            AnimationMode.PROGRESS_SMOOTH_FILL -> animateProgress(percent, settings)
        }
    }

    private suspend fun animateProgress(percent: Int, settings: GlyphSettings) {
        val maxIntensity = GlyphFrames.intensity(settings.brightness)
        var elapsed = 0L
        while (currentCoroutineContext().isActive) {
            val factor = ProgressAnimation.factor(settings.animationMode, elapsed)
            requireFrameWrite(GlyphFrames.progress(percent, settings, (factor * maxIntensity).roundToInt()))
            delay(delayMs(ProgressAnimation.FRAME_MS, settings))
            elapsed += ProgressAnimation.FRAME_MS
        }
    }

    private suspend fun animateBreathing(percent: Int, settings: GlyphSettings) {
        val groups = GlyphFrames.activeZoneGroups(percent, settings)
        val maxIntensity = GlyphFrames.intensity(settings.brightness)
        val stepDelay = delayMs(40, settings)
        val steps = 50

        while (currentCoroutineContext().isActive) {
            for (group in groups) {
                for (step in 0 until steps) {
                    currentCoroutineContext().ensureActive()
                    val phase = step.toDouble() / steps
                    val factor = (sin(phase * 2 * Math.PI - Math.PI / 2) + 1) / 2
                    val intensity = (factor * maxIntensity).roundToInt().coerceIn(0, maxIntensity)
                    requireFrameWrite(GlyphFrames.channels(group, intensity))
                    delay(stepDelay)
                }
            }
            delay(delayMs(200, settings))
        }
    }

    private suspend fun animateBlinking(percent: Int, settings: GlyphSettings) {
        val onFrame = GlyphFrames.static(percent, settings)
        val interval = delayMs(500, settings)
        while (currentCoroutineContext().isActive) {
            requireFrameWrite(onFrame)
            delay(interval)
            requireFrameWrite(GlyphFrames.off())
            delay(interval)
        }
    }

    private suspend fun animateWave(percent: Int, settings: GlyphSettings) {
        val channels = GlyphFrames.activeChannels(percent, settings)
        val intensity = GlyphFrames.intensity(settings.brightness)
        val windowSize = (channels.size / 3).coerceAtLeast(1)
        val stepDelay = delayMs(80, settings)
        var position = 0
        var direction = 1

        while (currentCoroutineContext().isActive) {
            val window = (0 until windowSize).map { offset ->
                channels[(position + offset).coerceIn(0, channels.lastIndex)]
            }
            requireFrameWrite(GlyphFrames.channels(window, intensity))
            delay(stepDelay)
            position += direction
            if (position >= channels.size - windowSize || position <= 0) direction = -direction
        }
    }

    private suspend fun animateHourglass(percent: Int, settings: GlyphSettings) {
        val channels = GlyphFrames.activeChannels(percent, settings)
        val intensity = GlyphFrames.intensity(settings.brightness)
        val stepDelay = delayMs(80, settings)

        while (currentCoroutineContext().isActive) {
            for (index in channels.indices) {
                requireFrameWrite(GlyphFrames.channels(channels.take(index + 1), intensity))
                delay(stepDelay)
            }
            delay(delayMs(400, settings))
            for (index in channels.indices) {
                requireFrameWrite(GlyphFrames.channels(channels.drop(index + 1), intensity))
                delay(stepDelay)
            }
            delay(delayMs(250, settings))
        }
    }

    private suspend fun animateSmoothFill(percent: Int, settings: GlyphSettings) {
        val channels = GlyphFrames.activeChannels(percent, settings)
        val maxIntensity = GlyphFrames.intensity(settings.brightness)
        val rampSteps = 5
        val rampDelay = delayMs(35, settings)

        while (currentCoroutineContext().isActive) {
            for (index in channels.indices) {
                for (step in 1..rampSteps) {
                    currentCoroutineContext().ensureActive()
                    val frame = GlyphFrames.channels(channels.take(index), maxIntensity)
                    frame[channels[index]] = maxIntensity * step / rampSteps
                    requireFrameWrite(frame)
                    delay(rampDelay)
                }
            }
            delay(delayMs(800, settings))
            requireFrameWrite(GlyphFrames.off())
            delay(delayMs(300, settings))
        }
    }

    private fun delayMs(baseMs: Long, settings: GlyphSettings): Long {
        return (baseMs * 100L / settings.animationSpeed.coerceIn(25, 200)).coerceAtLeast(10L)
    }

    private fun requireFrameWrite(frame: IntArray) {
        if (!writeFrame(frame)) {
            if (released.get() || !sessionReady) {
                throw CancellationException("Glyph session is closing")
            }
            error("Glyph frame write failed")
        }
    }

    private fun writeFrame(frame: IntArray, duringRelease: Boolean = false): Boolean {
        if (!sessionReady || (released.get() && !duringRelease)) return false
        if (frame.size != GlyphFrames.CHANNEL_COUNT) {
            Log.e(TAG, "Rejected invalid Glyph frame with ${frame.size} channels")
            return false
        }
        val manager = glyphManager ?: return false
        return try {
            manager.setFrameColors(frame)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to write Glyph frame", error)
            false
        }
    }

    private suspend fun writeOffBestEffort(duringRelease: Boolean = false): Boolean {
        if (!sessionReady) return true
        repeat(3) { attempt ->
            if (writeFrame(GlyphFrames.off(), duringRelease)) return true
            if (attempt < 2) delay(50L)
        }
        return false
    }

    private suspend fun stopAnimationInternal() {
        val runningAnimation = animationJob
        animationJob = null
        runningAnimation?.cancelAndJoin()
    }

    /** Called from Service.onDestroy on the main thread. */
    fun releaseBlocking() {
        if (!released.compareAndSet(false, true)) return
        connectionGeneration.incrementAndGet()

        runBlocking(rendererDispatcher) {
            stopAnimationInternal()
            val manager = glyphManager
            if (sessionReady && manager != null) {
                // Only this final zero frame may bypass the released guard, after cancelAndJoin.
                if (!writeOffBestEffort(duringRelease = true)) {
                    Log.e(TAG, "Final Glyph shutdown failed after three attempts")
                }
                runCatching { manager.closeSession() }
                    .onFailure { Log.e(TAG, "Closing Glyph session failed", it) }
            }
            sessionReady = false
        }

        val manager = glyphManager
        glyphManager = null
        runCatching { manager?.unInit() }
            .onFailure { Log.e(TAG, "Unbinding Glyph service failed", it) }
        publishConnection(connectionGeneration.get(), GlyphConnectionState.Released)
        rendererScope.cancel()
        rendererDispatcher.close()
    }
}
