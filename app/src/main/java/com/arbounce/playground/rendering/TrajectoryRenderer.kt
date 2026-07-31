package com.arbounce.playground.rendering

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Draws the dotted throw-preview arc as a series of world-space points. */
class TrajectoryRenderer {
    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private var uPointSize = 0

    private var buffer: FloatBuffer =
        ByteBuffer.allocateDirect(256 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val vertexShader = """
        uniform mat4 u_Mvp;
        uniform float u_PointSize;
        attribute vec3 a_Position;
        void main() {
            gl_Position = u_Mvp * vec4(a_Position, 1.0);
            gl_PointSize = u_PointSize;
        }
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
        uPointSize = GLES20.glGetUniformLocation(program, "u_PointSize")
    }

    /** [points] is a flat [x,y,z,...] world-space array. */
    fun draw(points: FloatArray, viewProj: FloatArray) {
        val n = points.size / 3
        if (n <= 0) return
        if (buffer.capacity() < points.size) {
            buffer = ByteBuffer.allocateDirect(points.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        buffer.position(0)
        buffer.put(points)
        buffer.position(0)

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, viewProj, 0)
        GLES20.glUniform4f(uColor, 1.0f, 0.85f, 0.3f, 0.95f)
        GLES20.glUniform1f(uPointSize, 14.0f)

        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, n)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisable(GLES20.GL_BLEND)
        GlUtil.checkGlError("TrajectoryRenderer.draw")
    }
}
