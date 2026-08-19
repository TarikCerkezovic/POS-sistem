package pos.ui;

import pos.model.Korisnik;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class UiUtil {

    public static DefaultTableModel model(String... kolone) {
        return new DefaultTableModel(kolone, 0) {
            @Override
            public boolean isCellEditable(int red, int kolona) { return false; }
        };
    }

    public static void greska(Component roditelj, String poruka) {
        JOptionPane.showMessageDialog(roditelj, poruka, "Greška", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component roditelj, String poruka) {
        JOptionPane.showMessageDialog(roditelj, poruka, "Obavještenje", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean potvrda(Component roditelj, String poruka) {
        return JOptionPane.showConfirmDialog(roditelj, poruka, "Potvrda",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static void dodaj(JPanel panel, GridBagConstraints gbc, int x, int y, int sirina, Component komp) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = sirina;
        panel.add(komp, gbc);
    }

    public static JPanel zaglavlje(String naslov, Korisnik korisnik, JFrame prozor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel lNaslov = new JLabel(naslov);
        lNaslov.setFont(lNaslov.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(lNaslov, BorderLayout.WEST);

        JPanel desno = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JLabel lKorisnik = new JLabel("Prijavljeni korisnik: " + korisnik.getIme() + " (" + korisnik.getUloga() + ")");
        JButton btnOdjava = new JButton("Odjava");
        btnOdjava.addActionListener(e -> {
            new PrijavaFrame().setVisible(true);
            prozor.dispose();
        });
        desno.add(lKorisnik);
        desno.add(btnOdjava);
        panel.add(desno, BorderLayout.EAST);
        return panel;
    }
}
