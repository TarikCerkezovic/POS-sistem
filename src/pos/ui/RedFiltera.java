package pos.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// zajednicki red sa filterima za sve tabele: sam osluskuje svoja polja pa se
// tabela osvjezava dok korisnik kuca, a greske javlja u samom redu
public class RedFiltera extends JPanel {

    // pauza od zadnje tipke do osvjezavanja tabele
    private static final int PAUZA_MS = 300;
    // labele koje razdvajaju dva polja jednog opsega ("od" - "do")
    private static final String[] RAZDVAJACI = {"-", "do", "–"};

    // dok je vece od nule, promjene se ignorisu (punjenje combo boxova nije
    // korisnikova izmjena filtera)
    private static int utisano = 0;
    private static boolean uzivo = false;

    private final List<Component> polja = new ArrayList<>();
    private final List<Component[]> opsezi = new ArrayList<>();
    private final List<JTextField> crvena = new ArrayList<>();
    private final JButton btnPonisti = new JButton("Poništi");
    private final JLabel lStanje = new JLabel();
    private final javax.swing.Timer pauza;
    private final Runnable prikazi;
    private final Runnable ponisti;
    private Border obicniOkvir;

    public RedFiltera(String naslov, String tekstDugmeta, Runnable prikazi, Runnable ponisti,
                      Runnable pdf, Object... komponente) {
        super(new Prelomni(5, 2));
        this.prikazi = prikazi;
        this.ponisti = ponisti;
        if (naslov != null) {
            setBorder(BorderFactory.createTitledBorder(naslov));
        }
        // suzavanjem prozora se red prelomi, pa mu visina mora biti ponovo izracunata
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                revalidate();
            }
        });

        Component prethodna = null;
        String prethodnaLabela = null;
        for (Object k : komponente) {
            if (k instanceof String) {
                add(new JLabel((String) k));
                prethodnaLabela = (String) k;
            } else {
                Component komp = (Component) k;
                add(komp);
                if (registruj(komp)) {
                    // dva ista polja razdvojena labelom "-" ili "do" su jedan opseg
                    if (prethodna != null && jeRazdvajac(prethodnaLabela)
                            && prethodna.getClass() == komp.getClass()) {
                        opsezi.add(new Component[]{prethodna, komp});
                    }
                    prethodna = komp;
                } else {
                    prethodna = null;
                }
                prethodnaLabela = null;
            }
        }

        JButton btnPrikazi = UiUtil.dugme(tekstDugmeta, "filter");
        btnPrikazi.setToolTipText("Primijeni filtere (tabela se osvježava i dok kucate)");
        btnPrikazi.addActionListener(e -> primijeni(false));
        add(btnPrikazi);

        if (ponisti != null) {
            btnPonisti.setToolTipText("Vrati sve filtere na početno stanje");
            btnPonisti.addActionListener(e -> ponistiSve());
            add(btnPonisti);
        }
        if (pdf != null) {
            JButton btnPdf = UiUtil.dugme("Izvezi u PDF", "pdf");
            btnPdf.addActionListener(e -> pdf.run());
            add(btnPdf);
        }

        lStanje.setForeground(Color.GRAY);
        add(lStanje);

        pauza = new javax.swing.Timer(PAUZA_MS, e -> primijeni(true));
        pauza.setRepeats(false);
        osvjeziStanje();
    }

    // period se racuna na klik, a ne pri gradnji prozora, da dugmad rade ispravno
    // i ako program ostane otvoren preko ponoci
    private interface Period {
        java.time.LocalDate[] izracunaj();
    }

    // redovi filtera se slazu jedan pod drugi, svaki sa svojom visinom
    // (GridLayout bi svima dao visinu najviseg reda)
    public static JPanel uspravnoSlaganje() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    // dugmad koja jednim klikom postave oba datuma
    public static JPanel brziPeriodi(BiracDatuma od, BiracDatuma doD, Runnable osvjezi) {
        JPanel red = new JPanel(new Prelomni(4, 2));
        JLabel oznaka = new JLabel("Brzi periodi:");
        oznaka.setForeground(Color.GRAY);
        red.add(oznaka);
        dodajPeriod(red, od, doD, osvjezi, "Danas",
                () -> new java.time.LocalDate[]{java.time.LocalDate.now(), java.time.LocalDate.now()});
        dodajPeriod(red, od, doD, osvjezi, "Juče",
                () -> new java.time.LocalDate[]{java.time.LocalDate.now().minusDays(1),
                        java.time.LocalDate.now().minusDays(1)});
        dodajPeriod(red, od, doD, osvjezi, "Zadnjih 7 dana",
                () -> new java.time.LocalDate[]{java.time.LocalDate.now().minusDays(6),
                        java.time.LocalDate.now()});
        dodajPeriod(red, od, doD, osvjezi, "Ova sedmica",
                () -> new java.time.LocalDate[]{pos.data.Baza.pocetakSedmice(java.time.LocalDate.now()),
                        java.time.LocalDate.now()});
        dodajPeriod(red, od, doD, osvjezi, "Ovaj mjesec",
                () -> new java.time.LocalDate[]{java.time.LocalDate.now().withDayOfMonth(1),
                        java.time.LocalDate.now()});
        dodajPeriod(red, od, doD, osvjezi, "Prošli mjesec", () -> {
            java.time.LocalDate prvi = java.time.LocalDate.now().withDayOfMonth(1).minusMonths(1);
            return new java.time.LocalDate[]{prvi, prvi.withDayOfMonth(prvi.lengthOfMonth())};
        });
        dodajPeriod(red, od, doD, osvjezi, "Ova godina",
                () -> new java.time.LocalDate[]{java.time.LocalDate.now().withDayOfYear(1),
                        java.time.LocalDate.now()});
        dodajPeriod(red, od, doD, osvjezi, "Sve", () -> new java.time.LocalDate[]{null, null});
        return red;
    }

    private static void dodajPeriod(JPanel red, BiracDatuma od, BiracDatuma doD, Runnable osvjezi,
                                    String naziv, Period period) {
        JButton btn = new JButton(naziv);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.addActionListener(e -> {
            java.time.LocalDate[] granice = period.izracunaj();
            // postaviDatum ne pokrece osluskivace, pa se osvjezavanje trazi jednom
            tiho(() -> {
                od.postaviDatum(granice[0]);
                doD.postaviDatum(granice[1]);
            });
            osvjezi.run();
        });
        red.add(btn);
    }

    // promjene unutar akcije se ne racunaju kao korisnikova izmjena filtera
    public static void tiho(Runnable akcija) {
        utisano = utisano + 1;
        try {
            akcija.run();
        } finally {
            utisano = utisano - 1;
        }
    }

    // true kad osvjezavanje dolazi od kucanja - tada se greske ne prikazuju dijalogom
    public static boolean uzivo() {
        return uzivo;
    }

    private static boolean jeTiho() {
        return utisano > 0;
    }

    private static boolean jeRazdvajac(String labela) {
        if (labela == null) {
            return false;
        }
        String cista = labela.trim().toLowerCase().replace(":", "");
        for (String r : RAZDVAJACI) {
            if (cista.equals(r)) {
                return true;
            }
        }
        return false;
    }

    // zapamti polje kao filter i osluskuj ga; false za komponente koje nisu filter
    private boolean registruj(Component komp) {
        if (komp instanceof PoljePretrage) {
            PoljePretrage pp = (PoljePretrage) komp;
            osluskujTekst(pp.polje());
            pp.naPrimjenu(() -> primijeni(false));
            polja.add(komp);
            return true;
        }
        if (komp instanceof JTextField) {
            JTextField tf = (JTextField) komp;
            osluskujTekst(tf);
            // Enter u polju odmah primjenjuje filtere
            tf.addActionListener(e -> primijeni(false));
            polja.add(komp);
            return true;
        }
        if (komp instanceof JComboBox) {
            ((JComboBox<?>) komp).addActionListener(e -> odmah());
            polja.add(komp);
            return true;
        }
        if (komp instanceof JCheckBox) {
            ((JCheckBox) komp).addActionListener(e -> odmah());
            polja.add(komp);
            return true;
        }
        if (komp instanceof BiracDatuma) {
            ((BiracDatuma) komp).naPromjenu(this::odmah);
            polja.add(komp);
            return true;
        }
        return false;
    }

    private void osluskujTekst(JTextField tf) {
        if (obicniOkvir == null) {
            obicniOkvir = tf.getBorder();
        }
        tf.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                sacekaj();
            }

            public void removeUpdate(DocumentEvent e) {
                sacekaj();
            }

            public void changedUpdate(DocumentEvent e) {
                sacekaj();
            }
        });
    }

    // kucanje: kratko se ceka da korisnik prestane pisati
    private void sacekaj() {
        if (jeTiho()) {
            return;
        }
        pauza.restart();
    }

    // izbor iz liste ili kvacica - nema sta da se ceka
    private void odmah() {
        if (jeTiho()) {
            return;
        }
        pauza.stop();
        primijeni(true);
    }

    // za pozivaoca koji sam promijeni filtere (npr. brzi periodi)
    public void primijeni() {
        primijeni(false);
    }

    private void primijeni(boolean izKucanja) {
        pauza.stop();
        String greska = provjeriPolja();
        if (greska != null) {
            poruka(greska, true);
            return;
        }
        uzivo = izKucanja;
        try {
            prikazi.run();
        } finally {
            uzivo = false;
        }
        osvjeziStanje();
    }

    private void ponistiSve() {
        tiho(ponisti);
        for (JTextField tf : crvena) {
            tf.setBorder(obicniOkvir);
        }
        crvena.clear();
        osvjeziStanje();
    }

    // vraca poruku o gresci ili null; polja sa neispravnim brojem dobiju crveni okvir
    private String provjeriPolja() {
        List<JTextField> nova = new ArrayList<>();
        String greska = null;
        for (Component[] opseg : opsezi) {
            if (opseg[0] instanceof JTextField) {
                JTextField od = (JTextField) opseg[0];
                JTextField doP = (JTextField) opseg[1];
                Double x = null;
                Double y = null;
                if (!prazno(od)) {
                    x = broj(od.getText());
                    if (x == null) {
                        nova.add(od);
                    }
                }
                if (!prazno(doP)) {
                    y = broj(doP.getText());
                    if (y == null) {
                        nova.add(doP);
                    }
                }
                if ((x == null && !prazno(od)) || (y == null && !prazno(doP))) {
                    greska = "Neispravan broj u polju filtera (koristite npr. 12 ili 12.50).";
                } else if (x != null && y != null && x > y) {
                    greska = "Vrijednost \"od\" je veća od \"do\" - nijedan red ne može proći filter.";
                }
            } else if (opseg[0] instanceof BiracDatuma) {
                java.time.LocalDate od = ((BiracDatuma) opseg[0]).getDatum();
                java.time.LocalDate doD = ((BiracDatuma) opseg[1]).getDatum();
                if (od != null && doD != null && od.isAfter(doD)) {
                    greska = "Datum \"od\" je poslije datuma \"do\" - nijedan red ne može proći filter.";
                }
            }
        }
        // prvo se skinu stari crveni okviri, pa se oboje novi
        for (JTextField tf : crvena) {
            tf.setBorder(obicniOkvir);
        }
        crvena.clear();
        for (JTextField tf : nova) {
            tf.setBorder(BorderFactory.createLineBorder(new Color(178, 60, 50), 2));
            crvena.add(tf);
        }
        return greska;
    }

    private static boolean prazno(JTextField tf) {
        return tf.getText().trim().isEmpty();
    }

    private static Double broj(String s) {
        try {
            return Double.valueOf(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private void osvjeziStanje() {
        if (ponisti == null) {
            poruka("", false);
            return;
        }
        int aktivnih = brojAktivnih();
        btnPonisti.setEnabled(aktivnih > 0);
        if (aktivnih == 0) {
            poruka("bez filtera - prikazani su svi redovi", false);
        } else {
            poruka(aktivnih + " " + rijecFilter(aktivnih), false);
        }
    }

    private void poruka(String tekst, boolean greska) {
        lStanje.setText(tekst);
        if (greska) {
            lStanje.setForeground(new Color(178, 60, 50));
            lStanje.setIcon(Ikone.ikona("upozorenje", 14));
        } else {
            lStanje.setForeground(Color.GRAY);
            lStanje.setIcon(null);
        }
    }

    private static String rijecFilter(int broj) {
        if (broj == 1) {
            return "aktivan filter";
        }
        if (broj < 5) {
            return "aktivna filtera";
        }
        return "aktivnih filtera";
    }

    // FlowLayout prelomi red u vise linija, ali za visinu javi samo jednu, pa ostatak
    // ostane odsjecen; ovaj raspored visinu racuna prema stvarnoj sirini reda
    private static class Prelomni extends FlowLayout {

        Prelomni(int vodoravno, int uspravno) {
            super(FlowLayout.LEFT, vodoravno, uspravno);
        }

        @Override
        public Dimension preferredLayoutSize(Container cilj) {
            return velicina(cilj, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container cilj) {
            Dimension d = velicina(cilj, false);
            d.width = d.width - getHgap() - 1;
            return d;
        }

        private Dimension velicina(Container cilj, boolean preferirana) {
            synchronized (cilj.getTreeLock()) {
                int sirinaCilja = cilj.getWidth();
                if (sirinaCilja == 0) {
                    // prije prvog prikaza se ponasa kao obicni FlowLayout
                    sirinaCilja = Integer.MAX_VALUE;
                }
                Insets ivice = cilj.getInsets();
                int raspolozivo = sirinaCilja - (ivice.left + ivice.right + getHgap() * 2);
                Dimension ukupno = new Dimension(0, 0);
                int sirinaReda = 0;
                int visinaReda = 0;
                for (int i = 0; i < cilj.getComponentCount(); i++) {
                    Component k = cilj.getComponent(i);
                    if (!k.isVisible()) {
                        continue;
                    }
                    Dimension d;
                    if (preferirana) {
                        d = k.getPreferredSize();
                    } else {
                        d = k.getMinimumSize();
                    }
                    if (sirinaReda != 0 && sirinaReda + getHgap() + d.width > raspolozivo) {
                        zatvoriRed(ukupno, sirinaReda, visinaReda);
                        sirinaReda = 0;
                        visinaReda = 0;
                    }
                    if (sirinaReda != 0) {
                        sirinaReda = sirinaReda + getHgap();
                    }
                    sirinaReda = sirinaReda + d.width;
                    visinaReda = Math.max(visinaReda, d.height);
                }
                zatvoriRed(ukupno, sirinaReda, visinaReda);
                ukupno.width = ukupno.width + ivice.left + ivice.right + getHgap() * 2;
                ukupno.height = ukupno.height + ivice.top + ivice.bottom + getVgap() * 2;
                return ukupno;
            }
        }

        private void zatvoriRed(Dimension ukupno, int sirina, int visina) {
            ukupno.width = Math.max(ukupno.width, sirina);
            if (ukupno.height > 0) {
                ukupno.height = ukupno.height + getVgap();
            }
            ukupno.height = ukupno.height + visina;
        }
    }

    private int brojAktivnih() {
        int broj = 0;
        for (Component k : polja) {
            if (aktivan(k)) {
                broj = broj + 1;
            }
        }
        return broj;
    }

    private static boolean aktivan(Component k) {
        if (k instanceof PoljePretrage) {
            return !((PoljePretrage) k).prazno();
        }
        if (k instanceof JTextField) {
            return !((JTextField) k).getText().trim().isEmpty();
        }
        if (k instanceof JComboBox) {
            // prva stavka filter combo boxa je uvijek "— Sve ... —"
            return ((JComboBox<?>) k).getSelectedIndex() > 0;
        }
        if (k instanceof JCheckBox) {
            return ((JCheckBox) k).isSelected();
        }
        if (k instanceof BiracDatuma) {
            return ((BiracDatuma) k).getDatum() != null;
        }
        return false;
    }
}
