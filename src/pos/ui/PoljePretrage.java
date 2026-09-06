package pos.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// polje za pretragu u filterima: dok se kuca, ispod iskace lista postojecih
// vrijednosti iz baze i nedavno trazenih pojmova. strelice biraju stavku,
// Enter primjenjuje filter, Escape zatvara listu, dugme "x" cisti pretragu
public class PoljePretrage extends JPanel {

    // izvor prijedloga - vraca vrijednosti koje odgovaraju ukucanom tekstu
    public interface Prijedlozi {
        List<String> za(String upit);
    }

    private static final int BROJ_PRIJEDLOGA = 10;
    private static final int BROJ_NEDAVNIH = 5;
    // kratka pauza da se ne udara u bazu na svaki pritisak tipke
    private static final int PAUZA_MS = 180;

    private final JTextField polje;
    private final JButton btnOcisti = new JButton("✕");
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<String> modelListe = new DefaultListModel<>();
    private final JList<String> lista = new JList<>(modelListe);
    private final JScrollPane skrol = new JScrollPane(lista);
    // nedavne pretrage ovog polja (najnovija prva), samo do izlaska iz programa
    private final List<String> nedavne = new ArrayList<>();
    private final Set<String> nedavnoUListi = new HashSet<>();
    private final Prijedlozi izvor;
    private final Timer pauza;
    private Runnable naPrimjenu;
    private boolean programskaPromjena = false;

    public PoljePretrage(int sirina, String opis, Prijedlozi izvor) {
        super(new BorderLayout(2, 0));
        this.izvor = izvor;
        polje = new JTextField(sirina);
        setOpaque(false);
        polje.setToolTipText(opis);
        add(polje, BorderLayout.CENTER);

        btnOcisti.setMargin(new Insets(0, 3, 0, 3));
        btnOcisti.setFocusable(false);
        btnOcisti.setToolTipText("Očisti pretragu");
        btnOcisti.setVisible(false);
        add(btnOcisti, BorderLayout.EAST);
        btnOcisti.addActionListener(e -> {
            ocisti();
            primijeni();
            polje.requestFocusInWindow();
        });

        popup.setFocusable(false);
        popup.setLayout(new BorderLayout());
        lista.setFocusable(false);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setCellRenderer(new Prikaz());
        skrol.setBorder(BorderFactory.createEmptyBorder());
        skrol.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        popup.add(skrol, BorderLayout.CENTER);

        pauza = new Timer(PAUZA_MS, e -> napuniPrijedloge());
        pauza.setRepeats(false);

        polje.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                promjenaTeksta();
            }

            public void removeUpdate(DocumentEvent e) {
                promjenaTeksta();
            }

