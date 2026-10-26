"""2. Mesafe sayaci: tetiklenen ya da yakindan gecilen her tuzak icin Ali'nin mesafesinin zamana gore dokumu (CSV)."""
import math

from kurgu import ortak


def calistir(klasor, cikti, yakin=12.0, pencere=15.0):
    konumlar = ortak.oku(klasor, "konum.jsonl")
    toplam = 0
    for tid, t in ortak.tuzaklar(klasor).items():
        seri = []
        for k in konumlar:
            if k.get("dunya") != t["dunya"]:
                continue
            d = math.dist((k["x"], k["y"], k["z"]), (t["x"], t["y"], t["z"]))
            seri.append({"t": k["t"], "mesafe": round(d, 2)})
        tetik = [o["t"] for o in t["olaylar"] if o["tip"] == "tuzak_tetiklendi"]
        yakin_gecis = [s for s in seri if s["mesafe"] <= yakin and s["t"] >= t["t"]]
        if not tetik and not yakin_gecis:
            continue
        merkez = tetik[0] if tetik else yakin_gecis[0]["t"]
        secili = [s for s in seri if merkez - pencere <= s["t"] <= merkez + pencere]
        ortak.csv_yaz(cikti / f"mesafe_{tid}.csv", ["t", "mesafe"], secili)
        toplam += 1
    return toplam


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(calistir(a.klasor, a.cikti), "tuzak icin mesafe dokumu")
