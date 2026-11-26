package app.zircut.core

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

/**
 * Alpha mask for a video overlay. The shader runs in the overlay's own video stream before
 * Media3 composites it over the main track, so transparent pixels reveal lower layers.
 */
@UnstableApi
class VideoOverlayMaskEffect(private val maskType: Int) : GlEffect {
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = maskType == 0

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
        return MaskShaderProgram(context, useHdr, maskType)
    }

    private class MaskShaderProgram(
        context: Context,
        useHdr: Boolean,
        private val maskType: Int
    ) : BaseGlShaderProgram(useHdr, 1) {
        private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        init {
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            program.setIntUniform("uMaskType", maskType)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val aspect = inputWidth.toFloat() / inputHeight.coerceAtLeast(1)
            program.setFloatUniform("uAspect", aspect)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            try {
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            } finally {
                super.release()
            }
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vec4 texturePosition = vec4(aFramePosition.xy * 0.5 + 0.5, 0.0, 1.0);
                vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #version 100
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform int uMaskType;
            uniform float uAspect;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                if (uMaskType == 0) {
                    gl_FragColor = src;
                    return;
                }

                float aspect = max(uAspect, 0.0001);
                vec2 p = vTexSamplingCoord - vec2(0.5);
                p.x *= max(aspect, 1.0);
                p.y *= max(1.0 / aspect, 1.0);

                float distanceToEdge;
                if (uMaskType == 1) {
                    distanceToEdge = length(p) - 0.48;
                } else {
                    float radius = 0.14;
                    vec2 halfSize = vec2(0.5 * max(aspect, 1.0), 0.5 * max(1.0 / aspect, 1.0));
                    vec2 q = abs(p) - halfSize + vec2(radius);
                    distanceToEdge = length(max(q, vec2(0.0)))
                        + min(max(q.x, q.y), 0.0) - radius;
                }
                float insideMask = 1.0 - step(0.0, distanceToEdge);
                gl_FragColor = vec4(src.rgb, src.a * insideMask);
            }
        """
    }
}
