# POS sistem

Projekat iz Razvoja softvera - POS aplikacija za manje prodavnice.
Java + Swing za GUI, SQLite za bazu (preko JPA/Hibernate), PDFBox za stampanje racuna.

## Pokretanje

Treba JDK 17+.

Prvi put treba skinuti Hibernate biblioteke (oko 10 MB):

```
./preuzmi-lib.sh      (Windows: preuzmi-lib.bat)
```

Poslije toga normalno:

```
./build.sh && ./run.sh      (Windows: build.bat pa run.bat)
```

## Nalozi za testiranje

- admin / admin (administrator - Tarik)
- prodavac / prodavac (prodavac - Mahir)
- prodavac1 / prodavac (prodavac - Maid)
- prodavac2 / prodavac (prodavac - Amina)
- menadzer / menadzer (menadzer - Denis)

Baza se sama napuni testnim podacima kad se prvi put pokrene (traje par sekundi,
ispise se u konzolu). Ako se obrise pos.db, dobiju se identicni podaci jer
generator koristi fiksno sjeme.

Dobije se 39 kategorija (do tri nivoa), 8 dobavljaca, 128 artikala, 40 nabavki i
oko 690 racuna kroz zadnjih 13 mjeseci, sa povratima, storniranim racunima,
otpisima i akcijama u svim stanjima - tako da svaki filter ima sta pokazati.

## Sta ko moze

**Admin** - artikli (sifru dodjeljuje sistem automatski, jedinica mjere se
bira iz liste, artikal moze imati sliku sa pregledom u formi), kategorije
(neogranicena dubina: nadkategorije i podkategorije, npr. Hrana > Slatki
program > Cokolade), dobavljaci, nabavka (povecava stanje), otpis (smanjuje
stanje), akcije/popusti, korisnici. Vidi i menadzerske izvjestaje i statistiku
(tabovi Izvjestaji i Statistika), da ne mora posebno prijavljivati menadzera.

Akcije se prave na jedan artikal ILI na cijelu kategoriju (vaze i za sve
podkategorije). Artikal sa vlastitom akcijom zadrzava nju: npr. 10% na Pica +
20% na Coca Colu -> sva pica idu 10%, a Cola 20%. Ako je vise kategorija u
lancu na akciji, vazi ona najbliza artiklu.

Artikal se u nabavci, otpisu i akcijama bira pretragom: kuca se dio naziva ili
sifre, prijedlozi iskacu ispod polja, strelice biraju a Enter potvrdjuje. Kad
se artikal odabere, forma se sama popuni (kolicina 1, nabavna cijena sa zadnje
nabavke tog artikla) i fokus skoci na kolicinu, pa se stavka doda sa dva Entera.
Svi datumi u aplikaciji se biraju iz kalendara (klik na polje/dugme), nista se
ne kuca rucno.

**Prodavac** - kucanje racuna preko sifre artikla, popust se sam obracuna ako
je akcija, PDV 17%, gotovina (racuna kusur) ili kartica, racun se snimi kao
PDF u folder racuni/. Moze i povrat robe i storno racuna (kod storna se kupcu
vraca iznos umanjen za vec vracene stavke).

**Menadzer** - izvjestaji: promet i racuni, promet po prodavacu, povrati,
nabavke, stanje zaliha, najprodavaniji artikli + tab Statistika (KPI kartice
i grafikoni: promet po danima/kategorijama/prodavacima/satima, nacin
placanja, top artikli).

## Filteri, straniceenje i PDF izvjestaji

- Svaka tabela u admin i menadzer prostoru ima filtere (tekst, kategorija sa
  podkategorijama, dobavljac, rasponi datuma/cijene/stanja/iznosa, status...).
- Tabela se osvjezava dok se kuca, dugme "Filtriraj" ne treba stiskati. Red
  filtera pise koliko je filtera aktivno, a "Ponisti" je upaljen samo kad ima
  sta ponistiti.
- Svako polje pretrage ima padajucu listu prijedloga: postojece vrijednosti iz
  baze i nedavno trazeni pojmovi, strelice biraju, Enter potvrdjuje, "x" cisti.
- Pretraga ne razlikuje velika/mala slova ni kvacice (kucanje bez kvacica nalazi
  i nazive sa njima).
- Raspon datuma se postavlja jednim klikom: Danas, Juce, Zadnjih 7 dana, Ova
  sedmica, Ovaj mjesec, Prosli mjesec, Ova godina, Sve.
- Neispravan broj ili obrnut raspon (od > do) se javlja u samom redu filtera
  (crveni okvir oko polja), bez iskakanja dijaloga na svaku tipku.
- Velike tabele se ne ucitavaju cijele: baza vraca stranu po stranu (dugmad
  |< < > >| ispod tabele), pa aplikacija radi i sa milionima zapisa.
- Svaki prikaz ima dugme "Izvezi u PDF" - izvjestaj sa naslovom, opisom
  filtera, brojevima strana ide u folder izvjestaji/. Statistika se izvozi
  zajedno sa grafikonima.

## Napomene

- pos.db je SQLite fajl u folderu aplikacije, moze se otvoriti sa DB Browserom.
  Radi u WAL modu (uz njega stoje i pos.db-wal i pos.db-shm) jer je bez toga
  upis racuna oko 100x sporiji
- folder fonts/ mora stajati uz aplikaciju (fontovi za PDF, zbog nasih slova)
- datumi se unose kao dd.MM.gggg, decimale mogu i sa tackom i sa zarezom
- slike artikala idu u images/ folder, fajl se zove po sifri (npr. 1001.jpg)

## Struktura

```
src/pos/
  Main.java    - main
  model/       - klase za podatke (Korisnik, Artikal, Racun...)
  data/        - Baza.java (sva logika oko podataka), Filteri.java, JPA konfiguracija
  util/        - parsiranje/formatiranje + PdfRacun (racun) + PdfIzvjestaj (izvjestaji)
  ui/          - prozori (prijava, admin, prodavac, menadzer) + zajednicki paneli
                 (IzvjestajiPanel, StatistikaPanel, Grafikon, Pager, RedFiltera,
                  PoljePretrage, PretragaArtikla, BiracDatuma)
```
