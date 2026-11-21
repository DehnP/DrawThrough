package com.example.drawthrough.ar

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders the translucent reference image (original photo or Sobel outline) flat on a 3D ARCore
 * surface plane, with optional Warm Studio corner brackets (when unlocked), optional surface-locked
 * tracing grid, and a clean surface reticle when no image is loaded yet.
 */
internal class ArOverlayQuadRenderer {

    private var program: Int = 0
    private var textureId: Int = -1
    private var uploadedBitmap: Bitmap? = null
    private var uploadedIsPremultiplied: Boolean = true

    private var positionAttrib: Int = -1
    private var texCoordAttrib: Int = -1
    private var mvpUniform: Int = -1
    private var textureUniform: Int = -1
    private var opacityUniform: Int = -1
    private var hasTextureUniform: Int = -1
    private var isPremultipliedUniform: Int = -1
    private var aspectUniform: Int = -1
    private var showFrameUniform: Int = -1
    private var showGridUniform: Int = -1

    // Unit quad lying flat in the anchor's X-Z plane (Y = 0), matching bitmap UV orientation:
    // Top-Left (-0.5, 0, -0.5) -> (0, 0); Bottom-Right (+0.5, 0, +0.5) -> (1, 1)
    private val quadVertices: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            -0.5f, 0f, -0.5f,
            -0.5f, 0f, +0.5f,
            +0.5f, 0f, -0.5f,
            +0.5f, 0f, +0.5f,
        ),
    )

    private val quadTexCoords: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            0f, 0f,
            0f, 1f,
            1f, 0f,
            1f, 1f,
        ),
    )

    fun createOnGlThread() {
        program = ArShaderUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttrib = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        textureUniform = GLES20.glGetUniformLocation(program, "uTexture")
        opacityUniform = GLES20.glGetUniformLocation(program, "uOpacity")
        hasTextureUniform = GLES20.glGetUniformLocation(program, "uHasTexture")
        isPremultipliedUniform = GLES20.glGetUniformLocation(program, "uIsPremultiplied")
        aspectUniform = GLES20.glGetUniformLocation(program, "uAspect")
        showFrameUniform = GLES20.glGetUniformLocation(program, "uShowFrame")
        showGridUniform = GLES20.glGetUniformLocation(program, "uShowGrid")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        // GL_LINEAR without mipmaps guarantees crisp rendering for arbitrary NPOT photo dimensions
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Force re-upload if GL context was recreated after onPause/onResume
        uploadedBitmap = null
    }

    fun draw(
        mvpMatrix: FloatArray,
        bitmap: Bitmap?,
        opacity: Float,
        aspectRatio: Float,
        showFrame: Boolean,
        showGrid: Boolean,
    ) {
        if (program == 0 || textureId == -1) return

        val hasValidBitmap = bitmap != null && !bitmap.isRecycled
        if (hasValidBitmap && bitmap !== uploadedBitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            uploadedBitmap = bitmap
            uploadedIsPremultiplied = bitmap.isPremultiplied
        } else if (!hasValidBitmap) {
            uploadedBitmap = null
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Premultiplied alpha blending prevents dark fringes around transparent Sobel outline edges
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(opacityUniform, opacity.coerceIn(0f, 1f))
        GLES20.glUniform1f(hasTextureUniform, if (hasValidBitmap) 1f else 0f)
        GLES20.glUniform1f(isPremultipliedUniform, if (uploadedIsPremultiplied) 1f else 0f)
        GLES20.glUniform1f(aspectUniform, aspectRatio.coerceIn(0.1f, 10f))
        GLES20.glUniform1f(showFrameUniform, if (showFrame) 1f else 0f)
        GLES20.glUniform1f(showGridUniform, if (showGrid) 1f else 0f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        quadVertices.position(0)
        quadTexCoords.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform float uOpacity;
            uniform float uHasTexture;
            uniform float uIsPremultiplied;
            uniform float uAspect;
            uniform float uShowFrame;
            uniform float uShowGrid;

            void main() {
                vec4 result = vec4(0.0);

                if (uHasTexture > 0.5) {
                    vec4 texColor = texture2D(uTexture, vTexCoord);
                    if (uIsPremultiplied < 0.5) {
                        texColor.rgb *= texColor.a;
                    }
                    result = texColor * uOpacity;
                }

                vec2 dims = vec2(uAspect, 1.0);
                vec2 p = vec2(vTexCoord.x * uAspect, vTexCoord.y);
                float minSide = min(dims.x, dims.y);
                float dx = min(p.x, dims.x - p.x);
                float dy = min(p.y, dims.y - p.y);

                // Optional subtle surface-locked dotted alignment grid behind translucent image
                if (uShowGrid > 0.5) {
                    float cellSize = minSide / 6.0;
                    float lineW = 0.0038 * minSide;
                    float gx = mod(p.x, cellSize);
                    float gy = mod(p.y, cellSize);
                    float lineX = min(gx, cellSize - gx);
                    float lineY = min(gy, cellSize - gy);
                    float dashPeriod = cellSize * 0.16;
                    bool onVertDash = mod(p.y, dashPeriod) < dashPeriod * 0.45;
                    bool onHorizDash = mod(p.x, dashPeriod) < dashPeriod * 0.45;
                    if (dx > lineW && dy > lineW &&
                        ((lineX < lineW && onVertDash) || (lineY < lineW && onHorizDash))) {
                        vec4 gridInk = vec4(vec3(0.118, 0.114, 0.106) * 0.30, 0.30);
                        result = result + gridInk * (1.0 - result.a);
                    }
                }

                // Subtle Warm Terracotta corner brackets when unlocked so surface lock is unmistakable
                if (uShowFrame > 0.5) {
                    float thickness = 0.010 * minSide;
                    float armLen = 0.095 * minSide;
                    bool inCorner = (dx < thickness && dy < armLen) || (dy < thickness && dx < armLen);
                    if (inCorner) {
                        float bracketAlpha = uHasTexture > 0.5 ? 0.72 : 0.85;
                        vec4 terracotta = vec4(vec3(0.827, 0.357, 0.212) * bracketAlpha, bracketAlpha);
                        result = terracotta + result * (1.0 - terracotta.a);
                    } else if (uHasTexture < 0.5) {
                        // Subtle center crosshair dot when pinned without a photo yet
                        vec2 centerDelta = abs(p - dims * 0.5);
                        float crossW = 0.009 * minSide;
                        float crossLen = 0.055 * minSide;
                        if ((centerDelta.x < crossW && centerDelta.y < crossLen) ||
                            (centerDelta.y < crossW && centerDelta.x < crossLen)) {
                            vec4 terracotta = vec4(vec3(0.827, 0.357, 0.212) * 0.75, 0.75);
                            result = terracotta;
                        }
                    }
                }

                if (result.a < 0.002) {
                    discard;
                }
                gl_FragColor = result;
            }
        """
    }
}
