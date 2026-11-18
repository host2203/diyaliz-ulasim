# Yapılanlar ve Proje Durumu

Bu dosya, proje boyunca yapılanları ve alınan kararları kayıt altında tutar.
Proje sonunda sunum/rapor için buradan özet çıkarılabilir.

## Proje

- **Ad:** Diyaliz Ulaşım
- **Tür:** Ders projesi / Android prototipi
- **Konu:** Diyaliz seansına gidiş-dönüş ulaşımının hasta, hasta yakını,
  koordinatör ve görevli arasında takibi
- **Kural:** Gerçek hasta verisi kullanılmıyor; gerçek diyaliz merkezi
  adları/adresleri kullanılabilir, hasta kimlikleri kurgusal.
- **Cihaz:** Samsung Galaxy S8+ (SM-G955F), Android 9 (API 28)

## Teknoloji

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose, ViewModel + StateFlow
- Firebase Authentication + Cloud Firestore
- OpenStreetMap/Nominatim ile ücretsiz merkez arama
- Android Studio 2026.2.1, Android SDK 35, JDK 21, Gradle 8.9

## Tamamlananlar

### Ortam
- Android Studio, SDK 35, build-tools, platform-tools kuruldu.
- JDK 21 kuruldu; Gradle 8.9 ile uyumlu hale getirildi.
- Proje masaüstünde: `C:\Users\Pigasor\Desktop\Mobilprogramlama_diyaliz`
- Uygulama telefona kuruldu ve çalıştırıldı.

### Firebase
- Firebase projesi: `diyaliz-ulasim`
- `google-services.json` → `app/google-services.json`
- Authentication: Email/Password etkin.
- Firestore veritabanı oluşturuldu.
- `firestore.rules` **2026-10-27'de canlıya yüklendi**; test modu kapandı.
- Canlı yetki testleri: **106/106** (63 okuma + 43 yazma/durum).
- Koordinatör, görevli ve hasta test hesapları oluşturuldu.

### Güvenlik
- `GUVENLIK.md`: veri minimizasyonu, roller, KVKK/etik sınırlar, loglama,
  bildirim gizliliği, teslim kontrol listesi.
- `firestore.rules`: hasta/koordinatör/görevli/yakın rolleri; personel ve
  bildirim koleksiyonları; durum zinciri; gecikme onayı; gecikme gerekçesi için
  ayrı `delayReports` koleksiyonu; öncelik doğrulama; geçmişin değiştirilemez
  olması; görevli gecikme gerekçeleri ve ETA alanları; seans doluluk sayaçları.
  Kurallar Firestore Emulator'da **88/88 test geçti**, 2026-10-27'de canlıya
  yüklendi ve canlıda doğrulandı. 1000 ifade sınırı uyarısı hâlâ açık risk.
- `Guvenlik.kt`: e-posta/şifre/tarih/saat/adres/telefon doğrulama, metin
  temizleme, bildirimlerde ad maskeleme.
- Koordinatör ve görevli hesapları kullanıcı tarafından oluşturulamaz;
  Console'dan yönetici oluşturur.
- `google-services.json` ve keystore dosyaları `.gitignore` içinde.

### Giriş ve roller
- E-posta/şifre ile giriş ve kayıt.
- Kayıtta Hasta veya Hasta yakını rolü seçimi.
- Koordinatör ve görevli girişi mevcut hesapla yapılır.
- Rol bazlı yönlendirme: hasta / koordinatör / yakın / görevli ekranı.
- Çıkış yap düğmesi.

