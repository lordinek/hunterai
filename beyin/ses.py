"""Konusma sesi: metin -> ElevenLabs TTS -> 48 kHz, 16 bit, mono PCM (Simple Voice Chat'in bekledigi bicim)."""
import array
import json
import math
import os
import pathlib
import urllib.error
import urllib.request

import numpy as np

import ses_efekt

HEDEF_HZ = 48000
KAYNAK_HZ = 24000  # ElevenLabs output_format=pcm_24000


def _yukselt(ornekler: array.array, katsayi: int) -> array.array:
    """Dogrusal aradegerlemeyle ornekleme hizini katsayi kadar artirir."""
    cikti = array.array("h")
    n = len(ornekler)
    for i in range(n):
        a = ornekler[i]
        b = ornekler[i + 1] if i + 1 < n else a
        for k in range(katsayi):
            cikti.append(int(a + (b - a) * k / katsayi))
    return cikti


def _elevenlabs_pcm(beyin, metin: str) -> array.array:
    cfg = beyin.cfg["elevenlabs"]
    url = (f"https://api.elevenlabs.io/v1/text-to-speech/{cfg['ses_id']}"
           f"?output_format=pcm_{KAYNAK_HZ}")
    govde = json.dumps({"text": metin, "model_id": cfg.get("model_id", "eleven_multilingual_v2"),
                        "voice_settings": cfg.get("ses_ayarlari", {})}).encode("utf-8")
    istek = urllib.request.Request(url, data=govde, method="POST", headers={
        "xi-api-key": os.environ["ELEVENLABS_API_KEY"],
        "Content-Type": "application/json",
        "Accept": "application/octet-stream",
    })
    with urllib.request.urlopen(istek, timeout=60) as r:
        ham = r.read()
    ornek = array.array("h")
    ornek.frombytes(ham[: len(ham) // 2 * 2])
    return _yukselt(ornek, HEDEF_HZ // KAYNAK_HZ)


def _sahte_pcm(metin: str) -> array.array:
    """ElevenLabs olmadan boru hattini denemek icin: konusma ritminde derin bir uguldama."""
    sure = max(1.5, 0.065 * len(metin))
    n = int(sure * HEDEF_HZ)
    cikti = array.array("h")
    for i in range(n):
        t = i / HEDEF_HZ
        zarf = 0.55 + 0.45 * math.sin(2 * math.pi * 4.0 * t)
        v = 0.5 * math.sin(2 * math.pi * 110 * t) + 0.25 * math.sin(2 * math.pi * 220 * t) + 0.12 * math.sin(2 * math.pi * 330 * t)
        cikti.append(int(9000 * zarf * v))
    return cikti


def uret(beyin, istek: dict) -> dict:
    """istek: {oyun_klasoru, no, metin}"""
    klasor = pathlib.Path(istek["oyun_klasoru"]) / "ses"
    klasor.mkdir(parents=True, exist_ok=True)
    metin = str(istek["metin"])
    cfg = beyin.cfg["elevenlabs"]
    sahte = not os.environ.get("ELEVENLABS_API_KEY") or str(cfg.get("ses_id", "")).startswith("<")
    uretim = "sahte"
    if sahte:
        pcm = _sahte_pcm(metin)
    else:
        try:
            pcm = _elevenlabs_pcm(beyin, metin)
            pcm = array.array("h", ses_efekt.uygula(np.frombuffer(pcm.tobytes(), dtype=np.int16), cfg.get("efekt")).tobytes())
            uretim = "elevenlabs"
        except (urllib.error.URLError, OSError, KeyError):
            # gercek ses uretilemediyse sahte ugultu calma: sadece altyazi gosterilsin
            return {"dosya": None, "sure_sn": 0, "uretim": "hata"}
    dosya = klasor / f"{int(istek['no'])}.pcm"
    dosya.write_bytes(pcm.tobytes())
    return {"dosya": str(dosya), "sure_sn": round(len(pcm) / HEDEF_HZ, 2), "uretim": uretim}
