"""Duzenli ve olay bazli dusunme: durum raporu -> LLM -> dogrulanmis karar."""
import json
import os
import pathlib
import random
import re
import time
import urllib.request
from datetime import datetime, timezone

import ayar
import profil

PROMPTLAR = pathlib.Path(__file__).parent / "prompts"
BEKLE = {
    "dusunce": "",
    "gozlemler": [],
    "tahmin": None,
    "hamle": {"tur": "bekle", "aday": None, "tarif": {"tetikleyici": None, "silah": None, "fizik": None}, "yem": "yok"},
    "konusma": {"konus": False, "metin": ""},
    "profil_guncelleme": [],
}


class SemaHatasi(ValueError):
    pass


def dogrula(d) -> dict:
    """Karar JSON'unu sema acisindan dogrular ve eksik alanlari tamamlar."""
    if not isinstance(d, dict):
        raise SemaHatasi("cevap bir JSON nesnesi degil")
    hamle = d.get("hamle")
    if not isinstance(hamle, dict) or hamle.get("tur") not in ("tuzak", "bekle", "mudahale"):
        raise SemaHatasi("hamle.tur 'tuzak', 'mudahale' ya da 'bekle' olmali")
    if hamle["tur"] == "tuzak":
        tarif = hamle.get("tarif")
        if not isinstance(tarif, dict):
            raise SemaHatasi("hamle.tarif nesne olmali")
        if not isinstance(hamle.get("aday"), str) or not hamle["aday"]:
            raise SemaHatasi("hamle.aday bir aday etiketi olmali")
        tarif = {k: tarif.get(k) or None for k in ("tetikleyici", "silah", "fizik")}
        birlesik = tarif["tetikleyici"] and tarif["silah"] and not tarif["fizik"]
        tek = tarif["fizik"] and not tarif["tetikleyici"] and not tarif["silah"]
        if not (birlesik or tek):
            raise SemaHatasi("tarif ya tetikleyici+silah ya da tek fizik olmali")
        hamle["tarif"] = tarif
        yem = hamle.get("yem")
        hamle["yem"] = yem if isinstance(yem, str) and yem else "yok"
    elif hamle["tur"] == "mudahale":
        if hamle.get("cesit") not in ("duvar", "mob_sel", "it", "cukur", "yagmur", "lav", "isinla", "karanlik", "kistir"):
            raise SemaHatasi("hamle.cesit gecerli bir mudahale cesidi olmali")
        hamle.setdefault("aday", None)
        hamle.setdefault("tarif", {"tetikleyici": None, "silah": None, "fizik": None})
        hamle.setdefault("yem", "yok")
    else:
        hamle.setdefault("aday", None)
        hamle.setdefault("tarif", {"tetikleyici": None, "silah": None, "fizik": None})
        hamle.setdefault("yem", "yok")
    d["dusunce"] = str(d.get("dusunce") or "")
    g = d.get("gozlemler")
    d["gozlemler"] = [str(x) for x in g] if isinstance(g, list) else []
    t = d.get("tahmin")
    if isinstance(t, dict) and t.get("ne"):
        try:
            d["tahmin"] = {"ne": str(t["ne"]), "sure_dk": float(t.get("sure_dk", 3)), "guven": float(t.get("guven", 0.5))}
        except (TypeError, ValueError):
            raise SemaHatasi("tahmin.sure_dk / tahmin.guven sayi olmali")
    else:
        d["tahmin"] = None
    k = d.get("konusma")
    d["konusma"] = {"konus": bool(k.get("konus")), "metin": str(k.get("metin") or "")} if isinstance(k, dict) else {"konus": False, "metin": ""}
    if d["konusma"]["konus"] and not d["konusma"]["metin"]:
        d["konusma"]["konus"] = False
    p = d.get("profil_guncelleme")
    d["profil_guncelleme"] = [str(x) for x in p] if isinstance(p, list) else []
    return d


