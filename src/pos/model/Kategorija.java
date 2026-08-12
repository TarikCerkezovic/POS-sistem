package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "kategorija")
public class Kategorija implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "naziv", nullable = false)
    private String naziv;

    @Column(name = "nadkategorija_id")
    private Integer nadkategorijaId;

    protected Kategorija() { }

    public Kategorija(int id, String naziv, Integer nadkategorijaId) {
        this.id = id;
        this.naziv = naziv;
        this.nadkategorijaId = nadkategorijaId;
    }

    public int getId() { return id; }
    public String getNaziv() { return naziv; }
    public void setNaziv(String naziv) { this.naziv = naziv; }
    public Integer getNadkategorijaId() { return nadkategorijaId; }
    public void setNadkategorijaId(Integer nadkategorijaId) { this.nadkategorijaId = nadkategorijaId; }

    @Override
    public String toString() { return naziv; }
}
