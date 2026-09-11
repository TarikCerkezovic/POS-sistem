package pos.ui;

import pos.data.Baza;
import pos.util.PdfIzvjestaj;
import pos.util.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// statisticki pregled sa KPI karticama i grafikonima, za menadzera i administratora
public class StatistikaPanel extends JPanel {

    private static final DateTimeFormatter KRATKI_DATUM = DateTimeFormatter.ofPattern("dd.MM.");

    private final Baza baza = Baza.get();

    private final BiracDatuma bdOd = new BiracDatuma(LocalDate.now().minusDays(29));
    private final BiracDatuma bdDo = new BiracDatuma(LocalDate.now());
    private final JPanel kartice = new JPanel(new GridLayout(2, 4, 8, 8));
    private final JPanel mrezaGrafikona = new JPanel(new GridLayout(0, 2, 10, 10));

    // zadnje izracunato, za izvoz u PDF
    private LocalDate zadnjiOd;
    private LocalDate zadnjiDo;
    private Baza.StatistikaPerioda zadnjaStatistika;
    private final List<Grafikon> grafikoni = new ArrayList<>();
    private List<Object[]> zadnjiTop = new ArrayList<>();

    public StatistikaPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        gore.add(new JLabel("Od:"));
        gore.add(bdOd);
        gore.add(new JLabel("Do:"));
        gore.add(bdDo);
        dodajPreset(gore, "Danas", 0);
        dodajPreset(gore, "7 dana", 6);
        dodajPreset(gore, "30 dana", 29);
        JButton btnMjesec = new JButton("Ovaj mjesec");
        btnMjesec.addActionListener(e -> {
            bdOd.postaviDatum(LocalDate.now().withDayOfMonth(1));
            bdDo.postaviDatum(LocalDate.now());
            osvjezi();
        });
        gore.add(btnMjesec);
        JButton btnGodina = new JButton("Ova godina");
        btnGodina.addActionListener(e -> {
            bdOd.postaviDatum(LocalDate.now().withDayOfYear(1));
            bdDo.postaviDatum(LocalDate.now());
            osvjezi();
        });
        gore.add(btnGodina);
        JButton btnPrikazi = UiUtil.dugme("Prikaži", "grafikon");
        btnPrikazi.addActionListener(e -> osvjezi());
        gore.add(btnPrikazi);
        JButton btnPdf = UiUtil.dugme("Izvezi u PDF", "pdf");
        btnPdf.addActionListener(e -> izveziPdf());
        gore.add(btnPdf);
        add(gore, BorderLayout.NORTH);

        JPanel sadrzaj = new JPanel(new BorderLayout(8, 8));
        sadrzaj.add(kartice, BorderLayout.NORTH);
        sadrzaj.add(mrezaGrafikona, BorderLayout.CENTER);

