# Diyaliz Ulaşım

Diyaliz hastalarının seans gidiş/dönüş ulaşımını hasta, hasta yakını,
koordinatör ve görevli arasında görünür hâle getiren Android prototipi. Ders
projesi kapsamında geliştiriliyor; gerçek hasta verisiyle kullanılmayacak,
yalnızca örnek/demo verilerle çalıştırılacaktır.

> **Depoyu klonlayanlar için:** Kaynak kodu ve dokümantasyon tamdır; ancak
> güvenlik ve makineye özel dosyalar bilinçli olarak depoya eklenmemiştir.
> Projeyi derleyip çalıştırmak için aşağıdaki **Hızlı kurulum** bölümünü izleyin.

## Hızlı kurulum (klonlayanlar için)

```powershell
git clone https://github.com/host2203/diyaliz-ulasim.git
cd diyaliz-ulasim
```

### Depoda olmayan dosyalar

| Eksik dosya/klasör | Neden yok | Nasıl oluşturulur |
| --- | --- | --- |
| `app/google-services.json` | Firebase proje yapılandırması; kişiye özel | Firebase Console'dan indirilip `app/` altına konur (adım 1) |
| `local.properties` | Makineye özel Android SDK yolu | Android Studio ilk açılışta oluşturur; gerekirse `sdk.dir=...` yazılır |
| `*.jks` / `*.keystore` | Release imzalama anahtarı | Yalnızca release build için kendi anahtarınızı üretin; debug build gerektirmez |
| `firestore-tests/node_modules/` | `npm install` ile kurulur (~250 MB) | `cd firestore-tests` → `npm install` |
| `firestore-tests/firestore.rules` | Kök `firestore.rules`'un test kopyası | `npm test` öncesi `sync-rules.js` otomatik kopyalar |
| `app/build/`, `.gradle/`, `.kotlin/` | Derleme çıktısı | Gradle / Android Studio üretir |

### 1. Firebase projesi ve `google-services.json`

1. <https://console.firebase.google.com> → **Add project** (Analytics isteğe bağlı).
2. Projeye **Android** uygulaması ekle; paket adı: `com.pigasor.diyalizulasim`.
3. `google-services.json` dosyasını indirip `app/google-services.json` konumuna koy.
4. **Authentication → Sign-in method**:
   - **Email/Password** etkinleştir.
   - **Google** için debug SHA-1/SHA-256 parmak izlerini ekle
     (`.\gradlew.bat signingReport` çıktısından alınır).
   - **Phone** için test numarası tanımla; gerçek SMS için kota gerekir.
5. **Firestore Database → Create database** (production mode).

> `google-services.json` yoksa proje derlenmez. Depoya eklenmez; her klonlayan
> kendi Firebase projesini kurmalıdır. Proje kimliği farklı olabilir; önemli olan
> Android paket adının `com.pigasor.diyalizulasim` olmasıdır. Bu dosyayı ve
> keystore'u asla commit etmeyin.

### 2. Firestore güvenlik kuralları

Kuralların tek doğruluk kaynağı kök dizindeki `firestore.rules` dosyasıdır.

```powershell
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules
```

Alternatif: Firebase Console → **Firestore → Rules** → dosya içeriğini yapıştır → **Publish**.

### 3. Koordinatör ve görevli hesapları

Hasta ve hasta yakını uygulamadan kayıt olabilir. **Koordinatör ve görevli
hesapları uygulamadan oluşturulamaz**; Firebase Console'dan açılır.

Her hesap için:

1. **Authentication → Users → Add user** ile e-posta/şifre oluştur ve **UID**'yi kopyala.
2. **Firestore → users/{UID}** belgesini oluştur:

| Alan | Tür | Değer |
| --- | --- | --- |
| `name` | string | Ad soyad |
| `email` | string | Hesabın e-postası |
| `phone` | string | Telefon (boş olabilir) |
| `role` | string | `COORDINATOR` veya `DRIVER` |
| `linkedPatientId` | string | `""` |
| `personnelId` | string | Görevli için `personnel` belge kimliği; koordinatörde `""` |
| `visibleStatuses` | array | `[]` |
| `createdAt` | number | Örn. `1730000000000` (epoch ms) |

