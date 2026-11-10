# Mini Alexa: plan v2

Ovaj dokument zamjenjuje redoslijed faza iz plana v1.0. Tehnička pravila iz
v1.0 (offline-first, Alexa ≠ LLM, export/import, snapshot prije zamjene) ostaju.

## Cilj prve verzije

Alexa kao glasovni chat (live chat, bez avatara) na Samsung Galaxy Z Fold 7
(12 GB RAM, najnoviji Android). Avatar dolazi kasnije, pa desktop nakon što
telefonska verzija bude stabilna.

## Odluke

**Sesije i dozvole**
- Sesija počinje otvaranjem chata ili wake wordom. Završava se zatvaranjem
  chata, ili 15 min tišine (idle), a aplikacija se zatvara nakon 1 h bez obraćanja.
- Dozvole važe samo u sesiji i resetuju se pri završetku sesije.
- Kamera: uvijek pita, jedna slika po zahtjevu, bez trajne dozvole, uz indikator.
- Podrazumijevano je zabranjeno: internet, kamera, aplikacije, pretraga.
- Sve što ide van chata (aplikacije, pretraga, slanje) traži dozvolu i bilježi se u dnevnik.
- Wake word nakon zatvaranja aplikacije ne radi.
- Android zahtijeva stalnu obavijest dok Alexa sluša u pozadini; to ostaje vidljivo.

**Modeli**
- Dva nivoa: slabiji (brz, lokalni, uvijek dostupan) i jači (oblak ili lokalni ako telefon podnese).
- Izbor lokalno/oblak je ručni, u postavkama. Automatski izbor dolazi kad bude plaćen API.
- Model manager: preuzimanje iz kataloga (uz provjeru integriteta), uvoz fajla, brisanje.
- Brisanje modela briše samo fajlove modela. Memorija, razgovori i sažeci ostaju.
- Uvoz modela ne šalje fajl na internet. Izvoz modela se provjerava po licenci.
- Automatski izbor po težini zahtjeva (kad bude plaćen API): pravila, ne drugi model.
  Lagani zahtjev ide na slabiji, a eskalacija na jači ide samo kad odgovor ne prođe provjeru.
  Dnevni i mjesečni limit tokena: pri 80% upozorenje, pri 100% prelazak na lokalni.

**Duša**
- Stanje (raspoloženje, energija, dosada, ljutnja, privrženost) odlučuje o ponašanju.
  LLM ga glumi, ali odluku donosi pravilo.
- Ljutnja i odbijanje su igra: uvijek se vraćaju u dobro raspoloženje.
- Odbijanje se nikad ne koristi za sigurnosne ili dozvole provjere.

**Paketi sadržaja**
- Format: zip s `manifest.json` (tip, ime, autor, verzija, licenca, verzija formata).
- Tipovi: odjeća, glasovi, animacije, osobnosti. Paketi su samo podaci, nikad kod.
- Memorija i razgovori se ne izvoze u pakete. Identitet export je zasebna, šifrovana funkcija.
- Klonirani glasovi tuđih osoba se ne prave bez pristanka.

## Faze

| Faza | Šta | Prolaz |
|---|---|---|
| 0 | Priprema: repo, inventar (3D model, demo glasovi), mjerenje Z Fold 7, Android toolchain | Repo i toolchain rade; popis sadržaja gotov |
| 1 | Duša kao modul (prototip u Pythonu, pa Kotlin), testovi, simulator | Testovi prolaze; simulator se ponaša po pravilima |
| 2 | Memorija: razgovori, odnos, ispravke, pretraga, backup identiteta | Prekid ne gubi podatke; ispravka pobjeđuje staru tvrdnju |
| 3 | Dozvole i postavke: prekidači, zabrane po defaultu, dnevnik, sesije | Ništa van chata ne radi bez dozvole |
| 4 | Model sloj: cloud adapter, slabi i jaki model, model manager | Chat radi; zamjena modela ne mijenja Alexu |
| 5 | Glas: live chat, push-to-talk, TTS po rečenicama, prekid, Android chat ekran | **Prva upotrebljiva verzija** |
| 6 | Paketi sadržaja: uvoz i izvoz glasova, osobnosti, odjeće | Paket se uvozi bez prerade koda |
| 7 | Lokalni jači model, rad bez interneta, mjerenje RAM-a i baterije | Chat radi u avionskom režimu |
| 8 | Wake word i kamera na zahtjev | Hej Alexa radi; kamera traži dozvolu svaki put |
| 9 | Akcije i alati: aplikacije, pretraga, sve kroz dozvole | Akcije se traže i bilježe |
| 10 | Overlay i autonomno ponašanje | Lik se ponaša po stanju (šeta, sjedi, spava) |
| 11 | Avatar na Z Fold 7 | Prati stanje i govor bez zamrzavanja |
| 12 | Odjeća i dodatne animacije preko paketa | Novi outfit ili pokret se uvozi |
| 13 | Pjevanje (zaseban model, istraživanje) | Odluka se donosi kasnije |
| 14 | Sync i desktop | Pet ciklusa Android ↔ desktop bez gubitka |

## Otvoreno

- Tačan 3D model i demo glasovi: nepoznato dok ih ne pronađem.
- Mjerenja na Z Fold 7 (RAM, baterija, latencija): faza 0 i 7.
- Vrijeme unloada modela: određuje se mjerenjem u fazi 7.
- Da li overlay i sjedenje na tastaturi (servis pristupačnosti) mogu ići na Google Play: provjeriti prije faze 10.
