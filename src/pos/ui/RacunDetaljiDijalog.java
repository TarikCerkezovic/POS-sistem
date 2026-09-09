package pos.ui;

import pos.data.Baza;
import pos.model.Povrat;
import pos.model.Racun;
import pos.model.StavkaRacuna;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

import pos.util.Util;

// detalji jednog racuna: zaglavlje, stavke i evidentirani povrati
public class RacunDetaljiDijalog extends JDialog {

    private boolean ispravan = false;

    @Override
    public void setVisible(boolean vidljiv) {
        // ako racun nije nadjen, dijalog se ne prikazuje (poruka je vec ispisana)
        if (vidljiv && !ispravan) {
            return;
        }
        super.setVisible(vidljiv);
    }

    public RacunDetaljiDijalog(Window roditelj, String brojRacuna) {
        super(roditelj, "Detalji računa " + brojRacuna, ModalityType.APPLICATION_MODAL);
        Baza baza = Baza.get();
        Racun racun = baza.nadjiRacun(brojRacuna);
        if (racun == null) {
            dispose();
            UiUtil.greska(roditelj, "Račun nije pronađen!");
            return;
        }
        ispravan = true;

        setSize(700, 520);
        setLocationRelativeTo(roditelj);
        setLayout(new BorderLayout(8, 8));

        String status;
        if (racun.isStorniran()) {
            status = "STORNIRAN";
        } else {
            status = "Izdat";
        }
        double vraceno = baza.vraceniIznosRacuna(racun.getBroj());

        JPanel zaglavlje = new JPanel(new GridLayout(0, 2, 12, 2));
        zaglavlje.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        zaglavlje.add(new JLabel("Broj računa: " + racun.getBroj()));
        zaglavlje.add(new JLabel("Vrijeme: " + racun.getVrijeme().format(Util.DATUM_VRIJEME)));
        zaglavlje.add(new JLabel("Prodavač: " + racun.getProdavac()));
        zaglavlje.add(new JLabel("Način plaćanja: " + racun.getNacinPlacanja()));
        zaglavlje.add(new JLabel("Status: " + status));
        zaglavlje.add(new JLabel("Ukupno: " + Util.km(racun.ukupno()) + " KM (PDV: "
                + Util.km(racun.pdv()) + " KM)"));
        if ("GOTOVINA".equals(racun.getNacinPlacanja())) {
            zaglavlje.add(new JLabel("Predato: " + Util.km(racun.getPredato()) + " KM"));
            zaglavlje.add(new JLabel("Kusur: " + Util.km(racun.getPovratNovca()) + " KM"));
        }
        zaglavlje.add(new JLabel("Vraćeno kroz povrate: " + Util.km(vraceno) + " KM"));
        // storniran racun je ponisten u cijelosti pa mu je neto vrijednost nula
        String neto;
        if (racun.isStorniran()) {
            neto = "0.00 KM (račun je storniran)";
        } else {
            neto = Util.km(Util.round2(racun.ukupno() - vraceno)) + " KM";
        }
        zaglavlje.add(new JLabel("Neto vrijednost računa: " + neto));
        add(zaglavlje, BorderLayout.NORTH);

        DefaultTableModel mStavke = UiUtil.model("Šifra", "Naziv", "Količina", "Cijena", "Popust %", "Iznos (KM)");
        JTable tStavke = UiUtil.tabela(mStavke, "Račun nema stavki");
        for (StavkaRacuna s : racun.getStavke()) {
            String popust = "-";
            if (s.getPopustProcenat() > 0) {
                popust = String.format("%.0f", s.getPopustProcenat());
            }
            mStavke.addRow(new Object[]{s.getSifraArtikla(), s.getNazivArtikla(), s.getKolicina(),
                    Util.km(s.getCijena()), popust, Util.km(s.iznos())});
        }

        DefaultTableModel mPovrati = UiUtil.model("Vrijeme", "Šifra", "Naziv", "Količina", "Iznos (KM)");
        JTable tPovrati = UiUtil.tabela(mPovrati, "Nema evidentiranih povrata za ovaj račun");
        List<Povrat> povrati = baza.povratiRacuna(racun.getBroj());
        for (Povrat p : povrati) {
            mPovrati.addRow(new Object[]{p.getVrijeme().format(Util.DATUM_VRIJEME), p.getSifraArtikla(),
                    p.getNazivArtikla(), p.getKolicina(), Util.km(p.getIznos())});
        }

        JPanel stavkePanel = new JPanel(new BorderLayout());
        stavkePanel.setBorder(BorderFactory.createTitledBorder("Stavke računa"));
        stavkePanel.add(new JScrollPane(tStavke), BorderLayout.CENTER);
        JPanel povratiPanel = new JPanel(new BorderLayout());
        povratiPanel.setBorder(BorderFactory.createTitledBorder("Povrati po ovom računu"));
        povratiPanel.add(new JScrollPane(tPovrati), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stavkePanel, povratiPanel);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        JPanel dolje = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnZatvori = new JButton("Zatvori");
        btnZatvori.addActionListener(e -> dispose());
        dolje.add(btnZatvori);
        add(dolje, BorderLayout.SOUTH);
    }
}
