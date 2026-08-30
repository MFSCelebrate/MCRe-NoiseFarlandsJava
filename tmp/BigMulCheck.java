import net.MinecraftTools.Math._256Bit.*;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class BigMulCheck {
    public static void main(String[] args) {
        Int256 x1 = Int256.of(0L, 0xDEAD_BEEF_CAFE_F00DL, 0x1234_5678_9ABC_DEF0L, -5L);
        Int256 x2 = Int256.of(0L, 0x0BAD_F00D_DEAD_BEEFL, 0x0FED_CBA9_8765_4321L, 7L);
        Int256 got = x1.multiply(x2);
        // BigInteger 完整乘积的低 256 位
        BigInteger full = x1.toBigInteger().multiply(x2.toBigInteger());
        byte[] fullBytes = full.toByteArray();
        byte[] low = new byte[32];
        System.arraycopy(fullBytes, Math.max(0, fullBytes.length-32), low, Math.max(0, 32-fullBytes.length), Math.min(32, fullBytes.length));
        Int256 expected = Int256.of(low);
        System.out.println("got      = " + got);
        System.out.println("low256   = " + expected);
        System.out.println(got.equals(expected) ? "PASS: 低256位一致 (补码乘法语义正确)" : "FAIL");
        // 加法/减法 vs BigInteger 全等
        System.out.println("add 一致: " + x1.add(x2).toBigInteger().equals(x1.toBigInteger().add(x2.toBigInteger())));
        System.out.println("sub 一致: " + x1.subtract(x2).toBigInteger().equals(x1.toBigInteger().subtract(x2.toBigInteger())));
        System.out.println("shl 一致: " + x1.shiftLeft(37).toBigInteger().equals(x1.toBigInteger().shiftLeft(37)));
        System.out.println("shr 一致: " + x1.shiftRight(37).toBigInteger().equals(x1.toBigInteger().shiftRight(37)));
        System.out.println("div 一致: " + x1.divide(x2).toBigInteger().equals(x1.toBigInteger().divide(x2.toBigInteger())));
        // UInt256 vs BigInteger（无符号）
        UInt256 u1 = UInt256.of(0xFFFF_FFFF_FFFF_FFFFL, -2L, -3L, -4L);
        UInt256 u2 = UInt256.of(0L, 0x0BAD_F00D_DEAD_BEEFL, 0x0FED_CBA9_8765_4321L, 7L);
        System.out.println("Uadd 一致: " + u1.add(u2).toBigInteger().equals(u1.toBigInteger().add(u2.toBigInteger())));
        System.out.println("Umul low256 一致: " + u1.multiply(u2).toBigInteger().equals(u1.toBigInteger().multiply(u2.toBigInteger()).mod(BigInteger.ONE.shiftLeft(256))));
        System.out.println("Udiv 一致: " + u1.divide(u2).toBigInteger().equals(u1.toBigInteger().divide(u2.toBigInteger())));
        System.out.println("Ushr 一致: " + u1.shiftRight(40).toBigInteger().equals(u1.toBigInteger().shiftRight(40)));
    }
}
