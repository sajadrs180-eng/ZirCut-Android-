package app.zircut.core

import java.io.File

/**
 * A 3D colour look-up table read from a `.cube` file (fix222). [data] holds N*N*N RGB triples (0..1), the red index runs fastest,
 * then green, then blue - the order of the .cube format: index = ((b * N + g) * N + r) * 3.
 * Export: [cube] feeds Media3's SingleColorLut. Preview: a LUT is not a matrix, so [fit] finds the best 3x3 matrix + offset
 * (least squares) that looks similar; the exported video uses the exact LUT.
 */
class CubeLut(val size: Int, val data: FloatArray) {

    /** the LUT blended with "no change" by [amount] (0..1), as Media3 wants it: cube[r][g][b] = ARGB */
    fun cube(amount: Float): Array<Array<IntArray>> {
        val n = size
        val a = amount.coerceIn(0f, 1f)
        val d = (n - 1).toFloat()
        fun mix(v: Float, id: Float): Int = ((id + (v - id) * a).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return Array(n) { r ->
            Array(n) { g ->
                IntArray(n) { b ->
                    val i = ((b * n + g) * n + r) * 3
                    (0xFF shl 24) or (mix(data[i], r / d) shl 16) or (mix(data[i + 1], g / d) shl 8) or mix(data[i + 2], b / d)
                }
            }
        }
    }

    /** least-squares colour matrix that imitates this LUT blended by [amount]; the identity when the fit is impossible */
    fun fit(amount: Float): Affine {
        val n = size
        val a = amount.coerceIn(0f, 1f)
        val g = minOf(n, 17)
        val d = (n - 1).toFloat()
        val ata = Array(4) { DoubleArray(4) }
        val aty = Array(3) { DoubleArray(4) }
        for (ri in 0 until g) for (gi in 0 until g) for (bi in 0 until g) {
            val r = Math.round(ri * d / (g - 1).coerceAtLeast(1))
            val gg = Math.round(gi * d / (g - 1).coerceAtLeast(1))
            val b = Math.round(bi * d / (g - 1).coerceAtLeast(1))
            val x = doubleArrayOf(r / d.toDouble(), gg / d.toDouble(), b / d.toDouble(), 1.0)
            val i = ((b * n + gg) * n + r) * 3
            for (p in 0 until 4) for (q in 0 until 4) ata[p][q] += x[p] * x[q]
            for (c in 0 until 3) {
                val y = x[c] + (data[i + c] - x[c]) * a
                for (p in 0 until 4) aty[c][p] += x[p] * y
            }
        }
        for (p in 0 until 4) ata[p][p] += 1e-6
        val l = FloatArray(9)
        val o = FloatArray(3)
        for (c in 0 until 3) {
            val sol = solve(ata, aty[c]) ?: return Affine.IDENTITY
            for (k in 0 until 3) l[c * 3 + k] = sol[k].toFloat()
            o[c] = sol[3].toFloat()
        }
        return Affine(l, o)
    }

    companion object {
        const val MAX_SIZE = 65

        /** null when the text is not a usable 3D .cube */
        fun parse(text: String): CubeLut? {
            var n = 0
            var dmin = 0f
            var dmax = 1f
            var out: FloatArray? = null
            var count = 0
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '#') continue
                val t = line.split(Regex("\\s+"))
                val key = t[0]
                when {
                    key == "LUT_1D_SIZE" -> return null
                    key == "LUT_3D_SIZE" -> {
                        n = t.getOrNull(1)?.toIntOrNull() ?: return null
                        if (n < 2 || n > MAX_SIZE) return null
                        out = FloatArray(n * n * n * 3)
                    }
                    key == "DOMAIN_MIN" -> dmin = t.getOrNull(1)?.toFloatOrNull() ?: return null
                    key == "DOMAIN_MAX" -> dmax = t.getOrNull(1)?.toFloatOrNull() ?: return null
                    key[0].isLetter() -> {}   // TITLE and other headers
                    else -> {
                        val buf = out ?: return null
                        if (t.size < 3 || count + 3 > buf.size) return null
                        val span = if (dmax - dmin > 1e-6f) dmax - dmin else 1f
                        for (c in 0 until 3) buf[count + c] = (((t[c].toFloatOrNull() ?: return null) - dmin) / span).coerceIn(0f, 1f)
                        count += 3
                    }
                }
            }
            val buf = out ?: return null
            return if (count == buf.size) CubeLut(n, buf) else null
        }

        fun load(file: File): CubeLut? = try {
            if (!file.isFile || file.length() > 8_000_000L) null else parse(file.readText())
        } catch (_: Exception) {
            null
        }

        /** Gaussian elimination with partial pivoting for a 4x4 system; null when it is singular */
        private fun solve(m: Array<DoubleArray>, v: DoubleArray): DoubleArray? {
            val n = 4
            val a = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) m[i][j] else v[i] } }
            for (col in 0 until n) {
                var piv = col
                for (r in col + 1 until n) if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r
                if (Math.abs(a[piv][col]) < 1e-12) return null
                val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col] / a[col][col]
                    for (c in col..n) a[r][c] -= f * a[col][c]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}
