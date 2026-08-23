package pos.ui;

import pos.data.Baza;
import pos.model.Korisnik;

import javax.swing.*;
import java.awt.*;

public class PrijavaFrame extends JFrame {

    private final JTextField tfLogin = new JTextField(15);
    private final JPasswordField tfSifra = new JPasswordField(15);

    public PrijavaFrame() {
        super("POS sistem - Prijava na sistem");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(25, 35, 25, 35));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lUniverzitet = new JLabel("UNIVERZITET U TUZLI", SwingConstants.CENTER);
        lUniverzitet.setFont(lUniverzitet.getFont().deriveFont(Font.BOLD, 16f));
        JLabel lFakultet = new JLabel("FAKULTET ELEKTROTEHNIKE", SwingConstants.CENTER);
        lFakultet.setFont(lFakultet.getFont().deriveFont(Font.PLAIN, 13f));
        JLabel lNaslov = new JLabel("POS sistem za maloprodajne objekte i markete", SwingConstants.CENTER);
        lNaslov.setFont(lNaslov.getFont().deriveFont(Font.ITALIC, 12f));

        JLabel lUputa = new JLabel("Unesite svoje korisničke podatke", SwingConstants.CENTER);
        lUputa.setFont(lUputa.getFont().deriveFont(Font.BOLD, 13f));

        
        JLabel lKatanac = new JLabel(Ikone.ikona("katanac", 72), SwingConstants.CENTER);
        lKatanac.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 18));

        JButton btnPrijava = UiUtil.dugme("Prijavi se", "osoba");

        UiUtil.dodaj(panel, gbc, 0, 0, 3, lUniverzitet);
        UiUtil.dodaj(panel, gbc, 0, 1, 3, lFakultet);
        UiUtil.dodaj(panel, gbc, 0, 2, 3, lNaslov);
        UiUtil.dodaj(panel, gbc, 0, 3, 3, new JSeparator());
        UiUtil.dodaj(panel, gbc, 0, 4, 3, lUputa);
        gbc.gridheight = 3;
        UiUtil.dodaj(panel, gbc, 0, 5, 1, lKatanac);
        gbc.gridheight = 1;
        UiUtil.dodaj(panel, gbc, 1, 5, 1, new JLabel("Login:"));
        UiUtil.dodaj(panel, gbc, 2, 5, 1, tfLogin);
        UiUtil.dodaj(panel, gbc, 1, 6, 1, new JLabel("Šifra:"));
        UiUtil.dodaj(panel, gbc, 2, 6, 1, tfSifra);
        UiUtil.dodaj(panel, gbc, 1, 7, 2, btnPrijava);

        btnPrijava.addActionListener(e -> prijava());
        getRootPane().setDefaultButton(btnPrijava);

        setContentPane(panel);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void prijava() {
        try {
            String login = tfLogin.getText().trim();
            String sifra = new String(tfSifra.getPassword());
            if (login.isEmpty() || sifra.isEmpty()) {
                throw new IllegalArgumentException("Unesite korisničko ime i šifru!");
            }
            Korisnik korisnik = Baza.get().prijava(login, sifra);

            JFrame glavni;
            switch (korisnik.getUloga()) {
                case ADMINISTRATOR:
                    glavni = new AdminFrame(korisnik);
                    break;
                case PRODAVAC:
                    glavni = new ProdavacFrame(korisnik);
                    break;
                default:
                    glavni = new MenadzerFrame(korisnik);
                    break;
            }
            glavni.setVisible(true);
            dispose();
        } catch (IllegalArgumentException ex) {
            UiUtil.greska(this, ex.getMessage());
            tfSifra.setText("");
        }
    }
}
