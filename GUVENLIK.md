# Güvenlik ve Kişisel Veri Koruma

Bu dosya, Diyaliz Ulaşım prototipinde kişisel verilerin nasıl korunduğunu ve
hangi güvenlik kurallarının uygulandığını açıklar. Kod yazarken bu kurallara
uyulur; teslim öncesi kontrol listesiyle doğrulanır.

## 1. Amaç ve ilke

- Uygulama yalnızca **ulaşım planlaması** için gereken en az veriyi toplar.
- Teşhis, tahlil, ilaç, sağlık raporu gibi tıbbi veriler **toplanmaz**.
- Ders demosunda gerçek hasta verisi kullanılmaz; gerçek diyaliz merkezi
  adları/adresleri kullanılabilir, hasta kimlikleri kurgusaldır.
- Veri minimizasyonu, amaçla sınırlılık, ölçülü saklama ve rol bazlı erişim
  temel ilkelerdir.

## 2. Toplanan veriler

| Veri | Neden | Zorunlu mu? |
|---|---|---|
| Ad soyad | Talebi kimin oluşturduğunu göstermek | Evet |
| E-posta | Firebase Authentication girişi | Evet |
| Telefon | Koordinatörün hastaya ulaşması | Hayır |
| Alınma adresi | Ulaşım planı | Evet |
| Seans tarihi/saati, merkez | Ulaşım planı | Evet |
| Refakat/tekerlekli sandalye ihtiyacı | Uygun araç seçimi | Evet |
| Refakatçi adı, telefonu, yakınlığı | Refakatçi gerektiğinde iletişim | Refakat varsa evet |
| Öncelik kategorisi | Ulaşım sıralaması | Hayır |
| Öncelik doğrulama bilgisi/notu | Doğrulamanın kim tarafından yapıldığı | Hayır |
| Bildirim kaydı (başlık, mesaj, hedef) | Durumdan haberdar etme | Sistem üretir |
| Gecikme süresi | Onaylı randevu ertelemesi | Gecikme bildirilirse evet |
| Gecikme gerekçesi | Koordinatörün onay kararı | Gecikme bildirilirse evet; yalnızca koordinatör görür |
| Görevliye mesaj | Onay sonrası görevliyi bilgilendirme | Hayır |
| Görevli gecikme gerekçesi | Araç gecikmesinin nedeni | Gecikme bildirilirse evet; hasta görmez |
| ETA (tahmini varış) | Yolculuk bilgisi | OSRM hesaplarsa evet; konum geçmişi tutulmaz |
| Tahmini seans süresi | Dönüş planı hesabı | Evet; klinik/hasta tahmini |
| Dönüş planı saati/aracı | Geri dönüş güvenliği | Round-trip taleplerde evet |
| Not | Ek ulaşım bilgisi | Hayır |

Toplanmayanlar: TC kimlik numarası, tanı, tahlil sonuçları, ilaç listesi,
sağlık raporu içeriği/belge görüntüsü, gerçek hasta kimliği. Öncelik
doğrulamasında yalnızca “resmi belge kontrol edildi” bilgisi ve kısa not
tutulur; belge veya tanı sisteme yüklenmez.

Gecikme gerekçesi sağlık veya özel yaşam bilgisi içerebileceği için talep
belgesine yazılmaz. Talep belgesini görevli okuyabildiğinden gerekçe ayrı
`delayReports` koleksiyonunda tutulur; bu koleksiyonu yalnızca koordinatör ve
raporu yazan hasta okuyabilir. Görevliye yalnızca hastanın açıkça yazdığı
“görevliye mesaj” ve onaylı yeni saat iletilir.

Görevli/araç gecikmesinin gerekçesi de ayrı `driverDelayReports` koleksiyonunda
tutulur; yalnızca koordinatör ve ilgili görevli okuyabilir. Hastaya gerekçe
değil, yalnızca “Tahmini varış: X dk” gösterilir. ETA için OSRM’e yalnızca
başlangıç/varış koordinatları gönderilir; sürekli konum geçmişi saklanmaz.

## 3. KVKK ve etik sınırlar

- Gerçek hasta verisi kullanılacaksa etik kurul/okul onayı, merkez izni,
  anonimleştirme ve KVKK şartları gerekir; bu proje kapsamında gerçek veri
  kullanılmıyor.
