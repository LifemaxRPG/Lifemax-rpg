package com.lifemax.rpg.core.lifedirector

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/**
 * LDF-1, LDF-2: `Math.exp` and `Math.pow` as the page's browser computes them. The recording
 * Chromium returns the correctly rounded result (the double nearest the exact value) for every
 * recorded argument, which is neither `java.lang.Math` (hardware intrinsics) nor `StrictMath`
 * (fdlibm, within one unit in the last place, as older JavaScript engines are): they differ in the
 * last bit on about one value in ten. So the value is worked out here: the exact result in
 * double-double arithmetic (about 104 bits, products split by Dekker's method, never a fused
 * multiply-add), rounded once; when that cannot decide the rounding, or the result is subnormal or
 * near overflow, the neighbours of the fdlibm value are compared with the exact result taken to 60
 * digits. Every step is IEEE double arithmetic in a fixed order, so the answer is the same on every
 * JVM and on ART.
 */
internal object PageMath {
    private const val LN2_HI = 0.6931471805599453
    private const val LN2_LO = 2.3190468138462996e-17
    private const val SPLITTER = 134217729.0
    private const val REDUCTION_STEPS = 10
    private const val REDUCTION = 1024.0
    private const val TAYLOR_TERMS = 12
    private const val SAFE_HIGH = 700.0
    private const val SAFE_LOW = -650.0
    private const val EXACT_POWERS = 1100.0
    private const val OVERFLOW = 709.79
    private const val UNDERFLOW = -745.14
    private const val MARGIN = 8.077935669463161e-28 // 2^-90
    private val CONTEXT = MathContext(60, RoundingMode.HALF_EVEN)
    private val LN2 =
        BigDecimal(
            "0.69314718055994530941723212145817656807550013436025525412068000949339362196969472"
        )

    /** A double-double: [hi] + [lo], `|lo| <= ulp(hi) / 2`. */
    private class Dd(val hi: Double, val lo: Double)

    private fun quickSum(a: Double, b: Double): Dd {
        val s = a + b
        return Dd(s, b - (s - a))
    }

    private fun sum(a: Double, b: Double): Dd {
        val s = a + b
        val v = s - a
        return Dd(s, (a - (s - v)) + (b - v))
    }

    private fun product(a: Double, b: Double): Dd {
        val p = a * b
        val ca = SPLITTER * a
        val ah = ca - (ca - a)
        val al = a - ah
        val cb = SPLITTER * b
        val bh = cb - (cb - b)
        val bl = b - bh
        return Dd(p, ((ah * bh - p) + ah * bl + al * bh) + al * bl)
    }

    private fun add(a: Dd, b: Dd): Dd {
        val s = sum(a.hi, b.hi)
        val t = sum(a.lo, b.lo)
        val u = quickSum(s.hi, s.lo + t.hi)
        return quickSum(u.hi, u.lo + t.lo)
    }

    private fun mul(a: Dd, b: Dd): Dd {
        val p = product(a.hi, b.hi)
        return quickSum(p.hi, p.lo + (a.hi * b.lo + a.lo * b.hi))
    }

    private fun scale(a: Dd, by: Double): Dd = Dd(a.hi * by, a.lo * by)

    private fun div(a: Dd, b: Double): Dd {
        val q = a.hi / b
        val p = product(q, b)
        return quickSum(q, (((a.hi - p.hi) - p.lo) + a.lo) / b)
    }

    /** `exp(x) - 1` of a small double-double (|x| below 0.35 / 1024), by its Taylor series. */
    private fun expm1Small(x: Dd): Dd {
        var term = x
        var total = x
        for (n in 2..TAYLOR_TERMS) {
            term = div(mul(term, x), n.toDouble())
            total = add(total, term)
        }
        return total
    }

    /**
     * `exp(x)` of a double-double in the normal range, as [hi, lo] with about 2^-100 relative
     * error: `x = k ln 2 + r`, `exp(r / 1024)` by series, squared ten times as `2s + s^2`.
     */
    private fun expDd(x: Dd): Dd {
        val k = floor(x.hi / LN2_HI + 0.5)
        val kLn2 = add(product(k, LN2_HI), product(k, LN2_LO))
        val r = add(x, Dd(-kLn2.hi, -kLn2.lo))
        var s = expm1Small(scale(r, 1.0 / REDUCTION))
        repeat(REDUCTION_STEPS) { s = add(scale(s, 2.0), mul(s, s)) }
        val e = add(Dd(1.0, 0.0), s)
        val twoK = Math.scalb(1.0, k.toInt())
        return Dd(e.hi * twoK, e.lo * twoK)
    }

    /** The double nearest [v], or null when [v]'s error could put it either side of a midpoint. */
    private fun rounded(v: Dd): Double? {
        val r = sum(v.hi, v.lo)
        val half = (if (r.lo < 0) r.hi - Math.nextDown(r.hi) else Math.nextUp(r.hi) - r.hi) / 2
        return if (abs(r.lo) < half - abs(r.hi) * MARGIN) r.hi else null
    }

