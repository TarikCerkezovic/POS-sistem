package pos.ui;

import pos.model.Korisnik;

import javax.swing.*;
import java.awt.*;

public class MenadzerFrame extends JFrame {

    public MenadzerFrame(Korisnik korisnik) {
        setTitle("POS sistem - Menadžer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        UiUtil.ikonaProzora(this);
        setSize(1050, 680);
        setLocationRelativeTo(null);
        // radni prozori idu preko cijelog ekrana (prijava ostaje mala)
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());

        add(UiUtil.zaglavlje("Izvještaji i analitika poslovanja", korisnik, this), BorderLayout.NORTH);

        JTabbedPane tabovi = new JTabbedPane();
        tabovi.addTab("Izvještaji", new IzvjestajiPanel(this));
        tabovi.addTab("Statistika", new StatistikaPanel());
        add(tabovi, BorderLayout.CENTER);
    }
}
