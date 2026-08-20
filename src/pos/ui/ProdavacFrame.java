package pos.ui;

import pos.data.Baza;
import pos.model.*;
import pos.util.Util;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProdavacFrame extends JFrame {

    private final Baza baza = Baza.get();
    private final Korisnik korisnik;

    private final List<StavkaRacuna> korpa = new ArrayList<>();

    private final JTextField tfPretraga = new JTextField();
    private final DefaultTableModel modelArtikli =
            UiUtil.model("Šifra", "Naziv", "Cijena (KM)", "Stanje", "Popust");
    private final JTable tabelaArtikli = new JTable(modelArtikli);

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
        add(zaglavlje, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                paneArtikli(), paneKorpa());
        split.setResizeWeight(0.5);
        split.setDividerLocation(560);
        add(split, BorderLayout.CENTER);

        osvjeziArtikle();
    }

    private JPanel paneArtikli() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Artikli (dupli klik dodaje u korpu)"));

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
                        dodajUKorpu((String) modelArtikli.getValueAt(red, 0), 1);
                    }
                }
            }
        });
        panel.add(new JScrollPane(tabelaArtikli), BorderLayout.CENTER);
        return panel;
    }

    private void osvjeziArtikle() {
        String filter = tfPretraga.getText().trim().toLowerCase();
        modelArtikli.setRowCount(0);
        LocalDate danas = LocalDate.now();
        for (Artikal a : baza.getArtikli()) {
            if (!filter.isEmpty()
                    && !a.getNaziv().toLowerCase().contains(filter)
                    && !a.getSifra().toLowerCase().contains(filter)) {
                continue;
            }
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
    }

    private JPanel paneKorpa() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Račun"));

        JPanel unos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        unos.add(new JLabel("Šifra artikla:"));
        unos.add(tfSifra);
        unos.add(new JLabel("Količina:"));
        unos.add(tfKolicina);
        JButton btnDodaj = new JButton("Dodaj u korpu");
        btnDodaj.addActionListener(e -> dodajIzPolja());
        unos.add(btnDodaj);
        panel.add(unos, BorderLayout.NORTH);
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
        JButton btnUkloni = new JButton("Ukloni stavku");
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

        JButton btnNaplati = new JButton("NAPLATI I ŠTAMPAJ RAČUN");
        btnNaplati.setFont(btnNaplati.getFont().deriveFont(Font.BOLD, 16f));
        btnNaplati.setBackground(new Color(46, 125, 50));
        btnNaplati.setForeground(Color.WHITE);
        btnNaplati.setOpaque(true);
        btnNaplati.addActionListener(e -> naplati());
        gbc.fill = GridBagConstraints.HORIZONTAL;
        UiUtil.dodaj(panel, gbc, 0, 5, 4, btnNaplati);

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


            StringBuilder sb = new StringBuilder();
            sb.append("Račun broj: ").append(r.getBroj()).append("\n");
            sb.append("Ukupno: ").append(Util.km(r.ukupno())).append(" KM\n");
            if ("GOTOVINA".equals(nacin)) {
                sb.append("Predato: ").append(Util.km(r.getPredato())).append(" KM\n");
                sb.append("Povrat: ").append(Util.km(r.getPovratNovca())).append(" KM\n");
            }
            UiUtil.info(this, sb.toString());

            korpa.clear();
            tfPredato.setText("");
            osvjeziKorpu();
            osvjeziArtikle();
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }
}
