# bitmapsToMp4
Android Lib, bitmaps convert to mp4 with mediaCodec

Bitmaps转Mp4视频
如果是ImageView的话，提供了`YapVideoUtils.convertViewToBitmap(findViewById(R.id.img_test))`方法
可以方便的把View专成bitmap

## 引用
How to
To get a Git project into your build:

Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Step 2. Add the dependency
```
	dependencies {
	        implementation 'com.github.YaphetZhao:bitmapsToMp4:1.0'
	}
```

## 1. 使用方法
Activity继承：IYapVideoProvider<Bitmap>
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
                // 当 progress>=1f 视频保存完成
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val uri: Uri = Uri.fromFile(File(latestResultPath))
                intent.data = uri
                sendBroadcast(intent)
            }
            val 进度 = """
                Save Progress: ${progress * 100}%
                """.trimIndent()
        }
    }
```

## 2. 开始保存视频
```
    // 获取手机相册目录
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
    // 创建输出文件File文件名称，根据自己的需求改一下
    latestResultPath = "${galleryPath}YapBitmapToMp4_${System.currentTimeMillis()}.mp4"
    // 开始保存视频，可以在主线程调用，也可以在线程中调用，方法里面做了判断
    YapVideoEncoder(this, File(latestResultPath)).start()
```

## 其他

可以clone这个项目，运行app来查看demo效果

如果有问题可以联系我

## 我

微信：yaphetzhao92

邮箱：yaphetzhao@foxmail.com

如果您觉得对您有帮助，就请我喝杯咖啡吧
![image](https://github.com/YaphetZhao/bitmapsToMp4/blob/master/wechat_qr.png)