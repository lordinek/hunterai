"""Yapay zeka sesi icin robotik, duygusuz, tekduze efekt zinciri (48 kHz mono int16)."""
import numpy as np

HZ = 48000
VARSAYILAN = {
    "perde": 0.88,          # < 1: ses alcalir (ve yavaslar)
    "halka_hz": 40.0,       # halka modulasyonu frekansi (metalik tinlama)
    "halka_karisim": 0.45,  # 0..1
    "bit_ezme": 3,          # n ornekte bir ornek tut (dusuk ornekleme hissi)
    "yanki_ms": 28.0,
    "yanki_geri": 0.45,
    "yanki_karisim": 0.35,
    "kazanc": 0.95,
}


def uygula(pcm: np.ndarray, ayar: dict | None = None) -> np.ndarray:
    a = dict(VARSAYILAN)
    a.update(ayar or {})
    x = pcm.astype(np.float64) / 32768.0

    # perde alcaltma: dogrusal yeniden ornekleme
    if a["perde"] != 1.0:
        n = int(len(x) / a["perde"])
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)

    # bit ezme: ornek tut
    k = int(a["bit_ezme"])
    if k > 1:
        x = np.repeat(x[::k], k)[: len(x)]

    # halka modulasyonu
    t = np.arange(len(x)) / HZ
    halka = x * np.sin(2 * np.pi * a["halka_hz"] * t)
    x = (1 - a["halka_karisim"]) * x + a["halka_karisim"] * halka

    # metalik yanki (geri beslemeli comb)
    gecikme = int(HZ * a["yanki_ms"] / 1000)
    y = x.copy()
    for i in range(gecikme, len(y)):
        y[i] += a["yanki_geri"] * y[i - gecikme]
    x = (1 - a["yanki_karisim"]) * x + a["yanki_karisim"] * y

    zirve = np.max(np.abs(x)) or 1.0
    x = x / zirve * a["kazanc"]
    return (x * 32767).astype(np.int16)
