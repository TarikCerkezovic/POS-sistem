package pos.ui;

import pos.data.Baza;
import pos.data.Filteri;
import pos.model.*;
import pos.util.PdfIzvjestaj;
import pos.util.Util;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
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
import java.util.Set;

// menadzerski izvjestaji sa filterima, stranicenjem i izvozom u PDF.
// koristi ga MenadzerFrame, a ugradjuje se i u AdminFrame da administrator
// odmah vidi efekte svojih izmjena bez posebne prijave menadzera.
public class IzvjestajiPanel extends JPanel {

    // izvoz u PDF je ogranicen da izvjestaj ne naraste na stotine hiljada strana
    private static final int MAKS_PDF_REDOVA = 20000;

    private final Baza baza = Baza.get();
    private final Window prozor;
    private boolean tihoOsvjezavanje = false;
    // redovi filtera se pamte zbog dugmadi za brze periode
    private RedFiltera redRacuna;
    private RedFiltera redProdavaca;
    private RedFiltera redPovrata;
    private RedFiltera redNabavki;
    private RedFiltera redTop;

    // --- tab: racuni / promet ---
    private final BiracDatuma bdRacOd = new BiracDatuma(LocalDate.now().withDayOfMonth(1));
    private final BiracDatuma bdRacDo = new BiracDatuma(LocalDate.now());
    private final PoljePretrage ppRacBroj = UiUtil.poljePretrage(9,
            "Broj računa - prijedlozi iskaču dok kucate", "racun", "broj");
    private final JComboBox<String> cbRacProdavac = new JComboBox<>();
    private final JComboBox<String> cbRacPlacanje =
            new JComboBox<>(new String[]{"— Sva plaćanja —", "GOTOVINA", "KARTICA"});
    private final JComboBox<String> cbRacStatus =
            new JComboBox<>(new String[]{"— Svi statusi —", "Izdati", "Stornirani"});
    private final JTextField tfRacIznosOd = new JTextField(5);
    private final JTextField tfRacIznosDo = new JTextField(5);
    private final DefaultTableModel mRacuni =
            UiUtil.model("Broj računa", "Vrijeme", "Prodavač", "Plaćanje", "Iznos (KM)", "Vraćeno (KM)", "Status");
    private final JTable tRacuni = UiUtil.tabela(mRacuni, "Nema računa za zadani filter");
    private final Pager pagerRacuni = new Pager(this::osvjeziRacune);
    private final JLabel lRacuni = new JLabel(" ");

    // --- tab: promet po prodavacu ---
    private final BiracDatuma bdProdOd = new BiracDatuma(LocalDate.now().withDayOfMonth(1));
    private final BiracDatuma bdProdDo = new BiracDatuma(LocalDate.now());
    private final DefaultTableModel mProdavaci = UiUtil.model("Prodavač", "Broj računa", "Neto promet (KM)");
    private final JTable tProdavaci = UiUtil.tabela(mProdavaci, "Nema prometa u odabranom periodu");

    // --- tab: povrati ---
    private final BiracDatuma bdPovOd = new BiracDatuma(LocalDate.now().withDayOfMonth(1));
    private final BiracDatuma bdPovDo = new BiracDatuma(LocalDate.now());
    private final PoljePretrage ppPovTekst = UiUtil.poljePretrage(10,
            "Šifra ili naziv vraćenog artikla", "povrat", "sifra_artikla;naziv_artikla");
    private final PoljePretrage ppPovBroj = UiUtil.poljePretrage(9,
            "Broj računa na kojem je bilo povrata", "povrat", "broj_racuna");
    private final JComboBox<String> cbPovProdavac = new JComboBox<>();
    private final DefaultTableModel mPovrati =
            UiUtil.model("Vrijeme", "Broj računa", "Šifra", "Artikal", "Količina", "Iznos (KM)", "Napomena");
    private final JTable tPovrati = UiUtil.tabela(mPovrati, "Nema povrata za zadani filter");
    private final Pager pagerPovrati = new Pager(this::osvjeziPovrate);
    private final JLabel lPovrati = new JLabel(" ");

    // --- tab: nabavke ---
    private final BiracDatuma bdNabOd = new BiracDatuma(null);
    private final BiracDatuma bdNabDo = new BiracDatuma(null);
    private final JComboBox<Object> cbNabDobavljac = new JComboBox<>();
    private final PoljePretrage ppNabTekst = UiUtil.poljeArtikla(10);
    private final DefaultTableModel mNabavke =
            UiUtil.model("Datum", "Dobavljač", "Šifra", "Artikal", "Količina", "Nab. cijena (KM)", "Iznos (KM)");
    private final JTable tNabavke = UiUtil.tabela(mNabavke, "Nema nabavki za zadani filter");
    private final Pager pagerNabavke = new Pager(this::osvjeziNabavke);
    private final JLabel lNabavke = new JLabel(" ");

