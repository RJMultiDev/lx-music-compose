package cn.guoyujie666.music.compose.core.music.sources

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * QRC lyric decoder — 3DES-ECB + zlib inflate.
 * Ported 1:1 from desktop/src/renderer/utils/musicSdk/tx/qrcDecode.js.
 */
object QrcDecoder {
    // QRC fixed key: !@#)(*$%123ZXC!@!@#)(NHL
    private val QRC_KEY = byteArrayOf(
        0x21, 0x40, 0x23, 0x29, 0x28, 0x2a, 0x24, 0x25,
        0x31, 0x32, 0x33, 0x5a, 0x58, 0x43, 0x21, 0x40,
        0x21, 0x40, 0x23, 0x29, 0x28, 0x4e, 0x48, 0x4c
    )

    // Modified DES S-boxes — non-standard at S2[23]=15, S4[53]=10
    private val SBOX = arrayOf(
        intArrayOf(14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13),
        intArrayOf(15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9),
        intArrayOf(10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12),
        intArrayOf(7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14),
        intArrayOf(2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3),
        intArrayOf(12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13),
        intArrayOf(4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12),
        intArrayOf(13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11)
    )

    private val KEY_RND_SHIFT = intArrayOf(1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1)
    private val KEY_PERM_C = intArrayOf(56,48,40,32,24,16,8,0,57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35)
    private val KEY_PERM_D = intArrayOf(62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,60,52,44,36,28,20,12,4,27,19,11,3)
    private val KEY_COMPRESSION = intArrayOf(13,16,10,23,0,4,2,27,14,5,20,9,22,18,11,3,25,7,15,6,26,19,12,1,40,51,30,36,46,54,29,39,50,44,32,47,43,48,38,55,33,52,45,41,49,35,28,31)

    private fun bitnum(arr: ByteArray, b: Int, c: Int): Int {
        return ((arr[(b / 32) * 4 + 3 - ((b % 32) / 8)].toInt() ushr (7 - (b % 8))) and 1) shl c
    }

    private fun bitnumIntr(a: Int, b: Int, c: Int): Int = (((a ushr (31 - b)) and 1) shl c)

    private fun bitnumIntl(a: Int, b: Int, c: Int): Int = ((((a shl b) and 0x80000000.toInt()) ushr c))

    private fun sboxBit(a: Int): Int = (a and 32) or ((a and 31) ushr 1) or ((a and 1) shl 4)

    private fun initialPermutation(input: ByteArray): Pair<Int, Int> {
        fun bn(i: Int, s: Int) = bitnum(input, i, s)
        val s0 = (bn(57,31) or bn(49,30) or bn(41,29) or bn(33,28) or bn(25,27) or bn(17,26) or bn(9,25) or bn(1,24) or bn(59,23) or bn(51,22) or bn(43,21) or bn(35,20) or bn(27,19) or bn(19,18) or bn(11,17) or bn(3,16) or bn(61,15) or bn(53,14) or bn(45,13) or bn(37,12) or bn(29,11) or bn(21,10) or bn(13,9) or bn(5,8) or bn(63,7) or bn(55,6) or bn(47,5) or bn(39,4) or bn(31,3) or bn(23,2) or bn(15,1) or bn(7,0))
        val s1 = (bn(56,31) or bn(48,30) or bn(40,29) or bn(32,28) or bn(24,27) or bn(16,26) or bn(8,25) or bn(0,24) or bn(58,23) or bn(50,22) or bn(42,21) or bn(34,20) or bn(26,19) or bn(18,18) or bn(10,17) or bn(2,16) or bn(60,15) or bn(52,14) or bn(44,13) or bn(36,12) or bn(28,11) or bn(20,10) or bn(12,9) or bn(4,8) or bn(62,7) or bn(54,6) or bn(46,5) or bn(38,4) or bn(30,3) or bn(22,2) or bn(14,1) or bn(6,0))
        return Pair(s0, s1)
    }

