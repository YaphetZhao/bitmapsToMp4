@file:Suppress(names = ["DEPRECATION", "SpellCheckingInspection"])

package com.yaphetzhao.bitmapstomp4

import android.graphics.Bitmap
import android.media.*
import android.os.Build
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * @author YaphetZhao
 * @email yaphetzhao@gmail.com
 * @data 2020-07-30
 * @wechat yaphetzhao92
 */
class YapVideoEncoder(
    private val IProvider: IYapVideoProvider<Bitmap>,
    private val out: File,
    private val mFrameRate: Int = 60
) {
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var mMuxerStarted = false
    private var isRunning = false
    private var mTrackIndex = 0
    private var colorFormat = 0
    private val defaultTimeOutUs = 10000L

    private val mediaCodecList: IntArray
        get() {
            val numCodecs = MediaCodecList.getCodecCount()
            var codecInfo: MediaCodecInfo? = null
            var i = 0
            while (i < numCodecs && codecInfo == null) {
                val info = MediaCodecList.getCodecInfoAt(i)
                if (!info.isEncoder) {
                    i++
                    continue
                }
                val types = info.supportedTypes
                var found = false
                // The decoder required by the rotation training
                var j = 0
                while (j < types.size && !found) {
                    if (types[j] == "video/avc") {
                        found = true
                    }
                    j++
                }
                if (!found) {
                    i++
                    continue
                }
                codecInfo = info
                i++
            }
            val capabilities = codecInfo!!.getCapabilitiesForType("video/avc")
            return capabilities.colorFormats
        }

    private fun init(width: Int, height: Int) {
        val formats: IntArray = mediaCodecList
        lab@ for (format in formats) {
            when (format) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> {
                    colorFormat = format
                    break@lab
                }
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> {
                    colorFormat = format
                    break@lab
                }
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> {
                    colorFormat = format
                    break@lab
                }
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> {
                    colorFormat = format
                    break@lab
                }
                else -> break@lab
            }
        }

        if (colorFormat <= 0) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
        var widthFix = width
        if (widthFix % 2 != 0) {
            widthFix -= 1
        }
        var heightFix = height
        if (heightFix % 2 != 0) {
            heightFix -= 1
        }
        val mediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, widthFix, heightFix)
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, widthFix * heightFix)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)

        // For planar YUV format, the Y of all pixels is stored consecutively,
        // followed by the U of all pixels, followed by the V of all pixels
        // For the YUV format of packed, the Y,U,
        // and V of each pixel are continuously cross-stored

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            // Create the generated MP4 initialization object
            if (!out.exists()) {
                out.createNewFile()
            }
            mediaMuxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec!!.start()
        isRunning = true
    }

    fun start() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            thread(start = true) {
                start()
            }
            return
        }
        try {
            if (IProvider.size() > 0) {
                val bitmap = IProvider.next()
                init(bitmap.width, bitmap.height)
                run(bitmap)
            }
        } finally {
            finish()
        }
    }

    private fun finish() {
        isRunning = false
        if (mediaCodec != null) {
            mediaCodec!!.stop()
            mediaCodec!!.release()
        }
        if (mediaMuxer != null) {
            try {
                if (mMuxerStarted) {
                    mediaMuxer!!.stop()
                    mediaMuxer!!.release()
                }
            } catch (e: Exception) {
                IProvider.progress(-1f)
                e.printStackTrace()
            }
        }
    }

    private fun run(bitmapFirst: Bitmap?) {
        var bitmap = bitmapFirst
        isRunning = true
        var generateIndex: Long = 0
        val info = MediaCodec.BufferInfo()
        var buffers: Array<ByteBuffer?>? = null
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            buffers = mediaCodec!!.inputBuffers
        }
        while (isRunning) {
            val inputBufferIndex = mediaCodec!!.dequeueInputBuffer(defaultTimeOutUs)
            if (inputBufferIndex >= 0) {
                val ptsUsec = computePresentationTime(generateIndex)
                IProvider.progress(
                    generateIndex / IProvider.size().toFloat()
                )
                if (generateIndex >= IProvider.size()) {
                    mediaCodec!!.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        ptsUsec,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    isRunning = false
                    drainEncoder(true, info)
                } else {
                    if (bitmap == null) {
                        bitmap = IProvider.next()
                    }
                    var widthFix = bitmap.width
                    if (widthFix % 2 != 0) {
                        widthFix -= 1
                    }
                    var heightFix = bitmap.height
                    if (heightFix % 2 != 0) {
                        heightFix -= 1
                    }
                    val input = getNV12(widthFix, heightFix, bitmap)
                    bitmap = null
                    // Valid empty cache
                    val inputBuffer = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                        buffers!![inputBufferIndex]
                    } else {
                        mediaCodec!!.getInputBuffer(inputBufferIndex)
                    }
                    inputBuffer!!.clear()
                    inputBuffer.put(input)
                    // Put the data on the encoding queue
                    mediaCodec!!.queueInputBuffer(inputBufferIndex, 0, input.size, ptsUsec, 0)
                    drainEncoder(false, info)
                }
                generateIndex++
            } else {
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    IProvider.progress(-1f)
                }
            }
        }
    }

    private fun computePresentationTime(frameIndex: Long): Long {
        return 132 + frameIndex * 1000000 / mFrameRate
    }

    private fun drainEncoder(endOfStream: Boolean, bufferInfo: MediaCodec.BufferInfo) {
        var buffers: Array<ByteBuffer?>? = null
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            buffers = mediaCodec!!.outputBuffers
        }
        if (endOfStream) {
            try {
                mediaCodec!!.signalEndOfInputStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        while (true) {
            val encoderStatus = mediaCodec!!.dequeueOutputBuffer(bufferInfo, defaultTimeOutUs)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mMuxerStarted) {
                    error { "format changed twice" }
                }
                val mediaFormat = mediaCodec!!.outputFormat
                mTrackIndex = mediaMuxer!!.addTrack(mediaFormat)
                mediaMuxer!!.start()
                mMuxerStarted = true
            } else if (encoderStatus < 0) {
                Log.d(
                    "YapVideoEncoder",
                    "unexpected result from encoder.dequeueOutputBuffer: $encoderStatus"
                )
            } else {
                val outputBuffer = (if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    buffers!![encoderStatus]
                } else {
                    mediaCodec!!.getOutputBuffer(encoderStatus)
                }) ?: error { "encoderOutputBuffer $encoderStatus was null" }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        Log.d("YapVideoEncoder", "error:muxer hasn't started")
                    }
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    try {
                        mediaMuxer!!.writeSampleData(mTrackIndex, outputBuffer, bufferInfo)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                mediaCodec!!.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        Log.d("YapVideoEncoder", "reached end of stream unexpectedly")
                        IProvider.progress(-1f)
                    } else {
                        Log.d("YapVideoEncoder", "end of stream reached")
                    }
                    break
                }
            }
        }
    }

    private fun getNV12(inputWidth: Int, inputHeight: Int, scaled: Bitmap?): ByteArray {
        val argb = IntArray(inputWidth * inputHeight)
        scaled!!.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val yuv = ByteArray(inputWidth * inputHeight * 3 / 2)
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> encodeYUV420SP(
                yuv,
                argb,
                inputWidth,
                inputHeight
            )
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> encodeYUV420P(
                yuv,
                argb,
                inputWidth,
                inputHeight
            )
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> encodeYUV420PSP(
                yuv,
                argb,
                inputWidth,
                inputHeight
            )
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> encodeYUV420PP(
                yuv,
                argb,
                inputWidth,
                inputHeight
            )
        }
        return yuv
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                // val a = argb[index] and -0x1000000 shr 24
                val r = argb[index] and 0xff0000 shr 16
                val g = argb[index] and 0xff00 shr 8
                val b = argb[index] and 0xff shr 0
                val y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                val u = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                val v = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                    yuv420sp[uvIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                }
                index++
            }
        }
    }

    private fun encodeYUV420P(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + width * height / 4
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                // val a = argb[index] and -0x1000000 shr 24
                val r = argb[index] and 0xff0000 shr 16
                val g = argb[index] and 0xff00 shr 8
                val b = argb[index] and 0xff shr 0
                val y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                val u = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                val v = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[vIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                    yuv420sp[uIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                }
                index++
            }
        }
    }

    private fun encodeYUV420PSP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        var yIndex = 0
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                // val a = argb[index] and -0x1000000 shr 24
                val r = argb[index] and 0xff0000 shr 16
                val g = argb[index] and 0xff00 shr 8
                val b = argb[index] and 0xff shr 0
                val y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                val u = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                val v = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[yIndex + 1] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                    yuv420sp[yIndex + 3] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                }
                if (index % 2 == 0) {
                    yIndex++
                }
                index++
            }
        }
    }

    private fun encodeYUV420PP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        var yIndex = 0
        var vIndex = yuv420sp.size / 2
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                // val a = argb[index] and -0x1000000 shr 24
                val r = argb[index] and 0xff0000 shr 16
                val g = argb[index] and 0xff00 shr 8
                val b = argb[index] and 0xff shr 0
                val y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                val u = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                val v = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                if (j % 2 == 0 && index % 2 == 0) { // 0
                    yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                    yuv420sp[yIndex + 1] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                    yuv420sp[vIndex + 1] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                    yIndex++
                } else if (j % 2 == 0 && index % 2 == 1) { //1
                    yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                } else if (j % 2 == 1 && index % 2 == 0) { //2
                    yuv420sp[vIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                    vIndex++
                } else if (j % 2 == 1 && index % 2 == 1) { //3
                    yuv420sp[vIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                }
                index++
            }
        }
    }

}