"""8. Karne: kurulan / tetiklenen / fark edilen tuzak, olumler, tahmin sayisi ve isabet orani, en uzun hayat vb."""
import json

from kurgu import ortak


def calistir(klasor, cikti):
    tuzaklar = ortak.tuzaklar(klasor)
    tetiklenen = {i for i, t in tuzaklar.items() if any(o["tip"] == "tuzak_tetiklendi" for o in t["olaylar"])}
    fark = {i for i, t in tuzaklar.items() if any(o["tip"] == "tuzak_fark_edildi" for o in t["olaylar"])}
    olaylar = ortak.oku(klasor, "olaylar.jsonl")
    kararlar = ortak.oku(klasor, "kararlar.jsonl")

    olum_t = [o["t"] for o in olaylar if o.get("tip") == "olum"]
    bitis = next((o["t"] for o in reversed(olaylar) if o.get("tip") in ("oyun_bitti", "oyun_bitiyor")), None)
    if bitis is None:
        konumlar = ortak.oku(klasor, "konum.jsonl")
        bitis = konumlar[-1]["t"] if konumlar else 0.0
    sinirlar = [0.0]
    for o in olaylar:
        if o.get("tip") == "yeniden_dogus":
            sinirlar.append(o["t"])
    hayatlar = []
    for bas in sinirlar:
        son = next((t for t in olum_t if t >= bas), bitis)
        hayatlar.append(round(son - bas, 1))

    tahmin_sonuc = {}
    for k in ortak.oku(klasor, "tahminler.jsonl"):
        t = k.get("tahmin", {})
        tahmin_sonuc[t.get("id")] = t.get("sonuc")
    tuttu = sum(1 for s in tahmin_sonuc.values() if s == "tuttu")
    tutmadi = sum(1 for s in tahmin_sonuc.values() if s == "tutmadi")
    tahmin_sayisi = sum(1 for k in kararlar if (k.get("cikti") or {}).get("tahmin"))

    karne = {
        "oyun_suresi_sn": round(bitis, 1),
        "kurulan_tuzak": len(tuzaklar),
        "tetiklenen_tuzak": len(tetiklenen),
        "fark_edilen_tuzak": len(fark),
        "olum": len(olum_t),
        "tahmin_sayisi": tahmin_sayisi,
        "degerlendirilen_tahmin": tuttu + tutmadi,
        "tahmin_isabet_orani": round(tuttu / (tuttu + tutmadi), 3) if tuttu + tutmadi else None,
        "en_uzun_hayat_sn": max(hayatlar) if hayatlar else 0,
        "hayat_sureleri_sn": hayatlar,
        "karar_sayisi": len(kararlar),
        "bekle_orani": round(sum(1 for k in kararlar if ((k.get("cikti") or {}).get("hamle") or {}).get("tur") == "bekle")
                             / len(kararlar), 3) if kararlar else None,
        "konusma_sayisi": sum(1 for k in ortak.oku(klasor, "konusmalar.jsonl") if k.get("metin")),
    }
    (cikti / "karne.json").write_text(json.dumps(karne, ensure_ascii=False, indent=2), encoding="utf-8")
    satirlar = [f"{ad}: {deger}" for ad, deger in karne.items()]
    (cikti / "karne.txt").write_text("\n".join(satirlar) + "\n", encoding="utf-8")
    return karne


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(json.dumps(calistir(a.klasor, a.cikti), ensure_ascii=False, indent=2))