    private fun inversePermutation(s0: Int, s1: Int, out: ByteArray) {
        fun bi(a: Int, b: Int, c: Int) = bitnumIntr(a, b, c)
        out[3] = (bi(s1,7,7) or bi(s0,7,6) or bi(s1,15,5) or bi(s0,15,4) or bi(s1,23,3) or bi(s0,23,2) or bi(s1,31,1) or bi(s0,31,0)).toByte()
        out[2] = (bi(s1,6,7) or bi(s0,6,6) or bi(s1,14,5) or bi(s0,14,4) or bi(s1,22,3) or bi(s0,22,2) or bi(s1,30,1) or bi(s0,30,0)).toByte()
        out[1] = (bi(s1,5,7) or bi(s0,5,6) or bi(s1,13,5) or bi(s0,13,4) or bi(s1,21,3) or bi(s0,21,2) or bi(s1,29,1) or bi(s0,29,0)).toByte()
        out[0] = (bi(s1,4,7) or bi(s0,4,6) or bi(s1,12,5) or bi(s0,12,4) or bi(s1,20,3) or bi(s0,20,2) or bi(s1,28,1) or bi(s0,28,0)).toByte()
        out[7] = (bi(s1,3,7) or bi(s0,3,6) or bi(s1,11,5) or bi(s0,11,4) or bi(s1,19,3) or bi(s0,19,2) or bi(s1,27,1) or bi(s0,27,0)).toByte()
        out[6] = (bi(s1,2,7) or bi(s0,2,6) or bi(s1,10,5) or bi(s0,10,4) or bi(s1,18,3) or bi(s0,18,2) or bi(s1,26,1) or bi(s0,26,0)).toByte()
        out[5] = (bi(s1,1,7) or bi(s0,1,6) or bi(s1,9,5) or bi(s0,9,4) or bi(s1,17,3) or bi(s0,17,2) or bi(s1,25,1) or bi(s0,25,0)).toByte()
        out[4] = (bi(s1,0,7) or bi(s0,0,6) or bi(s1,8,5) or bi(s0,8,4) or bi(s1,16,3) or bi(s0,16,2) or bi(s1,24,1) or bi(s0,24,0)).toByte()
    }

    private fun desF(state: Int, key: ByteArray): Int {
        val t1 = (
            bitnumIntl(state,31,0) or ((state and 0xF0000000.toInt()) ushr 1) or bitnumIntl(state,4,5) or bitnumIntl(state,3,6) or
            ((state and 0x0F000000) ushr 3) or bitnumIntl(state,8,11) or bitnumIntl(state,7,12) or ((state and 0x00F00000) ushr 5) or
            bitnumIntl(state,12,17) or bitnumIntl(state,11,18) or ((state and 0x000F0000) ushr 7) or bitnumIntl(state,16,23)
        )
        val t2 = (
            bitnumIntl(state,15,0) or (((state and 0x0000F000) shl 15)) or bitnumIntl(state,20,5) or bitnumIntl(state,19,6) or
            ((state and 0x00000F00) shl 13) or bitnumIntl(state,24,11) or bitnumIntl(state,23,12) or ((state and 0x000000F0) shl 11) or
            bitnumIntl(state,28,17) or bitnumIntl(state,27,18) or ((state and 0x0000000F) shl 9) or bitnumIntl(state,0,23)
        )
        val lrg = intArrayOf(
            (t1 ushr 24) and 0xFF, (t1 ushr 16) and 0xFF, (t1 ushr 8) and 0xFF,
            (t2 ushr 24) and 0xFF, (t2 ushr 16) and 0xFF, (t2 ushr 8) and 0xFF
        )
        for (i in 0..5) lrg[i] = lrg[i] xor (key[i].toInt() and 0xFF)
        val s = (
            (SBOX[0][sboxBit(lrg[0] ushr 2)] shl 28) or
            (SBOX[1][sboxBit(((lrg[0] and 0x03) shl 4) or (lrg[1] ushr 4))] shl 24) or
            (SBOX[2][sboxBit(((lrg[1] and 0x0F) shl 2) or (lrg[2] ushr 6))] shl 20) or
            (SBOX[3][sboxBit(lrg[2] and 0x3F)] shl 16) or
            (SBOX[4][sboxBit(lrg[3] ushr 2)] shl 12) or
            (SBOX[5][sboxBit(((lrg[3] and 0x03) shl 4) or (lrg[4] ushr 4))] shl 8) or
            (SBOX[6][sboxBit(((lrg[4] and 0x0F) shl 2) or (lrg[5] ushr 6))] shl 4) or
            SBOX[7][sboxBit(lrg[5] and 0x3F)]
        )
        return (
            bitnumIntl(s,15,0) or bitnumIntl(s,6,1) or bitnumIntl(s,19,2) or bitnumIntl(s,20,3) or
            bitnumIntl(s,28,4) or bitnumIntl(s,11,5) or bitnumIntl(s,27,6) or bitnumIntl(s,16,7) or
            bitnumIntl(s,0,8) or bitnumIntl(s,14,9) or bitnumIntl(s,22,10) or bitnumIntl(s,25,11) or
            bitnumIntl(s,4,12) or bitnumIntl(s,17,13) or bitnumIntl(s,30,14) or bitnumIntl(s,9,15) or
            bitnumIntl(s,1,16) or bitnumIntl(s,7,17) or bitnumIntl(s,23,18) or bitnumIntl(s,13,19) or
            bitnumIntl(s,31,20) or bitnumIntl(s,26,21) or bitnumIntl(s,2,22) or bitnumIntl(s,8,23) or
            bitnumIntl(s,18,24) or bitnumIntl(s,12,25) or bitnumIntl(s,29,26) or bitnumIntl(s,5,27) or
            bitnumIntl(s,21,28) or bitnumIntl(s,10,29) or bitnumIntl(s,3,30) or bitnumIntl(s,24,31)
        )
    }

