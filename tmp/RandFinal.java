import net.MinecraftTools.Math._256Bit.*;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class RandFinal {
    public static void main(String[] args) {
        java.util.Random rnd = new java.util.Random(1234);
        int ok = 0, total = 0;
        for (int t = 0; t < 2000; t++) {
            Int256 a = Int256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            Int256 b = Int256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            BigInteger ab = a.toBigInteger(), bb = b.toBigInteger();
            total += 6;
            if (a.add(b).equals(Int256.of(ab.add(bb)))) ok++;
            if (a.subtract(b).equals(Int256.of(ab.subtract(bb)))) ok++;
            if (a.multiply(b).equals(Int256.of(ab.multiply(bb)))) ok++;
            int n = rnd.nextInt(256);
            if (a.shiftLeft(n).equals(Int256.of(ab.shiftLeft(n)))) ok++;
            n = rnd.nextInt(256);
            if (a.shiftRight(n).equals(Int256.of(ab.shiftRight(n)))) ok++;
            if (b.signum() != 0 && a.divide(b).equals(Int256.of(ab.divide(bb)))) ok++;
        }
        System.out.println("Int256 随机交叉（正确 mod2^256 语义）: " + ok + "/" + total);
    }
}
