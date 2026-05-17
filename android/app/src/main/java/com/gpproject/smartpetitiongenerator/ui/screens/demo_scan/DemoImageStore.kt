package com.gpproject.smartpetitiongenerator.ui.screens.demo_scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object DemoImageStore {

    private const val DEMO_DIR_NAME = "demo_images"

    private fun demoDir(context: Context): File {
        val dir = File(context.filesDir, DEMO_DIR_NAME)

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun saveImageFromUri(context: Context, uri: Uri): File? {
        return try {
            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null

            val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val fixedBitmap = rotateBitmapByExif(originalBitmap, orientation)

            val dir = demoDir(context)
            val targetFile = File(dir, "demo_${System.currentTimeMillis()}.jpg")

            FileOutputStream(targetFile).use { output ->
                fixedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }

            if (fixedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            targetFile

        } catch (e: Exception) {
            null
        }
    }

    fun getDemoImages(context: Context): List<File> {
        val dir = demoDir(context)

        return dir.listFiles()
            ?.filter { file ->
                file.isFile &&
                        (
                                file.extension.equals("jpg", ignoreCase = true) ||
                                        file.extension.equals("jpeg", ignoreCase = true) ||
                                        file.extension.equals("png", ignoreCase = true)
                                )
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteDemoImage(file: File): Boolean {
        return runCatching {
            file.delete()
        }.getOrDefault(false)
    }

    fun decodeFileToBitmap(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateBitmapByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> {
                return bitmap
            }
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}