    private fun desCrypt(input: ByteArray, schedule: Array<ByteArray>, output: ByteArray) {
        val (s0Init, s1Init) = initialPermutation(input)
        var s0 = s0Init
        var s1 = s1Init
        for (i in 0..14) {
            val prev = s1
            s1 = desF(s1, schedule[i]) xor s0
            s0 = prev
        }
        s0 = desF(s1, schedule[15]) xor s0
        inversePermutation(s0, s1, output)
    }

    private fun keySchedule(key: ByteArray): Array<ByteArray> {
        val schedule = Array(16) { ByteArray(6) }
        var c = 0
        var d = 0
        for (i in 0..27) {
            c = c or bitnum(key, KEY_PERM_C[i], 31 - i)
            d = d or bitnum(key, KEY_PERM_D[i], 31 - i)
        }
        for (i in 0..15) {
            val shift = KEY_RND_SHIFT[i]
            c = (((c shl shift) or (c ushr (28 - shift))) and 0xFFFFFFF0.toInt())
            d = (((d shl shift) or (d ushr (28 - shift))) and 0xFFFFFFF0.toInt())
            for (j in 0..23) {
                schedule[i][j / 8] = (schedule[i][j / 8].toInt() or bitnumIntr(c, KEY_COMPRESSION[j], 7 - (j % 8))).toByte()
            }
            for (j in 24..47) {
                schedule[i][j / 8] = (schedule[i][j / 8].toInt() or bitnumIntr(d, KEY_COMPRESSION[j] - 27, 7 - (j % 8))).toByte()
            }
        }
        return schedule
    }

    private fun tripleDesCrypt(input: ByteArray, output: ByteArray) {
        // 3DES EDE3 decrypt: D(KA) → E(KB) → D(KC)
        // QRC_KEY bytes: [0-7]=!@#)(*$%, [8-15]=123ZXC!@, [16-23]=!@#)(NHL
        // Desktop order for decrypt: key.subarray(16,24) first, then (8,16), then (0,8)
        val ka = QRC_KEY.copyOfRange(16, 24) // first in schedule → D(KA)
        val kb = QRC_KEY.copyOfRange(8, 16)  // second in schedule → E(KB)
        val kc = QRC_KEY.copyOfRange(0, 8)   // third in schedule → D(KC)
        val sKa = keySchedule(ka)
        val sKb = keySchedule(kb)
        val sKc = keySchedule(kc)
        val buf = ByteArray(8)
        // D(KA): decrypt — reverse round keys
        val sKaRev = Array(16) { sKa[15 - it] }
        desCrypt(input, sKaRev, buf)
        // E(KB): encrypt — normal order
        desCrypt(buf, sKb, output)
        // D(KC): decrypt — reverse round keys
        val sKcRev = Array(16) { sKc[15 - it] }
        desCrypt(output, sKcRev, buf)
        output.let { System.arraycopy(buf, 0, it, 0, 8) }
    }

    /**
     * Decrypt a QRC hex string to plain lyric text.
     * @param hexData hex-encoded encrypted QRC data from TX API
     * @return decrypted lyric text, or empty string on failure
     */
    fun decode(hexData: String): String {
        if (hexData.isEmpty() || hexData.length % 2 != 0) return ""
        return try {
            val bytes = ByteArray(hexData.length / 2)
            for (i in bytes.indices) {
                bytes[i] = hexData.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            if (bytes.isEmpty()) return ""
            android.util.Log.d("QrcDecoder", "hex len=${hexData.length} bytes len=${bytes.size}")

            // 3DES decrypt in 8-byte blocks
            val block = ByteArray(8)
            for (i in 0 until bytes.size step 8) {
                val input = bytes.copyOfRange(i, minOf(i + 8, bytes.size))
                if (input.size < 8) break
                tripleDesCrypt(input, block)
                System.arraycopy(block, 0, bytes, i, 8)
            }
            android.util.Log.d("QrcDecoder", "after 3DES decrypt")

            // zlib inflate
            val inflater = Inflater()
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var totalInflated = 0
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                out.write(buf, 0, n)
                totalInflated += n
            }
            inflater.end()
            val result = out.toString("UTF-8")
            android.util.Log.d("QrcDecoder", "inflated $totalInflated bytes, result len=${result.length}")
            result
        } catch (e: Exception) {
            android.util.Log.e("QrcDecoder", "decode error: ${e.javaClass.simpleName} ${e.message}")
            ""
        }
    }
}
