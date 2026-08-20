package pos.ui;

import pos.data.Baza;
import pos.model.*;
import pos.util.Util;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AdminFrame extends JFrame {

    private final Baza baza = Baza.get();
    private final Korisnik korisnik;

    private final DefaultTableModel mArtikli = UiUtil.model("Šifra", "Naziv", "Kategorija", "JM", "Proizvođač", "Stanje", "Cijena (KM)", "Dobavljač");
    private final JTable tArtikli = new JTable(mArtikli);
    private final JTextField tfSifra = new JTextField(8);
    private final JTextField tfNaziv = new JTextField(16);
    private final JTextField tfJM = new JTextField(5);
    private final JTextField tfProizvodjac = new JTextField(12);
    private final JTextField tfCijena = new JTextField(7);
    private final JComboBox<Kategorija> cbKategorija = new JComboBox<>();
    private final JComboBox<Dobavljac> cbDobavljac = new JComboBox<>();
    private String odabranaSifraArtikla = null;

    private final DefaultTableModel mKategorije = UiUtil.model("ID", "Naziv", "Nadkategorija");
    private final JTable tKategorije = new JTable(mKategorije);
    private final JTextField tfKatNaziv = new JTextField(16);
    private final JComboBox<Object> cbNadkategorija = new JComboBox<>();
    private static final String GLAVNA = "— (glavna kategorija)";

    private final DefaultTableModel mDobavljaci = UiUtil.model("ID", "Naziv", "Adresa", "Telefon", "E-mail");
    private final JTable tDobavljaci = new JTable(mDobavljaci);
    private final JTextField tfDobNaziv = new JTextField(14);
    private final JTextField tfDobAdresa = new JTextField(16);
    private final JTextField tfDobTelefon = new JTextField(10);
    private final JTextField tfDobEmail = new JTextField(14);

    private final JComboBox<Dobavljac> cbNabDobavljac = new JComboBox<>();
    private final JTextField tfNabDatum = new JTextField(8);
    private final JComboBox<Artikal> cbNabArtikal = new JComboBox<>();
    private final JTextField tfNabKolicina = new JTextField(5);
    private final JTextField tfNabCijena = new JTextField(6);
    private final List<StavkaNabavke> stavkeNabavke = new ArrayList<>();
    private final DefaultTableModel mStavkeNabavke = UiUtil.model("Šifra", "Artikal", "Količina", "Nab. cijena", "Iznos");
    private final JTable tStavkeNabavke = new JTable(mStavkeNabavke);
    private final DefaultTableModel mNabavke = UiUtil.model("ID", "Datum", "Dobavljač", "Broj stavki", "Ukupno (KM)");
    private final JTable tNabavke = new JTable(mNabavke);

    private final JComboBox<Artikal> cbOtpisArtikal = new JComboBox<>();
    private final JTextField tfOtpisKolicina = new JTextField(5);
    private final JTextField tfOtpisRazlog = new JTextField(20);
    private final DefaultTableModel mOtpisi = UiUtil.model("ID", "Datum", "Šifra", "Artikal", "Količina", "Razlog");
    private final JTable tOtpisi = new JTable(mOtpisi);

    private final JComboBox<Artikal> cbAkcijaArtikal = new JComboBox<>();
    private final JTextField tfAkcijaOd = new JTextField(8);
    private final JTextField tfAkcijaDo = new JTextField(8);
    private final JTextField tfAkcijaPopust = new JTextField(5);
    private final DefaultTableModel mAkcije = UiUtil.model("ID", "Šifra", "Artikal", "Od", "Do", "Popust (%)", "Status");
    private final JTable tAkcije = new JTable(mAkcije);

    private final DefaultTableModel mKorisnici = UiUtil.model("ID", "Ime i prezime", "Korisničko ime", "Uloga");
    private final JTable tKorisnici = new JTable(mKorisnici);
    private final JTextField tfKorIme = new JTextField(14);
    private final JTextField tfKorLogin = new JTextField(10);
    private final JPasswordField tfKorLozinka = new JPasswordField(10);
    private final JComboBox<Uloga> cbUloga = new JComboBox<>(Uloga.values());

    public AdminFrame(Korisnik korisnik) {
        super("POS sistem - Administrator");
        this.korisnik = korisnik;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabovi = new JTabbedPane();
        tabovi.addTab("Artikli", tabArtikli());
        tabovi.addTab("Kategorije", tabKategorije());
        tabovi.addTab("Dobavljači", tabDobavljaci());
        tabovi.addTab("Nabavka robe", tabNabavka());
        tabovi.addTab("Otpis robe", tabOtpis());
        tabovi.addTab("Akcije i popusti", tabAkcije());
        tabovi.addTab("Korisnici", tabKorisnici());

        setLayout(new BorderLayout());
        add(UiUtil.zaglavlje("POS sistem - Administracija", korisnik, this), BorderLayout.NORTH);
        add(tabovi, BorderLayout.CENTER);

        osvjeziSve();
        setSize(1100, 680);
        setLocationRelativeTo(null);
    }

    private JPanel tabArtikli() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(tArtikli), BorderLayout.CENTER);

        cbKategorija.setRenderer(katRenderer());

        JPanel forma = new JPanel(new GridBagLayout());
        forma.setBorder(BorderFactory.createTitledBorder("Podaci o artiklu (stanje se mijenja kroz nabavku/otpis/prodaju)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        UiUtil.dodaj(forma, gbc, 0, 0, 1, new JLabel("Šifra:"));
        UiUtil.dodaj(forma, gbc, 1, 0, 1, tfSifra);
        UiUtil.dodaj(forma, gbc, 2, 0, 1, new JLabel("Naziv:"));
        UiUtil.dodaj(forma, gbc, 3, 0, 1, tfNaziv);
        UiUtil.dodaj(forma, gbc, 4, 0, 1, new JLabel("Kategorija:"));
        UiUtil.dodaj(forma, gbc, 5, 0, 1, cbKategorija);
        UiUtil.dodaj(forma, gbc, 0, 1, 1, new JLabel("Jed. mjere:"));
        UiUtil.dodaj(forma, gbc, 1, 1, 1, tfJM);
        UiUtil.dodaj(forma, gbc, 2, 1, 1, new JLabel("Proizvođač:"));
        UiUtil.dodaj(forma, gbc, 3, 1, 1, tfProizvodjac);
        UiUtil.dodaj(forma, gbc, 4, 1, 1, new JLabel("Cijena (KM):"));
        UiUtil.dodaj(forma, gbc, 5, 1, 1, tfCijena);
        UiUtil.dodaj(forma, gbc, 0, 2, 1, new JLabel("Dobavljač:"));
        UiUtil.dodaj(forma, gbc, 1, 2, 1, cbDobavljac);

        JButton btnDodaj = new JButton("Dodaj artikal");
        JButton btnIzmijeni = new JButton("Izmijeni");
        JButton btnObrisi = new JButton("Obriši");
        JButton btnOcisti = new JButton("Očisti formu");
        JPanel dugmad = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dugmad.add(btnDodaj);
        dugmad.add(btnIzmijeni);
        dugmad.add(btnObrisi);
        dugmad.add(btnOcisti);
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        forma.add(dugmad, gbc);

        panel.add(forma, BorderLayout.SOUTH);

        tArtikli.getSelectionModel().addListSelectionListener(e -> {
            int red = tArtikli.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            Artikal a = baza.nadjiArtikal((String) mArtikli.getValueAt(red, 0));
            if (a == null) {
                return;
            }
            odabranaSifraArtikla = a.getSifra();
            tfSifra.setText(a.getSifra());
            tfNaziv.setText(a.getNaziv());
            tfJM.setText(a.getJedinicaMjere());
            tfProizvodjac.setText(a.getProizvodjac());
            tfCijena.setText(Util.km(a.getCijena()));
            odaberiKategoriju(cbKategorija, a.getKategorijaId());
            odaberiDobavljaca(cbDobavljac, a.getDobavljacId());
        });

        btnDodaj.addActionListener(e -> {
            try {
                Kategorija k = (Kategorija) cbKategorija.getSelectedItem();
                Dobavljac d = (Dobavljac) cbDobavljac.getSelectedItem();
                if (k == null) {
                    throw new IllegalArgumentException("Odaberite kategoriju!");
                }
                if (d == null) {
                    throw new IllegalArgumentException("Odaberite dobavljača!");
                }
                baza.dodajArtikal(tfSifra.getText(), tfNaziv.getText(), k.getId(), tfJM.getText(),
                        tfProizvodjac.getText(), Util.parseBroj(tfCijena.getText(), "Cijena"), d.getId());
                osvjeziSve();
                ocistiFormuArtikla();
                UiUtil.info(this, "Artikal je dodat. Stanje se puni evidentiranjem nabavke.");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnIzmijeni.addActionListener(e -> {
            try {
                if (odabranaSifraArtikla == null) {
                    throw new IllegalArgumentException("Odaberite artikal u tabeli!");
                }
                Kategorija k = (Kategorija) cbKategorija.getSelectedItem();
                Dobavljac d = (Dobavljac) cbDobavljac.getSelectedItem();
                if (k == null) {
                    throw new IllegalArgumentException("Odaberite kategoriju!");
                }
                if (d == null) {
                    throw new IllegalArgumentException("Odaberite dobavljača!");
                }
                baza.izmijeniArtikal(odabranaSifraArtikla, tfSifra.getText(), tfNaziv.getText(), k.getId(),
                        tfJM.getText(), tfProizvodjac.getText(), Util.parseBroj(tfCijena.getText(), "Cijena"), d.getId());
                osvjeziSve();
                ocistiFormuArtikla();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnObrisi.addActionListener(e -> {
            try {
                if (odabranaSifraArtikla == null) {
                    throw new IllegalArgumentException("Odaberite artikal u tabeli!");
                }
                Artikal a = baza.nadjiArtikal(odabranaSifraArtikla);
                if (a == null) {
                    throw new IllegalArgumentException("Artikal nije pronađen!");
                }
                if (!UiUtil.potvrda(this, "Obrisati artikal \"" + a.getNaziv() + "\"?")) {
                    return;
                }
                baza.obrisiArtikal(odabranaSifraArtikla);
                osvjeziSve();
                ocistiFormuArtikla();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnOcisti.addActionListener(e -> ocistiFormuArtikla());
        return panel;
    }

    private void ocistiFormuArtikla() {
        odabranaSifraArtikla = null;
        tArtikli.clearSelection();
        tfSifra.setText("");
        tfNaziv.setText("");
        tfJM.setText("");
        tfProizvodjac.setText("");
        tfCijena.setText("");
    }

    private JPanel tabKategorije() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(tKategorije), BorderLayout.CENTER);

        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder("Podaci o kategoriji"));
        forma.add(new JLabel("Naziv:"));
        forma.add(tfKatNaziv);
        forma.add(new JLabel("Nadkategorija:"));
        forma.add(cbNadkategorija);
        JButton btnDodaj = new JButton("Dodaj");
        JButton btnIzmijeni = new JButton("Izmijeni");
        JButton btnObrisi = new JButton("Obriši");
        forma.add(btnDodaj);
        forma.add(btnIzmijeni);
        forma.add(btnObrisi);
        panel.add(forma, BorderLayout.SOUTH);

        tKategorije.getSelectionModel().addListSelectionListener(e -> {
            int red = tKategorije.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            Kategorija k = baza.nadjiKategoriju((Integer) mKategorije.getValueAt(red, 0));
            if (k == null) {
                return;
            }
            tfKatNaziv.setText(k.getNaziv());
            if (k.getNadkategorijaId() == null) {
                cbNadkategorija.setSelectedItem(GLAVNA);
            } else {
                for (int i = 0; i < cbNadkategorija.getItemCount(); i++) {
                    Object o = cbNadkategorija.getItemAt(i);
                    if (o instanceof Kategorija && ((Kategorija) o).getId() == k.getNadkategorijaId()) {
                        cbNadkategorija.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });

        btnDodaj.addActionListener(e -> {
            try {
                baza.dodajKategoriju(tfKatNaziv.getText(), odabranaNadkategorija());
                osvjeziSve();
                tfKatNaziv.setText("");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnIzmijeni.addActionListener(e -> {
            try {
                int red = tKategorije.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite kategoriju u tabeli!");
                }
                int id = (Integer) mKategorije.getValueAt(red, 0);
                baza.izmijeniKategoriju(id, tfKatNaziv.getText(), odabranaNadkategorija());
                osvjeziSve();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnObrisi.addActionListener(e -> {
            try {
                int red = tKategorije.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite kategoriju u tabeli!");
                }
                int id = (Integer) mKategorije.getValueAt(red, 0);
                if (!UiUtil.potvrda(this, "Obrisati odabranu kategoriju?")) {
                    return;
                }
                baza.obrisiKategoriju(id);
                osvjeziSve();
                tfKatNaziv.setText("");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });
        return panel;
    }

    private Integer odabranaNadkategorija() {
        Object o = cbNadkategorija.getSelectedItem();
        if (o instanceof Kategorija) {
            Kategorija odabrana = (Kategorija) o;
            return odabrana.getId();
        }
        return null;
    }

    private JPanel tabDobavljaci() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(tDobavljaci), BorderLayout.CENTER);

        JPanel forma = new JPanel(new GridBagLayout());
        forma.setBorder(BorderFactory.createTitledBorder("Podaci o dobavljaču"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        UiUtil.dodaj(forma, gbc, 0, 0, 1, new JLabel("Naziv:"));
        UiUtil.dodaj(forma, gbc, 1, 0, 1, tfDobNaziv);
        UiUtil.dodaj(forma, gbc, 2, 0, 1, new JLabel("Adresa:"));
        UiUtil.dodaj(forma, gbc, 3, 0, 1, tfDobAdresa);
        UiUtil.dodaj(forma, gbc, 0, 1, 1, new JLabel("Telefon:"));
        UiUtil.dodaj(forma, gbc, 1, 1, 1, tfDobTelefon);
        UiUtil.dodaj(forma, gbc, 2, 1, 1, new JLabel("E-mail:"));
        UiUtil.dodaj(forma, gbc, 3, 1, 1, tfDobEmail);

        JButton btnDodaj = new JButton("Dodaj");
        JButton btnIzmijeni = new JButton("Izmijeni");
        JButton btnObrisi = new JButton("Obriši");
        JPanel dugmad = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dugmad.add(btnDodaj);
        dugmad.add(btnIzmijeni);
        dugmad.add(btnObrisi);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        forma.add(dugmad, gbc);
        panel.add(forma, BorderLayout.SOUTH);

        tDobavljaci.getSelectionModel().addListSelectionListener(e -> {
            int red = tDobavljaci.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            Dobavljac d = baza.nadjiDobavljaca((Integer) mDobavljaci.getValueAt(red, 0));
            if (d == null) {
                return;
            }
            tfDobNaziv.setText(d.getNaziv());
            tfDobAdresa.setText(d.getAdresa());
            tfDobTelefon.setText(d.getTelefon());
            tfDobEmail.setText(d.getEmail());
        });

        btnDodaj.addActionListener(e -> {
            try {
                baza.dodajDobavljaca(tfDobNaziv.getText(), tfDobAdresa.getText(), tfDobTelefon.getText(), tfDobEmail.getText());
                osvjeziSve();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnIzmijeni.addActionListener(e -> {
            try {
                int red = tDobavljaci.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite dobavljača u tabeli!");
                }
                int id = (Integer) mDobavljaci.getValueAt(red, 0);
                baza.izmijeniDobavljaca(id, tfDobNaziv.getText(), tfDobAdresa.getText(), tfDobTelefon.getText(), tfDobEmail.getText());
                osvjeziSve();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnObrisi.addActionListener(e -> {
            try {
                int red = tDobavljaci.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite dobavljača u tabeli!");
                }
                int id = (Integer) mDobavljaci.getValueAt(red, 0);
                if (!UiUtil.potvrda(this, "Obrisati odabranog dobavljača?")) {
                    return;
                }
                baza.obrisiDobavljaca(id);
                osvjeziSve();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });
        return panel;
    }

    private JPanel tabNabavka() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel nova = new JPanel(new BorderLayout(5, 5));
        nova.setBorder(BorderFactory.createTitledBorder("Nova nabavka (evidentiranjem se stanje artikala povećava)"));

        JPanel vrh = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        vrh.add(new JLabel("Dobavljač:"));
        vrh.add(cbNabDobavljac);
        vrh.add(new JLabel("Datum (dd.MM.gggg):"));
        tfNabDatum.setText(LocalDate.now().format(Util.DATUM));
        vrh.add(tfNabDatum);
        nova.add(vrh, BorderLayout.NORTH);

        JPanel stavkaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        stavkaPanel.add(new JLabel("Artikal:"));
        stavkaPanel.add(cbNabArtikal);
        stavkaPanel.add(new JLabel("Količina:"));
        stavkaPanel.add(tfNabKolicina);
        stavkaPanel.add(new JLabel("Nabavna cijena (KM):"));
        stavkaPanel.add(tfNabCijena);
        JButton btnDodajStavku = new JButton("Dodaj stavku");
        JButton btnUkloniStavku = new JButton("Ukloni stavku");
        JButton btnEvidentiraj = new JButton("Evidentiraj nabavku");
        stavkaPanel.add(btnDodajStavku);
        stavkaPanel.add(btnUkloniStavku);
        stavkaPanel.add(btnEvidentiraj);

        JPanel sredina = new JPanel(new BorderLayout(5, 5));
        sredina.add(stavkaPanel, BorderLayout.NORTH);
        sredina.add(new JScrollPane(tStavkeNabavke), BorderLayout.CENTER);
        nova.add(sredina, BorderLayout.CENTER);

        JPanel historija = new JPanel(new BorderLayout(5, 5));
        historija.setBorder(BorderFactory.createTitledBorder("Evidentirane nabavke"));
        historija.add(new JScrollPane(tNabavke), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, nova, historija);
        split.setResizeWeight(0.55);
        panel.add(split, BorderLayout.CENTER);

        btnDodajStavku.addActionListener(e -> {
            try {
                Artikal a = (Artikal) cbNabArtikal.getSelectedItem();
                if (a == null) {
                    throw new IllegalArgumentException("Odaberite artikal!");
                }
                int kol = Util.parseCijeliBroj(tfNabKolicina.getText(), "Količina");
                if (kol <= 0) {
                    throw new IllegalArgumentException("Količina mora biti veća od 0!");
                }
                double cijena = Util.parseBroj(tfNabCijena.getText(), "Nabavna cijena");
                if (cijena <= 0) {
                    throw new IllegalArgumentException("Nabavna cijena mora biti veća od 0!");
                }
                stavkeNabavke.add(new StavkaNabavke(a.getSifra(), a.getNaziv(), kol, Util.round2(cijena)));
                osvjeziStavkeNabavke();
                tfNabKolicina.setText("");
                tfNabCijena.setText("");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnUkloniStavku.addActionListener(e -> {
            int red = tStavkeNabavke.getSelectedRow();
            if (red < 0) {
                UiUtil.greska(this, "Odaberite stavku za uklanjanje!");
                return;
            }
            stavkeNabavke.remove(red);
            osvjeziStavkeNabavke();
        });

        btnEvidentiraj.addActionListener(e -> {
            try {
                Dobavljac d = (Dobavljac) cbNabDobavljac.getSelectedItem();
                if (d == null) {
                    throw new IllegalArgumentException("Odaberite dobavljača!");
                }
                LocalDate datum = Util.parseDatum(tfNabDatum.getText());
                baza.evidentirajNabavku(d.getId(), datum, stavkeNabavke);
                stavkeNabavke.clear();
                osvjeziSve();
                UiUtil.info(this, "Nabavka je evidentirana. Stanje artikala je povećano.");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        return panel;
    }

    private JPanel tabOtpis() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder("Novi otpis (smanjuje stanje artikla)"));
        forma.add(new JLabel("Artikal:"));
        forma.add(cbOtpisArtikal);
        forma.add(new JLabel("Količina:"));
        forma.add(tfOtpisKolicina);
        forma.add(new JLabel("Razlog:"));
        forma.add(tfOtpisRazlog);
        JButton btnOtpisi = new JButton("Evidentiraj otpis");
        forma.add(btnOtpisi);

        panel.add(forma, BorderLayout.NORTH);
        panel.add(new JScrollPane(tOtpisi), BorderLayout.CENTER);

        btnOtpisi.addActionListener(e -> {
            try {
                Artikal a = (Artikal) cbOtpisArtikal.getSelectedItem();
                if (a == null) {
                    throw new IllegalArgumentException("Odaberite artikal!");
                }
                int kol = Util.parseCijeliBroj(tfOtpisKolicina.getText(), "Količina");
                baza.evidentirajOtpis(a.getSifra(), kol, tfOtpisRazlog.getText(), LocalDate.now());
                osvjeziSve();
                tfOtpisKolicina.setText("");
                tfOtpisRazlog.setText("");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });
        return panel;
    }

    private JPanel tabAkcije() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder("Nova akcija (trajanje + popust koji se obračunava pri prodaji)"));
        forma.add(new JLabel("Artikal:"));
        forma.add(cbAkcijaArtikal);
        forma.add(new JLabel("Od (dd.MM.gggg):"));
        forma.add(tfAkcijaOd);
        forma.add(new JLabel("Do (dd.MM.gggg):"));
        forma.add(tfAkcijaDo);
        forma.add(new JLabel("Popust (%):"));
        forma.add(tfAkcijaPopust);
        JButton btnDodaj = new JButton("Dodaj akciju");
        JButton btnObrisi = new JButton("Obriši akciju");
        forma.add(btnDodaj);
        forma.add(btnObrisi);

        panel.add(forma, BorderLayout.NORTH);
        panel.add(new JScrollPane(tAkcije), BorderLayout.CENTER);

        btnDodaj.addActionListener(e -> {
            try {
                Artikal a = (Artikal) cbAkcijaArtikal.getSelectedItem();
                if (a == null) {
                    throw new IllegalArgumentException("Odaberite artikal!");
                }
                LocalDate od = Util.parseDatum(tfAkcijaOd.getText());
                LocalDate doD = Util.parseDatum(tfAkcijaDo.getText());
                double popust = Util.parseBroj(tfAkcijaPopust.getText(), "Popust");
                baza.dodajAkciju(a.getSifra(), od, doD, popust);
                osvjeziSve();
                tfAkcijaPopust.setText("");
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnObrisi.addActionListener(e -> {
            try {
                int red = tAkcije.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite akciju u tabeli!");
                }
                int id = (Integer) mAkcije.getValueAt(red, 0);
                if (!UiUtil.potvrda(this, "Obrisati odabranu akciju?")) {
                    return;
                }
                baza.obrisiAkciju(id);
                osvjeziSve();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });
        return panel;
    }

    private JPanel tabKorisnici() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(tKorisnici), BorderLayout.CENTER);

        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder("Podaci o korisniku"));
        forma.add(new JLabel("Ime i prezime:"));
        forma.add(tfKorIme);
        forma.add(new JLabel("Korisničko ime:"));
        forma.add(tfKorLogin);
        forma.add(new JLabel("Šifra:"));
        forma.add(tfKorLozinka);
        forma.add(new JLabel("Uloga:"));
        forma.add(cbUloga);
        JButton btnDodaj = new JButton("Dodaj");
        JButton btnIzmijeni = new JButton("Izmijeni");
        JButton btnObrisi = new JButton("Obriši");
        forma.add(btnDodaj);
        forma.add(btnIzmijeni);
        forma.add(btnObrisi);
        panel.add(forma, BorderLayout.SOUTH);

        tKorisnici.getSelectionModel().addListSelectionListener(e -> {
            int red = tKorisnici.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            Korisnik k = baza.nadjiKorisnika((Integer) mKorisnici.getValueAt(red, 0));
            if (k == null) {
                return;
            }
            tfKorIme.setText(k.getIme());
            tfKorLogin.setText(k.getKorisnickoIme());
            tfKorLozinka.setText(k.getLozinka());
            cbUloga.setSelectedItem(k.getUloga());
        });

        btnDodaj.addActionListener(e -> {
            try {
                baza.dodajKorisnika(tfKorIme.getText(), tfKorLogin.getText(),
                        new String(tfKorLozinka.getPassword()), (Uloga) cbUloga.getSelectedItem());
                osvjeziSve();
                ocistiFormuKorisnika();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnIzmijeni.addActionListener(e -> {
            try {
                int red = tKorisnici.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite korisnika u tabeli!");
                }
                int id = (Integer) mKorisnici.getValueAt(red, 0);
                baza.izmijeniKorisnika(id, tfKorIme.getText(), tfKorLogin.getText(),
                        new String(tfKorLozinka.getPassword()), (Uloga) cbUloga.getSelectedItem());
                osvjeziSve();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnObrisi.addActionListener(e -> {
            try {
                int red = tKorisnici.getSelectedRow();
                if (red < 0) {
                    throw new IllegalArgumentException("Odaberite korisnika u tabeli!");
                }
                int id = (Integer) mKorisnici.getValueAt(red, 0);
                if (id == korisnik.getId()) {
                    throw new IllegalArgumentException("Ne možete obrisati vlastiti nalog!");
                }
                if (!UiUtil.potvrda(this, "Obrisati odabranog korisnika?")) {
                    return;
                }
                baza.obrisiKorisnika(id);
                osvjeziSve();
                ocistiFormuKorisnika();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });
        return panel;
    }

    private void ocistiFormuKorisnika() {
        tfKorIme.setText("");
        tfKorLogin.setText("");
        tfKorLozinka.setText("");
    }

    private ListCellRenderer<Object> katRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object vrijednost, int indeks,
                                                          boolean odabrano, boolean fokus) {
                super.getListCellRendererComponent(lista, vrijednost, indeks, odabrano, fokus);
                if (vrijednost instanceof Kategorija) {
                    setText(baza.putanjaKategorije(((Kategorija) vrijednost).getId()));
                }
                return this;
            }
        };
    }

    private void odaberiKategoriju(JComboBox<Kategorija> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId() == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void odaberiDobavljaca(JComboBox<Dobavljac> combo, int id) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getId() == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void osvjeziStavkeNabavke() {
        mStavkeNabavke.setRowCount(0);
        for (StavkaNabavke s : stavkeNabavke) {
            mStavkeNabavke.addRow(new Object[]{s.getSifraArtikla(), s.getNazivArtikla(),
                    s.getKolicina(), Util.km(s.getNabavnaCijena()), Util.km(s.iznos())});
        }
    }

    private void osvjeziSve() {
        mArtikli.setRowCount(0);
        for (Artikal a : baza.getArtikli()) {
            Dobavljac d = baza.nadjiDobavljaca(a.getDobavljacId());
            String nazivDobavljaca;
            if (d != null) {
                nazivDobavljaca = d.getNaziv();
            } else {
                nazivDobavljaca = "?";
            }
            mArtikli.addRow(new Object[]{a.getSifra(), a.getNaziv(), baza.putanjaKategorije(a.getKategorijaId()),
                    a.getJedinicaMjere(), a.getProizvodjac(), a.getStanje(), Util.km(a.getCijena()),
                    nazivDobavljaca});
        }

        mKategorije.setRowCount(0);
        for (Kategorija k : baza.getKategorije()) {
            String nad = "—";
            if (k.getNadkategorijaId() != null) {
                Kategorija n = baza.nadjiKategoriju(k.getNadkategorijaId());
                if (n != null) {
                    nad = n.getNaziv();
                }
            }
            mKategorije.addRow(new Object[]{k.getId(), k.getNaziv(), nad});
        }

        mDobavljaci.setRowCount(0);
        for (Dobavljac d : baza.getDobavljaci()) {
            mDobavljaci.addRow(new Object[]{d.getId(), d.getNaziv(), d.getAdresa(), d.getTelefon(), d.getEmail()});
        }

        mNabavke.setRowCount(0);
        for (Nabavka n : baza.getNabavke()) {
            Dobavljac d = baza.nadjiDobavljaca(n.getDobavljacId());
            String nazivDobavljaca;
            if (d != null) {
                nazivDobavljaca = d.getNaziv();
            } else {
                nazivDobavljaca = "?";
            }
            mNabavke.addRow(new Object[]{n.getId(), n.getDatum().format(Util.DATUM),
                    nazivDobavljaca, n.getStavke().size(), Util.km(n.ukupno())});
        }
        osvjeziStavkeNabavke();

        mOtpisi.setRowCount(0);
        for (Otpis o : baza.getOtpisi()) {
            mOtpisi.addRow(new Object[]{o.getId(), o.getDatum().format(Util.DATUM), o.getSifraArtikla(),
                    o.getNazivArtikla(), o.getKolicina(), o.getRazlog()});
        }

        mAkcije.setRowCount(0);
        LocalDate danas = LocalDate.now();
        for (Akcija a : baza.getAkcije()) {
            String status;
            if (a.aktivnaNa(danas)) {
                status = "AKTIVNA";
            } else if (danas.isBefore(a.getOdDatuma())) {
                status = "Najavljena";
            } else {
                status = "Istekla";
            }
            mAkcije.addRow(new Object[]{a.getId(), a.getSifraArtikla(), a.getNazivArtikla(),
                    a.getOdDatuma().format(Util.DATUM), a.getDoDatuma().format(Util.DATUM),
                    a.getPopustProcenat(), status});
        }

        mKorisnici.setRowCount(0);
        for (Korisnik k : baza.getKorisnici()) {
            mKorisnici.addRow(new Object[]{k.getId(), k.getIme(), k.getKorisnickoIme(), k.getUloga()});
        }

        cbKategorija.removeAllItems();
        for (Kategorija k : baza.getKategorije()) {
            cbKategorija.addItem(k);
        }

        cbNadkategorija.removeAllItems();
        cbNadkategorija.addItem(GLAVNA);
        for (Kategorija k : baza.getKategorije()) {
            if (k.getNadkategorijaId() == null) {
                cbNadkategorija.addItem(k);
            }
        }

        cbDobavljac.removeAllItems();
        cbNabDobavljac.removeAllItems();
        for (Dobavljac d : baza.getDobavljaci()) {
            cbDobavljac.addItem(d);
            cbNabDobavljac.addItem(d);
        }

        cbNabArtikal.removeAllItems();
        cbOtpisArtikal.removeAllItems();
        cbAkcijaArtikal.removeAllItems();
        for (Artikal a : baza.getArtikli()) {
            cbNabArtikal.addItem(a);
            cbOtpisArtikal.addItem(a);
            cbAkcijaArtikal.addItem(a);
        }
    }
}
