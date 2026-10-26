# HunterAI

Minecraft'ta bir yapay zeka seni avlıyor. Ejderhaya ulaşmaya çalışırken yapay zeka seni izler, ne yaptığına bakıp oyun içi tuzaklar kurar ve ara sıra sesli konuşur. Üç canın var.

Proje iki parçadan oluşur:

- **`plugin/`**: Paper plugin'i (Java 25). Oyunu yönetir, olayları toplar, tuzakları kurar, figürü gösterir.
- **`beyin/`**: Python (FastAPI) servisi. Plugin'den gelen duruma bakıp LLM'e sorar, hangi tuzağın nereye kurulacağına karar verir, konuşma metnini seslendirir.

Bir YouTube videosu için yapıldı. Kod olduğu gibi paylaşılıyor: kendi sunucunda çalıştırıp kendi videonu çekebilirsin.

## Gereksinimler

| Ne | Sürüm / not |
| --- | --- |
| Minecraft Java | 26.3 |
| Paper sunucusu | 26.3 (plugin `paper-api:26.3.build.214-stable` ile derleniyor) |
| JDK | 25 (hem derleme hem sunucu) |
| Python | 3.11+ |
| Gemini API anahtarı | Varsayılan LLM sağlayıcısı |
| ElevenLabs API anahtarı | İsteğe bağlı. Yoksa gerçek ses yerine sahte uğultu çalar |
| Anthropic API anahtarı | İsteğe bağlı. Gemini yerine Claude kullanmak istersen |
| Simple Voice Chat | İsteğe bağlı (`softdepend`). Sesin oyun içinde duyulması için |
| İşletim sistemi | Betikler PowerShell (Windows). Linux/macOS'ta komutları elle çalıştır |

## API anahtarları nereye girilir

Anahtarlar **yalnızca** proje kökündeki `.env` dosyasına yazılır. Bu dosya `.gitignore` içindedir, GitHub'a gitmez. Anahtarı `config.yml`'a veya koda yazma.

1. Şablonu kopyala:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Doldur:

   ```
   GEMINI_API_KEY=...        # https://aistudio.google.com/apikey
   ELEVENLABS_API_KEY=...    # https://elevenlabs.io (isteğe bağlı)
   ANTHROPIC_API_KEY=...     # https://console.anthropic.com (isteğe bağlı)
   ```

3. Sağlayıcıyı `config.yml` içindeki `beyin` bölümünden seç:

   ```yaml
   saglayici: gemini          # veya anthropic
   model_duzenli: gemini-3.5-flash
   model_agir: gemini-3.5-flash
   ```

   `anthropic` seçersen iki model alanına da Claude model adını yaz.

4. Ses için `config.yml` içindeki `elevenlabs.ses_id` alanına ElevenLabs ses kimliğini yaz. Anahtar boşsa veya `ses_id` `<` ile başlıyorsa gerçek ses üretilmez, sahte uğultu çalar. Oyun yine çalışır.

`.env` değerleri sistemdeki ortam değişkenlerinin **önüne** geçer (`beyin/ayar.py`).

## Kurulum

### 1. Paper sunucusunu hazırla

