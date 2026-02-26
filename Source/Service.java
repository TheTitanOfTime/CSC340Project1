package Source;

public enum Service {
    N_BODY_GRAVITATIONAL_STEPPER,
    YARA_LITE_PATTERN_SCANNER,
    COMPRESSION_DECOMPRESSION,
    CSV_STATS,
    TBD;

    public static Service fromString(String s) {
        if (s == null) return null;
        try { return Service.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
