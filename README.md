# bitmapsToMp4
Android Lib, bitmaps convert to mp4 with mediaCodec

继承：IYapVideoProvider<Bitmap>
重写方法
```
    override fun size(): Int {
        return 视频总帧数
    }

    override fun next(): Bitmap {
        return 下一帧的bitmap
    }
    
    override fun progress(progress: Float) {
        runOnUiThread {
            if (progress >= 1f) {
                视频保存完成
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val uri: Uri = Uri.fromFile(File(latestResultPath))
                intent.data = uri
                sendBroadcast(intent)
            }
            进度 = """
                Save Progress: ${progress * 100}%
                """.trimIndent()
        }
    }
```