Görevli için ayrıca **Firestore → personnel/{personnelId}** belgesi gerekir:

| Alan | Tür | Değer |
| --- | --- | --- |
| `name` | string | Görevli adı |
| `phone` | string | Telefon |
| `active` | boolean | `true` |
| `driverUid` | string | Görevlinin Auth UID'si |

Alternatif olarak koordinatör panelindeki **Araç ve personel** diyaloğundan
personel kaydı açılabilir; **“Görevli hesabının UID'si”** alanına Auth UID'si
yazılır. `users/{UID}` belgesindeki `personnelId`, bu kaydın kimliğiyle aynı
olmalıdır.

### 4. Demo verisi ve ilk kullanım

- Uygulama 6 örnek diyaliz merkeziyle gelir; koordinatör kapasite girdiğinde
  merkezler `centers` koleksiyonuna yazılır.
- Araçlar ve personel, koordinatör panelindeki **Araç ve personel** diyaloğundan eklenir.
- Araç/görevli ataması yapılmadan görevli ekranında iş görünmez.
- Gerçek hasta verisi kullanılmamalıdır; demo hesapları kurgusal olmalıdır.

### 5. Derleme ve çalıştırma

```powershell
.\gradlew.bat assembleDebug
```

Android Studio'da **Run** da kullanılabilir. Ayrıntılar: [Çalıştırma](#çalıştırma).
Güvenlik ve KVKK notları: `GUVENLIK.md`; pilot önerisi: `PILOT.md`.

## Problem

Diyaliz seansına ulaşım çoğu zaman telefon ve not kâğıtlarıyla koordine ediliyor.
Hasta ve yakını aracın ne zaman geleceğini, koordinatör de taleplerin hangi
aşamada olduğunu tek yerden göremiyor. Uygulama mevcut ulaşım planını görünür ve
düzenli hâle getirir; yeni bir nakil hizmeti kurmaz.

## Kullanıcı rolleri

- **Hasta:** Yaklaşan seansını ve ulaşım durumunu görür; talep oluşturur,
  hazırlık bildirir veya iptal eder.
- **Koordinatör:** Talepleri listeler, araç/görevli atar; gecikme ve öncelik
  taleplerini gerekçeli olarak onaylar.
- **Görevli:** Yalnızca kendisine atanmış yolculukları görür ve durum adımlarını
  (yolda, hasta alındı, merkeze ulaştı, eve bırakıldı) işaretler.
- **Hasta yakını:** Hastanın izniyle yolculuk durumunu görüntüler (yer tutucu).

## Durum zinciri

`Talep oluşturuldu → Planlandı → Hastaya gidiliyor → Hasta alındı →
Merkeze ulaştı → Eve bırakıldı` (iptal ayrı durum). Her adım zaman damgası ve
güncelleyen kullanıcıyla kaydedilir.

## Ekranlar

1. Giriş ve rol seçimi
2. Hasta paneli (talepler, hazırlık, iptal, bildirimler)
3. Talep formu (tarih, saat, adres, gidiş-dönüş, refakat, öncelik, not)
4. Koordinatör paneli (atama, gecikme/öncelik onayı, bildirimler)
5. Görevli paneli (atanmış işler, durum adımları, bildirimler)
6. Yakın görünümü (izin verilen hastanın durumu — yer tutucu)

## Özgün özellikler

- **Durum zinciri:** Her adım saat bilgisiyle kaydedilir; süreç geçmişi görünür.
- **Görevli tarafından durum işaretleme:** Hasta alındı/merkeze ulaştı/eve
  bırakıldı adımlarını sahadaki görevli yapar; transaction ile yetki ve sıra
  korunur.
- **Gecikme yönetimi:** Hasta gecikme süresini, yalnızca koordinatörün gördüğü
  gerekçeyi ve isteğe bağlı görevli mesajını bildirir. Araç henüz yola çıkmadıysa
  koordinatör onayı seans ve alınma saatini gecikme kadar erteler. Araç yoldayken
  hasta gecikmesi görevli + koordinatöre anında gider; onay gerekmez, görevli
  saha onayıyla “Hasta alındı” yapar.