- Firebase/Google Cloud veriyi yurt dışında saklayabilir; sağlık verisi için
  ek aktarım onayı gerekebilir. Bu nedenle gerçek veri Firebase'e yüklenmez.
- Demo verisinde gerçek merkez adları kullanılabilir; hasta adı/adresi/iletişim
  bilgisi kurgusaldır.

## 4. Roller ve erişim

- **Hasta:** Yalnızca kendi taleplerini, kendi gecikme raporunu ve kendisine
  gösterilen ETA’yı okur/yazar; görevli gecikme gerekçesini göremez.
- **Koordinatör:** Tüm talepleri, hasta/görevli gecikme gerekçelerini ve ETA’yı
  okur; araç/görevli atar, gecikme/öncelik onayı verir, uygun aşamada randevuyu
  erteler.
- **Görevli:** Yalnızca kendi `personnelId` değerine atanmış talepleri okur;
  durum zincirini ilerletir, kendi gecikmesini bildirir, ETA ve dönüş başlatma
  alanlarını günceller; hastanın gizli gerekçesini okuyamaz.
- **Hasta yakını:** Yalnızca bağlı olduğu hastanın taleplerini okur; yazamaz.

Ek kurallar:

- Kullanıcı kendini **koordinatör** yapamaz. Koordinatör hesabı Firebase
  Console'dan yönetici tarafından oluşturulur; kayıt ekranında yalnızca Hasta
  ve Hasta yakını rolleri seçilebilir.
- Rol değişikliği kullanıcı tarafından yapılamaz.
- Hasta yakını bağlantısı hasta tarafından üretilen davet koduyla kurulur ve
  hasta tarafından iptal edilebilir.

## 5. Firestore güvenlik kuralları

`firestore.rules` dosyası sunucu tarafında şunları zorlar:

- Kimliği doğrulanmamış kullanıcı hiçbir veriyi okuyamaz/yazamaz.
- Hasta yalnızca kendi talebini oluşturur; durum/atama alanlarını değiştiremez.
- Hasta talebini yalnızca belirli alanlarda düzenleyebilir; iptal edebilir.
- Hasta yalnızca kendi hazırlık durumunu (`readiness`), gecikme süresini ve
  iptal alanlarını güncelleyebilir; gecikme onayı bayrağını, erteleme
  bilgilerini veya görevliye mesaj alanını kendisi açamaz/yazamaz.
- Gecikme gerekçesi ayrı `delayReports/{requestId}` belgesinde tutulur. Hasta
  yalnızca kendi talebi için rapor oluşturabilir/güncelleyebilir; koordinatör
  tüm raporları okur; görevli ve hasta yakını okuyamaz.
- Koordinatör onayı yalnızca araç yola çıkmadan randevu ertelemesi için gerekir;
  araç yoldayken hasta gecikmesi bilgi amaçlıdır, alımı kilitlemez.
- Görevli gecikme gerekçesi `driverDelayReports` içinde; görevli kendi raporunu
  yazar/okur, koordinatör tümünü okur, hasta ve yakın okuyamaz. Görevli gecikmesi
  bilgi amaçlıdır; koordinatör onayı gerekmez.
- Görevli yalnızca kendi ETA alanlarını (OSRM sonucu), bacak temizliğini ve
  onaylı dönüş başlangıcını günceller; hasta/koordinatör bu alanları yalnızca okur.
- Koordinatör talep üzerinde atama, randevu erteleme, öncelik doğrulama ve
  otomatik dönüş planı onayı yapabilir; hastanın kimlik alanlarını değiştiremez.
- Görevli yalnızca kendisine atanmış talebin durumunu bir sonraki adıma
  taşıyabilir; adım atlayamaz. Yoldaki hasta gecikmesi alımı kilitlemez; saha
  onayı istemci tarafındadır.
- Dönüş planı onaylanmadan görevli “Dönüşe başla”/“Eve bırakıldı” yapamaz.
- Görevli yalnızca kendi personel kaydını okur; personel listesini koordinatör
  yönetir.
- Hasta yakını yalnızca bağlı hastanın belgesini okuyabilir.
- Bildirimleri yalnızca hedef kullanıcı/rol okur; yalnızca `read` alanı
  değiştirilebilir. Bildirim üretimi ilgili talebin taraflarıyla sınırlıdır.
