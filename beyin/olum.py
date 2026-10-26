"""Olum raporu: bir hayatin loglarindan, her maddesi bir log kaydina bagli zaman damgali zincir."""
import json
import pathlib

import profil
from karar import SemaHatasi, json_cikar

DOSYALAR = {
    "olaylar.jsonl": "olay",
    "kararlar.jsonl": "karar",
    "tuzaklar.jsonl": "tuzak",
}


def _oku(klasor: pathlib.Path, ad: str) -> list[dict]:
    dosya = klasor / ad
    if not dosya.exists():
        return []
    kayitlar = []
    for satir in dosya.read_text(encoding="utf-8").splitlines():
        try:
            kayitlar.append(json.loads(satir))
        except json.JSONDecodeError:
            continue
    return kayitlar


def kayitlari_topla(klasor: pathlib.Path, t_bas: float, t_olum: float) -> list[dict]:
    """Hayat araligindaki kayitlar: {log_id, t, tur, ozet}. 'hasar' ve 'craft' gibi sik olaylar elenir."""
    sonuc = []
    for ad, tur in DOSYALAR.items():
        for k in _oku(klasor, ad):
            t = k.get("t", 0)
            if not (t_bas <= t <= t_olum + 0.5):
                continue
            if tur == "olay" and k.get("tip") in ("hasar", "craft"):
                continue
            sonuc.append({
                "log_id": k["log_id"],
                "t": t,
                "tur": f"{tur}:{k.get('tip', '')}".rstrip(":"),
                "ozet": _ozet(tur, k),
            })
    sonuc.sort(key=lambda r: r["t"])
    return sonuc


def _ozet(tur: str, k: dict) -> dict:
    if tur == "karar":
        cikti = k.get("cikti") or {}
        return {
            "dusunce": cikti.get("dusunce", ""),
            "gozlemler": cikti.get("gozlemler", []),
            "tahmin": cikti.get("tahmin"),
            "hamle": (cikti.get("hamle") or {}).get("tur"),
            "uygulama": k.get("uygulama"),
        }
    if tur == "tuzak":
        return {k2: v for k2, v in k.items() if k2 in ("tip", "tarif", "cesit", "tahmin", "dusunce", "nedenler")}
    return k.get("detay", {})


def deterministik_zincir(kayitlar: list[dict], olum: dict) -> list[dict]:
    """LLM'siz rapor: sadece logdaki olgulari, sablonla, soguk bir dille siralar."""
    zincir = []
    for r in kayitlar:
        o = r["ozet"]
        if r["tur"] == "karar":
            for g in o.get("gozlemler", []):
                zincir.append({"t": r["t"], "tur": "gozlem", "metin": f"Fark ettim: {g}", "log_id": r["log_id"]})
            if o.get("tahmin"):
                zincir.append({"t": r["t"], "tur": "cikarim", "metin": f"Tahminim: {o['tahmin'].get('ne')}.", "log_id": r["log_id"]})
        elif r["tur"] == "tuzak:kurulum":
            zincir.append({"t": r["t"], "tur": "tuzak", "metin": "Bir tuzak kurdum ve bekledim.", "log_id": r["log_id"]})
        elif r["tur"] == "tuzak:mudahale":
            zincir.append({"t": r["t"], "tur": "tuzak", "metin": "Dogrudan mudahale ettim.", "log_id": r["log_id"]})
        elif r["tur"] in ("tuzak:tuzak_tetiklendi", "olay:tuzak_tetiklendi"):
            zincir.append({"t": r["t"], "tur": "tuzak", "metin": "Tuzak calisti.", "log_id": r["log_id"]})
    zincir = zincir[-11:]
    zincir.append({
        "t": olum["t"],
        "tur": "olum",
        "metin": f"Sonunda oldu: {olum.get('sebep', 'bilinmiyor')}.",
        "log_id": olum["log_id"],
    })
    return zincir


def zinciri_dogrula(zincir, kayitlar: list[dict]) -> list[dict]:
    """Her maddenin log_id'si girdide olmali; t atifta bulunulan kaydin t'siyle uyusmali."""
    kayit_t = {r["log_id"]: r["t"] for r in kayitlar}
    if not isinstance(zincir, list):
        raise SemaHatasi("zincir liste olmali")
    temiz = []
    for m in zincir:
        if not isinstance(m, dict) or m.get("log_id") not in kayit_t:
            continue
        if m.get("tur") not in ("gozlem", "cikarim", "tuzak", "olum"):
            continue
        temiz.append({"t": kayit_t[m["log_id"]], "tur": m["tur"], "metin": str(m.get("metin", "")), "log_id": m["log_id"]})
    return temiz[:12]


def rapor_uret(beyin, istek: dict) -> dict:
    klasor = pathlib.Path(istek["oyun_klasoru"])
    t_bas, t_olum = float(istek["t_bas"]), float(istek["t_olum"])
    no = int(istek["olum_no"])
    kayitlar = kayitlari_topla(klasor, t_bas, t_olum)
    olum = _olum_ozeti(kayitlar, istek)

    uretim = "sahte"
    zincir = None
    if not beyin.sahte_mi():
        try:
            zincir = _llm_zincir(beyin, kayitlar, olum)
            uretim = "llm"
        except Exception:
            zincir = None
    if not zincir or not any(m["tur"] == "olum" for m in zincir):
        zincir = deterministik_zincir(kayitlar, olum)
        uretim = "sahte" if beyin.sahte_mi() else "yedek"

    rapor = {
        "olum_no": no,
        "t_bas": t_bas,
        "t_olum": t_olum,
        "uretim": uretim,
        "model": "sahte" if uretim == "sahte" else str(beyin.cfg["beyin"]["model_agir"]),
        "zincir": zincir,
    }
    (klasor / f"olum_raporu_{no}.json").write_text(json.dumps(rapor, ensure_ascii=False, indent=2), encoding="utf-8")
    ozet = next((m["metin"] for m in zincir if m["tur"] == "olum"), "olum")
    profil.olay_isle(beyin.veri, {"tip": "olum", "ozet": ozet, "oyun_id": klasor.name, "t": t_olum})
    return rapor


def _olum_ozeti(kayitlar: list[dict], istek: dict) -> dict:
    olum_kaydi = next((r for r in reversed(kayitlar) if r["tur"] == "olay:olum"), None)
    return {
        "log_id": istek.get("olum_log_id") or (olum_kaydi["log_id"] if olum_kaydi else ""),
        "t": float(istek["t_olum"]),
        "sebep": (olum_kaydi or {}).get("ozet", {}).get("sebep") or istek.get("sebep", "bilinmiyor"),
    }


def _llm_zincir(beyin, kayitlar: list[dict], olum: dict) -> list[dict]:
    sistem = (pathlib.Path(__file__).parent / "prompts" / "olum_raporu.md").read_text(encoding="utf-8")
    icerik = json.dumps({"kayitlar": kayitlar, "olum": olum}, ensure_ascii=False)
    metin = beyin._llm(str(beyin.cfg["beyin"]["model_agir"]), sistem, [{"role": "user", "content": icerik}], 2500)
    return zinciri_dogrula(json_cikar(metin).get("zincir"), kayitlar)