- **Görevli/araç gecikmesi:** Görevli süre + gizli gerekçe bildirir; gerekçeyi
  yalnızca koordinatör görür. Bildirim her durumda anında koordinatör + hastaya
  gider; onay gerekmez; hastaya yalnızca ETA gösterilir.
- **Gerçek rota ETA’sı:** Ücretsiz OSRM ile merkez ↔ hasta evi arası sürüş süresi;
  hasta, koordinatör ve görevli kartında “Tahmini varış: X dk”. Görevli gecikmesi
  yalnızca ilgili bacağa eklenir. Canlı trafik/GPS sonraki fazdadır.
- **Otomatik dönüş planı:** Talep formunda tahmini seans süresi (varsayılan 240 dk)
  alınır. Hasta merkeze bırakılınca dönüş saati (varış + süre + 15 dk tampon)
  hesaplanır; koordinatör uygun araç/görevliyi onaylar. Uygun araç yoksa plan
  onay bekler; hastaya “Dönüş planı hazırlanıyor” gösterilir. Dönüş ETA’sı
  “Dönüşe başla”da hesaplanır. Görevli listesinde biten/iptal işler gizlenir.
- **Plan çakışması uyarısı:** Aynı araç/görevliye aynı saatte ikinci talep
  atanırsa koordinatör uyarılır.
- **Seans kapasite kontrolü:** Merkez + tarih + seans (sabah/öğleden sonra/akşam)
  doluluğu transaction ile kontrol edilir. Doluysa talep oluşturulmaz; en yakın
  üç uygun tarih/seans otomatik önerilir. Kapasiteyi koordinatör belirler;
  doluluk sayacı kişisel veri içermez.
- **Yanıtsız talep uyarısı:** Seansa az kala hâlâ planlanmamış talepler
  koordinatör ekranında öne çıkar.
- **Doğrulanmış öncelik:** 65+, engellilik/hareket kısıtlılığı, şehit/gazi
  yakını ve ciddi sağlık/fonksiyonel ihtiyaç kategorileri; yalnızca koordinatör
  resmi belge kontrolüyle doğrular. Belge/tanı saklanmaz.
- **Uygulama içi + yerel bildirimler:** Talep, atama, hazırlık, durum, iptal,
  gecikme ve öncelik olaylarında ilgili role bildirim; okunmamış sayacı ve
  cihaz bildirimi. Gizli gecikme gerekçesi bildirim metnine yazılmaz.
- **Hasta yakını daveti:** Hasta 6 haneli tek kullanımlık kod üretir; yakın
  kodla bağlanır ve yalnızca bağlı hastanın taleplerini/geçmişini okur.
- **Durum geçmişi:** Her talebin tür, değer, not, kullanıcı ve zaman içeren
  denetim izi; hasta/koordinatör/görevli/yakın görünümünde.
- **Koordinatör yönetimi:** Araç/personel ekleme-düzenleme, aktif/pasif yapma;
  merkez kapasitesi; otomatik oturum.
- **Google / telefon girişi:** Google hesap seçici ve Firebase telefon SMS
  doğrulaması; ilk girişte hasta profili. Telefon için SHA-1/SHA-256 ve test
  numarası gerekir.
- **Haritadan adres:** osmdroid haritasında nokta seçimi, Nominatim ile ters
  adres çevirme ve isteğe bağlı tek seferlik “konumumu kullan”. Canlı GPS takibi
  ve sürüş sırasında ETA yenileme yoktur.

## Teknoloji

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose, ViewModel + StateFlow
- Firebase Authentication (e-posta/şifre, Google ve telefon girişi; rol bilgisi)
- Cloud Firestore (kullanıcılar, talepler, personel, merkez kapasiteleri, seans
  doluluk sayaçları, durum geçmişi, gizli hasta/görevli gecikme raporları,
  ETA alanları, bildirimler)
