"""Kurgu scriptleri testi: sahte bir oyun logu uretip hepsini calistirir. Calistir: python test_kurgu.py"""
import json
import pathlib
import tempfile

import olum as olum_modulu
from karar import Beyin
from kurgu import hepsi, ortak


def yaz(klasor, ad, liste):
    (klasor / ad).write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in liste), encoding="utf-8")


def sahte_oyun(kok):
    k = kok / "2026-10-22_20-00-00"
    k.mkdir()
    yaz(k, "isaretler.jsonl", [{"log_id": "isaret-1", "t": 2.0}])
    yaz(k, "konum.jsonl", [{"log_id": f"konum-{i}", "t": float(i), "dunya": "world", "x": i * 2.0, "y": 64.0, "z": 10.0 + i, "can": 20}
                           for i in range(1, 61)])
    yaz(k, "olaylar.jsonl", [
        {"log_id": "olay-1", "t": 0.5, "tip": "oyun_basladi", "detay": {}},
        {"log_id": "olay-2", "t": 40.0, "tip": "olum", "detay": {"sebep": "PROJECTILE", "dunya": "world", "x": 80, "y": 64, "z": 50}},
        {"log_id": "olay-3", "t": 45.0, "tip": "yeniden_dogus", "detay": {}},
        {"log_id": "olay-4", "t": 60.0, "tip": "oyun_bitti", "detay": {}},
    ])
    yaz(k, "kararlar.jsonl", [
        {"log_id": "karar-1", "t": 10.0, "cikti": {"dusunce": "Sabirliyim.", "gozlemler": ["yere bakiyor"],
                                                  "tahmin": {"ne": "magaraya girecek", "sure_dk": 0.3, "guven": 0.6},
                                                  "hamle": {"tur": "tuzak"}}, "uygulama": "kuruldu:a1"},
        {"log_id": "karar-2", "t": 30.0, "cikti": {"dusunce": "Bekliyorum.", "gozlemler": [], "hamle": {"tur": "bekle"}}},
    ])
    yaz(k, "tuzaklar.jsonl", [
        {"log_id": "tuzak-1", "t": 10.5, "tip": "kurulum", "tuzak_id": "a1", "tarif": "tet1+sil1", "dunya": "world",
         "x": 40, "y": 64, "z": 30, "tahmin": "x", "dusunce": "Sabirliyim."},
        {"log_id": "tuzak-2", "t": 38.0, "tip": "tuzak_tetiklendi", "tuzak_id": "a1", "tarif": "tet1+sil1", "nedenler": []},
    ])
    yaz(k, "tahminler.jsonl", [{"zaman": "x", "tahmin": {"id": "t1", "t": 10.0, "bitis_t": 28.0, "ne": "magaraya girecek", "sonuc": "tuttu"}}])
    yaz(k, "konusmalar.jsonl", [{"log_id": "konusma-1", "t": 1.0, "metin": "Seni izliyorum."}])
    (k / "profil_baslangic.json").write_text(json.dumps({"aliskanliklar": [], "zaaflar": [], "kacinma_yontemleri": [],
                                                         "olumler": [], "fark_ettigi_tuzak_tipleri": []}), encoding="utf-8")
    (k / "profil_bitis.json").write_text(json.dumps({"aliskanliklar": [{"madde": "yere bakiyor", "oyun": k.name, "oyun_t": 10.0, "dogrulanma": 1}],
                                                     "zaaflar": [], "kacinma_yontemleri": [], "olumler": [],
                                                     "fark_ettigi_tuzak_tipleri": []}), encoding="utf-8")
    b = Beyin()
    b.cfg["beyin"]["model_duzenli"] = "<config>"
    b.veri = kok
    olum_modulu.rapor_uret(b, {"oyun_klasoru": str(k), "olum_no": 1, "t_bas": 0.0, "t_olum": 40.0, "olum_log_id": "olay-2"})
    return k


def test_hepsi():
    with tempfile.TemporaryDirectory() as d:
        kok = pathlib.Path(d)
        k = sahte_oyun(kok)
        cikti = k / "kurgu"
        cikti.mkdir()
        sonuc = hepsi.calistir(k, cikti, mp4=False)
        assert sonuc["tuzak_kartlari"] == 1
        assert sonuc["mesafe_sayaci"] == 1
        assert sonuc["gozlem_baloncuklari"] == 1
        assert sonuc["profil"]["bu_oyunda_eklenen"] == 1
        assert "tanri_haritasi_world.png" in sonuc["tanri_haritasi"]
        assert sonuc["olum_sahneleri"] >= 2
        assert sonuc["dorduncu_duvar"]["adet"] >= 1
        karne = sonuc["karne"]
        assert karne["kurulan_tuzak"] == 1 and karne["tetiklenen_tuzak"] == 1 and karne["olum"] == 1
        assert karne["tahmin_isabet_orani"] == 1.0 and karne["en_uzun_hayat_sn"] == 40.0
        srt = (cikti / "gozlem_baloncuklari.srt").read_text(encoding="utf-8")
        assert "00:00:08,000" in srt  # t=10, isaret t=2 -> 8 sn
        assert (cikti / "olum_sahneleri.csv").exists() and (cikti / "tuzak_kartlari.srt").exists()


def test_spoiler_kilidi():
    assert not ortak.SPOILER_ISARETI.exists()
    try:
        ortak.spoiler_kontrol(False)
    except SystemExit as e:
        assert "Spoiler kilidi" in str(e)
    else:
        raise AssertionError("kilit acikken calismamali")
    ortak.spoiler_kontrol(True)  # --spoiler-ac ile gecer


if __name__ == "__main__":
    for ad, f in list(globals().items()):
        if ad.startswith("test_"):
            f()
            print("ok", ad)
