# Mini Alexa

Offline-first AI saputnica za Android (Z Fold 7), kasnije Windows.
Alexa je identitet i ponašanje, a LLM, glas i model su zamjenjivi moduli.

## Trenutni status

- Faza 0 (priprema): u toku. Dokumenti u repou, kontrolne sume provjerene.
- Faza 1 (duša kao modul): prvi dio gotov, prototip u Pythonu.

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
