# Diyaliz Ulaşım — Proje Planı

Bu dosya, ders projesinin kapsamını ve geliştirme sırasını netleştirmek için
hazırlandı. Kod yazılmadan önce bu planın onaylanması hedefleniyor.

## 1. Amaç

Diyaliz hastasının seansına gidiş ve dönüş ulaşımını hasta, hasta yakını ve
koordinatör arasında görünür hâle getiren bir Android prototipi. Yeni bir nakil
hizmeti kurulmuyor; mevcut ulaşım planının durumu takip edilebilir ve düzenli
hâle getiriliyor.

Gerçek hasta verisi kullanılmayacak. Uygulamanın veri katmanı gerçek olacak:
Firebase Authentication ve Cloud Firestore üzerinde çalışan, uygulama kapansa da
verisi kalan bir sistem. Yalnızca demo kişi adları/adresleri kurgusal olacak.

## 2. Kapsam

### MVP (ilk çalışan sürüm)

- Firebase Authentication ile giriş, kayıt ve rol seçimi
- Hasta: ulaşım talebi oluşturma, listeleme, düzenleme, iptal
- Hasta: "Hazırım" / "Gecikeceğim" bildirimi
- Koordinatör: talep listesi, araç/görevli atama, yeniden atama
- Durum zinciri ve zaman damgalı geçmiş
- Çakışma, kapasite ve erişilebilirlik uyarıları
- Hasta yakını: izin verilen durumları görüntüleme
- Seans ve alınma saati için yerel bildirimler

### Sonraki sürüm

