package camera.mavrolume.app.filmsim

import android.graphics.Bitmap
import android.graphics.Color
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln
import kotlin.math.pow

/**
 * Parses a .cube 3D LUT file and converts it to a 512x512 Hald CLUT bitmap
 * compatible with GPUImage's GPUImageLookupFilter.
 *
 * The Fuji .cube files expect **F-Log2 / F-Gamut** input (per their headers:
 * "FLog2_FGamut_to_ETERNA_BT.709"). Since both our preview pipeline (ISP sRGB)
 * and RAW pipeline (sRGB gamma after demosaic+CCM) provide sRGB data to the LUT,
 * we pre-compose the sRGB → F-Log2/F-Gamut conversion into the Hald CLUT at
 * parse time. This makes the resulting texture work correctly with sRGB input.
 *
 * Hald CLUT format: 8x8 grid of 64x64 tiles. Tile layout: B selects tile
 * (col = B%8, row = B/8), R is x within tile, G is y within tile.
 */
object CubeLutParser {

    private const val HALD_SIZE = 64  // color resolution per channel
    private const val TILES_PER_ROW = 8
    private const val TILE_SIZE = 64
    private const val BITMAP_SIZE = 512  // 8 * 64

    fun parse(input: InputStream): Bitmap {
        val (size, data) = readCubeData(input)
        return buildHaldClut(size, data)
    }

    private data class CubeData(val size: Int, val data: FloatArray)

