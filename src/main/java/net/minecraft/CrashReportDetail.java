package net.minecraft;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.Callable;

public interface CrashReportDetail<V> extends Callable<V> {
}