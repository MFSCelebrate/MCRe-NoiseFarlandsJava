package net.minecraft.world.level.levelgen.synth;

import java.util.Locale;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

public class NoiseUtils {
    private static boolean isBedrockMode() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && ("Bedrock-Edition".equals(config.farlandsStyle));
    }

    public static double biasTowardsExtreme(final double noise, final double factor) {
        double result = noise + Math.sin(Math.PI * noise) * factor / Math.PI;
        if (isBedrockMode()) {
            return (float) result;
        }
        return result;
    }

    public static void parityNoiseOctaveConfigString(final StringBuilder sb, final double xo, final double yo, final double zo, final byte[] p) {
        sb.append(String.format(Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float) xo, (float) yo, (float) zo, p[
        0], p[255]));
    }

    public static void parityNoiseOctaveConfigString(final StringBuilder sb, final double xo, final double yo, final double zo, final int[] p) {
        sb.append(String.format(Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float) xo, (float) yo, (float) zo, p[
        0], p[255]));
    }
}