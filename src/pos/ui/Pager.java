package pos.ui;

import javax.swing.*;
import java.awt.*;

// navigacija po stranama za tabele: baza vraca samo jednu stranu redova,
// pa tabela radi i kad u bazi ima milione zapisa
public class Pager extends JPanel {

    private static final Integer[] VELICINE = {50, 100, 200, 500};

    private final JButton btnPrva = new JButton("|<");
    private final JButton btnNazad = new JButton("<");
    private final JButton btnNaprijed = new JButton(">");
    private final JButton btnZadnja = new JButton(">|");
    private final JLabel lStrana = new JLabel("Strana 1 od 1");
    private final JLabel lUkupno = new JLabel("(0 redova)");
    private final JComboBox<Integer> cbVelicina = new JComboBox<>(VELICINE);

    private final Runnable osvjezi;
    private int strana = 0;
    private long ukupno = 0;

    public Pager(Runnable osvjezi) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.osvjezi = osvjezi;
        cbVelicina.setSelectedItem(100);

        for (JButton btn : new JButton[]{btnPrva, btnNazad, btnNaprijed, btnZadnja}) {
            btn.setMargin(new Insets(1, 6, 1, 6));
        }
        add(btnPrva);
        add(btnNazad);
        add(lStrana);
        add(btnNaprijed);
        add(btnZadnja);
        add(Box.createHorizontalStrut(6));
        add(lUkupno);
        add(Box.createHorizontalStrut(10));
        add(new JLabel("Redova po strani:"));
        add(cbVelicina);

        btnPrva.addActionListener(e -> {
            strana = 0;
            osvjezi.run();
        });
        btnNazad.addActionListener(e -> {
            if (strana > 0) {
                strana = strana - 1;
            }
            osvjezi.run();
        });
        btnNaprijed.addActionListener(e -> {
            strana = strana + 1;
            osvjezi.run();
        });
        btnZadnja.addActionListener(e -> {
            strana = (int) zadnjaStrana();
            osvjezi.run();
        });
        cbVelicina.addActionListener(e -> {
            strana = 0;
            osvjezi.run();
        });
    }

    public int pomak() {
        return strana * limit();
    }

    public int limit() {
        Integer odabrano = (Integer) cbVelicina.getSelectedItem();
        if (odabrano == null) {
            return 100;
        }
        return odabrano;
    }

    // vraca se na prvu stranu bez osvjezavanja (poziva se kad se promijeni filter)
    public void naPrvu() {
        strana = 0;
    }

    // poziva se u toku osvjezavanja, prije upita za redove: postavlja ukupan broj
    // i po potrebi vraca stranu u opseg (npr. filter suzio rezultate)
    public void postaviUkupno(long ukupnoRedova) {
        ukupno = ukupnoRedova;
        long zadnja = zadnjaStrana();
        if (strana > zadnja) {
            strana = (int) zadnja;
        }
        lStrana.setText("Strana " + (strana + 1) + " od " + (zadnja + 1));
        lUkupno.setText("(" + ukupno + " redova)");
        btnPrva.setEnabled(strana > 0);
        btnNazad.setEnabled(strana > 0);
        btnNaprijed.setEnabled(strana < zadnja);
        btnZadnja.setEnabled(strana < zadnja);
    }

    private long zadnjaStrana() {
        if (ukupno <= 0) {
            return 0;
        }
        return (ukupno - 1) / limit();
    }
}
