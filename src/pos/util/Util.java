package pos.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Util {

    public static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATUM_VRIJEME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String km(double v) {
        return String.format("%.2f", v);
    }

    public static double parseBroj(String s, String nazivPolja) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            throw new IllegalArgumentException("Neispravan broj u polju \"" + nazivPolja + "\"!");
        }
    }

    public static int parseCijeliBroj(String s, String nazivPolja) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Neispravan cijeli broj u polju \"" + nazivPolja + "\"!");
        }
    }

    public static LocalDate parseDatum(String s) {
        try {
            return LocalDate.parse(s.trim(), DATUM);
        } catch (Exception e) {
            throw new IllegalArgumentException("Neispravan format datuma! Očekivano: dd.MM.gggg (npr. 22.03.2026)");
        }
    }
}
