"""Tekstualni simulator duše. Testiranje ličnosti bez telefona i bez modela.

Pokretanje:  python3 -m soul
Komande:     /stanje        prikaz brojčanog stanja
             /prodji N      prođe N minuta bez obraćanja
             /kraj          izlaz
"""
from __future__ import annotations

from typing import Callable

from .decide import decide
from .state import SoulState, on_event, rest, tick

KEYWORDS: list[tuple[str, tuple[str, ...]]] = [
    ("insult", ("glup", "idiot", "mrzim", "dosadna")),
    ("praise", ("bravo", "super", "hvala", "odlična")),
    ("joke", ("haha", "šala", "vic", "smij")),
    ("greeting", ("bok", "hej", "zdravo", "dobar dan")),
]

REPLIES = {
    "respond": "Evo me. Šta ima?",
    "sulk": "...",
    "refuse": "Ne da mi se. Pitaj me kasnije.",
    "seek_attention": "Halo?! Jesi li tu? Gledaj me!",
    "sleep": "*zijevne* Ja bih malo da spavam.",
}


def classify(text: str) -> str:
    lowered = text.lower()
    for kind, words in KEYWORDS:
        if any(word in lowered for word in words):
            return kind
    return "chat"


def format_state(state: SoulState) -> str:
    return (
        f"raspoloženje={state.mood:.0f} energija={state.energy:.0f} "
        f"dosada={state.boredom:.0f} ljutnja={state.anger:.0f} "
        f"privrženost={state.attachment:.0f}"
    )


def handle_message(state: SoulState, text: str) -> tuple[str, str]:
    """Obrađuje jednu poruku i vraća (odgovor, razlog) za pregled."""
    kind = classify(text)
    on_event(state, kind)
    decision = decide(state, kind, refuse_playfully=(kind == "chat" and text.endswith("!")))
    return REPLIES[decision.action], decision.reason


def run(
    state: SoulState,
    read: Callable[[str], str] = input,
    out: Callable[[str], None] = print,
) -> None:
    out("Alexa simulator. /stanje, /prodji N, /kraj")
    while True:
        try:
            text = read("ti> ").strip()
        except EOFError:
            break
        if not text:
            continue
        if text == "/kraj":
            break
        if text == "/stanje":
            out(format_state(state))
            continue
        if text.startswith("/prodji"):
            parts = text.split()
            minutes = float(parts[1]) if len(parts) > 1 else 15.0
            tick(state, minutes)
            out(f"(prošlo {minutes:.0f} min) {format_state(state)}")
            continue
        reply, reason = handle_message(state, text)
        out(f"alexa> {reply}  [{reason}]")


if __name__ == "__main__":
    run(SoulState())