Proje kökünde `sunucu/` klasörü oluştur (git'e girmez). İçine Paper 26.3 jar'ını `paper.jar` adıyla koy ve `eula.txt` dosyasına `eula=true` yaz. Bununla Minecraft EULA'yı kabul etmiş olursun.

`sunucu/baslat.ps1`:

```powershell
Set-Location $PSScriptRoot
java -Xms2G -Xmx4G -jar paper.jar --nogui
```

`java` komutu JDK 25'i göstermiyorsa tam yolunu yaz.

### 2. Plugin'i derle

```powershell
cd plugin
$env:JAVA_HOME = "<JDK 25 yolu>"
.\gradlew.bat build
New-Item -ItemType Directory -Force ..\sunucu\plugins | Out-Null
Copy-Item build\libs\yz-tuzak-0.1.0.jar ..\sunucu\plugins\
```

### 3. Kaynak paketi

Figür ve sesler kaynak paketiyle gelir. `paket/yz-paket.zip` hazır halde durur, açık hali `paket_kaynak/` içindedir. `baslat-hepsi.ps1` paketi `127.0.0.1:8090` üzerinden sunar. Sunucu ilk açıldıktan sonra `sunucu/server.properties` içine şunları yaz:

```
resource-pack=http\://localhost\:8090/yz-paket.zip
resource-pack-sha1=<paket/yz-paket.sha1 dosyasındaki değer>
```

Başka bir makineden bağlanacaksan `localhost` yerine o makinenin adresini yaz.

### 4. Başlat

```powershell
.\baslat-hepsi.ps1
```

Betik üç şey başlatır: beyin servisi (`127.0.0.1:8765`), kaynak paketi sunucusu (`8090`) ve Paper sunucusu. Beyin ilk açılışta `beyin/.venv` oluşturur ve `requirements.txt` kurar.

Oyundan `localhost` ile gir, sunucu konsoluna `op <adın>` yaz.

## Oyun komutları

| Komut | Ne yapar |
| --- | --- |
| `/yz baslat`, `/yz durdur` | Oyunu başlatır/bitirir (loglar, figür, tanıtım konuşması) |
| `/yz isaret` | Kurgu için senkron işareti (beyaz flaş + log) |
| `/yz durum` | Spoiler'sız durum |
| `/yz test beyin` | Beyin servisine sahte çağrı. Cevap `OK (ms)` |
| `/yz test aday` | Tuzak aday noktalarını partikülle gösterir (tuzak içermez) |
| `/yz test tuzaklar` | Otomatik tuzak testi, sadece `X/Y kombinasyon gecti` yazar |
| `/yz test kur` | İzinli tariflerden birini kurar (oyun aktifken) |
| `/yz varlik <ad>` | Figür animasyon testi |
| `/yz spoiler ac` | Oyun bittikten sonra spoiler kilidini açar |

Loglar `veri/oyunlar/<tarih>/` altına düşer.

## Ayarlar

Hepsi `config.yml` içinde: aşamalara göre tuzak dolum süresi ve aktif tuzak sayısı, can hakkı, tuzak mesafeleri, konuşma sayısı ve aralığı, figürün boyutu ve davranışı.

`config.yml` üç yerde aynı olmalı: kökte, `plugin/src/main/resources/config.yml` ve `sunucu/plugins/YzTuzak/config.yml`. Plugin sunucu klasöründeki kopyayı okur.

## Spoiler kilidi

Kendi videonu çekiyorsan `spoiler_kilidi: true` bırak. Kilit açıkken oyuncuya hiçbir tuzak bilgisi gösterilmez. Prompt dosyalarında tuzak listesi yoktur, katalog çalışma anında `tuzaklar/izinli_tarifler.json` dosyasından okunur. Oyun bitince `/yz spoiler ac` ile kilidi kaldır. Kurgu araçları ancak ondan sonra çalışır.

Not: `plugin/` içindeki tuzak modülleri açık kaynak olduğu için kodu okuyan biri tuzakları görebilir. Oynayacaksan kodu okuma.

## Test

```powershell
beyin\.venv\Scripts\python beyin\test_karar.py
beyin\.venv\Scripts\python beyin\test_kurgu.py
```

Kurgu araçları: `python -m kurgu.hepsi <veri/oyunlar/tarih>` (spoiler kilidi açılmış olmalı). MP4 çıktısı için ffmpeg gerekir.

## Klasörler

```
plugin/         Paper plugin'i (Gradle)
beyin/          FastAPI servisi, prompt'lar, kurgu araçları
paket/          Kaynak paketi (zip + sha1)
paket_kaynak/   Kaynak paketinin açık hali
varlik_kaynak/  Figürün çizim kaynakları
tuzaklar/       İzinli tuzak tarifleri
config.yml      Tüm oyun ayarları
.env.example    Anahtar şablonu
```

## Güvenlik

- API anahtarlarını commit'leme. `.env` ignore'da, yine de commit öncesi `git status` ile bak.
- Beyin servisi `127.0.0.1`'e bağlanır. Dışarı açma.
- Sunucuyu internete açacaksan `whitelist` kullan.

## Lisans

MIT. Bkz. `LICENSE`.