def json_cikar(metin: str):
    i, j = metin.find("{"), metin.rfind("}")
    if i < 0 or j <= i:
        raise SemaHatasi("cevapta JSON bulunamadi")
    try:
        return json.loads(metin[i:j + 1])
    except json.JSONDecodeError as e:
        raise SemaHatasi(f"gecersiz JSON: {e.msg}") from e


class Beyin:
    def __init__(self):
        self.cfg = ayar.yukle()
        self.veri = ayar.veri_klasoru(self.cfg)
        self.tahminler: dict[str, list[dict]] = {}
        self._istemci = None
        self._rnd = random.Random()

    # ---- LLM ----

    def model_duzenli(self) -> str:
        return str(self.cfg["beyin"]["model_duzenli"])

    def saglayici(self) -> str:
        return str(self.cfg["beyin"].get("saglayici", "anthropic"))

    def sahte_mi(self) -> bool:
        m = self.model_duzenli()
        anahtar = "GEMINI_API_KEY" if self.saglayici() == "gemini" else "ANTHROPIC_API_KEY"
        return not os.environ.get(anahtar) or m.startswith("<") or not m

    def _gemini(self, model: str, sistem: str, mesajlar: list[dict], max_tokens: int) -> str:
        govde = {
            "systemInstruction": {"parts": [{"text": sistem}]},
            "contents": [{"role": "model" if m["role"] == "assistant" else "user", "parts": [{"text": m["content"]}]}
                         for m in mesajlar],
            "generationConfig": {
                "maxOutputTokens": max_tokens,
                "responseMimeType": "application/json",
                "thinkingConfig": {"thinkingBudget": int(self.cfg["beyin"].get("dusunme_butcesi", 0))},
            },
        }
        istek = urllib.request.Request(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            data=json.dumps(govde).encode("utf-8"), method="POST",
            headers={"Content-Type": "application/json", "x-goog-api-key": os.environ["GEMINI_API_KEY"]})
        with urllib.request.urlopen(istek, timeout=int(self.cfg["beyin"].get("karar_zaman_asimi_sn", 60))) as r:
            cevap = json.loads(r.read().decode("utf-8"))
        return "".join(p.get("text", "") for p in cevap["candidates"][0]["content"].get("parts", []))

    def _llm(self, model: str, sistem: str, mesajlar: list[dict], max_tokens: int = 1500) -> str:
        if self.saglayici() == "gemini":
            return self._gemini(model, sistem, mesajlar, max_tokens)
        if self._istemci is None:
            import anthropic
            self._istemci = anthropic.Anthropic()
        cevap = self._istemci.messages.create(model=model, max_tokens=max_tokens, system=sistem, messages=mesajlar)
        return "".join(b.text for b in cevap.content if getattr(b, "type", "") == "text")

    # ---- karar ----

    def karar_ver(self, durum: dict, hata: str | None = None) -> dict:
        baslangic = time.time()
        oyun_id = durum.get("oyun_id", "")
        t = float(durum.get("oyun", {}).get("t", 0))
        self._tahminleri_degerlendir(durum, oyun_id, t)
        durum = dict(durum)
        durum["onceki_tahminler"] = self.tahminler.get(oyun_id, [])[-6:]
        durum["profil"] = profil.yukle(self.veri)
        durum.pop("oyun_klasoru", None)
        if hata:
            durum["hata"] = hata

        sahte = self.sahte_mi()
        if sahte:
            karar = self._sahte_karar(durum)
        else:
            karar = self._llm_karar(durum)
        if karar.get("tahmin"):
            self._tahmin_kaydet(oyun_id, t, karar["tahmin"])
        if karar.get("profil_guncelleme"):
            profil.guncelle(self.veri, karar["profil_guncelleme"], oyun_id, t)
        karar["model"] = "sahte" if sahte else self.model_duzenli()
        karar["sure_ms"] = int((time.time() - baslangic) * 1000)
        return karar

    def _llm_karar(self, durum: dict) -> dict:
        sistem = (PROMPTLAR / "sistem.md").read_text(encoding="utf-8")
        mesajlar = [{"role": "user", "content": json.dumps(durum, ensure_ascii=False)}]
        son_hata = ""
        for deneme in range(2):
            try:
                metin = self._llm(self.model_duzenli(), sistem, mesajlar)
                return dogrula(json_cikar(metin))
            except SemaHatasi as e:
                son_hata = str(e)
                mesajlar = mesajlar + [
                    {"role": "assistant", "content": metin},
                    {"role": "user", "content": f"Cevabin semaya uymadi: {son_hata}. Sadece duzeltilmis JSON'u dondur."},
                ]
            except Exception as e:  # ag, kota vb.
                son_hata = f"llm: {type(e).__name__}"
                break
        karar = json.loads(json.dumps(BEKLE))
        karar["hata"] = son_hata
        return karar

    def _sahte_karar(self, durum: dict) -> dict:
        """LLM olmadan boru hattini denemek icin: tutarli ama aptal bir karar."""
        karar = json.loads(json.dumps(BEKLE))
        karar["dusunce"] = "Izliyorum."
        oyun = durum.get("oyun", {})
        adaylar = durum.get("adaylar", [])
        tarifler = durum.get("katalog", {}).get("tarifler", [])
        if oyun.get("tuzak_hakki", 0) >= 1 and adaylar and tarifler and self._rnd.random() < 0.5:
            tarif = self._rnd.choice(tarifler)
            karar["hamle"] = {
                "tur": "tuzak",
                "aday": self._rnd.choice(adaylar)["etiket"],
                "tarif": {
                    "tetikleyici": tarif.get("tetikleyici"),
                    "silah": tarif.get("silah"),
                    "fizik": tarif.get("fizik"),
                },
                "yem": "yok",
            }
        karar["tahmin"] = {"ne": "ayni yonde ilerlemeye devam edecek", "sure_dk": 2.0, "guven": 0.5}
        return karar

    # ---- tahminler ----

    def _tahmin_kaydet(self, oyun_id: str, t: float, tahmin: dict) -> None:
        liste = self.tahminler.setdefault(oyun_id, [])
        liste.append({
            "id": f"t{len(liste) + 1}",
            "t": t,
            "bitis_t": t + tahmin["sure_dk"] * 60,
            "ne": tahmin["ne"],
            "guven": tahmin["guven"],
            "sonuc": None,
        })

    def _tahminleri_degerlendir(self, durum: dict, oyun_id: str, t: float) -> None:
        bekleyen = [p for p in self.tahminler.get(oyun_id, []) if p["sonuc"] is None and p["bitis_t"] <= t]
        if not bekleyen:
            return
        klasor = durum.get("oyun_klasoru")
        for p in bekleyen:
            if self.sahte_mi():
                p["sonuc"] = self._rnd.choice(["tuttu", "tutmadi", "belirsiz"])
            else:
                p["sonuc"] = self._tahmin_hakemi(p, durum)
            if klasor:
                satir = {"zaman": datetime.now(timezone.utc).isoformat(), "tahmin": p}
                with open(pathlib.Path(klasor) / "tahminler.jsonl", "a", encoding="utf-8") as f:
                    f.write(json.dumps(satir, ensure_ascii=False) + "\n")

    def _tahmin_hakemi(self, p: dict, durum: dict) -> str:
        sistem = ("Bir tahminin tutup tutmadigini son olaylara ve Ali'nin rotasina bakarak degerlendir. "
                  "Sadece su JSON'u dondur: {\"sonuc\": \"tuttu\" | \"tutmadi\" | \"belirsiz\"}")
        icerik = json.dumps({"tahmin": p, "son_olaylar": durum.get("son_olaylar", []),
                             "ali": durum.get("ali", {})}, ensure_ascii=False)
        try:
            metin = self._llm(self.model_duzenli(), sistem, [{"role": "user", "content": icerik}], 200)
            sonuc = json_cikar(metin).get("sonuc")
            return sonuc if sonuc in ("tuttu", "tutmadi", "belirsiz") else "belirsiz"
        except Exception:
            return "belirsiz"
