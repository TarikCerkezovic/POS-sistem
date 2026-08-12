package pos.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "artikal")
public class Artikal implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "sifra")
    private String sifra;

    @Column(name = "naziv", nullable = false)
    private String naziv;

    @Column(name = "kategorija_id", nullable = false)
    private int kategorijaId;

    @Column(name = "jedinica_mjere")
    private String jedinicaMjere;

    @Column(name = "proizvodjac")
    private String proizvodjac;

    @Column(name = "stanje", nullable = false)
    private int stanje;

    @Column(name = "cijena", nullable = false)
    private double cijena;

    @Column(name = "dobavljac_id", nullable = false)
    private int dobavljacId;

    protected Artikal() { }

    public Artikal(String sifra, String naziv, int kategorijaId, String jedinicaMjere,
                   String proizvodjac, int stanje, double cijena, int dobavljacId) {
        this.sifra = sifra;
        this.naziv = naziv;
        this.kategorijaId = kategorijaId;
        this.jedinicaMjere = jedinicaMjere;
        this.proizvodjac = proizvodjac;
        this.stanje = stanje;
        this.cijena = cijena;
        this.dobavljacId = dobavljacId;
    }

    public String getSifra() { return sifra; }
    public void setSifra(String sifra) { this.sifra = sifra; }
    public String getNaziv() { return naziv; }
    public void setNaziv(String naziv) { this.naziv = naziv; }
    public int getKategorijaId() { return kategorijaId; }
    public void setKategorijaId(int kategorijaId) { this.kategorijaId = kategorijaId; }
    public String getJedinicaMjere() { return jedinicaMjere; }
    public void setJedinicaMjere(String jedinicaMjere) { this.jedinicaMjere = jedinicaMjere; }
    public String getProizvodjac() { return proizvodjac; }
    public void setProizvodjac(String proizvodjac) { this.proizvodjac = proizvodjac; }
    public int getStanje() { return stanje; }
    public void setStanje(int stanje) { this.stanje = stanje; }
    public double getCijena() { return cijena; }
    public void setCijena(double cijena) { this.cijena = cijena; }
    public int getDobavljacId() { return dobavljacId; }
    public void setDobavljacId(int dobavljacId) { this.dobavljacId = dobavljacId; }

    @Override
    public String toString() { return sifra + " - " + naziv; }
}
