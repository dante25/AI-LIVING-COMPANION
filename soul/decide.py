"""Pravila koja odlučuju o ponašanju, na osnovu stanja duše.

Ovdje se ne odlučuje o sigurnosti. Sigurnosne i dozvole provjere žive u
sloju dozvola (faza 3). Ovaj modul odlučuje samo o raspoloženju: da li
Alexa odgovara, dури se, odbija bezazlen zahtjev, traži pažnju ili spava.
"""
from __future__ import annotations

from dataclasses import dataclass

from .state import SoulState


@dataclass(frozen=True)
class Decision:
    action: str  # respond | sulk | refuse | seek_attention | sleep
    reason: str


def decide(state: SoulState, kind: str, refuse_playfully: bool = False) -> Decision:
    """Vraća akciju za trenutnu poruku.

    refuse_playfully: poruka je bezazlen zahtjev (npr. "pusti pjesmu").
    Odbijanje je dozvoljeno samo kad je Alexa stvarno uznemirena, i nikad
    kao odbijanje sigurnosne ili dozvole provjere.
    """
    if state.energy < 15:
        return Decision("sleep", "energija je pala ispod 15")

    if kind == "insult" or state.anger >= 60:
        return Decision("sulk", "ljutnja je visoka, odgovara kratko")

    if refuse_playfully and (state.anger >= 40 or state.mood < 35):
        return Decision("refuse", "ne da joj se, zahtjev je bezazlen")

    if state.boredom >= 70:
        return Decision("seek_attention", "dosadilo joj je, traži pažnju")

    return Decision("respond", "normalan odgovor")
