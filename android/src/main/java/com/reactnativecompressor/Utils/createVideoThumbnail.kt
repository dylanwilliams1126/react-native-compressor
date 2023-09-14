package com.reactnativecompressor.Utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.webkit.URLUtil
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.GuardedResultAsyncTask
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.UUID


class CreateVideoThumbnailClass(private val reactContext: ReactApplicationContext) {
    @ReactMethod
    fun create(fileUrl:String,options: ReadableMap, promise: Promise) {
        ProcessDataTask(reactContext,fileUrl, promise, options).execute()
    }

    private class ProcessDataTask(reactContext: ReactContext,private val filePath:String, private val promise: Promise, private val options: ReadableMap) : GuardedResultAsyncTask<ReadableMap?>(reactContext.exceptionHandler) {
        private val weakContext: WeakReference<Context>

        init {
            weakContext = WeakReference(reactContext.applicationContext)
        }

        @RequiresApi(Build.VERSION_CODES.N)
        override fun doInBackgroundGuarded(): ReadableMap? {
            val format = "jpeg"
            val cacheName = if (options.hasKey("cacheName")) options.getString("cacheName") else ""
            val thumbnailDir = weakContext.get()!!.applicationContext.cacheDir.absolutePath + "/thumbnails"
            val cacheDir = createDirIfNotExists(thumbnailDir)
            if (!TextUtils.isEmpty(cacheName)) {
                val file = File(thumbnailDir, "$cacheName.$format")
                if (file.exists()) {
                    val map = Arguments.createMap()
                    map.putString("path", "file://" + file.absolutePath)
                    val image = BitmapFactory.decodeFile(file.absolutePath)
                    map.putDouble("size", image.byteCount.toDouble())
                    map.putString("mime", "image/$format")
                    map.putDouble("width", image.width.toDouble())
                    map.putDouble("height", image.height.toDouble())
                    return map
                }
            }
            val headers: Map<String, String> = if (options.hasKey("headers")) options.getMap("headers")!!.toHashMap() as Map<String, String> else HashMap<String, String>()
            val fileName = if (TextUtils.isEmpty(cacheName)) "thumb-" + UUID.randomUUID().toString() else "$cacheName.$format"
            var fOut: OutputStream? = null
            try {
                val file = File(cacheDir, fileName)
                val context = weakContext.get()
                val image = getBitmapAtTime(context, filePath, 0, headers)
                file.createNewFile()
                fOut = FileOutputStream(file)

                // 100 means no compression, the lower you go, the stronger the compression
                image.compress(Bitmap.CompressFormat.JPEG, 90, fOut)
                fOut.flush()
                fOut.close()

                val map = Arguments.createMap()
                map.putString("path", "file://" + file.absolutePath)
                map.putDouble("size", image.byteCount.toDouble())
                map.putString("mime", "image/$format")
                map.putDouble("width", image.width.toDouble())
                map.putDouble("height", image.height.toDouble())
                return map
            } catch (e: Exception) {
                promise.reject("CreateVideoThumbnail_ERROR", e)
            }
            return null
        }

        override fun onPostExecuteGuarded(readableArray: ReadableMap?) {
            promise.resolve(readableArray)
        }
    }

    companion object {
        // delete previously added files one by one untill requred space is available
        fun clearCache(cacheDir: String?,promise:Promise, reactContext: ReactApplicationContext) {
          val cacheDirectory=cacheDir?.takeIf { it.isNotEmpty() } ?:"/thumbnails"
          val thumbnailDir: String = reactContext.getApplicationContext().getCacheDir().getAbsolutePath() + cacheDirectory
          val thumbnailDirFile = createDirIfNotExists(thumbnailDir)

          if (thumbnailDirFile != null) {
            val files = thumbnailDirFile.listFiles()

            // Loop through the files and delete them
            if (files != null) {
              for (file in files) {
                if (file.isFile) {
                  file.delete()
                }
              }
            }
          }
          promise.resolve("done")
        }

        private fun createDirIfNotExists(path: String): File {
            val dir = File(path)
            if (dir.exists()) {
                return dir
            }
            try {
                dir.mkdirs()
                // Add .nomedia to hide the thumbnail directory from gallery
                val noMedia = File(path, ".nomedia")
                noMedia.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return dir
        }

        private fun getBitmapAtTime(context: Context?, filePath: String?, time: Int, headers: Map<String, String>): Bitmap {
            val retriever = MediaMetadataRetriever()
            if (URLUtil.isFileUrl(filePath)) {
                val decodedPath: String?
                decodedPath = try {
                    URLDecoder.decode(filePath, "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                    filePath
                }
                retriever.setDataSource(decodedPath!!.replace("file://", ""))
            } else if (filePath!!.contains("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath))
            } else {
                check(Build.VERSION.SDK_INT >= 14) { "Remote videos aren't supported on sdk_version < 14" }
                retriever.setDataSource(filePath, headers)
            }
            val image = retriever.getFrameAtTime((time * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            try {
                retriever.release()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            checkNotNull(image) { "File doesn't exist or not supported" }
            return image
        }
    }
}
