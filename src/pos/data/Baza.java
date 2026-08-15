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
    private static final String URL = "jdbc:sqlite:pos.db";
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
                sifra_artikla TEXT NOT NULL REFERENCES artikal(sifra)
                              ON UPDATE CASCADE ON DELETE CASCADE,
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
        try (Connection veza = DriverManager.getConnection(URL);
             Statement s = veza.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            for (String sql : ddl) {
                s.execute(sql);
            }
            s.execute("INSERT OR IGNORE INTO brojac(naziv, vrijednost) VALUES ('racun', 0)");
        } catch (SQLException e) {
            throw new RuntimeException("Nije moguće otvoriti bazu podataka: " + e.getMessage(), e);
        }
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

    public String putanjaKategorije(int id) {
        Kategorija k = nadjiKategoriju(id);
        if (k == null) {
            return "?";
        }
        if (k.getNadkategorijaId() == null) {
            return k.getNaziv();
        }
        Kategorija nad = nadjiKategoriju(k.getNadkategorijaId());
        if (nad == null) {
            return k.getNaziv();
        }
        return nad.getNaziv() + " > " + k.getNaziv();
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

    private static final String JPQL_AKCIJA = """
            SELECT NEW pos.model.Akcija(a.id, a.sifraArtikla, ar.naziv, a.odDatuma, a.doDatuma, a.popustProcenat)
            FROM Akcija a, Artikal ar
            WHERE ar.sifra = a.sifraArtikla""";

    public List<Akcija> getAkcije() {
        EntityManager em = JPA.em();
        try {
            return em.createQuery(JPQL_AKCIJA + " ORDER BY a.id", Akcija.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    public Akcija aktivnaAkcija(String sifraArtikla, LocalDate datum) {
        EntityManager em = JPA.em();
        try {
            List<Akcija> lista = em.createQuery(JPQL_AKCIJA
                    + " AND a.sifraArtikla = :sifra AND a.odDatuma <= :datum AND a.doDatuma >= :datum",
                    Akcija.class)
                    .setParameter("sifra", sifraArtikla)
                    .setParameter("datum", datum)
                    .getResultList();
            if (lista.isEmpty()) {
                return null;
            }
            return lista.get(0);
        } finally {
            em.close();
        }
    }

    public Akcija dodajAkciju(String sifraArtikla, LocalDate od, LocalDate doD, double popust) {
        Artikal art = nadjiArtikal(sifraArtikla);
        if (art == null) {
            throw new IllegalArgumentException("Artikal nije pronađen!");
        }
        if (od.isAfter(doD)) {
            throw new IllegalArgumentException("Datum početka akcije je poslije datuma kraja!");
        }
        if (popust <= 0 || popust >= 100) {
            throw new IllegalArgumentException("Popust mora biti između 1 i 99%!");
        }
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
        Akcija akcija = new Akcija(0, sifraArtikla, art.getNaziv(), od, doD, popust);
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

}
