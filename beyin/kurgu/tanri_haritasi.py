"""5. Tanri haritasi: yukaridan bakis PNG ve kisa MP4. Rota cizgi, tuzaklar nokta (tipine gore renk),
tahminler ok (sonuca gore renk), olumler isaret."""
import bisect
import shutil

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib import animation  # noqa: E402

from kurgu import ortak  # noqa: E402

RENKLER = ["tab:red", "tab:blue", "tab:orange", "tab:purple", "tab:brown", "tab:pink", "tab:olive", "tab:cyan"]
SONUC_RENK = {"tuttu": "tab:green", "tutmadi": "tab:red", "belirsiz": "tab:gray", None: "tab:gray"}


def _konum_fonksiyonu(noktalar):
    zamanlar = [n["t"] for n in noktalar]

    def konum(t):
        i = max(0, min(len(noktalar) - 1, bisect.bisect_left(zamanlar, t)))
        return noktalar[i]["x"], noktalar[i]["z"]

    return konum


def _tahminler(klasor):
    sonuc = {}
    for k in ortak.oku(klasor, "tahminler.jsonl"):
        t = k.get("tahmin", {})
        sonuc[t.get("id")] = t
    return list(sonuc.values())


def calistir(klasor, cikti, mp4=True):
    konumlar = ortak.oku(klasor, "konum.jsonl")
    if not konumlar:
        return []
    tuzaklar = ortak.tuzaklar(klasor)
    tahminler = _tahminler(klasor)
    olumler = [o for o in ortak.oku(klasor, "olaylar.jsonl") if o.get("tip") == "olum"]
    tipler = {}
    for t in tuzaklar.values():
        tipler.setdefault((t["tarif"] or "?").split("+")[0], RENKLER[len(tipler) % len(RENKLER)])

    uretilen = []
    dunyalar = sorted({k["dunya"] for k in konumlar})
    for dunya in dunyalar:
        nokta = [k for k in konumlar if k["dunya"] == dunya]
        fig, ax = plt.subplots(figsize=(9, 9))
        ax.set_aspect("equal")
        ax.plot([n["x"] for n in nokta], [n["z"] for n in nokta], color="tab:blue", lw=1.2, label="rota")
        konum = _konum_fonksiyonu(nokta)
        for tahmin in tahminler:
            a, b = konum(tahmin["t"]), konum(min(tahmin["bitis_t"], nokta[-1]["t"]))
            ax.annotate("", xy=b, xytext=a, arrowprops={"arrowstyle": "->", "color": SONUC_RENK.get(tahmin.get("sonuc"), "tab:gray"), "lw": 1.5})
        for t in tuzaklar.values():
            if t["dunya"] != dunya:
                continue
            renk = tipler[(t["tarif"] or "?").split("+")[0]]
            ax.scatter([t["x"]], [t["z"]], color=renk, s=60, zorder=5)
        for o in olumler:
            d = o.get("detay", {})
            if d.get("dunya") == dunya:
                ax.scatter([d["x"]], [d["z"]], color="black", marker="X", s=140, zorder=6)
        ax.invert_yaxis()  # z kuzeyden guneye artar: harita yukari = kuzey
        ax.set_title(f"{dunya} (yukaridan)")
        ax.grid(alpha=0.3)
        yol = cikti / f"tanri_haritasi_{dunya}.png"
        fig.savefig(yol, dpi=130, bbox_inches="tight")
        plt.close(fig)
        uretilen.append(yol)

    if mp4 and shutil.which("ffmpeg"):
        ana = max(dunyalar, key=lambda d: sum(1 for k in konumlar if k["dunya"] == d))
        nokta = [k for k in konumlar if k["dunya"] == ana]
        fig, ax = plt.subplots(figsize=(7, 7))
        ax.set_aspect("equal")
        ax.set_xlim(min(n["x"] for n in nokta) - 10, max(n["x"] for n in nokta) + 10)
        ax.set_ylim(max(n["z"] for n in nokta) + 10, min(n["z"] for n in nokta) - 10)
        cizgi, = ax.plot([], [], color="tab:blue", lw=1.2)
        imlec = ax.scatter([], [], color="tab:blue", s=40)
        adim = max(1, len(nokta) // 300)

        def kare(i):
            parca = nokta[: i * adim + 1]
            cizgi.set_data([n["x"] for n in parca], [n["z"] for n in parca])
            imlec.set_offsets([[parca[-1]["x"], parca[-1]["z"]]])
            return cizgi, imlec

        yol = cikti / "tanri_haritasi.mp4"
        anim = animation.FuncAnimation(fig, kare, frames=len(nokta) // adim + 1, blit=False)
        anim.save(yol, writer=animation.FFMpegWriter(fps=30))
        plt.close(fig)
        uretilen.append(yol)
    return uretilen


if __name__ == "__main__":
    a = ortak.arguman_oku(__doc__)
    print([str(y) for y in calistir(a.klasor, a.cikti)])
