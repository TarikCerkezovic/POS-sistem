package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "akcija")
public class Akcija implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "sifra_artikla", nullable = false)
    private String sifraArtikla;

    @Transient
    private String nazivArtikla;

    @Column(name = "od_datuma", nullable = false)
    private LocalDate odDatuma;

    @Column(name = "do_datuma", nullable = false)
    private LocalDate doDatuma;

    @Column(name = "popust", nullable = false)
    private double popustProcenat;

    protected Akcija() { }

    public Akcija(int id, String sifraArtikla, String nazivArtikla,
                  LocalDate odDatuma, LocalDate doDatuma, double popustProcenat) {
        this.id = id;
        this.sifraArtikla = sifraArtikla;
        this.nazivArtikla = nazivArtikla;
        this.odDatuma = odDatuma;
        this.doDatuma = doDatuma;
        this.popustProcenat = popustProcenat;
    }

    public int getId() { return id; }
    public String getSifraArtikla() { return sifraArtikla; }
    public String getNazivArtikla() { return nazivArtikla; }
    public LocalDate getOdDatuma() { return odDatuma; }
    public LocalDate getDoDatuma() { return doDatuma; }
    public double getPopustProcenat() { return popustProcenat; }

    public boolean aktivnaNa(LocalDate datum) {
        return !datum.isBefore(odDatuma) && !datum.isAfter(doDatuma);
    }

    public boolean preklapaSe(LocalDate od, LocalDate doD) {
        return !od.isAfter(doDatuma) && !doD.isBefore(odDatuma);
    }
}
