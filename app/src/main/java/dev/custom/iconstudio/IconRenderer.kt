package dev.custom.iconstudio

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

enum class IconStyle(val label: String) {
    NOTHING("Nothing"),
    LIQUID("Liquid Glass"),
    GLASS("Glassmorphism"),
    NEU("Neumorphism"),
    RETRO("Retromorphism"),
    CLAY("Claymorphism")
}

/** Draws every icon procedurally, so the whole app stays tiny. */
object IconRenderer {
    private const val S = 192
    private const val F = 192f
    private val AA = Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG

    fun render(src: Drawable, iconStyle: IconStyle, glass: Float = 0.6f): Bitmap {
        val d = src.constantState?.newDrawable()?.mutate() ?: src
        val g = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 96, 96); d.draw(Canvas(g))
        val tint = vivid(dominant(g), 0f)
        val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val rect = RectF(0f, 0f, F, F)
        var radius = F * 0.26f

        when (iconStyle) {
            IconStyle.NOTHING -> {
                c.drawColor(0xFF0B0B0B.toInt())
                val n = 22
                val sm = Bitmap.createScaledBitmap(g, n, n, true)
                val ls = FloatArray(n * n); val al = FloatArray(n * n)
                var lo = 1f; var hi = 0f; var sum = 0f; var cnt = 0
                for (i in 0 until n * n) {
                    val p = sm.getPixel(i % n, i / n)
                    al[i] = Color.alpha(p) / 255f
                    ls[i] = (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f
                    if (al[i] > 0.35f) { lo = min(lo, ls[i]); hi = max(hi, ls[i]); sum += ls[i]; cnt++ }
                }
                val invert = cnt > 0 && sum / cnt > 0.55f
                val cell = F * 0.72f / n; val off = (F - cell * n) / 2f
                val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                for (i in 0 until n * n) {
                    if (al[i] <= 0.35f) continue
                    var v = if (hi - lo < 0.08f) 0.7f else (ls[i] - lo) / (hi - lo)
                    if (invert) v = 1f - v
                    dot.color = Color.argb((90 + 165 * v).toInt(), 255, 255, 255)
                    c.drawCircle(off + (i % n + 0.5f) * cell, off + (i / n + 0.5f) * cell,
                        cell * 0.5f * (0.3f + 0.7f * v), dot)
                }
                dot.color = 0xFFD71921.toInt()
                c.drawCircle(F * 0.83f, F * 0.17f, F * 0.035f, dot)
                val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFF2A2A2A.toInt() }
                c.drawRoundRect(RectF(2f, 2f, F - 2, F - 2), radius, radius, st)
            }

            IconStyle.LIQUID -> {
                radius = F * 0.30f
                val th = glass.coerceIn(0f, 1f)
                val hA = vivid(tint, 48f)
                val hB = vivid(tint, -52f)
                val shape = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                c.save()
                c.clipPath(shape)

                // 1. deep aurora backdrop: three colour blobs on a dark gradient
                c.drawRect(rect, Paint(AA).apply {
                    shader = LinearGradient(0f, 0f, F, F, mix(tint, Color.BLACK, .5f), mix(hB, Color.BLACK, .7f), Shader.TileMode.CLAMP) })
                blob(c, F * .22f, F * .28f, F * .70f, hA, 235)
                blob(c, F * .85f, F * .30f, F * .55f, tint, 215)
                blob(c, F * .50f, F * .95f, F * .75f, hB, 225)

                // 2. frosted glass slab over the aurora
                c.drawRect(rect, Paint(AA).apply {
                    shader = LinearGradient(0f, 0f, 0f, F, 0x40FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP) })

                // 3. refraction ghost: a blurred, magnified copy of the glyph sitting "inside" the glass
                val ghost = Bitmap.createScaledBitmap(g, 10, 10, true)
                val gs = F * (.74f + .10f * th)
                val gl = (F - gs) / 2f
                c.drawBitmap(ghost, null, RectF(gl, gl + F * .04f, gl + gs, gl + gs + F * .04f),
                    Paint(AA).apply { alpha = (90 + 80 * th).toInt() })
                glyph(c, g, .56f, true)

                // 4. thick lens edge: soft bright band running along the inside of the border
                val band = F * (.045f + .10f * th)
                c.drawRoundRect(rect, radius, radius, Paint(AA).apply {
                    style = Paint.Style.STROKE; strokeWidth = band * 2f
                    maskFilter = BlurMaskFilter(band * .6f, BlurMaskFilter.Blur.NORMAL)
                    shader = LinearGradient(0f, 0f, F, F,
                        intArrayOf(0xF2FFFFFF.toInt(), 0x14FFFFFF, 0x00FFFFFF, 0x99FFFFFF.toInt()),
                        floatArrayOf(0f, .35f, .6f, 1f), Shader.TileMode.CLAMP) })

                // 5. caustic light pooling at the bottom
                c.drawCircle(F * .72f, F * 1.02f, F * .55f, Paint(AA).apply {
                    shader = RadialGradient(F * .72f, F * 1.02f, F * .55f,
                        withAlpha(mix(hA, Color.WHITE, .5f), 0xA0), 0x00FFFFFF, Shader.TileMode.CLAMP) })

                // 6. curved specular reflection across the top
                c.drawOval(RectF(F * .09f, F * .03f, F * .91f, F * .48f), Paint(AA).apply {
                    shader = LinearGradient(0f, F * .03f, 0f, F * .48f, 0xB8FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP) })

                // 7. sparkle
                c.drawCircle(F * .25f, F * .21f, F * .05f, Paint(AA).apply {
                    color = 0x55FFFFFF; maskFilter = BlurMaskFilter(F * .03f, BlurMaskFilter.Blur.NORMAL) })
                c.drawCircle(F * .25f, F * .21f, F * .022f, Paint(AA).apply { color = 0xEEFFFFFF.toInt() })

                // 8. chromatic rim: pink and cyan fringes under a white edge
                val rr = RectF(3f, 3f, F - 3f, F - 3f)
                fun fringe(dx: Float, dy: Float, col: Int) {
                    val r = RectF(rr); r.offset(dx, dy)
                    c.drawRoundRect(r, radius, radius, Paint(AA).apply {
                        style = Paint.Style.STROKE; strokeWidth = 2.5f; color = col })
                }
                fringe(-1.8f, -1.8f, 0x99FF4DA6.toInt())
                fringe(1.8f, 1.8f, 0x9940E0FF.toInt())
                c.drawRoundRect(rr, radius, radius, Paint(AA).apply {
                    style = Paint.Style.STROKE; strokeWidth = 3.5f
                    shader = LinearGradient(0f, 0f, F, F,
                        intArrayOf(0xEEFFFFFF.toInt(), 0x11FFFFFF, 0x99FFFFFF.toInt()),
                        floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) })
                c.restore()
            }

            IconStyle.GLASS -> {
                val bg = Paint(AA).apply {
                    shader = LinearGradient(0f, 0f, F, F, mix(tint, Color.WHITE, .15f), vivid(tint, 70f), Shader.TileMode.CLAMP) }
                c.drawRect(rect, bg)
                val b1 = Paint(AA).apply {
                    shader = RadialGradient(F * .2f, F * .25f, F * .5f, 0xCCFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP) }
                c.drawCircle(F * .2f, F * .25f, F * .5f, b1)
                val b2 = Paint(AA).apply {
                    shader = RadialGradient(F * .85f, F * .85f, F * .55f, vivid(tint, 140f), 0x00FFFFFF, Shader.TileMode.CLAMP) }
                c.drawCircle(F * .85f, F * .85f, F * .55f, b2)
                val panel = RectF(F * .13f, F * .13f, F * .87f, F * .87f)
                c.drawRoundRect(panel, F * .2f, F * .2f, Paint(AA).apply { color = 0x44FFFFFF })
                val edge = Paint(AA).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2.5f
                    shader = LinearGradient(0f, 0f, F, F, 0xBBFFFFFF.toInt(), 0x22FFFFFF, Shader.TileMode.CLAMP) }
                c.drawRoundRect(panel, F * .2f, F * .2f, edge)
                glyph(c, g, .50f, true)
            }