            public void changedUpdate(DocumentEvent e) {
                promjenaTeksta();
            }
        });

        // klik bira stavku i odmah filtrira, prelaz misem je samo osvjetljava
        lista.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int indeks = lista.locationToIndex(e.getPoint());
                if (indeks >= 0) {
                    uzmiStavku(indeks);
                }
            }
        });
        lista.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int indeks = lista.locationToIndex(e.getPoint());
                if (indeks >= 0) {
                    lista.setSelectedIndex(indeks);
                }
            }
        });

        polje.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                tipka(e);
            }
        });
        polje.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                zatvori();
            }
        });
    }

    // sta se radi kad korisnik primijeni pretragu (Enter, izbor iz liste, ciscenje)
    public void naPrimjenu(Runnable akcija) {
        this.naPrimjenu = akcija;
    }

    public String getText() {
        return polje.getText();
    }

    private void setText(String tekst) {
        programskaPromjena = true;
        polje.setText(tekst);
        programskaPromjena = false;
        btnOcisti.setVisible(!polje.getText().isEmpty());
    }

    public boolean prazno() {
        return polje.getText().trim().isEmpty();
    }

    // isprazni polje bez pokretanja filtriranja (za dugme "Poništi")
    public void ocisti() {
        setText("");
        zatvori();
    }

    // polje unutar omotaca - red filtera na njega vezuje svoje osluskivace
    public JTextField polje() {
        return polje;
    }

    @Override
    public void setEnabled(boolean ukljucen) {
        super.setEnabled(ukljucen);
        polje.setEnabled(ukljucen);
        btnOcisti.setEnabled(ukljucen);
    }

    @Override
    public void requestFocus() {
        polje.requestFocus();
    }

    private void primijeni() {
        String tekst = polje.getText().trim();
        if (!tekst.isEmpty()) {
            zapamti(tekst);
        }
        if (naPrimjenu != null) {
            naPrimjenu.run();
        }
    }

    private void zapamti(String tekst) {
        nedavne.remove(tekst);
        nedavne.add(0, tekst);
        while (nedavne.size() > BROJ_NEDAVNIH) {
            nedavne.remove(nedavne.size() - 1);
        }
    }

    private void promjenaTeksta() {
        btnOcisti.setVisible(!polje.getText().isEmpty());
        if (programskaPromjena) {
            return;
        }
        // prazno polje sklanja listu; strelicom dolje se moze ponovo otvoriti
        if (polje.getText().trim().isEmpty()) {
            zatvori();
            return;
        }
        pauza.restart();
    }

    private void tipka(java.awt.event.KeyEvent e) {
        int kod = e.getKeyCode();
        if (kod == java.awt.event.KeyEvent.VK_DOWN) {
            if (!popup.isVisible()) {
                napuniPrijedloge();
            } else {
                pomjeriIzbor(1);
            }
            e.consume();
        } else if (kod == java.awt.event.KeyEvent.VK_UP) {
            if (popup.isVisible()) {
                pomjeriIzbor(-1);
            }
            e.consume();
        } else if (kod == java.awt.event.KeyEvent.VK_ENTER) {
            int odabran = lista.getSelectedIndex();
            if (popup.isVisible() && odabran >= 0) {
                uzmiStavku(odabran);
            } else {
                zatvori();
                primijeni();
            }
            e.consume();
        } else if (kod == java.awt.event.KeyEvent.VK_ESCAPE) {
            if (popup.isVisible()) {
                zatvori();
            } else if (!polje.getText().isEmpty()) {
                ocisti();
                primijeni();
            }
            e.consume();
        }
    }

    private void pomjeriIzbor(int smjer) {
        if (modelListe.isEmpty()) {
            return;
        }
        int novi = lista.getSelectedIndex() + smjer;
        if (novi < 0) {
            novi = modelListe.size() - 1;
        } else if (novi >= modelListe.size()) {
            novi = 0;
        }
        lista.setSelectedIndex(novi);
        lista.ensureIndexIsVisible(novi);
    }

    private void uzmiStavku(int indeks) {
        String vrijednost = modelListe.get(indeks);
        setText(vrijednost);
        zatvori();
        primijeni();
    }

    private void zatvori() {
        pauza.stop();
        popup.setVisible(false);
        lista.clearSelection();
    }

    private void napuniPrijedloge() {
        if (!polje.isEnabled() || !polje.isShowing()) {
            return;
        }
        String upit = polje.getText().trim();
        List<String> vrijednosti = new ArrayList<>();
        nedavnoUListi.clear();
        // nedavno trazeni pojmovi idu na vrh liste
        for (String staro : nedavne) {
            if (vrijednosti.size() >= BROJ_NEDAVNIH) {
                break;
            }
            if (upit.isEmpty() || staro.toLowerCase().contains(upit.toLowerCase())) {
                if (!staro.equalsIgnoreCase(upit)) {
                    vrijednosti.add(staro);
                    nedavnoUListi.add(staro);
                }
            }
        }
        for (String prijedlog : izvor.za(upit)) {
            if (vrijednosti.size() >= BROJ_PRIJEDLOGA + nedavnoUListi.size()) {
                break;
            }
            if (!vrijednosti.contains(prijedlog)) {
                vrijednosti.add(prijedlog);
            }
        }
        modelListe.clear();
        for (String v : vrijednosti) {
            modelListe.addElement(v);
        }
        if (modelListe.isEmpty()) {
            zatvori();
            return;
        }
        lista.clearSelection();
        int redova = Math.min(modelListe.size(), 10);
        lista.setVisibleRowCount(redova);
        int sirina = Math.max(getWidth(), 240);
        skrol.setPreferredSize(new Dimension(sirina, lista.getPreferredSize().height + 6));
        popup.pack();
        popup.show(this, 0, getHeight());
        // fokus mora ostati u polju da se moze nastaviti kucanje
        polje.requestFocusInWindow();
    }

    // nedavne pretrage se pisu kurzivom, da se razlikuju od vrijednosti iz baze
    private class Prikaz extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object vrijednost, int indeks,
                                                      boolean odabrano, boolean fokus) {
            super.getListCellRendererComponent(l, vrijednost, indeks, odabrano, fokus);
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            if (nedavnoUListi.contains(String.valueOf(vrijednost))) {
                setFont(getFont().deriveFont(Font.ITALIC));
                setText(String.valueOf(vrijednost) + "   (nedavno)");
            } else {
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            return this;
        }
    }
}
