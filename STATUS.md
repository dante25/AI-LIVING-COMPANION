# STATUS Mini Alexa

Zadnje ažuriranje: 2026-11-10. Grana: `claude/eager-archimedes-366atg`.

## Urađeno

| Faza | Šta | Gdje | Testovi |
|---|---|---|---|
| 1 Duša | Stanje (raspoloženje, energija, dosada, ljutnja, privrženost), pravila ponašanja, simulator | `soul/` (Python), `core/.../soul/` (Kotlin) | Python 15, Kotlin dio od 79 |
| 2 Memorija (ugovor) | Poruke se ne brišu, prijedlozi prije potvrde, ispravke pobjeđuju stare, izvori obavezni | `core/.../memory/` | 9 |
| 3 Dozvole i sesije | Sve zabranjeno po defaultu, dozvola samo u sesiji, kamera uvijek pita, potvrde, dnevnik, sesije 15 min i 1 h | `core/.../permissions/` | 14 |
| 4 Model sloj | Slabi i jaki nivo, lokalno/oblak ručno, eskalacija samo kad odgovor ne prođe, budžet tokena (80% upozorenje), registar modela (brisanje briše samo fajlove) | `core/.../model/` | dio od 51 |
| 5 Glas (core) | Ugovori za STT i TTS, dijeljenje na rečenice, turn i prekid, chat tok | `core/.../voice/`, `core/.../chat/` | dio od 62 |
| 6 Paketi sadržaja (core) | Manifest i kontrolne sume, zabrana izvršnog koda, glas s poznatim izvorom, identitet se ne pakuje, nepoznat tip neaktivan | `core/.../packs/` | dio od 79 |

Ukupno: Python 15 testova, Kotlin 79 testova (provjereno kompajlerom kotlinc 2.1.0 i JUnit konzolom).

Dokumenti: `PLAN_v2.md` (plan i odluke), `Mini_Alexa_*` (originalni planovi), `README.md`.

## Nije urađeno

**Blokirano okruženjem:**
- Android SDK: `dl.google.com` je blokiran politikom (403). Zahtjev za dodavanje hosta je odbijen dva puta. Zato nema Android projekta, APK-a, emulatora ni testa na telefonu.
- Gradle build: nije provjeren. Kotlin testovi su pokretani direktno kompajlerom.

**Čeka tvoj unos:**
- 3D model: nije pronađen. Treba fajl (`.vrm`, `.glb`, `.fbx`).
- Demo glasovi: nisu pronađeni. Treba `.onnx` + `.json` par.
- Mjerenja na Z Fold 7 (RAM, baterija, latencija): zahtijevaju telefon.
- Odluka o cloud LLM-u: nema API ključa, pa cloud adapter nije napravljen.

**Ostaje za kasnije u kodu:**
- SQLite memorija (faza 2 trajno skladište): ide uz Android.
- Odnos i svađe kao zasebna memorija (faza 2).
- Sažimanje i vektorska pretraga (faza 2 i 4): zahtijevaju model.
- Katalog za preuzimanje modela i HTTP: ide u Android sloj.
- Lokalni model (LiteRT-LM ili llama.cpp): faza 7, zahtijeva telefon.
- Mikrofon, zvučnik, chat ekran: faza 5, zahtijeva Android.
- Wake word, kamera, overlay, akcije (aplikacije, pretraga): faze 8–10.
- Avatar, odjeća, pjevanje, sync i Windows: faze 11–14.

## Šta treba uraditi na laptopu (kad bude prilika)

1. Dodati `dl.google.com` u dozvoljene domene okruženja (claude.ai/code → okruženje → Edit → Network access → Allowed domains).
2. Potvrditi kad je gotovo, pa nastavljamo s Android SDK-om.
3. Pronaći i poslati 3D model i demo glasove.
4. Izmjeriti Z Fold 7: model, RAM, verziju Androida, slobodan prostor.

## Kako provjeriti lokalno

```bash
python3 -m unittest discover -v   # Python prototip duše
```

Kotlin: `./gradlew :core:test` kad bude podešen Gradle (još nije provjereno).

## Sljedeći korak

Kad se vratimo na ovo: ili Android (kad bude SDK), ili nastavak čisto Kotlin dijelova (fazu 2 i 8 možemo dalje raditi bez telefona).
