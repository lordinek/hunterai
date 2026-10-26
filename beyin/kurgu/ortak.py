"""Kurgu scriptlerinin ortak yardimcilari. Hepsi bir oyun log klasorunu girdi alir."""
import argparse
import csv
import json
import pathlib
import sys

KOK = pathlib.Path(__file__).resolve().parent.parent.parent
SPOILER_ISARETI = KOK / "veri" / "spoiler_acik"


def spoiler_kontrol(izin: bool) -> None:
    """Kurgu cikti dosyalari tuzaklari gosterir: spoiler kilidi acik degilse calismaz."""
    if izin or SPOILER_ISARETI.exists():
        return
    sys.exit("Spoiler kilidi kapali. Oyun bittikten sonra '/yz spoiler ac' komutunu calistir "
             "(ya da --spoiler-ac ver).")


def oku(klasor: pathlib.Path, ad: str) -> list[dict]:
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


def json_oku(klasor: pathlib.Path, ad: str, varsayilan=None):
    dosya = klasor / ad
    if not dosya.exists():
        return varsayilan
    return json.loads(dosya.read_text(encoding="utf-8"))


def isaret_t(klasor: pathlib.Path) -> float:
    """Senkron isareti (/yz isaret) anindaki oyun zamani; yoksa 0."""
    isaretler = oku(klasor, "isaretler.jsonl")
    return float(isaretler[0]["t"]) if isaretler else 0.0


def srt_zaman(sn: float) -> str:
    sn = max(0.0, sn)
    ms = int(round(sn * 1000))
    s, ms = divmod(ms, 1000)
    d, s = divmod(s, 60)
    sa, d = divmod(d, 60)
    return f"{sa:02d}:{d:02d}:{s:02d},{ms:03d}"


def srt_yaz(yol: pathlib.Path, ogeler: list[tuple[float, float, str]], kaydirma: float = 0.0) -> None:
    """ogeler: (baslangic_t, sure_sn, metin). kaydirma: kurguda isarete gore hizalamak icin cikarilir."""
    satirlar = []
    for i, (t, sure, metin) in enumerate(sorted(ogeler), 1):
        satirlar.append(f"{i}\n{srt_zaman(t - kaydirma)} --> {srt_zaman(t - kaydirma + sure)}\n{metin}\n")
    yol.write_text("\n".join(satirlar), encoding="utf-8")


def csv_yaz(yol: pathlib.Path, alanlar: list[str], satirlar: list[dict]) -> None:
    with open(yol, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=alanlar, extrasaction="ignore")
        w.writeheader()
        w.writerows(satirlar)


def arguman_oku(aciklama: str, ek=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=aciklama)
    p.add_argument("klasor", type=pathlib.Path, help="veri/oyunlar/<tarih> klasoru")
    p.add_argument("--cikti", type=pathlib.Path, default=None, help="cikti klasoru (varsayilan: <klasor>/kurgu)")
    p.add_argument("--spoiler-ac", action="store_true", help="spoiler kilidini bu calistirma icin yoksay")
    if ek:
        ek(p)
    a = p.parse_args()
    spoiler_kontrol(a.spoiler_ac)
    a.cikti = a.cikti or (a.klasor / "kurgu")
    a.cikti.mkdir(parents=True, exist_ok=True)
    return a


def tuzaklar(klasor: pathlib.Path) -> dict[str, dict]:
    """tuzak_id -> {kurulum kaydi, durum gecmisi}."""
    sonuc: dict[str, dict] = {}
    for k in oku(klasor, "tuzaklar.jsonl"):
        tid = k.get("tuzak_id")
        if not tid:
            continue
        kayit = sonuc.setdefault(tid, {"id": tid, "olaylar": []})
        if k.get("tip") == "kurulum":
            kayit.update({"t": k["t"], "tarif": k.get("tarif"), "dunya": k.get("dunya"),
                          "x": k.get("x"), "y": k.get("y"), "z": k.get("z"),
                          "tahmin": k.get("tahmin"), "dusunce": k.get("dusunce")})
        else:
            kayit["olaylar"].append({"t": k["t"], "tip": k.get("tip")})
    return {i: v for i, v in sonuc.items() if "t" in v}
