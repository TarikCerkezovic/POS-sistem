package pos.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// univerzalni PDF izvjestaj: naslov + opis filtera + tabela (vise strana) + opcioni grafikoni
public class PdfIzvjestaj {

    private static final String FOLDER = "izvjestaji";
    private static final float MARGINA = 36f;
    private static final float FONT_NASLOV = 14f;
    private static final float FONT_OPIS = 8.5f;
    private static final float FONT_TABELA = 7.5f;
    private static final float VISINA_REDA = 13f;
    private static final Color BOJA_ZAGLAVLJA = new Color(232, 236, 240);
    private static final Color BOJA_LINIJE = new Color(200, 205, 210);
    private static final Color BOJA_PRUGE = new Color(246, 248, 250);

    private final PDDocument dokument;
    private final PDFont obican;
    private final PDFont podebljan;
    private final float sirinaStrane;
    private final float visinaStrane;

    private PDPage strana;
    private PDPageContentStream tok;
    private float y;

    private PdfIzvjestaj(PDDocument dokument, boolean polozeno) throws IOException {
        this.dokument = dokument;
        this.obican = ucitajFont(dokument, "DejaVuSansMono.ttf");
        this.podebljan = ucitajFont(dokument, "DejaVuSansMono-Bold.ttf");
        if (polozeno) {
            sirinaStrane = PDRectangle.A4.getHeight();
            visinaStrane = PDRectangle.A4.getWidth();
        } else {
            sirinaStrane = PDRectangle.A4.getWidth();
            visinaStrane = PDRectangle.A4.getHeight();
        }
    }

    public static File izvezi(String naslov, String opis, String[] kolone,
                              List<Object[]> redovi) throws IOException {
        return izvezi(naslov, opis, kolone, redovi, null);
    }

    // opis moze imati vise linija odvojenih sa \n; grafikoni (ako ih ima) idu prije tabele
    public static File izvezi(String naslov, String opis, String[] kolone,
                              List<Object[]> redovi, List<BufferedImage> grafikoni) throws IOException {
        File folder = new File(FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Nije moguće kreirati folder \"" + FOLDER + "\"!");
        }
        String vrijeme = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File fajl = new File(folder, imeFajla(naslov) + "_" + vrijeme + ".pdf");