### Hasta akışı
- Hasta paneli: kullanıcı adı, talep listesi (gerçek zamanlı), yeni talep.
- Talep formu:
  - Tarih alanı: bitişik 8 rakam yazılır, ekranda otomatik `GG.AA.YYYY`.
  - Saat alanı: 4 rakam yazılır, ekranda otomatik `SS:DD`.
  - Geçmiş tarih/saat engeli.
  - Geçersiz tarih için ayrıntılı uyarı (ör. “Kasım ayı 30 gündür”).
  - Geçersiz saat için ayrıntılı uyarı (saat/dakika aralığı).
  - İl ve ilçe seçimi: 81 il ve tüm ilçeler uygulamaya gömülü.
  - Merkez seçimi:
    - OpenStreetMap/Nominatim üzerinden “diyaliz <ilçe> <il>” araması.
    - Firestore `centers` koleksiyonundaki merkezler.
    - 6 örnek merkez (İstanbul, Ankara, İzmir, Bursa).
    - Liste boşsa merkez adını elle girme.
  - Gidiş-dönüş, refakat, tekerlekli sandalye, öncelik ve not alanları.
  - Refakatçi gerekiyorsa ad, telefon ve yakınlık derecesi zorunlu.
  - Öncelik seçimi: 65 yaş üzeri, engellilik/hareket kısıtlılığı,
    şehit/gazi yakını, ciddi sağlık/fonksiyonel ihtiyaç.
  - **Seans doluluk kontrolü:** Merkez + tarih + seans (sabah/öğleden sonra/
    akşam) için toplam doluluk okunur. Doluysa talep oluşturulmaz; en yakın
    üç uygun tarih/seans otomatik önerilir. Kapasite merkez belgesinden gelir.
  - Talebi Firestore'a kaydetme; hasta panelinde canlı görünme.
- Hasta “Hazırım” / “Gecikeceğim” bildirimi ve talebi iptal etme.
  - “Gecikeceğim” diyaloğu gecikme süresini (5-180 dk), yalnızca koordinatörün
    göreceği gerekçeyi ve isteğe bağlı görevli mesajını toplar.
  - Gerekçe talep belgesine değil, ayrı ve yalnızca koordinatörün okuyabildiği
    `delayReports` koleksiyonuna yazılır.
- Tarih Firestore'da `YYYY-MM-DD`, ekranda `GG.AA.YYYY` olarak tutulur.

### Koordinatör paneli
- Tüm talepler gerçek zamanlı listelenir; aynı gün içinde doğrulanmış
  öncelikli talepler üstte gösterilir.
- Araç/görevli atama diyaloğu: araç ve görevli listeden seçilir, planlanan
  alınma saati girilir.
- Erişilebilirlik (tekerlekli sandalye), kapasite ve araç/görevli çakışma
  uyarıları.
- **Merkez kapasiteleri:** Koordinatör merkez seçip seans başına hasta
  kapasitesini (0-50) kaydeder; 0 doluluk kontrolünü kapatır. Demo merkezler
  için ilk kayıtta `centers` belgesi oluşturulur.
- Gecikmeli hasta için “Gecikmeyi onayla” akışı:
  - Gizli gecikme gerekçesi ve hastanın görevliye mesajı koordinatörde görünür.
  - Araç henüz yola çıkmadıysa (REQUESTED/PLANNED) seans ve alınma saati gecikme
    süresi kadar ileri alınır; gece geçişinde tarih de güncellenir.
  - Araç yoldaysa (ON_THE_WAY) saat değiştirilmez; bilgi görevli + koordinatöre
    anında gider ve **onay gerekmez**. Görevli, hasta gerçekten araçtaysa alımı
    işaretler (bir kerelik saha onayı).
  - Onay yalnızca araç yola çıkmadan gerekir; gerekçe görevliye değil, süre/yeni
    saat/hasta mesajı iletilir.
- Görevli/araç gecikmesi:
  - Görevli süre + gizli gerekçe bildirir; gerekçeyi yalnızca koordinatör görür.
  - Bildirim her durumda anında koordinatör + hastaya gider; **onay gerekmez**.
  - Hastaya yalnızca tahmini varış süresi gider; saat otomatik değişmez.
  - Gecikme yalnızca ilgili bacak ETA’sına eklenir; bacak değişince sıfırlanır.
