package com.arbounce.playground.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.arbounce.playground.math.Vec3
import com.arbounce.playground.physics.Ball
import com.arbounce.playground.physics.PhysicsWorld
import com.arbounce.playground.physics.Surface
import com.arbounce.playground.rendering.BackgroundRenderer
import com.arbounce.playground.rendering.PlaneRenderer
import com.arbounce.playground.rendering.PointCloudRenderer
import com.arbounce.playground.rendering.SphereRenderer
import com.arbounce.playground.rendering.TrajectoryRenderer
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView renderer: draws the camera, scanning aids, and physics balls, and
 * steps the [PhysicsWorld] each frame. Interaction (aim/charge/throw/reset) comes in
 * from the UI thread via volatile flags and is consumed on the GL thread.
 */
class ArRenderer(
    context: Context,
    private val events: GameEvents,
) : GLSurfaceView.Renderer {

    @Volatile
    var session: Session? = null

    private val displayRotationHelper = DisplayRotationHelper(context)

    private val background = BackgroundRenderer()
    private val pointCloud = PointCloudRenderer()
    private val planeRenderer = PlaneRenderer()
    private val sphere = SphereRenderer()
    private val trajectory = TrajectoryRenderer()

    private val physics = PhysicsWorld()

    // matrices
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    // interaction state (written on UI thread)
    @Volatile private var aiming = false
    @Volatile private var aimStartMs = 0L
    @Volatile private var pendingThrowCharge = -1f
    @Volatile private var resetRequested = false

    private var lastFrameNs = 0L

    private val baseSpeed = 3.2f
    private val extraSpeed = 5.5f
    private val maxChargeMs = 1200L

    // ---- called from UI thread ----
    fun startAim() {
        aiming = true
        aimStartMs = System.currentTimeMillis()
    }

    fun releaseThrow() {
        if (aiming) {
            pendingThrowCharge = currentCharge()
            aiming = false
        }
    }

    fun cancelAim() {
        aiming = false
    }

    fun requestReset() {
        resetRequested = true
    }

    fun onResume() = displayRotationHelper.onResume()
    fun onPause() = displayRotationHelper.onPause()
    fun onViewSurfaceChanged(width: Int, height: Int) =
        displayRotationHelper.onSurfaceChanged(width, height)

    private fun currentCharge(): Float {
        val held = (System.currentTimeMillis() - aimStartMs).coerceIn(0L, maxChargeMs)
        return held.toFloat() / maxChargeMs
    }

    // ---- GLSurfaceView.Renderer ----
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        pointCloud.createOnGlThread()
        planeRenderer.createOnGlThread()
        sphere.createOnGlThread()
        trajectory.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = this.session ?: return

        try {
            displayRotationHelper.updateSessionIfNeeded(session)
            session.setCameraTextureName(background.textureId)

            val frame = session.update()
            val camera = frame.camera

            background.draw(frame)

            if (camera.trackingState != TrackingState.TRACKING) {
                events.onTracking(false, 0)
                return
            }

            camera.getProjectionMatrix(projMatrix, 0, 0.05f, 20f)
            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)

            val planes = session.getAllTrackables(Plane::class.java)
            val surfaces = buildSurfaces(planes)

            if (resetRequested) {
                physics.clear()
                resetRequested = false
            }

            // Consume a queued throw using the CURRENT camera pose.
            val charge = pendingThrowCharge
            if (charge >= 0f) {
                spawnBall(camera.pose, charge)
                pendingThrowCharge = -1f
                events.onThrow()
            }

            // Step physics.
            val nowNs = System.nanoTime()
            val dt = if (lastFrameNs == 0L) 0f else (nowNs - lastFrameNs) / 1_000_000_000f
            lastFrameNs = nowNs
            val bounces = physics.update(dt, surfaces)
            for (b in bounces) events.onBounce(b.impactSpeed, b.airMs)

            // Scanning aids.
            frame.acquirePointCloud().use { pc ->
                pointCloud.draw(pc, viewProj)
            }
            planeRenderer.drawPlanes(planes, viewProj)

            // Aim preview.
            if (aiming) {
                val (start, vel) = throwVector(camera.pose, currentCharge())
                val pts = physics.predict(start, vel, 1.4f, 40)
                trajectory.draw(pts, viewProj)
            }

            // Balls.
            for (b in physics.balls) drawBall(b)

            events.onTracking(true, planes.count { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null })
        } catch (t: Throwable) {
            Log.e(TAG, "onDrawFrame error", t)
        }
    }

    private fun throwVector(camPose: Pose, charge: Float): Pair<Vec3, Vec3> {
        val startArr = camPose.transformPoint(floatArrayOf(0f, 0f, -0.12f))
        val start = Vec3.of(startArr)
        val zAxis = camPose.zAxis // +Z points toward the viewer; forward is -Z
        val dir = Vec3(-zAxis[0], -zAxis[1], -zAxis[2]).normalized()
        val speed = baseSpeed + charge * extraSpeed
        val vel = dir * speed
        return start to vel
    }

    private fun spawnBall(camPose: Pose, charge: Float) {
        val (start, vel) = throwVector(camPose, charge)
        physics.spawn(Ball(start, vel))
    }

    private fun drawBall(b: Ball) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, b.pos.x, b.pos.y, b.pos.z)
        val sq = b.squash
        val sx = b.radius * (1f + 0.35f * sq)
        val sy = b.radius * (1f - 0.5f * sq)
        Matrix.scaleM(model, 0, sx, sy, sx)
        Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
        sphere.draw(mvp, model, b.color)
    }

    private fun buildSurfaces(planes: Collection<Plane>): List<Surface> {
        val out = ArrayList<Surface>()
        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.subsumedBy != null) continue
            val center = plane.centerPose
            val yAxis = center.yAxis // plane normal
            val normal = Vec3(yAxis[0], yAxis[1], yAxis[2]).normalized()
            val point = Vec3(center.tx(), center.ty(), center.tz())
            val isFloor = plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            val planeRef = plane
            out.add(
                Surface(normal, point, isFloor) { p ->
                    planeRef.isPoseInPolygon(Pose.makeTranslation(p.x, p.y, p.z))
                },
            )
        }
        return out
    }

    companion object {
        private const val TAG = "ArRenderer"
    }
}