    /** LDF-1: `Math.exp(x)`, correctly rounded. */
    fun exp(x: Double): Double {
        if (x.isNaN()) return x
        if (x >= OVERFLOW) return Double.POSITIVE_INFINITY
        if (x <= UNDERFLOW) return 0.0
        if (x == 0.0) return 1.0
        if (x in SAFE_LOW..SAFE_HIGH)
            rounded(expDd(Dd(x, 0.0)))?.let {
                return it
            }
        return nearest(StrictMath.exp(x), exact(BigDecimal(x)))
    }

    /**
     * LDF-2: `Math.pow(base, exponent)`, correctly rounded, for the engine's use (base 0.5), a
     * power of two to a whole power, and any other positive base whose result is in the normal
     * range; the special values as IEEE `pow` (and JavaScript: a NaN exponent, or 1 to an infinite
     * power, is NaN). Outside that (a negative base, or another base near the ends of the range) it
     * is fdlibm's, which the engine never asks for.
     */
    fun pow(base: Double, exponent: Double): Double {
        if (exponent.isNaN()) return Double.NaN
        if (exponent == 0.0) return 1.0
        if (base.isNaN()) return Double.NaN
        if (exponent.isInfinite() || base <= 0.0 || base.isInfinite()) {
            return if (abs(base) == 1.0 && exponent.isInfinite()) Double.NaN
            else StrictMath.pow(base, exponent)
        }
        if (exponent == floor(exponent) && abs(exponent) <= EXACT_POWERS && powerOfTwo(base))
            return twoTo(Math.getExponent(base) * exponent)
        val power = mul(Dd(exponent, 0.0), logDd(base))
        if (power.hi in SAFE_LOW..SAFE_HIGH)
            rounded(expDd(power))?.let {
                return it
            }
        if (power.hi >= OVERFLOW) return Double.POSITIVE_INFINITY
        if (power.hi <= UNDERFLOW) return 0.0
        if (base != HALF) return StrictMath.pow(base, exponent)
        return nearest(
            StrictMath.pow(base, exponent),
            exact(BigDecimal(exponent).negate().multiply(LN2, CONTEXT)),
        )
    }

    private const val HALF = 0.5
    private const val LOWEST_EXPONENT = -1074.0
    private const val HIGHEST_EXPONENT = 1023.0

    private fun powerOfTwo(x: Double): Boolean = x == Math.scalb(1.0, Math.getExponent(x))

    /**
     * `2^p` for a whole `p`, exactly: infinity past the largest double, 0 below the smallest
     * subnormal (`2^-1075`, exactly half of it, rounds to the even 0).
     */
    private fun twoTo(p: Double): Double =
        when {
            p > HIGHEST_EXPONENT -> Double.POSITIVE_INFINITY
            p < LOWEST_EXPONENT -> 0.0
            else -> Math.scalb(1.0, p.toInt())
        }

    /** `ln(a)` as a double-double: one Newton step from the double logarithm. */
    private fun logDd(a: Double): Dd {
        if (a == HALF) return Dd(-LN2_HI, -LN2_LO)
        val y = StrictMath.log(a)
        val e = expDd(Dd(-y, 0.0))
        val t = add(mul(Dd(a, 0.0), e), Dd(-1.0, 0.0))
        return add(Dd(y, 0.0), t)
    }

    /** `e^x` to 60 digits. */
    private fun exact(x: BigDecimal): BigDecimal {
        val k = x.divide(LN2, CONTEXT).setScale(0, RoundingMode.HALF_EVEN)
        val r = x.subtract(LN2.multiply(k, CONTEXT), CONTEXT)
        var term = BigDecimal.ONE
        var total = BigDecimal.ONE
        var n = 1
        while (
            term.signum() != 0 && term.abs() > BigDecimal.ONE.movePointLeft(CONTEXT.precision + 2)
        ) {
            term = term.multiply(r, CONTEXT).divide(BigDecimal(n), CONTEXT)
            total = total.add(term, CONTEXT)
            n++
        }
        val two = BigDecimal(2)
        val power =
            if (k.signum() >= 0) two.pow(k.toInt()) else BigDecimal.ONE.divide(two.pow(-k.toInt()))
        return total.multiply(power, CONTEXT)
    }

    /** The double nearest [value], searched from [guess] (within a few units of it). */
    private fun nearest(guess: Double, value: BigDecimal): Double {
        var c = if (guess.isInfinite()) Double.MAX_VALUE else guess
        while (true) {
            val below = Math.nextDown(c)
            val above = Math.nextUp(c)
            val low =
                if (below < 0) null else BigDecimal(c).add(BigDecimal(below)).divide(BigDecimal(2))
            val high =
                if (above.isInfinite()) null
                else BigDecimal(c).add(BigDecimal(above)).divide(BigDecimal(2))
            c =
                when {
                    low != null && value < low -> below
                    high == null &&
                        value >= BigDecimal(c).add(BigDecimal(Math.ulp(c)).divide(BigDecimal(2))) ->
                        return Double.POSITIVE_INFINITY
                    high != null && value > high -> above
                    low != null && value.compareTo(low) == 0 -> return if (even(c)) c else below
                    high != null && value.compareTo(high) == 0 -> return if (even(c)) c else above
                    else -> return c
                }
        }
    }

    private fun even(d: Double): Boolean = (java.lang.Double.doubleToRawLongBits(d) and 1L) == 0L
}
