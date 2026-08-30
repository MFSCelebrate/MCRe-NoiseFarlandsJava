import net.MinecraftTools.Math._256Bit.*;
public class DivTest6 {
    public static void main(String[] args) {
        Int256 low = Int256.of(0L,0L,1L,0L);          // 2^64
        Int256 high = Int256.of(0L,0L,1L,0xFFFF_FFFF_FFFF_FFFEL); // 2^65-2
        Int256 diff = high.subtract(low);
        System.out.println("diff = " + diff + " (exp 2^64-2=18446744073709551614)");
        Int256 half = diff.shiftRight(1);
        System.out.println("diff>>1 = " + half + " (exp 2^63-1=9223372036854775807)");
        Int256 mid = low.add(half);
        System.out.println("mid = " + mid + " (exp 2^64+2^63-1=27670116110564327423)");
        // 单独验证 shiftRight 对 (0,0,0,0xFFFFFFFFFFFE)
        Int256 x = Int256.of(0L,0L,0L,0xFFFF_FFFF_FFFF_FFFEL);
        System.out.println("(0,0,0,0xFFFFFFFE)>>1 = " + x.shiftRight(1));
        // 验证 -1>>1
        System.out.println("(-1)>>1 = " + Int256.MINUS_ONE.shiftRight(1));
        System.out.println("fill check: -1>>1 should be -1");
    }
}
