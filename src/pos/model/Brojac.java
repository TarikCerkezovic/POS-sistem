package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "brojac")
public class Brojac implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "naziv")
    private String naziv;

    @Column(name = "vrijednost", nullable = false)
    private int vrijednost;

    protected Brojac() { }

    public Brojac(String naziv, int vrijednost) {
        this.naziv = naziv;
        this.vrijednost = vrijednost;
    }

    public String getNaziv() { return naziv; }
    public int getVrijednost() { return vrijednost; }
    public void setVrijednost(int vrijednost) { this.vrijednost = vrijednost; }
}
