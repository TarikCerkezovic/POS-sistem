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
        // klikom na zaglavlje se sortira; prevlacenje kolona je iskljuceno jer
        // kod citanja odabranog reda racunamo da je prva kolona uvijek kljuc
        tabela.getTableHeader().setReorderingAllowed(false);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        // da se brojevi i datumi ne sortiraju kao tekst
        Comparator<Object> comp = (a, b) -> {
            Double x = kaoBroj(a);
            Double y = kaoBroj(b);
            if (x != null && y != null) {
                return x.compareTo(y);
            }
            String datumX = kaoDatumKljuc(a);
            String datumY = kaoDatumKljuc(b);
            if (datumX != null && datumY != null) {
                return datumX.compareTo(datumY);
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

    // "dd.MM.gggg" ili "dd.MM.gggg HH:mm" pretvori u "gggg.MM.dd HH:mm" da se
    // poredi hronoloski, a ne kao tekst (inace 01.09. ispadne prije 02.01.)
    private static String kaoDatumKljuc(Object o) {
        String s = String.valueOf(o);
        if (s.length() < 10 || s.charAt(2) != '.' || s.charAt(5) != '.') {
            return null;
        }
        for (int i = 0; i < 10; i++) {
            if (i != 2 && i != 5 && !Character.isDigit(s.charAt(i))) {
                return null;
            }
        }
        return s.substring(6, 10) + "." + s.substring(3, 5) + "." + s.substring(0, 2) + s.substring(10);
    }

    // polje pretrage kojem prijedloge cine postojece vrijednosti iz zadanih kolona
    public static PoljePretrage poljePretrage(int sirina, String opis, String tabela, String kolone) {
        pos.data.Baza baza = pos.data.Baza.get();
        return new PoljePretrage(sirina, opis,
                upit -> baza.prijedloziVrijednosti(tabela, kolone, upit, 10));
    }

    // najcesci slucaj: pretraga artikala po sifri, nazivu ili proizvodjacu
    public static PoljePretrage poljeArtikla(int sirina) {
        return poljePretrage(sirina, "Šifra, naziv ili proizvođač - prijedlozi iskaču dok kucate "
                + "(strelice biraju, Enter filtrira)", "artikal", "sifra;naziv;proizvodjac");
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
                upit = upit + pos.data.Baza.kljucPretrage(String.valueOf(znak));
                // prvi prolaz trazi stavke koje pocinju ukucanim, drugi one koje ga sadrze
                for (int prolaz = 0; prolaz < 2; prolaz++) {
                    for (int i = 0; i < model.getSize(); i++) {
                        String n = pos.data.Baza.kljucPretrage(String.valueOf(model.getElementAt(i)));
                        if (prolaz == 0 && n.startsWith(upit)) {
                            return i;
                        }
                        if (prolaz == 1 && n.contains(upit)) {
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

    // poruka nakon snimanja PDF izvjestaja, sa mogucnoscu da se odmah otvori
    public static void izvjestajSnimljen(Component roditelj, java.io.File fajl) {
        boolean otvori = potvrda(roditelj,
                "Izvještaj je snimljen:\n" + fajl.getAbsolutePath() + "\n\nOtvoriti ga sada?");
        if (!otvori) {
            return;
        }
        try {
            Desktop.getDesktop().open(fajl);
        } catch (Exception e) {
            greska(roditelj, "Nije moguće otvoriti PDF: " + e.getMessage());
        }
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

    // puni combo kategorijama sa punom putanjom (npr. "Hrana > Slatki program > Čokolade"),
    // sortirano po putanji; prva stavka je tekst za "bez filtera" (moze biti null da se izostavi)
    public static void napuniKategorijePutanje(JComboBox<Object> combo, pos.data.Baza baza, String prvaStavka) {
        Object odabrano = combo.getSelectedItem();
        combo.removeAllItems();
        if (prvaStavka != null) {
            combo.addItem(prvaStavka);
        }
        java.util.List<pos.model.Kategorija> kategorije = new java.util.ArrayList<>(baza.getKategorije());
        kategorije.sort((a, b) -> baza.putanjaKategorije(a.getId())
                .compareToIgnoreCase(baza.putanjaKategorije(b.getId())));
        for (pos.model.Kategorija k : kategorije) {
            combo.addItem(k);
        }
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object vrijednost, int indeks,
                                                          boolean odabranaStavka, boolean fokus) {
                super.getListCellRendererComponent(lista, vrijednost, indeks, odabranaStavka, fokus);
                if (vrijednost instanceof pos.model.Kategorija) {
                    setText(baza.putanjaKategorije(((pos.model.Kategorija) vrijednost).getId()));
                }
                return this;
            }
        });
        // zadrzi prethodni izbor ako jos postoji
        if (odabrano instanceof pos.model.Kategorija) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object o = combo.getItemAt(i);
                if (o instanceof pos.model.Kategorija
                        && ((pos.model.Kategorija) o).getId() == ((pos.model.Kategorija) odabrano).getId()) {
                    combo.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    // odabrana kategorija iz comba napunjenog sa napuniKategorijePutanje, ili null za "sve"
    public static Integer odabranaKategorija(JComboBox<Object> combo) {
        Object o = combo.getSelectedItem();
        if (o instanceof pos.model.Kategorija) {
            return ((pos.model.Kategorija) o).getId();
        }
        return null;
    }

    // FET logo kao ikonica prozora u taskbaru (isti kao na PDF racunu)
    public static void ikonaProzora(JFrame prozor) {
        prozor.setIconImage(pos.util.Slike.logo(64));
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
