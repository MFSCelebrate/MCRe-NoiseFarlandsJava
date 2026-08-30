import net.MinecraftTools.Math._256Bit.*;
public class DivTest5 {
    public static void main(String[] args) {
        Int256 P64=Int256.of(0L,0L,1L,0L), P128=Int256.of(0L,1L,0L,0L);
        Int256 low=Int256.ZERO, high=P128;
        int iter=0;
        while (low.compareTo(high) <= 0 && iter < 70) {
            Int256 mid = low.add(high.subtract(low).shiftRight(1));
            Int256 prod = mid.multiply(P64);
            int cmp = prod.compareTo(P128);
            System.out.println("i="+iter+" low="+low+" high="+high+" mid="+mid+" cmp="+cmp);
            iter++;
            if (cmp == 0) { System.out.println("FOUND "+mid); return; }
            else if (cmp < 0) low = mid.add(Int256.ONE);
            else high = mid.subtract(Int256.ONE);
        }
    }
}
