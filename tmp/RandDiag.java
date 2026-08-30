import net.MinecraftTools.Math._256Bit.*;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class RandDiag {
    public static void main(String[] args) {
        java.util.Random rnd = new java.util.Random(42);
        BigInteger m256 = BigInteger.ONE.shiftLeft(256);
        int[] fail = new int[6]; // 0=Uadd 1=Usub 2=Umul 3=Ushl 4=Iadd 5=Ishl
        int[] cnt = new int[6];
        for (int t = 0; t < 1000; t++) {
            UInt256 a = UInt256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            UInt256 b = UInt256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            cnt[0]++; if (!a.add(b).toBigInteger().equals(a.toBigInteger().add(b.toBigInteger()).mod(m256))) fail[0]++;
            cnt[1]++; if (!a.subtract(b).toBigInteger().equals(a.toBigInteger().subtract(b.toBigInteger()).mod(m256))) fail[1]++;
            cnt[2]++; if (!a.multiply(b).toBigInteger().equals(a.toBigInteger().multiply(b.toBigInteger()).mod(m256))) fail[2]++;
            int n = rnd.nextInt(256);
            cnt[3]++; if (!a.shiftLeft(n).toBigInteger().equals(a.toBigInteger().shiftLeft(n).mod(m256))) fail[3]++;
        }
        for (int t = 0; t < 1000; t++) {
            Int256 a = Int256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            Int256 b = Int256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            cnt[4]++; if (!a.add(b).toBigInteger().equals(a.toBigInteger().add(b.toBigInteger()))) fail[4]++;
            int n = rnd.nextInt(256);
            cnt[5]++; if (!a.shiftLeft(n).toBigInteger().equals(a.toBigInteger().shiftLeft(n))) fail[5]++;
        }
        String[] name = {"Uadd","Usub","Umul","Ushl","Iadd","Ishl"};
        for (int i = 0; i < 6; i++) System.out.println(name[i] + " fail " + fail[i] + "/" + cnt[i]);
    }
}
