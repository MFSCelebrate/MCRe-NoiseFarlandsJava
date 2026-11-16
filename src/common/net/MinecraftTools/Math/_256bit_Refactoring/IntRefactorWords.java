package net.MinecraftTools.Math._256bit_Refactoring;

import java.util.Arrays;

/**
 * 无符号分组原语（低组在前 {@code long[]}）——IntRefactor256 的除法/余数、FloatRefactor256 的
 * sqrt 支撑。包私有，仅为同包两个公开类服务。
 *
 * <p>移植自 Long2-0.1.8 {@code UnsignedWords}（MIT 风格开源实现，AxorBisInfinitive 作品）的精选
 * 子集：Knuth Algorithm D 32-bit limb 除法（试商 long 单步无溢出）、部分积乘法、位工具。
 * 约定：数组低组在前（index 0 = 最低 64 位），方法不裁剪高零组（trim 除外）。
 */
final class IntRefactorWords {

    private static final long DIGIT_MASK = 0xffff_ffffL;
    private static final long DIGIT_BASE = 0x1_0000_0000L;

    /**
     * Knuth D 工作区池（ThreadLocal 独占）：dividend ≤ 16 ints（473 位 + 进位组）、divisor ≤ 8 ints。
     * 容量留一倍余量；本类除法不被嵌套调用（含 sqrt Newton 的串行迭代），单线程复用安全。
     */
    private static final ThreadLocal<int[][]> DIGIT_POOL = ThreadLocal.withInitial(() -> new int[][] {
        new int[32], new int[16]
    });

    private IntRefactorWords() {
    }

    /** 除法结果：商与余数均为低组在前，已 trim。 */
    record Division(long[] quotient, long[] remainder) {
    }

    /** 去掉高零组；全零返回空数组。 */
    static long[] trim(long[] value) {
        int length = length(value);
        return length == value.length ? value : Arrays.copyOf(value, length);
    }

    private static int length(long[] value) {
        int index = value.length;
        while (index > 0 && value[index - 1] == 0) {
            index--;
        }
        return index;
    }

    /** 越界分组补零。 */
    static long word(long[] value, int index) {
        return index >= 0 && index < value.length ? value[index] : 0L;
    }

    /** 无符号位长（0 表示全零）。 */
    static int bitLength(long[] value) {
        for (int index = value.length - 1; index >= 0; index--) {
            if (value[index] != 0) {
                return index * 64 + 64 - Long.numberOfLeadingZeros(value[index]);
            }
        }
        return 0;
    }

    static boolean bit(long[] value, long index) {
        return index >= 0 && (index >>> 6) < value.length
                && (value[(int) (index >>> 6)] & (1L << (index & 63))) != 0;
    }

    /** 低 count 位是否存在一；非正数量返回 false，不取模。 */
    static boolean anyLow(long[] value, long count) {
        if (count <= 0) {
            return false;
        }
        int whole = (int) Math.min(count >>> 6, value.length);
        for (int index = 0; index < whole; index++) {
            if (value[index] != 0) {
                return true;
            }
        }
        int bits = (int) (count & 63);
        return whole < value.length && bits != 0 && (value[whole] & ((1L << bits) - 1)) != 0;
    }

