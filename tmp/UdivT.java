import net.MinecraftTools.Math._256Bit.*;
public class UdivT {
    public static void main(String[] args) {
        long t0=System.nanoTime();
        UInt256 r = UInt256.MAX_VALUE.divide(UInt256.of(3));
        System.out.println("MAX/3 = " + r + " (" + (System.nanoTime()-t0)/1000000 + "ms)");
        long t1=System.nanoTime();
        UInt256 r2 = UInt256.MAX_VALUE.divide(UInt256.TWO);
        System.out.println("MAX/2 = " + r2 + " (" + (System.nanoTime()-t1)/1000000 + "ms)");
    }
}
