package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "povrat")
public class Povrat implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "broj_racuna", nullable = false)
    private String brojRacuna;

    @Column(name = "sifra_artikla", nullable = false)
    private String sifraArtikla;

    @Column(name = "naziv_artikla", nullable = false)
    private String nazivArtikla;

    @Column(name = "kolicina", nullable = false)
    private int kolicina;

    @Column(name = "iznos", nullable = false)
    private double iznos;

    @Column(name = "vrijeme", nullable = false)
    private LocalDateTime vrijeme;

    @Column(name = "prodavac", nullable = false)
    private String prodavac;

    protected Povrat() { }

    public Povrat(int id, String brojRacuna, String sifraArtikla, String nazivArtikla,
                  int kolicina, double iznos, LocalDateTime vrijeme, String prodavac) {
        this.id = id;
        this.brojRacuna = brojRacuna;
        this.sifraArtikla = sifraArtikla;
        this.nazivArtikla = nazivArtikla;
        this.kolicina = kolicina;
        this.iznos = iznos;
        this.vrijeme = vrijeme;
        this.prodavac = prodavac;
    }

    public int getId() { return id; }
    public String getBrojRacuna() { return brojRacuna; }
    public String getSifraArtikla() { return sifraArtikla; }
    public String getNazivArtikla() { return nazivArtikla; }
    public int getKolicina() { return kolicina; }
    public double getIznos() { return iznos; }
    public LocalDateTime getVrijeme() { return vrijeme; }
    public String getProdavac() { return prodavac; }
}