- OSRM tabanlı ETA:
  - Görevli “Hastaya gidiliyor” dediğinde merkez → hasta evi,
  - “Hasta alındı” dediğinde hasta evi → merkez,
  - “Dönüşe başla” dediğinde merkez → hasta evi rotası hesaplanır.
  - Sonuç hasta/koordinatör/görevli kartında “Tahmini varış: X dk” olarak görünür.
- Otomatik dönüş planı:
  - Talep formunda tahmini seans süresi (varsayılan 240 dk) alınır; tampon 15 dk.
  - Hasta merkeze bırakılınca dönüş saati otomatik hesaplanır ve plan onaya düşer.
  - Koordinatör uygun araç/görevliyi seçip onaylar; uygun olmayan araç onaylanamaz.
  - Uygun araç yoksa plan “onay bekliyor” kalır; hastaya “Dönüş planı hazırlanıyor”
    denir, “araç yok” denmez.
  - Dönüş ETA’sı “Dönüşe başla” anında hesaplanır.
- Öncelik talebini resmi belge kontrolü sonrası doğrulama (belge/tanı
  saklanmaz, yalnızca doğrulama notu).
- Her atama/durum/onay değişikliği geçmişe zaman damgasıyla yazılır.
- Yanıtsız/yaklaşan talep uyarıları; kalıcı “hasta hazır değil” uyarısı kaldırıldı.
  Hazırlık satırı ve gecikmeye özel mesaj korunuyor.
- Koordinatör olmayan kullanıcı bu ekrana giremez.

### Görevli paneli
- Yalnızca görevlinin personel kimliğine atanmış **aktif** talepler listelenir.
  İptal/eve bırakılanlar gizlenir; gidiş-dönüşte “Dönüş bekliyor” kartı korunur.
- Durum adımlarını görevli ilerletir:
  Planlandı → Hastaya gidiliyor → Hasta alındı → Merkeze ulaştı →
  Eve bırakıldı.
- Durum güncellemesi Firestore transaction'ı ile yapılır; eşzamanlı değişiklik
  ve yetkisiz personel engellenir.
- Hasta yoldayken “Gecikeceğim” bildirirse bilgi verilir; alımı kilitlemez.
  Görevli, hasta gerçekten araçtaysa bir kerelik “Evet, işaretle” onayıyla ilerler.
- Hasta yola çıkmadan gecikirse koordinatör onayı/randevu ertelemesi devam eder.
- Gizli gecikme gerekçesi görevliye hiçbir yerde gösterilmez.
- Hasta hazırlık bildirimi yapmadıysa “Hasta alındı” için bir kerelik onay sorulur.
- Görevli “Gecikeceğim” diyaloğuyla süre + gerekçe bildirir; gerekçe hastaya
  gösterilmez; onay gerekmez.
- “Dönüşe başla” yalnızca koordinatör dönüş planını onayladıktan sonra görünür.
- Görevli kartında kendi ETA’sı ve gecikme bildiriminin durumu görünür.
- Refakatçi ve doğrulanmış öncelik bilgisi görevliye gösterilir.

### Uygulama içi ve cihaz bildirimleri
- Firestore `notifications` koleksiyonu; rol bazlı bildirim kutusu.
- Bildirim olayları:
  - Yeni talep → koordinatör.
  - Araç/görevli ataması → görevli + hasta.
  - Hazırlık bildirimi → koordinatör; araç yoldayken hasta gecikmesi ayrıca
    görevliye hemen gider. Araç yola çıkmamışsa görevli onay sonrası bilgilendirilir.
  - Durum adımları (yolda/alındı/merkez/eve) → koordinatör + hasta.
  - İptal → koordinatör + görevli.
  - Hasta gecikmesi → koordinatör; araç yoldayken görevliye de anında gider.
    Gerekçe bildirim metnine yazılmaz.
  - Hasta gecikmesi onayı (araç yola çıkmadan) → görevli + hasta; görevliye süre,
    yeni saat ve hastanın mesajı gider, gizli gerekçe gitmez.
  - Görevli gecikmesi → koordinatör + hasta; hastaya yalnızca ETA gider, gerekçe
    gitmez; onay bildirimi yoktur.
  - Dönüş planı hazır → koordinatör; dönüş planı onaylandı → hasta + görevli.
  - Öncelik doğrulama → hasta.
