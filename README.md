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
- menadzer / menadzer (menadzer - Denis)

Baza se sama napuni testnim podacima kad se prvi put pokrene. Ako se obrise
pos.db, na sljedecem pokretanju opet krene od testnih podataka.

## Sta ko moze

**Admin** - artikli, kategorije (imaju i podkategorije), dobavljaci, nabavka
(povecava stanje), otpis (smanjuje stanje), akcije/popusti, korisnici.

**Prodavac** - kucanje racuna preko sifre artikla, popust se sam obracuna ako
je akcija, PDV 17%, gotovina (racuna kusur) ili kartica, racun se snimi kao
PDF u folder racuni/. Moze i povrat robe i storno racuna.

**Menadzer** - izvjestaji: dnevni/sedmicni/mjesecni promet, promet po
prodavacu, nabavke, stanje zaliha, najprodavaniji artikli.

## Napomene

- pos.db je SQLite fajl u folderu aplikacije, moze se otvoriti sa DB Browserom
- folder fonts/ mora stajati uz aplikaciju (fontovi za PDF, zbog nasih slova)
- datumi se unose kao dd.MM.gggg, decimale mogu i sa tackom i sa zarezom
- slike artikala idu u images/ folder, fajl se zove po sifri (npr. 1001.jpg)

## Struktura

```
src/pos/
  Main.java    - main
  model/       - klase za podatke (Korisnik, Artikal, Racun...)
  data/        - Baza.java (sva logika oko podataka), JPA konfiguracija
  util/        - parsiranje/formatiranje + PdfRacun (stampanje)
  ui/          - prozori (prijava, admin, prodavac, menadzer)
```
