# Mini Alexa

Offline-first AI saputnica za Android (Z Fold 7), kasnije Windows.
Alexa je identitet i ponašanje, a LLM, glas i model su zamjenjivi moduli.

## Trenutni status

- Faza 0 (priprema): u toku. Dokumenti u repou, kontrolne sume provjerene.
- Faza 1 (duša): gotova u Pythonu i Kotlinu, testovi prolaze.
- Faza 2 (memorija): ugovor i memorija u RAM-u gotovi; SQLite dolazi u fazi 5.
- Faza 3 (dozvole i sesije): gotova.
- Faza 4 (model sloj): router, eskalacija, budžet, registar modela; cloud adapter i katalog za preuzimanje dolaze kasnije.
- Faza 5 (glas, dio u core-u): ugovori za STT i TTS, dijeljenje na rečenice, turn i prekid, chat tok. Mikrofon, zvučnik i Android UI su još u planu.

U `core/` ukupno 62 testa prolazi.

Kotlin testovi se pokreću kompajlerom kotlinc 2.1.0 i JUnit konzolom (Gradle build
još nije provjeren).

## Duša (`soul/`)

Brojčano stanje (raspoloženje, energija, dosada, ljutnja, privrženost) i
pravila koja odlučuju o ponašanju. LLM dobija stanje, ali ne odlučuje sam.

```bash
python3 -m unittest discover -v   # testovi
python3 -m soul                    # tekstualni simulator
```

Komande u simulatoru: `/stanje`, `/prodji N` (prođe N minuta), `/kraj`.

## Plan

Vidi `Mini_Alexa_Plan_razvoja_po_fazama_v1_0-1_docx.txt` i tehnički PDF.
Novi redoslijed faza (duša, memorija, dozvole, model, glas, …) je u razgovoru
i biće zapisan u `PLAN_v2.md`.
