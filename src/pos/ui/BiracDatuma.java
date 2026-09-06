package pos.ui;

import pos.util.Util;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

// polje za datum: nema kucanja, klik otvara kalendar u kojem se dan bira misem.
// prazno polje znaci "bez datuma" (korisno u filterima)
public class BiracDatuma extends JPanel {

    private static final String[] MJESECI = {
        "Januar", "Februar", "Mart", "April", "Maj", "Juni",
        "Juli", "Avgust", "Septembar", "Oktobar", "Novembar", "Decembar"
    };
    private static final String[] DANI = {"po", "ut", "sr", "če", "pe", "su", "ne"};

    private final JTextField polje = new JTextField(8);
    private LocalDate datum;
    private YearMonth prikazaniMjesec = YearMonth.now();
    private Runnable naPromjenu;

    public BiracDatuma(LocalDate pocetni) {
        super(new BorderLayout(2, 0));
        setOpaque(false);
        polje.setEditable(false);
        polje.setToolTipText("Kliknite za izbor datuma");
        JButton dugme = new JButton(Ikone.ikona("kalendar", 14));
        dugme.setMargin(new Insets(2, 4, 2, 4));
        dugme.setToolTipText("Otvori kalendar");
        add(polje, BorderLayout.CENTER);
        add(dugme, BorderLayout.EAST);

        dugme.addActionListener(e -> otvoriKalendar());
        polje.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                otvoriKalendar();
            }
        });

        postaviDatum(pocetni);
    }

    // poziva se kad korisnik odabere ili obrise datum u kalendaru
    public void naPromjenu(Runnable akcija) {
        this.naPromjenu = akcija;
    }

    public LocalDate getDatum() {
        return datum;
    }

    public LocalDate getDatumObavezan(String nazivPolja) {
        if (datum == null) {
            throw new IllegalArgumentException("Odaberite datum u polju \"" + nazivPolja + "\"!");
        }
        return datum;
    }

    public void postaviDatum(LocalDate novi) {
        datum = novi;
        if (datum == null) {
            polje.setText("");
        } else {
            polje.setText(datum.format(Util.DATUM));
            prikazaniMjesec = YearMonth.from(datum);
        }
    }

    private void odaberi(LocalDate novi, JPopupMenu popup) {
        postaviDatum(novi);
        popup.setVisible(false);
        if (naPromjenu != null) {
            naPromjenu.run();
        }
    }

    private void otvoriKalendar() {
        if (!isEnabled()) {
            return;
        }
        if (datum != null) {
            prikazaniMjesec = YearMonth.from(datum);
        }
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(napraviKalendar(popup), BorderLayout.CENTER);
        popup.show(this, 0, getHeight());
    }

    private JPanel napraviKalendar(JPopupMenu popup) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // navigacija: << godina, < mjesec, naziv, mjesec >, godina >>
        JPanel vrh = new JPanel(new BorderLayout(2, 0));
        JPanel lijevo = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JPanel desno = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JButton godinaNazad = malaTipka("<<", "Prethodna godina");
        JButton mjesecNazad = malaTipka("<", "Prethodni mjesec");
        JButton mjesecNaprijed = malaTipka(">", "Sljedeći mjesec");
        JButton godinaNaprijed = malaTipka(">>", "Sljedeća godina");
        lijevo.add(godinaNazad);
        lijevo.add(mjesecNazad);
        desno.add(mjesecNaprijed);
        desno.add(godinaNaprijed);
        JLabel lMjesec = new JLabel(MJESECI[prikazaniMjesec.getMonthValue() - 1] + " "
                + prikazaniMjesec.getYear(), SwingConstants.CENTER);
        lMjesec.setFont(lMjesec.getFont().deriveFont(Font.BOLD));
        vrh.add(lijevo, BorderLayout.WEST);
        vrh.add(lMjesec, BorderLayout.CENTER);
        vrh.add(desno, BorderLayout.EAST);
        panel.add(vrh, BorderLayout.NORTH);

        // mreza dana
        JPanel mreza = new JPanel(new GridLayout(0, 7, 2, 2));
        for (String dan : DANI) {
            JLabel l = new JLabel(dan, SwingConstants.CENTER);
            l.setForeground(Color.GRAY);
            mreza.add(l);
        }
        LocalDate prviUMjesecu = prikazaniMjesec.atDay(1);
        int pomak = prviUMjesecu.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        for (int i = 0; i < pomak; i++) {
            mreza.add(new JLabel(""));
        }
        LocalDate danas = LocalDate.now();
        for (int dan = 1; dan <= prikazaniMjesec.lengthOfMonth(); dan++) {
            LocalDate ovaj = prikazaniMjesec.atDay(dan);
            JButton tipka = new JButton(String.valueOf(dan));
            tipka.setMargin(new Insets(2, 2, 2, 2));
            tipka.setFocusable(false);
            if (ovaj.equals(danas)) {
                tipka.setFont(tipka.getFont().deriveFont(Font.BOLD));
                tipka.setForeground(new Color(46, 78, 126));
            }
            if (ovaj.equals(datum)) {
                tipka.setBackground(new Color(46, 78, 126));
                tipka.setForeground(Color.WHITE);
                tipka.setOpaque(true);
            }
            tipka.addActionListener(e -> odaberi(ovaj, popup));
            mreza.add(tipka);
        }
        panel.add(mreza, BorderLayout.CENTER);

        // brze opcije
        JPanel dno = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        JButton btnDanas = new JButton("Danas");
        btnDanas.addActionListener(e -> odaberi(LocalDate.now(), popup));
        JButton btnObrisi = new JButton("Obriši");
        btnObrisi.setToolTipText("Isprazni polje (bez datuma)");
        btnObrisi.addActionListener(e -> odaberi(null, popup));
        dno.add(btnDanas);
        dno.add(btnObrisi);
        panel.add(dno, BorderLayout.SOUTH);

        // listice za promjenu mjeseca/godine ponovo grade sadrzaj popupa
        godinaNazad.addActionListener(e -> pomjeri(popup, prikazaniMjesec.minusYears(1)));
        mjesecNazad.addActionListener(e -> pomjeri(popup, prikazaniMjesec.minusMonths(1)));
        mjesecNaprijed.addActionListener(e -> pomjeri(popup, prikazaniMjesec.plusMonths(1)));
        godinaNaprijed.addActionListener(e -> pomjeri(popup, prikazaniMjesec.plusYears(1)));
        return panel;
    }

    private JButton malaTipka(String tekst, String opis) {
        JButton tipka = new JButton(tekst);
        tipka.setMargin(new Insets(2, 5, 2, 5));
        tipka.setToolTipText(opis);
        tipka.setFocusable(false);
        return tipka;
    }

    private void pomjeri(JPopupMenu popup, YearMonth novi) {
        prikazaniMjesec = novi;
        popup.removeAll();
        popup.add(napraviKalendar(popup), BorderLayout.CENTER);
        popup.revalidate();
        popup.pack();
    }

    @Override
    public void setEnabled(boolean ukljucen) {
        super.setEnabled(ukljucen);
        for (Component k : getComponents()) {
            k.setEnabled(ukljucen);
        }
    }
}
