package io.github.jmecn.fieldguideexport.export.ci;

/**
 * System properties for headless CI ({@code -Dfieldguide.*}).
 */
public final class FieldGuideExportCiProperties {

    public static final String RUN_EXPORT_AND_EXIT_PROPERTY = "fieldguide.runExportAndExit";
    public static final String EXPORT_TIMEOUT_SECONDS_PROPERTY = "fieldguide.exportTimeoutSeconds";
    public static final String EXPORT_WARMUP_TICKS_PROPERTY = "fieldguide.exportWarmupTicks";
    public static final String EXPORT_WORLD_DELAY_TICKS_PROPERTY = "fieldguide.exportWorldDelayTicks";
    public static final String EXPORT_EMI_PROPERTY = "fieldguide.exportEmi";

    private static final int DEFAULT_EXPORT_WARMUP_TICKS = 2400;
    private static final int DEFAULT_EXPORT_WORLD_DELAY_TICKS = 600;
    private static final int DEFAULT_EXPORT_TIMEOUT_SECONDS = 7200;

    private FieldGuideExportCiProperties() {}

    public static boolean runExportAndExit() {
        return Boolean.getBoolean(RUN_EXPORT_AND_EXIT_PROPERTY);
    }

    /** When true (default during CI), run guide + scoped EMI; when false, guide only. */
    public static boolean exportEmi() {
        return !"false".equalsIgnoreCase(System.getProperty(EXPORT_EMI_PROPERTY, "true").trim());
    }

    public static int exportWarmupTicks() {
        return Math.max(0, Integer.getInteger(EXPORT_WARMUP_TICKS_PROPERTY, DEFAULT_EXPORT_WARMUP_TICKS));
    }

    public static int exportWorldDelayTicks() {
        return Math.max(0, Integer.getInteger(EXPORT_WORLD_DELAY_TICKS_PROPERTY, DEFAULT_EXPORT_WORLD_DELAY_TICKS));
    }

    public static int exportTimeoutSeconds() {
        return Integer.getInteger(EXPORT_TIMEOUT_SECONDS_PROPERTY, DEFAULT_EXPORT_TIMEOUT_SECONDS);
    }

    public static boolean timedOut(long startNanos) {
        int sec = exportTimeoutSeconds();
        if (sec <= 0) {
            return false;
        }
        return (System.nanoTime() - startNanos) >= sec * 1_000_000_000L;
    }
}
