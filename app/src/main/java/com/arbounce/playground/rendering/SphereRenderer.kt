package com.arbounce.playground.rendering

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a procedurally generated unit sphere (radius 1, centred at origin) with a
 * simple diffuse+ambient shader. Used for the physics ball. No external assets.
 */
class SphereRenderer(
    private val stacks: Int = 16,
    private val slices: Int = 24,
) {
    private var program = 0
    private var aPosition = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLightDir = 0

    private lateinit var positions: FloatBuffer
    private lateinit var normals: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var indexCount = 0

    private val vertexShader = """
        uniform mat4 u_Mvp;
        uniform mat4 u_Model;
        attribute vec3 a_Position;
        attribute vec3 a_Normal;
        varying vec3 v_Normal;
        void main() {
            gl_Position = u_Mvp * vec4(a_Position, 1.0);
            v_Normal = mat3(u_Model) * a_Normal;
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        varying vec3 v_Normal;
        uniform vec4 u_Color;
        uniform vec3 u_LightDir;
        void main() {
            vec3 n = normalize(v_Normal);
            float diff = max(dot(n, normalize(u_LightDir)), 0.0);
            float shade = 0.35 + 0.75 * diff;
            gl_FragColor = vec4(u_Color.rgb * shade, u_Color.a);
        }
    """.trimIndent()

    fun createOnGlThread() {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        for (i in 0..stacks) {
            val phi = Math.PI * i / stacks           // 0..pi
            val y = cos(phi).toFloat()
            val r = sin(phi).toFloat()
            for (j in 0..slices) {
                val theta = 2.0 * Math.PI * j / slices
                val x = (r * cos(theta)).toFloat()
                val z = (r * sin(theta)).toFloat()
                pos.add(x); pos.add(y); pos.add(z)
                nrm.add(x); nrm.add(y); nrm.add(z) // unit sphere: normal == position
            }
        }
        val idx = ArrayList<Short>()
        val cols = slices + 1
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = (i * cols + j).toShort()
                val b = ((i + 1) * cols + j).toShort()
                val c = ((i + 1) * cols + j + 1).toShort()
                val d = (i * cols + j + 1).toShort()
                idx.add(a); idx.add(b); idx.add(d)
                idx.add(b); idx.add(c); idx.add(d)
            }
        }
        indexCount = idx.size

        positions = toFloatBuffer(pos)
        normals = toFloatBuffer(nrm)
        indices = ByteBuffer.allocateDirect(idx.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        for (s in idx) indices.put(s)
        indices.position(0)

        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPosition = GLES20.glGetAttribLocation(program, "a_Position")
        aNormal = GLES20.glGetAttribLocation(program, "a_Normal")
        uMvp = GLES20.glGetUniformLocation(program, "u_Mvp")
        uModel = GLES20.glGetUniformLocation(program, "u_Model")
        uColor = GLES20.glGetUniformLocation(program, "u_Color")
        uLightDir = GLES20.glGetUniformLocation(program, "u_LightDir")
    }

    /** [mvp] and [model] are column-major 4x4; [color] is RGBA. */
    fun draw(mvp: FloatArray, model: FloatArray, color: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        positions.position(0)
        normals.position(0)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 0, normals)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glEnableVertexAttribArray(aNormal)

        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform4fv(uColor, 1, color, 0)
        GLES20.glUniform3f(uLightDir, 0.5f, 1.0f, 0.3f)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
        GlUtil.checkGlError("SphereRenderer.draw")
    }

    private fun toFloatBuffer(list: List<Float>): FloatBuffer {
        val fb = ByteBuffer.allocateDirect(list.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (f in list) fb.put(f)
        fb.position(0)
        return fb
    }
}