- OpenStreetMap/osmdroid haritası + Nominatim ters geocoding (ücretsiz)
- Ücretsiz OSRM public API (gerçek rota süresi; API anahtarı/ücret yok)
- Yerel cihaz bildirimleri + uygulama içi bildirim merkezi (FCM yok)

## Testler

Kural emülatörü (Firebase Emulator; canlı projeye dokunmaz):

```powershell
cd firestore-tests
npm install
npm test
```

`npm test` öncesi `sync-rules.js`, kök `firestore.rules` dosyasını test
klasörüne kopyalar. Kotlin birim testleri proje kökünde:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

> Kural değişikliğinde önce emülatör testleri çalıştırılmalı, ardından
> `firebase deploy --only firestore:rules` ile canlıya yüklenmelidir.

## Çalıştırma

1. Android Studio'da `C:\Users\Pigasor\Desktop\Mobilprogramlama_diyaliz` klasörünü aç.
2. Gradle senkronizasyonunu bekle.
3. USB hata ayıklaması açık telefonu bağla ve **Run** seç.

Komut satırı:

```powershell
.\gradlew.bat assembleDebug
```

## Durum

- [x] Proje iskeleti (Compose + tema + Gradle wrapper)
- [x] Firebase entegrasyonu (Auth + Firestore)
- [x] Giriş / rol seçimi
- [x] Hasta: talep oluşturma, listeleme, hazırlık, iptal
- [x] Hasta: "Hazırım / Gecikeceğim" bildirimi
- [x] Koordinatör: araç/görevli atama, çakışma uyarısı
- [x] Görevli: atanmış işler ve durum zinciri
- [x] Gecikme süresi, gizli gerekçe ve koordinatör onayı
- [x] Onaylı gecikmede randevu/alınma saati erteleme; görevliye mesaj
- [x] Görevli/araç gecikmesi: gizli gerekçe, onaysız bilgi akışı
- [x] OSRM tabanlı gerçek rota ETA’sı; dönüş başlatma ve dönüş ETA’sı
- [x] Seans süresi + otomatik dönüş planı ve koordinatör onayı
- [x] Onay sadeleştirmesi; görevli listesinde aktif iş filtresi
- [x] Erişilebilirlik (tekerlekli sandalye/refakat) ve kapasite uyarıları
- [x] Seans doluluk kontrolü, otomatik alternatif önerisi ve koordinatör kapasite girişi
- [x] Firestore güvenlik kurallarının canlıya yüklenmesi ve canlı yetki testleri
- [x] Refakatçi iletişim bilgileri
- [x] Doğrulanmış öncelik kategorileri
- [x] Uygulama içi + yerel bildirimler
- [x] Firestore kuralları (emülatörde 99/99, canlıda 106/106 yetki testi)
- [x] Hasta yakını görünümü, davet kodu ve bağlantı
- [x] Durum geçmişi ekranı
- [x] Araç/personel ekleme-düzenleme ve aktif/pasif yönetimi
- [x] Otomatik oturum
- [x] Pilot öneri dosyası (`PILOT.md`)
- [x] Google ve telefon giriş akışları
- [x] Haritadan adres seçimi + tek seferlik konum
- [ ] Canlı GPS takibi / sürüş sırasında ETA yenileme
- [ ] Araç/personel ekleme-düzenleme (sonraki sürüm)
- [ ] FCM (uygulama kapalıyken bildirim)
- [ ] GPS ile sürüş sırasında ETA yenileme
- [ ] (İsteğe bağlı) canlı trafik sağlayıcısı

> Bildirimler, ödevde istenen "en az bir mobil cihaz özelliği" şartını karşılar.
> Harita/GPS kapsam dışıdır; adres elle girilir.

> Not: Bu bir ders prototipidir. Hasta adı, adresi, iletişim bilgisi veya
> sağlık verisi kullanılmamalıdır; sunumda kurgusal hastalarla çalıştırılmalıdır.
> Demo verisinde kamuya açık gerçek diyaliz merkezi adları/adresleri
> kullanılabilir.
