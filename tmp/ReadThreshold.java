import java.lang.reflect.Field;
public class ReadThreshold {
    public static void main(String[] a) throws Exception {
        Class<?> c = java.math.BigInteger.class;
        for (String f : new String[]{"KARATSUBA_THRESHOLD","TOOM_COOK_THRESHOLD","KARATSUBA_SQUARE_THRESHOLD","TOOM_COOK_SQUARE_THRESHOLD","MULTIPLY_SQUARE_THRESHOLD","MONTGOMERY_INTRINSIC_THRESHOLD","BURNIKEL_ZIEGLER_THRESHOLD","BURNIKEL_ZIEGLER_OFFSET"}) {
            try {
                Field fd = c.getDeclaredField(f);
                fd.setAccessible(true);
                System.out.println(f + " = " + fd.getInt(null));
            } catch (Throwable e) { System.out.println(f + " = <err>"); }
        }
    }
}
