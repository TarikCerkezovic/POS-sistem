package pos.ui;

import pos.data.Baza;
import pos.model.*;
import pos.util.PdfRacun;
import pos.util.Slike;
import pos.util.Util;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProdavacFrame extends JFrame {

    private final Baza baza = Baza.get();
    private final Korisnik korisnik;

    private final List<StavkaRacuna> korpa = new ArrayList<>();

    private final JTextField tfPretraga = new JTextField();
    private final JTabbedPane taboviArtikala = new JTabbedPane();
    private final DefaultTableModel modelArtikli =
            UiUtil.model("Šifra", "Naziv", "Cijena (KM)", "Stanje", "Popust");
    private final JTable tabelaArtikli = UiUtil.tabela(modelArtikli, "Nema artikala za prikaz");

    private final JTextField tfSifra = new JTextField(8);
    private final JTextField tfKolicina = new JTextField("1", 4);
    private final DefaultTableModel modelKorpa =
            UiUtil.model("Šifra", "Naziv", "Kol.", "Cijena", "Popust %", "Iznos (KM)");
    private final JTable tabelaKorpa = new JTable(modelKorpa);

    private final JLabel lUkupno = new JLabel("0.00 KM");
    private final JLabel lOsnovica = new JLabel("Osnovica: 0.00 KM");
    private final JLabel lPdv = new JLabel("PDV (17%): 0.00 KM");
    private final JRadioButton rbGotovina = new JRadioButton("Gotovina", true);
    private final JRadioButton rbKartica = new JRadioButton("Kartica");
    private final JTextField tfPredato = new JTextField(8);
    private final JLabel lPovrat = new JLabel("Povrat: 0.00 KM");

    public ProdavacFrame(Korisnik korisnik) {
        this.korisnik = korisnik;

        setTitle("POS sistem - Prodavač");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel zaglavlje = UiUtil.zaglavlje("Prodaja i izdavanje računa", korisnik, this);
        JPanel dodatno = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        JButton btnPovrat = UiUtil.dugme("Povrat robe (F5)", "povrat");
        JButton btnStorno = UiUtil.dugme("Storno računa (F8)", "storno");
        btnPovrat.addActionListener(e -> povratRobeDijalog());
        btnStorno.addActionListener(e -> stornoRacuna());
        dodatno.add(btnPovrat);
        dodatno.add(btnStorno);
        zaglavlje.add(dodatno, BorderLayout.CENTER);
        add(zaglavlje, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                paneArtikli(), paneKorpa());
        split.setResizeWeight(0.5);
        split.setDividerLocation(560);
        add(split, BorderLayout.CENTER);

        UiUtil.precica(getRootPane(), "F5", btnPovrat);
        UiUtil.precica(getRootPane(), "F8", btnStorno);

        osvjeziArtikle();
    }

    private JPanel paneArtikli() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Artikli (klik na sličicu ili dupli klik u tabeli dodaje u korpu)"));

        JPanel gore = new JPanel(new BorderLayout(6, 0));
        gore.add(new JLabel("Pretraga:"), BorderLayout.WEST);
        gore.add(tfPretraga, BorderLayout.CENTER);
        panel.add(gore, BorderLayout.NORTH);

        tfPretraga.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { osvjeziArtikle(); }
            public void removeUpdate(DocumentEvent e) { osvjeziArtikle(); }
            public void changedUpdate(DocumentEvent e) { osvjeziArtikle(); }
        });

        tabelaArtikli.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int red = tabelaArtikli.getSelectedRow();
                    if (red >= 0) {
                        // preko tabele a ne modela, zbog sortiranja
                        dodajUKorpu((String) tabelaArtikli.getValueAt(red, 0), 1);
                    }
                }
            }
        });

        panel.add(taboviArtikala, BorderLayout.CENTER);
        return panel;
    }

    private void osvjeziArtikle() {
        String filter = tfPretraga.getText().trim().toLowerCase();
        LocalDate danas = LocalDate.now();

        modelArtikli.setRowCount(0);
        List<Artikal> filtrirani = new ArrayList<>();
        for (Artikal a : baza.getArtikli()) {
            if (!filter.isEmpty()
                    && !a.getNaziv().toLowerCase().contains(filter)
                    && !a.getSifra().toLowerCase().contains(filter)) {
                continue;
            }
            filtrirani.add(a);
            Akcija ak = baza.aktivnaAkcija(a.getSifra(), danas);
            String popust;
            if (ak == null) {
                popust = "-";
            } else {
                popust = String.format("%.0f%%", ak.getPopustProcenat());
            }
            modelArtikli.addRow(new Object[]{
                    a.getSifra(), a.getNaziv(), Util.km(a.getCijena()), a.getStanje(), popust});
        }

        int odabrana = taboviArtikala.getSelectedIndex();
        taboviArtikala.removeAll();
        taboviArtikala.addTab("Svi artikli", mrezaArtikala(filtrirani, null, danas));
        for (Kategorija k : baza.getKategorije()) {
            if (k.getNadkategorijaId() == null) {
                taboviArtikala.addTab(k.getNaziv(), mrezaArtikala(filtrirani, k.getId(), danas));
            }
        }
        taboviArtikala.addTab("Tabela", new JScrollPane(tabelaArtikli));
        if (odabrana >= 0 && odabrana < taboviArtikala.getTabCount()) {
            taboviArtikala.setSelectedIndex(odabrana);
        }
    }

    private JScrollPane mrezaArtikala(List<Artikal> artikli, Integer glavnaKategorijaId, LocalDate danas) {
        JPanel mreza = new JPanel(new GridLayout(0, 3, 8, 8));
        mreza.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (Artikal a : artikli) {
            if (glavnaKategorijaId != null
                    && glavnaKategorija(a.getKategorijaId()) != glavnaKategorijaId) {
                continue;
            }
            mreza.add(dugmeArtikla(a, danas));
        }
        if (mreza.getComponentCount() == 0) {
            JLabel prazno = new JLabel("Nema artikala u ovoj kategoriji", SwingConstants.CENTER);
            prazno.setForeground(Color.GRAY);
            mreza.add(prazno);
        }

        // omotac prati sirinu viewporta, inace iskace horizontalni scroll
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
        omotac.add(mreza, BorderLayout.NORTH);
        JScrollPane skrol = new JScrollPane(omotac,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        skrol.getVerticalScrollBar().setUnitIncrement(16);
        skrol.setBorder(null);
        return skrol;
    }

    private JButton dugmeArtikla(Artikal a, LocalDate danas) {
        Akcija ak = baza.aktivnaAkcija(a.getSifra(), danas);
        String cijena;
        if (ak == null) {
            cijena = Util.km(a.getCijena()) + " KM";
        } else {
            cijena = "<span style='color:#38823c'>" + Util.km(a.getCijena() * (1 - ak.getPopustProcenat() / 100.0))
                    + " KM (-" + String.format("%.0f", ak.getPopustProcenat()) + "%)</span>";
        }
        JButton btn = new JButton("<html><center>" + a.getNaziv()
                + "<br>" + cijena + "</center></html>",
                new ImageIcon(Slike.artikal(a.getSifra(), a.getNaziv(), 52)));
        btn.setHorizontalTextPosition(SwingConstants.CENTER);
        btn.setVerticalTextPosition(SwingConstants.BOTTOM);
        btn.setMargin(new Insets(6, 4, 6, 4));
        btn.setToolTipText("Šifra: " + a.getSifra() + "  |  Stanje: " + a.getStanje() + " " + a.getJedinicaMjere());
        if (a.getStanje() <= 0) {
            btn.setEnabled(false);
            btn.setToolTipText("Nema na stanju");
        }
        btn.addActionListener(e -> dodajUKorpu(a.getSifra(), 1));
        return btn;
    }

    private int glavnaKategorija(int kategorijaId) {
        Kategorija k = baza.nadjiKategoriju(kategorijaId);
        int zastita = 0;
        while (k != null && k.getNadkategorijaId() != null && zastita < 10) {
            k = baza.nadjiKategoriju(k.getNadkategorijaId());
            zastita++;
        }
        if (k == null) {
            return -1;
        }
        return k.getId();
    }

    private JPanel paneKorpa() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Račun"));

        JPanel unos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        unos.add(new JLabel("Šifra artikla:"));
        unos.add(tfSifra);
        unos.add(new JLabel("Količina:"));
        unos.add(tfKolicina);
        JButton btnDodaj = UiUtil.dugme("Dodaj u korpu", "plus");
        btnDodaj.addActionListener(e -> dodajIzPolja());
        unos.add(btnDodaj);
        panel.add(unos, BorderLayout.NORTH);
        tfSifra.setToolTipText("Radi i sa barkod skenerom: skener ukuca šifru i pošalje Enter");
        tfSifra.addActionListener(e -> dodajIzPolja());
        tfKolicina.addActionListener(e -> dodajIzPolja());

        panel.add(new JScrollPane(tabelaKorpa), BorderLayout.CENTER);
        panel.add(paneNaplata(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel paneNaplata() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel dugmadKorpe = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btnUkloni = UiUtil.dugme("Ukloni stavku", "kanta");
        JButton btnIsprazni = new JButton("Isprazni korpu");
        btnUkloni.addActionListener(e -> ukloniStavku());
        btnIsprazni.addActionListener(e -> {
            korpa.clear();
            osvjeziKorpu();
        });
        dugmadKorpe.add(btnUkloni);
        dugmadKorpe.add(btnIsprazni);
        UiUtil.dodaj(panel, gbc, 0, 0, 4, dugmadKorpe);

        lUkupno.setFont(lUkupno.getFont().deriveFont(Font.BOLD, 26f));
        UiUtil.dodaj(panel, gbc, 0, 1, 2, new JLabel("UKUPNO ZA NAPLATU:"));
        UiUtil.dodaj(panel, gbc, 2, 1, 2, lUkupno);
        UiUtil.dodaj(panel, gbc, 0, 2, 2, lOsnovica);
        UiUtil.dodaj(panel, gbc, 2, 2, 2, lPdv);

        ButtonGroup grupa = new ButtonGroup();
        grupa.add(rbGotovina);
        grupa.add(rbKartica);
        JPanel placanje = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        placanje.add(new JLabel("Način plaćanja:"));
        placanje.add(rbGotovina);
        placanje.add(rbKartica);
        UiUtil.dodaj(panel, gbc, 0, 3, 4, placanje);

        JPanel gotovina = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        gotovina.add(new JLabel("Predato (KM):"));
        gotovina.add(tfPredato);
        // najcesce novcanice, klik umjesto kucanja
        for (int n : new int[]{5, 10, 20, 50}) {
            JButton brzo = new JButton(String.valueOf(n));
            brzo.setMargin(new Insets(2, 6, 2, 6));
            brzo.setToolTipText("Kupac je predao novčanicu od " + n + " KM");
            brzo.addActionListener(e -> tfPredato.setText(Util.km(n)));
            gotovina.add(brzo);
        }
        JButton btnTacno = new JButton("Tačan iznos");
        btnTacno.setMargin(new Insets(2, 6, 2, 6));
        btnTacno.setToolTipText("Kupac je predao tačan iznos računa");
        btnTacno.addActionListener(e -> tfPredato.setText(Util.km(ukupnoKorpe())));
        gotovina.add(btnTacno);
        gotovina.add(lPovrat);
        UiUtil.dodaj(panel, gbc, 0, 4, 4, gotovina);

        tfPredato.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { izracunajPovrat(); }
            public void removeUpdate(DocumentEvent e) { izracunajPovrat(); }
            public void changedUpdate(DocumentEvent e) { izracunajPovrat(); }
        });
        rbGotovina.addActionListener(e -> {
            tfPredato.setEnabled(true);
            izracunajPovrat();
        });
        rbKartica.addActionListener(e -> {
            tfPredato.setEnabled(false);
            lPovrat.setText("Povrat: 0.00 KM");
        });

        JButton btnNaplati = new JButton("NAPLATI I ŠTAMPAJ RAČUN (F2)", Ikone.ikona("stampac", 18));
        btnNaplati.setFont(btnNaplati.getFont().deriveFont(Font.BOLD, 16f));
        btnNaplati.setBackground(new Color(46, 125, 50));
        btnNaplati.setForeground(Color.WHITE);
        btnNaplati.setOpaque(true);
        btnNaplati.setIconTextGap(10);
        btnNaplati.addActionListener(e -> naplati());
        gbc.fill = GridBagConstraints.HORIZONTAL;
        UiUtil.dodaj(panel, gbc, 0, 5, 4, btnNaplati);
        UiUtil.precica(getRootPane(), "F2", btnNaplati);

        return panel;
    }

    private void dodajIzPolja() {
        try {
            String sifra = tfSifra.getText().trim();
            if (sifra.isEmpty()) {
                UiUtil.greska(this, "Unesite šifru artikla!");
                return;
            }
            int kolicina = Util.parseCijeliBroj(tfKolicina.getText(), "Količina");
            if (kolicina <= 0) {
                UiUtil.greska(this, "Količina mora biti veća od 0!");
                return;
            }
            dodajUKorpu(sifra, kolicina);
            tfSifra.setText("");
            tfKolicina.setText("1");
            tfSifra.requestFocusInWindow();
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }

    private void dodajUKorpu(String sifra, int kolicina) {
        Artikal a = baza.nadjiArtikal(sifra);
        if (a == null) {
            UiUtil.greska(this, "Artikal sa šifrom \"" + sifra + "\" ne postoji!");
            return;
        }

        StavkaRacuna postojeca = null;
        for (StavkaRacuna s : korpa) {
            if (s.getSifraArtikla().equals(a.getSifra())) {
                postojeca = s;
                break;
            }
        }
        int novaKolicina = kolicina;
        if (postojeca != null) {
            novaKolicina = novaKolicina + postojeca.getKolicina();
        }
        if (novaKolicina > a.getStanje()) {
            UiUtil.greska(this, "Nema dovoljno na stanju: " + a.getNaziv()
                    + " (stanje: " + a.getStanje() + ", traženo: " + novaKolicina + ")");
            return;
        }
        if (postojeca != null) {
            postojeca.setKolicina(novaKolicina);
        } else {
            Akcija ak = baza.aktivnaAkcija(a.getSifra(), LocalDate.now());
            double popust = 0;
            if (ak != null) {
                popust = ak.getPopustProcenat();
            }
            korpa.add(new StavkaRacuna(a.getSifra(), a.getNaziv(), kolicina, a.getCijena(), popust));
        }
        osvjeziKorpu();
    }

    private void ukloniStavku() {
        int red = tabelaKorpa.getSelectedRow();
        if (red < 0) {
            UiUtil.greska(this, "Odaberite stavku u korpi koju želite ukloniti!");
            return;
        }
        korpa.remove(red);
        osvjeziKorpu();
    }

    private double ukupnoKorpe() {
        double ukupno = 0;
        for (StavkaRacuna s : korpa) {
            ukupno = ukupno + s.iznos();
        }
        return Util.round2(ukupno);
    }

    private void osvjeziKorpu() {
        modelKorpa.setRowCount(0);
        for (StavkaRacuna s : korpa) {
            String popust;
            if (s.getPopustProcenat() > 0) {
                popust = String.format("%.0f", s.getPopustProcenat());
            } else {
                popust = "-";
            }
            modelKorpa.addRow(new Object[]{
                    s.getSifraArtikla(), s.getNazivArtikla(), s.getKolicina(),
                    Util.km(s.getCijena()), popust, Util.km(s.iznos())});
        }
        double ukupno = ukupnoKorpe();
        double pdv = Util.round2(ukupno * Racun.PDV_STOPA / (1 + Racun.PDV_STOPA));
        lUkupno.setText(Util.km(ukupno) + " KM");
        lOsnovica.setText("Osnovica: " + Util.km(ukupno - pdv) + " KM");
        lPdv.setText("PDV (17%): " + Util.km(pdv) + " KM");
        izracunajPovrat();
    }

    private void izracunajPovrat() {
        if (!rbGotovina.isSelected()) {
            lPovrat.setText("Povrat: 0.00 KM");
            return;
        }
        try {
            double predato = Util.parseBroj(tfPredato.getText(), "Predato");
            double povrat = predato - ukupnoKorpe();
            if (povrat >= 0) {
                lPovrat.setText("Povrat: " + Util.km(povrat) + " KM");
            } else {
                lPovrat.setText("Povrat: nedovoljno");
            }
        } catch (IllegalArgumentException ex) {
            lPovrat.setText("Povrat: -");
        }
    }

    private void naplati() {
        try {
            if (korpa.isEmpty()) {
                UiUtil.greska(this, "Korpa je prazna!");
                return;
            }
            String nacin;
            if (rbGotovina.isSelected()) {
                nacin = "GOTOVINA";
            } else {
                nacin = "KARTICA";
            }
            double predato = 0;
            if (rbGotovina.isSelected()) {
                predato = Util.parseBroj(tfPredato.getText(), "Predato (KM)");
            }
            Racun r = baza.izdajRacun(korisnik, korpa, nacin, predato, LocalDateTime.now());

            String porukaPdf;
            try {
                File pdf = PdfRacun.stampaj(r);
                porukaPdf = "PDF račun: " + pdf.getAbsolutePath();
            } catch (Exception ex) {
                porukaPdf = "Greška pri štampanju PDF-a: " + ex.getMessage();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Račun broj: ").append(r.getBroj()).append("\n");
            sb.append("Ukupno: ").append(Util.km(r.ukupno())).append(" KM\n");
            if ("GOTOVINA".equals(nacin)) {
                sb.append("Predato: ").append(Util.km(r.getPredato())).append(" KM\n");
                sb.append("Povrat: ").append(Util.km(r.getPovratNovca())).append(" KM\n");
            }
            sb.append("\n").append(porukaPdf);
            UiUtil.info(this, sb.toString());

            korpa.clear();
            tfPredato.setText("");
            osvjeziKorpu();
            osvjeziArtikle();
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }

    private void povratRobeDijalog() {
        PovratDijalog dijalog = new PovratDijalog();
        dijalog.setVisible(true);
    }

    private class PovratDijalog extends JDialog {

        private final JTextField tfBroj = new JTextField(12);
        private final JTextField tfKol = new JTextField("1", 4);
        private final DefaultTableModel modelStavke =
                UiUtil.model("Šifra", "Naziv", "Kupljeno", "Vraćeno", "Preostalo", "Cijena sa pop.");
        private final JTable tabelaStavke = UiUtil.tabela(modelStavke, "Učitajte račun po broju");
        private Racun ucitaniRacun = null;

        PovratDijalog() {
            super(ProdavacFrame.this, "Povrat robe", true);
            setSize(640, 420);
            setLocationRelativeTo(ProdavacFrame.this);
            setLayout(new BorderLayout(6, 6));

            JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            JButton btnUcitaj = new JButton("Učitaj račun");
            gore.add(new JLabel("Broj računa:"));
            gore.add(tfBroj);
            gore.add(btnUcitaj);
            add(gore, BorderLayout.NORTH);
            tfBroj.addActionListener(e -> ucitajRacun());

            add(new JScrollPane(tabelaStavke), BorderLayout.CENTER);

            JPanel dolje = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            JButton btnPovrat = UiUtil.dugme("Evidentiraj povrat", "povrat");
            dolje.add(new JLabel("Količina za povrat:"));
            dolje.add(tfKol);
            dolje.add(btnPovrat);
            add(dolje, BorderLayout.SOUTH);

            btnUcitaj.addActionListener(e -> ucitajRacun());
            btnPovrat.addActionListener(e -> evidentirajPovrat());
        }

        private void ucitajRacun() {
            Racun r = baza.nadjiRacun(tfBroj.getText().trim());
            if (r == null) {
                UiUtil.greska(this, "Račun nije pronađen!");
                return;
            }
            if (r.isStorniran()) {
                UiUtil.greska(this, "Račun je storniran - povrat nije moguć!");
                return;
            }
            ucitaniRacun = r;
            napuniTabelu();
        }

        private void napuniTabelu() {
            modelStavke.setRowCount(0);
            if (ucitaniRacun == null) {
                return;
            }
            for (StavkaRacuna s : ucitaniRacun.getStavke()) {
                int vraceno = baza.vracenaKolicina(ucitaniRacun.getBroj(), s.getSifraArtikla());
                modelStavke.addRow(new Object[]{
                        s.getSifraArtikla(), s.getNazivArtikla(), s.getKolicina(),
                        vraceno, s.getKolicina() - vraceno, Util.km(s.cijenaSaPopustom())});
            }
        }

        private void evidentirajPovrat() {
            try {
                if (ucitaniRacun == null) {
                    UiUtil.greska(this, "Prvo učitajte račun!");
                    return;
                }
                int red = tabelaStavke.getSelectedRow();
                if (red < 0) {
                    UiUtil.greska(this, "Odaberite stavku računa!");
                    return;
                }
                String sifra = (String) tabelaStavke.getValueAt(red, 0);
                int kolicina = Util.parseCijeliBroj(tfKol.getText(), "Količina za povrat");
                Povrat p = baza.evidentirajPovrat(ucitaniRacun, sifra, kolicina, LocalDateTime.now());
                UiUtil.info(this, "Povrat evidentiran!\nArtikal: " + p.getNazivArtikla()
                        + "\nKoličina: " + p.getKolicina()
                        + "\nVraćeni iznos: " + Util.km(p.getIznos()) + " KM");
                napuniTabelu();
                osvjeziArtikle();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        }
    }

    private void stornoRacuna() {
        String broj = JOptionPane.showInputDialog(this, "Unesite broj računa za storno:",
                "Storno računa", JOptionPane.QUESTION_MESSAGE);
        if (broj == null || broj.trim().isEmpty()) {
            return;
        }
        try {
            Racun r = baza.nadjiRacun(broj.trim());
            if (r == null) {
                UiUtil.greska(this, "Račun nije pronađen!");
                return;
            }
            if (r.isStorniran()) {
                UiUtil.greska(this, "Račun je već storniran!");
                return;
            }
            boolean potvrdjeno = UiUtil.potvrda(this, "Stornirati račun " + r.getBroj()
                    + " (iznos " + Util.km(r.ukupno()) + " KM)?\nPreostala roba se vraća na stanje.");
            if (!potvrdjeno) {
                return;
            }
            baza.stornirajRacun(r);
            UiUtil.info(this, "Račun " + r.getBroj() + " je storniran.");
            osvjeziArtikle();
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }
}
