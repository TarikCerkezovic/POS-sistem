package pos.ui;

import pos.data.Baza;
import pos.data.Filteri;
import pos.model.*;
import pos.util.PdfIzvjestaj;
import pos.util.Slike;
import pos.util.Util;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminFrame extends JFrame {

    private static final int MAKS_PDF_REDOVA = 20000;

    private final Baza baza = Baza.get();
    private final Korisnik korisnik;

    // --- artikli: filteri + tabela + forma ---
    private final PoljePretrage ppFiltArtTekst = UiUtil.poljeArtikla(10);
    private final JComboBox<Object> cbFiltArtKategorija = new JComboBox<>();
    private final JComboBox<Object> cbFiltArtDobavljac = new JComboBox<>();
    private final JComboBox<Object> cbFiltArtJM = new JComboBox<>();
    private final JTextField tfFiltArtCijenaOd = new JTextField(4);
    private final JTextField tfFiltArtCijenaDo = new JTextField(4);
    private final JTextField tfFiltArtStanjeOd = new JTextField(3);
    private final JTextField tfFiltArtStanjeDo = new JTextField(3);
    private final JCheckBox chFiltArtNisko = new JCheckBox("Nisko stanje");
    private final JCheckBox chFiltArtAkcija = new JCheckBox("Na akciji");
    private final Pager pagerArtikli = new Pager(this::osvjeziArtikle);
    private final DefaultTableModel mArtikli = UiUtil.model("Šifra", "Naziv", "Kategorija", "JM", "Proizvođač", "Stanje", "Cijena (KM)", "Dobavljač");
    private final JTable tArtikli = UiUtil.tabela(mArtikli, "Nema artikala za zadani filter");
    private final JTextField tfNaziv = new JTextField(16);
    private final JComboBox<String> cbJM =
            new JComboBox<>(new String[]{"kom", "kg", "g", "l", "ml", "m", "pak"});
    private final JTextField tfProizvodjac = new JTextField(12);
    private final JTextField tfCijena = new JTextField(7);
    private final JComboBox<Kategorija> cbKategorija = new JComboBox<>();
    private final JComboBox<Dobavljac> cbDobavljac = new JComboBox<>();
    private String odabranaSifraArtikla = null;
    // slika artikla: pregled + odabrani novi fajl (snima se tek na Dodaj/Izmijeni)
    private final JLabel lSlikaPregled = new JLabel("nema slike", SwingConstants.CENTER);
    private File odabranaSlikaFajl = null;
    private boolean ukloniSliku = false;

    // --- kategorije ---
    private final PoljePretrage ppFiltKatTekst = UiUtil.poljePretrage(12,
            "Naziv kategorije ili dio pune putanje", "kategorija", "naziv");
    private final JCheckBox chFiltKatGlavne = new JCheckBox("Samo glavne");
    private final DefaultTableModel mKategorije =
            UiUtil.model("ID", "Naziv", "Nadkategorija", "Puna putanja", "Podkategorija",
                    "Artikala (direktno)", "Artikala (ukupno)");
    private final JTable tKategorije = UiUtil.tabela(mKategorije, "Nema kategorija za zadani filter");
    private final JTextField tfKatNaziv = new JTextField(16);
    private final JComboBox<Object> cbNadkategorija = new JComboBox<>();
    private static final String GLAVNA = "— (glavna kategorija)";

    // --- dobavljaci ---
    private final PoljePretrage ppFiltDobTekst = UiUtil.poljePretrage(14,
            "Naziv, adresa, telefon ili e-mail dobavljača", "dobavljac", "naziv;adresa;telefon;email");
    private final DefaultTableModel mDobavljaci = UiUtil.model("ID", "Naziv", "Adresa", "Telefon", "E-mail");
    private final JTable tDobavljaci = UiUtil.tabela(mDobavljaci, "Nema dobavljača za zadani filter");
    private final JTextField tfDobNaziv = new JTextField(14);
    private final JTextField tfDobAdresa = new JTextField(16);
    private final JTextField tfDobTelefon = new JTextField(10);
    private final JTextField tfDobEmail = new JTextField(14);

    // --- nabavke ---
    private final JComboBox<Dobavljac> cbNabDobavljac = new JComboBox<>();
    private final BiracDatuma bdNabDatum = new BiracDatuma(LocalDate.now());
    private final PretragaArtikla paNabArtikal = new PretragaArtikla();
    private final JTextField tfNabKolicina = new JTextField(5);
    private final JTextField tfNabCijena = new JTextField(6);
    private final List<StavkaNabavke> stavkeNabavke = new ArrayList<>();
    private final DefaultTableModel mStavkeNabavke = UiUtil.model("Šifra", "Artikal", "Količina", "Nab. cijena", "Iznos");
    private final JTable tStavkeNabavke = new JTable(mStavkeNabavke);
    private final BiracDatuma bdFiltNabOd = new BiracDatuma(null);
    private final BiracDatuma bdFiltNabDo = new BiracDatuma(null);
    private final JComboBox<Object> cbFiltNabDobavljac = new JComboBox<>();
    private final PoljePretrage ppFiltNabTekst = UiUtil.poljeArtikla(9);
    private final JTextField tfFiltNabIznosOd = new JTextField(4);
    private final JTextField tfFiltNabIznosDo = new JTextField(4);
    private final Pager pagerNabavke = new Pager(this::osvjeziNabavke);
    private final DefaultTableModel mNabavke = UiUtil.model("ID", "Datum", "Dobavljač", "Broj stavki", "Ukupno (KM)");
    private final JTable tNabavke = UiUtil.tabela(mNabavke, "Nema nabavki za zadani filter");

    // --- otpisi ---
    private final PretragaArtikla paOtpisArtikal = new PretragaArtikla();
    private final JTextField tfOtpisKolicina = new JTextField(5);
    private final JTextField tfOtpisRazlog = new JTextField(20);
    private final BiracDatuma bdFiltOtpOd = new BiracDatuma(null);
    private final BiracDatuma bdFiltOtpDo = new BiracDatuma(null);
    private final PoljePretrage ppFiltOtpTekst = UiUtil.poljeArtikla(9);
    private final PoljePretrage ppFiltOtpRazlog = UiUtil.poljePretrage(9,
            "Razlog otpisa - ponude se razlozi koji su već korišteni", "otpis", "razlog");
    private final JTextField tfFiltOtpKolOd = new JTextField(3);
    private final JTextField tfFiltOtpKolDo = new JTextField(3);
    private final Pager pagerOtpisi = new Pager(this::osvjeziOtpise);
    private final DefaultTableModel mOtpisi = UiUtil.model("ID", "Datum", "Šifra", "Artikal", "Količina", "Razlog");
    private final JTable tOtpisi = UiUtil.tabela(mOtpisi, "Nema otpisa za zadani filter");

    // --- akcije: na jedan artikal ili na cijelu kategoriju (sa podkategorijama) ---
    private final JRadioButton rbAkcNaArtikal = new JRadioButton("Na artikal:", true);
    private final JRadioButton rbAkcNaKategoriju = new JRadioButton("Na kategoriju:");
    private final PretragaArtikla paAkcijaArtikal = new PretragaArtikla();
    private final JComboBox<Object> cbAkcKategorija = new JComboBox<>();
    private final BiracDatuma bdAkcijaOd = new BiracDatuma(null);
    private final BiracDatuma bdAkcijaDo = new BiracDatuma(null);
    private final JTextField tfAkcijaPopust = new JTextField(5);
    private final PoljePretrage ppFiltAkcTekst = UiUtil.poljePretrage(9,
            "Šifra ili naziv artikla, ili naziv kategorije na akciji",
            "artikal", "sifra;naziv");
    private final JComboBox<String> cbFiltAkcStatus =
            new JComboBox<>(new String[]{"— Svi statusi —", "Aktivne", "Najavljene", "Istekle"});
    private final JTextField tfFiltAkcPopustOd = new JTextField(3);
    private final JTextField tfFiltAkcPopustDo = new JTextField(3);
    private final BiracDatuma bdFiltAkcOd = new BiracDatuma(null);
    private final BiracDatuma bdFiltAkcDo = new BiracDatuma(null);
    private final Pager pagerAkcije = new Pager(this::osvjeziAkcije);
    private final DefaultTableModel mAkcije = UiUtil.model("ID", "Odnosi se na", "Od", "Do", "Popust (%)", "Status");
    private final JTable tAkcije = UiUtil.tabela(mAkcije, "Nema akcija za zadani filter");

    // --- korisnici ---
    private final PoljePretrage ppFiltKorTekst = UiUtil.poljePretrage(12,
            "Ime i prezime ili korisničko ime", "korisnik", "ime;korisnicko_ime");
    private final JComboBox<String> cbFiltKorUloga =
            new JComboBox<>(new String[]{"— Sve uloge —", "Administrator", "Prodavač", "Menadžer"});
    private final DefaultTableModel mKorisnici = UiUtil.model("ID", "Ime i prezime", "Korisničko ime", "Uloga");
    private final JTable tKorisnici = UiUtil.tabela(mKorisnici, "Nema korisnika za zadani filter");
    private final JTextField tfKorIme = new JTextField(14);
    private final JTextField tfKorLogin = new JTextField(10);
    private final JPasswordField tfKorLozinka = new JPasswordField(10);
    private final JComboBox<Uloga> cbUloga = new JComboBox<>(Uloga.values());

    // redovi filtera se pamte zbog dugmadi za brze periode
    private RedFiltera redNabavki;
    private RedFiltera redOtpisa;
    private RedFiltera redAkcija;

    // menadzerski pogledi ugradjeni kod administratora, da odmah vidi efekte izmjena
    private final IzvjestajiPanel izvjestaji;
    private final StatistikaPanel statistika = new StatistikaPanel();

    public AdminFrame(Korisnik korisnik) {
        super("POS sistem - Administrator");
        this.korisnik = korisnik;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UiUtil.ikonaProzora(this);
        izvjestaji = new IzvjestajiPanel(this);

        JTabbedPane tabovi = new JTabbedPane();
        tabovi.addTab("Artikli", tabArtikli());
        tabovi.addTab("Kategorije", tabKategorije());
        tabovi.addTab("Dobavljači", tabDobavljaci());
        tabovi.addTab("Nabavka robe", tabNabavka());
        tabovi.addTab("Otpis robe", tabOtpis());
        tabovi.addTab("Akcije i popusti", tabAkcije());
        tabovi.addTab("Korisnici", tabKorisnici());
        tabovi.addTab("Izvještaji", izvjestaji);
        tabovi.addTab("Statistika", statistika);

        // izvjestaji i statistika se preracunaju kad se otvori njihov tab
        tabovi.addChangeListener(e -> {
            Component odabran = tabovi.getSelectedComponent();
            if (odabran == izvjestaji) {
                izvjestaji.osvjeziSifrarnike();
                izvjestaji.osvjeziSveTiho();
            } else if (odabran == statistika) {
                statistika.osvjezi();
            }
        });

        // da se moze kucat u combo boxove
        UiUtil.pretraziv(cbKategorija);
        UiUtil.pretraziv(cbDobavljac);
        UiUtil.pretraziv(cbNadkategorija);
        UiUtil.pretraziv(cbNabDobavljac);
        UiUtil.pretraziv(cbAkcKategorija);
        UiUtil.pretraziv(cbFiltArtKategorija);
        UiUtil.pretraziv(cbFiltArtDobavljac);
        UiUtil.pretraziv(cbFiltNabDobavljac);
        UiUtil.pretraziv(cbFiltArtJM);

        setLayout(new BorderLayout());
        add(UiUtil.zaglavlje("POS sistem - Administracija", korisnik, this), BorderLayout.NORTH);
        add(tabovi, BorderLayout.CENTER);

        osvjeziSve();
        setSize(1100, 680);
        setLocationRelativeTo(null);
        // radni prozori idu preko cijelog ekrana (prijava ostaje mala)
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    // red sa filterima + dugmad; komponente idu redom: tekst oznake pa polja.
    // RedFiltera sam osluskuje polja, pa se tabela osvjezava i dok se kuca
    private RedFiltera filterRed(Runnable prikazi, Runnable ponisti, Runnable pdf, Object... komponente) {
        return new RedFiltera("Filteri", "Filtriraj", prikazi, ponisti, pdf, komponente);
    }

    // dok se kuca, greske ispisuje sam red filtera - ne dijalog
    private void greskaFiltera(String poruka) {
        if (!RedFiltera.uzivo()) {
            UiUtil.greska(this, poruka);
        }
    }

    // ==================== TAB: ARTIKLI ====================

    private JPanel tabArtikli() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(filterRed(() -> {
            pagerArtikli.naPrvu();
            osvjeziArtikle();
        }, () -> {
            ppFiltArtTekst.ocisti();
            cbFiltArtKategorija.setSelectedIndex(0);
            cbFiltArtDobavljac.setSelectedIndex(0);
            cbFiltArtJM.setSelectedIndex(0);
            tfFiltArtCijenaOd.setText("");
            tfFiltArtCijenaDo.setText("");
            tfFiltArtStanjeOd.setText("");
            tfFiltArtStanjeDo.setText("");
            chFiltArtNisko.setSelected(false);
            chFiltArtAkcija.setSelected(false);
            pagerArtikli.naPrvu();
            osvjeziArtikle();
        }, this::pdfArtikli,
                "Pretraga:", ppFiltArtTekst, "Kategorija:", cbFiltArtKategorija,
                "Dobavljač:", cbFiltArtDobavljac, "JM:", cbFiltArtJM,
                "Cijena:", tfFiltArtCijenaOd, "-", tfFiltArtCijenaDo,
                "Stanje:", tfFiltArtStanjeOd, "-", tfFiltArtStanjeDo,
                chFiltArtNisko, chFiltArtAkcija), BorderLayout.NORTH);

        JPanel sredina = new JPanel(new BorderLayout(4, 4));
        sredina.add(new JScrollPane(tArtikli), BorderLayout.CENTER);
        sredina.add(pagerArtikli, BorderLayout.SOUTH);
        panel.add(sredina, BorderLayout.CENTER);

        cbKategorija.setRenderer(katRenderer());

        JPanel forma = new JPanel(new GridBagLayout());
        forma.setBorder(BorderFactory.createTitledBorder("Podaci o artiklu (stanje se mijenja kroz nabavku/otpis/prodaju)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        UiUtil.dodaj(forma, gbc, 0, 0, 1, new JLabel("Naziv:"));
        UiUtil.dodaj(forma, gbc, 1, 0, 1, tfNaziv);
        UiUtil.dodaj(forma, gbc, 2, 0, 1, new JLabel("Kategorija:"));
        UiUtil.dodaj(forma, gbc, 3, 0, 1, cbKategorija);
        UiUtil.dodaj(forma, gbc, 4, 0, 1, new JLabel("Jed. mjere:"));
        UiUtil.dodaj(forma, gbc, 5, 0, 1, cbJM);
        UiUtil.dodaj(forma, gbc, 0, 1, 1, new JLabel("Proizvođač:"));
        UiUtil.dodaj(forma, gbc, 1, 1, 1, tfProizvodjac);
        UiUtil.dodaj(forma, gbc, 2, 1, 1, new JLabel("Cijena (KM):"));
        UiUtil.dodaj(forma, gbc, 3, 1, 1, tfCijena);
        UiUtil.dodaj(forma, gbc, 4, 1, 1, new JLabel("Dobavljač:"));
        UiUtil.dodaj(forma, gbc, 5, 1, 1, cbDobavljac);

        // slika artikla: pregled desno od forme + dugmad za izbor/uklanjanje
        lSlikaPregled.setPreferredSize(new Dimension(72, 72));
        lSlikaPregled.setBorder(BorderFactory.createLineBorder(new Color(0xc3c2b7)));
        lSlikaPregled.setForeground(Color.GRAY);
        JButton btnSlika = new JButton("Odaberi sliku...");
        JButton btnUkloniSliku = new JButton("Ukloni sliku");
        JPanel slikaPanel = new JPanel(new BorderLayout(4, 4));
        slikaPanel.add(lSlikaPregled, BorderLayout.CENTER);
        JPanel slikaDugmad = new JPanel(new GridLayout(2, 1, 2, 2));
        slikaDugmad.add(btnSlika);
        slikaDugmad.add(btnUkloniSliku);
        slikaPanel.add(slikaDugmad, BorderLayout.EAST);
        gbc.gridx = 6;
        gbc.gridy = 0;
        gbc.gridheight = 3;
        gbc.gridwidth = 1;
        forma.add(slikaPanel, gbc);
        gbc.gridheight = 1;

        btnSlika.addActionListener(e -> odaberiSliku());
        btnUkloniSliku.addActionListener(e -> {
            odabranaSlikaFajl = null;
            ukloniSliku = true;
            lSlikaPregled.setIcon(null);
            lSlikaPregled.setText("nema slike");
        });

        JButton btnDodaj = UiUtil.dugme("Dodaj artikal", "plus");
        JButton btnIzmijeni = UiUtil.dugme("Izmijeni", "olovka");
        JButton btnObrisi = UiUtil.dugme("Obriši", "kanta");
        JButton btnOcisti = new JButton("Očisti formu");
        JPanel dugmad = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dugmad.add(btnDodaj);
        dugmad.add(btnIzmijeni);
        dugmad.add(btnObrisi);
        dugmad.add(btnOcisti);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 6;
        forma.add(dugmad, gbc);

        panel.add(forma, BorderLayout.SOUTH);

        tArtikli.getSelectionModel().addListSelectionListener(e -> {
            int red = tArtikli.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            // preko tabele a ne modela, zbog sortiranja
            Artikal a = baza.nadjiArtikal((String) tArtikli.getValueAt(red, 0));
            if (a == null) {
                return;
            }
            odabranaSifraArtikla = a.getSifra();
            tfNaziv.setText(a.getNaziv());
            odaberiJM(a.getJedinicaMjere());
            tfProizvodjac.setText(a.getProizvodjac());
            tfCijena.setText(Util.km(a.getCijena()));
            odaberiKategoriju(cbKategorija, a.getKategorijaId());
            odaberiDobavljaca(cbDobavljac, a.getDobavljacId());
            odabranaSlikaFajl = null;
            ukloniSliku = false;
            prikaziPostojecuSliku(a.getSifra());
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
                Artikal novi = baza.dodajArtikal(tfNaziv.getText(), k.getId(), odabranoJM(),
                        tfProizvodjac.getText(), Util.parseBroj(tfCijena.getText(), "Cijena"), d.getId());
                snimiSlikuAkoTreba(novi.getSifra());
                osvjeziSve();
                ocistiFormuArtikla();
                UiUtil.info(this, "Artikal \"" + novi.getNaziv()
                        + "\" je dodat.\nStanje se puni evidentiranjem nabavke.");
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
                baza.izmijeniArtikal(odabranaSifraArtikla, odabranaSifraArtikla, tfNaziv.getText(), k.getId(),
                        odabranoJM(), tfProizvodjac.getText(), Util.parseBroj(tfCijena.getText(), "Cijena"), d.getId());
                if (ukloniSliku) {
                    Slike.obrisiSliku(odabranaSifraArtikla);
                }
                snimiSlikuAkoTreba(odabranaSifraArtikla);
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
                String pitanje = "Obrisati artikal \"" + a.getNaziv() + "\"?";
                if (baza.artikalImaProdaje(a.getSifra())) {
                    pitanje = pitanje + "\n\nPAŽNJA: artikal se nalazi na postojećim računima."
                            + "\nHistorija prodaje ostaje, ali se za te račune stanje ovog artikla"
                            + "\nviše neće moći vratiti kroz povrat ili storno.";
                }
                if (!UiUtil.potvrda(this, pitanje)) {
                    return;
                }
                baza.obrisiArtikal(odabranaSifraArtikla);
                Slike.obrisiSliku(odabranaSifraArtikla);
                osvjeziSve();
                ocistiFormuArtikla();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });

        btnOcisti.addActionListener(e -> ocistiFormuArtikla());
        return panel;
    }

    private String odabranoJM() {
        Object jm = cbJM.getSelectedItem();
        if (jm == null) {
            return "kom";
        }
        return (String) jm;
    }

    // postavi jedinicu mjere u combo; nepoznata (stara rucno unesena) se doda u listu
    private void odaberiJM(String jm) {
        if (jm == null || jm.trim().isEmpty()) {
            cbJM.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < cbJM.getItemCount(); i++) {
            if (cbJM.getItemAt(i).equals(jm)) {
                cbJM.setSelectedIndex(i);
                return;
            }
        }
        cbJM.addItem(jm);
        cbJM.setSelectedItem(jm);
    }

    private void odaberiSliku() {
        JFileChooser birac = new JFileChooser();
        birac.setDialogTitle("Odaberite sliku artikla");
        birac.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Slike (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (birac.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File fajl = birac.getSelectedFile();
        try {
            java.awt.image.BufferedImage slika = javax.imageio.ImageIO.read(fajl);
            if (slika == null) {
                throw new IllegalArgumentException("Odabrani fajl nije slika koju je moguće učitati!");
            }
            odabranaSlikaFajl = fajl;
            ukloniSliku = false;
            prikaziPregled(slika);
        } catch (Exception ex) {
            UiUtil.greska(this, "Nije moguće učitati sliku: " + ex.getMessage());
        }
    }

    private void prikaziPregled(Image slika) {
        // uklopi u kvadrat pregleda bez razvlacenja
        int vel = 68;
        double omjer = Math.min((double) vel / slika.getWidth(null), (double) vel / slika.getHeight(null));
        int sirina = Math.max(1, (int) (slika.getWidth(null) * omjer));
        int visina = Math.max(1, (int) (slika.getHeight(null) * omjer));
        lSlikaPregled.setText("");
        lSlikaPregled.setIcon(new ImageIcon(slika.getScaledInstance(sirina, visina, Image.SCALE_SMOOTH)));
    }

    private void prikaziPostojecuSliku(String sifra) {
        if (Slike.imaSliku(sifra)) {
            lSlikaPregled.setText("");
            lSlikaPregled.setIcon(new ImageIcon(Slike.artikal(sifra, "", 68)));
        } else {
            lSlikaPregled.setIcon(null);
            lSlikaPregled.setText("nema slike");
        }
    }

    private void snimiSlikuAkoTreba(String sifra) {
        if (odabranaSlikaFajl == null) {
            return;
        }
        try {
            Slike.snimiSliku(sifra, odabranaSlikaFajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Artikal je snimljen, ali slika nije: " + ex.getMessage());
        }
    }

    private Filteri.FilterArtikala filterArtikala() {
        Filteri.FilterArtikala f = new Filteri.FilterArtikala();
        f.tekst = ppFiltArtTekst.getText();
        f.kategorijaId = UiUtil.odabranaKategorija(cbFiltArtKategorija);
        Object dob = cbFiltArtDobavljac.getSelectedItem();
        if (dob instanceof Dobavljac) {
            f.dobavljacId = ((Dobavljac) dob).getId();
        }
        if (cbFiltArtJM.getSelectedIndex() > 0) {
            f.jedinicaMjere = String.valueOf(cbFiltArtJM.getSelectedItem());
        }
        f.cijenaOd = Util.parseBrojOpcioni(tfFiltArtCijenaOd.getText(), "Cijena od");
        f.cijenaDo = Util.parseBrojOpcioni(tfFiltArtCijenaDo.getText(), "Cijena do");
        f.stanjeOd = Util.parseCijeliBrojOpcioni(tfFiltArtStanjeOd.getText(), "Stanje od");
        f.stanjeDo = Util.parseCijeliBrojOpcioni(tfFiltArtStanjeDo.getText(), "Stanje do");
        f.samoNiskoStanje = chFiltArtNisko.isSelected();
        f.samoNaAkciji = chFiltArtAkcija.isSelected();
        return f;
    }

    private void osvjeziArtikle() {
        try {
            Filteri.FilterArtikala f = filterArtikala();
            pagerArtikli.postaviUkupno(baza.brojArtikalaFiltrirano(f));
            List<Artikal> lista = baza.artikliFiltrirano(f, pagerArtikli.pomak(), pagerArtikli.limit());
            Map<Integer, String> dobavljaci = mapaDobavljaca();
            mArtikli.setRowCount(0);
            for (Artikal a : lista) {
                String nazivDobavljaca = dobavljaci.get(a.getDobavljacId());
                if (nazivDobavljaca == null) {
                    nazivDobavljaca = "?";
                }
                mArtikli.addRow(new Object[]{a.getSifra(), a.getNaziv(), baza.putanjaKategorije(a.getKategorijaId()),
                        a.getJedinicaMjere(), a.getProizvodjac(), a.getStanje(), Util.km(a.getCijena()),
                        nazivDobavljaca});
            }
        } catch (IllegalArgumentException ex) {
            greskaFiltera(ex.getMessage());
        }
    }

    private void pdfArtikli() {
        try {
            Filteri.FilterArtikala f = filterArtikala();
            long ukupno = baza.brojArtikalaFiltrirano(f);
            Map<Integer, String> dobavljaci = mapaDobavljaca();
            List<Object[]> redovi = new ArrayList<>();
            for (Artikal a : baza.artikliFiltrirano(f, 0, MAKS_PDF_REDOVA)) {
                String nazivDobavljaca = dobavljaci.get(a.getDobavljacId());
                if (nazivDobavljaca == null) {
                    nazivDobavljaca = "?";
                }
                redovi.add(new Object[]{a.getSifra(), a.getNaziv(), baza.putanjaKategorije(a.getKategorijaId()),
                        a.getJedinicaMjere(), a.getProizvodjac(), a.getStanje(), Util.km(a.getCijena()),
                        nazivDobavljaca});
            }
            File fajl = PdfIzvjestaj.izvezi("Šifrarnik artikala", opisPdf(ukupno, redovi.size()),
                    new String[]{"Šifra", "Naziv", "Kategorija", "JM", "Proizvođač", "Stanje",
                            "Cijena (KM)", "Dobavljač"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    private String opisPdf(long ukupno, int uIzvjestaju) {
        if (ukupno > uIzvjestaju) {
            return "Redova koji odgovaraju filteru: " + ukupno + " (izvezeno prvih " + uIzvjestaju + ")";
        }
        return "Redova koji odgovaraju filteru: " + ukupno;
    }

    private Map<Integer, String> mapaDobavljaca() {
        Map<Integer, String> mapa = new HashMap<>();
        for (Dobavljac d : baza.getDobavljaci()) {
            mapa.put(d.getId(), d.getNaziv());
        }
        return mapa;
    }

    private void ocistiFormuArtikla() {
        odabranaSifraArtikla = null;
        tArtikli.clearSelection();
        tfNaziv.setText("");
        cbJM.setSelectedIndex(0);
        tfProizvodjac.setText("");
        tfCijena.setText("");
        odabranaSlikaFajl = null;
        ukloniSliku = false;
        lSlikaPregled.setIcon(null);
        lSlikaPregled.setText("nema slike");
    }

    // ==================== TAB: KATEGORIJE ====================

    private JPanel tabKategorije() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(filterRed(this::osvjeziKategorije, () -> {
            ppFiltKatTekst.ocisti();
            chFiltKatGlavne.setSelected(false);
            osvjeziKategorije();
        }, this::pdfKategorije,
                "Pretraga (naziv ili putanja):", ppFiltKatTekst, chFiltKatGlavne), BorderLayout.NORTH);

        panel.add(new JScrollPane(tKategorije), BorderLayout.CENTER);

        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder(
                "Podaci o kategoriji (nadkategorija može biti bilo koja kategorija - dubina nije ograničena)"));
        forma.add(new JLabel("Naziv:"));
        forma.add(tfKatNaziv);
        forma.add(new JLabel("Nadkategorija:"));
        forma.add(cbNadkategorija);
        JButton btnDodaj = UiUtil.dugme("Dodaj", "plus");
        JButton btnIzmijeni = UiUtil.dugme("Izmijeni", "olovka");
        JButton btnObrisi = UiUtil.dugme("Obriši", "kanta");
        JButton btnOcisti = new JButton("Očisti formu");
        forma.add(btnDodaj);
        forma.add(btnIzmijeni);
        forma.add(btnObrisi);
        forma.add(btnOcisti);
        panel.add(forma, BorderLayout.SOUTH);

        tKategorije.getSelectionModel().addListSelectionListener(e -> {
            int red = tKategorije.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            Kategorija k = baza.nadjiKategoriju((Integer) tKategorije.getValueAt(red, 0));
            if (k == null) {
                return;
            }
            tfKatNaziv.setText(k.getNaziv());
            // ponuda nadkategorija ostaje puna: "Dodaj" smije napraviti podkategoriju
            // bas ispod odabrane, a ciklus kod "Izmijeni" svakako odbija baza
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
                int id = (Integer) tKategorije.getValueAt(red, 0);
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
                int id = (Integer) tKategorije.getValueAt(red, 0);
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

        btnOcisti.addActionListener(e -> {
            tKategorije.clearSelection();
            tfKatNaziv.setText("");
            cbNadkategorija.setSelectedItem(GLAVNA);
        });
        return panel;
    }

    private Integer odabranaNadkategorija() {
        Object o = cbNadkategorija.getSelectedItem();
        if (o instanceof Kategorija) {
            return ((Kategorija) o).getId();
        }
        return null;
    }

    // u ponudi su sve kategorije, prikazane punom putanjom i sortirane po njoj
    private void napuniNadkategorije() {
        Object odabrano = cbNadkategorija.getSelectedItem();
        cbNadkategorija.removeAllItems();
        cbNadkategorija.addItem(GLAVNA);
        List<Kategorija> kategorije = new ArrayList<>(baza.getKategorije());
        kategorije.sort((a, b) -> baza.putanjaKategorije(a.getId())
                .compareToIgnoreCase(baza.putanjaKategorije(b.getId())));
        for (Kategorija k : kategorije) {
            cbNadkategorija.addItem(k);
        }
        cbNadkategorija.setRenderer(katRenderer());
        if (odabrano instanceof Kategorija) {
            for (int i = 0; i < cbNadkategorija.getItemCount(); i++) {
                Object o = cbNadkategorija.getItemAt(i);
                if (o instanceof Kategorija
                        && ((Kategorija) o).getId() == ((Kategorija) odabrano).getId()) {
                    cbNadkategorija.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void osvjeziKategorije() {
        Filteri.FilterKategorija f = new Filteri.FilterKategorija();
        f.tekst = ppFiltKatTekst.getText();
        if (chFiltKatGlavne.isSelected()) {
            f.samoGlavne = true;
        }
        Map<Integer, Long> brojArtikala = baza.brojArtikalaPoKategoriji();
        List<Kategorija> sve = baza.getKategorije();
        mKategorije.setRowCount(0);
        for (Kategorija k : baza.kategorijeFiltrirano(f)) {
            String nad = "—";
            if (k.getNadkategorijaId() != null) {
                Kategorija n = baza.nadjiKategoriju(k.getNadkategorijaId());
                if (n != null) {
                    nad = n.getNaziv();
                }
            }
            int podkategorija = 0;
            for (Kategorija druga : sve) {
                if (druga.getNadkategorijaId() != null && druga.getNadkategorijaId() == k.getId()) {
                    podkategorija = podkategorija + 1;
                }
            }
            Long artikala = brojArtikala.get(k.getId());
            if (artikala == null) {
                artikala = 0L;
            }
            // ukupno = i artikli iz svih podkategorija, do bilo koje dubine
            long ukupnoArtikala = 0;
            for (Integer idPodstabla : baza.kategorijaSaPodstablom(k.getId())) {
                Long uPodstablu = brojArtikala.get(idPodstabla);
                if (uPodstablu != null) {
                    ukupnoArtikala = ukupnoArtikala + uPodstablu;
                }
            }
            mKategorije.addRow(new Object[]{k.getId(), k.getNaziv(), nad,
                    baza.putanjaKategorije(k.getId()), podkategorija, artikala, ukupnoArtikala});
        }
    }

    private void pdfKategorije() {
        try {
            List<Object[]> redovi = new ArrayList<>();
            for (int i = 0; i < mKategorije.getRowCount(); i++) {
                Object[] red = new Object[mKategorije.getColumnCount()];
                for (int j = 0; j < red.length; j++) {
                    red[j] = mKategorije.getValueAt(i, j);
                }
                redovi.add(red);
            }
            File fajl = PdfIzvjestaj.izvezi("Šifrarnik kategorija", "Redova: " + redovi.size(),
                    new String[]{"ID", "Naziv", "Nadkategorija", "Puna putanja", "Podkategorija",
                            "Artikala (direktno)", "Artikala (ukupno)"},
                    redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ==================== TAB: DOBAVLJACI ====================

    private JPanel tabDobavljaci() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(filterRed(this::osvjeziDobavljace, () -> {
            ppFiltDobTekst.ocisti();
            osvjeziDobavljace();
        }, this::pdfDobavljaci,
                "Pretraga (naziv, adresa, telefon, e-mail):", ppFiltDobTekst), BorderLayout.NORTH);

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

        JButton btnDodaj = UiUtil.dugme("Dodaj", "plus");
        JButton btnIzmijeni = UiUtil.dugme("Izmijeni", "olovka");
        JButton btnObrisi = UiUtil.dugme("Obriši", "kanta");
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
            Dobavljac d = baza.nadjiDobavljaca((Integer) tDobavljaci.getValueAt(red, 0));
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
                int id = (Integer) tDobavljaci.getValueAt(red, 0);
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
                int id = (Integer) tDobavljaci.getValueAt(red, 0);
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

    private void osvjeziDobavljace() {
        Filteri.FilterDobavljaca f = new Filteri.FilterDobavljaca();
        f.tekst = ppFiltDobTekst.getText();
        mDobavljaci.setRowCount(0);
        for (Dobavljac d : baza.dobavljaciFiltrirano(f)) {
            mDobavljaci.addRow(new Object[]{d.getId(), d.getNaziv(), d.getAdresa(), d.getTelefon(), d.getEmail()});
        }
    }

    private void pdfDobavljaci() {
        try {
            List<Object[]> redovi = new ArrayList<>();
            for (int i = 0; i < mDobavljaci.getRowCount(); i++) {
                Object[] red = new Object[mDobavljaci.getColumnCount()];
                for (int j = 0; j < red.length; j++) {
                    red[j] = mDobavljaci.getValueAt(i, j);
                }
                redovi.add(red);
            }
            File fajl = PdfIzvjestaj.izvezi("Šifrarnik dobavljača", "Redova: " + redovi.size(),
                    new String[]{"ID", "Naziv", "Adresa", "Telefon", "E-mail"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ==================== TAB: NABAVKA ====================

    private JPanel tabNabavka() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel nova = new JPanel(new BorderLayout(5, 5));
        nova.setBorder(BorderFactory.createTitledBorder("Nova nabavka (evidentiranjem se stanje artikala povećava)"));

        JPanel vrh = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        vrh.add(new JLabel("Dobavljač:"));
        vrh.add(cbNabDobavljac);
        vrh.add(new JLabel("Datum:"));
        vrh.add(bdNabDatum);
        nova.add(vrh, BorderLayout.NORTH);

        JPanel stavkaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        stavkaPanel.add(new JLabel("Artikal:"));
        stavkaPanel.add(paNabArtikal);
        stavkaPanel.add(new JLabel("Količina:"));
        stavkaPanel.add(tfNabKolicina);
        stavkaPanel.add(new JLabel("Nabavna cijena (KM):"));
        stavkaPanel.add(tfNabCijena);
        JButton btnDodajStavku = UiUtil.dugme("Dodaj stavku", "plus");
        JButton btnUkloniStavku = UiUtil.dugme("Ukloni stavku", "kanta");
        JButton btnEvidentiraj = UiUtil.dugme("Evidentiraj nabavku", "kutija");
        stavkaPanel.add(btnDodajStavku);
        stavkaPanel.add(btnUkloniStavku);
        stavkaPanel.add(btnEvidentiraj);

        JPanel sredina = new JPanel(new BorderLayout(5, 5));
        sredina.add(stavkaPanel, BorderLayout.NORTH);
        sredina.add(new JScrollPane(tStavkeNabavke), BorderLayout.CENTER);
        nova.add(sredina, BorderLayout.CENTER);

        JPanel historija = new JPanel(new BorderLayout(5, 5));
        historija.setBorder(BorderFactory.createTitledBorder("Evidentirane nabavke (dupli klik za stavke)"));
        JPanel gornjiRedovi = RedFiltera.uspravnoSlaganje();
        gornjiRedovi.add(RedFiltera.brziPeriodi(bdFiltNabOd, bdFiltNabDo, () -> {
            pagerNabavke.naPrvu();
            redNabavki.primijeni();
        }));
        redNabavki = filterRed(() -> {
            pagerNabavke.naPrvu();
            osvjeziNabavke();
        }, () -> {
            bdFiltNabOd.postaviDatum(null);
            bdFiltNabDo.postaviDatum(null);
            cbFiltNabDobavljac.setSelectedIndex(0);
            ppFiltNabTekst.ocisti();
            tfFiltNabIznosOd.setText("");
            tfFiltNabIznosDo.setText("");
            pagerNabavke.naPrvu();
            osvjeziNabavke();
        }, this::pdfNabavke,
                "Od:", bdFiltNabOd, "Do:", bdFiltNabDo, "Dobavljač:", cbFiltNabDobavljac,
                "Artikal:", ppFiltNabTekst, "Iznos:", tfFiltNabIznosOd, "-", tfFiltNabIznosDo);
        gornjiRedovi.add(redNabavki);
        historija.add(gornjiRedovi, BorderLayout.NORTH);
        historija.add(new JScrollPane(tNabavke), BorderLayout.CENTER);
        historija.add(pagerNabavke, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, nova, historija);
        split.setResizeWeight(0.45);
        panel.add(split, BorderLayout.CENTER);

        tNabavke.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int red = tNabavke.getSelectedRow();
                    if (red >= 0) {
                        prikaziStavkeNabavke((Integer) tNabavke.getValueAt(red, 0));
                    }
                }
            }
        });

        // izborom artikla se kolicina predlozi na 1, a cijena sa zadnje nabavke
        paNabArtikal.naOdabir(a -> {
            tfNabKolicina.setText("1");
            Double zadnja = baza.zadnjaNabavnaCijena(a.getSifra());
            if (zadnja == null) {
                tfNabCijena.setText("");
            } else {
                tfNabCijena.setText(Util.km(zadnja));
            }
            tfNabKolicina.requestFocusInWindow();
            tfNabKolicina.selectAll();
        });
        // Enter vodi kroz formu: kolicina -> cijena -> dodavanje stavke
        tfNabKolicina.addActionListener(e -> {
            tfNabCijena.requestFocusInWindow();
            tfNabCijena.selectAll();
        });
        tfNabCijena.addActionListener(e -> btnDodajStavku.doClick());

        btnDodajStavku.addActionListener(e -> {
            try {
                Artikal a = paNabArtikal.odabraniObavezan();
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
                // odmah spremno za sljedecu stavku
                paNabArtikal.ocisti();
                paNabArtikal.requestFocus();
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
                LocalDate datum = bdNabDatum.getDatumObavezan("Datum nabavke");
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

    private void prikaziStavkeNabavke(int nabavkaId) {
        Nabavka n = baza.nadjiNabavku(nabavkaId);
        if (n == null) {
            UiUtil.greska(this, "Nabavka nije pronađena!");
            return;
        }
        DefaultTableModel model = UiUtil.model("Šifra", "Artikal", "Količina", "Nab. cijena (KM)", "Iznos (KM)");
        for (StavkaNabavke s : n.getStavke()) {
            model.addRow(new Object[]{s.getSifraArtikla(), s.getNazivArtikla(), s.getKolicina(),
                    Util.km(s.getNabavnaCijena()), Util.km(s.iznos())});
        }
        JTable tabela = UiUtil.tabela(model, "Nabavka nema stavki");
        JDialog dijalog = new JDialog(this, "Stavke nabavke #" + nabavkaId
                + " (" + n.getDatum().format(Util.DATUM) + ")", true);
        dijalog.setSize(560, 360);
        dijalog.setLocationRelativeTo(this);
        dijalog.setLayout(new BorderLayout(6, 6));
        dijalog.add(new JScrollPane(tabela), BorderLayout.CENTER);
        JLabel lUkupno = new JLabel("Ukupno: " + Util.km(n.ukupno()) + " KM");
        lUkupno.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        lUkupno.setFont(lUkupno.getFont().deriveFont(Font.BOLD, 13f));
        dijalog.add(lUkupno, BorderLayout.SOUTH);
        dijalog.setVisible(true);
    }

    private Filteri.FilterNabavki filterNabavki() {
        Filteri.FilterNabavki f = new Filteri.FilterNabavki();
        f.od = bdFiltNabOd.getDatum();
        f.doD = bdFiltNabDo.getDatum();
        Object dob = cbFiltNabDobavljac.getSelectedItem();
        if (dob instanceof Dobavljac) {
            f.dobavljacId = ((Dobavljac) dob).getId();
        }
        f.tekstArtikla = ppFiltNabTekst.getText();
        f.iznosOd = Util.parseBrojOpcioni(tfFiltNabIznosOd.getText(), "Iznos od");
        f.iznosDo = Util.parseBrojOpcioni(tfFiltNabIznosDo.getText(), "Iznos do");
        return f;
    }

    private void osvjeziNabavke() {
        try {
            Filteri.FilterNabavki f = filterNabavki();
            pagerNabavke.postaviUkupno(baza.brojNabavkiFiltrirano(f));
            mNabavke.setRowCount(0);
            for (Object[] red : baza.nabavkeFiltrirano(f, pagerNabavke.pomak(), pagerNabavke.limit())) {
                mNabavke.addRow(new Object[]{(int) ((Number) red[0]).longValue(),
                        LocalDate.parse((String) red[1]).format(Util.DATUM), red[2],
                        ((Number) red[3]).longValue(), Util.km(((Number) red[4]).doubleValue())});
            }
        } catch (IllegalArgumentException ex) {
            greskaFiltera(ex.getMessage());
        }
    }

    private void pdfNabavke() {
        try {
            Filteri.FilterNabavki f = filterNabavki();
            long ukupno = baza.brojNabavkiFiltrirano(f);
            List<Object[]> redovi = new ArrayList<>();
            for (Object[] red : baza.nabavkeFiltrirano(f, 0, MAKS_PDF_REDOVA)) {
                redovi.add(new Object[]{((Number) red[0]).longValue(),
                        LocalDate.parse((String) red[1]).format(Util.DATUM), red[2],
                        ((Number) red[3]).longValue(), Util.km(((Number) red[4]).doubleValue())});
            }
            File fajl = PdfIzvjestaj.izvezi("Evidencija nabavki", opisPdf(ukupno, redovi.size()),
                    new String[]{"ID", "Datum", "Dobavljač", "Broj stavki", "Ukupno (KM)"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ==================== TAB: OTPIS ====================

    private JPanel tabOtpis() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = RedFiltera.uspravnoSlaganje();
        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder("Novi otpis (smanjuje stanje artikla)"));
        forma.add(new JLabel("Artikal:"));
        forma.add(paOtpisArtikal);
        forma.add(new JLabel("Količina:"));
        forma.add(tfOtpisKolicina);
        forma.add(new JLabel("Razlog:"));
        forma.add(tfOtpisRazlog);
        JButton btnOtpisi = UiUtil.dugme("Evidentiraj otpis", "otpis");
        forma.add(btnOtpisi);
        gore.add(forma);
        gore.add(RedFiltera.brziPeriodi(bdFiltOtpOd, bdFiltOtpDo, () -> {
            pagerOtpisi.naPrvu();
            redOtpisa.primijeni();
        }));
        redOtpisa = filterRed(() -> {
            pagerOtpisi.naPrvu();
            osvjeziOtpise();
        }, () -> {
            bdFiltOtpOd.postaviDatum(null);
            bdFiltOtpDo.postaviDatum(null);
            ppFiltOtpTekst.ocisti();
            ppFiltOtpRazlog.ocisti();
            tfFiltOtpKolOd.setText("");
            tfFiltOtpKolDo.setText("");
            pagerOtpisi.naPrvu();
            osvjeziOtpise();
        }, this::pdfOtpisi,
                "Od:", bdFiltOtpOd, "Do:", bdFiltOtpDo, "Artikal:", ppFiltOtpTekst,
                "Razlog:", ppFiltOtpRazlog, "Količina:", tfFiltOtpKolOd, "-", tfFiltOtpKolDo);
        gore.add(redOtpisa);
        panel.add(gore, BorderLayout.NORTH);

        JPanel sredina = new JPanel(new BorderLayout(4, 4));
        sredina.add(new JScrollPane(tOtpisi), BorderLayout.CENTER);
        sredina.add(pagerOtpisi, BorderLayout.SOUTH);
        panel.add(sredina, BorderLayout.CENTER);

        // izbor artikla vodi na kolicinu, Enter dalje na razlog i na evidentiranje
        paOtpisArtikal.naOdabir(a -> {
            tfOtpisKolicina.setText("1");
            tfOtpisKolicina.requestFocusInWindow();
            tfOtpisKolicina.selectAll();
        });
        tfOtpisKolicina.addActionListener(e -> {
            tfOtpisRazlog.requestFocusInWindow();
            tfOtpisRazlog.selectAll();
        });
        tfOtpisRazlog.addActionListener(e -> btnOtpisi.doClick());

        btnOtpisi.addActionListener(e -> {
            try {
                Artikal a = paOtpisArtikal.odabraniObavezan();
                int kol = Util.parseCijeliBroj(tfOtpisKolicina.getText(), "Količina");
                baza.evidentirajOtpis(a.getSifra(), kol, tfOtpisRazlog.getText(), LocalDate.now());
                osvjeziSve();
                paOtpisArtikal.ocisti();
                tfOtpisKolicina.setText("");
                tfOtpisRazlog.setText("");
                paOtpisArtikal.requestFocus();
            } catch (IllegalArgumentException ex) {
                UiUtil.greska(this, ex.getMessage());
            }
        });
        return panel;
    }

    private Filteri.FilterOtpisa filterOtpisa() {
        Filteri.FilterOtpisa f = new Filteri.FilterOtpisa();
        f.od = bdFiltOtpOd.getDatum();
        f.doD = bdFiltOtpDo.getDatum();
        f.tekst = ppFiltOtpTekst.getText();
        f.razlog = ppFiltOtpRazlog.getText();
        f.kolicinaOd = Util.parseCijeliBrojOpcioni(tfFiltOtpKolOd.getText(), "Količina od");
        f.kolicinaDo = Util.parseCijeliBrojOpcioni(tfFiltOtpKolDo.getText(), "Količina do");
        return f;
    }

    private void osvjeziOtpise() {
        try {
            Filteri.FilterOtpisa f = filterOtpisa();
            pagerOtpisi.postaviUkupno(baza.brojOtpisaFiltrirano(f));
            mOtpisi.setRowCount(0);
            for (Otpis o : baza.otpisiFiltrirano(f, pagerOtpisi.pomak(), pagerOtpisi.limit())) {
                mOtpisi.addRow(new Object[]{o.getId(), o.getDatum().format(Util.DATUM), o.getSifraArtikla(),
                        o.getNazivArtikla(), o.getKolicina(), o.getRazlog()});
            }
        } catch (IllegalArgumentException ex) {
            greskaFiltera(ex.getMessage());
        }
    }

    private void pdfOtpisi() {
        try {
            Filteri.FilterOtpisa f = filterOtpisa();
            long ukupno = baza.brojOtpisaFiltrirano(f);
            List<Object[]> redovi = new ArrayList<>();
            for (Otpis o : baza.otpisiFiltrirano(f, 0, MAKS_PDF_REDOVA)) {
                redovi.add(new Object[]{o.getId(), o.getDatum().format(Util.DATUM), o.getSifraArtikla(),
                        o.getNazivArtikla(), o.getKolicina(), o.getRazlog()});
            }
            File fajl = PdfIzvjestaj.izvezi("Evidencija otpisa", opisPdf(ukupno, redovi.size()),
                    new String[]{"ID", "Datum", "Šifra", "Artikal", "Količina", "Razlog"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ==================== TAB: AKCIJE ====================

    private JPanel tabAkcije() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = RedFiltera.uspravnoSlaganje();
        JPanel forma = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        forma.setBorder(BorderFactory.createTitledBorder(
                "Nova akcija - na jedan artikal ili na cijelu kategoriju (vrijedi i za podkategorije; "
                + "artikal sa vlastitom akcijom zadržava nju)"));
        ButtonGroup grupaCilja = new ButtonGroup();
        grupaCilja.add(rbAkcNaArtikal);
        grupaCilja.add(rbAkcNaKategoriju);
        forma.add(rbAkcNaArtikal);
        forma.add(paAkcijaArtikal);
        forma.add(rbAkcNaKategoriju);
        forma.add(cbAkcKategorija);
        forma.add(new JLabel("Od:"));
        forma.add(bdAkcijaOd);
        forma.add(new JLabel("Do:"));
        forma.add(bdAkcijaDo);
        forma.add(new JLabel("Popust (%):"));
        forma.add(tfAkcijaPopust);
        JButton btnDodaj = UiUtil.dugme("Dodaj akciju", "plus");
        JButton btnObrisi = UiUtil.dugme("Obriši akciju", "kanta");
        forma.add(btnDodaj);
        forma.add(btnObrisi);
        gore.add(forma);

        // aktivan je samo birac koji odgovara odabranoj vrsti akcije
        cbAkcKategorija.setEnabled(false);
        rbAkcNaArtikal.addActionListener(e -> {
            paAkcijaArtikal.setEnabled(true);
            cbAkcKategorija.setEnabled(false);
        });
        rbAkcNaKategoriju.addActionListener(e -> {
            paAkcijaArtikal.setEnabled(false);
            cbAkcKategorija.setEnabled(true);
        });
        gore.add(RedFiltera.brziPeriodi(bdFiltAkcOd, bdFiltAkcDo, () -> {
            pagerAkcije.naPrvu();
            redAkcija.primijeni();
        }));
        redAkcija = filterRed(() -> {
            pagerAkcije.naPrvu();
            osvjeziAkcije();
        }, () -> {
            ppFiltAkcTekst.ocisti();
            cbFiltAkcStatus.setSelectedIndex(0);
            tfFiltAkcPopustOd.setText("");
            tfFiltAkcPopustDo.setText("");
            bdFiltAkcOd.postaviDatum(null);
            bdFiltAkcDo.postaviDatum(null);
            pagerAkcije.naPrvu();
            osvjeziAkcije();
        }, this::pdfAkcije,
                "Artikal/kategorija:", ppFiltAkcTekst, "Status:", cbFiltAkcStatus,
                "Popust (%):", tfFiltAkcPopustOd, "-", tfFiltAkcPopustDo,
                "U periodu od:", bdFiltAkcOd, "do:", bdFiltAkcDo);
        gore.add(redAkcija);
        panel.add(gore, BorderLayout.NORTH);

        JPanel sredina = new JPanel(new BorderLayout(4, 4));
        sredina.add(new JScrollPane(tAkcije), BorderLayout.CENTER);
        sredina.add(pagerAkcije, BorderLayout.SOUTH);
        panel.add(sredina, BorderLayout.CENTER);

        btnDodaj.addActionListener(e -> {
            try {
                LocalDate od = bdAkcijaOd.getDatumObavezan("Od");
                LocalDate doD = bdAkcijaDo.getDatumObavezan("Do");
                double popust = Util.parseBroj(tfAkcijaPopust.getText(), "Popust");
                if (rbAkcNaArtikal.isSelected()) {
                    Artikal a = paAkcijaArtikal.odabraniObavezan();
                    baza.dodajAkciju(a.getSifra(), od, doD, popust);
                } else {
                    Integer kategorijaId = UiUtil.odabranaKategorija(cbAkcKategorija);
                    if (kategorijaId == null) {
                        throw new IllegalArgumentException("Odaberite kategoriju za akciju!");
                    }
                    baza.dodajAkcijuNaKategoriju(kategorijaId, od, doD, popust);
                }
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
                int id = (Integer) tAkcije.getValueAt(red, 0);
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

    private Filteri.FilterAkcija filterAkcija() {
        Filteri.FilterAkcija f = new Filteri.FilterAkcija();
        f.tekst = ppFiltAkcTekst.getText();
        int status = cbFiltAkcStatus.getSelectedIndex();
        if (status == 1) {
            f.status = "AKTIVNA";
        } else if (status == 2) {
            f.status = "NAJAVLJENA";
        } else if (status == 3) {
            f.status = "ISTEKLA";
        }
        f.popustOd = Util.parseBrojOpcioni(tfFiltAkcPopustOd.getText(), "Popust od");
        f.popustDo = Util.parseBrojOpcioni(tfFiltAkcPopustDo.getText(), "Popust do");
        f.od = bdFiltAkcOd.getDatum();
        f.doD = bdFiltAkcDo.getDatum();
        return f;
    }

    // red iz akcijeFiltrirano: {id, sifra artikla, naziv artikla, kategorija_id, od, do, popust}
    private Object[] redAkcije(Object[] red) {
        LocalDate od = LocalDate.parse((String) red[4]);
        LocalDate doD = LocalDate.parse((String) red[5]);
        LocalDate danas = LocalDate.now();
        String status;
        if (!danas.isBefore(od) && !danas.isAfter(doD)) {
            status = "AKTIVNA";
        } else if (danas.isBefore(od)) {
            status = "Najavljena";
        } else {
            status = "Istekla";
        }
        String cilj;
        if (red[1] != null) {
            String naziv = "?";
            if (red[2] != null) {
                naziv = (String) red[2];
            }
            cilj = "Artikal: " + red[1] + " - " + naziv;
        } else if (red[3] != null) {
            cilj = "Kategorija: " + baza.putanjaKategorije((int) ((Number) red[3]).longValue());
        } else {
            cilj = "?";
        }
        return new Object[]{(int) ((Number) red[0]).longValue(), cilj,
                od.format(Util.DATUM), doD.format(Util.DATUM),
                ((Number) red[6]).doubleValue(), status};
    }

    private void osvjeziAkcije() {
        try {
            Filteri.FilterAkcija f = filterAkcija();
            pagerAkcije.postaviUkupno(baza.brojAkcijaFiltrirano(f));
            mAkcije.setRowCount(0);
            for (Object[] red : baza.akcijeFiltrirano(f, pagerAkcije.pomak(), pagerAkcije.limit())) {
                mAkcije.addRow(redAkcije(red));
            }
        } catch (IllegalArgumentException ex) {
            greskaFiltera(ex.getMessage());
        }
    }

    private void pdfAkcije() {
        try {
            Filteri.FilterAkcija f = filterAkcija();
            long ukupno = baza.brojAkcijaFiltrirano(f);
            List<Object[]> redovi = new ArrayList<>();
            for (Object[] red : baza.akcijeFiltrirano(f, 0, MAKS_PDF_REDOVA)) {
                redovi.add(redAkcije(red));
            }
            File fajl = PdfIzvjestaj.izvezi("Akcije i popusti", opisPdf(ukupno, redovi.size()),
                    new String[]{"ID", "Odnosi se na", "Od", "Do", "Popust (%)", "Status"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ==================== TAB: KORISNICI ====================

    private JPanel tabKorisnici() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        panel.add(filterRed(this::osvjeziKorisnike, () -> {
            ppFiltKorTekst.ocisti();
            cbFiltKorUloga.setSelectedIndex(0);
            osvjeziKorisnike();
        }, this::pdfKorisnici,
                "Pretraga (ime ili korisničko ime):", ppFiltKorTekst, "Uloga:", cbFiltKorUloga),
                BorderLayout.NORTH);

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
        JButton btnDodaj = UiUtil.dugme("Dodaj", "plus");
        JButton btnIzmijeni = UiUtil.dugme("Izmijeni", "olovka");
        JButton btnObrisi = UiUtil.dugme("Obriši", "kanta");
        forma.add(btnDodaj);
        forma.add(btnIzmijeni);
        forma.add(btnObrisi);
        panel.add(forma, BorderLayout.SOUTH);

        tKorisnici.getSelectionModel().addListSelectionListener(e -> {
            int red = tKorisnici.getSelectedRow();
            if (e.getValueIsAdjusting() || red < 0) {
                return;
            }
            Korisnik k = baza.nadjiKorisnika((Integer) tKorisnici.getValueAt(red, 0));
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
                int id = (Integer) tKorisnici.getValueAt(red, 0);
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
                int id = (Integer) tKorisnici.getValueAt(red, 0);
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

    private void osvjeziKorisnike() {
        Filteri.FilterKorisnika f = new Filteri.FilterKorisnika();
        f.tekst = ppFiltKorTekst.getText();
        int uloga = cbFiltKorUloga.getSelectedIndex();
        if (uloga == 1) {
            f.uloga = Uloga.ADMINISTRATOR.name();
        } else if (uloga == 2) {
            f.uloga = Uloga.PRODAVAC.name();
        } else if (uloga == 3) {
            f.uloga = Uloga.MENADZER.name();
        }
        mKorisnici.setRowCount(0);
        for (Korisnik k : baza.korisniciFiltrirano(f)) {
            mKorisnici.addRow(new Object[]{k.getId(), k.getIme(), k.getKorisnickoIme(), k.getUloga()});
        }
    }

    private void pdfKorisnici() {
        try {
            List<Object[]> redovi = new ArrayList<>();
            for (int i = 0; i < mKorisnici.getRowCount(); i++) {
                redovi.add(new Object[]{mKorisnici.getValueAt(i, 0), mKorisnici.getValueAt(i, 1),
                        mKorisnici.getValueAt(i, 2), String.valueOf(mKorisnici.getValueAt(i, 3))});
            }
            File fajl = PdfIzvjestaj.izvezi("Korisnici sistema", "Redova: " + redovi.size(),
                    new String[]{"ID", "Ime i prezime", "Korisničko ime", "Uloga"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    private void ocistiFormuKorisnika() {
        tfKorIme.setText("");
        tfKorLogin.setText("");
        tfKorLozinka.setText("");
    }

    // ==================== zajednicko ====================

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
        osvjeziArtikle();
        osvjeziKategorije();
        osvjeziDobavljace();
        osvjeziNabavke();
        osvjeziStavkeNabavke();
        osvjeziOtpise();
        osvjeziAkcije();
        osvjeziKorisnike();
        // punjenje combo boxova ne smije pokrenuti novo osvjezavanje tabela
        RedFiltera.tiho(this::napuniCombe);
        izvjestaji.osvjeziSifrarnike();
    }

    private void napuniCombe() {
        // kategorije: forma za artikal + filter artikala
        List<Kategorija> kategorije = new ArrayList<>(baza.getKategorije());
        kategorije.sort((a, b) -> baza.putanjaKategorije(a.getId())
                .compareToIgnoreCase(baza.putanjaKategorije(b.getId())));
        Kategorija odabranaKat = (Kategorija) cbKategorija.getSelectedItem();
        cbKategorija.removeAllItems();
        for (Kategorija k : kategorije) {
            cbKategorija.addItem(k);
        }
        if (odabranaKat != null) {
            odaberiKategoriju(cbKategorija, odabranaKat.getId());
        }
        UiUtil.napuniKategorijePutanje(cbFiltArtKategorija, baza, "— Sve kategorije —");

        // u filteru se nude samo jedinice mjere koje se stvarno koriste
        Object odabranaJM = cbFiltArtJM.getSelectedItem();
        cbFiltArtJM.removeAllItems();
        cbFiltArtJM.addItem("— Sve JM —");
        for (String jm : baza.jediniceMjere()) {
            cbFiltArtJM.addItem(jm);
        }
        if (odabranaJM != null) {
            cbFiltArtJM.setSelectedItem(odabranaJM);
        }

        napuniNadkategorije();

        // dobavljaci - izbor korisnika se cuva da osvjezavanje ne poremeti
        // npr. nabavku koja je na pola sastavljanja
        Dobavljac odabranDob = (Dobavljac) cbDobavljac.getSelectedItem();
        Dobavljac odabranNabDob = (Dobavljac) cbNabDobavljac.getSelectedItem();
        cbDobavljac.removeAllItems();
        cbNabDobavljac.removeAllItems();
        Object odabranFiltDob = cbFiltNabDobavljac.getSelectedItem();
        cbFiltNabDobavljac.removeAllItems();
        cbFiltNabDobavljac.addItem("— Svi dobavljači —");
        Object odabranFiltArtDob = cbFiltArtDobavljac.getSelectedItem();
        cbFiltArtDobavljac.removeAllItems();
        cbFiltArtDobavljac.addItem("— Svi dobavljači —");
        for (Dobavljac d : baza.getDobavljaci()) {
            cbDobavljac.addItem(d);
            cbNabDobavljac.addItem(d);
            cbFiltNabDobavljac.addItem(d);
            cbFiltArtDobavljac.addItem(d);
        }
        if (odabranDob != null) {
            odaberiDobavljaca(cbDobavljac, odabranDob.getId());
        }
        if (odabranNabDob != null) {
            odaberiDobavljaca(cbNabDobavljac, odabranNabDob.getId());
        }
        vratiIzborDobavljaca(cbFiltNabDobavljac, odabranFiltDob);
        vratiIzborDobavljaca(cbFiltArtDobavljac, odabranFiltArtDob);

        // artikal za nabavku/otpis/akciju se bira pretragom (PretragaArtikla),
        // pa se ovdje puni jos samo combo kategorija za akcije
        UiUtil.napuniKategorijePutanje(cbAkcKategorija, baza, null);
    }

    private void vratiIzborDobavljaca(JComboBox<Object> combo, Object odabran) {
        if (odabran instanceof Dobavljac) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object o = combo.getItemAt(i);
                if (o instanceof Dobavljac && ((Dobavljac) o).getId() == ((Dobavljac) odabran).getId()) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

}
