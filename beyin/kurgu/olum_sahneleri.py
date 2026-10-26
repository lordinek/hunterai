"""6. Olum sahneleri: her olum raporundaki maddeleri replay'de gidilecek zamanlarla eslestiren sahne listesi.
replay_t, /yz isaret anina gore goreli zamandir (kayda baslarken calistirilan senkron isareti)."""
import json

from kurgu import ortak


def calistir(klasor, cikti):
    kayma = ortak.isaret_t(klasor)
    satirlar = []
    for dosya in sorted(klasor.glob("olum_raporu_*.json")):
        rapor = json.loads(dosya.read_text(encoding="utf-8"))
        for m in rapor["zincir"]:
            satirlar.append({
                "olum_no": rapor["olum_no"],
                "t": m["t"],
                "replay_t": round(m["t"] - kayma, 1),
                "tur": m["tur"],
                "metin": m["metin"],
                "log_id": m["log_id"],
            })
    ortak.csv_yaz(cikti / "olum_sahneleri.csv", ["olum_no", "t", "replay_t", "tur", "metin", "log_id"], satirlar)
    (cikti / "olum_sahneleri.json").write_text(json.dumps(satirlar, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(satirlar)


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(calistir(a.klasor, a.cikti), "sahne maddesi")
