"""Ortak ayarlar: proje kokundeki config.yml ve .env."""
import pathlib

import yaml
from dotenv import load_dotenv

KOK = pathlib.Path(__file__).resolve().parent.parent
load_dotenv(KOK / ".env", override=True)  # .env, kullanici ortam degiskenlerinin onune gecer


def yukle() -> dict:
    return yaml.safe_load((KOK / "config.yml").read_text(encoding="utf-8"))


def veri_klasoru(cfg: dict) -> pathlib.Path:
    # plugin sunucu klasorunden calisir; goreli yollar oraya gore cozulur
    p = (KOK / "sunucu" / cfg["veri_klasoru"]).resolve()
    p.mkdir(parents=True, exist_ok=True)
    return p
