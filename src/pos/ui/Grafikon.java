package pos.ui;

import pos.util.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// jednostavni grafikoni (linije, kolone, vodoravne trake, slozena traka) crtani u Java2D.
// boje i pravila crtanja prate provjerenu paletu: kategorije se dodjeljuju fiksnim
// redoslijedom, jedna velicina = jedna nijansa, tekst nikad ne nosi boju serije.
public class Grafikon extends JPanel {

    // kategorijske boje, fiksni redoslijed (svijetla podloga)
    public static final Color[] SERIJE = {
        new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
        new Color(0xe87ba4), new Color(0x008300), new Color(0x4a3aa7), new Color(0xe34948)
    };
    private static final Color PODLOGA = new Color(0xfcfcfb);
    private static final Color MREZA = new Color(0xe1e0d9);
    private static final Color OSA = new Color(0xc3c2b7);
    private static final Color TINTA = new Color(0x0b0b0b);
    private static final Color TINTA_SPOREDNA = new Color(0x52514e);
    private static final Color TINTA_BLIJEDA = new Color(0x898781);

    public static class Serija {
        public final String naziv;
        public final double[] vrijednosti;

        public Serija(String naziv, double[] vrijednosti) {
            this.naziv = naziv;
            this.vrijednosti = vrijednosti;
        }
    }

    private static final int TIP_LINIJE = 0;
    private static final int TIP_KOLONE = 1;
    private static final int TIP_TRAKE = 2;
    private static final int TIP_SLOZENA = 3;

    private final int tip;
    private final String naslov;
    private final List<String> oznake;
    private final List<Serija> serije;

    // hit-zone za tooltip, popunjavaju se pri svakom crtanju
    private final List<Rectangle> zone = new ArrayList<>();
    private final List<String> opisiZona = new ArrayList<>();

