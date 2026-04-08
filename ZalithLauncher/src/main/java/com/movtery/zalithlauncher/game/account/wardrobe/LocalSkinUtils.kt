/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.account.wardrobe

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun legacyStrFill(str: String, code: Char, length: Int): String {
    return if (str.length > length) {
        str.take(length)
    } else {
        str.padEnd(length, code).drop(str.length) + str
    }
}

private fun getLocalUuid(name: String): String {
    val lenHex = name.length.toString(16)
    val lengthPart = legacyStrFill(lenHex, '0', 16)

    val hashCode = name.hashCode().toLong() and 0xFFFFFFFFL
    val hashHex = hashCode.toString(16)
    val hashPart = legacyStrFill(hashHex, '0', 16) //确保最长16位

    return buildString(34) {
        append(lengthPart.take(12))
        append('3')
        append(lengthPart.substring(13, 16))
        append('9')
        append(hashPart.take(15))
    }
}

/**
 * 根据皮肤模型类型，生成 profileId
 */
fun getLocalUUIDWithSkinModel(userName: String, skinModelType: SkinModelType): String {
    val baseUuid = getLocalUuid(userName)
    if (skinModelType == SkinModelType.NONE) return baseUuid

    val prefix = baseUuid.take(27)
    val a = baseUuid[7].digitToInt(16)
    val b = baseUuid[15].digitToInt(16)
    val c = baseUuid[23].digitToInt(16)

    var suffix = baseUuid.substring(27).toLong(16)
    val maxSuffix = 0xFFFFFL

    repeat(maxSuffix.toInt() + 1) {
        val currentD = (suffix and 0xFL).toInt()
        if ((a xor b xor c xor currentD) % 2 == skinModelType.targetParity) {
            return prefix + suffix.toString(16).padStart(5, '0').uppercase()
        }
        suffix = if (suffix == maxSuffix) 0L else suffix + 1
    }

    return prefix + suffix.toString(16).padStart(5, '0').uppercase()
}

/**
 * 检查皮肤像素合法性，Minecraft仅支持使用64x64或64x32像素的皮肤
 */
suspend fun validateSkinFile(skinFile: File): Boolean {
    return withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(skinFile.absolutePath, options)

        val width = options.outWidth
        val height = options.outHeight

        //像素尺寸是否满足 64x64 或 32x32
        (width == 64 && height == 32) || (width == 64 && height == 64)
    }
}

/**
 * 检查皮肤是否为纤细（Alex）模型
 */
suspend fun File.isSlimModel(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        // 先读取文件，如果为空直接返回 false
        val bitmap = BitmapFactory.decodeFile(absolutePath) ?: return@runCatching false
        
        try {
            // 纤细/经典模型的判断依赖于 64x64 格式的皮肤
            if (bitmap.width != 64 || bitmap.height != 64) return@runCatching false

            val pixels = IntArray(64 * 64)
            bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)

            val slimCheck = hasTransparency(pixels, 50, 16, 2, 4) ||
                    hasTransparency(pixels, 54, 20, 2, 12) ||
                    hasTransparency(pixels, 42, 48, 2, 4) ||
                    hasTransparency(pixels, 46, 52, 2, 12)

            // 如果已经确定有透明像素，直接返回 true，省去后续黑块检测
            if (slimCheck) return@runCatching true

            val blackCheck = isAreaBlack(pixels, 50, 16, 2, 4) &&
                    isAreaBlack(pixels, 54, 20, 2, 12) &&
                    isAreaBlack(pixels, 42, 48, 2, 4) &&
                    isAreaBlack(pixels, 46, 52, 2, 12)

            blackCheck
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }.getOrDefault(false)
}

private fun hasTransparency(pixels: IntArray, x: Int, y: Int, w: Int, h: Int): Boolean {
    for (i in y until y + h) {
        for (j in x until x + w) {
            if ((pixels[i * 64 + j] ushr 24) < 128) return true
        }
    }
    return false
}

private fun isAreaBlack(pixels: IntArray, x: Int, y: Int, w: Int, h: Int): Boolean {
    for (i in y until y + h) {
        for (j in x until x + w) {
            if (pixels[i * 64 + j] != 0xFF000000.toInt()) return false
        }
    }
    return true
}