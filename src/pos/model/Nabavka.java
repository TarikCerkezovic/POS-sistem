package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "nabavka")
public class Nabavka implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "datum", nullable = false)
    private LocalDate datum;

    @Column(name = "dobavljac_id", nullable = false)
    private int dobavljacId;

    @OneToMany(mappedBy = "nabavka", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<StavkaNabavke> stavke = new ArrayList<>();

    protected Nabavka() { }

    public Nabavka(int id, LocalDate datum, int dobavljacId, List<StavkaNabavke> stavke) {
        this.id = id;
        this.datum = datum;
        this.dobavljacId = dobavljacId;
        this.stavke = stavke;
    }

    public int getId() { return id; }
    public LocalDate getDatum() { return datum; }
    public int getDobavljacId() { return dobavljacId; }
    public List<StavkaNabavke> getStavke() { return stavke; }

    public double ukupno() {
        double suma = 0;
        for (StavkaNabavke s : stavke) {
            suma = suma + s.iznos();
        }
        return Math.round(suma * 100.0) / 100.0;
    }
}
