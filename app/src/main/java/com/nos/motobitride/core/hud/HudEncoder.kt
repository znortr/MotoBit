package com.nos.motobitride.core.hud

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import com.nos.motobitride.core.log.AppLog
import java.util.concurrent.atomic.AtomicLong

/** Frame YUV420 planare (I420) pronta per l'encoder. */
class YuvFrame(val width: Int, val height: Int) {
    val y = ByteArray(width * height)
    val u = ByteArray(width * height / 4)
    val v = ByteArray(width * height / 4)
}

/** Conversione ARGB -> I420 e riempimento dei piani dell'encoder. */
object YuvConverter {

    /**
     * @param pixels   pixel ARGB con passo di riga [srcStride]
     * @param srcStride larghezza reale della riga nel buffer (>= width)
     */
    fun fromArgb(pixels: IntArray, srcStride: Int, width: Int, height: Int): YuvFrame {
        val frame = YuvFrame(width, height)

        var di = 0
        for (row in 0 until height) {
            val rowBase = row * srcStride
            for (col in 0 until width) {
                val c = pixels[rowBase + col]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                var yv = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                if (yv < 16) yv = 16 else if (yv > 235) yv = 235
                frame.y[di++] = yv.toByte()
            }
        }

        var uv = 0
        var row = 0
        while (row < height) {
            val rowBase = row * srcStride
            var col = 0
            while (col < width) {
                val c = pixels[rowBase + col]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                var uu = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                var vv = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                if (uu < 16) uu = 16 else if (uu > 240) uu = 240
                if (vv < 16) vv = 16 else if (vv > 240) vv = 240

                frame.u[uv] = uu.toByte()
                frame.v[uv] = vv.toByte()
                uv++
                col += 2
            }
            row += 2
        }

        return frame
    }

    fun fillImage(image: Image, frame: YuvFrame) {
        copyPlane(image.planes[0], frame.y, frame.width, frame.height)
        copyPlane(image.planes[1], frame.u, frame.width / 2, frame.height / 2)
        copyPlane(image.planes[2], frame.v, frame.width / 2, frame.height / 2)
    }

    private fun copyPlane(plane: Image.Plane, src: ByteArray, w: Int, h: Int) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var srcOffset = 0

        for (row in 0 until h) {
            val base = row * rowStride
            if (pixelStride == 1) {
                buffer.position(base)
                buffer.put(src, srcOffset, w)
            } else {
                var pos = base
                for (col in 0 until w) {
                    buffer.put(pos, src[srcOffset + col])
                    pos += pixelStride
                }
            }
            srcOffset += w
        }
    }
}

/**
 * Encoder H.264 in modalita' ByteBuffer, alimentato da un thread di pacing.
 *
 * Perche' non la input Surface: con `createInputSurface()` il codec produce
 * frame solo quando lo schermo viene ridisegnato. A schermo fermo si scende
 * a ~1 fps, l'HU (che fa polling a 30 Hz su :10920) va in starvation del
 * decoder e il TFT resta nero. Qui invece l'ultimo frame catturato viene
 * riaccodato a 30 fps esatti, esattamente come fa il riferimento
 * `stream/desktop_stream.go` con ffmpeg.
 *
 * Parametri allineati al riferimento: baseline, level 3.1, keyint = 1
 * (IDR su ogni frame), CBR, SPS/PPS preponati ai keyframe.
 */
class HudEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val onSampleEncoded: (ByteArray) -> Unit
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

    private val frameLock = Any()
    private var currentFrame: YuvFrame? = null

    private var reusableBitmap: Bitmap? = null
    private var reusablePixels: IntArray? = null

    @Volatile
    private var running = false
    @Volatile
    private var stopped = false

    /**
     * In pausa: i frame non vengono ne' catturati ne' accodati al codec.
     * Usato quando la sessione HUD e' persa da un po' (risparmio batteria).
     * Il codec resta avviato, cosi' il resume e' immediato.
     */
    @Volatile
    private var paused = false
    private var drainThread: Thread? = null
    private var paceThread: Thread? = null

    private val sampleCount = AtomicLong(0)
    private val byteCount = AtomicLong(0)
    private val capturedCount = AtomicLong(0)

    init {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)

            // keyint = 1 -> IDR su ogni frame (richiesto dall'HU)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)

            // SPS/PPS allegati al keyframe: necessario per AVCC -> Annex-B lato Go
            setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)

            try {
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
                setInteger(
                    MediaFormat.KEY_LEVEL,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel31
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "profilo/level non impostabili: ${e.message}")
            }

            try {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
            } catch (_: Throwable) {
                // alcuni codec non supportano CBR
            }
        }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        AppLog.i(TAG, "Encoder configurato ${width}x$height @$fps fps, $bitrate bps (ByteBuffer mode)")
    }

    fun start() {
        if (running) return
        running = true
        codec.start()
        drainThread = Thread(::drainLoop, "HudEncoderDrain").apply { start() }
        paceThread = Thread(::paceLoop, "HudEncoderPace").apply { start() }
        AppLog.i(TAG, "Encoder avviato (pacing ${fps} fps)")
    }

    /** Sospende cattura ed encoding senza rilasciare il codec. */
    fun pause() {
        if (paused || !running) return
        paused = true
        AppLog.i(TAG, "Encoder in PAUSA (nessuna sessione HUD attiva)")
    }

    /** Riattiva cattura ed encoding; il prossimo frame sara' un IDR. */
    fun resume() {
        if (!paused) return
        paused = false
        AppLog.i(TAG, "Encoder RIATTIVATO")
    }

    val isPaused: Boolean get() = paused

    /** Converte un frame catturato dal VirtualDisplay in YUV e lo rende disponibile. */
    fun submitImage(image: Image) {
        // In pausa scartiamo subito: nessuna conversione RGB->YUV inutile.
        if (paused || !running) return
        try {
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride

            val bitmap = reusableBitmap
                ?: Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    .also { reusableBitmap = it }

            plane.buffer.rewind()
            bitmap.copyPixelsFromBuffer(plane.buffer)

            val needed = paddedWidth * image.height
            val pixels = reusablePixels?.takeIf { it.size >= needed }
                ?: IntArray(needed).also { reusablePixels = it }

            bitmap.getPixels(pixels, 0, paddedWidth, 0, 0, paddedWidth, image.height)

            val frame = YuvConverter.fromArgb(pixels, paddedWidth, width, height)
            synchronized(frameLock) { currentFrame = frame }

            val n = capturedCount.incrementAndGet()
            if (n == 1L || n % 150L == 0L) {
                AppLog.i(TAG, "cattura schermo: frame #$n (${paddedWidth}x${image.height})")
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "conversione frame fallita", t)
        }
    }

    fun requestSyncFrame() {
        if (!running) return
        try {
            val params = Bundle()
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            codec.setParameters(params)
        } catch (e: Exception) {
            AppLog.w(TAG, "requestSyncFrame fallita: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        if (stopped) return
        stopped = true
        if (!running) {
            try {
                codec.release()
            } catch (_: Throwable) {
            }
            return
        }
        running = false
        try {
            paceThread?.join(500)
        } catch (_: InterruptedException) {
        }
        paceThread = null
        try {
            drainThread?.join(1_000)
        } catch (_: InterruptedException) {
        }
        drainThread = null
        try {
            codec.stop()
        } catch (e: Exception) {
            AppLog.w(TAG, "codec.stop fallita: ${e.message}")
        }
        try {
            codec.release()
        } catch (e: Exception) {
            AppLog.w(TAG, "codec.release fallita: ${e.message}")
        }
        reusableBitmap = null
        reusablePixels = null
        AppLog.i(
            TAG,
            "Encoder fermato (${sampleCount.get()} frame inviati, " +
                    "${capturedCount.get()} catturati, ${byteCount.get() / 1024} KB)"
        )
    }

    /**
     * Thread di pacing: riaccoda l'ultimo frame disponibile ogni 1/fps,
     * anche se lo schermo non ha prodotto nuovi frame.
     */
    private fun paceLoop() {
        val intervalNs = 1_000_000_000L / fps
        val startNs = System.nanoTime()
        var nextNs = startNs
        var submitted = 0L
        var windowStart = System.currentTimeMillis()

        while (running) {
            if (paused) {
                // Riallinea la cadenza: senza questo, al resume il loop
                // cercherebbe di "recuperare" i frame persi.
                nextNs = System.nanoTime()
                submitted = 0
                windowStart = System.currentTimeMillis()
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                }
                continue
            }

            val now = System.nanoTime()
            if (now < nextNs) {
                val sleepMs = (nextNs - now) / 1_000_000L
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs)
                    } catch (_: InterruptedException) {
                    }
                }
                continue
            }
            nextNs += intervalNs

            val frame = synchronized(frameLock) { currentFrame } ?: continue

            val ptsUs = (System.nanoTime() - startNs) / 1000L
            val index = try {
                codec.dequeueInputBuffer(5_000)
            } catch (e: Exception) {
                if (running) AppLog.e(TAG, "dequeueInputBuffer fallita", e)
                break
            }
            if (index < 0) continue

            var size = 0
            try {
                val image = codec.getInputImage(index)
                if (image != null) {
                    YuvConverter.fillImage(image, frame)
                    size = width * height * 3 / 2
                } else {
                    val buffer = codec.getInputBuffer(index)
                    if (buffer != null) {
                        buffer.clear()
                        buffer.put(frame.y)
                        buffer.put(frame.u)
                        buffer.put(frame.v)
                        size = buffer.position()
                    }
                }
                codec.queueInputBuffer(index, 0, size, ptsUs, 0)
                submitted++

                val elapsed = System.currentTimeMillis() - windowStart
                if (elapsed >= 5_000L) {
                    AppLog.i(
                        TAG,
                        "flusso encoder: ${submitted * 1000L / elapsed} fps inviati"
                    )
                    submitted = 0
                    windowStart = System.currentTimeMillis()
                }
            } catch (t: Throwable) {
                if (running) AppLog.e(TAG, "accodamento frame fallito", t)
                try {
                    codec.queueInputBuffer(index, 0, 0, ptsUs, 0)
                } catch (_: Throwable) {
                }
            }
        }
        AppLog.i(TAG, "pacing loop terminato")
    }

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()

        while (running) {
            val index = try {
                codec.dequeueOutputBuffer(bufferInfo, 10_000)
            } catch (e: Exception) {
                if (running) AppLog.e(TAG, "dequeueOutputBuffer fallita", e)
                break
            }

            if (!running) break

            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> continue

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    AppLog.i(TAG, "Formato di output: ${codec.outputFormat}")
                    continue
                }

                index < 0 -> continue

                else -> {
                    try {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(index)
                            if (outputBuffer != null) {
                                val data = ByteArray(bufferInfo.size)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.get(data)

                                val isConfig =
                                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                                if (!isConfig) {
                                    val n = sampleCount.incrementAndGet()
                                    byteCount.addAndGet(data.size.toLong())
                                    if (n == 1L || n % 150L == 0L) {
                                        AppLog.i(
                                            TAG,
                                            "frame #$n (${data.size} B, " +
                                                    "flags=${bufferInfo.flags})"
                                        )
                                    }
                                    onSampleEncoded(data)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.e(TAG, "lettura sample fallita", e)
                    } finally {
                        try {
                            codec.releaseOutputBuffer(index, false)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        AppLog.i(TAG, "drain loop terminato")
    }

    companion object {
        private const val TAG = "HudEncoder"
    }
}
