package tj.kod.assistant

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalVideoResult(
    val filePath: String,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val fps: Int,
)

class LocalVideoEngine(
    private val context: Context,
) : AutoCloseable {
    suspend fun generate(
        diffusionModelPath: String,
        vaePath: String,
        textEncoderPath: String,
        prompt: String,
        width: Int = 320,
        height: Int = 192,
        steps: Int = 8,
        frameCount: Int = 9,
        fps: Int = 8,
        seed: Long = System.currentTimeMillis(),
    ): LocalVideoResult = withContext(Dispatchers.Default) {
        requireModel(diffusionModelPath, "Wan diffusion")
        requireModel(vaePath, "Wan VAE")
        requireModel(textEncoderPath, "UMT5")

        val packed = nativeGenerateVideoRgb(
            diffusionModelPath,
            vaePath,
            textEncoderPath,
            prompt.trim(),
            "worst quality, low quality, blurry, distorted, artifacts",
            width,
            height,
            steps,
            frameCount,
            fps,
            seed,
        ) ?: error("Видео-движок не вернул кадры")

        val buffer = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.remaining() >= 20) { "Некорректный ответ видео-движка" }
        require(buffer.int == MAGIC) { "Неверный формат кадров" }

        val outWidth = buffer.int
        val outHeight = buffer.int
        val outFrames = buffer.int
        val outFps = buffer.int

        require(outWidth > 0 && outHeight > 0 && outFrames > 0 && outFps > 0) {
            "Видео-движок вернул неверные параметры"
        }

        val expected = outWidth * outHeight * 3 * outFrames
        require(buffer.remaining() == expected) {
            "Неверный размер RGB кадров"
        }

        val rgb = ByteArray(expected)
        buffer.get(rgb)

        val dir = context.getExternalFilesDir(
            Environment.DIRECTORY_MOVIES
        )?.let { File(it, "KOT") }
            ?: File(context.filesDir, "generated-videos")

        dir.mkdirs()

        val output = File(
            dir,
            "kot-video-" + System.currentTimeMillis() + ".mp4",
        )

        AndroidMp4Encoder.encode(
            rgb = rgb,
            width = outWidth,
            height = outHeight,
            frameCount = outFrames,
            fps = outFps,
            output = output,
        )

        LocalVideoResult(
            filePath = output.absolutePath,
            width = outWidth,
            height = outHeight,
            frameCount = outFrames,
            fps = outFps,
        )
    }

    override fun close() {
        runCatching { nativeCloseVideo() }
    }

    private fun requireModel(path: String, label: String) {
        val file = File(path)
        require(file.exists() && file.isFile) {
            label + " не найден: " + path
        }
    }

    private external fun nativeGenerateVideoRgb(
        diffusionModelPath: String,
        vaePath: String,
        textEncoderPath: String,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        frameCount: Int,
        fps: Int,
        seed: Long,
    ): ByteArray?

    private external fun nativeCloseVideo()

    private companion object {
        const val MAGIC = 0x3156544B

        init {
            System.loadLibrary("kot_image")
        }
    }
}

private object AndroidMp4Encoder {
    private const val MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L

    private data class Choice(
        val name: String,
        val colorFormat: Int,
    )

    fun encode(
        rgb: ByteArray,
        width: Int,
        height: Int,
        frameCount: Int,
        fps: Int,
        output: File,
    ) {
        require(width % 2 == 0 && height % 2 == 0) {
            "Размер видео должен быть чётным"
        }

        val rgbFrameSize = width * height * 3
        require(rgb.size == rgbFrameSize * frameCount) {
            "RGB-буфер не совпадает с количеством кадров"
        }

        val choice = chooseEncoder()
        val codec = MediaCodec.createByCodecName(choice.name)
        val muxer = MediaMuxer(
            output.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )

        var codecStarted = false
        var muxerStarted = false
        var track = -1

        try {
            val format = MediaFormat.createVideoFormat(
                MIME,
                width,
                height,
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    choice.colorFormat,
                )
                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    max(350_000, width * height * fps),
                )
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            codec.start()
            codecStarted = true

            val info = MediaCodec.BufferInfo()
            var next = 0
            var inputEnded = false
            var outputEnded = false

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)

                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("Нет input buffer")
                        inputBuffer.clear()