- Öncelik doğrulama alanları yalnızca koordinatör tarafından yazılabilir;
  hasta kendine doğrulanmış öncelik veremez.
- Geçmiş kayıtları yalnızca eklenebilir; sonradan değiştirilemez/silinemez.
- Araç kayıtlarını yalnızca koordinatör yazabilir.
- Seans doluluk sayaçları `slotAvailability/{slotId}` yalnızca merkez, tarih,
  seans ve toplam `booked` sayısını tutar; kişisel veri içermez. Giriş yapan
  herkes okuyabilir; rezervasyonda +1, iptalde -1 yapılabilir. Sayaç yoksa 1 ile
  oluşturulur; değer 0-50 aralığında ve tek adımda değişebilir. Amaç eşzamanlı
  iki talebin son kontenjanı aşmasını transaction ile önlemektir; kapasite
  kontrolü iş kuralı olarak istemcide uygulanır.
- Merkez kapasitesi `centers/{centerId}.capacityPerSession` alanında tutulur;
  yalnızca koordinatör yazar, tüm giriş yapanlar okur.
- Hasta tarih/saat/merkez alanlarını sonradan değiştiremez; kapasite sayacını
  atlatmamak için değişiklik yalnızca iptal + yeni taleple yapılır.
- Hasta yakını daveti `relativeInvites/{code}`: hastanın kendi ürettiği, SecureRandom ile
  üretilmiş 12 karakterlik (≈2^60 ihtimal), tek kullanımlık ve 7 günlük koddur.
  Kodu yalnızca hasta ve yakın rolündeki hesaplar bilinerek okuyabilir; liste
  sorgusu kapalıdır.
- `slotAvailability` sayaçları yalnızca hasta ve koordinatör rolleri tarafından
  ±1 adımla değiştirilebilir; profili olmayan, görevli veya yakın hesaplar yazamaz.
- `relativeLinks/{relativeUid}` yalnızca geçerli/aktif davetle ve ilgili yakın
  hesabı tarafından oluşturulur; sonradan değiştirilemez/silinemez.
- Yakının `linkedPatientId` alanı yalnızca `relativeLinks` belgesindeki hastayla
  eşleşiyorsa güncellenebilir; aksi halde başka hastanın verisi okunamaz.
- Hiçbir koleksiyonda istemciden silme yapılamaz (iptal, durum değişikliğidir).

Firestore kuralları, istemci kodundan bağımsız olarak uygulanır; arayüzde
gizlenen bir düğme güvenlik sağlamaz, asıl güvence kurallardır.

## 6. Kimlik doğrulama

- Firebase Authentication, e-posta/şifre yöntemi.
- Şifre en az 8 karakter, harf ve rakam içermeli.
- Firebase parolaları kendi altyapısında hash'ler; uygulama şifreyi saklamaz.
- E-posta doğrulama ve parola sıfırlama sonraki adımda eklenebilir.
- Başarısız giriş denemeleri için Firebase'in yerleşik sınırlamaları kullanılır.
- Google girişi yalnızca Firebase ID token'ı ile yapılır; şifre uygulamaya girmez.
- Telefon girişi Firebase Phone Auth ile yapılır; SMS kodu uygulamada saklanmaz.
- Google/telefonla ilk girişte kullanıcı **hasta** rolüyle profil oluşturur;
  koordinatör/görevli rolleri yalnızca yönetici tarafından atanır.
- Harita ve ters adres çevirme OpenStreetMap/Nominatim'e koordinat gönderir;
  gerçek hasta verisiyle pilot öncesi bu dış servis ve veri minimizasyonu
  değerlendirilmelidir. Harita karoları da OSM sunucusundan yüklenir.

## 7. Şifreleme, sırlar ve yapılandırma

- Tüm trafik HTTPS/TLS üzerinden gider.
- Firestore verisi Google tarafından diskte şifrelenir.
- `google-services.json` ve API anahtarları depoya commit edilmez; `.gitignore`
  içinde tutulur.
- Google Maps API anahtarı Android paketi + SHA-1 ile kısıtlanır; genel amaçlı
  anahtar kullanılmaz.
- Kodda sabit (hard-coded) şifre, token veya kişisel veri bulunmaz.

## 8. Günlükler ve bildirimler

