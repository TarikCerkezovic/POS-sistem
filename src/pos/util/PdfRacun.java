package pos.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import pos.model.Racun;
import pos.model.StavkaRacuna;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfRacun {

    private static final String FOLDER = "racuni";
    private static final int SIRINA_ZNAKOVA = 42;
    private static final float SIRINA_STRANE = 227f;
    private static final float MARGINA = 11f;
    private static final float VELICINA_FONTA = 8f;
    private static final float PRORED = 9.6f;

    private static class Linija {
        String tekst;
        boolean bold;

        Linija(String tekst, boolean bold) {
            this.tekst = tekst;
            this.bold = bold;
        }
    }

    public static File stampaj(Racun r) throws IOException {
        File folder = new File(FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Nije moguće kreirati folder \"" + FOLDER + "\"!");
        }
        File fajl = new File(folder, "racun_" + r.getBroj() + ".pdf");

        List<Linija> linije = linijeRacuna(r);
        float visina = 2 * MARGINA + linije.size() * PRORED;

        try (PDDocument dokument = new PDDocument()) {
            PDPage strana = new PDPage(new PDRectangle(SIRINA_STRANE, visina));
            dokument.addPage(strana);

            PDFont obican = ucitajFont(dokument, "DejaVuSansMono.ttf");
            PDFont podebljan = ucitajFont(dokument, "DejaVuSansMono-Bold.ttf");

            try (PDPageContentStream tok = new PDPageContentStream(dokument, strana)) {
                tok.beginText();
                tok.setLeading(PRORED);
                tok.newLineAtOffset(MARGINA, visina - MARGINA - VELICINA_FONTA);
                for (Linija l : linije) {
                    if (l.bold) {
                        tok.setFont(podebljan, VELICINA_FONTA);
                    } else {
                        tok.setFont(obican, VELICINA_FONTA);
                    }
                    tok.showText(l.tekst);
                    tok.newLine();
                }
                tok.endText();
            }
            dokument.save(fajl);
        }
        return fajl;
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

    private static List<Linija> linijeRacuna(Racun r) {
        List<Linija> l = new ArrayList<>();
        l.add(new Linija(centar("MARKET \"FET\" TUZLA"), true));
        l.add(new Linija(centar("Univerzitetska br. 1, 75000 Tuzla"), false));
        l.add(new Linija(centar("ID broj: 4209876543210"), false));
        l.add(new Linija(crta(), false));
        String redBrojRacuna = "Račun broj: " + r.getBroj();
        if (r.isStorniran()) {
            redBrojRacuna = redBrojRacuna + "  (STORNIRAN)";
        }
        l.add(new Linija(redBrojRacuna, false));
        l.add(new Linija("Datum:      " + r.getVrijeme().format(Util.DATUM_VRIJEME), false));
        l.add(new Linija("Prodavač:   " + r.getProdavac(), false));
        l.add(new Linija(crta(), false));
        l.add(new Linija(par("Artikal / kol. x cijena", "Iznos"), false));
        l.add(new Linija(crta(), false));
        for (StavkaRacuna s : r.getStavke()) {
            l.add(new Linija(skrati(s.getSifraArtikla() + " " + s.getNazivArtikla()), false));
            String lijevo = "  " + s.getKolicina() + " x " + Util.km(s.getCijena());
            if (s.getPopustProcenat() > 0) {
                lijevo = lijevo + " (popust " + String.format("%.0f", s.getPopustProcenat()) + "%)";
            }
            l.add(new Linija(par(lijevo, Util.km(s.iznos())), false));
        }
        l.add(new Linija(crta(), false));
        l.add(new Linija(par("UKUPNO ZA NAPLATU (KM):", Util.km(r.ukupno())), true));
        l.add(new Linija(par("Osnovica:", Util.km(r.osnovica())), false));
        l.add(new Linija(par("PDV (17%):", Util.km(r.pdv())), false));
        l.add(new Linija(crta(), false));
        l.add(new Linija("Način plaćanja: " + r.getNacinPlacanja(), false));
        if ("GOTOVINA".equals(r.getNacinPlacanja())) {
            l.add(new Linija(par("Predato:", Util.km(r.getPredato())), false));
            l.add(new Linija(par("Povrat:", Util.km(r.getPovratNovca())), false));
        }
        l.add(new Linija(crta(), false));
        l.add(new Linija(centar("HVALA NA POVJERENJU!"), false));
        l.add(new Linija(centar("Račun je generisan POS sistemom"), false));
        return l;
    }

    private static String crta() {
        return "-".repeat(SIRINA_ZNAKOVA);
    }

    private static String centar(String s) {
        if (s.length() >= SIRINA_ZNAKOVA) {
            return s;
        }
        int lijevo = (SIRINA_ZNAKOVA - s.length()) / 2;
        return " ".repeat(lijevo) + s;
    }

    private static String par(String lijevo, String desno) {
        int razmak = SIRINA_ZNAKOVA - lijevo.length() - desno.length();
        if (razmak < 1) {
            razmak = 1;
        }
        return lijevo + " ".repeat(razmak) + desno;
    }

    private static String skrati(String s) {
        if (s.length() <= SIRINA_ZNAKOVA) {
            return s;
        }
        return s.substring(0, SIRINA_ZNAKOVA - 1) + ".";
    }
}