                        if (next < frameCount) {
                            val yuv = rgbToYuv420(
                                rgb = rgb,
                                offset = next * rgbFrameSize,
                                width = width,
                                height = height,
                                semiPlanar =
                                    choice.colorFormat ==
                                        MediaCodecInfo.CodecCapabilities
                                            .COLOR_FormatYUV420SemiPlanar,
                            )

                            require(inputBuffer.capacity() >= yuv.size) {
                                "Слишком маленький encoder buffer"
                            }

                            inputBuffer.put(yuv)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                yuv.size,
                                pts(next, fps),
                                0,
                            )
                            next += 1
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                pts(next, fps),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        }
                    }
                }

                var draining = true

                while (draining && !outputEnded) {
                    val out = codec.dequeueOutputBuffer(
                        info,
                        if (inputEnded) TIMEOUT_US else 0L,
                    )

                    when {
                        out == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            draining = false
                        }

                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted)
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }

                        out >= 0 -> {
                            val data = codec.getOutputBuffer(out)
                                ?: error("Нет output buffer")

                            if (
                                info.flags and
                                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            ) {
                                info.size = 0
                            }

                            if (info.size > 0) {
                                check(muxerStarted)
                                data.position(info.offset)
                                data.limit(info.offset + info.size)
                                muxer.writeSampleData(track, data, info)
                            }

                            val eos =
                                info.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                            codec.releaseOutputBuffer(out, false)

                            if (eos) outputEnded = true
                        }
                    }
                }
            }
        } finally {
            if (codecStarted) runCatching { codec.stop() }
            runCatching { codec.release() }

            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun chooseEncoder(): Choice {
        val preferred = intArrayOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        )

        for (
            info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        ) {
            if (!info.isEncoder) continue

            val type = info.supportedTypes.firstOrNull {
                it.equals(MIME, ignoreCase = true)
            } ?: continue

            val caps = runCatching {
                info.getCapabilitiesForType(type)
            }.getOrNull() ?: continue

            for (format in preferred) {
                if (caps.colorFormats.contains(format)) {
                    return Choice(info.name, format)
                }
            }
        }

        error("Нет H.264 encoder с YUV420")
    }

    private fun pts(frame: Int, fps: Int): Long =
        frame.toLong() * 1_000_000L / fps.toLong()

    private fun rgbToYuv420(
        rgb: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        semiPlanar: Boolean,
    ): ByteArray {
        val frame = width * height
        val quarter = frame / 4
        val out = ByteArray(frame + quarter * 2)

        var yIndex = 0
        var uIndex = frame
        var vIndex = if (semiPlanar) frame + 1 else frame + quarter

        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = offset + (y * width + x) * 3
                val r = rgb[p].toInt() and 0xff
                val g = rgb[p + 1].toInt() and 0xff
                val b = rgb[p + 2].toInt() and 0xff

                val yy = (
                    ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    ).coerceIn(0, 255)

                out[yIndex++] = yy.toByte()

                if (y % 2 == 0 && x % 2 == 0) {
                    val uu = (
                        ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                        ).coerceIn(0, 255)
                    val vv = (
                        ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                        ).coerceIn(0, 255)

                    if (semiPlanar) {
                        out[uIndex] = uu.toByte()
                        out[vIndex] = vv.toByte()
                        uIndex += 2
                        vIndex += 2
                    } else {
                        out[uIndex++] = uu.toByte()
                        out[vIndex++] = vv.toByte()
                    }
                }
            }
        }

        return out
    }
}
