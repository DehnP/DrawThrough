package com.example.drawthrough.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.drawthrough.ar.ArBackgroundRenderer
import com.example.drawthrough.ar.ArOverlayQuadRenderer
import com.example.drawthrough.ar.ArRenderParams
import com.example.drawthrough.ar.ArSurfaceController
import com.example.drawthrough.ar.ArSurfaceMath
import com.example.drawthrough.ar.ArSurfaceState
import com.example.drawthrough.ar.ArSurfaceStatus
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.EnumSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

private const val TAG = "ArSurfaceCameraLayer"
private const val INSTANT_PLACEMENT_FALLBACK_DELAY_MS = 900L
private const val APPROXIMATE_PLANE_UPGRADE_WINDOW_MS = 2500L
private const val DEFAULT_PAPER_DISTANCE_METERS = 0.38f
private const val CAMERA_OPEN_MAX_RETRIES = 6
private const val CAMERA_OPEN_RETRY_DELAY_MS = 160L

/**
 * ARCore-backed live camera and 3D planar reference image overlay.
 *
 * Renders both the ARCore camera background and the translucent reference image in the same
 * OpenGL ES frame using ARCore's synchronized View/Projection matrices for zero-lag surface lock.
 */
@Composable
fun ArSurfaceCameraLayer(
    renderParams: ArRenderParams,
    controller: ArSurfaceController,
    onStatusChanged: (ArSurfaceStatus) -> Unit,
    onPlaneOffsetChanged: (Boolean) -> Unit,
    onUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestStatusCallback by rememberUpdatedState(onStatusChanged)
    val latestOffsetCallback by rememberUpdatedState(onPlaneOffsetChanged)
    val latestUnavailableCallback by rememberUpdatedState(onUnavailable)

    // Keep controller render parameters synchronized with latest Compose state
    controller.updateParams(renderParams)

    val arHost = remember(context, controller) {
        ArSessionHost(
            context = context,
            controller = controller,
            onStatusChanged = { latestStatusCallback(it) },
            onPlaneOffsetChanged = { latestOffsetCallback(it) },
            onUnavailable = { latestUnavailableCallback(it) },
        )
    }

    val glSurfaceView = remember(context, arHost) {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(arHost)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    // Start ARCore session once composed (with brief retry for CameraX -> ARCore handoff)
    LaunchedEffect(arHost, glSurfaceView) {
        arHost.attachView(glSurfaceView)
        arHost.ensureSessionStarted()
    }

    // Keep display rotation synced even across 180-degree flips where onSurfaceChanged isn't called
    DisposableEffect(context, arHost) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                arHost.onDisplayChanged()
            }
        }
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        var wasPausedByLifecycle = false
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPausedByLifecycle = true
                    arHost.onHostPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPausedByLifecycle) {
                        wasPausedByLifecycle = false
                        arHost.onHostResume()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            displayManager?.unregisterDisplayListener(displayListener)
            arHost.release()
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Thread-safe manager combining ARCore [Session] lifecycle and [GLSurfaceView.Renderer].
 */
private class ArSessionHost(
    private val context: Context,
    private val controller: ArSurfaceController,
    private val onStatusChanged: (ArSurfaceStatus) -> Unit,
    private val onPlaneOffsetChanged: (Boolean) -> Unit,
    private val onUnavailable: (String) -> Unit,
) : GLSurfaceView.Renderer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionLock = Any()

    private val backgroundRenderer = ArBackgroundRenderer()
    private val quadRenderer = ArOverlayQuadRenderer()

    private var glSurfaceView: GLSurfaceView? = null
    private var session: Session? = null
    private var isSessionResumed: Boolean = false
    private var isClosed: Boolean = false
    private var userRequestedInstall: Boolean = true
    private var cameraTextureBound: Boolean = false

    @Volatile
    private var viewportWidth: Int = 0

    @Volatile
    private var viewportHeight: Int = 0

    @Volatile
    private var viewportDirty: Boolean = true

    // Anchor & surface-local state (GL thread only, protected by sessionLock when modifying Anchor)
    private var anchor: Anchor? = null
    private var isApproximateAnchor: Boolean = false
    private var anchorCreatedMs: Long = 0L
    private var trackingStartedMs: Long = 0L
    private var baseYawDeg: Float = 0f
    private var baseWidthMeters: Float = 0.22f
    private var baseHeightMeters: Float = 0.22f
    private var sizedForAspectRatio: Float? = null
    private var planeOffsetX: Float = 0f
    private var planeOffsetZ: Float = 0f
    private var userHasDraggedSinceAnchor: Boolean = false
    private var appliedAnchorVersion: Int = controller.renderParams.anchorVersion
    private var appliedResetVersion: Int = controller.renderParams.resetVersion
    private var appliedTorchEnabled: Boolean = false

    // Last reported UI state (avoid flooding main thread)
    private var lastReportedStatus: ArSurfaceStatus? = null
    private var lastReportedHasOffset: Boolean = false

    // Reusable matrices on GL thread (zero per-frame allocations)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val anchorMatrix = FloatArray(16)

    fun attachView(view: GLSurfaceView) {
        glSurfaceView = view
    }

    fun onDisplayChanged() {
        viewportDirty = true
    }

    suspend fun ensureSessionStarted() {
        if (isClosed) return
        postStatus(ArSurfaceStatus(ArSurfaceState.INITIALIZING, "Starting AR…"))

        val activity = context.findActivity()
        if (activity == null) {
            postUnavailable("Unable to access activity for AR")
            return
        }

        // Wait briefly for transient availability check on first launch
        var availability = ArCoreApk.getInstance().checkAvailability(context)
        var checks = 0
        while (availability.isTransient && checks < 10 && !isClosed) {
            delay(200L)
            availability = ArCoreApk.getInstance().checkAvailability(context)
            checks++
        }
        if (isClosed) return

        if (!availability.isSupported) {
            postUnavailable("AR Surface Pin isn't supported on this device")
            return
        }

        // Retry loop accommodates brief CameraX -> ARCore Camera2 device handoff
        for (attempt in 0 until CAMERA_OPEN_MAX_RETRIES) {
            if (isClosed) return
            when (val result = tryCreateAndResumeSession(activity)) {
                StartResult.STARTED -> {
                    glSurfaceView?.onResume()
                    postStatus(
                        ArSurfaceStatus(
                            state = ArSurfaceState.SEARCHING,
                            hint = "Point at paper & move slightly",
                        ),
                    )
                    return
                }
                StartResult.INSTALL_REQUESTED -> {
                    postStatus(
                        ArSurfaceStatus(
                            state = ArSurfaceState.INITIALIZING,
                            hint = "Finish installing AR Services…",
                        ),
                    )
                    return
                }
                StartResult.RETRY_CAMERA -> {
                    if (attempt < CAMERA_OPEN_MAX_RETRIES - 1) {
                        delay(CAMERA_OPEN_RETRY_DELAY_MS)
                    } else {
                        postUnavailable("Camera was busy starting AR — tap AR Pin to retry")
                        return
                    }
                }
                is StartResult.FAILED -> {
                    postUnavailable(result.message)
                    return
                }
            }
        }
    }

    fun onHostResume() {
        if (isClosed) return
        val activity = context.findActivity() ?: return
        val result = tryCreateAndResumeSession(activity)
        when (result) {
            StartResult.STARTED -> {
                glSurfaceView?.onResume()
                if (anchor == null) {
                    postStatus(
                        ArSurfaceStatus(
                            state = ArSurfaceState.SEARCHING,
                            hint = "Point at paper & move slightly",
                        ),
                    )
                }
            }
            StartResult.INSTALL_REQUESTED -> Unit
            StartResult.RETRY_CAMERA -> {
                mainHandler.postDelayed({
                    if (!isClosed) onHostResume()
                }, CAMERA_OPEN_RETRY_DELAY_MS)
            }
            is StartResult.FAILED -> postUnavailable(result.message)
        }
    }

    fun onHostPause() {
        glSurfaceView?.onPause()
        synchronized(sessionLock) {
            if (isSessionResumed) {
                runCatching { session?.pause() }
                isSessionResumed = false
            }
        }
    }

    fun release() {
        isClosed = true
        // Pause GLSurfaceView first so onDrawFrame finishes before session.close()
        glSurfaceView?.onPause()
        synchronized(sessionLock) {
            detachAnchorLocked()
            if (isSessionResumed) {
                runCatching { session?.pause() }
                isSessionResumed = false
            }
            runCatching { session?.close() }
            session = null
            cameraTextureBound = false
        }
        controller.clearQueues()
        glSurfaceView = null
    }

    private sealed interface StartResult {
        data object STARTED : StartResult
        data object INSTALL_REQUESTED : StartResult
        data object RETRY_CAMERA : StartResult
        data class FAILED(val message: String) : StartResult
    }

    private fun tryCreateAndResumeSession(activity: Activity): StartResult {
        synchronized(sessionLock) {
            if (isClosed) return StartResult.FAILED("AR session closed")

            try {
                if (session == null) {
                    val installStatus = ArCoreApk.getInstance().requestInstall(
                        activity,
                        userRequestedInstall,
                    )
                    if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                        userRequestedInstall = false
                        return StartResult.INSTALL_REQUESTED
                    }
                    val created = Session(activity)
                    configureSessionLocked(created, controller.renderParams.isTorchEnabled)
                    session = created
                    cameraTextureBound = false
                    viewportDirty = true
                }

                if (!isSessionResumed) {
                    session?.resume()
                    isSessionResumed = true
                }
                return StartResult.STARTED
            } catch (_: UnavailableUserDeclinedInstallationException) {
                return StartResult.FAILED("Google Play Services for AR is required for AR Pin")
            } catch (_: UnavailableArcoreNotInstalledException) {
                return StartResult.FAILED("Install Google Play Services for AR to use AR Pin")
            } catch (_: UnavailableApkTooOldException) {
                return StartResult.FAILED("Update Google Play Services for AR to use AR Pin")
            } catch (_: UnavailableSdkTooOldException) {
                return StartResult.FAILED("Update DrawThrough to use AR Pin")
            } catch (_: UnavailableDeviceNotCompatibleException) {
                return StartResult.FAILED("AR Surface Pin isn't supported on this device")
            } catch (e: CameraNotAvailableException) {
                Log.w(TAG, "Camera temporarily unavailable for ARCore; retrying", e)
                return StartResult.RETRY_CAMERA
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ARCore session", e)
                return StartResult.FAILED(e.message ?: "Unable to start AR Surface Pin")
            }
        }
    }

    private fun configureSessionLocked(arSession: Session, torchEnabled: Boolean) {
        // Prefer 60fps rear camera config for crisp, responsive tracing if the device supports it
        runCatching {
            val filter = CameraConfigFilter(arSession).apply {
                facingDirection = CameraConfig.FacingDirection.BACK
                targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)
            }
            val configs60 = arSession.getSupportedCameraConfigs(filter)
            if (configs60.isNotEmpty()) {
                val targetPixels = 1920 * 1080
                val best = configs60.minByOrNull { cfg ->
                    abs(cfg.textureSize.width * cfg.textureSize.height - targetPixels)
                } ?: configs60.first()
                arSession.cameraConfig = best
            }
        }

        val config = arSession.config.apply {
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            // AUTO focus is critical for close-up drawing paper (~20-50 cm)
            focusMode = Config.FocusMode.AUTO
            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            depthMode = Config.DepthMode.DISABLED
            flashMode = if (torchEnabled) Config.FlashMode.TORCH else Config.FlashMode.OFF
        }
        arSession.configure(config)
        appliedTorchEnabled = torchEnabled
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.11f, 0.11f, 0.10f, 1.0f)
        backgroundRenderer.createOnGlThread()
        quadRenderer.createOnGlThread()
        synchronized(sessionLock) {
            cameraTextureBound = false
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        viewportDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val params = controller.renderParams

        synchronized(sessionLock) {
            val arSession = session
            if (isClosed || arSession == null || !isSessionResumed) return

            if (!cameraTextureBound && backgroundRenderer.cameraTextureId != -1) {
                arSession.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraTextureId))
                cameraTextureBound = true
            }

            if (viewportDirty && viewportWidth > 0 && viewportHeight > 0) {
                arSession.setDisplayGeometry(getDisplayRotation(context), viewportWidth, viewportHeight)
                viewportDirty = false
            }

            // Apply torch changes safely inside sessionLock
            if (params.isTorchEnabled != appliedTorchEnabled) {
                runCatching {
                    val cfg = arSession.config
                    cfg.flashMode = if (params.isTorchEnabled) Config.FlashMode.TORCH else Config.FlashMode.OFF
                    arSession.configure(cfg)
                }
                appliedTorchEnabled = params.isTorchEnabled
            }

            // Handle explicit Re-pin request
            if (params.anchorVersion != appliedAnchorVersion) {
                appliedAnchorVersion = params.anchorVersion
                detachAnchorLocked()
                controller.clearQueues()
                resetOffsetsInternal()
                trackingStartedMs = 0L
                sizedForAspectRatio = null
            }

            // Handle Reset position request
            if (params.resetVersion != appliedResetVersion) {
                appliedResetVersion = params.resetVersion
                resetOffsetsInternal()
            }

            val frame = try {
                arSession.update()
            } catch (e: CameraNotAvailableException) {
                Log.w(TAG, "Camera not available during frame update", e)
                postStatus(ArSurfaceStatus(ArSurfaceState.TRACKING_LIMITED, "Camera busy…"))
                return
            } catch (e: Exception) {
                Log.w(TAG, "ARCore update skipped", e)
                return
            }

            // 1. Always render the live AR camera background first
            backgroundRenderer.draw(frame)

            val camera = frame.camera
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 30f)

            val cameraTracking = camera.trackingState
            val nowMs = SystemClock.elapsedRealtime()

            // Drop dead anchors
            if (anchor?.trackingState == TrackingState.STOPPED) {
                detachAnchorLocked()
                trackingStartedMs = 0L
            }

            // 2. Anchor creation / gentle approximate->plane upgrade
            if (cameraTracking == TrackingState.TRACKING) {
                if (anchor == null) {
                    if (trackingStartedMs == 0L) trackingStartedMs = nowMs
                    val tapEvent = controller.pendingTapAnchors.poll()
                    val hitX = tapEvent?.x ?: (viewportWidth * 0.5f)
                    val hitY = tapEvent?.y ?: (viewportHeight * 0.5f)
                    val allowInstant = tapEvent != null ||
                        (nowMs - trackingStartedMs >= INSTANT_PLACEMENT_FALLBACK_DELAY_MS)

                    val placement = findBestSurfaceHit(frame, hitX, hitY, allowInstant)
                    if (placement != null) {
                        createSurfaceAnchorLocked(placement.first, placement.second, frame, params, nowMs)
                    } else {
                        postStatus(
                            ArSurfaceStatus(
                                state = ArSurfaceState.SEARCHING,
                                hint = "Point at paper & move slightly",
                            ),
                        )
                    }
                } else if (
                    isApproximateAnchor &&
                    !params.isLocked &&
                    !userHasDraggedSinceAnchor &&
                    nowMs - anchorCreatedMs <= APPROXIMATE_PLANE_UPGRADE_WINDOW_MS
                ) {
                    // Upgrade approximate placement to a true detected Plane before user starts drawing
                    val exactPlaneHit = findExactPlaneHit(frame, viewportWidth * 0.5f, viewportHeight * 0.5f)
                    if (exactPlaneHit != null) {
                        detachAnchorLocked()
                        createSurfaceAnchorLocked(exactPlaneHit, false, frame, params, nowMs)
                    }
                }
            } else {
                // Camera tracking paused — discard queued taps and report artist-friendly reason
                controller.pendingTapAnchors.clear()
                val hint = trackingFailureHint(camera.trackingFailureReason)
                if (anchor != null) {
                    postStatus(
                        ArSurfaceStatus(
                            state = ArSurfaceState.TRACKING_LIMITED,
                            hint = hint,
                            isApproximateAnchor = isApproximateAnchor,
                        ),
                    )
                } else {
                    postStatus(ArSurfaceStatus(ArSurfaceState.SEARCHING, hint))
                }
                return
            }

            // 3. Render anchored reference image (or surface reticle if no photo chosen yet)
            val currentAnchor = anchor ?: return
            if (currentAnchor.trackingState != TrackingState.TRACKING) {
                postStatus(
                    ArSurfaceStatus(
                        state = ArSurfaceState.TRACKING_LIMITED,
                        hint = "Re-acquiring surface…",
                        isApproximateAnchor = isApproximateAnchor,
                    ),
                )
                return
            }

            postStatus(
                ArSurfaceStatus(
                    state = ArSurfaceState.ANCHORED,
                    isApproximateAnchor = isApproximateAnchor,
                ),
            )

            val anchorPose = currentAnchor.pose
            anchorPose.toMatrix(anchorMatrix, 0)

            // If user picked a photo (or switched to one with a different aspect ratio), size cleanly
            val bitmap = params.bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
                val currentSizedAspect = sizedForAspectRatio
                if (currentSizedAspect == null || abs(aspect - currentSizedAspect) > 0.02f) {
                    val camPos = ArSurfaceMath.Vec3.fromArray(camera.pose.translation)
                    val anchorPos = ArSurfaceMath.Vec3.fromArray(anchorPose.translation)
                    val distance = (camPos - anchorPos).length()
                    val (w, h) = ArSurfaceMath.computeInitialQuadSizeMeters(
                        distanceMeters = distance,
                        projectionMatrix = projectionMatrix,
                        bitmapWidth = bitmap.width,
                        bitmapHeight = bitmap.height,
                    )
                    baseWidthMeters = w
                    baseHeightMeters = h
                    sizedForAspectRatio = aspect
                }
            }

            // Process queued surface-plane drag gestures
            processPendingDrags(anchorPose, camera.pose.translation)

            // Build Model-View-Projection matrix for the 3D surface-anchored quad
            val quadWidth = if (bitmap != null) baseWidthMeters * params.scale else 0.16f
            val quadHeight = if (bitmap != null) baseHeightMeters * params.scale else 0.16f
            val modelMatrix = ArSurfaceMath.buildQuadModelMatrix(
                anchorMatrix = anchorMatrix,
                baseYawDeg = baseYawDeg,
                planeOffsetX = if (bitmap != null) planeOffsetX else 0f,
                planeOffsetZ = if (bitmap != null) planeOffsetZ else 0f,
                userRotationDeg = if (bitmap != null) params.rotationDeg else 0f,
                widthMeters = quadWidth,
                heightMeters = quadHeight,
            )
            val viewModel = ArSurfaceMath.multiplyMatrix4x4(viewMatrix, modelMatrix)
            val mvpMatrix = ArSurfaceMath.multiplyMatrix4x4(projectionMatrix, viewModel)

            quadRenderer.draw(
                mvpMatrix = mvpMatrix,
                bitmap = bitmap,
                opacity = params.opacity,
                aspectRatio = quadWidth / quadHeight.coerceAtLeast(0.0001f),
                showFrame = !params.isLocked,
                showGrid = params.showGrid && bitmap != null,
            )
        }
    }

    private fun processPendingDrags(anchorPose: com.google.ar.core.Pose, cameraTranslation: FloatArray) {
        if (controller.pendingDrags.isEmpty()) return

        val anchorOrigin = ArSurfaceMath.Vec3.fromArray(anchorPose.translation)
        val planeNormal = ArSurfaceMath.Vec3.fromArray(anchorPose.yAxis)
        val (baseRight, baseDown) = ArSurfaceMath.computeBasePlaneAxes(anchorMatrix, baseYawDeg)
        val fallbackDist = (ArSurfaceMath.Vec3.fromArray(cameraTranslation) - anchorOrigin).length()

        var moved = false
        while (true) {
            val drag = controller.pendingDrags.poll() ?: break
            val (dX, dZ) = ArSurfaceMath.computePlaneDragDeltaMeters(
                prevScreenX = drag.prevX,
                prevScreenY = drag.prevY,
                currScreenX = drag.currX,
                currScreenY = drag.currY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                viewMatrix = viewMatrix,
                projectionMatrix = projectionMatrix,
                planeOrigin = anchorOrigin,
                planeNormal = planeNormal,
                baseRight = baseRight,
                baseDown = baseDown,
                fallbackDistanceMeters = fallbackDist,
            )
            planeOffsetX = (planeOffsetX + dX).coerceIn(-3f, 3f)
            planeOffsetZ = (planeOffsetZ + dZ).coerceIn(-3f, 3f)
            if (abs(dX) > 1e-4f || abs(dZ) > 1e-4f) {
                moved = true
            }
        }

        if (moved) {
            userHasDraggedSinceAnchor = true
            val hasOffset = abs(planeOffsetX) > 0.002f || abs(planeOffsetZ) > 0.002f
            postPlaneOffset(hasOffset)
        }
    }

    private fun findExactPlaneHit(frame: Frame, x: Float, y: Float): HitResult? {
        val camPos = ArSurfaceMath.Vec3.fromArray(frame.camera.pose.translation)
        return frame.hitTest(x, y).firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(hit.hitPose) &&
                isFrontFacing(hit, camPos)
        }
    }

    private fun findBestSurfaceHit(
        frame: Frame,
        x: Float,
        y: Float,
        allowInstantPlacement: Boolean,
    ): Pair<HitResult, Boolean>? {
        val camPos = ArSurfaceMath.Vec3.fromArray(frame.camera.pose.translation)
        val hits = frame.hitTest(x, y)

        // Priority 1: Tracked plane with hit inside the detected polygon
        hits.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(hit.hitPose) &&
                isFrontFacing(hit, camPos)
        }?.let { return it to false }

        // Priority 2: Feature point with estimated surface normal (handles angled easels/drafting boards)
        hits.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Point &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL &&
                isFrontFacing(hit, camPos)
        }?.let { return it to false }

        // Priority 3: Any tracked plane hit (helpful when blank paper sits on a tracked desk)
        hits.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                isFrontFacing(hit, camPos)
        }?.let { return it to false }

        // Priority 4: Instant Placement fallback so plain paper anchors smoothly without stalling
        if (allowInstantPlacement) {
            frame.hitTestInstantPlacement(x, y, DEFAULT_PAPER_DISTANCE_METERS).firstOrNull { hit ->
                hit.trackable is InstantPlacementPoint
            }?.let { return it to true }
        }

        return null
    }

    private fun isFrontFacing(hit: HitResult, cameraPos: ArSurfaceMath.Vec3): Boolean {
        val hitPos = ArSurfaceMath.Vec3.fromArray(hit.hitPose.translation)
        val normal = ArSurfaceMath.Vec3.fromArray(hit.hitPose.yAxis)
        return normal.dot(cameraPos - hitPos) >= 0f
    }

    private fun createSurfaceAnchorLocked(
        hit: HitResult,
        approximate: Boolean,
        frame: Frame,
        params: ArRenderParams,
        nowMs: Long,
    ) {
        val newAnchor = hit.createAnchor()
        anchor = newAnchor
        isApproximateAnchor = approximate
        anchorCreatedMs = nowMs
        userHasDraggedSinceAnchor = false
        resetOffsetsInternal()

        val anchorPose = newAnchor.pose
        val displayPose = frame.camera.displayOrientedPose
        baseYawDeg = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = ArSurfaceMath.Vec3.fromArray(anchorPose.xAxis),
            anchorYAxis = ArSurfaceMath.Vec3.fromArray(anchorPose.yAxis),
            anchorZAxis = ArSurfaceMath.Vec3.fromArray(anchorPose.zAxis),
            cameraRightAxis = ArSurfaceMath.Vec3.fromArray(displayPose.xAxis),
            cameraUpAxis = ArSurfaceMath.Vec3.fromArray(displayPose.yAxis),
        )

        val bitmap = params.bitmap
        val camPos = ArSurfaceMath.Vec3.fromArray(frame.camera.pose.translation)
        val anchorPos = ArSurfaceMath.Vec3.fromArray(anchorPose.translation)
        val distance = (camPos - anchorPos).length()

        if (bitmap != null && !bitmap.isRecycled) {
            val (w, h) = ArSurfaceMath.computeInitialQuadSizeMeters(
                distanceMeters = distance,
                projectionMatrix = projectionMatrix,
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
            )
            baseWidthMeters = w
            baseHeightMeters = h
            sizedForAspectRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        } else {
            baseWidthMeters = 0.16f
            baseHeightMeters = 0.16f
            sizedForAspectRatio = null
        }

        postStatus(
            ArSurfaceStatus(
                state = ArSurfaceState.ANCHORED,
                isApproximateAnchor = approximate,
            ),
        )
    }

    private fun detachAnchorLocked() {
        runCatching { anchor?.detach() }
        anchor = null
        isApproximateAnchor = false
    }

    private fun resetOffsetsInternal() {
        planeOffsetX = 0f
        planeOffsetZ = 0f
        postPlaneOffset(false)
    }

    private fun trackingFailureHint(reason: TrackingFailureReason): String = when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark — try the flashlight"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Move camera a bit slower"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Show paper edges or desk texture"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera temporarily unavailable"
        TrackingFailureReason.BAD_STATE,
        TrackingFailureReason.NONE,
        -> "Point at paper & move slightly"
    }

    private fun postStatus(status: ArSurfaceStatus) {
        if (status == lastReportedStatus) return
        lastReportedStatus = status
        mainHandler.post { onStatusChanged(status) }
    }

    private fun postPlaneOffset(hasOffset: Boolean) {
        if (hasOffset == lastReportedHasOffset) return
        lastReportedHasOffset = hasOffset
        mainHandler.post { onPlaneOffsetChanged(hasOffset) }
    }

    private fun postUnavailable(message: String) {
        val status = ArSurfaceStatus(ArSurfaceState.UNAVAILABLE, message)
        lastReportedStatus = status
        mainHandler.post { onUnavailable(message) }
    }

    @Suppress("DEPRECATION")
    private fun getDisplayRotation(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display.rotation }.getOrNull()?.let { return it }
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
