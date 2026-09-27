package com.jagapathi.immichtv.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    fun generateQrCode(text: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i ->
            if (bitMatrix[i % size, i / size]) Color.BLACK else Color.WHITE
        }
        return createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}
