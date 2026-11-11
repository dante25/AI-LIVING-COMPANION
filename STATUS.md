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
| 2 Odnos u memoriji | Vrste tvrdnji (šala, svađa, što joj smeta), ispravka čuva vrstu | `core/.../memory/` | dio od 95 |
| 8 Wake word (logika) | Prag povjerenja i pauza nakon buđenja; bez mikrofona | `core/.../voice/WakeWord.kt` | dio od 95 |
| 9 Akcije | Aplikacije, pretraga i slanje prolaze kroz dozvole i potvrdu; neuspjeh se prijavljuje kao neuspjeh | `core/.../actions/` | dio od 95 |
| 10 Autonomno ponašanje (pravila) | Spava, sjeda, zaleti se na ekran ili miruje, prema stanju duše; namjere samo iz dozvoljene liste | `core/.../behavior/` | dio od 95 |

Ukupno: Python 15 testova, Kotlin 106 testova (provjereno kompajlerom kotlinc 2.1.0 i JUnit konzolom).

Dokumenti: `PLAN_v2.md` (plan i odluke), `Mini_Alexa_*` (originalni planovi), `README.md`.

## Android aplikacija (prva verzija)

APK: `build/apk/mini-alexa-debug.apk` (poslan kao fajl, nije u repou).

- `app/`: ekran chata, unos tekstom i glasom (Android prepoznavanje govora), odgovor se čita naglas (Android TTS).
- Memorija se čuva trajno na telefonu (SQLite). Dozvole u postavkama se pamte, a u sesiji važe samo dok je chat otvoren. Paketi se uvoze i čuvaju između pokretanja.
- Odgovori su `RuleModel` (pravila iz stanja duše: raspoloženje, dosada, ljutnja). Nije jezički model. Pravi lokalni model je faza 7.
- Build: `ANDROID_HOME=... KOTLINC=... ./scripts/build_apk.sh` → `build/apk/mini-alexa-debug.apk`.
- Potpisan debug ključem, minSdk 26, targetSdk 35.
- Nije pokrenut ni na emulatoru ni na telefonu.

## Nije urađeno

**Blokirano okruženjem:**
- Android: SDK je instaliran nakon što je `dl.google.com` dodan u dozvoljene domene. APK se gradi skriptom `scripts/build_apk.sh`. Nije testiran na telefonu ni emulatoru.
- Gradle: `downloads.gradle.org` je blokiran, pa se APK gradi bez Gradle-a. Gradle build nije podešen.

**Čeka tvoj unos:**
- 3D model: nije pronađen. Treba fajl (`.vrm`, `.glb`, `.fbx`).
- Demo glasovi: nisu pronađeni. Treba `.onnx` + `.json` par.
- Mjerenja na Z Fold 7 (RAM, baterija, latencija): zahtijevaju telefon.
- Odluka o cloud LLM-u: nema API ključa, pa cloud adapter nije napravljen.

**Ostaje za kasnije u kodu:**
- SQLite memorija (faza 2 trajno skladište): ide uz Android.
- Sažimanje i vektorska pretraga (faza 2 i 4): zahtijevaju model.
- Pravi detektor wake worda i mjerenje lažnih buđenja: zahtijeva mikrofon i telefon.
- Izvođenje akcija (otvaranje aplikacija, slanje, pretraga): samo sučelje i pravila; stvarni handleri su u Android sloju.
- Stvarno ponašanje na ekranu (hodanje, sjedenje): samo odluka; prikaz dolazi s avatarom.
- Katalog za preuzimanje modela i HTTP: ide u Android sloj.
- Lokalni model (LiteRT-LM ili llama.cpp): faza 7, zahtijeva telefon.
- Mikrofon, zvučnik, chat ekran: faza 5, zahtijeva Android.
- Kamera (slikanje po zahtjevu), overlay, sjedenje na tastaturi (servis pristupačnosti): faze 8–10, traže Android.
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