- Panel başlığında okunmamış sayacı; bildirim merkezinde “okundu” işaretleme.
- Yeni bildirimlerde cihaz bildirimi (Android kanalı + izin); FCM yok.
- Bildirim metinlerinde ad maskelenir (`T***`).

### Test (2026-10-25)
- Uçtan uca hasta + koordinatör + görevli akışı fiziksel telefonda test edildi.
- Tarih/saat uç durumları, OSM merkez araması, atama uyarıları, durum zinciri,
  hazırlık bildirimi ve gerçek zamanlı güncelleme çalıştı.
- Görevliye özel atama, durum zinciri, iptal, refakatçi bilgileri, öncelik
  doğrulama ve bildirimler cihazda denendi.
- Gecikme süresi + gizli gerekçe + görevli mesajı cihazda denendi:
  koordinatör onayı seansı 09:30 → 09:50, alınmayı 09:00 → 09:20 yaptı;
  görevli kartı/notification yeni saati ve mesajı gösterdi, gerekçeyi göstermedi.
- Araç yoldayken hasta gecikmesi: görevli + koordinatöre anında bilgi gitti;
  onay ve buton kilidi kaldırıldı; görevli saha onayıyla “Hasta alındı” yaptı.
- Görevli/araç gecikmesi cihazda denendi:
  - 15 dk + gizli gerekçe; onay beklenmeden bilgi verildi; hastaya yalnızca ETA.
  - OSRM 8 dk + 15 dk gecikme = 23 dk; “Hasta alındı”da gecikme temizlendi ve
    merkez ETA’sı 7 dk oldu.
  - “Merkeze ulaştı”da otomatik dönüş planı (4 saat + 15 dk) onaya düştü.
  - Koordinatör aynı araç/görevliyle onayladı; hasta + görevli bilgilendirildi.
  - “Dönüşe başla”da ev ETA’sı 8 dk; “Eve bırakıldı” sonrası kart görevli
    listesinden kalktı.
  - Dönüş planı onaylanmadan “Eve bırakıldı” ve “Dönüşe başla” görünmedi.