    // --- tab: zalihe ---
    private final PoljePretrage ppZalTekst = UiUtil.poljeArtikla(10);
    private final JComboBox<Object> cbZalKategorija = new JComboBox<>();
    private final JTextField tfZalStanjeOd = new JTextField(4);
    private final JTextField tfZalStanjeDo = new JTextField(4);
    private final JCheckBox chZalNisko = new JCheckBox("Samo nisko stanje (< 10)");
    private final DefaultTableModel mZalihe =
            UiUtil.model("Šifra", "Naziv", "Kategorija", "JM", "Stanje", "Cijena (KM)", "Vrijednost (KM)", "Napomena");
    private final JTable tZalihe = UiUtil.tabela(mZalihe, "Nema artikala za zadani filter");
    private final Pager pagerZalihe = new Pager(this::osvjeziZalihe);
    private final JLabel lZalihe = new JLabel(" ");

    // --- tab: najprodavaniji ---
    private final BiracDatuma bdTopOd = new BiracDatuma(LocalDate.now().withDayOfMonth(1));
    private final BiracDatuma bdTopDo = new BiracDatuma(LocalDate.now());
    private final JComboBox<Object> cbTopKategorija = new JComboBox<>();
    private final JComboBox<String> cbTopSort = new JComboBox<>(new String[]{"Po količini", "Po prometu"});
    private final JComboBox<String> cbTopSmjer =
            new JComboBox<>(new String[]{"Najprodavaniji prvo", "Najmanje prodavani prvo"});
    private final JComboBox<String> cbTopN = new JComboBox<>(new String[]{"10", "20", "50", "100", "Svi"});
    private final DefaultTableModel mTop = UiUtil.model("Rang", "Šifra", "Naziv", "Prodana količina", "JM", "Promet (KM)");
    private final JTable tTop = UiUtil.tabela(mTop, "Nema prodaja u odabranom periodu");

