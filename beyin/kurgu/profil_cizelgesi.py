"""4. Profil karti zaman cizelgesi: profilin oyun boyunca nasil doldugu."""
import json

from kurgu import ortak

KATEGORILER = ["aliskanliklar", "zaaflar", "kacinma_yontemleri", "olumler", "fark_ettigi_tuzak_tipleri"]


def calistir(klasor, cikti):
    bas = ortak.json_oku(klasor, "profil_baslangic.json", {}) or {}
    son = ortak.json_oku(klasor, "profil_bitis.json", {}) or {}
    oyun_id = klasor.name
    satirlar = []
    for kat in KATEGORILER:
        for m in son.get(kat, []):
            if m.get("oyun") == oyun_id:
                satirlar.append({"t": m.get("oyun_t", 0), "kategori": kat, "madde": m["madde"], "dogrulanma": m.get("dogrulanma", 1)})
    satirlar.sort(key=lambda s: s["t"])
    ozet = {
        "baslangic_madde_sayisi": sum(len(bas.get(k, [])) for k in KATEGORILER),
        "bitis_madde_sayisi": sum(len(son.get(k, [])) for k in KATEGORILER),
        "bu_oyunda_eklenen": len(satirlar),
        "baslangic_kategori": {k: len(bas.get(k, [])) for k in KATEGORILER},
        "bitis_kategori": {k: len(son.get(k, [])) for k in KATEGORILER},
    }
    ortak.csv_yaz(cikti / "profil_cizelgesi.csv", ["t", "kategori", "madde", "dogrulanma"], satirlar)
    (cikti / "profil_ozeti.json").write_text(json.dumps(ozet, ensure_ascii=False, indent=2), encoding="utf-8")
    return ozet


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(calistir(a.klasor, a.cikti))