    private fun readCubeData(input: InputStream): CubeData {
        var size = 0
        val values = ArrayList<Float>(33 * 33 * 33 * 3)

        BufferedReader(InputStreamReader(input)).use { reader ->
            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("TITLE") ||
                    line.startsWith("DOMAIN_MIN") || line.startsWith("DOMAIN_MAX")
                ) {
                    return@forEachLine
                }
                if (line.startsWith("LUT_3D_SIZE")) {
                    size = line.substringAfter("LUT_3D_SIZE").trim().toInt()
                    return@forEachLine
                }
                // Data line: "R G B"
                val parts = line.split(" ")
                if (parts.size >= 3) {
                    values.add(parts[0].toFloat())
                    values.add(parts[1].toFloat())
                    values.add(parts[2].toFloat())
                }
            }
        }

        require(size > 0) { ".cube file missing LUT_3D_SIZE" }
        require(values.size == size * size * size * 3) {
            "Expected ${size * size * size * 3} values, got ${values.size}"
        }

        return CubeData(size, values.toFloatArray())
    }

    // ── Color space conversion: sRGB → F-Log2 / F-Gamut ────────────────

    /** sRGB EOTF: sRGB gamma → linear */
    private fun srgbToLinear(v: Float): Float {
        return if (v <= 0.04045f) {
            v / 12.92f
        } else {
            ((v + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    /**
     * Linear → F-Log2 transfer function (Fujifilm spec).
     *
     * F-Log2 encoding:
     *   if (linear >= cut)  flog2 = c * log10(a * linear + b) + d
     *   else                flog2 = e * linear + f
     */
    private fun linearToFLog2(linear: Float): Float {
        val a = 5.555556f    // 1/0.18 ≈ 5.555556
        val b = 0.064829f
        val c = 0.245281f
        val d = 0.384316f
        val e = 8.009406f
        val f = 0.092864f
        val cut = 0.000889f

        return if (linear >= cut) {
            (c * log10(a * linear + b) + d).coerceIn(0f, 1f)
        } else {
            (e * linear + f).coerceIn(0f, 1f)
        }
    }

    private fun log10(x: Float): Float = (ln(x) / ln(10f))

    /**
     * BT.709/sRGB → F-Gamut 3×3 matrix (linear domain).
     *
     * Derived from the chromaticity primaries:
     *   F-Gamut: R(0.708,0.292) G(0.170,0.797) B(0.131,0.046) W=D65
     *   BT.709:  R(0.64,0.33)  G(0.30,0.60)   B(0.15,0.06)   W=D65
     *
     * M = M_fgamut_from_xyz * M_xyz_from_bt709
     *
     * Since F-Gamut is wider than BT.709, all BT.709 values map into F-Gamut
     * without clipping.
     */
    private fun bt709ToFGamut(r: Float, g: Float, b: Float): FloatArray {
        // Pre-computed matrix: inverse(M_fgamut_to_xyz) * M_bt709_to_xyz
        val fr = 0.6274f * r + 0.3293f * g + 0.0433f * b
        val fg = 0.0691f * r + 0.9195f * g + 0.0114f * b
        val fb = 0.0164f * r + 0.0880f * g + 0.8956f * b
        return floatArrayOf(fr, fg, fb)
    }

    /**
     * Convert sRGB (gamma-encoded) input to F-Log2 / F-Gamut for .cube LUT lookup.
     */
    private fun srgbToFLog2FGamut(sR: Float, sG: Float, sB: Float): FloatArray {
        // Step 1: sRGB gamma → linear sRGB
        val linR = srgbToLinear(sR)
        val linG = srgbToLinear(sG)
        val linB = srgbToLinear(sB)

        // Step 2: Linear BT.709/sRGB → linear F-Gamut
        val fgamut = bt709ToFGamut(linR, linG, linB)

        // Step 3: Linear → F-Log2
        return floatArrayOf(
            linearToFLog2(fgamut[0]),
            linearToFLog2(fgamut[1]),
            linearToFLog2(fgamut[2]),
        )
    }

    // ── 3D LUT sampling ────────────────────────────────────────────────

    /**
     * Looks up a color in the 3D LUT using trilinear interpolation.
     * Input coords are in 0.0..1.0 range (in the LUT's native color space).
     * Returns (r, g, b) floats in 0.0..1.0.
     */
    private fun trilinearSample(
        data: FloatArray,
        size: Int,
        r: Float,
        g: Float,
        b: Float,
    ): FloatArray {
        val maxIdx = size - 1
        val rf = r * maxIdx
        val gf = g * maxIdx
        val bf = b * maxIdx

        val r0 = rf.toInt().coerceIn(0, maxIdx - 1)
        val g0 = gf.toInt().coerceIn(0, maxIdx - 1)
        val b0 = bf.toInt().coerceIn(0, maxIdx - 1)
        val r1 = (r0 + 1).coerceAtMost(maxIdx)
        val g1 = (g0 + 1).coerceAtMost(maxIdx)
        val b1 = (b0 + 1).coerceAtMost(maxIdx)

        val rd = rf - r0
        val gd = gf - g0
        val bd = bf - b0

        // .cube ordering: R varies fastest, then G, then B
        // index = (b * size * size + g * size + r) * 3
        val result = FloatArray(3)
        for (ch in 0..2) {
            val c000 = data[(b0 * size * size + g0 * size + r0) * 3 + ch]
            val c100 = data[(b0 * size * size + g0 * size + r1) * 3 + ch]
            val c010 = data[(b0 * size * size + g1 * size + r0) * 3 + ch]
            val c110 = data[(b0 * size * size + g1 * size + r1) * 3 + ch]
            val c001 = data[(b1 * size * size + g0 * size + r0) * 3 + ch]
            val c101 = data[(b1 * size * size + g0 * size + r1) * 3 + ch]
            val c011 = data[(b1 * size * size + g1 * size + r0) * 3 + ch]
            val c111 = data[(b1 * size * size + g1 * size + r1) * 3 + ch]

            val c00 = c000 + (c100 - c000) * rd
            val c10 = c010 + (c110 - c010) * rd
            val c01 = c001 + (c101 - c001) * rd
            val c11 = c011 + (c111 - c011) * rd

            val c0 = c00 + (c10 - c00) * gd
            val c1 = c01 + (c11 - c01) * gd

            result[ch] = (c0 + (c1 - c0) * bd).coerceIn(0f, 1f)
        }
        return result
    }

    // ── Hald CLUT generation ───────────────────────────────────────────

    private fun buildHaldClut(size: Int, data: FloatArray): Bitmap {
        val pixels = IntArray(BITMAP_SIZE * BITMAP_SIZE)

        for (b in 0 until HALD_SIZE) {
            val tileCol = b % TILES_PER_ROW
            val tileRow = b / TILES_PER_ROW
            val tileX = tileCol * TILE_SIZE
            val tileY = tileRow * TILE_SIZE

            for (g in 0 until HALD_SIZE) {
                for (r in 0 until HALD_SIZE) {
                    // Input is sRGB (what the shader will provide).
                    // Convert to F-Log2/F-Gamut before looking up in the .cube LUT.
                    val sR = r / 63f
                    val sG = g / 63f
                    val sB = b / 63f

                    val flog2 = srgbToFLog2FGamut(sR, sG, sB)
                    val out = trilinearSample(data, size, flog2[0], flog2[1], flog2[2])

                    val x = tileX + r
                    val y = tileY + g
                    pixels[y * BITMAP_SIZE + x] = Color.argb(
                        255,
                        (out[0] * 255f + 0.5f).toInt().coerceIn(0, 255),
                        (out[1] * 255f + 0.5f).toInt().coerceIn(0, 255),
                        (out[2] * 255f + 0.5f).toInt().coerceIn(0, 255),
                    )
                }
            }
        }

        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, BITMAP_SIZE, 0, 0, BITMAP_SIZE, BITMAP_SIZE)
        return bitmap
    }
}
