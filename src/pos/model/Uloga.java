package pos.model;

public enum Uloga {
    ADMINISTRATOR("Administrator"),
    PRODAVAC("Prodavač"),
    MENADZER("Menadžer");

    private final String naziv;

    Uloga(String naziv) { this.naziv = naziv; }

    @Override
    public String toString() { return naziv; }
}
