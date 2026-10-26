"""Ali profili: kalici, videolar arasi saklanir (veri/profil.json)."""
import difflib
import json
import threading
from datetime import datetime, timezone

KATEGORILER = ["aliskanliklar", "zaaflar", "kacinma_yontemleri", "olumler", "fark_ettigi_tuzak_tipleri"]
ONEKLER = {
    "aliskanlik": "aliskanliklar",
    "zaaf": "zaaflar",
    "kacinma": "kacinma_yontemleri",
    "olum": "olumler",
    "fark": "fark_ettigi_tuzak_tipleri",
}
BENZERLIK = 0.8
_kilit = threading.Lock()


def _bos() -> dict:
    return {k: [] for k in KATEGORILER}


def yukle(klasor) -> dict:
    dosya = klasor / "profil.json"
    if not dosya.exists():
        return _bos()
    try:
        p = json.loads(dosya.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return _bos()
    for k in KATEGORILER:
        p.setdefault(k, [])
    return p


def kaydet(klasor, profil: dict) -> None:
    (klasor / "profil.json").write_text(json.dumps(profil, ensure_ascii=False, indent=2), encoding="utf-8")


def _kategori(metin: str) -> tuple[str, str]:
    """'zaaf: elmasa kosuyor' -> ('zaaflar', 'elmasa kosuyor'); onek yoksa aliskanliklar."""
    on, ayrac, kalan = metin.partition(":")
    if ayrac:
        anahtar = on.strip().lower()
        for onek, kat in ONEKLER.items():
            if anahtar.startswith(onek):
                return kat, kalan.strip()
    return "aliskanliklar", metin.strip()


def ekle(klasor, kategori: str, madde: str, oyun_id: str, t: float) -> None:
    """Benzer madde varsa dogrulanma sayisini artirir, yoksa yeni madde ekler."""
    if not madde:
        return
    with _kilit:
        p = yukle(klasor)
        liste = p[kategori]
        for m in liste:
            if difflib.SequenceMatcher(None, m["madde"].lower(), madde.lower()).ratio() >= BENZERLIK:
                m["dogrulanma"] += 1
                m["son_gorulme"] = datetime.now(timezone.utc).isoformat()
                break
        else:
            liste.append({
                "madde": madde,
                "ilk_gorulme": datetime.now(timezone.utc).isoformat(),
                "oyun": oyun_id,
                "oyun_t": t,
                "dogrulanma": 1,
            })
        kaydet(klasor, p)


def guncelle(klasor, maddeler: list[str], oyun_id: str, t: float) -> None:
    for metin in maddeler:
        kategori, madde = _kategori(str(metin))
        ekle(klasor, kategori, madde, oyun_id, t)


def olay_isle(klasor, olay: dict) -> None:
    """Plugin'in kesin bildigi olgular (LLM yorumu gerekmez)."""
    tip = olay.get("tip")
    oyun_id, t = olay.get("oyun_id", ""), float(olay.get("t", 0))
    if tip == "fark_edildi":
        for m in olay.get("moduller", []):
            ekle(klasor, "fark_ettigi_tuzak_tipleri", f"{m} fark edildi ({', '.join(olay.get('nedenler', []))})", oyun_id, t)
    elif tip == "kil_payi":
        ekle(klasor, "kacinma_yontemleri", "tetiklenen tuzaktan sag cikti", oyun_id, t)
    elif tip == "yeme_atladi":
        ekle(klasor, "zaaflar", f"{olay.get('yem', 'yem')} yemine atladi", oyun_id, t)
    elif tip == "olum":
        ekle(klasor, "olumler", olay.get("ozet", "olum"), oyun_id, t)
