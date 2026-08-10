package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "korisnik")
public class Korisnik implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "ime", nullable = false)
    private String ime;

    @Column(name = "korisnicko_ime", nullable = false, unique = true)
    private String korisnickoIme;

    @Column(name = "lozinka", nullable = false)
    private String lozinka;

    @Enumerated(EnumType.STRING)
    @Column(name = "uloga", nullable = false)
    private Uloga uloga;

    protected Korisnik() { }

    public Korisnik(int id, String ime, String korisnickoIme, String lozinka, Uloga uloga) {
        this.id = id;
        this.ime = ime;
        this.korisnickoIme = korisnickoIme;
        this.lozinka = lozinka;
        this.uloga = uloga;
    }

    public int getId() { return id; }
    public String getIme() { return ime; }
    public void setIme(String ime) { this.ime = ime; }
    public String getKorisnickoIme() { return korisnickoIme; }
    public void setKorisnickoIme(String korisnickoIme) { this.korisnickoIme = korisnickoIme; }
    public String getLozinka() { return lozinka; }
    public void setLozinka(String lozinka) { this.lozinka = lozinka; }
    public Uloga getUloga() { return uloga; }
    public void setUloga(Uloga uloga) { this.uloga = uloga; }

    @Override
    public String toString() { return ime; }
}