- Araç ekleme/düzenleme ekranı (araçlar baştan Firestore'da tutulacak)
- Google Maps + Places ile haritadan adres seçme
- Push bildirim (FCM) — gerekirse
- Basit istatistik/rapor ekranı

### Kapsam dışı

- Aracın haritada canlı GPS takibi (arka plan konum + gerçek zamanlı sunucu
  gerektirir)
- Gerçek hasta/sevk verisi (aşağıdaki koşullar sağlanmadan)
- Ödeme, faturalandırma, sigorta entegrasyonu
- iOS uygulaması

## 2.1 Gerçek hasta verisi kullanımı

Ders teslimi ve demo için kurgusal hasta adları/adresleri kullanılacak; verilen
spec bunu açıkça şart koşuyor. Demo verisinde **kamuya açık gerçek diyaliz
merkezi adları/adresleri** kullanılabilir; hasta adı, adresi, iletişim bilgisi
ve sağlık verisi kurgusal olacak. Gerçek hasta verisi ancak aşağıdaki koşulların
tümü sağlanırsa ve danışman onaylarsa değerlendirilir:

- Etik kurul/okul onayı ve diyaliz merkezinin yazılı izni
- Anonimleştirilmiş veya en az veri (ad, adres, kimlik numarası yok)
- KVKK kapsamında aydınlatma/açık rıza ve veri işleme şartları
- Verinin bulut (Firebase/Google) üzerinde saklanmasının onay kapsamında olması
- Danışmanın proje kapsamına onayı

Firebase/Google Cloud veriyi yurt dışında saklayabilir; sağlık verisi için ek
aktarım onayı gerekebilir. Bu nedenle gerçek veri Firebase'e yüklenmeyecek;
gerekirse yerel veya kurum tarafından onaylanmış bir ortamda tutulacak. Ders
demosunda gerçek hasta verisi kullanılmayacak.

## 3. Roller ve yetkiler

- **Hasta:** Kendi taleplerini oluşturur, düzenler, iptal eder; hazırlık
  bildirimi verir; kendi geçmişini görür.
- **Koordinatör:** Tüm talepleri görür; araç/görevli atar; durum günceller;
  çakışma ve uyarıları yönetir.
- **Hasta yakını:** Yalnızca izin verilen hastanın izin verilen durumlarını
  görür; işlem yapamaz.

Rol, kayıt sırasında seçilir (prototip kolaylığı). Gerçek üründe koordinatör
rolü yönetici tarafından atanır.

## 4. Ekranlar

1. **Giriş / kayıt:** e-posta, şifre, ad; kayıtta rol seçimi.
2. **Hasta ana sayfa:** yaklaşan seans, güncel durum, "Hazırım/Gecikeceğim".
3. **Talep formu:** tarih, saat, merkez, adres, gidiş-dönüş, refakat,
   tekerlekli sandalye, not.
4. **Taleplerim:** geçmiş ve yaklaşan talepler; düzenleme/iptal.
5. **Talep detayı / zaman çizelgesi:** durum geçmişi saatleriyle.
6. **Koordinatör paneli:** filtreli talep listesi, uyarı rozetleri.
7. **Atama ekranı:** araç/görevli seçimi, kapasite ve erişilebilirlik kontrolü,
   çakışma uyarısı, yeniden atama.
8. **Yakın görünümü:** bağlı hastanın izin verilen durumları.
9. **Yakın izinleri (hasta):** hangi durumların görüleceğini seçme.
10. **Çıkış:** oturumu kapatıp giriş ekranına döner.

## 5. Durum modeli

Taşıma durumu:

`REQUESTED → PLANNED → ON_THE_WAY → PICKED_UP → AT_CENTER → DROPPED_HOME`

Ayrıca `CANCELLED`.

Türkçe etiketler:

- Talep oluşturuldu
- Planlandı
- Hastaya gidiliyor
- Hasta alındı
- Merkeze ulaştı
- Eve bırakıldı
- İptal edildi

Hazırlık durumu (taşıma durumundan bağımsız):

- `NOT_SET` — bildirmedi
- `READY` — hazırım
- `DELAYED` — gecikeceğim (+15/+30 dk)

Her durum ve hazırlık değişikliği geçmişe zaman damgası ve güncelleyen
kullanıcıyla yazılır.

## 6. Veri modeli (Cloud Firestore)

### users/{uid}

- name, email, phone, role
- Yakın için: linkedPatientId, visibleStatuses
- createdAt

### vehicles/{vehicleId}

- name, plate
- wheelchairAccessible (boolean)
- capacity (int)
- active (boolean)

İlk sürümde 3–4 örnek araç Firestore'a eklenecek. Araç CRUD ekranı sonra.

### transportRequests/{requestId}

- patientId, patientName
- sessionDate, sessionTime, center
- pickupAddress, pickupLat, pickupLng
- roundTrip, companionRequired, wheelchairRequired
- note
- status
- vehicleId, vehicleName, personnel, plannedPickupTime
- readiness, delayMinutes, readinessUpdatedAt
- createdBy, createdAt, updatedAt

### transportRequests/{requestId}/history/{eventId}

- type: STATUS | READINESS | ASSIGNMENT | NOTE
- value (yeni durum/metin)
- note
- updatedBy, updatedByName
- timestamp

Seans bilgisi prototipte talebin içinde tutulur; ayrı `sessions` koleksiyonuna
ayırmak sonraki sadeleştirme/ayrıştırma adımı olabilir.

## 7. İş kuralları

- **Araç çakışması:** Aynı araç, aynı tarihte ve zaman aralıkları çakışıyorsa
  (varsayılan 60 dk tampon) uyarı.
- **Görevli çakışması:** Aynı görevliye aynı saatte ikinci görev → uyarı.
- **Kapasite:** Hasta + refakatçi, aracın kapasitesini aşıyorsa uyarı.
- **Erişilebilirlik:** Tekerlekli sandalye gerekiyorsa ve araç uygun değilse
  uyarı; koordinatör gerekçe yazarak devam edebilir (geçmişe kaydedilir).
- **Yanıtsız talep:** Seansa 24 saatten az kala hâlâ `REQUESTED` ise listede
  öncelikli/kırmızı gösterim.
- **Hasta hazır değil:** Araç `ON_THE_WAY` durumundayken hasta `DELAYED` veya
  `NOT_SET` ise koordinatör ekranında uyarı.
- **Yeniden atama:** Araç/görevli değişikliği geçmişe yazılır; kontroller
  yeniden çalışır.

## 8. Teknoloji ve mimari

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose
- ViewModel + StateFlow
- Firebase Authentication (e-posta/şifre)
- Cloud Firestore
- Google Maps SDK + Places (adres seçimi; sonraki faz)
- WorkManager + yerel bildirimler
- Test: fiziksel Android telefon, USB hata ayıklama

Katmanlar:

- `ui/` — ekranlar ve bileşenler
- `data/model/` — veri sınıfları
- `data/repository/` — Auth ve Firestore işlemleri
- `viewmodel/` — ekran durumları

## 9. Harita entegrasyonu (Google Maps + Places)

- Talep formunda "Haritadan seç" düğmesi.
- Harita ekranında adres arama, haritada pin sürükleme, seçilen adresi forma
  aktarma.
- Google Cloud projesinde Maps SDK for Android ve Places API etkinleştirilecek.
- API anahtarı Android uygulamasına kısıtlanacak (paket adı + debug SHA-1).
- Faturalandırma hesabı gerekir; küçük demo kullanımı genellikle ücretsiz
  kotada kalır, güncel fiyatlandırma kontrol edilmeli.
- Canlı araç takibi yok; harita yalnızca adres seçimi ve konum gösterimi için.

## 10. Bildirimler

- Seans hatırlatması: 24 saat ve 2 saat önce.
- Planlanan alınma saati hatırlatması: 30 dakika önce.
- Uygulama açıkken durum değişikliği bildirimi (Firestore dinleyicisi).
- Android 13+ için `POST_NOTIFICATIONS` izni istenecek.
- Uygulama tamamen kapalıyken başka cihaza push için FCM + Cloud Functions
  gerekir; MVP dışı, isteğe bağlı sonraki faz.

## 11. Geliştirme fazları

| Faz | İçerik | Tahmini süre |
|---|---|---|
| 0 | Ortam kurulumu, iskelet, derleme doğrulama | Tamamlandı |
| 1 | Firebase kurulumu, Auth, rol seçimi, navigasyon | 3–5 gün |
| 2 | Hasta: talep CRUD, hazırlık bildirimi, geçmiş | 5–7 gün |
| 3 | Koordinatör: liste, araç/görevli atama, uyarılar, durum zinciri | 7–10 gün |
| 4 | Google Maps + Places adres seçimi (çekirdekten hemen sonra) | 3–5 gün |
| 5 | Yakın görünümü ve izinler | 3–5 gün |
| 6 | Bildirimler, demo verileri, cila | 4–6 gün |
| 7 | Telefon testi, hata düzeltme, sunum provası | 3–5 gün |

Toplam yaklaşık 5–6 hafta; 1 aydan uzun süreye sığar. Harita çekirdek akıştan
hemen sonra; araç CRUD'u takvime göre sona bırakılabilir.

## 12. Demo senaryosu

1. Hasta giriş yapar, yarın 09:00 için talep oluşturur; adresi haritadan seçer,
   tekerlekli sandalye ve refakat işaretler.
2. Koordinatör talebi görür; uygun aracı atar, durumu "Planlandı" yapar.
3. Aynı araca aynı saatte ikinci talep atanır → çakışma uyarısı gösterilir.
4. Hasta "Hazırım" veya "Gecikeceğim" der; koordinatör rozeti anında görür.
5. Koordinatör durum zincirini günceller; hasta ve yakın zaman çizelgesini görür.
6. Yakın, davet koduyla bağlanır; yalnızca izin verilen durumları görür.
7. Firebase Console'da gerçek kayıtlar gösterilir; uygulama kapatılıp açılınca
   verilerin kaldığı doğrulanır.

## 13. Kurulum gereksinimleri

- Firebase projesi (Firebase Console)
  - Authentication → Email/Password
  - Firestore Database (test modu; teslim öncesi kurallar sıkılaştırılacak)
  - `google-services.json` → `app/` klasörü
- Google Cloud (aynı proje üzerinden)
  - Maps SDK for Android
  - Places API
  - Faturalandırma hesabı
  - Android kısıtlı API anahtarı (paket + SHA-1)
- Fiziksel telefon + USB hata ayıklama

## 14. Varsayılan kararlar

- Rol, kayıt sırasında seçilir (prototip).
- Yakın bağlantısı: hasta 6 karakterlik davet kodu üretir; yakın bu kodu girer
  ve hasta bağlantıyı iptal edebilir.
- Bildirimler MVP'de yerel; FCM sonraki faz.
- Harita adres seçici çekirdek akıştan hemen sonra (4. faz).
- Araç CRUD'u MVP sonrası; araç verileri baştan Firestore'da.

## 15. Riskler ve önlemler

- **Google Maps faturalandırma:** Kart/hesap gerektirir. Sorun olursa
  OpenStreetMap (osmdroid + Nominatim) veya yalnızca "konumumu kullan"
  seçeneğine düşülebilir.
- **Firestore test modu:** 30 gün sonra kapanır; teslim öncesi güvenlik
  kuralları yazılmalı.
- **Push bildirim:** Uygulama kapalıyken FCM, Cloud Functions/Blaze planı
  gerektirir; MVP'de yerel bildirimle sınırlı.
- **Canlı GPS:** Kapsam dışı; istenirse ayrı bir proje ölçeğinde ele alınmalı.
- **Tek cihaz demosu:** İki rol aynı anda gösterilecekse iki telefon veya
  sırayla rol değiştirme kullanılabilir.
