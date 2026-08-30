import net.MinecraftTools.Math._256Bit.*;
public class DivTest {
    public static void main(String[] args) {
        Int256 P64=Int256.of(0L,0L,1L,0L), P128=Int256.of(0L,1L,0L,0L);
        long t0=System.nanoTime();
        Int256 r = P128.divide(P64);
        long t1=System.nanoTime();
        System.out.println("2^128/2^64 = " + r + "  (" + (t1-t0)/1000000 + "ms)");
        long t2=System.nanoTime();
        Int256 r2 = Int256.of(0x7FFF_FFFF_FFFF_FFFFL,-1L,-1L,-2L).divide(Int256.TWO);
        long t3=System.nanoTime();
        System.out.println("(2^255-2)/2 = " + r2 + "  (" + (t3-t2)/1000000 + "ms)");
    }
}
