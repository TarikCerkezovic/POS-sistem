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

}
