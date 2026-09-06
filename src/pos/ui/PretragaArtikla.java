package pos.ui;

import pos.data.Baza;
import pos.data.Filteri;
import pos.model.Artikal;
import pos.util.Util;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;

// polje za izbor artikla: dok se kuca, ispod iskacu slicni artikli iz baze.
// strelice biraju iz liste, Enter uzima odabrani (ili prvi) prijedlog, klik
// misem radi isto. za razliku od PoljePretrage, ovdje se mora odabrati
// postojeci artikal - vraca se sam objekat, a ne tekst
public class PretragaArtikla extends JPanel {

    private static final int BROJ_PRIJEDLOGA = 10;
    private static final int PAUZA_MS = 150;

    private final Baza baza = Baza.get();
    private final JTextField polje = new JTextField(18);
    private final JButton btnOcisti = new JButton("✕");
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<Artikal> modelListe = new DefaultListModel<>();
    private final JList<Artikal> lista = new JList<>(modelListe);
    private final JScrollPane skrol = new JScrollPane(lista);
    private final javax.swing.Timer pauza;
    private Artikal odabrani = null;
    private boolean programskaPromjena = false;
    private java.util.function.Consumer<Artikal> naOdabir;

    public PretragaArtikla() {
        super(new BorderLayout(2, 0));
        setOpaque(false);
        add(polje, BorderLayout.CENTER);
        polje.setToolTipText("Kucajte šifru ili naziv - prijedlozi iskaču dok pišete "
                + "(strelice biraju, Enter potvrđuje)");

        btnOcisti.setMargin(new Insets(0, 3, 0, 3));
        btnOcisti.setFocusable(false);
        btnOcisti.setToolTipText("Očisti izbor");
        btnOcisti.setVisible(false);
        add(btnOcisti, BorderLayout.EAST);
        btnOcisti.addActionListener(e -> {
            ocisti();
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

        pauza = new javax.swing.Timer(PAUZA_MS, e -> napuniPrijedloge());
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

        lista.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int indeks = lista.locationToIndex(e.getPoint());
                if (indeks >= 0) {
                    odaberi(modelListe.get(indeks));
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

    // sta se radi kad artikal bude odabran (npr. skok na polje za kolicinu)
    public void naOdabir(java.util.function.Consumer<Artikal> akcija) {
        this.naOdabir = akcija;
    }

    public Artikal odabraniObavezan() {
        if (odabrani == null) {
            throw new IllegalArgumentException(
                    "Odaberite artikal iz liste prijedloga (kucajte pa kliknite na artikal)!");
        }
        return odabrani;
    }

    public void ocisti() {
        odabrani = null;
        programskaPromjena = true;
        polje.setText("");
        programskaPromjena = false;
        btnOcisti.setVisible(false);
        zatvori();
    }

    @Override
    public void requestFocus() {
        polje.requestFocus();
    }

    private void promjenaTeksta() {
        btnOcisti.setVisible(!polje.getText().isEmpty());
        if (programskaPromjena) {
            return;
        }
        // rucna izmjena ponistava prethodni izbor
        odabrani = null;
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
            int indeks = lista.getSelectedIndex();
            if (popup.isVisible() && indeks >= 0) {
                odaberi(modelListe.get(indeks));
            } else if (popup.isVisible() && !modelListe.isEmpty()) {
                // bez izbora strelicama uzima se prvi prijedlog sa liste
                odaberi(modelListe.get(0));
            }
            e.consume();
        } else if (kod == java.awt.event.KeyEvent.VK_ESCAPE) {
            zatvori();
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

    private void napuniPrijedloge() {
        if (!polje.isEnabled() || !polje.isShowing()) {
            return;
        }
        Filteri.FilterArtikala f = new Filteri.FilterArtikala();
        f.tekst = polje.getText().trim();
        List<Artikal> prijedlozi = baza.artikliFiltrirano(f, 0, BROJ_PRIJEDLOGA);
        modelListe.clear();
        for (Artikal a : prijedlozi) {
            modelListe.addElement(a);
        }
        if (modelListe.isEmpty()) {
            zatvori();
            return;
        }
        lista.clearSelection();
        lista.setVisibleRowCount(Math.min(modelListe.size(), 10));
        int sirina = Math.max(getWidth(), 320);
        skrol.setPreferredSize(new Dimension(sirina, lista.getPreferredSize().height + 6));
        popup.pack();
        popup.show(this, 0, getHeight());
        polje.requestFocusInWindow();
    }

    private void zatvori() {
        pauza.stop();
        popup.setVisible(false);
        lista.clearSelection();
    }

    private void odaberi(Artikal a) {
        odabrani = a;
        programskaPromjena = true;
        polje.setText(a.getSifra() + " - " + a.getNaziv());
        programskaPromjena = false;
        btnOcisti.setVisible(true);
        zatvori();
        if (naOdabir != null) {
            naOdabir.accept(a);
        }
    }

    @Override
    public void setEnabled(boolean ukljucen) {
        super.setEnabled(ukljucen);
        polje.setEnabled(ukljucen);
        btnOcisti.setEnabled(ukljucen);
    }

    // u listi se vidi sifra, naziv, stanje i cijena
    private class Prikaz extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object vrijednost, int indeks,
                                                      boolean odabrano, boolean fokus) {
            super.getListCellRendererComponent(l, vrijednost, indeks, odabrano, fokus);
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            if (vrijednost instanceof Artikal) {
                Artikal a = (Artikal) vrijednost;
                setText(a.getSifra() + " - " + a.getNaziv() + "   (stanje: " + a.getStanje()
                        + " " + a.getJedinicaMjere() + ", " + Util.km(a.getCijena()) + " KM)");
            }
            return this;
        }
    }
}