            IconStyle.NEU -> {
                val bg = 0xFFE0E5EC.toInt()
                c.drawColor(bg)
                val plate = RectF(F * .15f, F * .15f, F * .85f, F * .85f)
                val dark = Paint(AA).apply { color = bg; setShadowLayer(16f, 8f, 8f, 0xFFA3B1C6.toInt()) }
                c.drawRoundRect(plate, F * .2f, F * .2f, dark)
                val light = Paint(AA).apply { color = bg; setShadowLayer(16f, -8f, -8f, 0xFFFFFFFF.toInt()) }
                c.drawRoundRect(plate, F * .2f, F * .2f, light)
                glyph(c, g, .46f, false)
            }

            IconStyle.RETRO -> {
                radius = F * .05f
                c.drawColor(0xFFC0C0C0.toInt())
                val n = 24
                val sm = Bitmap.createScaledBitmap(g, n, n, false).copy(Bitmap.Config.ARGB_8888, true)
                for (y in 0 until n) for (x in 0 until n) {
                    val p = sm.getPixel(x, y)
                    val q = { v: Int -> (v shr 6) * 85 }
                    sm.setPixel(x, y, Color.argb(if (Color.alpha(p) > 100) 255 else 0, q(Color.red(p)), q(Color.green(p)), q(Color.blue(p))))
                }
                c.drawBitmap(sm, null, RectF(F * .2f, F * .2f, F * .8f, F * .8f), Paint())
                val w = Paint().apply { color = Color.WHITE }
                val gr = Paint().apply { color = 0xFF808080.toInt() }
                c.drawRect(0f, 0f, F, 7f, w); c.drawRect(0f, 0f, 7f, F, w)
                c.drawRect(0f, F - 7f, F, F, gr); c.drawRect(F - 7f, 0f, F, F, gr)
                val bk = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
                c.drawRect(2f, 2f, F - 2, F - 2, bk)
            }