    public IzvjestajiPanel(Window prozor) {
        super(new BorderLayout());
        this.prozor = prozor;

        JTabbedPane tabovi = new JTabbedPane();
        tabovi.addTab("Promet i računi", tabRacuni());
        tabovi.addTab("Promet po prodavaču", tabProdavaci());
        tabovi.addTab("Povrati", tabPovrati());
        tabovi.addTab("Nabavke", tabNabavke());
        tabovi.addTab("Stanje zaliha", tabZalihe());
        tabovi.addTab("Najprodavaniji artikli", tabTop());
        add(tabovi, BorderLayout.CENTER);

        UiUtil.pretraziv(cbNabDobavljac);
        UiUtil.pretraziv(cbZalKategorija);
        UiUtil.pretraziv(cbTopKategorija);

        // storniran racun crveno u koloni statusa
        tRacuni.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabela, Object vrijednost,
                    boolean odabrano, boolean fokus, int red, int kolona) {
                super.getTableCellRendererComponent(tabela, vrijednost, odabrano, fokus, red, kolona);
                if ("STORNIRAN".equals(vrijednost)) {
                    setForeground(new Color(178, 60, 50));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (!odabrano) {
                    setForeground(tabela.getForeground());
                }
                return this;
            }
        });
        tZalihe.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabela, Object vrijednost,
                    boolean odabrano, boolean fokus, int red, int kolona) {
                super.getTableCellRendererComponent(tabela, vrijednost, odabrano, fokus, red, kolona);
                if ("NISKO STANJE".equals(vrijednost)) {
                    setIcon(Ikone.ikona("upozorenje", 14));
                } else {
                    setIcon(null);
                }
                return this;
            }
        });

        // dupli klik otvara detalje racuna
        tRacuni.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int red = tRacuni.getSelectedRow();
                    if (red >= 0) {
                        new RacunDetaljiDijalog(prozor, (String) tRacuni.getValueAt(red, 0)).setVisible(true);
                    }
                }
            }
        });
        tPovrati.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int red = tPovrati.getSelectedRow();
                    if (red >= 0) {
                        new RacunDetaljiDijalog(prozor, (String) tPovrati.getValueAt(red, 1)).setVisible(true);
                    }
                }
            }
        });

        osvjeziSifrarnike();
        osvjeziSve();
    }

    // ponovo puni combo-e (poziva se i kad admin doda korisnika/dobavljaca/kategoriju)
    public void osvjeziSifrarnike() {
        napuniProdavace(cbRacProdavac);
        napuniProdavace(cbPovProdavac);
        Object odabran = cbNabDobavljac.getSelectedItem();
        cbNabDobavljac.removeAllItems();
        cbNabDobavljac.addItem("— Svi dobavljači —");
        for (Dobavljac d : baza.getDobavljaci()) {
            cbNabDobavljac.addItem(d);
        }
        if (odabran instanceof Dobavljac) {
            for (int i = 0; i < cbNabDobavljac.getItemCount(); i++) {
                Object o = cbNabDobavljac.getItemAt(i);
                if (o instanceof Dobavljac && ((Dobavljac) o).getId() == ((Dobavljac) odabran).getId()) {
                    cbNabDobavljac.setSelectedIndex(i);
                    break;
                }
            }
        }
        UiUtil.napuniKategorijePutanje(cbZalKategorija, baza, "— Sve kategorije —");
        UiUtil.napuniKategorijePutanje(cbTopKategorija, baza, "— Sve kategorije —");
    }

    private void napuniProdavace(JComboBox<String> combo) {
        String odabran = (String) combo.getSelectedItem();
        combo.removeAllItems();
        combo.addItem("— Svi prodavači —");
        for (String prodavac : baza.prodavaciRacuna()) {
            combo.addItem(prodavac);
        }
        if (odabran != null) {
            combo.setSelectedItem(odabran);
        }
    }

    public void osvjeziSve() {
        osvjeziRacune();
        osvjeziProdavace();
        osvjeziPovrate();
        osvjeziNabavke();
        osvjeziZalihe();
        osvjeziTop();
    }

    // osvjezavanje pri prelasku na tab: neispravan tekst u nekom polju filtera
    // ne smije zasuti korisnika sa vise modalnih dijaloga - greske se presute,
    // a pojavice se cim korisnik sam klikne "Prikaži"
    public void osvjeziSveTiho() {
        tihoOsvjezavanje = true;
        try {
            osvjeziSve();
        } finally {
            tihoOsvjezavanje = false;
        }
    }

    private void prikaziGresku(String poruka) {
        // dok se kuca u filteru, poruku ispise sam red filtera
        if (!tihoOsvjezavanje && !RedFiltera.uzivo()) {
            UiUtil.greska(this, poruka);
        }
    }

    // pomocna: red sa filterima + dugmad. tabovi bez filtera za ponistavanje
    // (promet po prodavacu, najprodavaniji) se samo prikazuju
    private RedFiltera filterRed(Runnable prikazi, Runnable ponisti, Runnable pdf, Object... komponente) {
        String tekstDugmeta = "Filtriraj";
        if (ponisti == null) {
            tekstDugmeta = "Prikaži";
        }
        return new RedFiltera(null, tekstDugmeta, prikazi, ponisti, pdf, komponente);
    }

    // ================= tab: racuni =================

    private JPanel tabRacuni() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = RedFiltera.uspravnoSlaganje();
        gore.add(RedFiltera.brziPeriodi(bdRacOd, bdRacDo, () -> {
            pagerRacuni.naPrvu();
            redRacuna.primijeni();
        }));
        redRacuna = filterRed(() -> {
            pagerRacuni.naPrvu();
            osvjeziRacune();
        }, () -> {
            bdRacOd.postaviDatum(null);
            bdRacDo.postaviDatum(null);
            ppRacBroj.ocisti();
            cbRacProdavac.setSelectedIndex(0);
            cbRacPlacanje.setSelectedIndex(0);
            cbRacStatus.setSelectedIndex(0);
            tfRacIznosOd.setText("");
            tfRacIznosDo.setText("");
            pagerRacuni.naPrvu();
            osvjeziRacune();
        }, this::pdfRacuni,
                "Od:", bdRacOd, "Do:", bdRacDo, "Broj:", ppRacBroj, "Prodavač:", cbRacProdavac,
                "Plaćanje:", cbRacPlacanje, "Status:", cbRacStatus,
                "Iznos od:", tfRacIznosOd, "do:", tfRacIznosDo);
        gore.add(redRacuna);
        panel.add(gore, BorderLayout.NORTH);

        panel.add(new JScrollPane(tRacuni), BorderLayout.CENTER);

        JPanel dolje = new JPanel(new BorderLayout());
        dolje.add(pagerRacuni, BorderLayout.NORTH);
        lRacuni.setFont(lRacuni.getFont().deriveFont(Font.BOLD, 13f));
        dolje.add(lRacuni, BorderLayout.SOUTH);
        JLabel lUputa = new JLabel("Dupli klik na račun otvara njegove detalje (stavke i povrate).");
        lUputa.setForeground(Color.GRAY);
        dolje.add(lUputa, BorderLayout.CENTER);
        panel.add(dolje, BorderLayout.SOUTH);
        return panel;
    }

    private Filteri.FilterRacuna filterRacuna() {
        Filteri.FilterRacuna f = new Filteri.FilterRacuna();
        f.od = bdRacOd.getDatum();
        f.doD = bdRacDo.getDatum();
        f.broj = ppRacBroj.getText();
        if (cbRacProdavac.getSelectedIndex() > 0) {
            f.prodavac = (String) cbRacProdavac.getSelectedItem();
        }
        if (cbRacPlacanje.getSelectedIndex() > 0) {
            f.nacinPlacanja = (String) cbRacPlacanje.getSelectedItem();
        }
        if (cbRacStatus.getSelectedIndex() == 1) {
            f.storniran = false;
        } else if (cbRacStatus.getSelectedIndex() == 2) {
            f.storniran = true;
        }
        f.iznosOd = Util.parseBrojOpcioni(tfRacIznosOd.getText(), "Iznos od");
        f.iznosDo = Util.parseBrojOpcioni(tfRacIznosDo.getText(), "Iznos do");
        return f;
    }

    private void osvjeziRacune() {
        try {
            Filteri.FilterRacuna f = filterRacuna();
            pagerRacuni.postaviUkupno(baza.brojRacunaFiltrirano(f));
            List<Racun> lista = baza.racuniFiltrirano(f, pagerRacuni.pomak(), pagerRacuni.limit());
            List<String> brojevi = new ArrayList<>();
            for (Racun r : lista) {
                brojevi.add(r.getBroj());
            }
            Map<String, Double> vraceno = baza.vraceniIznosiRacuna(brojevi);
            mRacuni.setRowCount(0);
            for (Racun r : lista) {
                String status;
                if (r.isStorniran()) {
                    status = "STORNIRAN";
                } else {
                    status = "Izdat";
                }
                Double vracenoRacuna = vraceno.get(r.getBroj());
                if (vracenoRacuna == null) {
                    vracenoRacuna = 0.0;
                }
                mRacuni.addRow(new Object[]{r.getBroj(), r.getVrijeme().format(Util.DATUM_VRIJEME),
                        r.getProdavac(), r.getNacinPlacanja(), Util.km(r.ukupno()),
                        Util.km(vracenoRacuna), status});
            }
            lRacuni.setText(tekstZbiraRacuna(f));
        } catch (IllegalArgumentException ex) {
            prikaziGresku(ex.getMessage());
        }
    }

    // zbirovi za cijeli filter (ne samo prikazanu stranu); povrati postuju iste
    // uslove kao i racuni, da "neto" uvijek odgovara onome sto je filtrirano
    private String tekstZbiraRacuna(Filteri.FilterRacuna f) {
        if (f.storniran != null && f.storniran) {
            return "Prikazani su stornirani računi - oni ne ulaze u promet.";
        }
        double sumaIzdatih = baza.sumaRacunaFiltrirano(f);
        double sumaPovrata = baza.sumaPovrataZaFilterRacuna(f);
        return "Zbir izdatih računa u filteru: " + Util.km(sumaIzdatih)
                + " KM   |   Povrati u periodu (bez storniranih računa): " + Util.km(sumaPovrata)
                + " KM   |   Neto promet: " + Util.km(Util.round2(sumaIzdatih - sumaPovrata)) + " KM";
    }

    private void pdfRacuni() {
        try {
            Filteri.FilterRacuna f = filterRacuna();
            long ukupno = baza.brojRacunaFiltrirano(f);
            List<Racun> lista = baza.racuniFiltrirano(f, 0, MAKS_PDF_REDOVA);
            List<String> brojevi = new ArrayList<>();
            for (Racun r : lista) {
                brojevi.add(r.getBroj());
            }
            Map<String, Double> vraceno = baza.vraceniIznosiRacuna(brojevi);
            List<Object[]> redovi = new ArrayList<>();
            for (Racun r : lista) {
                String status;
                if (r.isStorniran()) {
                    status = "STORNIRAN";
                } else {
                    status = "Izdat";
                }
                Double vracenoRacuna = vraceno.get(r.getBroj());
                if (vracenoRacuna == null) {
                    vracenoRacuna = 0.0;
                }
                redovi.add(new Object[]{r.getBroj(), r.getVrijeme().format(Util.DATUM_VRIJEME), r.getProdavac(),
                        r.getNacinPlacanja(), Util.km(r.ukupno()), Util.km(vracenoRacuna), status});
            }
            // zbirovi se racunaju svjeze za trenutne filtere, ne iz podnozja na ekranu
            // (ono moze biti staro ako je filter mijenjan bez klika na "Prikaži")
            String opis = opisFiltera(ukupno, redovi.size()) + "\n" + tekstZbiraRacuna(f);
            File fajl = PdfIzvjestaj.izvezi("Izvještaj o prometu - računi", opis,
                    new String[]{"Broj računa", "Vrijeme", "Prodavač", "Plaćanje", "Iznos (KM)",
                            "Vraćeno (KM)", "Status"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    private String opisFiltera(long ukupno, int uIzvjestaju) {
        if (ukupno > uIzvjestaju) {
            return "Redova koji odgovaraju filteru: " + ukupno + " (izvezeno prvih " + uIzvjestaju + ")";
        }
        return "Redova koji odgovaraju filteru: " + ukupno;
    }

    // ================= tab: promet po prodavacu =================

    private JPanel tabProdavaci() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel gore = RedFiltera.uspravnoSlaganje();
        gore.add(RedFiltera.brziPeriodi(bdProdOd, bdProdDo, () -> redProdavaca.primijeni()));
        redProdavaca = filterRed(this::osvjeziProdavace, null, this::pdfProdavaci,
                "Od:", bdProdOd, "Do:", bdProdDo);
        gore.add(redProdavaca);
        panel.add(gore, BorderLayout.NORTH);
        panel.add(new JScrollPane(tProdavaci), BorderLayout.CENTER);
        return panel;
    }

    private void osvjeziProdavace() {
        try {
            LocalDate od = bdProdOd.getDatumObavezan("Od");
            LocalDate doD = bdProdDo.getDatumObavezan("Do");
            mProdavaci.setRowCount(0);
            for (Map.Entry<String, double[]> e : baza.prometPoProdavacima(od, doD).entrySet()) {
                mProdavaci.addRow(new Object[]{e.getKey(), (int) e.getValue()[0], Util.km(e.getValue()[1])});
            }
        } catch (IllegalArgumentException ex) {
            prikaziGresku(ex.getMessage());
        }
    }

    private void pdfProdavaci() {
        try {
            List<Object[]> redovi = new ArrayList<>();
            for (int i = 0; i < mProdavaci.getRowCount(); i++) {
                redovi.add(new Object[]{mProdavaci.getValueAt(i, 0), mProdavaci.getValueAt(i, 1),
                        mProdavaci.getValueAt(i, 2)});
            }
            File fajl = PdfIzvjestaj.izvezi("Promet po prodavačima",
                    "Period: " + bdProdOd.getDatumObavezan("Od").format(Util.DATUM)
                            + " - " + bdProdDo.getDatumObavezan("Do").format(Util.DATUM),
                    new String[]{"Prodavač", "Broj računa", "Neto promet (KM)"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ================= tab: povrati =================

    private JPanel tabPovrati() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel gore = RedFiltera.uspravnoSlaganje();
        gore.add(RedFiltera.brziPeriodi(bdPovOd, bdPovDo, () -> {
            pagerPovrati.naPrvu();
            redPovrata.primijeni();
        }));
        redPovrata = filterRed(() -> {
            pagerPovrati.naPrvu();
            osvjeziPovrate();
        }, () -> {
            bdPovOd.postaviDatum(null);
            bdPovDo.postaviDatum(null);
            ppPovTekst.ocisti();
            ppPovBroj.ocisti();
            cbPovProdavac.setSelectedIndex(0);
            pagerPovrati.naPrvu();
            osvjeziPovrate();
        }, this::pdfPovrati,
                "Od:", bdPovOd, "Do:", bdPovDo, "Artikal:", ppPovTekst,
                "Broj računa:", ppPovBroj, "Prodavač:", cbPovProdavac);
        gore.add(redPovrata);
        panel.add(gore, BorderLayout.NORTH);
        panel.add(new JScrollPane(tPovrati), BorderLayout.CENTER);
        JPanel dolje = new JPanel(new BorderLayout());
        dolje.add(pagerPovrati, BorderLayout.NORTH);
        lPovrati.setFont(lPovrati.getFont().deriveFont(Font.BOLD, 13f));
        dolje.add(lPovrati, BorderLayout.SOUTH);
        panel.add(dolje, BorderLayout.SOUTH);
        return panel;
    }

    private Filteri.FilterPovrata filterPovrata() {
        Filteri.FilterPovrata f = new Filteri.FilterPovrata();
        f.od = bdPovOd.getDatum();
        f.doD = bdPovDo.getDatum();
        f.tekst = ppPovTekst.getText();
        f.brojRacuna = ppPovBroj.getText();
        if (cbPovProdavac.getSelectedIndex() > 0) {
            f.prodavac = (String) cbPovProdavac.getSelectedItem();
        }
        return f;
    }

    private void osvjeziPovrate() {
        try {
            Filteri.FilterPovrata f = filterPovrata();
            pagerPovrati.postaviUkupno(baza.brojPovrataFiltrirano(f));
            List<Povrat> lista = baza.povratiFiltrirano(f, pagerPovrati.pomak(), pagerPovrati.limit());
            List<String> brojevi = new ArrayList<>();
            for (Povrat p : lista) {
                brojevi.add(p.getBrojRacuna());
            }
            Set<String> stornirani = baza.storniraniRacuni(brojevi);
            mPovrati.setRowCount(0);
            for (Povrat p : lista) {
                String napomena = "";
                if (stornirani.contains(p.getBrojRacuna())) {
                    napomena = "račun kasnije storniran";
                }
                mPovrati.addRow(new Object[]{p.getVrijeme().format(Util.DATUM_VRIJEME), p.getBrojRacuna(),
                        p.getSifraArtikla(), p.getNazivArtikla(), p.getKolicina(), Util.km(p.getIznos()),
                        napomena});
            }
            lPovrati.setText("Zbir povrata u filteru: " + Util.km(baza.sumaPovrataFiltrirano(f)) + " KM");
        } catch (IllegalArgumentException ex) {
            prikaziGresku(ex.getMessage());
        }
    }

    private void pdfPovrati() {
        try {
            Filteri.FilterPovrata f = filterPovrata();
            long ukupno = baza.brojPovrataFiltrirano(f);
            List<Povrat> lista = baza.povratiFiltrirano(f, 0, MAKS_PDF_REDOVA);
            List<String> brojevi = new ArrayList<>();
            for (Povrat p : lista) {
                brojevi.add(p.getBrojRacuna());
            }
            Set<String> stornirani = baza.storniraniRacuni(brojevi);
            List<Object[]> redovi = new ArrayList<>();
            for (Povrat p : lista) {
                String napomena = "";
                if (stornirani.contains(p.getBrojRacuna())) {
                    napomena = "račun kasnije storniran";
                }
                redovi.add(new Object[]{p.getVrijeme().format(Util.DATUM_VRIJEME), p.getBrojRacuna(),
                        p.getSifraArtikla(), p.getNazivArtikla(), p.getKolicina(), Util.km(p.getIznos()),
                        napomena});
            }
            String opis = opisFiltera(ukupno, redovi.size()) + "\nZbir povrata u filteru: "
                    + Util.km(baza.sumaPovrataFiltrirano(f)) + " KM";
            File fajl = PdfIzvjestaj.izvezi("Izvještaj o povratima", opis,
                    new String[]{"Vrijeme", "Broj računa", "Šifra", "Artikal", "Količina",
                            "Iznos (KM)", "Napomena"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ================= tab: nabavke =================

    private JPanel tabNabavke() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel gore = RedFiltera.uspravnoSlaganje();
        gore.add(RedFiltera.brziPeriodi(bdNabOd, bdNabDo, () -> {
            pagerNabavke.naPrvu();
            redNabavki.primijeni();
        }));
        redNabavki = filterRed(() -> {
            pagerNabavke.naPrvu();
            osvjeziNabavke();
        }, () -> {
            bdNabOd.postaviDatum(null);
            bdNabDo.postaviDatum(null);
            cbNabDobavljac.setSelectedIndex(0);
            ppNabTekst.ocisti();
            pagerNabavke.naPrvu();
            osvjeziNabavke();
        }, this::pdfNabavke,
                "Od:", bdNabOd, "Do:", bdNabDo, "Dobavljač:", cbNabDobavljac,
                "Artikal:", ppNabTekst);
        gore.add(redNabavki);
        panel.add(gore, BorderLayout.NORTH);
        panel.add(new JScrollPane(tNabavke), BorderLayout.CENTER);
        JPanel dolje = new JPanel(new BorderLayout());
        dolje.add(pagerNabavke, BorderLayout.NORTH);
        lNabavke.setFont(lNabavke.getFont().deriveFont(Font.BOLD, 13f));
        dolje.add(lNabavke, BorderLayout.SOUTH);
        panel.add(dolje, BorderLayout.SOUTH);
        return panel;
    }

    private Filteri.FilterNabavki filterNabavki() {
        Filteri.FilterNabavki f = new Filteri.FilterNabavki();
        f.od = bdNabOd.getDatum();
        f.doD = bdNabDo.getDatum();
        Object odabran = cbNabDobavljac.getSelectedItem();
        if (odabran instanceof Dobavljac) {
            f.dobavljacId = ((Dobavljac) odabran).getId();
        }
        f.tekstArtikla = ppNabTekst.getText();
        return f;
    }

    private void osvjeziNabavke() {
        try {
            Filteri.FilterNabavki f = filterNabavki();
            pagerNabavke.postaviUkupno(baza.brojStavkiNabavkiFiltrirano(f));
            mNabavke.setRowCount(0);
            for (Object[] red : baza.stavkeNabavkiFiltrirano(f, pagerNabavke.pomak(), pagerNabavke.limit())) {
                mNabavke.addRow(new Object[]{
                        LocalDate.parse((String) red[0]).format(Util.DATUM), red[1], red[2], red[3],
                        red[4], Util.km(((Number) red[5]).doubleValue()),
                        Util.km(((Number) red[6]).doubleValue())});
            }
            lNabavke.setText("Ukupna vrijednost nabavki u filteru: "
                    + Util.km(baza.sumaStavkiNabavkiFiltrirano(f)) + " KM");
        } catch (IllegalArgumentException ex) {
            prikaziGresku(ex.getMessage());
        }
    }

    private void pdfNabavke() {
        try {
            Filteri.FilterNabavki f = filterNabavki();
            long ukupno = baza.brojStavkiNabavkiFiltrirano(f);
            List<Object[]> redovi = new ArrayList<>();
            for (Object[] red : baza.stavkeNabavkiFiltrirano(f, 0, MAKS_PDF_REDOVA)) {
                redovi.add(new Object[]{LocalDate.parse((String) red[0]).format(Util.DATUM), red[1], red[2],
                        red[3], red[4], Util.km(((Number) red[5]).doubleValue()),
                        Util.km(((Number) red[6]).doubleValue())});
            }
            String opis = opisFiltera(ukupno, redovi.size()) + "\n" + lNabavke.getText();
            File fajl = PdfIzvjestaj.izvezi("Izvještaj o nabavkama", opis,
                    new String[]{"Datum", "Dobavljač", "Šifra", "Artikal", "Količina",
                            "Nab. cijena (KM)", "Iznos (KM)"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ================= tab: zalihe =================

    private JPanel tabZalihe() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(filterRed(() -> {
            pagerZalihe.naPrvu();
            osvjeziZalihe();
        }, () -> {
            ppZalTekst.ocisti();
            cbZalKategorija.setSelectedIndex(0);
            tfZalStanjeOd.setText("");
            tfZalStanjeDo.setText("");
            chZalNisko.setSelected(false);
            pagerZalihe.naPrvu();
            osvjeziZalihe();
        }, this::pdfZalihe,
                "Pretraga:", ppZalTekst, "Kategorija:", cbZalKategorija,
                "Stanje od:", tfZalStanjeOd, "do:", tfZalStanjeDo, chZalNisko), BorderLayout.NORTH);
        panel.add(new JScrollPane(tZalihe), BorderLayout.CENTER);
        JPanel dolje = new JPanel(new BorderLayout());
        dolje.add(pagerZalihe, BorderLayout.NORTH);
        lZalihe.setFont(lZalihe.getFont().deriveFont(Font.BOLD, 13f));
        dolje.add(lZalihe, BorderLayout.SOUTH);
        panel.add(dolje, BorderLayout.SOUTH);
        return panel;
    }

    private Filteri.FilterArtikala filterZaliha() {
        Filteri.FilterArtikala f = new Filteri.FilterArtikala();
        f.tekst = ppZalTekst.getText();
        f.kategorijaId = UiUtil.odabranaKategorija(cbZalKategorija);
        f.stanjeOd = Util.parseCijeliBrojOpcioni(tfZalStanjeOd.getText(), "Stanje od");
        f.stanjeDo = Util.parseCijeliBrojOpcioni(tfZalStanjeDo.getText(), "Stanje do");
        f.samoNiskoStanje = chZalNisko.isSelected();
        return f;
    }

    private void osvjeziZalihe() {
        try {
            Filteri.FilterArtikala f = filterZaliha();
            pagerZalihe.postaviUkupno(baza.brojArtikalaFiltrirano(f));
            mZalihe.setRowCount(0);
            for (Artikal a : baza.artikliFiltrirano(f, pagerZalihe.pomak(), pagerZalihe.limit())) {
                String napomena = "";
                if (a.getStanje() < 10) {
                    napomena = "NISKO STANJE";
                }
                mZalihe.addRow(new Object[]{a.getSifra(), a.getNaziv(),
                        baza.putanjaKategorije(a.getKategorijaId()), a.getJedinicaMjere(), a.getStanje(),
                        Util.km(a.getCijena()), Util.km(Util.round2(a.getStanje() * a.getCijena())), napomena});
            }
            lZalihe.setText("Vrijednost zaliha u filteru: " + Util.km(baza.vrijednostZalihaFiltrirano(f))
                    + " KM   |   Sve zalihe: " + Util.km(baza.vrijednostZaliha()) + " KM");
        } catch (IllegalArgumentException ex) {
            prikaziGresku(ex.getMessage());
        }
    }

    private void pdfZalihe() {
        try {
            Filteri.FilterArtikala f = filterZaliha();
            long ukupno = baza.brojArtikalaFiltrirano(f);
            List<Object[]> redovi = new ArrayList<>();
            for (Artikal a : baza.artikliFiltrirano(f, 0, MAKS_PDF_REDOVA)) {
                String napomena = "";
                if (a.getStanje() < 10) {
                    napomena = "NISKO STANJE";
                }
                redovi.add(new Object[]{a.getSifra(), a.getNaziv(), baza.putanjaKategorije(a.getKategorijaId()),
                        a.getJedinicaMjere(), a.getStanje(), Util.km(a.getCijena()),
                        Util.km(Util.round2(a.getStanje() * a.getCijena())), napomena});
            }
            String opis = opisFiltera(ukupno, redovi.size())
                    + "\nVrijednost zaliha u filteru: " + Util.km(baza.vrijednostZalihaFiltrirano(f))
                    + " KM   |   Sve zalihe: " + Util.km(baza.vrijednostZaliha()) + " KM";
            File fajl = PdfIzvjestaj.izvezi("Izvještaj o stanju zaliha", opis,
                    new String[]{"Šifra", "Naziv", "Kategorija", "JM", "Stanje", "Cijena (KM)",
                            "Vrijednost (KM)", "Napomena"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }

    // ================= tab: najprodavaniji =================

    private JPanel tabTop() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel gore = RedFiltera.uspravnoSlaganje();
        gore.add(RedFiltera.brziPeriodi(bdTopOd, bdTopDo, () -> redTop.primijeni()));
        redTop = filterRed(this::osvjeziTop, null, this::pdfTop,
                "Od:", bdTopOd, "Do:", bdTopDo, "Kategorija:", cbTopKategorija,
                "Sortiraj:", cbTopSort, "Redoslijed:", cbTopSmjer, "Prikaži prvih:", cbTopN);
        gore.add(redTop);
        panel.add(gore, BorderLayout.NORTH);
        panel.add(new JScrollPane(tTop), BorderLayout.CENTER);
        return panel;
    }

    private void osvjeziTop() {
        try {
            mTop.setRowCount(0);
            int rang = 1;
            for (Object[] red : izracunajTop()) {
                mTop.addRow(new Object[]{rang, red[0], red[1], red[2], red[4], Util.km((Double) red[3])});
                rang = rang + 1;
            }
        } catch (IllegalArgumentException ex) {
            prikaziGresku(ex.getMessage());
        }
    }

    private List<Object[]> izracunajTop() {
        LocalDate od = bdTopOd.getDatumObavezan("Od");
        LocalDate doD = bdTopDo.getDatumObavezan("Do");
        boolean poPrometu = cbTopSort.getSelectedIndex() == 1;
        boolean rastuce = cbTopSmjer.getSelectedIndex() == 1;
        int limit = 0;
        String odabranoN = (String) cbTopN.getSelectedItem();
        if (odabranoN != null && !"Svi".equals(odabranoN)) {
            limit = Integer.parseInt(odabranoN);
        }
        return baza.najprodavaniji(od, doD, UiUtil.odabranaKategorija(cbTopKategorija), poPrometu, rastuce, limit);
    }

    private void pdfTop() {
        try {
            List<Object[]> redovi = new ArrayList<>();
            int rang = 1;
            for (Object[] red : izracunajTop()) {
                redovi.add(new Object[]{rang, red[0], red[1], red[2], red[4], Util.km((Double) red[3])});
                rang = rang + 1;
            }
            String opis = "Period: " + bdTopOd.getDatumObavezan("Od").format(Util.DATUM)
                    + " - " + bdTopDo.getDatumObavezan("Do").format(Util.DATUM)
                    + "   |   Sortirano: " + cbTopSort.getSelectedItem() + " (" + cbTopSmjer.getSelectedItem() + ")";
            File fajl = PdfIzvjestaj.izvezi("Najprodavaniji artikli", opis,
                    new String[]{"Rang", "Šifra", "Naziv", "Prodana količina", "JM", "Promet (KM)"}, redovi);
            UiUtil.izvjestajSnimljen(this, fajl);
        } catch (Exception ex) {
            UiUtil.greska(this, "Greška pri izvozu PDF-a: " + ex.getMessage());
        }
    }
}
