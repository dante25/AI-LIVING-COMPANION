# Zadatak: avatar Alexe (2,5D, Live2D stil)

Za sesiju koja radi na laptopu s Blenderom, Live2D Cubism Editorom i Kritom.
Aplikacija je u `android/` (Kotlin, Jetpack Compose). Ovaj zadatak NE mijenja kod aplikacije,
nego pravi model. Aplikacija ga učitava iz `android/app/src/main/assets/avatar/alexa/`.

## Cilj

Ilustrovan, visoke rezolucije, 2,5D lik: ženska saputnica, razigrana, šašava, luckasta i topla.
Lakši za resurse od 3D-a. Mora izgledati živo: treptanje, disanje, govor (usta prate zvuk),
izrazi lica koji dolaze iz stanja duše (raspoloženje, dosada, ljutnja), i poze za ponašanje na ekranu.

## Ulaz

- Ako postoji 3D model (generisani kandidat): u Blenderu ga renderuj u **odvojene slojeve** (vidi Slojevi).
- Ako nema 3D modela: ilustracija u Kriti (ili već gotova slika), razdvojena u slojeve.
- Ništa se ne smije preuzimati tuđe bez prava korištenja; licenca ide u `LICENCE.md` pored modela.

## Slojevi (PSD za Live2D), svaki dio zaseban sloj s providnom pozadinom

Glava: kosa pozadi, kosa sprijeda (pramenovi odvojeni), lice (baza), obrve L/R, oko L/R (bjeonjača,
šarenica, kapak gornji, kapak donji, trepavice), nos, usta (zatvorena, otvorena A, otvorena O,
osmijeh, tuga), uši, vrat. Tijelo: trup, ruke L/R (nadlaktica, podlaktica, šaka), noge L/R, odjeća
odvojeno po komadima (da se kasnije mijenja garderoba).

## Parametri koje model MORA imati (standardni nazivi Live2D)

Aplikacija će ih mijenjati u hodu:

- ParamAngleX, ParamAngleY, ParamAngleZ (glava)
- ParamEyeLOpen, ParamEyeROpen, ParamEyeBallX, ParamEyeBallY
- ParamBrowLY, ParamBrowRY, ParamBrowLAngle, ParamBrowRAngle
- ParamMouthOpenY (govor, pokreće ga zvuk), ParamMouthForm (osmijeh/tuga)
- ParamBodyAngleX, ParamBodyAngleY, ParamBodyAngleZ, ParamBreath
- ParamCheek (rumenilo), ParamArmLA, ParamArmRA (ruke za mahanje i naslanjanje)

## Izrazi (exp3.json)

neutralna, radosna, razigrana (jezik van, namigivanje), ljuta (durenje), dosadna, pospana,
zbunjena, iznenađena, zaljubljena.

## Pokreti (motion3.json), kratki i petlje

- idle (disanje, treptanje, blago ljuljanje), govor (mali pokreti glave)
- mahanje, smijeh, durenje (okrene glavu, prekrsti ruke), zijevanje, spavanje
- poze: stoji, sjedi, naslanja se rukama na rub ekrana (kad je pozovu), sjedi na tastaturi

## Izvoz

Folder `android/app/src/main/assets/avatar/alexa/`:

- `alexa.model3.json`, `alexa.moc3`, `alexa.physics3.json`, `alexa.cdi3.json`
- teksture `alexa.4096/texture_00.png` (najviše 2 teksture, 4096x4096)
- `expressions/*.exp3.json`, `motions/*.motion3.json`
- `LICENCE.md` (izvor ilustracije, prava korištenja)

Dodatno u istom folderu: `PREVIEW.png` (slika modela) i `PARAMETRI.md` (popis stvarnih naziva parametara i
raspona, ako se razlikuju od gornjih).

## Pipeline u Blenderu (ako postoji 3D model)

1. Postavi kameru ortografski, frontalno; stil "toon": kontura (Freestyle) i jednostavno sjenčenje.
2. Svaku grupu (kosa, lice, oči, usta, odjeća, tijelo) stavi u zasebnu kolekciju.
3. Python skriptom izrenderuj svaku kolekciju zasebno s providnom pozadinom, istim kadrom i rezolucijom 4096.
4. Spoji ih u PSD sa slojevima (Krita: Python API ili ručno), nazivi slojeva po tablici iznad.
5. Usta: izrenderuj varijante A, O, E, zatvorena, osmijeh. Oči: otvorene, poluzatvorene, zatvorene.

## Kad završiš

- Commit u istu granu s porukom "Avatar: model Alexe" i dodaj kratak izvještaj u `STATUS.md`.
- Šta nije urađeno ili nije provjereno napiši jasno. Ne tvrdi da je rig završen ako nije otvoren i provjeren u Cubism Vieweru.

## Šta radi aplikacija (ne ti)

Učitavanje modela (Cubism SDK ili Rive), pokretanje usta po zvuku glasa, izrazi iz duše, premještanje
lika ("Alexa, miči se"), lebdenje iznad drugih aplikacija. To ostaje za sesiju koja radi na aplikaciji.
