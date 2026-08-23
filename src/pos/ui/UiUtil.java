package pos.ui;

import pos.model.Korisnik;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.Comparator;

public class UiUtil {

    public static DefaultTableModel model(String... kolone) {
        return new DefaultTableModel(kolone, 0) {
            @Override
            public boolean isCellEditable(int red, int kolona) { return false; }
        };
    }

    public static JTable tabela(DefaultTableModel model, String porukaKadPrazna) {
        JTable tabela = new JTable(model) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getRowCount() == 0 && porukaKadPrazna != null) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(Color.GRAY);
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(porukaKadPrazna)) / 2;
                    g2.drawString(porukaKadPrazna, Math.max(4, x), Math.max(20, getHeight() / 3));
                }
            }
        };
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        // da se brojevi ne sortiraju kao tekst
        Comparator<Object> comp = (a, b) -> {
            Double x = kaoBroj(a);
            Double y = kaoBroj(b);
            if (x != null && y != null) {
                return x.compareTo(y);
            }
            return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
        };
        for (int i = 0; i < model.getColumnCount(); i++) {
            sorter.setComparator(i, comp);
        }
        tabela.setRowSorter(sorter);
        return tabela;
    }

    private static Double kaoBroj(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    // kucanje u combo boxu skace na stavku koja pocinje (ili sadrzi) ukucano
    public static void pretraziv(JComboBox<?> combo) {
        combo.setKeySelectionManager(new JComboBox.KeySelectionManager() {
            private String upit = "";
            private long zadnji = 0;

            @Override
            public int selectionForKey(char znak, ComboBoxModel<?> model) {
                long sada = System.currentTimeMillis();
                if (sada - zadnji > 900) {
                    upit = "";
                }
                zadnji = sada;
                upit = upit + Character.toLowerCase(znak);
                for (int prolaz = 0; prolaz < 2; prolaz++) {
                    for (int i = 0; i < model.getSize(); i++) {
                        String n = String.valueOf(model.getElementAt(i)).toLowerCase();
                        if (prolaz == 0 ? n.startsWith(upit) : n.contains(upit)) {
                            return i;
                        }
                    }
                }
                return -1;
            }
        });
        combo.setToolTipText("Kucajte za pretragu stavki");
    }

    public static JButton dugme(String tekst, String ikona) {
        JButton btn = new JButton(tekst, Ikone.ikona(ikona, 14));
        btn.setIconTextGap(6);
        return btn;
    }

    public static void precica(JRootPane root, String tipka, JButton dugme) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(tipka), tipka);
        root.getActionMap().put(tipka, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dugme.doClick();
            }
        });
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
        JLabel lKorisnik = new JLabel("Prijavljeni korisnik: " + korisnik.getIme() + " (" + korisnik.getUloga() + ")",
                Ikone.ikona("osoba", 14), SwingConstants.LEADING);
        JButton btnOdjava = dugme("Odjava", "odjava");
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
