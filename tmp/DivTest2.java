import net.MinecraftTools.Math._256Bit.*;
public class DivTest2 {
    public static void main(String[] args) {
        Int256 P64=Int256.of(0L,0L,1L,0L);
        // 小组合逐个测
        System.out.println("2^64 / 2 = " + P64.divide(Int256.TWO));
        System.out.println("2^64 / 3 = " + P64.divide(Int256.of(3)));
        System.out.println("2^64 / 2^64 = " + P64.divide(P64));
        System.out.println("done small");
    }
}
