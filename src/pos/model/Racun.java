package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "racun")
public class Racun implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final double PDV_STOPA = 0.17;

    @Id
    @Column(name = "broj")
    private String broj;

    @Column(name = "vrijeme", nullable = false)
    private LocalDateTime vrijeme;

    @Column(name = "prodavac", nullable = false)
    private String prodavac;

    @OneToMany(mappedBy = "racun", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<StavkaRacuna> stavke = new ArrayList<>();

    @Column(name = "nacin_placanja", nullable = false)
    private String nacinPlacanja;

    @Column(name = "predato", nullable = false)
    private double predato;

    @Column(name = "povrat_novca", nullable = false)
    private double povratNovca;

    @Column(name = "storniran", nullable = false)
    private boolean storniran;

    protected Racun() { }

    public Racun(String broj, LocalDateTime vrijeme, String prodavac, List<StavkaRacuna> stavke,
                 String nacinPlacanja, double predato, double povratNovca) {
        this.broj = broj;
        this.vrijeme = vrijeme;
        this.prodavac = prodavac;
        this.stavke = stavke;
        this.nacinPlacanja = nacinPlacanja;
        this.predato = predato;
        this.povratNovca = povratNovca;
        this.storniran = false;
    }

    public String getBroj() { return broj; }
    public LocalDateTime getVrijeme() { return vrijeme; }
    public String getProdavac() { return prodavac; }
    public List<StavkaRacuna> getStavke() { return stavke; }
    public String getNacinPlacanja() { return nacinPlacanja; }
    public double getPredato() { return predato; }
    public double getPovratNovca() { return povratNovca; }
    public boolean isStorniran() { return storniran; }
    public void setStorniran(boolean storniran) { this.storniran = storniran; }

    public double ukupno() {
        double suma = 0;
        for (StavkaRacuna s : stavke) {
            suma = suma + s.iznos();
        }
        return Math.round(suma * 100.0) / 100.0;
    }

    public double pdv() {
        return Math.round(ukupno() * PDV_STOPA / (1 + PDV_STOPA) * 100.0) / 100.0;
    }

    public double osnovica() {
        return Math.round((ukupno() - pdv()) * 100.0) / 100.0;
    }
}
