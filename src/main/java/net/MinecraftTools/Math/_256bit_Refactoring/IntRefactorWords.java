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

    /** 无符号除法（商 + 余数）。 */
    static Division divide(long[] numerator, long[] denominator) {
        int distance = divisionDistance(numerator, denominator);
        if (distance < 0) {
            return new Division(new long[0], numerator);
        }
        if (distance == 0) {
            int comparison = compare(numerator, denominator);
            if (comparison < 0) {
                return new Division(new long[0], numerator);
            }
            if (comparison == 0) {
                return new Division(new long[] {1}, new long[0]);
            }
        }
        long[] quotient = new long[distance / 64 + 1];
        long[] remainder = divideRemainder(numerator, denominator, distance, quotient);
        return new Division(trim(quotient), remainder);
    }

    /** 无符号余数。 */
    static long[] remainder(long[] numerator, long[] denominator) {
        int distance = divisionDistance(numerator, denominator);
        if (distance < 0) {
            return numerator;
        }
        if (distance == 0) {
            int comparison = compare(numerator, denominator);
            if (comparison < 0) {
                return numerator;
            }
            if (comparison == 0) {
                return new long[0];
            }
        }
        return divideRemainder(numerator, denominator, distance, null);
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
}