- `firestore.rules` emülatör testleri: **77/77 geçti** (2026-10-25; güncel
  sonuç 88/88 — aşağıdaki 2026-10-27 bölümü) (onaysız gecikmeler,
  görevli gecikme raporu, ETA alanları, dönüş planı/başlatma ve atomik batch'ler).
- Kotlin birim testleri: **11/11 geçti** (2026-10-25; güncel sonuç 19/19)
  (gecikme/ETA/dönüş zamanı, görevli liste
  filtresi, tarih/saat sınırları).
- Geçici test hesapları, talepleri, geçmişleri, gecikme raporları ve bildirimleri
  temizlendi. Canlıda yalnızca bir geçerli demo talebi ve ona ait dört bildirim
  kaldı; demo talebine ETA için merkez/ev koordinatları eklendi.

### Test (2026-10-27)
- P0 canlı yetki testleri: **106/106** geçti (63 okuma + 43 yazma/durum).
  Oturumsuz erişim 403; hasta yalnızca kendi talebi; koordinatör tümü; görevli
  yalnızca kendi `personnelId`'si; gizli gecikme raporları ve dönüş planı onayı
  doğrulandı.
- Kapasite özelliği cihazda denendi:
  - Yeni talep formunda “Bu seans: 0/4 dolu” gösterildi.
  - Sayaç 4/4 yapıldığında “DOLU” ve üç otomatik alternatif listelendi.
  - Alternatif seçimi tarih/saati güncelledi; dolu seansa kayıt sunucu tarafında
    engellendi ve alternatifler korundu.
  - Koordinatör “Merkez kapasiteleri” diyaloğu kapasiteyi kaydetti.
- `firestore.rules` emülatör testleri: **88/88** (11 yeni doluluk sayacı testi).
- Kotlin birim testleri: **19/19** (`KapasiteTest` dahil).
- Test talebi, doluluk sayacı, bildirimi ve test merkez belgesi silindi; canlıda
  yalnızca demo talep ve onun 1/4 doluluk sayacı kaldı.

### P1/P2 (2026-10-27)
- **Durum geçmişi ekranı:** hasta, koordinatör, görevli ve hasta yakını kartlarında
  zaman damgalı denetim izi; tür/değer/not/yapan kişi gösterilir.
- **Hasta yakını görünümü:** hasta 6 haneli tek kullanımlık davet kodu üretir;
  yakın kodu girerek bağlanır. `relativeInvites` + değiştirilemez `relativeLinks`
  belgeleri; yakın yalnızca bağlı hastanın taleplerini/geçmişini okur.
- **Araç/personel CRUD:** koordinatör panelinden ekleme/düzenleme, aktif/pasif
  yapma; pasif kayıtlar atama listesinde görünmez.
- **Otomatik oturum:** uygulama açılışında Firebase profili yüklenir ve kullanıcı
  doğrudan rol ekranına gider.
- **Pilot dosyası:** `PILOT.md` (problem/çözüm, kapsam, mimari, KVKK, maliyet,
  yol haritası).
- **Bulunan ve düzeltilen hatalar:** geçmişte öncelik kategorisi ham kod olarak
  görünüyordu; yakın bağlantısında talep listesi sessizce boş kalabiliyordu
  (yeniden deneme + hata mesajı eklendi); hasta talebinin tarih/saat/merkezini
  değiştirerek kapasite sayacını atlaması kural düzeyinde engellendi.
- **Google/telefon girişi:** Google hesap seçici + Firebase ID token ve telefon
  SMS doğrulaması; ilk girişte hasta profili. `google-services.json` güncellendi,
  debug SHA-1/SHA-256 Firebase'e eklendi. Telefon test numarası ve gerçek SMS
  testi kullanıcı tarafında yapılmalı.
- **Haritadan adres:** osmdroid haritası, Nominatim ters geocoding ve tek
  seferlik “konumumu kullan”. Canlı GPS takibi/sürüş sırasında ETA yenileme yok.
- **Test:** kural emülatörü 99/99; cihazda geçmiş, yakın bağlantısı, araç CRUD,
  otomatik oturum, davet kodu, Google hesap seçici, telefon giriş ekranı, harita
  ve ters adres çevirme denendi. İptal sonrası sayaç azaltma cihazda ayrıca
  tamamlanamadı; kod ve kurallar emülatörde doğrulandı.
- **Kapsam dışı bırakılanlar:** FCM/push (sunucusuz/faturasız kısıt), canlı
  GPS takibi ve canlı trafik; ayrı faz.

### Veri modeli (Firestore)
- `users/{uid}`
- `vehicles/{vehicleId}`
- `personnel/{personnelId}`
- `centers/{centerId}` (`capacityPerSession` seans başına hasta kapasitesi)
- `slotAvailability/{slotId}` (merkez/tarih/seans için yalnızca toplam `booked`; kişisel veri yok)
- `transportRequests/{requestId}` (`centerCapacity` talep anındaki kapasite anlık görüntüsü)
- `transportRequests/{requestId}/history/{eventId}`
- `delayReports/{requestId}` (hasta gecikme gerekçesi; yalnızca hasta ve koordinatör)
- `driverDelayReports/{requestId}` (görevli gecikme gerekçesi; yalnızca görevli ve koordinatör)
- `notifications/{notificationId}`
- `transportRequests` içinde ETA: `etaBaseMinutes`, `etaMinutes`, `etaTarget`,
  `etaUpdatedAt`, `etaSource`; dönüş için `returnStartedAt`.
- Dönüş planı alanları: `sessionDurationMinutes`, `returnBufferMinutes`,
  `centerArrivedAt`, `returnPlannedAt`, `returnPlanStatus`, onay bilgileri.
- Görevli gecikmesi hedefi: `driverDelayTarget` (hangi bacak ETA’sına ait).

## Kararlar

- **Kotlin + Android Studio** kullanılıyor (ilk tercih).
- **Firebase** gerçek veritabanı ve kimlik doğrulama için kullanılıyor.
- **Google Maps/Places kaldırıldı**: faturalandırma gerektirdiği için ücretsiz
  OpenStreetMap/Nominatim kullanılıyor.
- Araç ekleme/düzenleme ekranı sonraki sürüme bırakıldı; araçlar Firestore'da
  ve örnek listeyle geliyor.
- GPS ile canlı konum takibi sonraki faz; ETA şimdilik bilinen merkez/ev
  koordinatları ve OSRM ile hesaplanıyor.
- Bildirimler MVP'de **uygulama içi + yerel cihaz bildirimi**; FCM sonraki faz.
- Kapasite kontrolü ayrı `slotAvailability` sayaçlarıyla yapılır; hasta başka
  hastaların taleplerini okuyamadığı için doğrudan talep sayımı mümkün değildir.
  Sayaç `booked` alanı yalnızca toplam sayıyı taşır; transaction ile atomik artar.
  Tam çizelgeleme (slot kilitleme, araç optimizasyonu) MVP dışıdır.
- Öncelik kendiliğinden verilmez; koordinatör resmi belge kontrolüyle doğrular.
  Belge, tanı veya rapor içeriği saklanmaz.
- Gecikme gerekçesi talep belgesinde tutulmaz: Firestore alan düzeyinde gizlilik
  desteklemediği için gerekçe ayrı `delayReports` koleksiyonunda tutulur ve
  kurallarla yalnızca koordinatöre açılır.
- Görevliye hastanın açıkça yazdığı mesaj iletilir; gizli gerekçe iletilmez.
- ETA için ücretsiz **OSRM public** kullanılıyor; API anahtarı/ücret yok.
  Canlı trafik yok; süre gerçek yol rotasına göre hesaplanır. Sağlayıcı
  `EtaProvider` arayüzüyle tek noktadan değiştirilebilir.
- Kalıcı manuel ETA yok; OSRM başarısızsa “hesaplanamadı” gösterilir.
- Görevli gecikmesi ve yoldaki hasta gecikmesi bilgi amaçlıdır; koordinatör onayı
  yalnızca araç yola çıkmadan randevu ertelemesi ve öncelik/dönüş planı için gerekir.
- Dönüş planı otomatik hesaplanır; koordinatör uygun araç/görevliyi onaylar.
  Aktif görevli listesinde iptal/biten işler gizlenir.
- Gerçek hasta verisi kullanılmıyor; demo hesaplar kurgusal.

## Sıradaki işler

- [x] Koordinatör paneli: talep listesi, atama, uyarılar.
- [x] Hasta “Hazırım / Gecikeceğim” bildirimi.
- [x] Görevli rolü, personel seçimi ve durum zinciri.
- [x] Yoldaki hasta gecikmesi bilgi akışı; yola çıkmadan onay/erteleme.
- [x] Hasta iptali ve refakatçi bilgileri.
- [x] Uygulama içi/yerel bildirimler ve öncelik doğrulama.
- [x] Gecikme süresi, gizli gerekçe, koordinatör onayıyla randevu erteleme ve
      görevliye mesaj iletme.
- [x] Görevli/araç gecikmesi, gizli gerekçe ve onaysız bilgi akışı.
- [x] OSRM tabanlı gerçek rota ETA’sı; hasta/koordinatör/görevli ekranları.
- [x] Dönüş yolculuğunu başlatma ve dönüş ETA’sı.
- [x] Düşük değerli onayların kaldırılması; görevli listesi temizliği.
- [x] Kalıcı “hasta hazır değil” uyarısının kaldırılması.
- [x] Seans süresi + otomatik dönüş planı ve koordinatör onayı.
- [x] Demo/test verilerinin temizliği.
- [x] `firestore.rules` dosyasını Firebase'e yükleme ve test modunu kapatma.
- [x] Seans doluluk kontrolü ve otomatik alternatif önerisi.
- [x] Koordinatör merkez kapasitesi girişi.
- [x] Hasta yakını görünümü, davet kodu ve bağlantı.
- [x] Durum geçmişi ekranı.
- [x] Araç/personel ekleme-düzenleme ve aktif/pasif yönetimi.
- [x] Otomatik oturum.
- [x] Pilot dosyası (`PILOT.md`).
- [x] Google ve telefon giriş akışları (kod; telefon SMS testi kullanıcıda).
- [x] Haritadan adres seçimi ve tek seferlik konum.
- [ ] FCM/push (uygulama kapalıyken bildirim).
- [ ] Haritadan adres seçimi (OpenStreetMap/osmdroid).
- [ ] Araç ekleme/düzenleme ve personel yönetimi ekranı.
- [ ] Otomatik oturum yükleme.
- [ ] Gerçek FCM bildirimleri (uygulama kapalıyken).
- [ ] GPS ile konum güncellemesi ve ETA’nın sürüş sırasında yenilenmesi.
- [ ] (İsteğe bağlı) canlı trafik sağlayıcısı; ücretsiz/ücretli seçenekler.
- [ ] Sunum provası.

## Sunum için notlar

- Veri katmanı gerçek: Firebase Auth + Firestore; uygulama kapansa da veriler
  kalır, Firebase Console'dan görülebilir.
- Dört rol ve rol bazlı ekranlar.
- Durum zinciri: Talep oluşturuldu → Planlandı → Hastaya gidiliyor →
  Hasta alındı → Merkeze ulaştı → Eve bırakıldı.
- Güvenlik: rol bazlı Firestore kuralları, veri minimizasyonu, maskeleme,
  belge/tanı saklamama; gecikme gerekçesi ayrı koleksiyonda ve yalnızca
  koordinatöre açık.
- Gecikme yönetimi: hasta süre + gerekçe + mesaj bildirir; koordinatör onaylar;
  araç yola çıkmadıysa seans/alınma saati otomatik ertelenir; görevli gizli
  gerekçeyi görmeden yeni saat ve mesajla bilgilendirilir.
- Görevli gecikmesi: süre + gizli gerekçe; anında bilgi, onay yok; hastaya
  yalnızca ETA gider.
- ETA: OSRM ile gerçek rota süresi; merkez ↔ hasta evi; dönüş ayağı ayrı
  “Dönüşe başla” eylemiyle. Canlı trafik/GPS sonraki faz; ücretsiz sürüm.
- Onay sadeleştirmesi: yoldaki hasta gecikmesi ve görevli gecikmesi bilgi amaçlı;
  koordinatör yalnızca randevu erteleme, öncelik ve dönüş planında karar verir.
- Otomatik dönüş planı: 4 saat + 15 dk varsayılan; koordinatör uygun araç/görevliyi
  onaylar; uygun araç yoksa hasta “hazırlanıyor” görür, mağdur edilmez.
- Merkez arama: ücretsiz OpenStreetMap; API anahtarı/faturalandırma yok.
- Kapsam sınırı: canlı GPS yok; gerçek hasta verisi yok.

## Çalıştırma

1. Android Studio'da `C:\Users\Pigasor\Desktop\Mobilprogramlama_diyaliz` aç.
2. Gradle senkronizasyonu bitince telefonu USB ile bağla.
3. Run veya `.\gradlew.bat assembleDebug`.