        // omotac da mreza ne raste u sirinu preko viewporta
        JPanel omotac = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                Container p = getParent();
                if (p instanceof JViewport) {
                    d.width = p.getWidth();
                }
                return d;
            }
        };
        omotac.add(sadrzaj, BorderLayout.NORTH);
        JScrollPane skrol = new JScrollPane(omotac,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        skrol.getVerticalScrollBar().setUnitIncrement(16);
        skrol.setBorder(null);
        add(skrol, BorderLayout.CENTER);

        osvjezi();
    }

    private void dodajPreset(JPanel panel, String naziv, int danaUnazad) {
        JButton btn = new JButton(naziv);
        btn.addActionListener(e -> {
            bdOd.postaviDatum(LocalDate.now().minusDays(danaUnazad));
            bdDo.postaviDatum(LocalDate.now());
            osvjezi();
        });
        panel.add(btn);
    }

    public void osvjezi() {
        try {
            LocalDate od = bdOd.getDatumObavezan("Od");
            LocalDate doD = bdDo.getDatumObavezan("Do");
            if (od.isAfter(doD)) {
                throw new IllegalArgumentException("Datum \"od\" je poslije datuma \"do\"!");
            }
            zadnjiOd = od;
            zadnjiDo = doD;
            zadnjaStatistika = baza.statistikaPerioda(od, doD);
            napuniKartice(zadnjaStatistika);
            napuniGrafikone(od, doD);
            revalidate();
            repaint();
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }

    private void napuniKartice(Baza.StatistikaPerioda st) {
        kartice.removeAll();
        kartice.add(kartica("Neto promet", Util.km(st.promet) + " KM", true));
        kartice.add(kartica("Prodaja (bez povrata)", Util.km(st.prodaja) + " KM", false));
        kartice.add(kartica("Povrati", st.brojPovrata + " povrata / " + Util.km(st.iznosPovrata) + " KM", false));
        kartice.add(kartica("Izdatih računa", String.valueOf(st.brojIzdatihRacuna), false));
        kartice.add(kartica("Prosječan račun", Util.km(st.prosjecanRacun) + " KM", false));
        kartice.add(kartica("Storniranih računa", String.valueOf(st.brojStorniranihRacuna), false));
        kartice.add(kartica("Otpisi u periodu", String.valueOf(st.brojOtpisa), false));
        kartice.add(kartica("Vrijednost zaliha (sada)", Util.km(baza.vrijednostZaliha()) + " KM", false));
    }

    // kartica: oznaka sitno u sporednoj boji, vrijednost krupno u osnovnoj
    private JPanel kartica(String oznaka, String vrijednost, boolean istaknuta) {
        JPanel panel = new JPanel(new BorderLayout(2, 2));
        panel.setBackground(new Color(0xfcfcfb));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xe1e0d9)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JLabel lOznaka = new JLabel(oznaka);
        lOznaka.setForeground(new Color(0x52514e));
        lOznaka.setFont(lOznaka.getFont().deriveFont(Font.PLAIN, 11f));
        JLabel lVrijednost = new JLabel(vrijednost);
        float velicina = 16f;
        if (istaknuta) {
            velicina = 22f;
        }
        lVrijednost.setFont(lVrijednost.getFont().deriveFont(Font.BOLD, velicina));
        lVrijednost.setForeground(new Color(0x0b0b0b));
        panel.add(lOznaka, BorderLayout.NORTH);
        panel.add(lVrijednost, BorderLayout.CENTER);
        return panel;
    }

    private void napuniGrafikone(LocalDate od, LocalDate doD) {
        mrezaGrafikona.removeAll();
        grafikoni.clear();

        // 1. promet po danima (linije: prodaja i povrati)
        Map<LocalDate, double[]> poDanima = baza.prometPoDanima(od, doD);
        List<String> dani = new ArrayList<>();
        double[] prodaja = new double[poDanima.size()];
        double[] povrati = new double[poDanima.size()];
        int i = 0;
        for (Map.Entry<LocalDate, double[]> e : poDanima.entrySet()) {
            dani.add(e.getKey().format(KRATKI_DATUM));
            prodaja[i] = e.getValue()[0];
            povrati[i] = e.getValue()[1];
            i++;
        }
        List<Grafikon.Serija> serije = new ArrayList<>();
        serije.add(new Grafikon.Serija("Prodaja", prodaja));
        serije.add(new Grafikon.Serija("Povrati", povrati));
        dodajGrafikon(Grafikon.linije("Promet po danima (KM)", dani, serije));

        // 2. promet po glavnim kategorijama: najvise 7 najjacih + jedno "Ostalo"
        // (u "Ostalo" ide i prirodna "Ostalo" grupa iz baze, gdje god da je u poretku)
        Map<String, Double> poKategorijama = baza.prometPoKategorijama(od, doD);
        List<String> kategorije = new ArrayList<>();
        List<Double> iznosiKategorija = new ArrayList<>();
        double ostalo = 0;
        boolean imaOstalog = false;
        for (Map.Entry<String, Double> e : poKategorijama.entrySet()) {
            if (!"Ostalo".equals(e.getKey()) && kategorije.size() < 7) {
                kategorije.add(e.getKey());
                iznosiKategorija.add(e.getValue());
            } else {
                ostalo = ostalo + e.getValue();
                imaOstalog = true;
            }
        }
        if (imaOstalog) {
            kategorije.add("Ostalo");
            iznosiKategorija.add(ostalo);
        }
        dodajGrafikon(Grafikon.trakasti("Neto promet po glavnim kategorijama (KM)",
                kategorije, uNiz(iznosiKategorija)));

        // 3. promet po prodavacima
        Map<String, double[]> poProdavacima = baza.prometPoProdavacima(od, doD);
        List<String> prodavaci = new ArrayList<>();
        double[] iznosiProdavaca = new double[poProdavacima.size()];
        i = 0;
        for (Map.Entry<String, double[]> e : poProdavacima.entrySet()) {
            prodavaci.add(e.getKey());
            iznosiProdavaca[i] = e.getValue()[1];
            i++;
        }
        dodajGrafikon(Grafikon.trakasti("Neto promet po prodavačima (KM)", prodavaci, iznosiProdavaca));

        // 4. prodaja po satima
        double[] poSatima = baza.prometPoSatima(od, doD);
        List<String> sati = new ArrayList<>();
        for (int sat = 0; sat < 24; sat++) {
            sati.add(sat + "h");
        }
        dodajGrafikon(Grafikon.kolone("Prodaja po satima u danu (KM)", sati, poSatima));

        // 5. nacin placanja (udio u prodaji)
        Map<String, double[]> placanja = baza.prometPoNacinuPlacanja(od, doD);
        List<String> naciniNazivi = new ArrayList<>();
        double[] naciniIznosi = new double[placanja.size()];
        i = 0;
        for (Map.Entry<String, double[]> e : placanja.entrySet()) {
            naciniNazivi.add(e.getKey() + " (" + (long) e.getValue()[0] + " rač.)");
            naciniIznosi[i] = e.getValue()[1];
            i++;
        }
        dodajGrafikon(Grafikon.slozenaTraka("Način plaćanja - udio u prodaji", naciniNazivi, naciniIznosi));

        // 6. top 10 artikala po prometu
        zadnjiTop = baza.najprodavaniji(od, doD, null, true, false, 10);
        List<String> naziviTop = new ArrayList<>();
        double[] iznosiTop = new double[zadnjiTop.size()];
        i = 0;
        for (Object[] red : zadnjiTop) {
            naziviTop.add(String.valueOf(red[1]));
            iznosiTop[i] = (Double) red[3];
            i++;
        }
        dodajGrafikon(Grafikon.trakasti("Top 10 artikala po prometu (KM)", naziviTop, iznosiTop));
    }

    private void dodajGrafikon(Grafikon grafikon) {
        grafikon.setPreferredSize(new Dimension(200, 250));
        grafikon.setBorder(BorderFactory.createLineBorder(new Color(0xe1e0d9)));
        mrezaGrafikona.add(grafikon);
        grafikoni.add(grafikon);
    }

    private static double[] uNiz(List<Double> lista) {
        double[] niz = new double[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            niz[i] = lista.get(i);
        }
        return niz;
    }

    private void izveziPdf() {
        if (zadnjaStatistika == null) {
            UiUtil.greska(this, "Prvo prikažite statistiku!");
            return;
        }
        try {
            Baza.StatistikaPerioda st = zadnjaStatistika;
            StringBuilder opis = new StringBuilder();
            opis.append("Period: ").append(zadnjiOd.format(Util.DATUM))
                    .append(" - ").append(zadnjiDo.format(Util.DATUM)).append("\n");
            opis.append("Neto promet: ").append(Util.km(st.promet)).append(" KM   |   Prodaja: ")
                    .append(Util.km(st.prodaja)).append(" KM   |   Povrati: ")
                    .append(Util.km(st.iznosPovrata)).append(" KM (").append(st.brojPovrata).append(" povrata)\n");
            opis.append("Izdatih računa: ").append(st.brojIzdatihRacuna)
                    .append("   |   Prosječan račun: ").append(Util.km(st.prosjecanRacun))
                    .append(" KM   |   Storniranih: ").append(st.brojStorniranihRacuna).append("\n");
            opis.append("Otpisi: ").append(st.brojOtpisa)
                    .append("   |   Vrijednost zaliha: ").append(Util.km(baza.vrijednostZaliha()))
                    .append(" KM");

            List<BufferedImage> slike = new ArrayList<>();
            for (Grafikon grafikon : grafikoni) {
                slike.add(grafikon.slika(520, 260));
            }

            String[] kolone = {"Rang", "Šifra", "Naziv", "Prodano (kom)", "Promet (KM)"};
            List<Object[]> redovi = new ArrayList<>();
            int rang = 1;
            for (Object[] red : zadnjiTop) {
                redovi.add(new Object[]{rang, red[0], red[1], red[2], Util.km((Double) red[3])});
                rang = rang + 1;
            }
            File fajl = PdfIzvjestaj.izvezi("Statistika poslovanja", opis.toString(), kolone, redovi, slike);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }
}
