package pos.data;

import java.time.LocalDate;

// klase za filtere - polje null/prazno znaci "bez filtera"
public class Filteri {

    public static class FilterArtikala {
        public String tekst;            // trazi u sifri, nazivu i proizvodjacu
        public Integer kategorijaId;    // ukljucuje i sve podkategorije
        public Integer dobavljacId;
        public String jedinicaMjere;
        public Double cijenaOd;
        public Double cijenaDo;
        public Integer stanjeOd;
        public Integer stanjeDo;
        public boolean samoNiskoStanje;
        public boolean samoNaAkciji;
    }

    public static class FilterRacuna {
        public LocalDate od;
        public LocalDate doD;
        public String broj;             // trazi u broju racuna
        public String prodavac;
        public String nacinPlacanja;    // GOTOVINA / KARTICA
        public Boolean storniran;       // null = svi
        public Double iznosOd;
        public Double iznosDo;
    }

    public static class FilterPovrata {
        public LocalDate od;
        public LocalDate doD;
        public String tekst;            // sifra ili naziv artikla
        public String brojRacuna;
        public String prodavac;
        public boolean bezStorniranihRacuna; // preskoci povrate ciji je racun kasnije storniran
    }

    public static class FilterNabavki {
        public LocalDate od;
        public LocalDate doD;
        public Integer dobavljacId;
        public String tekstArtikla;     // sifra ili naziv artikla u stavkama
        public Double iznosOd;          // ukupan iznos nabavke
        public Double iznosDo;
    }

    public static class FilterOtpisa {
        public LocalDate od;
        public LocalDate doD;
        public String tekst;            // sifra ili naziv artikla
        public String razlog;
        public Integer kolicinaOd;
        public Integer kolicinaDo;
    }

    public static class FilterAkcija {
        public String tekst;            // sifra ili naziv artikla
        public String status;           // AKTIVNA / NAJAVLJENA / ISTEKLA
        public Double popustOd;
        public Double popustDo;
        public LocalDate od;            // akcije koje se preklapaju sa periodom
        public LocalDate doD;
    }

    public static class FilterKorisnika {
        public String tekst;            // ime ili korisnicko ime
        public String uloga;            // naziv uloge iz enuma
    }

    public static class FilterKategorija {
        public String tekst;            // naziv ili puna putanja
        public Integer nadkategorijaId; // direktna nadkategorija
        public Boolean samoGlavne;      // true = samo kategorije bez nadkategorije
    }

    public static class FilterDobavljaca {
        public String tekst;            // naziv, adresa, telefon ili email
    }
}
