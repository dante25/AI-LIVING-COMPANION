# STATUS Mini Alexa

Zadnje ažuriranje: 2026-11-11. Grana: `claude/eager-archimedes-366atg`.

## Osnova

Aplikacija je **AndroLLM** (Apache 2.0) u folderu `android/`, prepravljena u Alexu. Raniji pokušaj ispočetka je u `app/` i `core/` i ostaje samo kao referenca.

Build (Linux/WSL, JDK 17+, Android SDK 36, NDK 26.1.10909125, CMake 3.22.1):

```bash
ANDROID_HOME=/putanja/do/sdk LC_ALL=C.UTF-8 ./scripts/build_androllm.sh /putanja/do/repo/android
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Verzija 0.5.

## Urađeno (provjereno: build prolazi, jedinični testovi prolaze)

| Korak | Šta | Modul |
|---|---|---|
| Izgled | Roze tema (svijetla, tamna, AMOLED), kontrast provjeren računski, natpisi na bosanskom | `core/ui`, `feature/chat` |
| Bez prijave | Ekran prijave se preskače, ništa se ne šalje na server | `app` (AppNavHost) |
| Procesor | Automatski izbor je CPU (NPU/GPU samo na izričit izbor), jer pad native biblioteke ruši cijelu aplikaciju | `engine` |
| 1 Duša | Stanje (raspoloženje, energija, dosada, ljutnja, privrženost), pravila ponašanja, uputa modelu u svakom odgovoru | `core/soul` |
| Karakter | Alexa persona na bosanskom (uređuje se u Postavkama), manji spisak alata u uputi | `feature/chat` |
| Postavke Alexe | Uređivanje karaktera, prekidač duše, prikaz stanja, resetovanje, kartica raspoloženja na početnom ekranu | `feature/settings`, `feature/home` |
| 2 Memorija | Prijedlozi se potvrđuju, ispravke zamjenjuju stare tvrdnje, kategorije odnosa, ekran Memorija, migracija baze 3 na 4 | `core/memory` |
| 3 Dozvole i sesije | Politika po dozvoli (isključeno, pitaj svaki put, pitaj jednom u sesiji), kamera uvijek pita, novi INTERNET i MICROPHONE, sesija 15 min, gašenje servisa nakon 1 h, trajni dnevnik akcija | `core/permissions`, `core/tools` |
| 5 Ekstenzije | Registar funkcija: nova funkcija je jedna klasa i jedna `@Binds @IntoSet` linija | `core/extensions` |
| 6 Paketi i backup | `.alexapack` paketi (provjera, bez izvršnog koda), "Izvezi sve" / "Uvezi sve" jednim zipom, snapshot prije uvoza | `core/packs`, `core/backup` |

Preuzimanje modela ne zavisi od dozvola. Ekstenzije se dodaju i preko MCP servera koji AndroLLM već ima.

## Avatar: odluke i ponašanje (zabilježeno)

- **Odluka (ažurirano):** 3D u realnom vremenu (Filament / SceneView, glTF `.glb`) sa toon izgledom; model je napravljen na Tripo i Meshy (preuzima se u bilo kojem formatu). Rezerva: 2,5D Live2D. Detalji i spisak za preuzimanje: `docs/AVATAR_ZADATAK.md`. Kostur avatara već postoji (`feature/avatar`, probni lik).
- **Izrada modela:** korisnik ima na laptopu Blender, Live2D Cubism i Krita. Odavde ne mogu da ih pokrenem; treba sesija na laptopu (vidi niže).
- **Pokreti iz duše:** izraz lica i držanje dolaze iz stanja duše; usta prate zvuk Alexinog glasa (PCM iz TTS-a); treptanje, disanje, praćenje.
- **"Alexa, miči se" (miči guzicu, makni se, smetaš mi):** lik se premješta na drugo mjesto na ekranu koje ne zaklanja tastaturu ni polje za unos. Prepoznavanje naredbe i biranje mjesta su gotovi u `core/soul` (`AvatarPlacement.kt`, testirano). Prikaz lika i prepoznavanje tastature u drugim aplikacijama (servis pristupačnosti) dolaze s avatarom.
- Slične naredbe: idi lijevo/desno/gore/dolje, vrati se.

## Nije urađeno (po redu)

1. **Optimizacija i stabilnost:** veličina APK-a (oko 278 MB), pokretanje, memorija, rezervni izbor modela.
2. **Slabi i jaki model** s automatskim izborom po težini zahtjeva i budžetom tokena (čeka plaćeni API; AndroLLM ima mjerenje potrošnje).
3. **Autonomno ponašanje** (spava, sjedi, traži pažnju): pravila postoje u `core/soul` (`SoulAction`), prikaz ide s avatarom.
4. **Katalog "Preporučeno"** na ekranu Modeli (AndroLLM već ima pretragu Hugging Face-a i preuzimanje).
5. **Wake word "Alexa"** umjesto "Hey Andro", glas (bosanski), **avatar**, odjeća, pjevanje: potpuno zadnje, kako je dogovoreno.

## Poznata ograničenja

- Gradle build je provjeren samo na Linuxu u oblaku, ne na tvom računaru. Aplikacija nije pokrenuta na uređaju osim ranijih verzija.
- Model Gemma 4 12B vjerovatno ne staje u RAM telefona uz sve ostalo; preporuka je Gemma 4 E4B ili E2B na CPU-u.
- Wake word i dalje radi s fraza "Hey Andro".

## Testovi

Moduli `core/soul`, `core/permissions`, `core/memory`, `core/extensions`, `core/packs`, `core/backup`: svi jedinični testovi prolaze (preko 200). Pokretanje:

```bash
LC_ALL=C.UTF-8 ./gradlew :core:soul:testDebugUnitTest :core:memory:testDebugUnitTest
```
