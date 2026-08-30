import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class BigTest {
    public static void main(String[] args) {
        BigInteger MAX = new BigInteger("57896044618658097711785492504343953926634992332820282019728792003956564819967"); // 2^255-1
        System.out.println("MAX/3 = " + MAX.divide(BigInteger.valueOf(3)));
        System.out.println("MAX*MAX >> ... check mul:");
        BigInteger a = new BigInteger("340282366920938463463374607431768211456"); // 2^128
        System.out.println("2^128*2^128 = " + a.multiply(a));
        System.out.println("2^128/2^64 = " + a.divide(new BigInteger("18446744073709551616")));
        System.out.println("neg: -100/3 = " + BigInteger.valueOf(-100).divide(BigInteger.valueOf(3)));
        System.out.println("shift: 2^128<<10 = " + a.shiftLeft(10));
        System.out.println("pow: 5^3 = " + BigInteger.valueOf(5).pow(3));
        System.out.println("cmp: 2^128 vs 2^128 = " + a.compareTo(a));
        System.out.println("bitLength(2^128) = " + a.bitLength());
    }
}
