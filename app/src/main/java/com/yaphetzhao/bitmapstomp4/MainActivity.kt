package com.yaphetzhao.bitmapstomp4

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * @author YaphetZhao
 * @email yaphetzhao@gmail.com
 * @data 2020-07-30
 * @wechat yaphetzhao92
 */
class MainActivity : AppCompatActivity(), IYapVideoProvider<Bitmap> {

    private val progressTv: TextView by lazy {
        findViewById<TextView>(R.id.progress)
    }

    private val startBtn: Button by lazy {
        findViewById<Button>(R.id.start)
    }

    private val galleryPath: String
        get() {
            val result = (Environment.getExternalStorageDirectory().toString()
                    + File.separator + Environment.DIRECTORY_DCIM
                    + File.separator + "Camera" + File.separator)
            val file = File(result)
            if (!file.exists()) {
                file.mkdir()
            }
            return result + File.separator
        }

    private var latestResultPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                    return@setOnClickListener
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
                    return@setOnClickListener
                }
            }
            startBtn.isEnabled = false
            latestResultPath = "${galleryPath}YapBitmapToMp4_${System.currentTimeMillis()}.mp4"
            YapVideoEncoder(this, File(latestResultPath)).start()
        }
    }

    override fun size(): Int {
        // 60fps and 60s
        // this test video is 1min
        // you can set your bitmap list size in here
        return 60 * 5
    }

    private val textPaint by lazy {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
        textPaint.textSize = 50.0f
        textPaint.color = Color.BLACK
        textPaint.isFakeBoldText = false
        textPaint.setShadowLayer(3f, 0f, 0f, resources.getColor(android.R.color.background_dark))
        textPaint
    }

    /**
     * get the next bitmap
     */
    override fun next(): Bitmap {
        val bitmapResult = YapVideoUtils.convertViewToBitmap(findViewById(R.id.img_test))
        val canvas = Canvas(bitmapResult)
        canvas.drawText(System.currentTimeMillis().toString(), 100f, 100f, textPaint)
        canvas.save()
        canvas.restore()
        return bitmapResult
    }

    @SuppressLint("SetTextI18n")
    override fun progress(progress: Float) {
        runOnUiThread {
            if (progress >= 1f) {
                startBtn.isEnabled = true
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val uri: Uri = Uri.fromFile(File(latestResultPath))
                intent.data = uri
                sendBroadcast(intent)
            }
            progressTv.text = """
                Save Progress: ${progress * 100}%
                """.trimIndent()
        }
    }
}