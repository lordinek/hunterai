"""Butun kurgu ciktilarini tek seferde uretir: python -m kurgu.hepsi <log_klasoru>"""
from kurgu import (dorduncu_duvar, gozlem_baloncuklari, karne, mesafe_sayaci, olum_sahneleri, ortak,
                   profil_cizelgesi, tanri_haritasi, tuzak_kartlari)


def calistir(klasor, cikti, mp4=True):
    kayma = ortak.isaret_t(klasor)
    return {
        "tuzak_kartlari": tuzak_kartlari.calistir(klasor, cikti, kayma),
        "mesafe_sayaci": mesafe_sayaci.calistir(klasor, cikti),
        "gozlem_baloncuklari": gozlem_baloncuklari.calistir(klasor, cikti, kayma),
        "profil": profil_cizelgesi.calistir(klasor, cikti),
        "tanri_haritasi": [str(y.name) for y in tanri_haritasi.calistir(klasor, cikti, mp4)],
        "olum_sahneleri": olum_sahneleri.calistir(klasor, cikti),
        "dorduncu_duvar": dorduncu_duvar.calistir(klasor, cikti, kayma),
        "karne": karne.calistir(klasor, cikti),
    }


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    for ad, sonuc in calistir(a.klasor, a.cikti).items():
        print(ad, "->", sonuc if not isinstance(sonuc, dict) else "tamam")
