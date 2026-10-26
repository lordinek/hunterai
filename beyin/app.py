from fastapi import FastAPI

import ayar
import profil
import olum
import ses
from karar import Beyin

app = FastAPI(title="yz-beyin")
beyin = Beyin()


@app.get("/saglik")
def saglik():
    return {"ok": True}


@app.post("/test")
def test(durum: dict):
    return {"ok": True}


@app.post("/karar")
def karar(istek: dict):
    """istek: {"durum": {...}, "hata": "..."|null}"""
    return beyin.karar_ver(istek.get("durum", {}), istek.get("hata"))


@app.post("/profil/olay")
def profil_olay(olay: dict):
    profil.olay_isle(ayar.veri_klasoru(ayar.yukle()), olay)
    return {"ok": True}


@app.post("/olum_raporu")
def olum_raporu(istek: dict):
    """istek: {oyun_klasoru, olum_no, t_bas, t_olum, olum_log_id, sebep}"""
    return olum.rapor_uret(beyin, istek)


@app.post("/ses")
def ses_uret(istek: dict):
    """istek: {oyun_klasoru, no, metin}"""
    return ses.uret(beyin, istek)
