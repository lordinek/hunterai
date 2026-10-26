"""7. Dorduncu duvar replikleri: yapay zekanin agzindan izleyiciye, logdaki olgulara bagli, zaman damgali replikler.
Logda olmayan hicbir sey uydurulmaz: her replik bir log_id tasir ve dogrulanir."""
import json
import pathlib

import olum
from karar import Beyin, json_cikar
from kurgu import ortak

PROMPT = pathlib.Path(__file__).resolve().parent.parent / "prompts" / "dorduncu_duvar.md"


def _deterministik(kayitlar):
    """LLM'siz: yapay zekanin kendi kayitli dusuncelerini replik olarak siralar."""
    sonuc = []
    for r in kayitlar:
        o = r["ozet"]
        if r["tur"] == "karar" and o.get("dusunce"):
            sonuc.append({"replik": o["dusunce"], "log_id": r["log_id"]})
        elif r["tur"] == "tuzak:kurulum" and o.get("dusunce"):
            sonuc.append({"replik": o["dusunce"], "log_id": r["log_id"]})
    return sonuc


def _dogrula(replikler, kayit_t):
    temiz = []
    for r in replikler if isinstance(replikler, list) else []:
        if isinstance(r, dict) and r.get("log_id") in kayit_t and str(r.get("replik", "")).strip():
            temiz.append({"t": kayit_t[r["log_id"]], "replik": str(r["replik"]).strip(), "log_id": r["log_id"]})
    return temiz


def calistir(klasor, cikti, kaydirma=0.0, beyin=None):
    kayitlar = olum.kayitlari_topla(klasor, 0.0, 1e12)
    kayit_t = {r["log_id"]: r["t"] for r in kayitlar}
    beyin = beyin or Beyin()
    replikler = []
    uretim = "sahte"
    if not beyin.sahte_mi() and kayitlar:
        try:
            metin = beyin._llm(str(beyin.cfg["beyin"]["model_agir"]), PROMPT.read_text(encoding="utf-8"),
                               [{"role": "user", "content": json.dumps({"kayitlar": kayitlar}, ensure_ascii=False)}], 3000)
            replikler = _dogrula(json_cikar(metin).get("replikler"), kayit_t)
            uretim = "llm"
        except Exception:
            replikler = []
    if not replikler:
        replikler = _dogrula(_deterministik(kayitlar), kayit_t)
        uretim = "sahte" if beyin.sahte_mi() else "yedek"
    replikler.sort(key=lambda r: r["t"])
    ortak.csv_yaz(cikti / "dorduncu_duvar.csv", ["t", "replik", "log_id"], replikler)
    ortak.srt_yaz(cikti / "dorduncu_duvar.srt", [(r["t"], 6.0, r["replik"]) for r in replikler], kaydirma)
    return {"adet": len(replikler), "uretim": uretim}


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(calistir(a.klasor, a.cikti, ortak.isaret_t(a.klasor)))