    /** 无符号比较。 */
    static int compare(long[] a, long[] b) {
        int length = Math.max(a.length, b.length);
        for (int index = length - 1; index >= 0; index--) {
            int comparison = Long.compareUnsigned(word(a, index), word(b, index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /** 无符号相减（调用方保证 a >= b）。 */
    static long[] subtract(long[] a, long[] b) {
        long[] result = Arrays.copyOf(a, Math.max(a.length, b.length));
        long borrow = 0;
        for (int index = 0; index < result.length; index++) {
            long subtrahend = word(b, index);
            long difference = result[index] - subtrahend;
            long next = difference - borrow;
            borrow = (Long.compareUnsigned(result[index], subtrahend) < 0
                    || Long.compareUnsigned(difference, borrow) < 0) ? 1 : 0;
            result[index] = next;
        }
        return result;
    }

    /** 左移 count 位（count >= 0）。 */
    static long[] left(long[] value, int count) {
        if (count == 0) {
            return Arrays.copyOf(value, value.length);
        }
        int whole = count >>> 6, bits = count & 63;
        long[] result = new long[value.length + whole + 1];
        for (int index = 0; index < value.length; index++) {
            long current = value[index];
            if (current == 0) {
                continue;
            }
            result[index + whole] |= current << bits;
            if (bits != 0) {
                result[index + whole + 1] |= current >>> (64 - bits);
            }
        }
        return trim(result);
    }

    /** 低 count 位（0 < count <= 64*length）。 */
    static long[] lowBits(long[] value, int count) {
        int whole = count >>> 6, bits = count & 63;
        long[] result = new long[whole + (bits != 0 ? 1 : 0)];
        for (int index = 0; index < result.length; index++) {
            result[index] = word(value, index);
        }
        if (bits != 0) {
            result[whole] &= (1L << bits) - 1;
        }
        return result;
    }

    /** 无符号部分积乘法（低组在前）。 */
    static long[] multiply(long[] a, long[] b) {
        long[] result = new long[a.length + b.length];
        for (int i = 0; i < a.length; i++) {
            long carry = 0;
            for (int j = 0; j < b.length; j++) {
                long low = a[i] * b[j];
                long high = Math.unsignedMultiplyHigh(a[i], b[j]);
                long sum = low + result[i + j];
                high += Long.compareUnsigned(sum, low) < 0 ? 1 : 0;
                long next = sum + carry;
                high += Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i + j] = next;
                carry = high;
            }
            result[i + b.length] = carry;
        }
        return trim(result);
    }

    // ═══════════ Knuth Algorithm D（32-bit limb） ═══════════

    /**
     * 无符号除法（商 + 余数）——64-bit limb Knuth Algorithm D。
     * 🔧 移植 ScalarWide {@code Limbs.divmod} 的核心：试商用 64/32 分解（divQ）、
     * 乘减用 {@code Math.unsignedMultiplyHigh}（128 位硬件指令）——原 32-bit limb 版
     * （Long2 UnsignedWords 同款）试商轮数多一倍且乘减是 32×32，实测慢 5×。
     */
    static Division divide(long[] numerator, long[] denominator) {
        int n = denominator.length;
        while (n > 0 && denominator[n - 1] == 0) {
            n--;
        }
        if (n == 0) {
            throw new ArithmeticException("Unsigned division by zero");
        }
        int uz = numerator.length;
        while (uz > 0 && numerator[uz - 1] == 0) {
            uz--;
        }
        if (compareUnsigned(numerator, uz, denominator, n) < 0) {
            return new Division(new long[0], Arrays.copyOf(numerator, uz));
        }
        if (n == 1) {
            final long[] quotient = new long[Math.max(1, uz)];
            final long[] rem = divmodSingle(numerator, uz, denominator[0], quotient);
            return new Division(trim(quotient), rem);
        }
        final int s = Long.numberOfLeadingZeros(denominator[n - 1]);
        final long[] vn = new long[n];
        if (s == 0) {
            System.arraycopy(denominator, 0, vn, 0, n);
        } else {
            for (int i = n - 1; i > 0; i--) {
                vn[i] = (denominator[i] << s) | (denominator[i - 1] >>> (64 - s));
            }
            vn[0] = denominator[0] << s;
        }
        final long[] un = new long[uz + 1];
        if (s == 0) {
            System.arraycopy(numerator, 0, un, 0, uz);
        } else {
            un[uz] = numerator[uz - 1] >>> (64 - s);
            for (int i = uz - 1; i > 0; i--) {
                un[i] = (numerator[i] << s) | (numerator[i - 1] >>> (64 - s));
            }
            un[0] = numerator[0] << s;
        }
        final long[] quotient = new long[Math.max(1, uz - n + 1)];
        for (int j = uz - n; j >= 0; j--) {
            final long u2 = un[j + n];
            long qhat;
            if (u2 == vn[n - 1]) {
                qhat = -1L;
                long rhat = un[j + n - 1] + vn[n - 1];
                if (Long.compareUnsigned(rhat, un[j + n - 1]) >= 0) {
                    qhat = correctQhat(qhat, rhat, vn, un, j, n);
                }
            } else {
                qhat = divQ(u2, un[j + n - 1], vn[n - 1]);
                long rhat = un[j + n - 1] - qhat * vn[n - 1];
                qhat = correctQhat(qhat, rhat, vn, un, j, n);
            }
            long borrow = 0;
            for (int i = 0; i < n; i++) {
                long phi = Math.unsignedMultiplyHigh(qhat, vn[i]);
                long plo = qhat * vn[i];
                long plo2 = plo + borrow;
                long phi2 = phi + (Long.compareUnsigned(plo2, plo) < 0 ? 1 : 0);
                long t = un[j + i] - plo2;
                borrow = phi2 + (Long.compareUnsigned(t, un[j + i]) > 0 ? 1 : 0);
                un[j + i] = t;
            }
            long t = un[j + n] - borrow;
            final boolean finalBorrow = Long.compareUnsigned(t, un[j + n]) > 0;
            un[j + n] = t;
            if (finalBorrow) {
                // 完整乘减为负：试商减一并回加除数
                qhat--;
                long c = 0;
                for (int i = 0; i < n; i++) {
                    long sum = un[j + i] + vn[i];
                    long c1 = Long.compareUnsigned(sum, un[j + i]) < 0 ? 1 : 0;
                    long sum2 = sum + c;
                    long c2 = Long.compareUnsigned(sum2, sum) < 0 ? 1 : 0;
                    un[j + i] = sum2;
                    c = c1 + c2;
                }
                un[j + n] += c;
            }
            quotient[j] = qhat;
        }
        // 余数反规范化
        final long[] rem = new long[n];
        if (s == 0) {
            System.arraycopy(un, 0, rem, 0, n);
        } else {
            for (int i = 0; i < n - 1; i++) {
                rem[i] = (un[i] >>> s) | (un[i + 1] << (64 - s));
            }
            rem[n - 1] = un[n - 1] >>> s;
        }
        return new Division(trim(quotient), trim(rem));
    }

    /** 无符号余数。 */
    static long[] remainder(long[] numerator, long[] denominator) {
        return divide(numerator, denominator).remainder();
    }

    /** 无符号比较（有效组数内，高组优先）。 */
    private static int compareUnsigned(final long[] a, final int alen, final long[] b, final int blen) {
        for (int i = Math.max(alen, blen) - 1; i >= 0; i--) {
            int cmp = Long.compareUnsigned(word(a, i), word(b, i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * 修正高估的试商（D3）：while {@code qhat*v[n-2] > rhat*B + u[j+n-2]} 则减一。
     */
    private static long correctQhat(long qhat, long rhat, final long[] vn, final long[] un, final int j, final int n) {
        while (true) {
            final long lo = qhat * vn[n - 2];
            final long hi = Math.unsignedMultiplyHigh(qhat, vn[n - 2]);
            if (Long.compareUnsigned(hi, rhat) > 0
                    || (hi == rhat && Long.compareUnsigned(lo, un[j + n - 2]) > 0)) {
                qhat--;
                final long old = rhat;
                rhat += vn[n - 1];
                if (Long.compareUnsigned(rhat, old) < 0) {
                    break;
                }
            } else {
                break;
            }
        }
        return qhat;
    }

    /**
     * 128/64 无符号商（Hacker's Delight divlu2 风格：两个 32-bit 估计半）——
     * 要求 {@code u1 < v} 且 v 已归一化（最高位为 1），商可容纳 64 位。
     */
    private static long divQ(final long u1, final long u0, final long v) {
        final long b = 1L << 32;
        final long vn1 = v >>> 32;
        final long vn0 = v & 0xFFFFFFFFL;
        long q1 = Long.divideUnsigned(u1, vn1);
        long rhat = u1 - q1 * vn1;
        while (Long.compareUnsigned(q1, b) >= 0
                || Long.compareUnsigned(q1 * vn0, b * rhat + (u0 >>> 32)) > 0) {
            q1--;
            rhat += vn1;
            if (Long.compareUnsigned(rhat, b) >= 0) {
                break;
            }
        }
        long un21 = u1 * b + (u0 >>> 32) - q1 * v;
        long q0 = Long.divideUnsigned(un21, vn1);
        rhat = un21 - q0 * vn1;
        while (Long.compareUnsigned(q0, b) >= 0
                || Long.compareUnsigned(q0 * vn0, b * rhat + (u0 & 0xFFFFFFFFL)) > 0) {
            q0--;
            rhat += vn1;
            if (Long.compareUnsigned(rhat, b) >= 0) {
                break;
            }
        }
        return q1 * b + q0;
    }

    /** 单组除数（d < 2^64）的逐组长除法——罕见路径（除数小于 64 位），正确性优先。 */
    private static long[] divmodSingle(final long[] u, final int uz, final long d, final long[] q) {
        long rem = 0;
        for (int i = uz - 1; i >= 0; i--) {
            // (rem << 64 | u[i]) / d 是 128/64 除法——用逐位长除（64 轮）保证正确
            long cur = u[i];
            long quotient = 0;
            for (int bit = 63; bit >= 0; bit--) {
                final boolean carry = (rem >>> 63) != 0;
                rem = (rem << 1) | ((cur >>> bit) & 1L);
                if (carry || Long.compareUnsigned(rem, d) >= 0) {
                    rem -= d;
                    quotient |= 1L << bit;
                }
            }
            q[i] = quotient;
        }
        return rem == 0 ? new long[0] : new long[] {rem};
    }

    private static int divisionDistance(long[] numerator, long[] denominator) {
        int denominatorBits = bitLength(denominator);
        if (denominatorBits == 0) {
            throw new ArithmeticException("Unsigned division by zero");
        }
        return bitLength(numerator) - denominatorBits;
    }

    /** 规范化后最高除数组至少为 2^31，各轮以高两组估商，再用完整乘减恢复小于除数的余数。 */
    private static long[] divideRemainder(long[] numerator, long[] denominator, int distance, long[] quotient) {
        int denominatorBits = bitLength(denominator);
        int divisorLength = (int) ((denominatorBits + 31L) / 32);
        int dividendLength = (int) ((denominatorBits + (long) distance + 31) / 32);
        if (divisorLength == 1) {
            return divideOneDigit(numerator, denominator[0] & DIGIT_MASK, dividendLength, quotient);
        }
        int shift = Integer.numberOfLeadingZeros(digit(denominator, divisorLength - 1));
        final int[][] pool = DIGIT_POOL.get();
        final int[] divisor = pool[0];
        final int[] dividend = pool[1];
        normalizedDigitsInto(denominator, divisorLength, shift, divisor, false);
        normalizedDigitsInto(numerator, dividendLength, shift, dividend, true);
        divideNormalized(dividend, divisor, dividendLength, divisorLength, quotient, 0);
        return remainderWords(dividend, divisorLength, shift);
    }

    /** 除数最高组的 bit 31 为一，分子多一组进位；工作区独占，非空 quotient 从 offset 开始的商区须预先清零。 */
    private static void divideNormalized(int[] dividend, int[] divisor, int dividendLength, int divisorLength, long[] quotient, int offset) {
        long top = divisor[divisorLength - 1] & DIGIT_MASK;
        long second = divisor[divisorLength - 2] & DIGIT_MASK;
        for (int j = dividendLength - divisorLength; j >= 0; j--) {
            long high = dividend[j + divisorLength] & DIGIT_MASK;
            long next = dividend[j + divisorLength - 1] & DIGIT_MASK;
            long estimate, residual;
            if (high == top) {
                // 两组估商可能达到基数，先钳制到单组上界；residual 允许多出一位。
                estimate = DIGIT_MASK;
                residual = next + top;
            } else {
                long pair = (high << 32) | next;
                estimate = Long.divideUnsigned(pair, top);
                residual = pair - estimate * top;
            }
            // residual 达到基数后，右侧已超过任意两组乘积，不再构造会丢失最高位的 pair。
            long lower = dividend[j + divisorLength - 2] & DIGIT_MASK;
            while (residual < DIGIT_BASE && Long.compareUnsigned(estimate * second, (residual << 32) | lower) > 0) {
                estimate--;
                residual += top;
            }
            if (estimate == 0) {
                continue;
            }
            if (subtractProduct(dividend, j, divisor, divisorLength, estimate)) {
                // 高两组的修正未观察更低分组，完整乘减为负时还需将试商减一并回加除数。
                estimate--;
                addBack(dividend, j, divisor, divisorLength);
            }
            if (quotient != null && estimate != 0) {
                quotient[offset + (j >>> 1)] |= estimate << ((j & 1) * 32);
            }
        }
    }

    private static long[] divideOneDigit(long[] numerator, long divisor, int length, long[] quotient) {
        long remainder = 0;
        for (int i = length - 1; i >= 0; i--) {
            long pair = (remainder << 32) | (digit(numerator, i) & DIGIT_MASK);
            long current = Long.divideUnsigned(pair, divisor);
            remainder = pair - current * divisor;
            if (quotient != null && current != 0) {
                quotient[i >>> 1] |= current << ((i & 1) * 32);
            }
        }
        return remainder == 0 ? new long[0] : new long[] {remainder};
    }

    private static int digit(long[] value, int index) {
        return (int) (value[index >>> 1] >>> ((index & 1) * 32));
    }

    /** 规范化到池/调用方数组（长度不再隐含控制 carry 写入；extra 显式决定是否写进位组）。 */
    private static void normalizedDigitsInto(long[] value, int length, int shift, int[] result, boolean extra) {
        long carry = 0;
        for (int i = 0; i < length; i++) {
            long current = ((digit(value, i) & DIGIT_MASK) << shift) | carry;
            result[i] = (int) current;
            carry = current >>> 32;
        }
        if (extra) {
            result[length] = (int) carry;
        }
    }

    /** 在独占分子窗口内减去完整试商乘积，返回最高组是否发生借位。 */
    private static boolean subtractProduct(int[] dividend, int offset, int[] divisor, int divisorLength, long estimate) {
        long borrow = 0;
        for (int i = 0; i < divisorLength; i++) {
            long product = estimate * (divisor[i] & DIGIT_MASK) + borrow;
            long low = product & DIGIT_MASK, current = dividend[offset + i] & DIGIT_MASK;
            dividend[offset + i] = (int) (current - low);
            borrow = (product >>> 32) + (current < low ? 1 : 0);
        }
        long high = dividend[offset + divisorLength] & DIGIT_MASK;
        dividend[offset + divisorLength] = (int) (high - borrow);
        return high < borrow;
    }

    private static void addBack(int[] dividend, int offset, int[] divisor, int divisorLength) {
        long carry = 0;
        for (int i = 0; i < divisorLength; i++) {
            long sum = (dividend[offset + i] & DIGIT_MASK) + (divisor[i] & DIGIT_MASK) + carry;
            dividend[offset + i] = (int) sum;
            carry = sum >>> 32;
        }
        dividend[offset + divisorLength] += (int) carry;
    }

    private static long[] remainderWords(int[] dividend, int length, int shift) {
        long[] result = new long[(length + 1) / 2];
        remainderWords(dividend, length, shift, result, 0);
        return trim(result);
    }

    /** 完整覆盖 result 从 offset 到结尾的区间，包括最后半组和未使用高组；保留 offset 之前的分组。 */
    private static void remainderWords(int[] dividend, int length, int shift, long[] result, int offset) {
        for (int i = 0; i < length; i++) {
            long current = (dividend[i] & DIGIT_MASK) >>> shift;
            if (shift != 0 && i + 1 < length) {
                current |= (dividend[i + 1] & DIGIT_MASK) << (32 - shift);
            }
            if ((i & 1) == 0) {
                result[offset + (i >>> 1)] = current & DIGIT_MASK;
            } else {
                result[offset + (i >>> 1)] |= (current & DIGIT_MASK) << 32;
            }
        }
        Arrays.fill(result, offset + (length + 1) / 2, result.length, 0L);
    }

    // ═══════════ sqrt 支撑 ═══════════

    /** 输入按无符号位模式解释，返回不大于精确平方根的最大整数。 */
    static long sqrtUnsignedLong(long value) {
        long root = 0, remainder = 0;
        for (int pair = (63 - Long.numberOfLeadingZeros(value)) >> 1; pair >= 0; pair--) {
            remainder = (remainder << 2) | ((value >>> (pair * 2)) & 3);
            long trial = (root << 2) | 1;
            root <<= 1;
            if (remainder >= trial) {
                remainder -= trial;
                root |= 1;
            }
        }
        return root;
    }
    
        /** 无符号相加（低组前，结果已 trim）。 */
    static long[] add(long[] a, long[] b) {
        long[] result = new long[Math.max(a.length, b.length) + 1];
        long carry = 0;
        for (int i = 0; i < result.length; i++) {
            final long av = word(a, i), bv = word(b, i);
            final long sum = av + bv;
            final long next = sum + carry;
            carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            result[i] = next;
        }
        return trim(result);
    }

    /** 无符号右移一位（低组前）。 */
    static long[] shiftRight1(long[] value) {
        long[] result = new long[value.length];
        for (int i = 0; i < result.length - 1; i++) {
            result[i] = (value[i] >>> 1) | (value[i + 1] << 63);
        }
        result[result.length - 1] = value[value.length - 1] >>> 1;
        return trim(result);
    }

    /** 右移 count 位（count ≥ 0；低组前，trim）。 */
    static long[] shiftRight(long[] value, int count) {
        if (count == 0) {
            return Arrays.copyOf(value, value.length);
        }
        int whole = count >>> 6, bits = count & 63;
        long[] result = new long[Math.max(0, value.length - whole)];
        for (int i = 0; i < result.length; i++) {
            long low = value[i + whole] >>> bits;
            if (bits != 0 && i + whole + 1 < value.length) {
                low |= value[i + whole + 1] << (64 - bits);
            }
            result[i] = low;
        }
        return trim(result);
    }

    /** 右移 count 位取低 4 组（count ≥ 0）。 */
    static void shiftRightInto(long[] value, int count, long[] kept) {
        int whole = count >>> 6, bits = count & 63;
        for (int i = 0; i < 4; i++) {
            final long a = i + whole < value.length ? value[i + whole] >>> bits : 0L;
            final long b = bits != 0 && i + whole + 1 < value.length ? value[i + whole + 1] << (64 - bits) : 0L;
            kept[i] = a | b;
        }
    }

    /** 左移 count 位取低 4 组（count ≥ 0；调用方保证高位不丢）。 */
    static void shiftLeftInto(long[] value, int count, long[] kept) {
        int whole = count >>> 6, bits = count & 63;
        for (int i = 0; i < 4; i++) {
            final int src = i - whole;
            long v = 0L;
            if (src >= 0) {
                v = value[src] << bits;
                if (bits != 0 && src - 1 >= 0) {
                    v |= value[src - 1] >>> (64 - bits);
                }
            }
            kept[i] = v;
        }
    }

    /**
     * 输入为 127 或 128 位无符号数（低组前两 group），返回其完整 64 位整数根。
     * 高半部整数根构造的上界误差不超过 2^32，一次 Newton 更新后候选最多高一，完整平方比较只需修正一次。
     */
    static long sqrtUnsigned128(long low, long high) {
        long upper = sqrtUnsignedLong(high) + 1;
        long candidate;
        if (upper == DIGIT_BASE) {
            // 初值为 2^64，平均值仍小于 2^64，按最高位和其余位直接构造，避免初值回绕。
            candidate = Long.MIN_VALUE | (high >>> 1);
        } else {
            long quotientHigh = Long.divideUnsigned(high, upper);
            long remainder = high - quotientHigh * upper;
            long pair = (remainder << 32) | (low >>> 32);
            long quotient = (quotientHigh << 32) | Long.divideUnsigned(pair, upper);
            long initial = upper << 32;
            // 公共位与差异位分开求平均，不构造可能需要 65 位的和。
            candidate = (initial & quotient) + ((initial ^ quotient) >>> 1);
        }
        long squareLow = candidate * candidate, squareHigh = Math.unsignedMultiplyHigh(candidate, candidate);
        if (Long.compareUnsigned(squareHigh, high) > 0
                || (squareHigh == high && Long.compareUnsigned(squareLow, low) > 0)) {
            candidate--;
        }
        return candidate;
    }

    /** 不物化被开方数；shift 为正时左移，为负时右移，幅值范围外补零。 */
    private static long scaledWord(long w0, long w1, long w2, long w3, int index, int shift) {
        int group = index - (shift >> 6), bits = shift & 63;
        long low = fourWord(w0, w1, w2, w3, group) << bits;
        return bits == 0 ? low : low | (fourWord(w0, w1, w2, w3, group - 1) >>> (64 - bits));
    }

    private static long fourWord(long w0, long w1, long w2, long w3, int index) {
        return switch (index) {
            case 0 -> w0;
            case 1 -> w1;
            case 2 -> w2;
            case 3 -> w3;
            default -> 0L;
        };
    }

    // ═══════════ 平方根支撑（移植 Long2 UnsignedWords.sqrtScaled 全套） ═══════════

    private static final int SQRT_WORDS = 4;

    /**
     * 求四个低组在前的幅值分组按 shift 缩放后的精确整数根与余数（移植 Long2 {@code sqrtScaled}）。
     * 调用方保证幅值 1..237 位、缩放结果 N 为 473/474 位；workspace 独占 8 组——
     * 前 4 组为未舍入整数根 S、后 4 组为余数 R（均低组在前）；返回 R 与 S 的比较结果，
     * 满足 N = S² + R 且 0 ≤ R < 2S+1。
     */
    static int sqrtScaled(final long w0, final long w1, final long w2, final long w3, final int shift, final long[] workspace) {
        workspace[0] = 0L;
        workspace[1] = 0L;
        final long prefixLow = scaledWord(w0, w1, w2, w3, 0, shift - 346);
        final long prefixHigh = scaledWord(w0, w1, w2, w3, 1, shift - 346);
        final long prefixRoot = sqrtUnsigned128(prefixLow, prefixHigh);
        if (prefixRoot * prefixRoot == prefixLow && Math.unsignedMultiplyHigh(prefixRoot, prefixRoot) == prefixHigh) {
            // shift 至少为 236，前缀之外最多剩余 110 个幅值 bit，检查前两组即可确认整个尾部为零
            final int tailBits = 346 - shift;
            if (tailBits <= 0 || (tailBits < 64 ? (w0 & ((1L << tailBits) - 1)) == 0
                    : w0 == 0 && (tailBits == 64 || (w1 & ((1L << (tailBits - 64)) - 1)) == 0))) {
                workspace[2] = prefixRoot << 45;
                workspace[3] = prefixRoot >>> 19;
                Arrays.fill(workspace, SQRT_WORDS, workspace.length, 0L);
                return -1;
            }
        }
        final long seed = prefixRoot + 1L;
        // seed 的零位模式表示 2^64，左移 173 位后须保留初值的第 238 位
        workspace[2] = seed << 45;
        workspace[3] = seed == 0L ? 1L << 45 : seed >>> 19;
        final long residual = divideSqrtSeed64(w0, w1, w2, w3, shift, seed, workspace);
        if (oneBelow(workspace)) {
            // 初值严格大于精确根，商为 X-1 时已经是整数根
            System.arraycopy(workspace, SQRT_WORDS, workspace, 0, SQRT_WORDS);
            workspace[4] = 0L;
            workspace[5] = 0L;
            workspace[6] = residual << 45;
            workspace[7] = residual >>> 19;
            addMultipleInPlace(workspace, SQRT_WORDS, 0, 1);
            return compareSqrtBlocks(workspace);
        }
        averageInPlace(workspace);
        final int[] dividend = new int[16];
        final int[] divisor = new int[8];
        for (int iteration = 1; ; iteration++) {
            final int normalization = Integer.numberOfLeadingZeros(digit(workspace, 7));
            normalizedDigitsInto(workspace, 8, normalization, divisor, false);
            for (int i = 0; i < 8; i++) {
                final long current = scaledWord(w0, w1, w2, w3, i, shift + normalization);
                dividend[i * 2] = (int) current;
                dividend[i * 2 + 1] = (int) (current >>> 32);
            }
            Arrays.fill(workspace, SQRT_WORDS, workspace.length, 0L);
            divideNormalized(dividend, divisor, 15, 8, workspace, SQRT_WORDS);
            if (compareSqrtBlocks(workspace) >= 0) {
                // 前四组估值就是整数根，商与根之差最多为二；直接恢复 N-S²
                final int multiple = (int) (workspace[SQRT_WORDS] - workspace[0]);
                remainderWords(dividend, 8, normalization, workspace, SQRT_WORDS);
                if (multiple != 0) {
                    addMultipleInPlace(workspace, SQRT_WORDS, 0, multiple);
                }
                return compareSqrtBlocks(workspace);
            }
            if (oneBelow(workspace)) {
                // 商为 X-1 时 S = X-1 且 N-S² = S+U，直接结束相邻值循环
                for (int i = 0; i < SQRT_WORDS; i++) {
                    if (workspace[i]-- != 0L) {
                        break;
                    }
                }
                remainderWords(dividend, 8, normalization, workspace, SQRT_WORDS);
                addMultipleInPlace(workspace, SQRT_WORDS, 0, 1);
                return compareSqrtBlocks(workspace);
            }
            averageInPlace(workspace);
        }
    }

    /** seed 为 N 高 127/128 位整数根加一；返回 N>>>173 除以 seed 的无符号余数。 */
    private static long divideSqrtSeed64(final long w0, final long w1, final long w2, final long w3,
            final int shift, final long seed, final long[] workspace) {
        if ((seed & DIGIT_MASK) == 0L) {
            // 低 32 位为零时除数写成单组种子左移 205 位；余数换回 173 位缩放
            return divideSqrtSeed(w0, w1, w2, w3, shift, seed == 0L ? DIGIT_BASE : seed >>> 32, workspace) << 32;
        }
        final long top = seed >>> 32, bottom = seed & DIGIT_MASK;
        final int reducedShift = shift - 173;
        long remainder = scaledWord(w0, w1, w2, w3, 4, reducedShift);
        for (int i = SQRT_WORDS - 1; i >= 0; i--) {
            final long current = scaledWord(w0, w1, w2, w3, i, reducedShift);
            final long high = divideTwoDigitQuotient(remainder, current >>> 32, top, bottom);
            remainder = ((remainder << 32) | (current >>> 32)) - high * seed;
            final long low = divideTwoDigitQuotient(remainder, current & DIGIT_MASK, top, bottom);
            remainder = ((remainder << 32) | (current & DIGIT_MASK)) - low * seed;
            workspace[SQRT_WORDS + i] = (high << 32) | low;
        }
        return remainder;
    }

    /** 幅值不超过 237 位且缩放结果 N 为 474 位，seed 为 N 高 63/64 位整数根加一。 */
    private static long divideSqrtSeed(final long w0, final long w1, final long w2, final long w3,
            final int shift, final long seed, final long[] workspace) {
        if (seed == DIGIT_BASE) {
            // 该种子要求 N 为 474 位，除以 2^237 的余数必为零
            for (int i = 0; i < SQRT_WORDS; i++) {
                workspace[SQRT_WORDS + i] = scaledWord(w0, w1, w2, w3, i, shift - 237);
            }
            return 0L;
        }
        final int reducedShift = shift - 205;
        long remainder = scaledWord(w0, w1, w2, w3, 4, reducedShift);
        for (int i = SQRT_WORDS - 1; i >= 0; i--) {
            final long current = scaledWord(w0, w1, w2, w3, i, reducedShift);
            long pair = (remainder << 32) | (current >>> 32);
            final long high = Long.divideUnsigned(pair, seed);
            remainder = pair - high * seed;
            pair = (remainder << 32) | (current & DIGIT_MASK);
            final long low = Long.divideUnsigned(pair, seed);
            remainder = pair - low * seed;
            workspace[SQRT_WORDS + i] = (high << 32) | low;
        }
        return remainder;
    }

    /** 双组除数最高 bit 为一；返回 remainder 左移 32 位补入 digit 后的单组商。 */
    private static long divideTwoDigitQuotient(long remainder, final long digit, final long top, final long bottom) {
        long quotient;
        long residual;
        if ((remainder >>> 32) == top) {
            quotient = DIGIT_MASK;
            residual = (remainder & DIGIT_MASK) + top;
        } else {
            quotient = Long.divideUnsigned(remainder, top);
            residual = remainder - quotient * top;
        }
        while (residual < DIGIT_BASE && Long.compareUnsigned(quotient * bottom, (residual << 32) | digit) > 0) {
            quotient--;
            residual += top;
        }
        return quotient;
    }

    /** 两区间的精确和不超出四组容量，仅覆盖前四组估值，平均值向下取整。 */
    private static void averageInPlace(final long[] workspace) {
        addMultipleInPlace(workspace, 0, SQRT_WORDS, 1);
        for (int i = 0; i < SQRT_WORDS - 1; i++) {
            workspace[i] = (workspace[i] >>> 1) | (workspace[i + 1] << 63);
        }
        workspace[SQRT_WORDS - 1] >>>= 1;
    }

    /** 比较两个独立的四组无符号区间（后四组是商、前四组是估值）。 */
    private static int compareSqrtBlocks(final long[] workspace) {
        for (int i = SQRT_WORDS - 1; i >= 0; i--) {
            final int comparison = Long.compareUnsigned(workspace[SQRT_WORDS + i], workspace[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /** 后四组商加一是否恰好等于前四组估值（不修改商）。 */
    private static boolean oneBelow(final long[] workspace) {
        long carry = 1;
        for (int i = 0; i < SQRT_WORDS; i++) {
            final long next = workspace[SQRT_WORDS + i] + carry;
            if (next != workspace[i]) {
                return false;
            }
            carry = carry != 0 && next == 0 ? 1 : 0;
        }
        return true;
    }

    /** 两个四组区间互不重叠；multiple 只能为一或二，源区间只读。 */
    private static void addMultipleInPlace(final long[] workspace, final int targetOffset, final int valueOffset, final int multiple) {
        final int shift = multiple - 1;
        long carry = 0, shiftedCarry = 0;
        for (int i = 0; i < SQRT_WORDS; i++) {
            final long current = workspace[valueOffset + i];
            final long addend = (current << shift) | shiftedCarry;
            shiftedCarry = shift == 0 ? 0 : current >>> 63;
            final long sum = workspace[targetOffset + i] + addend;
            final long next = sum + carry;
            carry = Long.compareUnsigned(sum, workspace[targetOffset + i]) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            workspace[targetOffset + i] = next;
        }
    }

    /** 32-bit limb 规范化（左移使最高组 bit31 为一）。 */
}
