package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "otpis")
public class Otpis implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "datum", nullable = false)
    private LocalDate datum;

    @Column(name = "sifra_artikla", nullable = false)
    private String sifraArtikla;

    @Column(name = "naziv_artikla", nullable = false)
    private String nazivArtikla;

    @Column(name = "kolicina", nullable = false)
    private int kolicina;

    @Column(name = "razlog", nullable = false)
    private String razlog;

    protected Otpis() { }

    public Otpis(int id, LocalDate datum, String sifraArtikla, String nazivArtikla, int kolicina, String razlog) {
        this.id = id;
        this.datum = datum;
        this.sifraArtikla = sifraArtikla;
        this.nazivArtikla = nazivArtikla;
        this.kolicina = kolicina;
        this.razlog = razlog;
    }

    public int getId() { return id; }
    public LocalDate getDatum() { return datum; }
    public String getSifraArtikla() { return sifraArtikla; }
    public String getNazivArtikla() { return nazivArtikla; }
    public int getKolicina() { return kolicina; }
    public String getRazlog() { return razlog; }
}