- Loglara ad, adres, telefon veya sağlık bilgisi yazılmaz.
- Bildirim metinlerinde tam ad yerine maskeli ad kullanılır (`A*** Y***`);
  refakatçi telefonu ve adres bildirimlere yazılmaz.
- Gecikme gerekçesi hiçbir bildirim metnine yazılmaz; koordinatör gerekçeyi
  yalnızca panelde görür. Görevliye onay sonrası süre, yeni saat ve hastanın
  açıkça yazdığı mesaj iletilir.
- Görevli gecikmesinde hastaya yalnızca “Tahmini varış: X dk” gider; neden ve
  görevli kimliği bildirim metnine yazılmaz.
- Kilit ekranında görünecek bildirimlerde mümkün olduğunca az bilgi gösterilir.
- Bildirimler uygulama içi merkezde ve cihaz bildirimi olarak gösterilir;
  uygulama tamamen kapalıyken FCM olmadığı için yeni bildirim gelmez (bilinen
  sınır).
- Hata mesajları kullanıcıya teknik ayrıntı sızdırmadan gösterilir.

## 9. Cihaz güvenliği

- Hassas ekranlarda ekran görüntüsü engelleme (`FLAG_SECURE`) sonraki adımda
  eklenebilir.
- Uygulama yalnızca gerekli izinleri ister: bildirim ve (harita fazında) konum.
- Arka planda sürekli konum takibi yapılmaz.
- Telefonun ekran kilidi ve işletim sistemi güncellemeleri kullanıcı
  sorumluluğundadır.

## 10. Veri saklama ve silme

- Talepler silinmez; `CANCELLED` durumuna alınır ve geçmiş korunur.
- Gizli gecikme raporu talebe bağlı olarak saklanır; talep silinmediği için
  rapor da kalır.
- Demo verileri teslim sonrası Firebase Console'dan temizlenebilir.
- Hesap silme talebi için ileride bir akış eklenebilir; şimdilik yönetici
  tarafından Console'dan yapılır.

## 11. Geliştirme ve test kuralları

- `firestore.rules` **2026-10-27'de canlıya yüklendi**; test modu kapalı.
- Kurallar Firestore Emulator'da 88 senaryoyla test edildi (88/88 geçti;
  onaysız yoldaki gecikmeler, hasta/görevli gizli raporları, ETA alanları, bacak
  temizliği, dönüş planı/başlatma, atomik batch'ler ve doluluk sayaçları dahil).
- Canlı yetki testleri: 106/106 (63 okuma + 43 yazma/durum); oturumsuz erişim
  reddedildi, roller arası izolasyon doğrulandı.
- Demo hesapları kurgusal adlarla oluşturulur.
- Gerçek hasta verisiyle test yapılmaz.
- Ekran görüntüleri ve sunum dosyalarında kişisel veri bulunmaz.

## 12. Teslim öncesi kontrol listesi

- [x] `firestore.rules` Firebase'e yüklendi, test modu kapalı.
- [ ] Sadece Hasta ve Hasta yakını kendi kendine kayıt olabiliyor.
- [ ] Koordinatör hesapları Console'dan oluşturulmuş.
- [ ] `google-services.json` ve API anahtarları `.gitignore` içinde.
- [ ] Loglarda kişisel veri yok.
- [ ] Bildirimlerde maskeli ad kullanılıyor; adres/refakatçi telefonu yok.
- [ ] Öncelik doğrulamasında belge/tanı saklanmıyor; yalnızca doğrulama notu var.
- [ ] Gecikme gerekçesi talep belgesinde/bildirimde yok; yalnızca koordinatör
      `delayReports` üzerinden okuyabiliyor.
- [ ] Görevli gecikme gerekçesi hastaya gitmiyor; `driverDelayReports` yalnızca
      koordinatör ve ilgili görevliye açık.
- [ ] ETA için konum geçmişi saklanmıyor; yalnızca son süre/hedef tutuluyor.
- [ ] Dönüş planı onaylanmadan görevli dönüşe başlayamıyor; uygun olmayan araç
      istemcide engelleniyor (kural düzeyinde kapasite kontrolü sınırı var).
- [ ] Görevli yalnızca kendi personel kaydını ve atanmış işlerini okuyabiliyor.
- [ ] Demo verileri kurgusal; gerçek merkez adları dışında gerçek veri yok.
- [ ] Gerçek hasta verisi kullanılmadı.
