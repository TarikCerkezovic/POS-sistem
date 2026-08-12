package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "dobavljac")
public class Dobavljac implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "naziv", nullable = false)
    private String naziv;

    @Column(name = "adresa")
    private String adresa;

    @Column(name = "telefon")
    private String telefon;

    @Column(name = "email")
    private String email;

    protected Dobavljac() { }

    public Dobavljac(int id, String naziv, String adresa, String telefon, String email) {
        this.id = id;
        this.naziv = naziv;
        this.adresa = adresa;
        this.telefon = telefon;
        this.email = email;
    }

    public int getId() { return id; }
    public String getNaziv() { return naziv; }
    public void setNaziv(String naziv) { this.naziv = naziv; }
    public String getAdresa() { return adresa; }
    public void setAdresa(String adresa) { this.adresa = adresa; }
    public String getTelefon() { return telefon; }
    public void setTelefon(String telefon) { this.telefon = telefon; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() { return naziv; }
}
