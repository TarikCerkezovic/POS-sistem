package pos.ui;

import pos.data.Baza;
import pos.model.*;
import pos.util.Util;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class MenadzerFrame extends JFrame {

    private final Baza baza = Baza.get();

    private final JComboBox<String> cbPeriod =
            new JComboBox<>(new String[]{"Dnevni", "Sedmični", "Mjesečni"});
    private final JTextField tfDatum = new JTextField(LocalDate.now().format(Util.DATUM), 10);
    private final DefaultTableModel modelPromet =
            UiUtil.model("Broj računa", "Vrijeme", "Prodavač", "Plaćanje", "Iznos (KM)", "Status");
    private final JLabel lPromet = new JLabel(" ");

    private final JTextField tfProdOd = new JTextField(LocalDate.now().withDayOfMonth(1).format(Util.DATUM), 10);
    private final JTextField tfProdDo = new JTextField(LocalDate.now().format(Util.DATUM), 10);
    private final DefaultTableModel modelProdavaci =
            UiUtil.model("Prodavač", "Broj računa", "Neto promet (KM)");

    private final JComboBox<Object> cbDobavljac = new JComboBox<>();
    private final DefaultTableModel modelNabavke =
            UiUtil.model("Datum", "Dobavljač", "Šifra", "Artikal", "Količina", "Nab. cijena (KM)", "Iznos (KM)");
    private final JLabel lNabavke = new JLabel(" ");

    private final DefaultTableModel modelZalihe =
            UiUtil.model("Šifra", "Naziv", "Kategorija", "JM", "Stanje", "Cijena (KM)", "Napomena");

    private final JTextField tfTopOd = new JTextField(LocalDate.now().withDayOfMonth(1).format(Util.DATUM), 10);
    private final JTextField tfTopDo = new JTextField(LocalDate.now().format(Util.DATUM), 10);
    private final DefaultTableModel modelTop =
            UiUtil.model("Rang", "Šifra", "Naziv", "Prodano (kom)", "Promet (KM)");

    public MenadzerFrame(Korisnik korisnik) {
        setTitle("POS sistem - Menadžer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1050, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(UiUtil.zaglavlje("Izvještaji i analitika poslovanja", korisnik, this), BorderLayout.NORTH);

        JTabbedPane tabovi = new JTabbedPane();
        tabovi.addTab("Promet", tabPromet());
        tabovi.addTab("Promet po prodavaču", tabProdavaci());
        tabovi.addTab("Nabavke od dobavljača", tabNabavke());
        tabovi.addTab("Stanje zaliha", tabZalihe());
        tabovi.addTab("Najprodavaniji artikli", tabTop());
        add(tabovi, BorderLayout.CENTER);

        prikaziPromet();
        prikaziProdavace();
        napuniDobavljace();
        prikaziNabavke();
        prikaziZalihe();
        prikaziTop();
    }

    private JPanel tabPromet() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        gore.add(new JLabel("Izvještaj:"));
        gore.add(cbPeriod);
        gore.add(new JLabel("Datum (dd.MM.gggg):"));
        gore.add(tfDatum);
        JButton btn = new JButton("Prikaži");
        btn.addActionListener(e -> prikaziPromet());
        gore.add(btn);
        panel.add(gore, BorderLayout.NORTH);

        panel.add(new JScrollPane(new JTable(modelPromet)), BorderLayout.CENTER);

        lPromet.setFont(lPromet.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(lPromet, BorderLayout.SOUTH);
        return panel;
    }

    private void prikaziPromet() {
        try {
            LocalDate datum = Util.parseDatum(tfDatum.getText());
            LocalDate od;
            LocalDate doD;
            String naziv;
            int odabraniPeriod = cbPeriod.getSelectedIndex();
            if (odabraniPeriod == 1) {
                od = Baza.pocetakSedmice(datum);
                doD = od.plusDays(6);
                naziv = "Sedmični";
            } else if (odabraniPeriod == 2) {
                od = datum.withDayOfMonth(1);
                doD = od.withDayOfMonth(od.lengthOfMonth());
                naziv = "Mjesečni";
            } else {
                od = datum;
                doD = datum;
                naziv = "Dnevni";
            }
            modelPromet.setRowCount(0);
            int izdatih = 0;
            for (Racun r : baza.racuniURasponu(od, doD)) {
                if (!r.isStorniran()) {
                    izdatih++;
                }
                String status;
                if (r.isStorniran()) {
                    status = "STORNIRAN";
                } else {
                    status = "Izdat";
                }
                modelPromet.addRow(new Object[]{
                        r.getBroj(), r.getVrijeme().format(Util.DATUM_VRIJEME), r.getProdavac(),
                        r.getNacinPlacanja(), Util.km(r.ukupno()), status});
            }
            double povrati = 0;
            for (Povrat p : baza.povratiURasponu(od, doD)) {
                povrati = povrati + p.getIznos();
            }
            lPromet.setText(naziv + " promet (" + od.format(Util.DATUM) + " - " + doD.format(Util.DATUM)
                    + "): " + Util.km(baza.promet(od, doD)) + " KM   |   Izdatih računa: " + izdatih
                    + "   |   Povrati: " + Util.km(Util.round2(povrati)) + " KM");
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }

    private JPanel tabProdavaci() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        gore.add(new JLabel("Od (dd.MM.gggg):"));
        gore.add(tfProdOd);
        gore.add(new JLabel("Do:"));
        gore.add(tfProdDo);
        JButton btn = new JButton("Prikaži");
        btn.addActionListener(e -> prikaziProdavace());
        gore.add(btn);
        panel.add(gore, BorderLayout.NORTH);

        panel.add(new JScrollPane(new JTable(modelProdavaci)), BorderLayout.CENTER);
        return panel;
    }

    private void prikaziProdavace() {
        try {
            LocalDate od = Util.parseDatum(tfProdOd.getText());
            LocalDate doD = Util.parseDatum(tfProdDo.getText());
            modelProdavaci.setRowCount(0);
            Map<String, double[]> mapa = baza.prometPoProdavacima(od, doD);
            for (Map.Entry<String, double[]> e : mapa.entrySet()) {
                modelProdavaci.addRow(new Object[]{
                        e.getKey(), (int) e.getValue()[0], Util.km(e.getValue()[1])});
            }
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }

    private JPanel tabNabavke() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        gore.add(new JLabel("Dobavljač:"));
        gore.add(cbDobavljac);
        JButton btn = new JButton("Prikaži");
        btn.addActionListener(e -> prikaziNabavke());
        gore.add(btn);
        panel.add(gore, BorderLayout.NORTH);

        panel.add(new JScrollPane(new JTable(modelNabavke)), BorderLayout.CENTER);

        lNabavke.setFont(lNabavke.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(lNabavke, BorderLayout.SOUTH);
        return panel;
    }

    private void napuniDobavljace() {
        cbDobavljac.removeAllItems();
        cbDobavljac.addItem("— Svi dobavljači —");
        for (Dobavljac d : baza.getDobavljaci()) {
            cbDobavljac.addItem(d);
        }
    }

    private void prikaziNabavke() {
        Object odabran = cbDobavljac.getSelectedItem();
        Integer filterId = null;
        if (odabran instanceof Dobavljac) {
            Dobavljac odabraniDobavljac = (Dobavljac) odabran;
            filterId = odabraniDobavljac.getId();
        }
        modelNabavke.setRowCount(0);
        double ukupno = 0;
        for (Nabavka n : baza.getNabavke()) {
            if (filterId != null && n.getDobavljacId() != filterId) {
                continue;
            }
            Dobavljac d = baza.nadjiDobavljaca(n.getDobavljacId());
            String nazivDob;
            if (d == null) {
                nazivDob = "?";
            } else {
                nazivDob = d.getNaziv();
            }
            for (StavkaNabavke s : n.getStavke()) {
                modelNabavke.addRow(new Object[]{
                        n.getDatum().format(Util.DATUM), nazivDob, s.getSifraArtikla(),
                        s.getNazivArtikla(), s.getKolicina(), Util.km(s.getNabavnaCijena()),
                        Util.km(s.iznos())});
            }
            ukupno = ukupno + n.ukupno();
        }
        lNabavke.setText("Ukupna vrijednost prikazanih nabavki: " + Util.km(Util.round2(ukupno)) + " KM");
    }

    private JPanel tabZalihe() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton btn = new JButton("Osvježi");
        btn.addActionListener(e -> prikaziZalihe());
        gore.add(btn);
        gore.add(new JLabel("Artikli sa stanjem ispod 10 komada označeni su napomenom \"NISKO STANJE\"."));
        panel.add(gore, BorderLayout.NORTH);

        panel.add(new JScrollPane(new JTable(modelZalihe)), BorderLayout.CENTER);
        return panel;
    }

    private void prikaziZalihe() {
        modelZalihe.setRowCount(0);
        for (Artikal a : baza.getArtikli()) {
            String napomena;
            if (a.getStanje() < 10) {
                napomena = "NISKO STANJE";
            } else {
                napomena = "";
            }
            modelZalihe.addRow(new Object[]{
                    a.getSifra(), a.getNaziv(), baza.putanjaKategorije(a.getKategorijaId()),
                    a.getJedinicaMjere(), a.getStanje(), Util.km(a.getCijena()), napomena});
        }
    }

    private JPanel tabTop() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel gore = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        gore.add(new JLabel("Od (dd.MM.gggg):"));
        gore.add(tfTopOd);
        gore.add(new JLabel("Do:"));
        gore.add(tfTopDo);
        JButton btn = new JButton("Prikaži");
        btn.addActionListener(e -> prikaziTop());
        gore.add(btn);
        panel.add(gore, BorderLayout.NORTH);

        panel.add(new JScrollPane(new JTable(modelTop)), BorderLayout.CENTER);
        return panel;
    }

    private void prikaziTop() {
        try {
            LocalDate od = Util.parseDatum(tfTopOd.getText());
            LocalDate doD = Util.parseDatum(tfTopDo.getText());
            modelTop.setRowCount(0);
            List<Object[]> lista = baza.najprodavaniji(od, doD);
            int rang = 1;
            for (Object[] red : lista) {
                modelTop.addRow(new Object[]{
                        rang, red[0], red[1], red[2], Util.km((Double) red[3])});
                rang = rang + 1;
            }
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
        }
    }
}
