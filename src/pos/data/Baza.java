package pos.data;

import pos.model.*;
import pos.util.Util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class Baza {

    public static final double PDV = Racun.PDV_STOPA;
    // WAL: bez njega SQLite fsync-uje na svaku transakciju pa je upis ~100x sporiji
    // (isti parametri stoje i u persistence.xml)
    private static final String URL = "jdbc:sqlite:pos.db?journal_mode=WAL&synchronous=NORMAL";
    private static Baza instanca;

    public static synchronized Baza get() {
        if (instanca == null) {
            instanca = new Baza();
        }
        return instanca;
    }

    private Baza() {
        kreirajTabele();
        JPA.emf();
        long brojKorisnika = prebrojKorisnike();
        if (brojKorisnika == 0) {
            // punjenje potraje par sekundi, pa se javi u konzolu
            System.out.println("Prazna baza - punim testne podatke...");
            long pocetak = System.currentTimeMillis();
            popuniTestnePodatke();
            System.out.println("Testni podaci su spremni ("
                    + (System.currentTimeMillis() - pocetak) + " ms).");
        }
    }

    private long prebrojKorisnike() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT COUNT(k) FROM Korisnik k", Long.class)
                    .getSingleResult();
        } finally {
            em.close();
        }
    }

    private void kreirajTabele() {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS korisnik (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                ime            TEXT NOT NULL,
                korisnicko_ime TEXT NOT NULL UNIQUE,
                lozinka        TEXT NOT NULL,
                uloga          TEXT NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS kategorija (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                naziv            TEXT NOT NULL,
                nadkategorija_id INTEGER REFERENCES kategorija(id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS dobavljac (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                naziv   TEXT NOT NULL,
                adresa  TEXT,
                telefon TEXT,
                email   TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS artikal (
                sifra          TEXT PRIMARY KEY,
                naziv          TEXT NOT NULL,
                kategorija_id  INTEGER NOT NULL REFERENCES kategorija(id),
                jedinica_mjere TEXT,
                proizvodjac    TEXT,
                stanje         INTEGER NOT NULL DEFAULT 0,
                cijena         REAL NOT NULL,
                dobavljac_id   INTEGER NOT NULL REFERENCES dobavljac(id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS akcija (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                sifra_artikla TEXT REFERENCES artikal(sifra)
                              ON UPDATE CASCADE ON DELETE CASCADE,
                kategorija_id INTEGER REFERENCES kategorija(id) ON DELETE CASCADE,
                od_datuma    TEXT NOT NULL,
                do_datuma    TEXT NOT NULL,
                popust       REAL NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS nabavka (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                datum        TEXT NOT NULL,
                dobavljac_id INTEGER NOT NULL REFERENCES dobavljac(id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS stavka_nabavke (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                nabavka_id    INTEGER NOT NULL REFERENCES nabavka(id),
                sifra_artikla TEXT NOT NULL,
                naziv_artikla TEXT NOT NULL,
                kolicina      INTEGER NOT NULL,
                nabavna_cijena REAL NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS otpis (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                datum         TEXT NOT NULL,
                sifra_artikla TEXT NOT NULL,
                naziv_artikla TEXT NOT NULL,
                kolicina      INTEGER NOT NULL,
                razlog        TEXT NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS racun (
                broj           TEXT PRIMARY KEY,
                vrijeme        TEXT NOT NULL,
                prodavac       TEXT NOT NULL,
                nacin_placanja TEXT NOT NULL,
                predato        REAL NOT NULL,
                povrat_novca   REAL NOT NULL,
                storniran      INTEGER NOT NULL DEFAULT 0
            )""",
            """
            CREATE TABLE IF NOT EXISTS stavka_racuna (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                broj_racuna   TEXT NOT NULL REFERENCES racun(broj),
                sifra_artikla TEXT NOT NULL,
                naziv_artikla TEXT NOT NULL,
                kolicina      INTEGER NOT NULL,
                cijena        REAL NOT NULL,
                popust        REAL NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS povrat (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                broj_racuna   TEXT NOT NULL REFERENCES racun(broj),
                sifra_artikla TEXT NOT NULL,
                naziv_artikla TEXT NOT NULL,
                kolicina      INTEGER NOT NULL,
                iznos         REAL NOT NULL,
                vrijeme       TEXT NOT NULL,
                prodavac      TEXT NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS brojac (
                naziv     TEXT PRIMARY KEY,
                vrijednost INTEGER NOT NULL
            )"""
        };
        // indeksi da upiti sa filterima rade brzo i kad tabele narastu na milione redova
        String[] indeksi = {
            "CREATE INDEX IF NOT EXISTS idx_racun_vrijeme ON racun(vrijeme)",
            "CREATE INDEX IF NOT EXISTS idx_racun_prodavac ON racun(prodavac)",
            "CREATE INDEX IF NOT EXISTS idx_stavka_racuna_broj ON stavka_racuna(broj_racuna)",
            "CREATE INDEX IF NOT EXISTS idx_stavka_racuna_sifra ON stavka_racuna(sifra_artikla)",
            "CREATE INDEX IF NOT EXISTS idx_povrat_broj ON povrat(broj_racuna)",
            "CREATE INDEX IF NOT EXISTS idx_povrat_vrijeme ON povrat(vrijeme)",
            "CREATE INDEX IF NOT EXISTS idx_artikal_kategorija ON artikal(kategorija_id)",
            "CREATE INDEX IF NOT EXISTS idx_artikal_dobavljac ON artikal(dobavljac_id)",
            "CREATE INDEX IF NOT EXISTS idx_artikal_naziv ON artikal(naziv)",
            "CREATE INDEX IF NOT EXISTS idx_akcija_sifra ON akcija(sifra_artikla)",
            "CREATE INDEX IF NOT EXISTS idx_akcija_kategorija ON akcija(kategorija_id)",
            "CREATE INDEX IF NOT EXISTS idx_akcija_datumi ON akcija(od_datuma, do_datuma)",
            "CREATE INDEX IF NOT EXISTS idx_nabavka_datum ON nabavka(datum)",
            "CREATE INDEX IF NOT EXISTS idx_nabavka_dobavljac ON nabavka(dobavljac_id)",
            "CREATE INDEX IF NOT EXISTS idx_stavka_nabavke_nabavka ON stavka_nabavke(nabavka_id)",
            "CREATE INDEX IF NOT EXISTS idx_otpis_datum ON otpis(datum)",
            "CREATE INDEX IF NOT EXISTS idx_kategorija_nad ON kategorija(nadkategorija_id)"
        };
        try (Connection veza = DriverManager.getConnection(URL);
             Statement s = veza.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            for (String sql : ddl) {
                s.execute(sql);
            }
            migrirajAkcije(s);
            for (String sql : indeksi) {
                s.execute(sql);
            }
            s.execute("INSERT OR IGNORE INTO brojac(naziv, vrijednost) VALUES ('racun', 0)");
            // sifre artikala generise aplikacija; brojac krece od 1000 pa je prva 1001
            s.execute("INSERT OR IGNORE INTO brojac(naziv, vrijednost) VALUES ('artikal', 1000)");
        } catch (SQLException e) {
            throw new RuntimeException("Nije moguće otvoriti bazu podataka: " + e.getMessage(), e);
        }
    }

    // starije baze imaju tabelu akcija bez kolone kategorija_id (i sa NOT NULL sifrom
    // artikla) - takva tabela se prepravi uz zadrzavanje postojecih akcija
    private void migrirajAkcije(Statement s) throws SQLException {
        boolean imaKategoriju = false;
        try (java.sql.ResultSet rs = s.executeQuery("PRAGMA table_info(akcija)")) {
            while (rs.next()) {
                if ("kategorija_id".equals(rs.getString("name"))) {
                    imaKategoriju = true;
                }
            }
        }
        if (imaKategoriju) {
            return;
        }
        s.execute("ALTER TABLE akcija RENAME TO akcija_staro");
        s.execute("""
                CREATE TABLE akcija (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    sifra_artikla TEXT REFERENCES artikal(sifra)
                                  ON UPDATE CASCADE ON DELETE CASCADE,
                    kategorija_id INTEGER REFERENCES kategorija(id) ON DELETE CASCADE,
                    od_datuma    TEXT NOT NULL,
                    do_datuma    TEXT NOT NULL,
                    popust       REAL NOT NULL
                )""");
        s.execute("""
                INSERT INTO akcija(id, sifra_artikla, kategorija_id, od_datuma, do_datuma, popust)
                SELECT id, sifra_artikla, NULL, od_datuma, do_datuma, popust FROM akcija_staro""");
        s.execute("DROP TABLE akcija_staro");
    }

    private long broj(String jpql, String parametar, Object vrijednost) {
        EntityManager em = JPA.em();
        try {
            return em.createQuery(jpql, Long.class)
                    .setParameter(parametar, vrijednost)
                    .getSingleResult();
        } finally {
            em.close();
        }
    }

    public List<Korisnik> getKorisnici() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT k FROM Korisnik k ORDER BY k.id", Korisnik.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Korisnik prijava(String korisnickoIme, String lozinka) {
        Korisnik k;
        EntityManager em = JPA.em();
        try {
            List<Korisnik> lista = em.createQuery(
                    "SELECT k FROM Korisnik k WHERE k.korisnickoIme = :ime AND k.lozinka = :lozinka",
                    Korisnik.class)
                    .setParameter("ime", korisnickoIme)
                    .setParameter("lozinka", lozinka)
                    .getResultList();
            if (lista.isEmpty()) {
                k = null;
            } else {
                k = lista.get(0);
            }
        } finally {
            em.close();
        }
        if (k == null) {
            throw new IllegalArgumentException("Pogrešno korisničko ime ili šifra!");
        }
        return k;
    }

    public Korisnik dodajKorisnika(String ime, String korisnickoIme, String lozinka, Uloga uloga) {
        provjeriKorisnika(ime, korisnickoIme, lozinka, null);
        Korisnik k = new Korisnik(0, ime.trim(), korisnickoIme.trim(), lozinka, uloga);
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            em.persist(k);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        return k;
    }

    public void izmijeniKorisnika(int id, String ime, String korisnickoIme, String lozinka, Uloga uloga) {
        if (nadjiKorisnika(id) == null) {
            throw new IllegalArgumentException("Korisnik nije pronađen!");
        }
        provjeriKorisnika(ime, korisnickoIme, lozinka, id);
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Korisnik k = em.find(Korisnik.class, id);
            if (k == null) {
                throw new IllegalArgumentException("Korisnik nije pronađen!");
            }
            k.setIme(ime.trim());
            k.setKorisnickoIme(korisnickoIme.trim());
            k.setLozinka(lozinka);
            k.setUloga(uloga);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private void provjeriKorisnika(String ime, String korisnickoIme, String lozinka, Integer zaIzmjenuId) {
        if (ime.trim().isEmpty()) {
            throw new IllegalArgumentException("Ime i prezime ne smiju biti prazni!");
        }
        if (korisnickoIme.trim().isEmpty()) {
            throw new IllegalArgumentException("Korisničko ime ne smije biti prazno!");
        }
        if (lozinka.isEmpty()) {
            throw new IllegalArgumentException("Šifra ne smije biti prazna!");
        }
        long zauzeto;
        EntityManager em = JPA.em();
        try {
            if (zaIzmjenuId == null) {
                zauzeto = em.createQuery("""
                        SELECT COUNT(k) FROM Korisnik k
                        WHERE LOWER(k.korisnickoIme) = LOWER(:ime)""", Long.class)
                        .setParameter("ime", korisnickoIme.trim())
                        .getSingleResult();
            } else {
                zauzeto = em.createQuery("""
                        SELECT COUNT(k) FROM Korisnik k
                        WHERE LOWER(k.korisnickoIme) = LOWER(:ime) AND k.id <> :id""", Long.class)
                        .setParameter("ime", korisnickoIme.trim())
                        .setParameter("id", zaIzmjenuId)
                        .getSingleResult();
            }
        } finally {
            em.close();
        }
        if (zauzeto > 0) {
            throw new IllegalArgumentException("Korisničko ime \"" + korisnickoIme + "\" već postoji!");
        }
    }

    public void obrisiKorisnika(int id) {
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Korisnik k = em.find(Korisnik.class, id);
            if (k == null) {
                throw new IllegalArgumentException("Korisnik nije pronađen!");
            }
            em.remove(k);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public Korisnik nadjiKorisnika(int id) {
        EntityManager em = JPA.em();
        try {
            return em.find(Korisnik.class, id);
        } finally {
            em.close();
        }
    }

    public List<Kategorija> getKategorije() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT k FROM Kategorija k ORDER BY k.id", Kategorija.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Kategorija nadjiKategoriju(int id) {
        EntityManager em = JPA.em();
        try {
            return em.find(Kategorija.class, id);
        } finally {
            em.close();
        }
    }

    // puna putanja od glavne kategorije do zadane, npr. "Hrana > Slatki program > Čokolade"
    public String putanjaKategorije(int id) {
        Kategorija k = nadjiKategoriju(id);
        if (k == null) {
            return "?";
        }
        StringBuilder putanja = new StringBuilder(k.getNaziv());
        int zastita = 0;
        while (k != null && k.getNadkategorijaId() != null && zastita < 30) {
            k = nadjiKategoriju(k.getNadkategorijaId());
            if (k != null) {
                putanja.insert(0, k.getNaziv() + " > ");
            }
            zastita++;
        }
        return putanja.toString();
    }

    // id zadane kategorije + svih njenih podkategorija, do bilo koje dubine
    public List<Integer> kategorijaSaPodstablom(int id) {
        List<Kategorija> sve = getKategorije();
        List<Integer> rezultat = new ArrayList<>();
        rezultat.add(id);
        // sirinski obilazak: dodaje direktnu djecu vec pronadjenih dok ima novih
        int pocetak = 0;
        while (pocetak < rezultat.size()) {
            int kraj = rezultat.size();
            for (int i = pocetak; i < kraj; i++) {
                int trenutni = rezultat.get(i);
                for (Kategorija k : sve) {
                    if (k.getNadkategorijaId() != null && k.getNadkategorijaId() == trenutni
                            && !rezultat.contains(k.getId())) {
                        rezultat.add(k.getId());
                    }
                }
            }
            pocetak = kraj;
        }
        return rezultat;
    }

    // korijenska (glavna) kategorija u koju zadana spada
    public Integer glavnaKategorijaZa(int id) {
        Kategorija k = nadjiKategoriju(id);
        if (k == null) {
            return null;
        }
        int zastita = 0;
        while (k.getNadkategorijaId() != null && zastita < 30) {
            Kategorija nad = nadjiKategoriju(k.getNadkategorijaId());
            if (nad == null) {
                break;
            }
            k = nad;
            zastita++;
        }
        return k.getId();
    }

    public Kategorija dodajKategoriju(String naziv, Integer nadkategorijaId) {
        if (naziv.trim().isEmpty()) {
            throw new IllegalArgumentException("Naziv kategorije ne smije biti prazan!");
        }
        Kategorija k = new Kategorija(0, naziv.trim(), nadkategorijaId);
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            em.persist(k);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        return k;
    }

    public void izmijeniKategoriju(int id, String naziv, Integer nadkategorijaId) {
        if (nadjiKategoriju(id) == null) {
            throw new IllegalArgumentException("Kategorija nije pronađena!");
        }
        if (naziv.trim().isEmpty()) {
            throw new IllegalArgumentException("Naziv kategorije ne smije biti prazan!");
        }
        if (nadkategorijaId != null && nadkategorijaId == id) {
            throw new IllegalArgumentException("Kategorija ne može biti sama sebi nadkategorija!");
        }
        // nadkategorija ne smije biti ni jedna od podkategorija ove kategorije (nastao bi ciklus)
        if (nadkategorijaId != null && kategorijaSaPodstablom(id).contains(nadkategorijaId)) {
            throw new IllegalArgumentException(
                    "Odabrana nadkategorija je podkategorija ove kategorije - to bi napravilo ciklus!");
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Kategorija k = em.find(Kategorija.class, id);
            if (k == null) {
                throw new IllegalArgumentException("Kategorija nije pronađena!");
            }
            k.setNaziv(naziv.trim());
            k.setNadkategorijaId(nadkategorijaId);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void obrisiKategoriju(int id) {
        if (nadjiKategoriju(id) == null) {
            throw new IllegalArgumentException("Kategorija nije pronađena!");
        }
        if (broj("SELECT COUNT(k) FROM Kategorija k WHERE k.nadkategorijaId = :id", "id", id) > 0) {
            throw new IllegalArgumentException("Kategorija ima podkategorije i ne može se obrisati!");
        }
        if (broj("SELECT COUNT(a) FROM Artikal a WHERE a.kategorijaId = :id", "id", id) > 0) {
            throw new IllegalArgumentException("Postoje artikli u ovoj kategoriji - brisanje nije moguće!");
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Kategorija k = em.find(Kategorija.class, id);
            if (k != null) {
                em.remove(k);
            }
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Dobavljac> getDobavljaci() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT d FROM Dobavljac d ORDER BY d.id", Dobavljac.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Dobavljac nadjiDobavljaca(int id) {
        EntityManager em = JPA.em();
        try {
            return em.find(Dobavljac.class, id);
        } finally {
            em.close();
        }
    }

    public Dobavljac dodajDobavljaca(String naziv, String adresa, String telefon, String email) {
        if (naziv.trim().isEmpty()) {
            throw new IllegalArgumentException("Naziv dobavljača ne smije biti prazan!");
        }
        Dobavljac d = new Dobavljac(0, naziv.trim(), adresa.trim(), telefon.trim(), email.trim());
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            em.persist(d);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        return d;
    }

    public void izmijeniDobavljaca(int id, String naziv, String adresa, String telefon, String email) {
        if (nadjiDobavljaca(id) == null) {
            throw new IllegalArgumentException("Dobavljač nije pronađen!");
        }
        if (naziv.trim().isEmpty()) {
            throw new IllegalArgumentException("Naziv dobavljača ne smije biti prazan!");
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Dobavljac d = em.find(Dobavljac.class, id);
            if (d == null) {
                throw new IllegalArgumentException("Dobavljač nije pronađen!");
            }
            d.setNaziv(naziv.trim());
            d.setAdresa(adresa.trim());
            d.setTelefon(telefon.trim());
            d.setEmail(email.trim());
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void obrisiDobavljaca(int id) {
        if (nadjiDobavljaca(id) == null) {
            throw new IllegalArgumentException("Dobavljač nije pronađen!");
        }
        if (broj("SELECT COUNT(a) FROM Artikal a WHERE a.dobavljacId = :id", "id", id) > 0) {
            throw new IllegalArgumentException("Postoje artikli vezani za ovog dobavljača - brisanje nije moguće!");
        }
        if (broj("SELECT COUNT(n) FROM Nabavka n WHERE n.dobavljacId = :id", "id", id) > 0) {
            throw new IllegalArgumentException("Postoje evidentirane nabavke od ovog dobavljača - brisanje nije moguće!");
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Dobavljac d = em.find(Dobavljac.class, id);
            if (d != null) {
                em.remove(d);
            }
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Artikal> getArtikli() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT a FROM Artikal a ORDER BY a.sifra", Artikal.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Artikal nadjiArtikal(String sifra) {
        EntityManager em = JPA.em();
        try {
            return em.find(Artikal.class, sifra);
        } finally {
            em.close();
        }
    }

    // dodavanje bez sifre: sifru dodjeljuje aplikacija iz brojaca, pa korisnik
    // pri unosu artikla o sifri uopste ne razmislja
    public Artikal dodajArtikal(String naziv, int kategorijaId, String jm,
                                String proizvodjac, double cijena, int dobavljacId) {
        provjeriArtikal("x", naziv, cijena);
        Artikal a;
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Brojac brojac = em.find(Brojac.class, "artikal");
            if (brojac == null) {
                brojac = new Brojac("artikal", 1000);
                em.persist(brojac);
            }
            // preskoci sifre koje su vec zauzete (npr. iz starih rucnih unosa)
            int kandidat = brojac.getVrijednost();
            String sifra;
            do {
                kandidat = kandidat + 1;
                sifra = String.valueOf(kandidat);
            } while (em.find(Artikal.class, sifra) != null);
            brojac.setVrijednost(kandidat);
            a = new Artikal(sifra, naziv.trim(), kategorijaId, jm.trim(),
                    proizvodjac.trim(), 0, Util.round2(cijena), dobavljacId);
            em.persist(a);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        return a;
    }

    public Artikal dodajArtikal(String sifra, String naziv, int kategorijaId, String jm,
                                String proizvodjac, double cijena, int dobavljacId) {
        provjeriArtikal(sifra, naziv, cijena);
        if (nadjiArtikal(sifra.trim()) != null) {
            throw new IllegalArgumentException("Artikal sa šifrom \"" + sifra + "\" već postoji!");
        }
        Artikal a = new Artikal(sifra.trim(), naziv.trim(), kategorijaId, jm.trim(),
                proizvodjac.trim(), 0, Util.round2(cijena), dobavljacId);
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            em.persist(a);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        return a;
    }

    public void izmijeniArtikal(String staraSifra, String sifra, String naziv, int kategorijaId,
                                String jm, String proizvodjac, double cijena, int dobavljacId) {
        if (nadjiArtikal(staraSifra) == null) {
            throw new IllegalArgumentException("Artikal nije pronađen!");
        }
        provjeriArtikal(sifra, naziv, cijena);
        if (!staraSifra.equals(sifra.trim()) && nadjiArtikal(sifra.trim()) != null) {
            throw new IllegalArgumentException("Artikal sa šifrom \"" + sifra + "\" već postoji!");
        }
        if (staraSifra.equals(sifra.trim())) {
            izmijeniArtikalIstaSifra(staraSifra, naziv, kategorijaId, jm, proizvodjac, cijena, dobavljacId);
        } else {
            izmijeniArtikalNovaSifra(staraSifra, sifra, naziv, kategorijaId, jm, proizvodjac, cijena, dobavljacId);
        }
    }

    private void izmijeniArtikalIstaSifra(String staraSifra, String naziv, int kategorijaId,
                                          String jm, String proizvodjac, double cijena, int dobavljacId) {
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Artikal a = em.find(Artikal.class, staraSifra);
            if (a == null) {
                throw new IllegalArgumentException("Artikal nije pronađen!");
            }
            a.setNaziv(naziv.trim());
            a.setKategorijaId(kategorijaId);
            a.setJedinicaMjere(jm.trim());
            a.setProizvodjac(proizvodjac.trim());
            a.setCijena(Util.round2(cijena));
            a.setDobavljacId(dobavljacId);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private void izmijeniArtikalNovaSifra(String staraSifra, String sifra, String naziv, int kategorijaId,
                                          String jm, String proizvodjac, double cijena, int dobavljacId) {
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            em.createNativeQuery("""
                    UPDATE artikal SET sifra = ?1, naziv = ?2, kategorija_id = ?3, jedinica_mjere = ?4,
                           proizvodjac = ?5, cijena = ?6, dobavljac_id = ?7 WHERE sifra = ?8""")
                    .setParameter(1, sifra.trim())
                    .setParameter(2, naziv.trim())
                    .setParameter(3, kategorijaId)
                    .setParameter(4, jm.trim())
                    .setParameter(5, proizvodjac.trim())
                    .setParameter(6, Util.round2(cijena))
                    .setParameter(7, dobavljacId)
                    .setParameter(8, staraSifra)
                    .executeUpdate();
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private void provjeriArtikal(String sifra, String naziv, double cijena) {
        if (sifra.trim().isEmpty()) {
            throw new IllegalArgumentException("Šifra artikla ne smije biti prazna!");
        }
        if (naziv.trim().isEmpty()) {
            throw new IllegalArgumentException("Naziv artikla ne smije biti prazan!");
        }
        if (cijena <= 0) {
            throw new IllegalArgumentException("Cijena mora biti veća od 0!");
        }
    }

    public void obrisiArtikal(String sifra) {
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Artikal a = em.find(Artikal.class, sifra);
            if (a == null) {
                throw new IllegalArgumentException("Artikal nije pronađen!");
            }
            em.remove(a);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Akcija> getAkcije() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT a FROM Akcija a ORDER BY a.id", Akcija.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // sve akcije aktivne na zadani datum, spakovane tako da se popust za artikal
    // rjesava u memoriji: akcija na artikal ima prednost, a od akcija na kategorije
    // vazi ona na najblizoj kategoriji uz lanac nadkategorija
    public static class AktivneAkcije {
        private final Map<String, Double> poArtiklu;
        private final Map<Integer, Double> poKategoriji;
        private final Map<Integer, Integer> nadkategorija;

        AktivneAkcije(Map<String, Double> poArtiklu, Map<Integer, Double> poKategoriji,
                      Map<Integer, Integer> nadkategorija) {
            this.poArtiklu = poArtiklu;
            this.poKategoriji = poKategoriji;
            this.nadkategorija = nadkategorija;
        }

        // vraca procenat popusta ili null ako artikal nije ni na kakvoj akciji
        public Double popustZa(Artikal artikal) {
            Double popust = poArtiklu.get(artikal.getSifra());
            if (popust != null) {
                return popust;
            }
            Integer kategorija = artikal.getKategorijaId();
            int zastita = 0;
            while (kategorija != null && zastita < 30) {
                popust = poKategoriji.get(kategorija);
                if (popust != null) {
                    return popust;
                }
                kategorija = nadkategorija.get(kategorija);
                zastita++;
            }
            return null;
        }
    }

    public AktivneAkcije aktivneAkcije(LocalDate datum) {
        Map<String, Double> poArtiklu = new HashMap<>();
        Map<Integer, Double> poKategoriji = new HashMap<>();
        EntityManager em = JPA.em();
        try {
            List<Akcija> lista = em.createQuery("""
                    SELECT a FROM Akcija a
                    WHERE a.odDatuma <= :datum AND a.doDatuma >= :datum""", Akcija.class)
                    .setParameter("datum", datum)
                    .getResultList();
            for (Akcija a : lista) {
                if (a.getSifraArtikla() != null) {
                    poArtiklu.put(a.getSifraArtikla(), a.getPopustProcenat());
                } else if (a.getKategorijaId() != null) {
                    poKategoriji.put(a.getKategorijaId(), a.getPopustProcenat());
                }
            }
        } finally {
            em.close();
        }
        Map<Integer, Integer> nadkategorija = new HashMap<>();
        for (Kategorija k : getKategorije()) {
            if (k.getNadkategorijaId() != null) {
                nadkategorija.put(k.getId(), k.getNadkategorijaId());
            }
        }
        return new AktivneAkcije(poArtiklu, poKategoriji, nadkategorija);
    }

    // efektivna akcija za artikal: prvo akcija na sam artikal, inace akcija
    // na najblizu kategoriju u lancu nadkategorija
    public Akcija aktivnaAkcija(String sifraArtikla, LocalDate datum) {
        EntityManager em = JPA.em();
        try {
            List<Akcija> naArtikal = em.createQuery("""
                    SELECT a FROM Akcija a
                    WHERE a.sifraArtikla = :sifra AND a.odDatuma <= :datum AND a.doDatuma >= :datum""",
                    Akcija.class)
                    .setParameter("sifra", sifraArtikla)
                    .setParameter("datum", datum)
                    .getResultList();
            if (!naArtikal.isEmpty()) {
                return naArtikal.get(0);
            }
            Artikal artikal = em.find(Artikal.class, sifraArtikla);
            if (artikal == null) {
                return null;
            }
            Integer kategorija = artikal.getKategorijaId();
            int zastita = 0;
            while (kategorija != null && zastita < 30) {
                List<Akcija> naKategoriju = em.createQuery("""
                        SELECT a FROM Akcija a
                        WHERE a.kategorijaId = :kat AND a.odDatuma <= :datum AND a.doDatuma >= :datum""",
                        Akcija.class)
                        .setParameter("kat", kategorija)
                        .setParameter("datum", datum)
                        .getResultList();
                if (!naKategoriju.isEmpty()) {
                    return naKategoriju.get(0);
                }
                Kategorija k = em.find(Kategorija.class, kategorija);
                if (k == null) {
                    break;
                }
                kategorija = k.getNadkategorijaId();
                zastita++;
            }
            return null;
        } finally {
            em.close();
        }
    }

    private static void provjeriAkciju(LocalDate od, LocalDate doD, double popust) {
        if (od.isAfter(doD)) {
            throw new IllegalArgumentException("Datum početka akcije je poslije datuma kraja!");
        }
        if (popust <= 0 || popust >= 100) {
            throw new IllegalArgumentException("Popust mora biti između 1 i 99%!");
        }
    }

    private Akcija snimiAkciju(Akcija akcija) {
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            em.persist(akcija);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        return akcija;
    }

    public Akcija dodajAkciju(String sifraArtikla, LocalDate od, LocalDate doD, double popust) {
        Artikal art = nadjiArtikal(sifraArtikla);
        if (art == null) {
            throw new IllegalArgumentException("Artikal nije pronađen!");
        }
        provjeriAkciju(od, doD, popust);
        long preklapanja;
        EntityManager emProvjera = JPA.em();
        try {
            preklapanja = emProvjera.createQuery("""
                    SELECT COUNT(a) FROM Akcija a
                    WHERE a.sifraArtikla = :sifra AND a.odDatuma <= :doD AND a.doDatuma >= :od""", Long.class)
                    .setParameter("sifra", sifraArtikla)
                    .setParameter("doD", doD)
                    .setParameter("od", od)
                    .getSingleResult();
        } finally {
            emProvjera.close();
        }
        if (preklapanja > 0) {
            throw new IllegalArgumentException("Za ovaj artikal već postoji akcija u zadanom periodu!");
        }
        return snimiAkciju(new Akcija(0, sifraArtikla, null, od, doD, popust));
    }

    // akcija na kategoriju vazi za sve artikle u njoj i u svim podkategorijama;
    // artikli koji imaju svoju akciju zadrzavaju nju (prednost pojedinacne akcije)
    public Akcija dodajAkcijuNaKategoriju(int kategorijaId, LocalDate od, LocalDate doD, double popust) {
        if (nadjiKategoriju(kategorijaId) == null) {
            throw new IllegalArgumentException("Kategorija nije pronađena!");
        }
        provjeriAkciju(od, doD, popust);
        long preklapanja;
        EntityManager emProvjera = JPA.em();
        try {
            preklapanja = emProvjera.createQuery("""
                    SELECT COUNT(a) FROM Akcija a
                    WHERE a.kategorijaId = :kat AND a.odDatuma <= :doD AND a.doDatuma >= :od""", Long.class)
                    .setParameter("kat", kategorijaId)
                    .setParameter("doD", doD)
                    .setParameter("od", od)
                    .getSingleResult();
        } finally {
            emProvjera.close();
        }
        if (preklapanja > 0) {
            throw new IllegalArgumentException("Za ovu kategoriju već postoji akcija u zadanom periodu!");
        }
        return snimiAkciju(new Akcija(0, null, kategorijaId, od, doD, popust));
    }

    public void obrisiAkciju(int id) {
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Akcija a = em.find(Akcija.class, id);
            if (a != null) {
                em.remove(a);
            }
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Nabavka> getNabavke() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT n FROM Nabavka n ORDER BY n.datum, n.id", Nabavka.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Nabavka evidentirajNabavku(int dobavljacId, LocalDate datum, List<StavkaNabavke> stavke) {
        if (nadjiDobavljaca(dobavljacId) == null) {
            throw new IllegalArgumentException("Dobavljač nije pronađen!");
        }
        if (stavke.isEmpty()) {
            throw new IllegalArgumentException("Nabavka mora imati barem jednu stavku!");
        }
        for (StavkaNabavke s : stavke) {
            if (nadjiArtikal(s.getSifraArtikla()) == null) {
                throw new IllegalArgumentException("Artikal " + s.getSifraArtikla() + " nije pronađen!");
            }
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Nabavka n = new Nabavka(0, datum, dobavljacId, new ArrayList<>(stavke));
            for (StavkaNabavke s : n.getStavke()) {
                s.setNabavka(n);
            }
            em.persist(n);
            for (StavkaNabavke s : n.getStavke()) {
                Artikal a = em.find(Artikal.class, s.getSifraArtikla());
                a.setStanje(a.getStanje() + s.getKolicina());
            }
            transakcija.commit();
            return n;
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Otpis> getOtpisi() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT o FROM Otpis o ORDER BY o.datum, o.id", Otpis.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Otpis evidentirajOtpis(String sifraArtikla, int kolicina, String razlog, LocalDate datum) {
        Artikal a = nadjiArtikal(sifraArtikla);
        if (a == null) {
            throw new IllegalArgumentException("Artikal nije pronađen!");
        }
        if (kolicina <= 0) {
            throw new IllegalArgumentException("Količina otpisa mora biti veća od 0!");
        }
        if (kolicina > a.getStanje()) {
            throw new IllegalArgumentException("Količina otpisa (" + kolicina + ") je veća od stanja (" + a.getStanje() + ")!");
        }
        if (razlog.trim().isEmpty()) {
            throw new IllegalArgumentException("Razlog otpisa ne smije biti prazan!");
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Artikal upravljani = em.find(Artikal.class, sifraArtikla);
            upravljani.setStanje(upravljani.getStanje() - kolicina);
            Otpis o = new Otpis(0, datum, sifraArtikla, a.getNaziv(), kolicina, razlog.trim());
            em.persist(o);
            transakcija.commit();
            return o;
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Racun> getRacuni() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT r FROM Racun r ORDER BY r.vrijeme", Racun.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Racun nadjiRacun(String broj) {
        EntityManager em = JPA.em();
        try {
            return em.find(Racun.class, broj);
        } finally {
            em.close();
        }
    }

    public Racun izdajRacun(Korisnik prodavac, List<StavkaRacuna> stavke,
                            String nacinPlacanja, double predato, LocalDateTime vrijeme) {
        if (stavke.isEmpty()) {
            throw new IllegalArgumentException("Korpa je prazna!");
        }
        for (StavkaRacuna s : stavke) {
            Artikal a = nadjiArtikal(s.getSifraArtikla());
            if (a == null) {
                throw new IllegalArgumentException("Artikal " + s.getSifraArtikla() + " nije pronađen!");
            }
            if (s.getKolicina() > a.getStanje()) {
                throw new IllegalArgumentException("Nema dovoljno na stanju: " + a.getNaziv()
                        + " (traženo: " + s.getKolicina() + ", stanje: " + a.getStanje() + ")");
            }
        }
        double ukupno = 0;
        for (StavkaRacuna s : stavke) {
            ukupno = ukupno + s.iznos();
        }
        ukupno = Util.round2(ukupno);

        double predatoKupca = 0;
        double povratNovca = 0;
        if ("GOTOVINA".equals(nacinPlacanja)) {
            if (predato + 1e-9 < ukupno) {
                throw new IllegalArgumentException("Predati iznos (" + Util.km(predato)
                        + " KM) je manji od ukupnog iznosa računa (" + Util.km(ukupno) + " KM)!");
            }
            predatoKupca = Util.round2(predato);
            povratNovca = Util.round2(predato - ukupno);
        }

        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Brojac brojac = em.find(Brojac.class, "racun");
            brojac.setVrijednost(brojac.getVrijednost() + 1);
            String brojRacuna = vrijeme.getYear() + "-" + String.format("%06d", brojac.getVrijednost());

            Racun r = new Racun(brojRacuna, vrijeme, prodavac.getIme(), new ArrayList<>(stavke),
                    nacinPlacanja, predatoKupca, povratNovca);
            for (StavkaRacuna s : r.getStavke()) {
                s.setRacun(r);
            }
            em.persist(r);

            for (StavkaRacuna s : r.getStavke()) {
                Artikal a = em.find(Artikal.class, s.getSifraArtikla());
                a.setStanje(a.getStanje() - s.getKolicina());
            }
            transakcija.commit();
            return r;
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public List<Povrat> getPovrati() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT p FROM Povrat p ORDER BY p.vrijeme, p.id", Povrat.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public int vracenaKolicina(String brojRacuna, String sifraArtikla) {
        EntityManager em = JPA.em();
        try {
            return vracenaKolicina(em, brojRacuna, sifraArtikla);
        } finally {
            em.close();
        }
    }

    private int vracenaKolicina(EntityManager em, String brojRacuna, String sifraArtikla) {
        Long suma = em.createQuery("""
                SELECT SUM(p.kolicina) FROM Povrat p
                WHERE p.brojRacuna = :broj AND p.sifraArtikla = :sifra""", Long.class)
                .setParameter("broj", brojRacuna)
                .setParameter("sifra", sifraArtikla)
                .getSingleResult();
        if (suma == null) {
            return 0;
        }
        return suma.intValue();
    }

    public Povrat evidentirajPovrat(Racun racunUlaz, String sifraArtikla, int kolicina, LocalDateTime vrijeme) {
        if (racunUlaz == null) {
            throw new IllegalArgumentException("Račun nije pronađen!");
        }
        Racun racun = nadjiRacun(racunUlaz.getBroj());
        if (racun == null) {
            throw new IllegalArgumentException("Račun nije pronađen!");
        }
        if (racun.isStorniran()) {
            throw new IllegalArgumentException("Račun je storniran - povrat nije moguć!");
        }
        StavkaRacuna stavka = null;
        for (StavkaRacuna s : racun.getStavke()) {
            if (s.getSifraArtikla().equals(sifraArtikla)) {
                stavka = s;
                break;
            }
        }
        if (stavka == null) {
            throw new IllegalArgumentException("Artikal se ne nalazi na ovom računu!");
        }
        int preostalo = stavka.getKolicina() - vracenaKolicina(racun.getBroj(), sifraArtikla);
        if (kolicina <= 0) {
            throw new IllegalArgumentException("Količina povrata mora biti veća od 0!");
        }
        if (kolicina > preostalo) {
            throw new IllegalArgumentException("Moguće je vratiti najviše " + preostalo + " kom ovog artikla!");
        }
        double iznos = Util.round2(kolicina * stavka.cijenaSaPopustom());

        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Artikal a = em.find(Artikal.class, sifraArtikla);
            // artikal je u medjuvremenu mogao biti obrisan ili mu je promijenjena sifra -
            // povrat novca se svejedno evidentira, samo se stanje nema kome vratiti
            if (a != null) {
                a.setStanje(a.getStanje() + kolicina);
            }
            Povrat p = new Povrat(0, racun.getBroj(), sifraArtikla, stavka.getNazivArtikla(),
                    kolicina, iznos, vrijeme, racun.getProdavac());
            em.persist(p);
            transakcija.commit();
            return p;
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void stornirajRacun(Racun racunUlaz) {
        if (racunUlaz == null) {
            throw new IllegalArgumentException("Račun nije pronađen!");
        }
        Racun provjera = nadjiRacun(racunUlaz.getBroj());
        if (provjera == null) {
            throw new IllegalArgumentException("Račun nije pronađen!");
        }
        if (provjera.isStorniran()) {
            throw new IllegalArgumentException("Račun je već storniran!");
        }
        EntityManager em = JPA.em();
        EntityTransaction transakcija = em.getTransaction();
        try {
            transakcija.begin();
            Racun racun = em.find(Racun.class, racunUlaz.getBroj());
            for (StavkaRacuna s : racun.getStavke()) {
                int preostalo = s.getKolicina() - vracenaKolicina(em, racun.getBroj(), s.getSifraArtikla());
                if (preostalo > 0) {
                    Artikal a = em.find(Artikal.class, s.getSifraArtikla());
                    // ako je artikal u medjuvremenu obrisan iz sifrarnika, storno prolazi
                    // ali se stanje tog artikla nema gdje vratiti
                    if (a != null) {
                        a.setStanje(a.getStanje() + preostalo);
                    }
                }
            }
            racun.setStorniran(true);
            transakcija.commit();
        } catch (RuntimeException e) {
            if (transakcija.isActive()) {
                transakcija.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        racunUlaz.setStorniran(true);
    }

    public List<Racun> racuniURasponu(LocalDate od, LocalDate doD) {
        LocalDateTime pocetak = od.atStartOfDay();
        LocalDateTime kraj = doD.plusDays(1).atStartOfDay();
        EntityManager em = JPA.em();
        try {
            return em.createQuery("""
                    SELECT r FROM Racun r
                    WHERE r.vrijeme >= :pocetak AND r.vrijeme < :kraj
                    ORDER BY r.vrijeme""", Racun.class)
                    .setParameter("pocetak", pocetak)
                    .setParameter("kraj", kraj)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Povrat> povratiURasponu(LocalDate od, LocalDate doD) {
        LocalDateTime pocetak = od.atStartOfDay();
        LocalDateTime kraj = doD.plusDays(1).atStartOfDay();
        EntityManager em = JPA.em();
        try {
            return em.createQuery("""
                    SELECT p FROM Povrat p
                    WHERE p.vrijeme >= :pocetak AND p.vrijeme < :kraj
                    ORDER BY p.vrijeme""", Povrat.class)
                    .setParameter("pocetak", pocetak)
                    .setParameter("kraj", kraj)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // ---------------------------------------------------------------------
    // Napomena o obracunu: storniran racun se ponistava u cijelosti, pa se
    // ni njegovi povrati NE odbijaju od prometa (inace bi se odbili dva puta -
    // jednom kroz storno cijelog racuna i jos jednom kroz povrat).
    // ---------------------------------------------------------------------

    // izraz za iznos stavke racuna, bit-po-bit identican obracunu u StavkaRacuna.iznos():
    // Math.round(v) je floor(v + 0.5), sto je za pozitivne brojeve CAST(v + 0.5 AS INTEGER),
    // dok SQLite ROUND() zaokruzuje malo drugacije pa bi se izvjestaji razilazili sa racunom
    private static final String SQL_IZNOS_STAVKE = """
            (CAST(s.kolicina * (CAST(s.cijena * (1 - s.popust / 100.0) * 100.0 + 0.5 AS INTEGER) / 100.0)
                  * 100.0 + 0.5 AS INTEGER) / 100.0)""";

    // granice vremenskog raspona kao tekst, isto kako konverter snima LocalDateTime u bazu
    private static String vrijemeOd(LocalDate od) {
        return od.atStartOfDay().toString();
    }

    private static String vrijemeDo(LocalDate doD) {
        return doD.plusDays(1).atStartOfDay().toString();
    }

    private static double kaoDouble(Object vrijednost) {
        if (vrijednost == null) {
            return 0;
        }
        return ((Number) vrijednost).doubleValue();
    }

    private static long kaoLong(Object vrijednost) {
        if (vrijednost == null) {
            return 0;
        }
        return ((Number) vrijednost).longValue();
    }

    public double promet(LocalDate od, LocalDate doD) {
        EntityManager em = JPA.em();
        try {
            Object prodaja = em.createNativeQuery("""
                    SELECT SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM stavka_racuna s
                    JOIN racun r ON r.broj = s.broj_racuna
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2""")
                    .setParameter(1, vrijemeOd(od))
                    .setParameter(2, vrijemeDo(doD))
                    .getSingleResult();
            Object povrati = em.createNativeQuery("""
                    SELECT SUM(p.iznos) FROM povrat p
                    JOIN racun r ON r.broj = p.broj_racuna
                    WHERE r.storniran = 0 AND p.vrijeme >= ?1 AND p.vrijeme < ?2""")
                    .setParameter(1, vrijemeOd(od))
                    .setParameter(2, vrijemeDo(doD))
                    .getSingleResult();
            return Util.round2(kaoDouble(prodaja) - kaoDouble(povrati));
        } finally {
            em.close();
        }
    }

    public static LocalDate pocetakSedmice(LocalDate datum) {
        return datum.with(DayOfWeek.MONDAY);
    }

    // za svakog prodavaca: [0] broj izdatih racuna, [1] neto promet
    public Map<String, double[]> prometPoProdavacima(LocalDate od, LocalDate doD) {
        Map<String, double[]> mapa = new LinkedHashMap<>();
        EntityManager em = JPA.em();
        try {
            List<?> prodaje = em.createNativeQuery("""
                    SELECT r.prodavac, COUNT(DISTINCT r.broj), SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM racun r
                    JOIN stavka_racuna s ON s.broj_racuna = r.broj
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2
                    GROUP BY r.prodavac ORDER BY r.prodavac""")
                    .setParameter(1, vrijemeOd(od))
                    .setParameter(2, vrijemeDo(doD))
                    .getResultList();
            for (Object red : prodaje) {
                Object[] kolone = (Object[]) red;
                mapa.put((String) kolone[0],
                        new double[]{kaoLong(kolone[1]), kaoDouble(kolone[2])});
            }
            List<?> povrati = em.createNativeQuery("""
                    SELECT p.prodavac, SUM(p.iznos) FROM povrat p
                    JOIN racun r ON r.broj = p.broj_racuna
                    WHERE r.storniran = 0 AND p.vrijeme >= ?1 AND p.vrijeme < ?2
                    GROUP BY p.prodavac""")
                    .setParameter(1, vrijemeOd(od))
                    .setParameter(2, vrijemeDo(doD))
                    .getResultList();
            for (Object red : povrati) {
                Object[] kolone = (Object[]) red;
                double[] vrijednosti = mapa.get((String) kolone[0]);
                if (vrijednosti == null) {
                    vrijednosti = new double[2];
                    mapa.put((String) kolone[0], vrijednosti);
                }
                vrijednosti[1] = vrijednosti[1] - kaoDouble(kolone[1]);
            }
        } finally {
            em.close();
        }
        for (double[] vrijednosti : mapa.values()) {
            vrijednosti[1] = Util.round2(vrijednosti[1]);
        }
        return mapa;
    }

    // redovi {sifra, naziv, kolicina, promet}; kategorijaId null = sve kategorije,
    // poPrometu bira sortiranje po iznosu umjesto po kolicini, rastuce okrece redoslijed
    public List<Object[]> najprodavaniji(LocalDate od, LocalDate doD, Integer kategorijaId,
                                         boolean poPrometu, boolean rastuce, int limit) {
        String uslovKategorije = "";
        if (kategorijaId != null) {
            uslovKategorije = " AND s.sifra_artikla IN (SELECT a.sifra FROM artikal a WHERE a.kategorija_id IN ("
                    + idLista(kategorijaSaPodstablom(kategorijaId)) + "))";
        }
        Map<String, Object[]> mapa = new LinkedHashMap<>();
        EntityManager em = JPA.em();
        try {
            // jedinica mjere ide uz svaki red: kolicine razlicitih artikala se ne smiju
            // porediti kao "komadi" kad je jedan u kg a drugi u komadima
            List<?> prodaje = em.createNativeQuery("""
                    SELECT s.sifra_artikla, s.naziv_artikla, SUM(s.kolicina), SUM(""" + SQL_IZNOS_STAVKE + """
                    ), MAX(COALESCE(art.jedinica_mjere, ''))
                     FROM stavka_racuna s
                    JOIN racun r ON r.broj = s.broj_racuna
                    LEFT JOIN artikal art ON art.sifra = s.sifra_artikla
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2"""
                    + uslovKategorije + " GROUP BY s.sifra_artikla, s.naziv_artikla")
                    .setParameter(1, vrijemeOd(od))
                    .setParameter(2, vrijemeDo(doD))
                    .getResultList();
            for (Object red : prodaje) {
                Object[] kolone = (Object[]) red;
                mapa.put((String) kolone[0], new Object[]{kolone[0], kolone[1],
                        (int) kaoLong(kolone[2]), kaoDouble(kolone[3]), kolone[4]});
            }
            List<?> povrati = em.createNativeQuery("""
                    SELECT s.sifra_artikla, MAX(s.naziv_artikla), SUM(s.kolicina), SUM(s.iznos),
                           MAX(COALESCE(art.jedinica_mjere, ''))
                     FROM povrat s
                    JOIN racun r ON r.broj = s.broj_racuna
                    LEFT JOIN artikal art ON art.sifra = s.sifra_artikla
                    WHERE r.storniran = 0 AND s.vrijeme >= ?1 AND s.vrijeme < ?2"""
                    + uslovKategorije + " GROUP BY s.sifra_artikla")
                    .setParameter(1, vrijemeOd(od))
                    .setParameter(2, vrijemeDo(doD))
                    .getResultList();
            for (Object red : povrati) {
                Object[] kolone = (Object[]) red;
                Object[] podaci = mapa.get((String) kolone[0]);
                if (podaci == null) {
                    // povrat artikla koji u periodu nije prodavan (kupljen ranije) -
                    // mora uci u izvjestaj kao negativna stavka, isto kao kod prometa
                    podaci = new Object[]{kolone[0], kolone[1], 0, 0.0, kolone[4]};
                    mapa.put((String) kolone[0], podaci);
                }
                podaci[2] = (Integer) podaci[2] - (int) kaoLong(kolone[2]);
                podaci[3] = (Double) podaci[3] - kaoDouble(kolone[3]);
            }
        } finally {
            em.close();
        }
        List<Object[]> rezultat = new ArrayList<>(mapa.values());
        for (Object[] podaci : rezultat) {
            podaci[3] = Util.round2((Double) podaci[3]);
        }
        Comparator<Object[]> poredjenje;
        if (poPrometu) {
            poredjenje = new Comparator<Object[]>() {
                @Override
                public int compare(Object[] prvi, Object[] drugi) {
                    return Double.compare((Double) drugi[3], (Double) prvi[3]);
                }
            };
        } else {
            poredjenje = new Comparator<Object[]>() {
                @Override
                public int compare(Object[] prvi, Object[] drugi) {
                    return Integer.compare((Integer) drugi[2], (Integer) prvi[2]);
                }
            };
        }
        if (rastuce) {
            poredjenje = poredjenje.reversed();
        }
        Collections.sort(rezultat, poredjenje);
        if (limit > 0 && rezultat.size() > limit) {
            rezultat = new ArrayList<>(rezultat.subList(0, limit));
        }
        return rezultat;
    }

    public List<Object[]> najprodavaniji(LocalDate od, LocalDate doD) {
        return najprodavaniji(od, doD, null, false, false, 0);
    }

    // =====================================================================
    // FILTRIRANI I STRANICENI UPITI
    // Tabele u UI ne smiju vuci milione redova odjednom, pa se filtriranje
    // i LIMIT/OFFSET rade u bazi, a UI trazi stranu po stranu.
    // =====================================================================

    // lista id-eva za IN(...) - id-evi su nasi cijeli brojevi pa je bezbjedno
    private static String idLista(List<Integer> idevi) {
        if (idevi.isEmpty()) {
            return "-1";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : idevi) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(id);
        }
        return sb.toString();
    }

    // dodaje vrijednost u listu parametara i vraca "?N" za upit
    private static String par(List<Object> parametri, Object vrijednost) {
        parametri.add(vrijednost);
        return "?" + parametri.size();
    }

    private static void postaviParametre(jakarta.persistence.Query upit, List<Object> parametri) {
        for (int i = 0; i < parametri.size(); i++) {
            upit.setParameter(i + 1, parametri.get(i));
        }
    }

    // nasa slova sa kvacicama i njihove ASCII zamjene - pretraga ih ne razlikuje
    private static final String[][] KVACICE = {
        {"Č", "c"}, {"č", "c"}, {"Ć", "c"}, {"ć", "c"}, {"Ž", "z"}, {"ž", "z"},
        {"Š", "s"}, {"š", "s"}, {"Đ", "d"}, {"đ", "d"}
    };

    // SQLite LOWER() spusta samo ASCII slova, pa se nasa slova u koloni prvo
    // zamijene osnovnim ASCII slovom - tako "cokolada" nalazi "Čokolada"
    private static String bezKvacica(String kolona) {
        String izraz = kolona;
        for (int i = 0; i < KVACICE.length; i++) {
            izraz = "replace(" + izraz + ",'" + KVACICE[i][0] + "','" + KVACICE[i][1] + "')";
        }
        return "LOWER(" + izraz + ")";
    }

    // isto preslikavanje nad ukucanim tekstom; koriste ga i filteri u memoriji
    public static String kljucPretrage(String tekst) {
        String rezultat = tekst.toLowerCase();
        for (int i = 0; i < KVACICE.length; i++) {
            rezultat = rezultat.replace(KVACICE[i][0], KVACICE[i][1]);
        }
        return rezultat;
    }

    // uslov za tekstualnu pretragu: LIKE bez obzira na velika/mala slova i kvacice
    private static String tekstUslov(List<Object> parametri, String obrazacKolona, String tekst) {
        String obrazacMala = "%" + kljucPretrage(tekst.trim()) + "%";
        String[] kolone = obrazacKolona.split(";");
        StringBuilder sb = new StringBuilder(" AND (");
        for (int i = 0; i < kolone.length; i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append(bezKvacica(kolone[i])).append(" LIKE ").append(par(parametri, obrazacMala));
        }
        sb.append(")");
        return sb.toString();
    }

    private static boolean imaTeksta(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ---------------- artikli ----------------

    private String uslovArtikala(Filteri.FilterArtikala f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder(" WHERE 1=1");
        if (imaTeksta(f.tekst)) {
            gdje.append(tekstUslov(parametri, "a.sifra;a.naziv;a.proizvodjac", f.tekst));
        }
        if (f.kategorijaId != null) {
            gdje.append(" AND a.kategorija_id IN (")
                    .append(idLista(kategorijaSaPodstablom(f.kategorijaId))).append(")");
        }
        if (f.dobavljacId != null) {
            gdje.append(" AND a.dobavljac_id = ").append(par(parametri, f.dobavljacId));
        }
        if (imaTeksta(f.jedinicaMjere)) {
            // jedinica se bira iz padajuce liste postojecih vrijednosti, pa se
            // poredi tacno - inace bi "g" hvatalo i "kg"
            gdje.append(" AND ").append(bezKvacica("a.jedinica_mjere")).append(" = ")
                    .append(par(parametri, kljucPretrage(f.jedinicaMjere.trim())));
        }
        if (f.cijenaOd != null) {
            gdje.append(" AND a.cijena >= ").append(par(parametri, f.cijenaOd));
        }
        if (f.cijenaDo != null) {
            gdje.append(" AND a.cijena <= ").append(par(parametri, f.cijenaDo));
        }
        if (f.stanjeOd != null) {
            gdje.append(" AND a.stanje >= ").append(par(parametri, f.stanjeOd));
        }
        if (f.stanjeDo != null) {
            gdje.append(" AND a.stanje <= ").append(par(parametri, f.stanjeDo));
        }
        if (f.samoNiskoStanje) {
            gdje.append(" AND a.stanje < 10");
        }
        if (f.samoNaAkciji) {
            // na akciji je artikal sa vlastitom akcijom ili artikal u kategoriji
            // (odnosno podkategoriji kategorije) koja je na akciji
            String danas = LocalDate.now().toString();
            gdje.append(" AND (EXISTS (SELECT 1 FROM akcija ak WHERE ak.sifra_artikla = a.sifra")
                    .append(" AND ak.od_datuma <= ").append(par(parametri, danas))
                    .append(" AND ak.do_datuma >= ").append(par(parametri, danas)).append(")");
            List<Integer> pokrivene = kategorijePodAkcijom(LocalDate.now());
            if (!pokrivene.isEmpty()) {
                gdje.append(" OR a.kategorija_id IN (").append(idLista(pokrivene)).append(")");
            }
            gdje.append(")");
        }
        return gdje.toString();
    }

    // sve kategorije (sa podstablima) pokrivene akcijom aktivnom na zadani datum
    private List<Integer> kategorijePodAkcijom(LocalDate datum) {
        List<Integer> pokrivene = new ArrayList<>();
        EntityManager em = JPA.em();
        List<Akcija> lista;
        try {
            lista = em.createQuery("""
                    SELECT a FROM Akcija a
                    WHERE a.kategorijaId IS NOT NULL AND a.odDatuma <= :datum AND a.doDatuma >= :datum""",
                    Akcija.class)
                    .setParameter("datum", datum)
                    .getResultList();
        } finally {
            em.close();
        }
        for (Akcija a : lista) {
            for (Integer id : kategorijaSaPodstablom(a.getKategorijaId())) {
                if (!pokrivene.contains(id)) {
                    pokrivene.add(id);
                }
            }
        }
        return pokrivene;
    }

    @SuppressWarnings("unchecked")
    public List<Artikal> artikliFiltrirano(Filteri.FilterArtikala f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT a.* FROM artikal a" + uslovArtikala(f, parametri) + " ORDER BY a.sifra";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql, Artikal.class);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            return upit.getResultList();
        } finally {
            em.close();
        }
    }

    public long brojArtikalaFiltrirano(Filteri.FilterArtikala f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM artikal a" + uslovArtikala(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    // ---------------- racuni ----------------

    // ukupan iznos racuna izracunat iz stavki, za filtriranje po iznosu;
    // suma se zaokruzi na 2 decimale da odgovara iznosu prikazanom u tabeli
    private static final String SQL_IZNOS_RACUNA =
            "(SELECT COALESCE(ROUND(SUM(" + SQL_IZNOS_STAVKE + "), 2), 0)"
            + " FROM stavka_racuna s WHERE s.broj_racuna = r.broj)";

    // dio uslova koji ne zavisi od datuma i statusa - koristi se i za zbir povrata,
    // da podnozje tabele racuna bude konzistentno sa aktivnim filterima
    private String uslovRacunaOstalo(Filteri.FilterRacuna f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder();
        if (imaTeksta(f.broj)) {
            gdje.append(tekstUslov(parametri, "r.broj", f.broj));
        }
        if (imaTeksta(f.prodavac)) {
            gdje.append(" AND r.prodavac = ").append(par(parametri, f.prodavac));
        }
        if (imaTeksta(f.nacinPlacanja)) {
            gdje.append(" AND r.nacin_placanja = ").append(par(parametri, f.nacinPlacanja));
        }
        if (f.iznosOd != null) {
            gdje.append(" AND ").append(SQL_IZNOS_RACUNA).append(" >= ").append(par(parametri, f.iznosOd));
        }
        if (f.iznosDo != null) {
            gdje.append(" AND ").append(SQL_IZNOS_RACUNA).append(" <= ").append(par(parametri, f.iznosDo));
        }
        return gdje.toString();
    }

    private String uslovRacuna(Filteri.FilterRacuna f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder(" WHERE 1=1");
        if (f.od != null) {
            gdje.append(" AND r.vrijeme >= ").append(par(parametri, vrijemeOd(f.od)));
        }
        if (f.doD != null) {
            gdje.append(" AND r.vrijeme < ").append(par(parametri, vrijemeDo(f.doD)));
        }
        if (f.storniran != null) {
            if (f.storniran) {
                gdje.append(" AND r.storniran = 1");
            } else {
                gdje.append(" AND r.storniran = 0");
            }
        }
        gdje.append(uslovRacunaOstalo(f, parametri));
        return gdje.toString();
    }

    @SuppressWarnings("unchecked")
    public List<Racun> racuniFiltrirano(Filteri.FilterRacuna f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT r.* FROM racun r" + uslovRacuna(f, parametri) + " ORDER BY r.vrijeme DESC, r.broj DESC";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql, Racun.class);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            return upit.getResultList();
        } finally {
            em.close();
        }
    }

    public long brojRacunaFiltrirano(Filteri.FilterRacuna f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM racun r" + uslovRacuna(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    // zbir iznosa nestorniranih racuna koji prolaze filter
    public double sumaRacunaFiltrirano(Filteri.FilterRacuna f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT SUM(" + SQL_IZNOS_RACUNA + ") FROM racun r"
                + uslovRacuna(f, parametri) + " AND r.storniran = 0";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return Util.round2(kaoDouble(upit.getSingleResult()));
        } finally {
            em.close();
        }
    }

    // zbir povrata u periodu, ali samo na nestorniranim racunima koji prolaze
    // ostale uslove filtera racuna (prodavac, placanje, broj, iznos) - za podnozje
    // tabele prometa, da se "neto" slaze sa onim sto je gore filtrirano
    public double sumaPovrataZaFilterRacuna(Filteri.FilterRacuna f) {
        List<Object> parametri = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT SUM(p.iznos) FROM povrat p
                JOIN racun r ON r.broj = p.broj_racuna
                WHERE r.storniran = 0""");
        if (f.od != null) {
            sql.append(" AND p.vrijeme >= ").append(par(parametri, vrijemeOd(f.od)));
        }
        if (f.doD != null) {
            sql.append(" AND p.vrijeme < ").append(par(parametri, vrijemeDo(f.doD)));
        }
        sql.append(uslovRacunaOstalo(f, parametri));
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql.toString());
            postaviParametre(upit, parametri);
            return Util.round2(kaoDouble(upit.getSingleResult()));
        } finally {
            em.close();
        }
    }

    // koliko je novca vec vraceno kupcu po ovom racunu (kroz povrate)
    public double vraceniIznosRacuna(String brojRacuna) {
        EntityManager em = JPA.em();
        try {
            Object suma = em.createNativeQuery("SELECT SUM(iznos) FROM povrat WHERE broj_racuna = ?1")
                    .setParameter(1, brojRacuna)
                    .getSingleResult();
            return Util.round2(kaoDouble(suma));
        } finally {
            em.close();
        }
    }

    // vraceni iznosi za vise racuna odjednom (za kolonu u tabeli prometa)
    public Map<String, Double> vraceniIznosiRacuna(List<String> brojevi) {
        Map<String, Double> mapa = new HashMap<>();
        if (brojevi.isEmpty()) {
            return mapa;
        }
        EntityManager em = JPA.em();
        try {
            List<Object[]> redovi = em.createQuery("""
                    SELECT p.brojRacuna, SUM(p.iznos) FROM Povrat p
                    WHERE p.brojRacuna IN :brojevi GROUP BY p.brojRacuna""", Object[].class)
                    .setParameter("brojevi", brojevi)
                    .getResultList();
            for (Object[] red : redovi) {
                mapa.put((String) red[0], Util.round2(kaoDouble(red[1])));
            }
            return mapa;
        } finally {
            em.close();
        }
    }

    public List<Povrat> povratiRacuna(String brojRacuna) {
        EntityManager em = JPA.em();
        try {
            return em.createQuery("SELECT p FROM Povrat p WHERE p.brojRacuna = :broj ORDER BY p.vrijeme",
                    Povrat.class)
                    .setParameter("broj", brojRacuna)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    // ---------------- povrati ----------------

    private String uslovPovrata(Filteri.FilterPovrata f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder(" WHERE 1=1");
        if (f.od != null) {
            gdje.append(" AND p.vrijeme >= ").append(par(parametri, vrijemeOd(f.od)));
        }
        if (f.doD != null) {
            gdje.append(" AND p.vrijeme < ").append(par(parametri, vrijemeDo(f.doD)));
        }
        if (imaTeksta(f.tekst)) {
            gdje.append(tekstUslov(parametri, "p.sifra_artikla;p.naziv_artikla", f.tekst));
        }
        if (imaTeksta(f.brojRacuna)) {
            gdje.append(tekstUslov(parametri, "p.broj_racuna", f.brojRacuna));
        }
        if (imaTeksta(f.prodavac)) {
            gdje.append(" AND p.prodavac = ").append(par(parametri, f.prodavac));
        }
        if (f.bezStorniranihRacuna) {
            gdje.append(" AND EXISTS (SELECT 1 FROM racun r WHERE r.broj = p.broj_racuna AND r.storniran = 0)");
        }
        return gdje.toString();
    }

    // imena prodavaca koja se pojavljuju na racunima (za filter combo)
    public List<String> prodavaciRacuna() {
        EntityManager em = JPA.em();
        try {
            List<?> redovi = em.createNativeQuery(
                    "SELECT DISTINCT prodavac FROM racun ORDER BY prodavac").getResultList();
            List<String> rezultat = new ArrayList<>();
            for (Object red : redovi) {
                rezultat.add((String) red);
            }
            return rezultat;
        } finally {
            em.close();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Povrat> povratiFiltrirano(Filteri.FilterPovrata f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT p.* FROM povrat p" + uslovPovrata(f, parametri) + " ORDER BY p.vrijeme DESC, p.id DESC";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql, Povrat.class);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            return upit.getResultList();
        } finally {
            em.close();
        }
    }

    public long brojPovrataFiltrirano(Filteri.FilterPovrata f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM povrat p" + uslovPovrata(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    public double sumaPovrataFiltrirano(Filteri.FilterPovrata f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT SUM(p.iznos) FROM povrat p" + uslovPovrata(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return Util.round2(kaoDouble(upit.getSingleResult()));
        } finally {
            em.close();
        }
    }

    // da tabela povrata moze oznaciti povrate ciji je racun kasnije storniran
    public Set<String> storniraniRacuni(List<String> brojevi) {
        Set<String> rezultat = new HashSet<>();
        if (brojevi.isEmpty()) {
            return rezultat;
        }
        EntityManager em = JPA.em();
        try {
            List<Racun> lista = em.createQuery(
                    "SELECT r FROM Racun r WHERE r.storniran = true AND r.broj IN :brojevi", Racun.class)
                    .setParameter("brojevi", brojevi)
                    .getResultList();
            for (Racun r : lista) {
                rezultat.add(r.getBroj());
            }
            return rezultat;
        } finally {
            em.close();
        }
    }

    // ---------------- nabavke ----------------

    // direktnoNaStavke: u upitu nad pojedinacnim stavkama filter teksta ide direktno
    // na kolone stavke (alias s), a u upitu nad nabavkama kroz EXISTS podupit
    private String uslovNabavki(Filteri.FilterNabavki f, List<Object> parametri, boolean direktnoNaStavke) {
        StringBuilder gdje = new StringBuilder(" WHERE 1=1");
        if (f.od != null) {
            gdje.append(" AND n.datum >= ").append(par(parametri, f.od.toString()));
        }
        if (f.doD != null) {
            gdje.append(" AND n.datum <= ").append(par(parametri, f.doD.toString()));
        }
        if (f.dobavljacId != null) {
            gdje.append(" AND n.dobavljac_id = ").append(par(parametri, f.dobavljacId));
        }
        if (imaTeksta(f.tekstArtikla)) {
            if (direktnoNaStavke) {
                gdje.append(tekstUslov(parametri, "s.sifra_artikla;s.naziv_artikla", f.tekstArtikla));
            } else {
                // parametri su pozicioni kroz cijeli upit, pa tekstUslov moze pisati u istu listu
                gdje.append(" AND EXISTS (SELECT 1 FROM stavka_nabavke sn WHERE sn.nabavka_id = n.id")
                        .append(tekstUslov(parametri, "sn.sifra_artikla;sn.naziv_artikla", f.tekstArtikla))
                        .append(")");
            }
        }
        return gdje.toString();
    }

    // iznos stavke nabavke identican StavkaNabavke.iznos(); suma zaokruzena kao u tabeli
    private static final String SQL_IZNOS_STAVKE_NABAVKE =
            "(CAST(%s.kolicina * %s.nabavna_cijena * 100.0 + 0.5 AS INTEGER) / 100.0)";

    private static final String SQL_IZNOS_NABAVKE =
            "(SELECT COALESCE(ROUND(SUM(" + String.format(SQL_IZNOS_STAVKE_NABAVKE, "s2", "s2") + "), 2), 0)"
            + " FROM stavka_nabavke s2 WHERE s2.nabavka_id = n.id)";

    private String uslovIznosaNabavke(Filteri.FilterNabavki f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder();
        if (f.iznosOd != null) {
            gdje.append(" AND ").append(SQL_IZNOS_NABAVKE).append(" >= ").append(par(parametri, f.iznosOd));
        }
        if (f.iznosDo != null) {
            gdje.append(" AND ").append(SQL_IZNOS_NABAVKE).append(" <= ").append(par(parametri, f.iznosDo));
        }
        return gdje.toString();
    }

    // redovi {id, datum, naziv dobavljaca, broj stavki, ukupan iznos}
    public List<Object[]> nabavkeFiltrirano(Filteri.FilterNabavki f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = """
                SELECT n.id, n.datum, COALESCE(d.naziv, '?'),
                       (SELECT COUNT(*) FROM stavka_nabavke s3 WHERE s3.nabavka_id = n.id),
                       """ + SQL_IZNOS_NABAVKE + """
                 FROM nabavka n LEFT JOIN dobavljac d ON d.id = n.dobavljac_id"""
                + uslovNabavki(f, parametri, false) + uslovIznosaNabavke(f, parametri)
                + " ORDER BY n.datum DESC, n.id DESC";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            List<Object[]> rezultat = new ArrayList<>();
            for (Object red : upit.getResultList()) {
                rezultat.add((Object[]) red);
            }
            return rezultat;
        } finally {
            em.close();
        }
    }

    public long brojNabavkiFiltrirano(Filteri.FilterNabavki f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM nabavka n" + uslovNabavki(f, parametri, false) + uslovIznosaNabavke(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    public double sumaNabavkiFiltrirano(Filteri.FilterNabavki f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT SUM(" + SQL_IZNOS_NABAVKE + ") FROM nabavka n"
                + uslovNabavki(f, parametri, false) + uslovIznosaNabavke(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return Util.round2(kaoDouble(upit.getSingleResult()));
        } finally {
            em.close();
        }
    }

    public Nabavka nadjiNabavku(int id) {
        EntityManager em = JPA.em();
        try {
            return em.find(Nabavka.class, id);
        } finally {
            em.close();
        }
    }

    // pojedinacne stavke nabavki (za menadzerski izvjestaj):
    // redovi {datum, dobavljac, sifra, naziv, kolicina, nabavna cijena, iznos}
    public List<Object[]> stavkeNabavkiFiltrirano(Filteri.FilterNabavki f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = """
                SELECT n.datum, COALESCE(d.naziv, '?'), s.sifra_artikla, s.naziv_artikla,
                       s.kolicina, s.nabavna_cijena, (CAST(s.kolicina * s.nabavna_cijena * 100.0 + 0.5 AS INTEGER) / 100.0)
                 FROM stavka_nabavke s
                 JOIN nabavka n ON n.id = s.nabavka_id
                 LEFT JOIN dobavljac d ON d.id = n.dobavljac_id"""
                + uslovNabavki(f, parametri, true)
                + uslovIznosaNabavke(f, parametri)
                + " ORDER BY n.datum DESC, n.id DESC, s.id";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            List<Object[]> rezultat = new ArrayList<>();
            for (Object red : upit.getResultList()) {
                rezultat.add((Object[]) red);
            }
            return rezultat;
        } finally {
            em.close();
        }
    }

    public long brojStavkiNabavkiFiltrirano(Filteri.FilterNabavki f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM stavka_nabavke s JOIN nabavka n ON n.id = s.nabavka_id"
                + uslovNabavki(f, parametri, true)
                + uslovIznosaNabavke(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    public double sumaStavkiNabavkiFiltrirano(Filteri.FilterNabavki f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT SUM(CAST(s.kolicina * s.nabavna_cijena * 100.0 + 0.5 AS INTEGER) / 100.0) FROM stavka_nabavke s"
                + " JOIN nabavka n ON n.id = s.nabavka_id"
                + uslovNabavki(f, parametri, true)
                + uslovIznosaNabavke(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return Util.round2(kaoDouble(upit.getSingleResult()));
        } finally {
            em.close();
        }
    }

    // ---------------- otpisi ----------------

    private String uslovOtpisa(Filteri.FilterOtpisa f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder(" WHERE 1=1");
        if (f.od != null) {
            gdje.append(" AND o.datum >= ").append(par(parametri, f.od.toString()));
        }
        if (f.doD != null) {
            gdje.append(" AND o.datum <= ").append(par(parametri, f.doD.toString()));
        }
        if (imaTeksta(f.tekst)) {
            gdje.append(tekstUslov(parametri, "o.sifra_artikla;o.naziv_artikla", f.tekst));
        }
        if (imaTeksta(f.razlog)) {
            gdje.append(tekstUslov(parametri, "o.razlog", f.razlog));
        }
        if (f.kolicinaOd != null) {
            gdje.append(" AND o.kolicina >= ").append(par(parametri, f.kolicinaOd));
        }
        if (f.kolicinaDo != null) {
            gdje.append(" AND o.kolicina <= ").append(par(parametri, f.kolicinaDo));
        }
        return gdje.toString();
    }

    @SuppressWarnings("unchecked")
    public List<Otpis> otpisiFiltrirano(Filteri.FilterOtpisa f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT o.* FROM otpis o" + uslovOtpisa(f, parametri) + " ORDER BY o.datum DESC, o.id DESC";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql, Otpis.class);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            return upit.getResultList();
        } finally {
            em.close();
        }
    }

    public long brojOtpisaFiltrirano(Filteri.FilterOtpisa f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM otpis o" + uslovOtpisa(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    // ---------------- akcije ----------------

    private String uslovAkcija(Filteri.FilterAkcija f, List<Object> parametri) {
        StringBuilder gdje = new StringBuilder(" WHERE 1=1");
        if (imaTeksta(f.tekst)) {
            gdje.append(tekstUslov(parametri,
                    "COALESCE(a.sifra_artikla, '');COALESCE(ar.naziv, '');COALESCE(k.naziv, '')", f.tekst));
        }
        if (imaTeksta(f.status)) {
            String danas = LocalDate.now().toString();
            if ("AKTIVNA".equals(f.status)) {
                gdje.append(" AND a.od_datuma <= ").append(par(parametri, danas))
                        .append(" AND a.do_datuma >= ").append(par(parametri, danas));
            } else if ("NAJAVLJENA".equals(f.status)) {
                gdje.append(" AND a.od_datuma > ").append(par(parametri, danas));
            } else if ("ISTEKLA".equals(f.status)) {
                gdje.append(" AND a.do_datuma < ").append(par(parametri, danas));
            }
        }
        if (f.popustOd != null) {
            gdje.append(" AND a.popust >= ").append(par(parametri, f.popustOd));
        }
        if (f.popustDo != null) {
            gdje.append(" AND a.popust <= ").append(par(parametri, f.popustDo));
        }
        if (f.od != null) {
            gdje.append(" AND a.do_datuma >= ").append(par(parametri, f.od.toString()));
        }
        if (f.doD != null) {
            gdje.append(" AND a.od_datuma <= ").append(par(parametri, f.doD.toString()));
        }
        return gdje.toString();
    }

    // redovi {id, sifra artikla, naziv artikla, kategorija_id, od datuma, do datuma, popust}
    // (za akciju na kategoriju su sifra i naziv artikla NULL, a kategorija_id popunjen)
    public List<Object[]> akcijeFiltrirano(Filteri.FilterAkcija f, int pomak, int limit) {
        List<Object> parametri = new ArrayList<>();
        String sql = """
                SELECT a.id, a.sifra_artikla, ar.naziv, a.kategorija_id, a.od_datuma, a.do_datuma, a.popust
                 FROM akcija a
                 LEFT JOIN artikal ar ON ar.sifra = a.sifra_artikla
                 LEFT JOIN kategorija k ON k.id = a.kategorija_id"""
                + uslovAkcija(f, parametri) + " ORDER BY a.od_datuma DESC, a.id DESC";
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            upit.setFirstResult(pomak);
            upit.setMaxResults(limit);
            List<Object[]> rezultat = new ArrayList<>();
            for (Object red : upit.getResultList()) {
                rezultat.add((Object[]) red);
            }
            return rezultat;
        } finally {
            em.close();
        }
    }

    public long brojAkcijaFiltrirano(Filteri.FilterAkcija f) {
        List<Object> parametri = new ArrayList<>();
        String sql = """
                SELECT COUNT(*) FROM akcija a
                 LEFT JOIN artikal ar ON ar.sifra = a.sifra_artikla
                 LEFT JOIN kategorija k ON k.id = a.kategorija_id"""
                + uslovAkcija(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return kaoLong(upit.getSingleResult());
        } finally {
            em.close();
        }
    }

    // ---------------- korisnici, kategorije, dobavljaci (male tabele, filtriranje u memoriji) ----------------

    public List<Korisnik> korisniciFiltrirano(Filteri.FilterKorisnika f) {
        List<Korisnik> rezultat = new ArrayList<>();
        for (Korisnik k : getKorisnici()) {
            if (imaTeksta(f.tekst)) {
                String trazeno = kljucPretrage(f.tekst.trim());
                if (!kljucPretrage(k.getIme()).contains(trazeno)
                        && !kljucPretrage(k.getKorisnickoIme()).contains(trazeno)) {
                    continue;
                }
            }
            if (imaTeksta(f.uloga) && !k.getUloga().name().equals(f.uloga)) {
                continue;
            }
            rezultat.add(k);
        }
        return rezultat;
    }

    public List<Kategorija> kategorijeFiltrirano(Filteri.FilterKategorija f) {
        List<Kategorija> rezultat = new ArrayList<>();
        for (Kategorija k : getKategorije()) {
            if (imaTeksta(f.tekst)) {
                String trazeno = kljucPretrage(f.tekst.trim());
                if (!kljucPretrage(k.getNaziv()).contains(trazeno)
                        && !kljucPretrage(putanjaKategorije(k.getId())).contains(trazeno)) {
                    continue;
                }
            }
            if (f.samoGlavne != null && f.samoGlavne && k.getNadkategorijaId() != null) {
                continue;
            }
            if (f.nadkategorijaId != null
                    && (k.getNadkategorijaId() == null || !k.getNadkategorijaId().equals(f.nadkategorijaId))) {
                continue;
            }
            rezultat.add(k);
        }
        return rezultat;
    }

    public List<Dobavljac> dobavljaciFiltrirano(Filteri.FilterDobavljaca f) {
        List<Dobavljac> rezultat = new ArrayList<>();
        for (Dobavljac d : getDobavljaci()) {
            if (imaTeksta(f.tekst)) {
                String trazeno = kljucPretrage(f.tekst.trim());
                // prazna polja se preskacu, da kucanje "null" ne pogodi svakog dobavljaca
                String sve = kljucPretrage(prazniAkoNull(d.getNaziv()) + " " + prazniAkoNull(d.getAdresa())
                        + " " + prazniAkoNull(d.getTelefon()) + " " + prazniAkoNull(d.getEmail()));
                if (!sve.contains(trazeno)) {
                    continue;
                }
            }
            rezultat.add(d);
        }
        return rezultat;
    }

    private static String prazniAkoNull(String s) {
        if (s == null) {
            return "";
        }
        return s;
    }

    // ---------------- prijedlozi za polja pretrage ----------------

    // razlicite vrijednosti iz zadanih kolona koje sadrze ukucani tekst - njima se
    // pune padajuce liste prijedloga. naziv tabele i kolona su interne konstante,
    // ali se ipak provjeravaju da se upit ne moze sastaviti od tudjeg teksta
    public List<String> prijedloziVrijednosti(String tabela, String kolone, String upit, int limit) {
        provjeriIdentifikator(tabela);
        String[] listaKolona = kolone.split(";");
        StringBuilder izvor = new StringBuilder();
        for (int i = 0; i < listaKolona.length; i++) {
            provjeriIdentifikator(listaKolona[i]);
            if (i > 0) {
                izvor.append(" UNION ALL ");
            }
            izvor.append("SELECT ").append(listaKolona[i]).append(" AS v FROM ").append(tabela);
        }
        List<Object> parametri = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT v FROM (")
                .append(izvor).append(") WHERE v IS NOT NULL AND v <> ''");
        String trazeno = "";
        if (imaTeksta(upit)) {
            trazeno = kljucPretrage(upit.trim());
            sql.append(" AND ").append(bezKvacica("v")).append(" LIKE ")
                    .append(par(parametri, "%" + trazeno + "%"));
        }
        sql.append(" ORDER BY v");
        EntityManager em = JPA.em();
        List<?> redovi;
        try {
            jakarta.persistence.Query q = em.createNativeQuery(sql.toString());
            postaviParametre(q, parametri);
            // uzme se vise nego treba jer se lista ispod ponovo poredja
            q.setMaxResults(limit * 5);
            redovi = q.getResultList();
        } finally {
            em.close();
        }
        List<String> pocinju = new ArrayList<>();
        List<String> sadrze = new ArrayList<>();
        for (Object red : redovi) {
            String v = String.valueOf(red);
            if (!trazeno.isEmpty() && kljucPretrage(v).startsWith(trazeno)) {
                pocinju.add(v);
            } else {
                sadrze.add(v);
            }
        }
        List<String> rezultat = new ArrayList<>(pocinju);
        rezultat.addAll(sadrze);
        if (rezultat.size() > limit) {
            return new ArrayList<>(rezultat.subList(0, limit));
        }
        return rezultat;
    }

    private static void provjeriIdentifikator(String ime) {
        for (int i = 0; i < ime.length(); i++) {
            char z = ime.charAt(i);
            if (!Character.isLetter(z) && z != '_') {
                throw new IllegalArgumentException("Neispravan naziv kolone/tabele: " + ime);
            }
        }
        if (ime.isEmpty()) {
            throw new IllegalArgumentException("Prazan naziv kolone/tabele!");
        }
    }

    // sve jedinice mjere koje se stvarno koriste (za padajucu listu u filteru)
    public List<String> jediniceMjere() {
        return prijedloziVrijednosti("artikal", "jedinica_mjere", "", 50);
    }

    // nabavna cijena sa zadnje nabavke artikla, ili null ako ga nikad nije bilo
    public Double zadnjaNabavnaCijena(String sifraArtikla) {
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery("""
                    SELECT s.nabavna_cijena FROM stavka_nabavke s
                     JOIN nabavka n ON n.id = s.nabavka_id
                     WHERE s.sifra_artikla = ?1
                     ORDER BY n.datum DESC, n.id DESC""");
            upit.setParameter(1, sifraArtikla);
            upit.setMaxResults(1);
            List<?> redovi = upit.getResultList();
            if (redovi.isEmpty()) {
                return null;
            }
            return Util.round2(((Number) redovi.get(0)).doubleValue());
        } finally {
            em.close();
        }
    }

    // broj artikala direktno u svakoj kategoriji, jednim upitom
    public Map<Integer, Long> brojArtikalaPoKategoriji() {
        Map<Integer, Long> mapa = new HashMap<>();
        EntityManager em = JPA.em();
        try {
            List<?> redovi = em.createNativeQuery(
                    "SELECT kategorija_id, COUNT(*) FROM artikal GROUP BY kategorija_id")
                    .getResultList();
            for (Object red : redovi) {
                Object[] kolone = (Object[]) red;
                mapa.put((int) kaoLong(kolone[0]), kaoLong(kolone[1]));
            }
            return mapa;
        } finally {
            em.close();
        }
    }

    // =====================================================================
    // STATISTIKA - agregacije u bazi, za grafikone i KPI kartice
    // =====================================================================

    public static class StatistikaPerioda {
        public long brojIzdatihRacuna;
        public long brojStorniranihRacuna;
        public double prodaja;          // bruto prodaja bez povrata
        public double iznosPovrata;
        public double promet;           // prodaja - povrati
        public long brojPovrata;
        public long brojOtpisa;
        public long otpisanoKomada;
        public double prosjecanRacun;
    }

    public StatistikaPerioda statistikaPerioda(LocalDate od, LocalDate doD) {
        StatistikaPerioda st = new StatistikaPerioda();
        EntityManager em = JPA.em();
        try {
            String vOd = vrijemeOd(od);
            String vDo = vrijemeDo(doD);
            st.brojIzdatihRacuna = kaoLong(em.createNativeQuery(
                    "SELECT COUNT(*) FROM racun WHERE storniran = 0 AND vrijeme >= ?1 AND vrijeme < ?2")
                    .setParameter(1, vOd).setParameter(2, vDo).getSingleResult());
            st.brojStorniranihRacuna = kaoLong(em.createNativeQuery(
                    "SELECT COUNT(*) FROM racun WHERE storniran = 1 AND vrijeme >= ?1 AND vrijeme < ?2")
                    .setParameter(1, vOd).setParameter(2, vDo).getSingleResult());
            st.prodaja = Util.round2(kaoDouble(em.createNativeQuery("""
                    SELECT SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM stavka_racuna s JOIN racun r ON r.broj = s.broj_racuna
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2""")
                    .setParameter(1, vOd).setParameter(2, vDo).getSingleResult()));
            Object[] povrati = (Object[]) em.createNativeQuery("""
                    SELECT COUNT(*), SUM(p.iznos) FROM povrat p
                    JOIN racun r ON r.broj = p.broj_racuna
                    WHERE r.storniran = 0 AND p.vrijeme >= ?1 AND p.vrijeme < ?2""")
                    .setParameter(1, vOd).setParameter(2, vDo).getSingleResult();
            st.brojPovrata = kaoLong(povrati[0]);
            st.iznosPovrata = Util.round2(kaoDouble(povrati[1]));
            Object[] otpisi = (Object[]) em.createNativeQuery(
                    "SELECT COUNT(*), SUM(kolicina) FROM otpis WHERE datum >= ?1 AND datum <= ?2")
                    .setParameter(1, od.toString()).setParameter(2, doD.toString()).getSingleResult();
            st.brojOtpisa = kaoLong(otpisi[0]);
            st.otpisanoKomada = kaoLong(otpisi[1]);
        } finally {
            em.close();
        }
        st.promet = Util.round2(st.prodaja - st.iznosPovrata);
        if (st.brojIzdatihRacuna > 0) {
            st.prosjecanRacun = Util.round2(st.prodaja / st.brojIzdatihRacuna);
        }
        return st;
    }

    // za svaki dan u periodu: [0] prodaja, [1] povrati (dani bez prometa su nule);
    // grafikon nema smisla preko ~3 godine, pa se i upiti i prikaz rezu na istu granicu
    // (da se podaci van prikazanog raspona ne bi tiho gubili)
    public Map<LocalDate, double[]> prometPoDanima(LocalDate od, LocalDate doD) {
        if (doD.isAfter(od.plusDays(1099))) {
            doD = od.plusDays(1099);
        }
        Map<LocalDate, double[]> mapa = new LinkedHashMap<>();
        LocalDate dan = od;
        while (!dan.isAfter(doD)) {
            mapa.put(dan, new double[2]);
            dan = dan.plusDays(1);
        }
        EntityManager em = JPA.em();
        try {
            List<?> prodaje = em.createNativeQuery("""
                    SELECT substr(r.vrijeme, 1, 10), SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM stavka_racuna s JOIN racun r ON r.broj = s.broj_racuna
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2
                    GROUP BY substr(r.vrijeme, 1, 10)""")
                    .setParameter(1, vrijemeOd(od)).setParameter(2, vrijemeDo(doD)).getResultList();
            for (Object red : prodaje) {
                Object[] kolone = (Object[]) red;
                double[] vrijednosti = mapa.get(LocalDate.parse((String) kolone[0]));
                if (vrijednosti != null) {
                    vrijednosti[0] = Util.round2(kaoDouble(kolone[1]));
                }
            }
            List<?> povrati = em.createNativeQuery("""
                    SELECT substr(p.vrijeme, 1, 10), SUM(p.iznos) FROM povrat p
                    JOIN racun r ON r.broj = p.broj_racuna
                    WHERE r.storniran = 0 AND p.vrijeme >= ?1 AND p.vrijeme < ?2
                    GROUP BY substr(p.vrijeme, 1, 10)""")
                    .setParameter(1, vrijemeOd(od)).setParameter(2, vrijemeDo(doD)).getResultList();
            for (Object red : povrati) {
                Object[] kolone = (Object[]) red;
                double[] vrijednosti = mapa.get(LocalDate.parse((String) kolone[0]));
                if (vrijednosti != null) {
                    vrijednosti[1] = Util.round2(kaoDouble(kolone[1]));
                }
            }
        } finally {
            em.close();
        }
        return mapa;
    }

    // bruto prodaja po satima u danu (0-23), za grafikon "kad se najvise prodaje"
    public double[] prometPoSatima(LocalDate od, LocalDate doD) {
        double[] poSatima = new double[24];
        EntityManager em = JPA.em();
        try {
            List<?> redovi = em.createNativeQuery("""
                    SELECT substr(r.vrijeme, 12, 2), SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM stavka_racuna s JOIN racun r ON r.broj = s.broj_racuna
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2
                    GROUP BY substr(r.vrijeme, 12, 2)""")
                    .setParameter(1, vrijemeOd(od)).setParameter(2, vrijemeDo(doD)).getResultList();
            for (Object red : redovi) {
                Object[] kolone = (Object[]) red;
                int sat = Integer.parseInt((String) kolone[0]);
                if (sat >= 0 && sat < 24) {
                    poSatima[sat] = Util.round2(kaoDouble(kolone[1]));
                }
            }
        } finally {
            em.close();
        }
        return poSatima;
    }

    // za svaki nacin placanja: [0] broj racuna, [1] iznos
    public Map<String, double[]> prometPoNacinuPlacanja(LocalDate od, LocalDate doD) {
        Map<String, double[]> mapa = new LinkedHashMap<>();
        EntityManager em = JPA.em();
        try {
            List<?> redovi = em.createNativeQuery("""
                    SELECT r.nacin_placanja, COUNT(DISTINCT r.broj), SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM racun r JOIN stavka_racuna s ON s.broj_racuna = r.broj
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2
                    GROUP BY r.nacin_placanja ORDER BY r.nacin_placanja""")
                    .setParameter(1, vrijemeOd(od)).setParameter(2, vrijemeDo(doD)).getResultList();
            for (Object red : redovi) {
                Object[] kolone = (Object[]) red;
                mapa.put((String) kolone[0],
                        new double[]{kaoLong(kolone[1]), Util.round2(kaoDouble(kolone[2]))});
            }
        } finally {
            em.close();
        }
        return mapa;
    }

    // neto promet grupisan po glavnim (korijenskim) kategorijama, opadajuce
    public Map<String, Double> prometPoKategorijama(LocalDate od, LocalDate doD) {
        Map<Integer, Double> poKategoriji = new LinkedHashMap<>();
        double nepoznato = 0;
        EntityManager em = JPA.em();
        try {
            List<?> prodaje = em.createNativeQuery("""
                    SELECT a.kategorija_id, SUM(""" + SQL_IZNOS_STAVKE + """
                    ) FROM stavka_racuna s
                    JOIN racun r ON r.broj = s.broj_racuna
                    LEFT JOIN artikal a ON a.sifra = s.sifra_artikla
                    WHERE r.storniran = 0 AND r.vrijeme >= ?1 AND r.vrijeme < ?2
                    GROUP BY a.kategorija_id""")
                    .setParameter(1, vrijemeOd(od)).setParameter(2, vrijemeDo(doD)).getResultList();
            for (Object red : prodaje) {
                Object[] kolone = (Object[]) red;
                if (kolone[0] == null) {
                    nepoznato = nepoznato + kaoDouble(kolone[1]);
                } else {
                    int id = (int) kaoLong(kolone[0]);
                    poKategoriji.merge(id, kaoDouble(kolone[1]), Double::sum);
                }
            }
            List<?> povrati = em.createNativeQuery("""
                    SELECT a.kategorija_id, SUM(p.iznos) FROM povrat p
                    JOIN racun r ON r.broj = p.broj_racuna
                    LEFT JOIN artikal a ON a.sifra = p.sifra_artikla
                    WHERE r.storniran = 0 AND p.vrijeme >= ?1 AND p.vrijeme < ?2
                    GROUP BY a.kategorija_id""")
                    .setParameter(1, vrijemeOd(od)).setParameter(2, vrijemeDo(doD)).getResultList();
            for (Object red : povrati) {
                Object[] kolone = (Object[]) red;
                if (kolone[0] == null) {
                    nepoznato = nepoznato - kaoDouble(kolone[1]);
                } else {
                    int id = (int) kaoLong(kolone[0]);
                    poKategoriji.merge(id, -kaoDouble(kolone[1]), Double::sum);
                }
            }
        } finally {
            em.close();
        }
        // svaka kategorija se pripisuje svojoj glavnoj (korijenskoj) kategoriji
        Map<String, Double> poGlavnoj = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> e : poKategoriji.entrySet()) {
            Integer glavnaId = glavnaKategorijaZa(e.getKey());
            String naziv;
            if (glavnaId == null) {
                naziv = "Ostalo";
            } else {
                Kategorija glavna = nadjiKategoriju(glavnaId);
                if (glavna == null) {
                    naziv = "Ostalo";
                } else {
                    naziv = glavna.getNaziv();
                }
            }
            poGlavnoj.merge(naziv, e.getValue(), Double::sum);
        }
        if (nepoznato != 0) {
            poGlavnoj.merge("Ostalo", nepoznato, Double::sum);
        }
        List<Map.Entry<String, Double>> lista = new ArrayList<>(poGlavnoj.entrySet());
        lista.sort(new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> prvi, Map.Entry<String, Double> drugi) {
                return Double.compare(drugi.getValue(), prvi.getValue());
            }
        });
        Map<String, Double> rezultat = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : lista) {
            rezultat.put(e.getKey(), Util.round2(e.getValue()));
        }
        return rezultat;
    }

    public double vrijednostZaliha() {
        EntityManager em = JPA.em();
        try {
            Object suma = em.createNativeQuery("SELECT SUM(stanje * cijena) FROM artikal").getSingleResult();
            return Util.round2(kaoDouble(suma));
        } finally {
            em.close();
        }
    }

    // vrijednost zaliha samo za artikle koji prolaze filter (za podnozje tabele zaliha)
    public double vrijednostZalihaFiltrirano(Filteri.FilterArtikala f) {
        List<Object> parametri = new ArrayList<>();
        String sql = "SELECT SUM(a.stanje * a.cijena) FROM artikal a" + uslovArtikala(f, parametri);
        EntityManager em = JPA.em();
        try {
            jakarta.persistence.Query upit = em.createNativeQuery(sql);
            postaviParametre(upit, parametri);
            return Util.round2(kaoDouble(upit.getSingleResult()));
        } finally {
            em.close();
        }
    }

    // da li se artikal pojavljuje na nekom racunu (prije brisanja se korisnik upozori)
    public boolean artikalImaProdaje(String sifra) {
        EntityManager em = JPA.em();
        try {
            Object ima = em.createNativeQuery(
                    "SELECT EXISTS(SELECT 1 FROM stavka_racuna WHERE sifra_artikla = ?1)")
                    .setParameter(1, sifra)
                    .getSingleResult();
            return kaoLong(ima) > 0;
        } finally {
            em.close();
        }
    }

    // ==================== testni podaci ====================

    // koliko zadnjih artikala ostaje bez nabavke ("novo u ponudi", stanje 0)
    private static final int NOVIH_ARTIKALA = 4;
    // koliko puta svaki dobavljac isporuci robu u posmatranom periodu
    private static final int NABAVKI_PO_DOBAVLJACU = 5;
    // koliko mjeseci historije se generise
    private static final int MJESECI_HISTORIJE = 13;

    // korisnici: ime | korisnicko ime | lozinka | uloga
    private static final String[][] TESTNI_KORISNICI = {
        {"Tarik Čerkezović", "admin", "admin", "ADMINISTRATOR"},
        {"Mahir Halilović", "prodavac", "prodavac", "PRODAVAC"},
        {"Maid Hrustić", "prodavac1", "prodavac", "PRODAVAC"},
        {"Amina Suljić", "prodavac2", "prodavac", "PRODAVAC"},
        {"Denis Lazić", "menadzer", "menadzer", "MENADZER"}
    };

    // kategorije: naziv | nadkategorija (prazno = glavna). nadkategorija mora
    // biti navedena prije svojih podkategorija
    private static final String[][] TESTNE_KATEGORIJE = {
        {"Pića", ""},
        {"Gazirana pića", "Pića"},
        {"Voda", "Pića"},
        {"Sokovi", "Pića"},
        {"Voćni sokovi", "Sokovi"},
        {"Nektari", "Sokovi"},
        {"Topli napici", "Pića"},
        {"Kafe", "Topli napici"},
        {"Čajevi", "Topli napici"},
        {"Energetska pića", "Pića"},
        {"Alkoholna pića", "Pića"},
        {"Pivo", "Alkoholna pića"},
        {"Vino", "Alkoholna pića"},
        {"Slatki program", ""},
        {"Bombone", "Slatki program"},
        {"Čokolade", "Slatki program"},
        {"Čokoladne tablice", "Čokolade"},
        {"Bombonijere", "Čokolade"},
        {"Keks i vafli", "Slatki program"},
        {"Sladoled", "Slatki program"},
        {"Mliječni proizvodi", ""},
        {"Svježe mlijeko", "Mliječni proizvodi"},
        {"Jogurti i kiselo", "Mliječni proizvodi"},
        {"Sirevi", "Mliječni proizvodi"},
        {"Maslo i kajmak", "Mliječni proizvodi"},
        {"Osnovne namirnice", ""},
        {"Brašno i šećer", "Osnovne namirnice"},
        {"Ulje i mast", "Osnovne namirnice"},
        {"Tjestenina", "Osnovne namirnice"},
        {"Konzerve", "Osnovne namirnice"},
        {"Začini", "Osnovne namirnice"},
        {"Pekarski proizvodi", ""},
        {"Meso i mesni proizvodi", ""},
        {"Suhomesnati proizvodi", "Meso i mesni proizvodi"},
        {"Pašteta i konzerve", "Meso i mesni proizvodi"},
        {"Higijena i čistoća", ""},
        {"Lična higijena", "Higijena i čistoća"},
        {"Sredstva za čišćenje", "Higijena i čistoća"},
        {"Voće i povrće", ""}
    };

    // dobavljaci: naziv | adresa | telefon | email
    private static final String[][] TESTNI_DOBAVLJACI = {
        {"Bingo d.o.o.", "Bosanska poljana bb, Tuzla", "035 368 100", "nabavka@bingo.ba"},
        {"AS Group d.o.o.", "Industrijska zona bb, Jelah", "032 666 100", "prodaja@asgroup.ba"},
        {"Mljekara Tuzla d.o.o.", "Husinskih rudara 162, Tuzla", "035 280 050", "info@mljekaratz.ba"},
        {"Violeta d.o.o.", "Pobrežje 15, Grude", "039 660 200", "narudzbe@violeta.ba"},
        {"Klas d.d.", "Paromlinska 45, Sarajevo", "033 445 700", "komercijala@klas.ba"},
        {"Vispak d.d.", "Podvinci 3, Visoko", "032 730 400", "prodaja@vispak.ba"},
        {"Sarajevski kiseljak d.d.", "Kraljice Katarine 1, Kiseljak", "030 877 200", "kontakt@kiseljak.ba"},
        {"Meggle BH d.o.o.", "Bosanska 1, Bihać", "037 318 900", "info@meggle.ba"}
    };

    // artikli: naziv | kategorija | jedinica mjere | proizvodjac | cijena | dobavljac.
    // sifre dodjeljuje aplikacija (brojac), pa idu 1001, 1002, ... po ovom redu
    private static final String[][] TESTNI_ARTIKLI = {
        {"Coca Cola 0.5L", "Gazirana pića", "kom", "Coca-Cola HBC", "2.50", "Bingo d.o.o."},
        {"Coca Cola 2L", "Gazirana pića", "kom", "Coca-Cola HBC", "4.90", "Bingo d.o.o."},
        {"Fanta 0.5L", "Gazirana pića", "kom", "Coca-Cola HBC", "2.40", "Bingo d.o.o."},
        {"Sprite 0.5L", "Gazirana pića", "kom", "Coca-Cola HBC", "2.40", "Bingo d.o.o."},
        {"Cockta 0.5L", "Gazirana pića", "kom", "Droga Kolinska", "2.30", "Bingo d.o.o."},
        {"Pepsi 1.5L", "Gazirana pića", "kom", "PepsiCo", "3.60", "Bingo d.o.o."},
        {"Schweppes bitter lemon 1L", "Gazirana pića", "kom", "Coca-Cola HBC", "3.10", "Bingo d.o.o."},
        {"Sarajevski kiseljak 1.5L", "Voda", "kom", "Sarajevski kiseljak", "1.60", "Sarajevski kiseljak d.d."},
        {"Kiseljak gazirani 0.5L", "Voda", "kom", "Sarajevski kiseljak", "1.10", "Sarajevski kiseljak d.d."},
        {"Voda Oaza 1.5L", "Voda", "kom", "Oaza", "1.30", "Sarajevski kiseljak d.d."},
        {"Voda Jana 0.75L", "Voda", "kom", "Jamnica", "2.20", "Bingo d.o.o."},
        {"Sok od narandže 1L", "Voćni sokovi", "kom", "Vitaminka", "3.20", "Bingo d.o.o."},
        {"Sok od jabuke 1L", "Voćni sokovi", "kom", "Vitaminka", "3.00", "Bingo d.o.o."},
        {"Sok od višnje 1L", "Voćni sokovi", "kom", "Fructal", "3.40", "Bingo d.o.o."},
        {"Cijeđena narandža 0.25L", "Voćni sokovi", "kom", "Vitaminka", "2.10", "Bingo d.o.o."},
        {"Nektar breskva 1L", "Nektari", "kom", "Fructal", "2.80", "Bingo d.o.o."},
        {"Nektar kruška 1L", "Nektari", "kom", "Fructal", "2.80", "Bingo d.o.o."},
        {"Nektar multivitamin 1.5L", "Nektari", "kom", "Vitaminka", "3.90", "Bingo d.o.o."},
        {"Kafa mljevena 250g", "Kafe", "kom", "Vispak", "5.80", "Vispak d.d."},
        {"Kafa mljevena 500g", "Kafe", "kom", "Vispak", "10.90", "Vispak d.d."},
        {"Kafa Zlatna džezva 200g", "Kafe", "kom", "Vispak", "5.20", "Vispak d.d."},
        {"Instant kafa 100g", "Kafe", "kom", "Nescafé", "8.40", "AS Group d.o.o."},
        {"Kapsule za kafu 10/1", "Kafe", "pak", "Lavazza", "12.50", "AS Group d.o.o."},
        {"Čaj od nane 20/1", "Čajevi", "kom", "Franck", "2.90", "AS Group d.o.o."},
        {"Čaj od šipka 20/1", "Čajevi", "kom", "Franck", "2.90", "AS Group d.o.o."},
        {"Čaj kamilica 20/1", "Čajevi", "kom", "Vispak", "2.60", "Vispak d.d."},
        {"Zeleni čaj 20/1", "Čajevi", "kom", "Franck", "3.10", "AS Group d.o.o."},
        {"Red Bull 0.25L", "Energetska pića", "kom", "Red Bull", "3.50", "Bingo d.o.o."},
        {"Guarana 0.5L", "Energetska pića", "kom", "Knjaz Miloš", "2.90", "Bingo d.o.o."},
        {"Izotonik limun 0.75L", "Energetska pića", "kom", "Vitaminka", "2.70", "Bingo d.o.o."},
        {"Sarajevsko pivo 0.5L", "Pivo", "kom", "Sarajevska pivara", "2.20", "Bingo d.o.o."},
        {"Tuzlansko pivo 0.5L", "Pivo", "kom", "Tuzlanska pivara", "2.10", "Bingo d.o.o."},
        {"Nektar pivo 0.5L", "Pivo", "kom", "Banjalučka pivara", "2.20", "Bingo d.o.o."},
        {"Bezalkoholno pivo 0.5L", "Pivo", "kom", "Sarajevska pivara", "2.30", "Bingo d.o.o."},
        {"Vranac 0.75L", "Vino", "kom", "Plantaže", "14.90", "Bingo d.o.o."},
        {"Žilavka 0.75L", "Vino", "kom", "Hepok", "16.50", "Bingo d.o.o."},
        {"Blatina 0.75L", "Vino", "kom", "Čitluk", "15.20", "Bingo d.o.o."},
        {"Bombone Negro 100g", "Bombone", "kom", "Pionir", "1.80", "AS Group d.o.o."},
        {"Bombone medene 100g", "Bombone", "kom", "Kandit", "1.70", "AS Group d.o.o."},
        {"Žele bombone 200g", "Bombone", "kom", "Haribo", "3.40", "AS Group d.o.o."},
        {"Lizalice 10/1", "Bombone", "pak", "Chupa Chups", "4.20", "AS Group d.o.o."},
        {"Milka mliječna 80g", "Čokoladne tablice", "kom", "Mondelez", "2.20", "AS Group d.o.o."},
        {"Milka oreo 100g", "Čokoladne tablice", "kom", "Mondelez", "2.90", "AS Group d.o.o."},
        {"Čokolada za kuhanje 200g", "Čokoladne tablice", "kom", "Kandit", "3.50", "AS Group d.o.o."},
        {"Dorina mliječna 220g", "Čokoladne tablice", "kom", "Kraš", "4.10", "AS Group d.o.o."},
        {"Crna čokolada 75% 100g", "Čokoladne tablice", "kom", "Lindt", "5.60", "AS Group d.o.o."},
        {"Kinder čokolada 4/1", "Čokoladne tablice", "pak", "Ferrero", "3.80", "AS Group d.o.o."},
        {"Bombonijera Ledo 400g", "Bombonijere", "kom", "Kraš", "12.90", "AS Group d.o.o."},
        {"Ferrero Rocher 16/1", "Bombonijere", "pak", "Ferrero", "15.50", "AS Group d.o.o."},
        {"Bajadera 300g", "Bombonijere", "kom", "Kraš", "9.80", "AS Group d.o.o."},
        {"Plazma keks 300g", "Keks i vafli", "kom", "Bambi", "4.60", "AS Group d.o.o."},
        {"Petit Beurre 150g", "Keks i vafli", "kom", "Kraš", "2.10", "AS Group d.o.o."},
        {"Napolitanke lješnjak 200g", "Keks i vafli", "kom", "Kraš", "3.20", "AS Group d.o.o."},
        {"Domaćica keks 200g", "Keks i vafli", "kom", "Kraš", "2.80", "AS Group d.o.o."},
        {"Vafl štanglica 40g", "Keks i vafli", "kom", "Takovo", "0.90", "AS Group d.o.o."},
        {"Sladoled King 120ml", "Sladoled", "kom", "Ledo", "2.50", "Bingo d.o.o."},
        {"Sladoled kornet vanila", "Sladoled", "kom", "Ledo", "2.20", "Bingo d.o.o."},
        {"Sladoled kutija 1L", "Sladoled", "l", "Ledo", "7.90", "Bingo d.o.o."},
        {"Mlijeko 2.8% mm 1L", "Svježe mlijeko", "l", "Mljekara Tuzla", "1.90", "Mljekara Tuzla d.o.o."},
        {"Mlijeko 3.2% mm 1L", "Svježe mlijeko", "l", "Meggle", "2.20", "Meggle BH d.o.o."},
        {"Mlijeko bez laktoze 1L", "Svježe mlijeko", "l", "Meggle", "3.10", "Meggle BH d.o.o."},
        {"Mlijeko u prahu 400g", "Svježe mlijeko", "kom", "Mljekara Tuzla", "8.90", "Mljekara Tuzla d.o.o."},
        {"Jogurt čvrsti 180g", "Jogurti i kiselo", "kom", "Mljekara Tuzla", "1.20", "Mljekara Tuzla d.o.o."},
        {"Jogurt tečni 1L", "Jogurti i kiselo", "l", "Mljekara Tuzla", "2.60", "Mljekara Tuzla d.o.o."},
        {"Voćni jogurt jagoda 150g", "Jogurti i kiselo", "kom", "Meggle", "1.40", "Meggle BH d.o.o."},
        {"Kiselo mlijeko 500g", "Jogurti i kiselo", "kom", "Mljekara Tuzla", "2.10", "Mljekara Tuzla d.o.o."},
        {"Pavlaka 20% mm 400g", "Jogurti i kiselo", "kom", "Meggle", "3.40", "Meggle BH d.o.o."},
        {"Sir trapist 250g", "Sirevi", "kom", "Mljekara Tuzla", "5.60", "Mljekara Tuzla d.o.o."},
        {"Sir feta 200g", "Sirevi", "kom", "Meggle", "4.80", "Meggle BH d.o.o."},
        {"Sir za mazanje 175g", "Sirevi", "kom", "Meggle", "3.20", "Meggle BH d.o.o."},
        {"Kačkavalj 300g", "Sirevi", "kom", "Mljekara Tuzla", "8.40", "Mljekara Tuzla d.o.o."},
        {"Mladi sir rinfuz", "Sirevi", "kg", "Mljekara Tuzla", "14.50", "Mljekara Tuzla d.o.o."},
        {"Kajmak 250g", "Maslo i kajmak", "kom", "Mljekara Tuzla", "4.50", "Mljekara Tuzla d.o.o."},
        {"Maslo 250g", "Maslo i kajmak", "kom", "Meggle", "6.20", "Meggle BH d.o.o."},
        {"Margarin 500g", "Maslo i kajmak", "kom", "Dijamant", "3.60", "AS Group d.o.o."},
        {"Brašno T-500 1kg", "Brašno i šećer", "kg", "Klas", "1.60", "Klas d.d."},
        {"Brašno T-400 1kg", "Brašno i šećer", "kg", "Klas", "1.80", "Klas d.d."},
        {"Brašno integralno 1kg", "Brašno i šećer", "kg", "Klas", "2.40", "Klas d.d."},
        {"Šećer kristal 1kg", "Brašno i šećer", "kg", "AS", "1.70", "AS Group d.o.o."},
        {"Šećer u prahu 500g", "Brašno i šećer", "kom", "AS", "1.50", "AS Group d.o.o."},
        {"Ulje suncokretovo 1L", "Ulje i mast", "l", "Dijamant", "3.90", "AS Group d.o.o."},
        {"Ulje maslinovo 0.5L", "Ulje i mast", "l", "Zvijezda", "12.90", "AS Group d.o.o."},
        {"Svinjska mast 500g", "Ulje i mast", "kom", "Klas", "4.20", "Klas d.d."},
        {"Špagete 500g", "Tjestenina", "kom", "Klas", "2.30", "Klas d.d."},
        {"Makarone 500g", "Tjestenina", "kom", "Klas", "2.30", "Klas d.d."},
        {"Rezanci za supu 250g", "Tjestenina", "kom", "Klas", "1.60", "Klas d.d."},
        {"Njoke 500g", "Tjestenina", "kom", "Barilla", "3.80", "AS Group d.o.o."},
        {"Grašak konzerva 400g", "Konzerve", "kom", "Podravka", "2.60", "AS Group d.o.o."},
        {"Kukuruz šećerac 300g", "Konzerve", "kom", "Podravka", "2.80", "AS Group d.o.o."},
        {"Paradajz pelati 400g", "Konzerve", "kom", "Podravka", "2.50", "AS Group d.o.o."},
        {"Tuna u ulju 160g", "Konzerve", "kom", "Eva", "4.30", "AS Group d.o.o."},
        {"So kuhinjska 1kg", "Začini", "kg", "Tuzlanska so", "1.20", "AS Group d.o.o."},
        {"Biber mljeveni rinfuz", "Začini", "g", "Kotanyi", "0.04", "AS Group d.o.o."},
        {"Vegeta 200g", "Začini", "kom", "Podravka", "4.10", "AS Group d.o.o."},
        {"Začin za meso rinfuz", "Začini", "g", "Kotanyi", "0.03", "AS Group d.o.o."},
        {"Cimet mljeveni 50g", "Začini", "kom", "Kotanyi", "2.20", "AS Group d.o.o."},
        {"Kruh polubijeli 500g", "Pekarski proizvodi", "kom", "Klas", "1.50", "Klas d.d."},
        {"Kruh integralni 500g", "Pekarski proizvodi", "kom", "Klas", "2.10", "Klas d.d."},
        {"Somun 2/1", "Pekarski proizvodi", "pak", "Klas", "1.80", "Klas d.d."},
        {"Burek 500g", "Pekarski proizvodi", "kom", "Klas", "6.50", "Klas d.d."},
        {"Krofna sa čokoladom", "Pekarski proizvodi", "kom", "Klas", "1.90", "Klas d.d."},
        {"Pecivo sa sirom", "Pekarski proizvodi", "kom", "Klas", "1.60", "Klas d.d."},
        {"Sudžuk 400g", "Suhomesnati proizvodi", "kom", "Ovako", "12.90", "AS Group d.o.o."},
        {"Pršut 100g", "Suhomesnati proizvodi", "kom", "Lijanovići", "7.80", "AS Group d.o.o."},
        {"Šunka u ovitku 300g", "Suhomesnati proizvodi", "kom", "Ovako", "6.40", "AS Group d.o.o."},
        {"Slanina dimljena rinfuz", "Suhomesnati proizvodi", "kg", "Lijanovići", "18.90", "AS Group d.o.o."},
        {"Pašteta pileća 100g", "Pašteta i konzerve", "kom", "Argeta", "2.40", "AS Group d.o.o."},
        {"Pašteta juneća 95g", "Pašteta i konzerve", "kom", "Argeta", "2.60", "AS Group d.o.o."},
        {"Mesni narezak 150g", "Pašteta i konzerve", "kom", "Ovako", "3.20", "AS Group d.o.o."},
        {"Šampon za kosu 400ml", "Lična higijena", "kom", "Violeta", "5.90", "Violeta d.o.o."},
        {"Sapun tvrdi 90g", "Lična higijena", "kom", "Violeta", "1.30", "Violeta d.o.o."},
        {"Pasta za zube 75ml", "Lična higijena", "kom", "Colgate", "4.60", "Violeta d.o.o."},
        {"Toalet papir 10/1", "Lična higijena", "pak", "Violeta", "8.90", "Violeta d.o.o."},
        {"Vlažne salvete 72/1", "Lična higijena", "pak", "Violeta", "3.70", "Violeta d.o.o."},
        {"Deterdžent za posuđe 900ml", "Sredstva za čišćenje", "kom", "Violeta", "4.20", "Violeta d.o.o."},
        {"Prašak za pranje 3kg", "Sredstva za čišćenje", "kg", "Violeta", "14.90", "Violeta d.o.o."},
        {"Sredstvo za podove 1L", "Sredstva za čišćenje", "l", "Violeta", "3.80", "Violeta d.o.o."},
        {"Sunđeri za posuđe 5/1", "Sredstva za čišćenje", "pak", "Violeta", "2.10", "Violeta d.o.o."},
        {"Banane", "Voće i povrće", "kg", "Uvoz", "2.80", "Bingo d.o.o."},
        {"Jabuke idared", "Voće i povrće", "kg", "Domaća proizvodnja", "2.20", "Bingo d.o.o."},
        {"Krompir", "Voće i povrće", "kg", "Domaća proizvodnja", "1.40", "Bingo d.o.o."},
        {"Luk crveni", "Voće i povrće", "kg", "Domaća proizvodnja", "1.80", "Bingo d.o.o."},
        {"Paradajz", "Voće i povrće", "kg", "Domaća proizvodnja", "3.60", "Bingo d.o.o."},
        {"Limun", "Voće i povrće", "kg", "Uvoz", "3.90", "Bingo d.o.o."},
        // zadnja NOVIH_ARTIKALA se nikad ne nabavljaju - ostaju na stanju 0
        {"Proteinski napitak 0.33L", "Energetska pića", "kom", "Nutrend", "4.90", "Bingo d.o.o."},
        {"Bezglutenski keks 150g", "Keks i vafli", "kom", "Schär", "6.40", "AS Group d.o.o."},
        {"Sir mozzarella 125g", "Sirevi", "kom", "Meggle", "4.10", "Meggle BH d.o.o."},
        {"Ekološki deterdžent 750ml", "Sredstva za čišćenje", "kom", "Violeta", "6.80", "Violeta d.o.o."}
    };

    private static final String[] RAZLOZI_OTPISA = {
        "Istekao rok trajanja", "Lom prilikom transporta", "Oštećena ambalaža",
        "Kvar u hladnjaku", "Inventurni manjak", "Roba vraćena dobavljaču",
        "Neispravno deklarisan proizvod"
    };

    // fiksno sjeme: podaci izgledaju raznovrsno, ali su svaki put isti
    private int sjeme = 20260914;

    private int slucajni(int granica) {
        sjeme = (sjeme * 1103515245 + 12345) & 0x7fffffff;
        return sjeme % granica;
    }

    private void popuniTestnePodatke() {
        List<Korisnik> prodavaci = testniKorisnici();
        Map<String, Kategorija> kategorije = testneKategorije();
        Map<String, Dobavljac> dobavljaci = testniDobavljaci();
        List<Artikal> artikli = testniArtikli(kategorije, dobavljaci);
        List<Artikal> naZalihama = new ArrayList<>(artikli.subList(0, artikli.size() - NOVIH_ARTIKALA));

        // redoslijed je vazan: nabavka puni stanje, akcije moraju postojati prije
        // prodaje da se popusti obracunaju, otpisi idu na kraju.
        // "raspolozivo" prati stanje u memoriji, pa nema upita po svakoj stavci
        Map<String, Integer> raspolozivo = testneNabavke(naZalihama, dobavljaci);
        testneAkcije(naZalihama, kategorije);
        testnaProdaja(naZalihama, prodavaci, raspolozivo);
        testniOtpisi(naZalihama, raspolozivo);
    }

    private List<Korisnik> testniKorisnici() {
        List<Korisnik> prodavaci = new ArrayList<>();
        for (String[] red : TESTNI_KORISNICI) {
            Uloga uloga = Uloga.valueOf(red[3]);
            Korisnik k = dodajKorisnika(red[0], red[1], red[2], uloga);
            if (uloga == Uloga.PRODAVAC) {
                prodavaci.add(k);
            }
        }
        return prodavaci;
    }

    private Map<String, Kategorija> testneKategorije() {
        Map<String, Kategorija> kategorije = new LinkedHashMap<>();
        for (String[] red : TESTNE_KATEGORIJE) {
            Integer nadkategorija = null;
            if (!red[1].isEmpty()) {
                nadkategorija = kategorije.get(red[1]).getId();
            }
            kategorije.put(red[0], dodajKategoriju(red[0], nadkategorija));
        }
        return kategorije;
    }

    private Map<String, Dobavljac> testniDobavljaci() {
        Map<String, Dobavljac> dobavljaci = new LinkedHashMap<>();
        for (String[] red : TESTNI_DOBAVLJACI) {
            dobavljaci.put(red[0], dodajDobavljaca(red[0], red[1], red[2], red[3]));
        }
        return dobavljaci;
    }

    private List<Artikal> testniArtikli(Map<String, Kategorija> kategorije,
                                        Map<String, Dobavljac> dobavljaci) {
        List<Artikal> artikli = new ArrayList<>();
        for (String[] red : TESTNI_ARTIKLI) {
            artikli.add(dodajArtikal(red[0], kategorije.get(red[1]).getId(), red[2], red[3],
                    Double.parseDouble(red[4]), dobavljaci.get(red[5]).getId()));
        }
        return artikli;
    }

    // svaki dobavljac isporuci nekoliko puta; vraca nabavljenu kolicinu po artiklu
    private Map<String, Integer> testneNabavke(List<Artikal> artikli, Map<String, Dobavljac> dobavljaci) {
        Map<String, Integer> nabavljeno = new HashMap<>();
        LocalDate danas = LocalDate.now();
        for (Dobavljac d : dobavljaci.values()) {
            List<Artikal> njegovi = new ArrayList<>();
            for (Artikal a : artikli) {
                if (a.getDobavljacId() == d.getId()) {
                    njegovi.add(a);
                }
            }
            if (njegovi.isEmpty()) {
                continue;
            }
            for (int krug = 0; krug < NABAVKI_PO_DOBAVLJACU; krug++) {
                List<StavkaNabavke> stavke = new ArrayList<>();
                for (Artikal a : njegovi) {
                    // male kolicine, kao u malom marketu
                    int kolicina = 10 + slucajni(14);
                    // nabavna cijena je 60-75% prodajne
                    double nabavna = Util.round2(a.getCijena() * (0.60 + slucajni(16) / 100.0));
                    if (nabavna <= 0) {
                        nabavna = 0.01;
                    }
                    stavke.add(stNab(a.getSifra(), kolicina, nabavna));
                    Integer dosad = nabavljeno.get(a.getSifra());
                    if (dosad == null) {
                        dosad = 0;
                    }
                    nabavljeno.put(a.getSifra(), dosad + kolicina);
                }
                // isporuke idu od najstarije ka najnovijoj, po jedna svaka 3 mjeseca
                LocalDate datum = danas.minusMonths(MJESECI_HISTORIJE - krug * 3L)
                        .withDayOfMonth(2 + slucajni(20));
                evidentirajNabavku(d.getId(), datum, stavke);
            }
        }
        return nabavljeno;
    }

    // akcije na artikle i na kategorije, u svim stanjima (aktivna/najavljena/istekla)
    private void testneAkcije(List<Artikal> artikli, Map<String, Kategorija> kategorije) {
        LocalDate danas = LocalDate.now();
        // aktivne
        dodajAkciju(artikli.get(0).getSifra(), danas.minusDays(3), danas.plusDays(10), 10);
        dodajAkciju(artikli.get(41).getSifra(), danas.minusDays(7), danas.plusDays(7), 15);
        dodajAkciju(artikli.get(58).getSifra(), danas.minusDays(1), danas.plusDays(5), 20);
        dodajAkciju(artikli.get(96).getSifra(), danas.minusDays(10), danas.plusDays(4), 25);
        // najavljene
        dodajAkciju(artikli.get(18).getSifra(), danas.plusDays(5), danas.plusDays(20), 12);
        dodajAkciju(artikli.get(73).getSifra(), danas.plusDays(10), danas.plusDays(24), 8);
        // istekle
        dodajAkciju(artikli.get(0).getSifra(), danas.minusDays(75), danas.minusDays(60), 30);
        dodajAkciju(artikli.get(5).getSifra(), danas.minusDays(120), danas.minusDays(100), 20);
        dodajAkciju(artikli.get(50).getSifra(), danas.minusDays(200), danas.minusDays(185), 15);
        dodajAkciju(artikli.get(105).getSifra(), danas.minusDays(40), danas.minusDays(25), 10);
        // na cijele kategorije (vaze i za podkategorije)
        dodajAkcijuNaKategoriju(kategorije.get("Sokovi").getId(),
                danas.minusDays(5), danas.plusDays(9), 10);
        dodajAkcijuNaKategoriju(kategorije.get("Higijena i čistoća").getId(),
                danas.minusDays(2), danas.plusDays(12), 15);
        dodajAkcijuNaKategoriju(kategorije.get("Pekarski proizvodi").getId(),
                danas.plusDays(7), danas.plusDays(14), 20);
        dodajAkcijuNaKategoriju(kategorije.get("Sladoled").getId(),
                danas.minusMonths(4), danas.minusMonths(3), 30);
        dodajAkcijuNaKategoriju(kategorije.get("Čokolade").getId(),
                danas.minusMonths(8), danas.minusMonths(7), 25);
    }

    // prodaja kroz cijeli period, pa povrati i stornirani racuni
    private void testnaProdaja(List<Artikal> artikli, List<Korisnik> prodavaci,
                               Map<String, Integer> raspolozivo) {
        LocalDate danas = LocalDate.now();
        LocalDate pocetak = danas.minusMonths(MJESECI_HISTORIJE).withDayOfMonth(1);
        List<Racun> racuni = new ArrayList<>();
        LocalDate dan = pocetak;
        while (!dan.isAfter(danas)) {
            int brojRacuna = brojRacunaZaDan(dan, danas);
            for (int i = 0; i < brojRacuna; i++) {
                Racun r = jedanTestniRacun(dan, artikli, prodavaci, raspolozivo);
                if (r != null) {
                    racuni.add(r);
                }
            }
            dan = dan.plusDays(1);
        }

        // povrati: na svaki petnaesti racun se vrati jedna stavka
        List<Racun> saPovratom = new ArrayList<>();
        for (int i = 7; i < racuni.size(); i = i + 15) {
            Racun r = racuni.get(i);
            StavkaRacuna s = r.getStavke().get(slucajni(r.getStavke().size()));
            evidentirajPovrat(r, s.getSifraArtikla(), 1, r.getVrijeme().plusDays(1));
            saPovratom.add(r);
        }

        // stornirani racuni, medju njima i jedan na kojem je prije toga bio povrat
        // (taj se u izvjestajima ne smije dvaput odbiti od prometa)
        for (int i = 30; i < racuni.size(); i = i + 90) {
            stornirajRacun(racuni.get(i));
        }
        if (!saPovratom.isEmpty()) {
            stornirajRacun(saPovratom.get(saPovratom.size() / 2));
        }
    }

    // petkom i subotom se proda najvise, nedjeljom se radi skraceno; danasnji dan
    // uvijek ima nekoliko racuna, da filter "Danas" ima sta pokazati
    private int brojRacunaZaDan(LocalDate dan, LocalDate danas) {
        if (dan.equals(danas)) {
            return 3 + slucajni(3);
        }
        DayOfWeek uSedmici = dan.getDayOfWeek();
        if (uSedmici == DayOfWeek.SUNDAY) {
            // skraceno, ali nikad prazan dan - da i "Juče" uvijek nesto vrati
            return 1 + slucajni(2);
        }
        if (uSedmici == DayOfWeek.FRIDAY || uSedmici == DayOfWeek.SATURDAY) {
            return 2 + slucajni(3);
        }
        return 1 + slucajni(2);
    }

    private Racun jedanTestniRacun(LocalDate dan, List<Artikal> artikli, List<Korisnik> prodavaci,
                                   Map<String, Integer> raspolozivo) {
        List<StavkaRacuna> korpa = new ArrayList<>();
        List<String> uzeti = new ArrayList<>();
        int brojStavki = 1 + slucajni(5);
        for (int i = 0; i < brojStavki; i++) {
            Artikal a = artikli.get(slucajni(artikli.size()));
            if (uzeti.contains(a.getSifra())) {
                continue;
            }
            int kolicina = 1 + slucajni(3);
            Integer naStanju = raspolozivo.get(a.getSifra());
            // rezerva ostaje za otpise i da stanje ne padne na nulu
            if (naStanju == null || naStanju < kolicina + 12) {
                continue;
            }
            raspolozivo.put(a.getSifra(), naStanju - kolicina);
            uzeti.add(a.getSifra());
            korpa.add(st(a.getSifra(), kolicina, dan));
        }
        if (korpa.isEmpty()) {
            return null;
        }
        Korisnik prodavac = prodavaci.get(slucajni(prodavaci.size()));
        String nacin = "GOTOVINA";
        if (slucajni(10) < 4) {
            nacin = "KARTICA";
        }
        LocalDateTime vrijeme = dan.atTime(8 + slucajni(12), slucajni(60));
        return testniRacun(prodavac, nacin, 0, vrijeme, korpa);
    }

    private void testniOtpisi(List<Artikal> artikli, Map<String, Integer> raspolozivo) {
        LocalDate danas = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            Artikal a = artikli.get(slucajni(artikli.size()));
            Integer naStanju = raspolozivo.get(a.getSifra());
            if (naStanju == null || naStanju < 8) {
                continue;
            }
            int kolicina = 1 + slucajni(4);
            raspolozivo.put(a.getSifra(), naStanju - kolicina);
            evidentirajOtpis(a.getSifra(), kolicina, RAZLOZI_OTPISA[slucajni(RAZLOZI_OTPISA.length)],
                    danas.minusDays(slucajni(MJESECI_HISTORIJE * 30)));
        }
        // nekoliko artikala se svede na nisko stanje, zbog filtera "Nisko stanje"
        for (int i = 0; i < 6; i++) {
            Artikal a = artikli.get(i * 17);
            Integer naStanju = raspolozivo.get(a.getSifra());
            if (naStanju == null || naStanju <= 6) {
                continue;
            }
            int kolicina = naStanju - (1 + slucajni(5));
            raspolozivo.put(a.getSifra(), naStanju - kolicina);
            evidentirajOtpis(a.getSifra(), kolicina, "Inventurni manjak", danas.minusDays(2));
        }
    }

    private StavkaNabavke stNab(String sifra, int kol, double cijena) {
        Artikal a = nadjiArtikal(sifra);
        return new StavkaNabavke(sifra, a.getNaziv(), kol, cijena);
    }

    private StavkaRacuna st(String sifra, int kol, LocalDate datum) {
        Artikal a = nadjiArtikal(sifra);
        Akcija ak = aktivnaAkcija(sifra, datum);
        double popust = 0;
        if (ak != null) {
            popust = ak.getPopustProcenat();
        }
        return new StavkaRacuna(sifra, a.getNaziv(), kol, a.getCijena(), popust);
    }

    private Racun testniRacun(Korisnik prodavac, String nacin, double predatoOkvirno,
                              LocalDateTime vrijeme, List<StavkaRacuna> stavke) {
        List<StavkaRacuna> lista = new ArrayList<>(stavke);
        double ukupno = 0;
        for (StavkaRacuna s : lista) {
            ukupno = ukupno + s.iznos();
        }
        double predato = 0;
        if ("GOTOVINA".equals(nacin)) {
            predato = Math.max(predatoOkvirno, Math.ceil(ukupno / 10.0) * 10.0);
        }
        return izdajRacun(prodavac, lista, nacin, predato, vrijeme);
    }
}