    private Grafikon(int tip, String naslov, List<String> oznake, List<Serija> serije) {
        this.tip = tip;
        this.naslov = naslov;
        this.oznake = oznake;
        this.serije = serije;
        setOpaque(true);
        setBackground(PODLOGA);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public static Grafikon linije(String naslov, List<String> oznakeX, List<Serija> serije) {
        return new Grafikon(TIP_LINIJE, naslov, oznakeX, serije);
    }

    public static Grafikon kolone(String naslov, List<String> oznakeX, double[] vrijednosti) {
        List<Serija> lista = new ArrayList<>();
        lista.add(new Serija("", vrijednosti));
        return new Grafikon(TIP_KOLONE, naslov, oznakeX, lista);
    }

    public static Grafikon trakasti(String naslov, List<String> kategorije, double[] vrijednosti) {
        List<Serija> lista = new ArrayList<>();
        lista.add(new Serija("", vrijednosti));
        return new Grafikon(TIP_TRAKE, naslov, kategorije, lista);
    }

    // jedna vodoravna traka podijeljena na segmente (udio u cjelini)
    public static Grafikon slozenaTraka(String naslov, List<String> kategorije, double[] vrijednosti) {
        List<Serija> lista = new ArrayList<>();
        lista.add(new Serija("", vrijednosti));
        return new Grafikon(TIP_SLOZENA, naslov, kategorije, lista);
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        for (int i = zone.size() - 1; i >= 0; i--) {
            if (zone.get(i).contains(e.getPoint())) {
                return opisiZona.get(i);
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        nacrtaj((Graphics2D) g.create(), getWidth(), getHeight(), true);
    }

    // slika za PDF izvjestaj (crta se u dvostrukoj rezoluciji radi ostrine)
    public BufferedImage slika(int sirina, int visina) {
        BufferedImage img = new BufferedImage(sirina * 2, visina * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(2, 2);
        g.setColor(PODLOGA);
        g.fillRect(0, 0, sirina, visina);
        nacrtaj(g, sirina, visina, false);
        g.dispose();
        return img;
    }

    private void nacrtaj(Graphics2D g, int w, int h, boolean zaEkran) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (zaEkran) {
            zone.clear();
            opisiZona.clear();
        }

        Font osnovni = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        Font naslovni = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        g.setFont(naslovni);
        g.setColor(TINTA);
        g.drawString(naslov, 12, 20);

        int vrh = 34;
        // legenda samo kad ima vise serija - jedna serija je vec imenovana naslovom;
        // ako ne stane pored naslova, ide u svoj red ispod (naslov se ne precrtava)
        if (serije.size() > 1 || tip == TIP_SLOZENA) {
            List<String> nazivi = naziviLegende();
            int sirinaNaslova = g.getFontMetrics(naslovni).stringWidth(naslov);
            int sirinaLegende = sirinaLegende(g.getFontMetrics(osnovni), nazivi);
            int osnovicaLegende = 20;
            if (12 + sirinaNaslova + 16 + sirinaLegende > w - 12) {
                osnovicaLegende = 34;
                vrh = 48;
            }
            nacrtajLegendu(g, w, osnovni, nazivi, osnovicaLegende);
        }

        int dno = h - 26;
        if (tip == TIP_TRAKE || tip == TIP_SLOZENA) {
            dno = h - 12;
        }

        if (tip == TIP_LINIJE) {
            nacrtajLinije(g, w, h, vrh, dno, osnovni, zaEkran);
        } else if (tip == TIP_KOLONE) {
            nacrtajKolone(g, w, h, vrh, dno, osnovni, zaEkran);
        } else if (tip == TIP_TRAKE) {
            nacrtajTrake(g, w, h, vrh, dno, osnovni, zaEkran);
        } else {
            nacrtajSlozenu(g, w, h, vrh, dno, osnovni, zaEkran);
        }
        g.dispose();
    }

    private List<String> naziviLegende() {
        List<String> nazivi = new ArrayList<>();
        if (tip == TIP_SLOZENA) {
            nazivi.addAll(oznake);
        } else {
            for (Serija s : serije) {
                nazivi.add(s.naziv);
            }
        }
        return nazivi;
    }

    private static int sirinaLegende(FontMetrics fm, List<String> nazivi) {
        int sirina = 0;
        for (String naziv : nazivi) {
            sirina = sirina + fm.stringWidth(naziv) + 12 + 14;
        }
        return sirina;
    }

    private void nacrtajLegendu(Graphics2D g, int w, Font osnovni, List<String> nazivi, int osnovica) {
        g.setFont(osnovni);
        FontMetrics fm = g.getFontMetrics();
        int x = w - 12;
        for (int i = nazivi.size() - 1; i >= 0; i--) {
            String naziv = nazivi.get(i);
            int sirinaTeksta = fm.stringWidth(naziv);
            x = x - sirinaTeksta;
            g.setColor(TINTA_SPOREDNA);
            g.drawString(naziv, x, osnovica);
            x = x - 12;
            g.setColor(SERIJE[i % SERIJE.length]);
            g.fillOval(x, osnovica - 8, 8, 8);
            x = x - 14;
        }
    }

    // korak mreze: "lijep" broj oblika 1/2/5 * 10^k
    private static double lijepKorak(double raspon, int zeljenoLinija) {
        if (raspon <= 0) {
            return 1;
        }
        double grubo = raspon / zeljenoLinija;
        double velicina = Math.pow(10, Math.floor(Math.log10(grubo)));
        double ostatak = grubo / velicina;
        double faktor;
        if (ostatak < 1.5) {
            faktor = 1;
        } else if (ostatak < 3.5) {
            faktor = 2;
        } else if (ostatak < 7.5) {
            faktor = 5;
        } else {
            faktor = 10;
        }
        return faktor * velicina;
    }

    private double[] granice() {
        double min = 0;
        double maks = 0;
        for (Serija s : serije) {
            for (double v : s.vrijednosti) {
                if (v < min) {
                    min = v;
                }
                if (v > maks) {
                    maks = v;
                }
            }
        }
        if (maks == min) {
            maks = min + 1;
        }
        return new double[]{min, maks};
    }

    private static String oznakaBroja(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e7) {
            return String.format("%,.0f", v);
        }
        return String.format("%,.1f", v);
    }

    // y-osa sa mrezom; vraca {lijeviRub, skala, yNule, min}
    private double[] pripremiOsu(Graphics2D g, int w, int vrh, int dno, Font osnovni) {
        double[] g2 = granice();
        double min = g2[0];
        double maks = g2[1];
        double korak = lijepKorak(maks - min, 4);
        min = Math.floor(min / korak) * korak;
        maks = Math.ceil(maks / korak) * korak;
        if (maks == min) {
            maks = min + korak;
        }

        g.setFont(osnovni);
        FontMetrics fm = g.getFontMetrics();
        int najsira = 0;
        for (double v = min; v <= maks + korak / 2; v = v + korak) {
            int sirina = fm.stringWidth(oznakaBroja(v));
            if (sirina > najsira) {
                najsira = sirina;
            }
        }
        int lijevo = 12 + najsira + 8;
        double skala = (dno - vrh) / (maks - min);

        for (double v = min; v <= maks + korak / 2; v = v + korak) {
            int y = (int) Math.round(dno - (v - min) * skala);
            g.setColor(MREZA);
            g.draw(new Line2D.Double(lijevo, y, w - 12, y));
            g.setColor(TINTA_BLIJEDA);
            String tekst = oznakaBroja(v);
            g.drawString(tekst, lijevo - 8 - fm.stringWidth(tekst), y + 4);
        }
        // osa na nuli
        int yNula = (int) Math.round(dno - (0 - min) * skala);
        g.setColor(OSA);
        g.draw(new Line2D.Double(lijevo, yNula, w - 12, yNula));
        return new double[]{lijevo, skala, yNula, min};
    }

    private void oznakeX(Graphics2D g, int lijevo, int desno, int dno, Font osnovni, double korakX) {
        g.setFont(osnovni);
        FontMetrics fm = g.getFontMetrics();
        int stane = Math.max(1, (desno - lijevo) / 70);
        int preskok = Math.max(1, (int) Math.ceil((double) oznake.size() / stane));
        g.setColor(TINTA_BLIJEDA);
        for (int i = 0; i < oznake.size(); i = i + preskok) {
            String tekst = oznake.get(i);
            int x = (int) Math.round(lijevo + korakX * i + korakX / 2);
            g.drawString(tekst, x - fm.stringWidth(tekst) / 2, dno + 16);
        }
    }

    private void dodajZonu(boolean zaEkran, Rectangle zona, String opis) {
        if (zaEkran) {
            zone.add(zona);
            opisiZona.add(opis);
        }
    }

    private void nacrtajLinije(Graphics2D g, int w, int h, int vrh, int dno, Font osnovni, boolean zaEkran) {
        double[] osa = pripremiOsu(g, w, vrh, dno, osnovni);
        int lijevo = (int) osa[0];
        double skala = osa[1];
        double min = osa[3];
        int tacaka = oznake.size();
        if (tacaka == 0) {
            return;
        }
        double korakX = (double) (w - 12 - lijevo) / Math.max(1, tacaka);
        oznakeX(g, lijevo, w - 12, dno, osnovni, korakX);

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int s = 0; s < serije.size(); s++) {
            Serija serija = serije.get(s);
            Color boja = SERIJE[s % SERIJE.length];
            Path2D put = new Path2D.Double();
            for (int i = 0; i < tacaka && i < serija.vrijednosti.length; i++) {
                double x = lijevo + korakX * i + korakX / 2;
                double y = dno - (serija.vrijednosti[i] - min) * skala;
                if (i == 0) {
                    put.moveTo(x, y);
                } else {
                    put.lineTo(x, y);
                }
            }
            g.setColor(boja);
            g.draw(put);

            // markeri sa prstenom u boji podloge, samo kad tacke nisu pregusto
            boolean saMarkerima = korakX >= 14;
            if (saMarkerima) {
                for (int i = 0; i < tacaka && i < serija.vrijednosti.length; i++) {
                    double x = lijevo + korakX * i + korakX / 2;
                    double y = dno - (serija.vrijednosti[i] - min) * skala;
                    g.setColor(PODLOGA);
                    g.fillOval((int) x - 6, (int) y - 6, 12, 12);
                    g.setColor(boja);
                    g.fillOval((int) x - 4, (int) y - 4, 8, 8);
                }
            }

            // oznaka na kraju linije, u boji teksta a ne serije
            if (tacaka > 0 && serija.vrijednosti.length > 0) {
                int zadnja = Math.min(tacaka, serija.vrijednosti.length) - 1;
                double x = lijevo + korakX * zadnja + korakX / 2;
                double y = dno - (serija.vrijednosti[zadnja] - min) * skala;
                g.setFont(osnovni);
                g.setColor(TINTA_SPOREDNA);
                String tekst = Util.km(serija.vrijednosti[zadnja]);
                g.drawString(tekst, (int) Math.min(x + 8, w - 12 - g.getFontMetrics().stringWidth(tekst)),
                        (int) y - 8);
            }
        }

        // jedna zona po tacki, a tooltip nabraja vrijednosti svih serija
        // (zone po serijama bi se preklapale pa bi se vidjela samo zadnja serija)
        for (int i = 0; i < tacaka; i++) {
            StringBuilder opis = new StringBuilder(oznake.get(i));
            for (Serija serija : serije) {
                if (i < serija.vrijednosti.length) {
                    opis.append("  |  ");
                    if (!serija.naziv.isEmpty()) {
                        opis.append(serija.naziv).append(": ");
                    }
                    opis.append(Util.km(serija.vrijednosti[i])).append(" KM");
                }
            }
            double x = lijevo + korakX * i + korakX / 2;
            dodajZonu(zaEkran, new Rectangle((int) (x - korakX / 2), vrh, (int) Math.ceil(korakX), dno - vrh),
                    opis.toString());
        }
    }

    private void nacrtajKolone(Graphics2D g, int w, int h, int vrh, int dno, Font osnovni, boolean zaEkran) {
        double[] osa = pripremiOsu(g, w, vrh, dno, osnovni);
        int lijevo = (int) osa[0];
        double skala = osa[1];
        int yNula = (int) osa[2];
        double min = osa[3];
        double[] vrijednosti = serije.get(0).vrijednosti;
        int kolona = Math.min(oznake.size(), vrijednosti.length);
        if (kolona == 0) {
            return;
        }
        double korakX = (double) (w - 12 - lijevo) / kolona;
        oznakeX(g, lijevo, w - 12, dno, osnovni, korakX);

        // stub najvise 24px sirok, sa 2px razmaka i zaobljenim vrhom (ravno na osnovici)
        int sirina = (int) Math.min(24, Math.max(3, korakX - 2));
        for (int i = 0; i < kolona; i++) {
            double x = lijevo + korakX * i + (korakX - sirina) / 2;
            double v = vrijednosti[i];
            double yVrijednosti = dno - (v - min) * skala;
            g.setColor(SERIJE[0]);
            if (v >= 0) {
                nacrtajStub(g, x, yVrijednosti, sirina, yNula - yVrijednosti, true);
            } else {
                nacrtajStub(g, x, yNula, sirina, yVrijednosti - yNula, false);
            }
            dodajZonu(zaEkran, new Rectangle((int) (lijevo + korakX * i), vrh, (int) Math.ceil(korakX), dno - vrh),
                    oznake.get(i) + ": " + Util.km(v) + " KM");
        }
    }

    // stub sa zaobljenim krajem na strani podatka, ravnim uz osnovicu
    private void nacrtajStub(Graphics2D g, double x, double y, int sirina, double visina, boolean premaGore) {
        if (visina <= 0) {
            return;
        }
        double zaobljenje = Math.min(4, visina / 2);
        g.fill(new RoundRectangle2D.Double(x, y, sirina, visina, zaobljenje * 2, zaobljenje * 2));
        if (premaGore) {
            // poravnaj donji (osnovicki) kraj
            g.fill(new Rectangle2D.Double(x, y + zaobljenje, sirina, visina - zaobljenje));
        } else {
            // poravnaj gornji (osnovicki) kraj
            g.fill(new Rectangle2D.Double(x, y, sirina, visina - zaobljenje));
        }
    }

    private void nacrtajTrake(Graphics2D g, int w, int h, int vrh, int dno, Font osnovni, boolean zaEkran) {
        double[] vrijednosti = serije.get(0).vrijednosti;
        int traka = Math.min(oznake.size(), vrijednosti.length);
        if (traka == 0) {
            return;
        }
        g.setFont(osnovni);
        FontMetrics fm = g.getFontMetrics();
        int najsira = 0;
        for (String oznaka : oznake) {
            int sirina = fm.stringWidth(oznaka);
            if (sirina > najsira) {
                najsira = sirina;
            }
        }
        if (najsira > w / 3) {
            najsira = w / 3;
        }
        int lijevo = 12 + najsira + 8;

        double min = 0;
        double maks = 0;
        for (double v : vrijednosti) {
            if (v < min) {
                min = v;
            }
            if (v > maks) {
                maks = v;
            }
        }
        if (maks == min) {
            maks = min + 1;
        }
        // prostor za oznaku vrijednosti na vrhu trake
        int desno = w - 12 - 64;
        double skala = (desno - lijevo) / (maks - min);
        int xNula = (int) Math.round(lijevo + (0 - min) * skala);

        double korakY = (double) (dno - vrh) / traka;
        int debljina = (int) Math.min(24, Math.max(4, korakY - 2));

        g.setColor(OSA);
        g.draw(new Line2D.Double(xNula, vrh, xNula, dno));

        for (int i = 0; i < traka; i++) {
            double y = vrh + korakY * i + (korakY - debljina) / 2;
            double v = vrijednosti[i];
            double xVrijednosti = lijevo + (v - min) * skala;

            g.setColor(TINTA_SPOREDNA);
            String oznaka = skratiTekst(oznake.get(i), fm, najsira);
            g.drawString(oznaka, lijevo - 8 - fm.stringWidth(oznaka), (int) (y + debljina / 2.0 + 4));

            g.setColor(SERIJE[0]);
            if (v >= 0 && xVrijednosti - xNula > 0) {
                double duzina = xVrijednosti - xNula;
                double zaobljenje = Math.min(4, duzina / 2);
                g.fill(new RoundRectangle2D.Double(xNula, y, duzina, debljina, zaobljenje * 2, zaobljenje * 2));
                // poravnaj lijevi (osnovicki) kraj
                g.fill(new Rectangle2D.Double(xNula, y, duzina - zaobljenje, debljina));
            } else if (v < 0 && xNula - xVrijednosti > 0) {
                double duzina = xNula - xVrijednosti;
                double zaobljenje = Math.min(4, duzina / 2);
                g.fill(new RoundRectangle2D.Double(xVrijednosti, y, duzina, debljina, zaobljenje * 2, zaobljenje * 2));
                // poravnaj desni (osnovicki) kraj
                g.fill(new Rectangle2D.Double(xVrijednosti + zaobljenje, y, duzina - zaobljenje, debljina));
            }

            // vrijednost na vrhu trake, u boji teksta; za negativnu traku ide desno
            // od nulte ose (prazna strana), da se ne sudari sa nazivom kategorije
            g.setColor(TINTA);
            String tekst = Util.km(v);
            if (v >= 0) {
                g.drawString(tekst, (int) xVrijednosti + 6, (int) (y + debljina / 2.0 + 4));
            } else {
                g.drawString(tekst, xNula + 6, (int) (y + debljina / 2.0 + 4));
            }
            dodajZonu(zaEkran, new Rectangle(0, (int) (vrh + korakY * i), w, (int) Math.ceil(korakY)),
                    oznake.get(i) + ": " + Util.km(v) + " KM");
        }
    }

    private void nacrtajSlozenu(Graphics2D g, int w, int h, int vrh, int dno, Font osnovni, boolean zaEkran) {
        double[] vrijednosti = serije.get(0).vrijednosti;
        double ukupno = 0;
        for (double v : vrijednosti) {
            if (v > 0) {
                ukupno = ukupno + v;
            }
        }
        if (ukupno <= 0) {
            g.setFont(osnovni);
            g.setColor(TINTA_BLIJEDA);
            g.drawString("Nema podataka za prikaz", 12, (vrh + dno) / 2);
            return;
        }
        int lijevo = 12;
        int desno = w - 12;
        int debljina = 24;
        int y = (vrh + dno) / 2 - debljina / 2;

        g.setFont(osnovni);
        FontMetrics fm = g.getFontMetrics();
        double x = lijevo;
        for (int i = 0; i < vrijednosti.length; i++) {
            double v = vrijednosti[i];
            if (v <= 0) {
                continue;
            }
            double sirina = (desno - lijevo) * v / ukupno;
            g.setColor(SERIJE[i % SERIJE.length]);
            // 2px razmaka u boji podloge izmedju segmenata
            g.fill(new Rectangle2D.Double(x, y, Math.max(0, sirina - 2), debljina));

            double udio = 100.0 * v / ukupno;
            String tekst = String.format("%.0f%%", udio);
            if (fm.stringWidth(tekst) + 8 < sirina) {
                // tekst unutar segmenta: bijelo ili crno prema svjetlini podloge segmenta
                Color boja = SERIJE[i % SERIJE.length];
                int svjetlina = (boja.getRed() * 299 + boja.getGreen() * 587 + boja.getBlue() * 114) / 1000;
                if (svjetlina > 150) {
                    g.setColor(TINTA);
                } else {
                    g.setColor(Color.WHITE);
                }
                g.drawString(tekst, (int) (x + (sirina - 2 - fm.stringWidth(tekst)) / 2), y + debljina / 2 + 4);
            }
            dodajZonu(zaEkran, new Rectangle((int) x, y - 6, (int) Math.ceil(sirina), debljina + 12),
                    oznake.get(i) + ": " + Util.km(v) + " KM (" + String.format("%.1f%%", udio) + ")");
            x = x + sirina;
        }
    }

    private static String skratiTekst(String tekst, FontMetrics fm, int maksSirina) {
        if (fm.stringWidth(tekst) <= maksSirina) {
            return tekst;
        }
        String skraceno = tekst;
        while (skraceno.length() > 1 && fm.stringWidth(skraceno + "…") > maksSirina) {
            skraceno = skraceno.substring(0, skraceno.length() - 1);
        }
        return skraceno + "…";
    }
}
