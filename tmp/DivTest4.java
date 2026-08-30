import net.MinecraftTools.Math._256Bit.*;
public class DivTest4 {
    public static void main(String[] args) {
        Int256 P64=Int256.of(0L,0L,1L,0L), P128=Int256.of(0L,1L,0L,0L);
        Int256 low=Int256.ZERO, high=P128;
        int iter=0;
        while (low.compareTo(high) <= 0) {
            Int256 mid = low.add(high.subtract(low).shiftRight(1));
            Int256 prod = mid.multiply(P64);
            int cmp = prod.compareTo(P128);
            if (iter % 16 == 0) System.out.println("iter="+iter+" high="+high);
            iter++;
            if (cmp == 0) { System.out.println("FOUND "+mid+" iter="+iter); return; }
            else if (cmp < 0) low = mid.add(Int256.ONE);
            else high = mid.subtract(Int256.ONE);
        }
        System.out.println("loop end iter="+iter+" high="+high);
    }
}
