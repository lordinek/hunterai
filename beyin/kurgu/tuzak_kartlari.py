"""1. Tuzak kartlari: her tuzak icin zaman, ad, dusunce. CSV + SRT."""
from kurgu import ortak


def calistir(klasor, cikti, kaydirma=0.0):
    satirlar, srt = [], []
    for tid, t in sorted(ortak.tuzaklar(klasor).items(), key=lambda x: x[1]["t"]):
        durumlar = [o["tip"] for o in t["olaylar"]]
        durum = "tetiklendi" if "tuzak_tetiklendi" in durumlar else "fark_edildi" if "tuzak_fark_edildi" in durumlar else "kurulu"
        satirlar.append({"t": t["t"], "tuzak_id": tid, "ad": t["tarif"], "durum": durum, "dusunce": t.get("dusunce", "")})
        srt.append((t["t"], 6.0, f"{t['tarif']}\n{t.get('dusunce', '')}".strip()))
    ortak.csv_yaz(cikti / "tuzak_kartlari.csv", ["t", "tuzak_id", "ad", "durum", "dusunce"], satirlar)
    ortak.srt_yaz(cikti / "tuzak_kartlari.srt", srt, kaydirma)
    return len(satirlar)


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(calistir(a.klasor, a.cikti, ortak.isaret_t(a.klasor)), "tuzak karti")
