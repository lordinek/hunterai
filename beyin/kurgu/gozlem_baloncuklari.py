"""3. Gozlem baloncuklari: kararlardaki 'gozlemler' alani, zaman damgali (SRT)."""
from kurgu import ortak


def calistir(klasor, cikti, kaydirma=0.0, sure=5.0):
    ogeler = []
    for k in ortak.oku(klasor, "kararlar.jsonl"):
        for g in (k.get("cikti") or {}).get("gozlemler", []):
            ogeler.append((k["t"], sure, g))
    ortak.srt_yaz(cikti / "gozlem_baloncuklari.srt", ogeler, kaydirma)
    return len(ogeler)


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print(calistir(a.klasor, a.cikti, ortak.isaret_t(a.klasor)), "gozlem baloncugu")
