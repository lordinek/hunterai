# HunterAI

Minecraft'ta bir yapay zeka seni avlıyor. Kapıya ulaşmaya çalışırken yapay zeka seni izler, ne yaptığına bakıp oyun içi tuzaklar kurar ve ara sıra sesli konuşur.

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
| Bir LLM API anahtarı | Gemini, OpenAI (ChatGPT) veya Anthropic (Claude). Üçünden **biri** yeter, hepsi gerekmez |
| ElevenLabs API anahtarı | İsteğe bağlı. Yoksa gerçek ses yerine sahte uğultu çalar |
| Simple Voice Chat | İsteğe bağlı (`softdepend`). Sesin oyun içinde duyulması için |
| İşletim sistemi | Betikler PowerShell (Windows). Linux/macOS'ta komutları elle çalıştır |

## API anahtarları nereye girilir

Anahtarlar **yalnızca** proje kökündeki `.env` dosyasına yazılır. Bu dosya `.gitignore` içindedir, GitHub'a gitmez. Anahtarı `config.yml`'a veya koda yazma.

1. Şablonu kopyala:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Kullanacağın yapay zekânın anahtarını doldur. **Hepsini doldurman gerekmez**, yalnızca seçtiğin sağlayıcınınkini yaz:

   | Sağlayıcı | `.env` değişkeni | Anahtarı nereden alırsın |
   | --- | --- | --- |
   | Gemini (Google) | `GEMINI_API_KEY` | https://aistudio.google.com/apikey |
   | OpenAI (ChatGPT) | `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
   | Anthropic (Claude) | `ANTHROPIC_API_KEY` | https://console.anthropic.com |
   | ElevenLabs (ses, isteğe bağlı) | `ELEVENLABS_API_KEY` | https://elevenlabs.io |

   Örnek `.env` (Gemini kullanan biri için):

   ```
   GEMINI_API_KEY=buraya_anahtarin
   ELEVENLABS_API_KEY=
   ```

3. Hangi yapay zekâyı kullanacağını `config.yml` içindeki `beyin` bölümünden seç. `saglayici` alanı ile `.env`'e yazdığın anahtar **aynı sağlayıcıya** ait olmalı:

   ```yaml
   saglayici: gemini          # gemini | openai | anthropic
   model_duzenli: gemini-3.5-flash   # düzenli kararlar, hızlı/ucuz model
   model_agir: gemini-3.5-flash      # ölüm raporu gibi ağır işler
   ```

   Model alanlarına seçtiğin sağlayıcının model adını yaz. Örneğin `openai` için OpenAI'ın bir model adı, `anthropic` için bir Claude model adı. Anahtar boşsa beyin LLM'e hiç gitmez, rastgele "sahte" kararlarla çalışır (boru hattını denemek için). Anahtar var ama model adı yanlışsa LLM çağrısı hata verir ve yapay zeka o turda `bekle` kararı verir, yani oyunda hiçbir şey yapmaz. Bunu `/yz test beyin` ile kontrol et.

   **ChatGPT dışında OpenAI uyumlu bir servis** (OpenRouter, Groq, yerel Ollama/LM Studio gibi) kullanmak için `saglayici: openai` yap, `openai_adres` alanına servisin adresini (ör. `https://openrouter.ai/api/v1`) yaz, anahtarı yine `OPENAI_API_KEY` olarak gir. Servis `response_format: json_object` ve `max_completion_tokens` alanlarını kabul etmeli.

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

`config.yml` üç yerde aynı olmalı: kökte, `plugin/src/main/resources/config.yml` ve `sunucu/plugins/YzTuzak/config.yml`. Beyin servisi kökteki dosyayı, plugin sunucu klasöründeki kopyayı okur. `sunucu/plugins/YzTuzak/config.yml` plugin ilk açıldığında kendiliğinden oluşur. Ayarı değiştirirsen üçünü de güncelle.

Varsayılan oyun can sınırsızdır (`can_hakki: 0`). Sınır koymak istersen sayıyı yaz.

## Spoiler kilidi

`config.yml` içindeki `spoiler_kilidi: true` ayarı, oyun sırasında oyuncuya tuzak bilgisi gösterilmesini engeller. Oynayan kişi tuzakları bilmesin istiyorsan açık bırak. Oyun bitince `/yz spoiler ac` ile kilidi kaldır. Kurgu araçları ancak ondan sonra çalışır. Tuzak kataloğu `tuzaklar/izinli_tarifler.json` dosyasından okunur.

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
