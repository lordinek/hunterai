package yz.tuzak;

import org.bukkit.plugin.java.JavaPlugin;
import yz.tuzak.beyin.BeyinIstemci;
import yz.tuzak.beyin.DusunmeDongusu;
import yz.tuzak.can.OlumAkisi;
import yz.tuzak.ses.Konusma;
import yz.tuzak.ses.SesKopru;
import yz.tuzak.varlik.Varlik;
import yz.tuzak.olay.OyunDinleyici;
import yz.tuzak.tuzak.ModulKaydi;
import yz.tuzak.tuzak.TuzakIzleyici;
import yz.tuzak.tuzak.TuzakTesti;
import yz.tuzak.tuzak.Yerlestirici;

public final class YzPlugin extends JavaPlugin {

    private Oyun oyun;
    private BeyinIstemci beyin;
    private ModulKaydi moduller;
    private Yerlestirici yerlestirici;
    private TuzakTesti tuzakTesti;
    private OlumAkisi olumAkisi;
    private Varlik varlik;
    private Konusma konusma;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        varlik = new Varlik(this);
        varlik.artiklariTemizle();
        oyun = new Oyun(this);
        Kurtulus kurtulus = new Kurtulus(this, oyun);
        oyun.kurtulusAyarla(kurtulus);
        getServer().getPluginManager().registerEvents(kurtulus, this);
        Savas savas = new Savas(this, oyun);
        oyun.savasAyarla(savas);
        getServer().getPluginManager().registerEvents(savas, this);
        konusma = new Konusma(this, oyun);
        sesKopruKur();
        olumAkisi = new OlumAkisi(this, oyun);
        getServer().getPluginManager().registerEvents(olumAkisi, this);
        beyin = new BeyinIstemci(this);
        TuzakIzleyici izleyici = new TuzakIzleyici(this, oyun);
        oyun.izleyiciAyarla(izleyici);
        getServer().getPluginManager().registerEvents(izleyici, this);
        moduller = new ModulKaydi(getConfig());
        yerlestirici = new Yerlestirici(getConfig(), moduller);
        oyun.donguAyarla(new DusunmeDongusu(this, oyun, beyin, moduller, yerlestirici));
        tuzakTesti = new TuzakTesti(this, moduller, yerlestirici);
        YzKomut komut = new YzKomut(this, oyun, beyin, moduller, yerlestirici, tuzakTesti);
        getCommand("yz").setExecutor(komut);
        getCommand("yz").setTabCompleter(komut);
        getServer().getPluginManager().registerEvents(new OyunDinleyici(this, oyun), this);
    }

    /** Simple Voice Chat yuklu degilse ses atlanir, altyazi calismaya devam eder. */
    private void sesKopruKur() {
        if (getServer().getPluginManager().getPlugin("voicechat") == null) {
            getLogger().warning("Simple Voice Chat yok: konusmalar sadece altyazi olarak gosterilecek.");
            return;
        }
        try {
            var servis = getServer().getServicesManager().load(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);
            if (servis == null) return;
            SesKopru k = new SesKopru();
            servis.registerPlugin(k);
            konusma.koprusuAyarla(k);
        } catch (LinkageError | RuntimeException e) {
            getLogger().warning("Simple Voice Chat baglantisi kurulamadi: " + e.getClass().getSimpleName());
        }
    }

    public Konusma konusma() {
        return konusma;
    }

    public OlumAkisi olumAkisi() {
        return olumAkisi;
    }

    public Varlik varlik() {
        return varlik;
    }

    public BeyinIstemci beyin() {
        return beyin;
    }

    @Override
    public void onDisable() {
        oyun.durdur();
        varlik.durdur();
    }
}
