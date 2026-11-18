package com.kuriousdragon.smartindex.tools.seeder.media

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File

/** 12 solid-colour 96×72 JPEGs (quality 70) cached in [dir] (`files/templates`; LLD §4.7). */
class JpegTemplates(private val dir: File) {
    private val memory = arrayOfNulls<ByteArray>(COUNT)

    @Synchronized
    fun get(template: Int): ByteArray {
        val index = template.mod(COUNT)
        memory[index]?.let { return it }
        val file = File(dir, "jpeg-$index.jpg")
        val bytes = if (file.length() > 0) {
            file.readBytes()
        } else {
            render(index).also {
                dir.mkdirs()
                file.writeBytes(it)
            }
        }
        memory[index] = bytes
        return bytes
    }

    private fun render(index: Int): ByteArray {
        val hue = index * 360f / COUNT
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.9f)))
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
                .toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val COUNT = 12
        const val WIDTH = 96
        const val HEIGHT = 72
        const val QUALITY = 70
    }
}
