"""Brojčano stanje Alexine duše. Sve vrijednosti su u rasponu 0–100.

Ovo je srce ponašanja: LLM dobija stanje i glumi ga, ali odluku o ponašanju
donosi pravilo u decide.py, pa je ponašanje dosljedno, a ne nasumično.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass

MIN_LEVEL = 0.0
MAX_LEVEL = 100.0
MOOD_HOME = 60.0  # raspoloženje se uvijek vraća ovdje (dobro raspoloženje)


def _clamp(value: float) -> float:
    return max(MIN_LEVEL, min(MAX_LEVEL, value))


@dataclass
class SoulState:
    mood: float = 60.0        # raspoloženje
    energy: float = 80.0      # energija
    boredom: float = 10.0     # dosada
    anger: float = 0.0        # ljutnja
    attachment: float = 40.0  # privrženost

    def __post_init__(self) -> None:
        for name in ("mood", "energy", "boredom", "anger", "attachment"):
            setattr(self, name, _clamp(getattr(self, name)))

    def apply(self, **deltas: float) -> None:
        """Dodaje promjene na stanje, uz ograničenje na 0–100."""
        for name, delta in deltas.items():
            setattr(self, name, _clamp(getattr(self, name) + delta))

    def as_dict(self) -> dict:
        return asdict(self)


# Promjene koje nastaju kao reakcija na poruku korisnika.
EVENTS: dict[str, dict[str, float]] = {
    "greeting": {"mood": 5, "attachment": 4, "boredom": -10},
    "joke": {"mood": 6, "boredom": -8},
    "praise": {"mood": 8, "attachment": 3, "anger": -10},
    "insult": {"mood": -10, "anger": 20, "attachment": -3},
    "command": {"boredom": -3, "attachment": 1},
    "chat": {"boredom": -4, "attachment": 1},
}


def on_event(state: SoulState, kind: str) -> None:
    """Primjenjuje reakciju na jednu poruku korisnika."""
    if kind not in EVENTS:
        raise ValueError(f"Nepoznat događaj: {kind!r}")
    state.apply(**EVENTS[kind])


def tick(state: SoulState, minutes: float) -> None:
    """Protok vremena bez obraćanja: raste dosada, ljutnja se smiruje."""
    if minutes <= 0:
        return
    state.apply(
        boredom=0.4 * minutes,
        energy=-0.1 * minutes,
        anger=-1.0 * minutes,
        attachment=-0.02 * minutes,
    )
    # raspoloženje se polako vraća na dobro raspoloženje
    drift = (MOOD_HOME - state.mood) * min(1.0, 0.05 * minutes)
    state.apply(mood=drift)


def rest(state: SoulState, minutes: float) -> None:
    """Odmor (spavanje ili idle): vraća energiju."""
    if minutes > 0:
        state.apply(energy=0.5 * minutes)
