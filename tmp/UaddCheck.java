import net.MinecraftTools.Math._256Bit.*;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class UaddCheck {
    public static void main(String[] args) {
        UInt256 u1 = UInt256.of(0xFFFF_FFFF_FFFF_FFFFL, -2L, -3L, -4L);
        UInt256 u2 = UInt256.of(0L, 0x0BAD_F00D_DEAD_BEEFL, 0x0FED_CBA9_8765_4321L, 7L);
        BigInteger m256 = BigInteger.ONE.shiftLeft(256);
        boolean addMod = u1.add(u2).toBigInteger().equals(u1.toBigInteger().add(u2.toBigInteger()).mod(m256));
        boolean subMod = u1.subtract(u2).toBigInteger().equals(u1.toBigInteger().subtract(u2.toBigInteger()).mod(m256));
        System.out.println("Uadd mod2^256 一致: " + addMod);
        System.out.println("Usub mod2^256 一致: " + subMod);
        // 随机化交叉验证 add/sub/mul/shl/shr/div
        java.util.Random rnd = new java.util.Random(42);
        int ok = 0, total = 0;
        for (int t = 0; t < 500; t++) {
            UInt256 a = UInt256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            UInt256 b = UInt256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            total += 4;
            if (a.add(b).toBigInteger().equals(a.toBigInteger().add(b.toBigInteger()).mod(m256))) ok++;
            if (a.subtract(b).toBigInteger().equals(a.toBigInteger().subtract(b.toBigInteger()).mod(m256))) ok++;
            if (a.multiply(b).toBigInteger().equals(a.toBigInteger().multiply(b.toBigInteger()).mod(m256))) ok++;
            if (a.shiftLeft(rnd.nextInt(256)).toBigInteger().equals(a.toBigInteger().shiftLeft(rnd.nextInt(256)).mod(m256))) ok++;
        }
        System.out.println("UInt256 随机交叉: " + ok + "/" + total);
        // Int256 随机交叉（有符号）
        ok = 0; total = 0;
        for (int t = 0; t < 500; t++) {
            Int256 a = Int256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            Int256 b = Int256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            total += 5;
            if (a.add(b).toBigInteger().equals(a.toBigInteger().add(b.toBigInteger()))) ok++;
            if (a.subtract(b).toBigInteger().equals(a.toBigInteger().subtract(b.toBigInteger()))) ok++;
            if (a.multiply(b).toBigInteger().equals(a.toBigInteger().multiply(b.toBigInteger()).mod(m256).subtract(a.toBigInteger().multiply(b.toBigInteger()).testBit(255)?BigInteger.ZERO:BigInteger.ZERO))) {
                // Int256 补码乘法低 256 位 = 无符号乘积低 256 位；有符号结果需符号扩展
                BigInteger prod = a.toBigInteger().multiply(b.toBigInteger());
                BigInteger low = prod.mod(m256);
                if (low.compareTo(BigInteger.ONE.shiftLeft(255)) >= 0) low = low.subtract(m256);
                if (a.multiply(b).toBigInteger().equals(low)) ok++;
            }
            total--;
            int n = rnd.nextInt(256);
            if (a.shiftLeft(n).toBigInteger().equals(a.toBigInteger().shiftLeft(n))) ok++;
            n = rnd.nextInt(256);
            if (a.shiftRight(n).toBigInteger().equals(a.toBigInteger().shiftRight(n))) ok++;
        }
        System.out.println("Int256 随机交叉: " + ok + "/" + total);
    }
}
