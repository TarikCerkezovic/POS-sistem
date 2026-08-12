package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "stavka_racuna")
public class StavkaRacuna implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broj_racuna", nullable = false)
    private Racun racun;

    @Column(name = "sifra_artikla", nullable = false)
    private String sifraArtikla;

    @Column(name = "naziv_artikla", nullable = false)
    private String nazivArtikla;

    @Column(name = "kolicina", nullable = false)
    private int kolicina;

    @Column(name = "cijena", nullable = false)
    private double cijena;

    @Column(name = "popust", nullable = false)
    private double popustProcenat;

    protected StavkaRacuna() { }

    public StavkaRacuna(String sifraArtikla, String nazivArtikla, int kolicina, double cijena, double popustProcenat) {
        this.sifraArtikla = sifraArtikla;
        this.nazivArtikla = nazivArtikla;
        this.kolicina = kolicina;
        this.cijena = cijena;
        this.popustProcenat = popustProcenat;
    }

    public void setRacun(Racun racun) { this.racun = racun; }

    public String getSifraArtikla() { return sifraArtikla; }
    public String getNazivArtikla() { return nazivArtikla; }
    public int getKolicina() { return kolicina; }
    public void setKolicina(int kolicina) { this.kolicina = kolicina; }
    public double getCijena() { return cijena; }
    public double getPopustProcenat() { return popustProcenat; }

    public double cijenaSaPopustom() {
        return Math.round(cijena * (1 - popustProcenat / 100.0) * 100.0) / 100.0;
    }

    public double iznos() {
        return Math.round(kolicina * cijenaSaPopustom() * 100.0) / 100.0;
    }
}
