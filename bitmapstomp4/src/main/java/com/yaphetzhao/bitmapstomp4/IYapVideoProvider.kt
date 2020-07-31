package com.yaphetzhao.bitmapstomp4

/**
 * @author YaphetZhao
 * @email yaphetzhao@gmail.com
 * @data 2020-07-30
 * @wechat yaphetzhao92
 */
interface IYapVideoProvider<Bitmap> {

    /**
     * bitmap list size, you can set like
     *
     * return bitmapList.size()
     */
    fun size(): Int

    /**
     * the next bitmap
     */
    operator fun next(): Bitmap

    /**
     * progress
     * If 1f is returned, progress is complete
     * A return of -1 indicates failure
     */
    fun progress(progress: Float)

}