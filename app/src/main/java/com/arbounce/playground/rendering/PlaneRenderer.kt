package com.arbounce.playground.rendering

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Lightweight, texture-free plane visualisation: each detected plane is filled as a
 * translucent triangle fan and outlined, giving the "scanning" feedback from the PRD
 * without the heavy textured PlaneRenderer from the ARCore sample.
 */
class PlaneRenderer {
    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0

    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val vertexShader = """
        uniform mat4 u_Mvp;
        attribute vec3 a_Position;
        void main() { gl_Position = u_Mvp * vec4(a_Position, 1.0); }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        uniform vec4 u_Color;
        void main() { gl_FragColor = u_Color; }
    """.trimIndent()

    fun createOnGlThread() {
        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPosition = GLES20.glGetAttribLocation(program, "a_Position")
        uMvp = GLES20.glGetUniformLocation(program, "u_Mvp")
        uColor = GLES20.glGetUniformLocation(program, "u_Color")
    }

    fun drawPlanes(planes: Collection<Plane>, viewProj: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.subsumedBy != null) continue
            val polygon = plane.polygon ?: continue
            val count2d = polygon.limit() / 2
            if (count2d < 3) continue

            ensureCapacity(count2d * 3)
            vertexBuffer.position(0)
            polygon.position(0)
            for (i in 0 until count2d) {
                val x = polygon.get(i * 2)
                val z = polygon.get(i * 2 + 1)
                vertexBuffer.put(x); vertexBuffer.put(0f); vertexBuffer.put(z)
            }
            vertexBuffer.position(0)

            plane.centerPose.toMatrix(model, 0)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

            GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aPosition)

            // translucent fill
            GLES20.glUniform4f(uColor, 0.31f, 0.76f, 0.97f, 0.22f)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, count2d)
            // brighter outline
            GLES20.glUniform4f(uColor, 0.31f, 0.76f, 0.97f, 0.7f)
            GLES20.glLineWidth(4f)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, count2d)

            GLES20.glDisableVertexAttribArray(aPosition)
        }

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GlUtil.checkGlError("PlaneRenderer.draw")
    }

    private fun ensureCapacity(floats: Int) {
        if (vertexBuffer.capacity() < floats) {
            vertexBuffer = ByteBuffer.allocateDirect(floats * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
    }
}
