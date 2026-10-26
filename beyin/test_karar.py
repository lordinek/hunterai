"""Beyin servisi birim testleri (LLM gerektirmez). Calistir: python test_karar.py"""
import json
import pathlib
import tempfile

import karar
import profil


def hata_bekle(f, parca):
    try:
        f()
    except karar.SemaHatasi as e:
        assert parca in str(e), str(e)
        return
    raise AssertionError("SemaHatasi bekleniyordu")


def test_dogrula():
    ok = karar.dogrula({"hamle": {"tur": "bekle"}})
    assert ok["hamle"]["tur"] == "bekle" and ok["konusma"]["konus"] is False and ok["tahmin"] is None
    ok = karar.dogrula({
        "hamle": {"tur": "tuzak", "aday": "C", "tarif": {"tetikleyici": "a", "silah": "b", "fizik": ""}},
        "tahmin": {"ne": "x", "sure_dk": 2, "guven": 0.7},
        "konusma": {"konus": True, "metin": ""},
    })
    assert ok["hamle"]["tarif"] == {"tetikleyici": "a", "silah": "b", "fizik": None}
    assert ok["hamle"]["yem"] == "yok" and ok["konusma"]["konus"] is False  # metin bos -> konusma yok
    hata_bekle(lambda: karar.dogrula({"hamle": {"tur": "tuzak", "aday": "C", "tarif": {"tetikleyici": "a"}}}), "tarif")
    hata_bekle(lambda: karar.dogrula({"hamle": {"tur": "oldur"}}), "hamle.tur")
    hata_bekle(lambda: karar.dogrula({"hamle": {"tur": "tuzak", "tarif": {"fizik": "f"}}}), "aday")
    hata_bekle(lambda: karar.dogrula([]), "nesne")


def test_json_cikar():
    assert karar.json_cikar('bla ```json\n{"a": 1}\n``` bla') == {"a": 1}
    hata_bekle(lambda: karar.json_cikar("json yok"), "bulunamadi")


def test_profil_birlestirme():
    with tempfile.TemporaryDirectory() as d:
        k = pathlib.Path(d)
        profil.guncelle(k, ["aliskanlik: cevher gorunce rotasini degistiriyor",
                            "aliskanlik: cevher gorunce rotasini degistirir",
                            "zaaf: elmas cevherine atliyor"], "o1", 10.0)
        p = profil.yukle(k)
        assert len(p["aliskanliklar"]) == 1 and p["aliskanliklar"][0]["dogrulanma"] == 2
        assert len(p["zaaflar"]) == 1
        profil.olay_isle(k, {"tip": "fark_edildi", "moduller": ["m1"], "nedenler": ["yavasladi"], "oyun_id": "o1", "t": 5})
        assert len(profil.yukle(k)["fark_ettigi_tuzak_tipleri"]) == 1


def test_sahte_karar():
    b = karar.Beyin()
    b.cfg["beyin"]["model_duzenli"] = "<config>"
    durum = {
        "oyun_id": "o1",
        "oyun": {"t": 100, "tuzak_hakki": 1},
        "adaylar": [{"etiket": "A"}, {"etiket": "B"}],
        "katalog": {"tarifler": [{"tetikleyici": "t", "silah": "s", "fizik": None}]},
    }
    gorulen = set()
    for _ in range(40):
        k = karar.dogrula(b.karar_ver(durum))
        gorulen.add(k["hamle"]["tur"])
        assert k["model"] == "sahte"
    assert gorulen == {"tuzak", "bekle"}


def test_olum_raporu():
    import olum
    with tempfile.TemporaryDirectory() as d:
        k = pathlib.Path(d) / "oyun1"
        k.mkdir()
        satirlar = {
            "kararlar.jsonl": [
                {"log_id": "karar-1", "t": 10.0,
                 "cikti": {"gozlemler": ["yere bakiyor"], "tahmin": {"ne": "magaraya girecek"}, "hamle": {"tur": "tuzak"}},
                 "uygulama": "kuruldu:x"},
                {"log_id": "karar-9", "t": 500.0, "cikti": {"gozlemler": ["baska hayat"]}},
            ],
            "tuzaklar.jsonl": [{"log_id": "tuzak-2", "t": 11.0, "tip": "kurulum", "tarif": "a+b"}],
            "olaylar.jsonl": [
                {"log_id": "olay-5", "t": 40.0, "tip": "olum", "detay": {"sebep": "PROJECTILE"}},
                {"log_id": "olay-4", "t": 39.0, "tip": "hasar", "detay": {}},
            ],
        }
        for ad, liste in satirlar.items():
            (k / ad).write_text("\n".join(json.dumps(x) for x in liste), encoding="utf-8")
        b = karar.Beyin()
        b.cfg["beyin"]["model_duzenli"] = "<config>"
        b.veri = pathlib.Path(d)
        r = olum.rapor_uret(b, {"oyun_klasoru": str(k), "olum_no": 1, "t_bas": 0, "t_olum": 40.0, "olum_log_id": "olay-5"})
        idler = {m["log_id"] for m in r["zincir"]}
        assert idler <= {"karar-1", "tuzak-2", "olay-5"}, idler  # baska hayatin kaydi ve hasar girmemeli
        assert r["zincir"][-1]["tur"] == "olum" and (k / "olum_raporu_1.json").exists()
        # uydurma log_id'li LLM maddesi elenir
        kayitlar = olum.kayitlari_topla(k, 0, 40.0)
        temiz = olum.zinciri_dogrula([{"t": 1, "tur": "gozlem", "metin": "x", "log_id": "yok-99"},
                                      {"t": 1, "tur": "gozlem", "metin": "y", "log_id": "karar-1"}], kayitlar)
        assert [m["log_id"] for m in temiz] == ["karar-1"] and temiz[0]["t"] == 10.0


if __name__ == "__main__":
    for ad, f in list(globals().items()):
        if ad.startswith("test_"):
            f()
            print("ok", ad)
