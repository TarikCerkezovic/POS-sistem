package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "stavka_nabavke")
public class StavkaNabavke implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nabavka_id", nullable = false)
    private Nabavka nabavka;

    @Column(name = "sifra_artikla", nullable = false)
    private String sifraArtikla;

    @Column(name = "naziv_artikla", nullable = false)
    private String nazivArtikla;

    @Column(name = "kolicina", nullable = false)
    private int kolicina;

    @Column(name = "nabavna_cijena", nullable = false)
    private double nabavnaCijena;

    protected StavkaNabavke() { }

    public StavkaNabavke(String sifraArtikla, String nazivArtikla, int kolicina, double nabavnaCijena) {
        this.sifraArtikla = sifraArtikla;
        this.nazivArtikla = nazivArtikla;
        this.kolicina = kolicina;
        this.nabavnaCijena = nabavnaCijena;
    }

    public void setNabavka(Nabavka nabavka) { this.nabavka = nabavka; }

    public String getSifraArtikla() { return sifraArtikla; }
    public String getNazivArtikla() { return nazivArtikla; }
    public int getKolicina() { return kolicina; }
    public double getNabavnaCijena() { return nabavnaCijena; }

    public double iznos() {
        return Math.round(kolicina * nabavnaCijena * 100.0) / 100.0;
    }
}
