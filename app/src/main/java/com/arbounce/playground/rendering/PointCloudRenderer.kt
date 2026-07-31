package com.arbounce.playground.rendering

import android.opengl.GLES20
import com.google.ar.core.PointCloud

/** Draws ARCore feature points as small dots during scanning. */
class PointCloudRenderer {
    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private var uPointSize = 0

    private val vertexShader = """
        uniform mat4 u_Mvp;
        uniform float u_PointSize;
        attribute vec4 a_Position;
        void main() {
            gl_Position = u_Mvp * vec4(a_Position.xyz, 1.0);
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

    fun draw(pointCloud: PointCloud, viewProj: FloatArray) {
        val points = pointCloud.points
        val numPoints = points.remaining() / 4
        if (numPoints <= 0) return

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, viewProj, 0)
        GLES20.glUniform4f(uColor, 0.85f, 0.95f, 1.0f, 0.9f)
        GLES20.glUniform1f(uPointSize, 9.0f)

        points.position(0)
        // stride 16 bytes (x,y,z,confidence); read only xyz via vec4 with stride
        GLES20.glVertexAttribPointer(aPosition, 4, GLES20.GL_FLOAT, false, 16, points)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints)
        GLES20.glDisableVertexAttribArray(aPosition)
        GlUtil.checkGlError("PointCloudRenderer.draw")
    }
}