            IconStyle.CLAY -> {
                radius = F * .32f
                val body = mix(tint, Color.WHITE, .4f)
                c.drawRoundRect(rect, radius, radius, Paint(AA).apply {
                    shader = LinearGradient(0f, 0f, 0f, F, mix(body, Color.WHITE, .3f), mix(body, Color.BLACK, .22f), Shader.TileMode.CLAMP) })
                c.drawRoundRect(rect, radius, radius, Paint(AA).apply {
                    shader = RadialGradient(F * .3f, F * .25f, F * .6f, 0xAAFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP) })
                c.drawRoundRect(RectF(5f, 5f, F - 5, F - 5), radius, radius, Paint(AA).apply {
                    style = Paint.Style.STROKE; strokeWidth = 5f; color = 0x55FFFFFF })
                glyph(c, g, .54f, true)
            }
        }
        return mask(out, radius)
    }

    private fun glyph(c: Canvas, g: Bitmap, scale: Float, shadow: Boolean) {
        val sz = F * scale; val l = (F - sz) / 2f
        val dst = RectF(l, l, l + sz, l + sz)
        if (shadow) {
            val sp = Paint(AA).apply { colorFilter = PorterDuffColorFilter(0x55000000, PorterDuff.Mode.SRC_IN) }
            val d2 = RectF(dst); d2.offset(0f, F * .03f); c.drawBitmap(g, null, d2, sp)
        }
        c.drawBitmap(g, null, dst, Paint(AA))
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun blob(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Int) {
        c.drawCircle(x, y, r, Paint(AA).apply {
            shader = RadialGradient(x, y, r, withAlpha(color, a), withAlpha(color, 0), Shader.TileMode.CLAMP) })
    }

    private fun mask(b: Bitmap, r: Float): Bitmap {
        val m = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        Canvas(m).drawRoundRect(RectF(0f, 0f, F, F), r, r, Paint(Paint.ANTI_ALIAS_FLAG))
        Canvas(b).drawBitmap(m, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        return b
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun l(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(l(Color.alpha(a), Color.alpha(b)), l(Color.red(a), Color.red(b)),
            l(Color.green(a), Color.green(b)), l(Color.blue(a), Color.blue(b)))
    }

    private fun vivid(c: Int, shift: Float): Int {
        val h = FloatArray(3); Color.colorToHSV(c, h)
        h[0] = (h[0] + shift) % 360f; h[1] = h[1].coerceIn(.35f, 1f); h[2] = h[2].coerceIn(.6f, 1f)
        return Color.HSVToColor(h)
    }

    private fun dominant(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 12, 12, true)
        var r = 0.0; var gg = 0.0; var bl = 0.0; var w = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until 12) for (x in 0 until 12) {
            val p = s.getPixel(x, y)
            if (Color.alpha(p) < 120) continue
            Color.colorToHSV(p, hsv)
            val k = hsv[1] * hsv[2] + 0.03
            r += Color.red(p) * k; gg += Color.green(p) * k; bl += Color.blue(p) * k; w += k
        }
        if (w == 0.0) return 0xFF7A8CFF.toInt()
        return Color.rgb((r / w).toInt(), (gg / w).toInt(), (bl / w).toInt())
    }
}