        boolean polozeno = kolone != null && kolone.length > 6;
        try (PDDocument dokument = new PDDocument()) {
            PdfIzvjestaj izvjestaj = new PdfIzvjestaj(dokument, polozeno);
            izvjestaj.napisi(naslov, opis, kolone, redovi, grafikoni);
            izvjestaj.upisiBrojeveStrana();
            dokument.save(fajl);
        }
        return fajl;
    }

    private void napisi(String naslov, String opis, String[] kolone,
                        List<Object[]> redovi, List<BufferedImage> grafikoni) throws IOException {
        novaStrana();

        // naslov i opis
        pisi(podebljan, FONT_NASLOV, MARGINA, y, naslov);
        y = y - FONT_NASLOV - 6f;
        String generisano = "Generisano: " + LocalDateTime.now().format(Util.DATUM_VRIJEME);
        pisi(obican, FONT_OPIS, MARGINA, y, generisano);
        y = y - FONT_OPIS - 3f;
        if (opis != null && !opis.isEmpty()) {
            for (String linija : opis.split("\n")) {
                pisi(obican, FONT_OPIS, MARGINA, y, linija);
                y = y - FONT_OPIS - 3f;
            }
        }
        y = y - 8f;

        if (grafikoni != null) {
            for (BufferedImage slika : grafikoni) {
                nacrtajGrafikon(slika);
            }
            y = y - 8f;
        }

        if (kolone != null && kolone.length > 0) {
            nacrtajTabelu(kolone, redovi);
        }
        tok.close();
    }

    private void nacrtajGrafikon(BufferedImage slika) throws IOException {
        float dostupno = sirinaStrane - 2 * MARGINA;
        float omjer = dostupno / slika.getWidth();
        if (omjer > 1f) {
            omjer = 1f;
        }
        float sirina = slika.getWidth() * omjer;
        float visina = slika.getHeight() * omjer;
        if (y - visina < MARGINA + VISINA_REDA) {
            tok.close();
            novaStrana();
        }
        PDImageXObject objekat = LosslessFactory.createFromImage(dokument, slika);
        tok.drawImage(objekat, MARGINA + (dostupno - sirina) / 2f, y - visina, sirina, visina);
        y = y - visina - 10f;
    }

    private void nacrtajTabelu(String[] kolone, List<Object[]> redovi) throws IOException {
        float[] sirine = izracunajSirine(kolone, redovi);
        boolean[] desno = numerickeKolone(kolone.length, redovi);

        nacrtajZaglavljeTabele(kolone, sirine);
        int naStrani = 0;
        for (Object[] red : redovi) {
            if (y - VISINA_REDA < MARGINA + VISINA_REDA) {
                tok.close();
                novaStrana();
                nacrtajZaglavljeTabele(kolone, sirine);
                naStrani = 0;
            }
            if (naStrani % 2 == 1) {
                tok.setNonStrokingColor(BOJA_PRUGE);
                tok.addRect(MARGINA, y - VISINA_REDA, ukupno(sirine), VISINA_REDA);
                tok.fill();
                tok.setNonStrokingColor(Color.BLACK);
            }
            float x = MARGINA;
            for (int i = 0; i < kolone.length; i++) {
                String tekst = "";
                if (i < red.length && red[i] != null) {
                    tekst = String.valueOf(red[i]);
                }
                tekst = skrati(tekst, sirine[i] - 4f, obican, FONT_TABELA);
                float tx = x + 2f;
                if (desno[i]) {
                    tx = x + sirine[i] - 2f - sirinaTeksta(tekst, obican, FONT_TABELA);
                }
                pisi(obican, FONT_TABELA, tx, y - VISINA_REDA + 3.5f, tekst);
                x = x + sirine[i];
            }
            y = y - VISINA_REDA;
            naStrani++;
        }
        // donja linija tabele
        tok.setStrokingColor(BOJA_LINIJE);
        tok.moveTo(MARGINA, y);
        tok.lineTo(MARGINA + ukupno(sirine), y);
        tok.stroke();
        tok.setStrokingColor(Color.BLACK);
        y = y - VISINA_REDA;
        pisi(obican, FONT_OPIS, MARGINA, y, "Ukupno redova: " + redovi.size());
    }

    private void nacrtajZaglavljeTabele(String[] kolone, float[] sirine) throws IOException {
        tok.setNonStrokingColor(BOJA_ZAGLAVLJA);
        tok.addRect(MARGINA, y - VISINA_REDA, ukupno(sirine), VISINA_REDA);
        tok.fill();
        tok.setNonStrokingColor(Color.BLACK);
        float x = MARGINA;
        for (int i = 0; i < kolone.length; i++) {
            String tekst = skrati(kolone[i], sirine[i] - 4f, podebljan, FONT_TABELA);
            pisi(podebljan, FONT_TABELA, x + 2f, y - VISINA_REDA + 3.5f, tekst);
            x = x + sirine[i];
        }
        y = y - VISINA_REDA;
    }

    // sirine kolona po najsirem sadrzaju, skalirane da stanu na stranu
    private float[] izracunajSirine(String[] kolone, List<Object[]> redovi) throws IOException {
        float[] sirine = new float[kolone.length];
        for (int i = 0; i < kolone.length; i++) {
            sirine[i] = sirinaTeksta(kolone[i], podebljan, FONT_TABELA) + 8f;
        }
        int uzorak = 0;
        for (Object[] red : redovi) {
            for (int i = 0; i < kolone.length && i < red.length; i++) {
                if (red[i] == null) {
                    continue;
                }
                float w = sirinaTeksta(String.valueOf(red[i]), obican, FONT_TABELA) + 8f;
                if (w > sirine[i]) {
                    sirine[i] = w;
                }
            }
            uzorak++;
            if (uzorak >= 500) {
                break;
            }
        }
        float dostupno = sirinaStrane - 2 * MARGINA;
        float maksimalno = dostupno * 0.45f;
        for (int i = 0; i < sirine.length; i++) {
            if (sirine[i] > maksimalno) {
                sirine[i] = maksimalno;
            }
        }
        float suma = ukupno(sirine);
        if (suma > dostupno) {
            for (int i = 0; i < sirine.length; i++) {
                sirine[i] = sirine[i] * dostupno / suma;
            }
        }
        return sirine;
    }

    // kolona je numericka ako su sve popunjene vrijednosti brojevi (poravnanje udesno)
    private boolean[] numerickeKolone(int brojKolona, List<Object[]> redovi) {
        boolean[] desno = new boolean[brojKolona];
        for (int i = 0; i < brojKolona; i++) {
            boolean imaVrijednosti = false;
            boolean sveBrojevi = true;
            int uzorak = 0;
            for (Object[] red : redovi) {
                if (i >= red.length || red[i] == null || String.valueOf(red[i]).isEmpty()) {
                    continue;
                }
                imaVrijednosti = true;
                if (!(red[i] instanceof Number)) {
                    try {
                        Double.parseDouble(String.valueOf(red[i]).replace(',', '.'));
                    } catch (Exception e) {
                        sveBrojevi = false;
                        break;
                    }
                }
                uzorak++;
                if (uzorak >= 200) {
                    break;
                }
            }
            desno[i] = imaVrijednosti && sveBrojevi;
        }
        return desno;
    }

    private void novaStrana() throws IOException {
        strana = new PDPage(new PDRectangle(sirinaStrane, visinaStrane));
        dokument.addPage(strana);
        tok = new PDPageContentStream(dokument, strana);
        y = visinaStrane - MARGINA;
    }

    // broj strane se upisuje naknadno, kad se zna koliko ih ukupno ima
    private void upisiBrojeveStrana() throws IOException {
        int ukupnoStrana = dokument.getNumberOfPages();
        for (int i = 0; i < ukupnoStrana; i++) {
            PDPage p = dokument.getPage(i);
            try (PDPageContentStream podnozje = new PDPageContentStream(
                    dokument, p, PDPageContentStream.AppendMode.APPEND, true, true)) {
                String tekst = "Strana " + (i + 1) + " od " + ukupnoStrana;
                float sirina = sirinaTeksta(tekst, obican, FONT_OPIS);
                podnozje.beginText();
                podnozje.setFont(obican, FONT_OPIS);
                podnozje.newLineAtOffset((sirinaStrane - sirina) / 2f, MARGINA / 2f);
                podnozje.showText(tekst);
                podnozje.endText();
            }
        }
    }

    private void pisi(PDFont font, float velicina, float x, float pozicijaY, String tekst) throws IOException {
        tok.beginText();
        tok.setFont(font, velicina);
        tok.newLineAtOffset(x, pozicijaY);
        tok.showText(ocisti(tekst));
        tok.endText();
    }

    private float sirinaTeksta(String tekst, PDFont font, float velicina) throws IOException {
        return font.getStringWidth(ocisti(tekst)) / 1000f * velicina;
    }

    private String skrati(String tekst, float maksSirina, PDFont font, float velicina) throws IOException {
        if (sirinaTeksta(tekst, font, velicina) <= maksSirina) {
            return tekst;
        }
        String skraceno = tekst;
        while (skraceno.length() > 1
                && sirinaTeksta(skraceno + "…", font, velicina) > maksSirina) {
            skraceno = skraceno.substring(0, skraceno.length() - 1);
        }
        return skraceno + "…";
    }

    // kontrolni znakovi rusili bi showText, a zamjenski znak ide umjesto glifa kojeg font nema
    private String ocisti(String tekst) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tekst.length(); i++) {
            char znak = tekst.charAt(i);
            if (znak == '\n' || znak == '\r' || znak == '\t') {
                sb.append(' ');
            } else if (znak < 32) {
                sb.append('?');
            } else {
                sb.append(znak);
            }
        }
        String rezultat = sb.toString();
        try {
            obican.encode(rezultat);
            return rezultat;
        } catch (Exception e) {
            // zamijeni znakove koje font ne poznaje
            StringBuilder sigurno = new StringBuilder();
            for (int i = 0; i < rezultat.length(); i++) {
                String znak = String.valueOf(rezultat.charAt(i));
                try {
                    obican.encode(znak);
                    sigurno.append(znak);
                } catch (Exception nema) {
                    sigurno.append('?');
                }
            }
            return sigurno.toString();
        }
    }

    private static float ukupno(float[] sirine) {
        float suma = 0;
        for (float s : sirine) {
            suma = suma + s;
        }
        return suma;
    }

    private static String imeFajla(String naslov) {
        StringBuilder sb = new StringBuilder();
        for (char znak : naslov.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(znak)) {
                sb.append(znak);
            } else if (znak == ' ' || znak == '-' || znak == '_') {
                sb.append('_');
            }
        }
        if (sb.length() == 0) {
            sb.append("izvjestaj");
        }
        if (sb.length() > 40) {
            sb.setLength(40);
        }
        return sb.toString();
    }

    private static PDType0Font ucitajFont(PDDocument dokument, String nazivFajla) throws IOException {
        File f = new File("fonts", nazivFajla);
        if (!f.exists()) {
            f = new File("/usr/share/fonts/truetype/dejavu/" + nazivFajla);
        }
        if (!f.exists()) {
            throw new IOException("Font \"" + nazivFajla + "\" nije pronađen - folder fonts/ mora biti uz aplikaciju!");
        }
        return PDType0Font.load(dokument, f);
    }
}
