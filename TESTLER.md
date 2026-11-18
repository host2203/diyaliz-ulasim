# Test Planı

Bu dosya, Diyaliz Ulaşım prototipinde denenmesi gereken akışları, sınır
durumlarını ve bilinen riskleri listeler. Test sırasında beklenen sonuç ile
gerçek sonucu not et.

## 0. Test ortamı

- Fiziksel telefon: Samsung Galaxy S8+ (Android 9).
- Hesaplar: hasta, hasta yakını, koordinatör.
- Tek telefonda rol değiştirmek için **Çıkış** kullan.
- İki cihaz varsa gerçek zamanlı senkronizasyon daha iyi test edilir.
- `firestore.rules` canlıya yüklendi (2026-10-27); test modu kapalı.
  Emülatörde 88/88, canlı yetki testlerinde 106/106 geçti.

## 1. Öncelikli (P0) testler

### 1.1 Giriş ve roller
- [ ] Koordinatör doğru e-posta/şifre ile giriş yapar, koordinatör paneli açılır.
- [ ] Hasta hesabıyla giriş yapılır, hasta paneli açılır.
- [ ] Hasta yakını hesabıyla giriş yapılır, yakın ekranı açılır.
- [ ] Yanlış şifre → anlaşılır hata, uygulama çökmez.
- [ ] Kayıtlı olmayan e-posta → anlaşılır hata.
- [ ] Aynı e-posta ile ikinci kayıt → “zaten kayıtlı” hatası.
- [ ] Kayıt formunda koordinatör rolü seçilemez (güvenlik).
- [ ] Çıkış yap → giriş ekranı; geri tuşu panele dönmez.
- [ ] Uygulamayı kapat/aç → giriş ekranı gelir (oturum otomatik yüklenmiyor;
      bu bilinen bir eksik).
- [ ] Koordinatör olmayan hesapla koordinatör ekranına erişim engellenir.

### 1.2 Hasta talebi
- [ ] Geçerli gelecek tarih + saat ile talep oluşturulur.
- [ ] Talep hasta panelinde anında görünür.
- [ ] Firestore `transportRequests` içinde belge oluşur.
- [ ] Geçmiş tarih → kayıt engellenir, açıklayıcı uyarı çıkar.
- [ ] Geçersiz tarih (31 Kasım) → “Kasım ayı 30 gündür” uyarısı.
- [ ] Geçersiz saat (24:00, 12:60) → açıklayıcı uyarı.
- [ ] İl/ilçe seçilmeden kayıt → “İl/İlçe seçin” uyarısı.
- [ ] Merkez seçilmeden/elle girilmeden kayıt → uyarı.
- [ ] Boş adres → uyarı.
- [ ] Çok uzun adres/not → sınır uyarısı.
- [ ] Yeni talepte “Hazırım” → koordinatör panelinde yeşil “Hazırım”.
- [ ] “Gecikeceğim” → koordinatör panelinde “Gecikeceğim” ve gecikme bilgisi.
- [ ] Hazırlık bildirimi geçmiş kaydı oluşturur.

### 1.3 Koordinatör paneli
- [ ] Tüm talepler listelenir.
- [ ] Araç listesi Firestore `vehicles` koleksiyonundan gelir.
- [ ] Araç yoksa “Kayıtlı araç yok” uyarısı çıkar.
- [ ] Araç + görevli + geçerli saatle atama kaydedilir.
- [ ] Atama sonrası durum **Planlandı** olur.
- [ ] Aynı talebe yeniden atama yapılabilir.
- [ ] Tekerlekli sandalye gerektiren talebe uygun olmayan araç seçilince uyarı.
- [ ] Refakatçi + hasta kapasiteyi aşınca uyarı.
- [ ] Aynı araç/görevli aynı saatte başka talebe atanınca çakışma uyarısı.
- [ ] Durum zinciri sırayla ilerletilir.
- [ ] Seansa 24 saatten az kala atanmamış talep uyarı gösterir.
- [ ] Araç yoldayken “Hazırlık: Bildirmedi/Gecikeceğim” satırı görünür; kalıcı
      kırmızı “hasta hazır değil” uyarısı gösterilmez.
- [ ] Aynı talebe hızlı iki kez “Durumu ilerlet” basılınca iki adım atlamıyor mu?
- [ ] İki koordinatör aynı anda aynı araca atama yaparsa ne oluyor?
- [ ] İptal/tamamlanmış talebe atama veya durum ilerletme yapılamıyor mu?

### 1.4 Gerçek zamanlı senkronizasyon
- [ ] Hasta talep oluşturunca koordinatör ekranı yenilenmeden görür.
- [ ] Koordinatör atama yapınca hasta ekranı anında güncellenir.
- [ ] Durum değişikliği hasta ekranına anında yansır.
- [ ] Hasta “Hazırım” deyince koordinatör ekranı anında güncellenir.

### 1.5 Görevli, iptal, bildirim ve öncelik
- [ ] Görevli yalnızca kendi `personnelId` değerine atanmış talepleri görür.
- [ ] Koordinatör görevliyi listeden seçer; elle ad yazılamaz.
- [ ] Görevli durum zincirini ilerletir; koordinatörde durum düğmesi yoktur.
- [ ] Hasta yola çıkmadan “Gecikeceğim” derse koordinatör onayı/erteleme gerekir.
- [ ] Hasta araç yoldayken “Gecikeceğim” derse görevli saha onayıyla “Hasta alındı”
      yapabilir; koordinatör onayı gerekmez.
- [ ] “Gecikeceğim” diyaloğu 5-180 dk süre ve en az 3 karakter gerekçe olmadan
      gönderilemez.
- [ ] Gecikme gerekçesi yalnızca koordinatör kartında ve onay diyaloğunda görünür.
- [ ] Gecikme gerekçesi talep belgesinde ve görevli kartında/notification'da yoktur.
- [ ] Araç yola çıkmamışken hasta gecikmesi yalnızca koordinatöre gider; görevli
      onay sonrası bilgilendirilir.
- [ ] Araç yoldayken hasta gecikmesi görevliye ve koordinatöre **anında** gider;
      onay/buton kilidi yoktur. Görevli bir kerelik “Hasta gerçekten araçta mı?”
      onayıyla ilerler.
- [ ] Araç yola çıkmamışken hasta gecikmesi koordinatör onayıyla seans/alınma
      saatini gecikme kadar erteler (örn. 09:30 → 09:50, 09:00 → 09:20).
- [ ] Onay diyaloğu eski → yeni saati ve hastanın görevliye mesajını önizler.
- [ ] Görevli kartı ve notification'ı yeni saati ve hastanın mesajını gösterir;
      gizli gerekçeyi göstermez.
- [ ] Görevli “Gecikeceğim” diyaloğu 5-180 dk + en az 3 karakter gerekçe ister.
- [ ] Görevli gecikmesi **her durumda** anında koordinatör + hastaya gider;
      onay gerekmez; hastaya yalnızca ETA, koordinatöre gerekçe görünür.
- [ ] Görevli gecikmesi yalnızca ilgili bacak ETA’sına eklenir; “Hasta alındı”da
      temizlenir, merkez ETA’sına taşınmaz.
- [ ] Görevli kartında iptal/biten işler görünmez; gidiş-dönüşte “Dönüş bekliyor”
      kartı dönüş tamamlanana kadar kalır.
- [ ] “Hastaya gidiliyor”da merkez → ev, “Hasta alındı”da ev → merkez,
      “Dönüşe başla”da merkez → ev ETA’sı OSRM’den gelir.
- [ ] ETA hasta/koordinatör/görevli kartında randevu bilgisinin altında görünür.
- [ ] Görevli gecikmesi varsa ETA’ya eklenir (örn. 8 dk rota + 15 dk = 23 dk).
- [ ] OSRM yanıt vermezse “hesaplanamadı” gösterilir; manuel ETA istenmez.
- [ ] Talep formunda tahmini seans süresi (60-720 dk, varsayılan 240) alınır.
- [ ] Hasta merkeze bırakılınca dönüş saati = varış + seans süresi + 15 dk tampon.
- [ ] Dönüş planı koordinatöre “onay bekliyor” olarak düşer; hastaya
      “Dönüş planı hazırlanıyor” denir.
- [ ] Koordinatör uygun araç/görevliyi seçip onaylar; tekerlekli sandalye/kapasite
      uygun değilse onay düğmesi kapalıdır.
- [ ] Onaylanınca hasta ve görevli bilgilendirilir; dönüş ETA’sı “Dönüşe başla”da
      hesaplanır.
- [ ] Dönüş planı onaylanmadan “Dönüşe başla” ve “Eve bırakıldı” görünmez.
- [ ] Uygun araç yoksa plan “onay bekliyor” kalır; hastaya “araç yok” denmez.
- [ ] Hasta hazırlık bildirmediyse “Hasta alındı” için bir kerelik onay sorulur.
- [ ] Hasta talebini iptal eder; koordinatör ve görevli bildirim alır.
- [ ] Hasta yoldaki/tamamlanmış talebi iptal edemez.
- [ ] Refakatçi açıkken ad, telefon, yakınlık zorunlu; hatalı telefon reddedilir.
- [ ] Refakatçi bilgisi koordinatör ve görevli kartında görünür.
- [ ] Öncelik seçilen talep “doğrulanmadı” olarak görünür.
- [ ] Koordinatör belge kontrolü onayı olmadan önceliği doğrulayamaz.
- [ ] Doğrulanmış öncelik aynı gün içinde listede üste taşınır.
- [ ] Her olayda doğru role bildirim gider; okunmamış sayacı güncellenir.
- [ ] Cihaz bildirimi uygulama açıkken yeni olayda görünür.
- [ ] Bildirim metninde tam ad yerine maskeli ad kullanılır.

## 2. Tarih/saat sınır testleri

- [ ] `29022028` → geçerli (artık yıl).
- [ ] `29022027` → geçersiz.
- [ ] `29022000` → geçerli (400’e bölünen yüzyıl).
- [ ] `29021900` → geçersiz (100’e bölünen, 400’e bölünmeyen).
- [ ] `31042026` → geçersiz (Nisan 30 gün).
- [ ] `31112026` → geçersiz (Kasım 30 gün).
- [ ] `31122026` → geçerli.
- [ ] `00012026` → geçersiz gün.
- [ ] `01132026` → geçersiz ay.
- [ ] `01011899` → geçersiz yıl.
- [ ] Saat `0000` → geçerli.
- [ ] Saat `2359` → geçerli.
- [ ] Saat `2400` → geçersiz.
- [ ] Saat `1260` → geçersiz.
- [ ] Bugünün ileriki saati → geçerli; bugünün geçmiş saati → geçersiz.
- [ ] Tarih alanına noktalı yapıştırma (`05.11.2026`) → doğru ayrıştırma.
- [ ] Tarih alanında silme/yeniden yazma → imleç doğru yerde.
- [ ] Saat alanında 3 rakam/4 rakam/geri silme → doğru gösterim.

## 3. Hata senaryoları

### 3.1 Ağ ve servisler
- [ ] Uçak modunda giriş → bağlantı hatası, çökme yok.
- [ ] Uçak modunda talep kaydetme → hata mesajı.
- [ ] İnternet yavaşken çift kayıt → iki belge oluşuyor mu?
- [ ] OpenStreetMap/Nominatim yavaş/yanıt vermiyor → hata mesajı, elle giriş.
- [ ] Aynı anda çok il/ilçe değiştirme → Nominatim 429/rate-limit oluyor mu?
- [ ] Firestore kuralları yayınlandıktan sonra izinsiz okuma/yazma → engellenir.

### 3.2 Firebase / veri
- [ ] Kullanıcı profil belgesi silinmiş → girişte anlaşılır hata.
- [ ] `role` alanı yanlış/eksik → girişte hata, çökme yok.
- [ ] `vehicles` koleksiyonu boş → atama yapılamıyor, uyarı var.
- [ ] `transportRequests` alanları eksik/yanlış tipte → uygulama çökmüyor.
- [ ] Firestore test modu süresi dolmuş → erişim reddi, anlaşılır hata.
- [ ] Koordinatör olmayan kullanıcı doğrudan Firestore’dan veri yazamıyor
      (kurallar yayınlandıktan sonra).

## 4. Güvenlik / yetki testleri (kurallar yayınlandıktan sonra)

- [ ] Hasta A, Hasta B’nin talebini okuyamıyor.
- [ ] Hasta kendi talebinin durumunu/atamasını değiştiremiyor.
- [ ] Hasta yalnızca `readiness` alanını güncelleyebiliyor.
- [ ] Hasta kendi talebini iptal edebiliyor; başka alanı değiştiremiyor.
- [ ] Koordinatör atama/durum güncelleyebiliyor.
- [ ] Yakın, yalnızca bağlı olduğu hastanın talebini okuyabiliyor.
- [ ] Yakın yazma yapamıyor.
- [ ] Kullanıcı kendi rolünü yükseltemiyor (koordinatör olamıyor).
- [ ] Geçmiş kayıtları güncellenemiyor/silinemiyor.
- [ ] Hiçbir koleksiyonda silme yapılamıyor.
- [ ] Firestore Rules Playground’da yukarıdaki senaryolar deneniyor.

## 5. UI / cihaz testleri

- [ ] Ekran döndürme: form alanları korunuyor mu?
- [ ] Karanlık modda okunabilirlik.
- [ ] Küçük ekran/modern telefonlarda taşma.
- [ ] Klavye açılınca alanlar gizleniyor mu?
- [ ] Çok uzun hasta adı/adres → kart taşması.
- [ ] Türkçe karakterler (ğ, ş, ı, İ) ve emoji.
- [ ] Uygulama arka plandan dönünce veri güncel mi?
- [ ] Telefon ekranı kapanıp açılınca oturum/veri.
- [ ] Geri tuşu: formdan panele, panelden çıkışa.
- [ ] Çıkış sonrası geri tuşu oturum açmıyor.
- [ ] Pil optimizasyonu (bildirimler eklendiğinde).

## 6. Veri tutarlılığı / eşzamanlılık

- [ ] Çift dokunuşla iki talep oluşuyor mu?
- [ ] İki koordinatör aynı talebi aynı anda güncelliyor → son yazan kazanıyor;
      çakışma tespiti yeterli mi?
- [ ] Aynı araç iki talebe atanırsa uyarı çıkıyor; yine de kaydedilebiliyor.
- [ ] Durum zinciri atlanabiliyor mu (Planlandı’dan doğrudan Eve bırakıldı)?
- [ ] Geçmiş kayıtları sıralı ve doğru kullanıcı adıyla mı?

## 7. Bilinen eksikler / riskler

- [ ] `firestore.rules` içinde “maximum 1000 expressions” uyarısı sürüyor;
      meşru yollar geçiyor ancak kuralların sadeleştirilmesi gerekiyor.
- [ ] Hasta talebi düzenleme ekranı yok; iptal, hazırlık ve gecikme bildirimi var.
- [ ] Gecikme gerekçesinin onay anındaki değişmez bir kopyası yok; rapor belgesi
      hasta tarafından yeniden yazılabilir (görevliye sızmaz, yalnızca koordinatör
      görür).
- [ ] Hasta yakını görünümü henüz yer tutucu.
- [ ] Durum geçmişi ekranı henüz yok (veri yazılıyor, gösterilmiyor).
- [ ] FCM yok; uygulama tamamen kapalıyken cihaz bildirimi gelmez.
- [ ] ETA, ücretsiz OSRM public demo ile hesaplanıyor; canlı trafik ve üretim
      garantisi yok. Süre, durum değişiminde bir kez hesaplanır; GPS ile sürüş
      sırasında sürekli yenilenmez.
- [ ] Merkez/ev koordinatı olmayan eski taleplerde ETA hesaplanamaz; yeni
      taleplerde seçilen merkezin koordinatı kaydedilir, elle girilen merkezde
      koordinat yoktur.
- [ ] Görevli gecikmesinde onay/red yoktur; bilgi amaçlıdır (tasarım gereği).
- [ ] Dönüş planı onayı istemci tarafında uygunluk kontrolü yapar; kural düzeyinde
      araç kapasitesi/tekerlekli uygunluğu ayrıca doğrulanmaz.
- [ ] Seans süresi tahminidir; klinik/gerçek süre değişirse dönüş planı manuel
      güncellenemez (sonraki iyileştirme).
- [ ] Bildirim okundu durumu koordinatörler arasında ortaktır (prototip sınırı).
- [ ] Haritadan adres seçimi henüz yok.
- [ ] Araç ve personel ekleme/düzenleme ekranı henüz yok.
- [ ] Uygulama açılışında otomatik oturum yükleme yok.
- [ ] OSM/Nominatim kapsaması sınırlı; bazı ilçelerde sonuç yok.
- [ ] Çakışma uyarısı istemci tarafında; atama için transaction yok (durum
      güncellemesinde transaction var).
- [ ] Öncelik sıralaması istemci tarafında; çok koordinatörlü ortamda sunucu
      tarafı sıralama/atama politikası gerekir.
- [ ] Gerçek hasta verisi kullanılmıyor; demo hesaplar kurgusal.

## 8. Hata bildirimi formatı

Bir hata bulunca şunları not et:

1. Hangi ekran ve hangi adım?
2. Beklenen davranış neydi?
3. Gerçekte ne oldu?
4. Ekran görüntüsü / hata metni.
5. Hangi hesapla (hasta/koordinatör/yakın)?
6. İnternet var mıydı?
7. Telefon saati/tarihi doğru muydu?

Bu bilgilerle hatayı hızlıca tekrar üretip düzeltebiliriz.

## 9. Test sonuçları (2026-10-25)

### Başarılı testler

- Hasta kaydı, giriş, rol yönlendirme ve çıkış.
- Talep formu: otomatik tarih/saat biçimlendirme, 31 Kasım için
  “Kasım ayı 30 gündür” uyarısı, geçmiş tarih engeli, geçerli gelecek tarih.
- İl/ilçe seçimi (Ankara/Çankaya), OpenStreetMap merkez araması, demo merkez +
  OSM sonucunun birlikte listelenmesi, adres ve ihtiyaç anahtarları.
- Talep kaydı ve hasta panelinde anlık görünme.
- “Hazırım” ve “Gecikeceğim” bildirimleri; koordinatör panelinde görünme.
- Koordinatör paneli: talep listesi, araç/görevli atama, tekerlekli sandalye
  uyumsuzluğu, kapasite yetersizliği ve araç/görevli çakışma uyarıları.
- Uyarılara rağmen atamanın kaydedilebilmesi (uyarılar engelleyici değil).
- Durum zinciri: Planlandı → Hastaya gidiliyor → Hasta alındı → Merkeze ulaştı
  → Eve bırakıldı; tamamlanınca düğmelerin kaybolması.
- 24 saat içindeki atanmamış talep uyarısı.
- Araç yoldayken “Hazırlık: Bildirmedi” satırı görünür; kalıcı kırmızı uyarı yok.
- REST ile yazılan talebin hasta ve koordinatör ekranlarında **gerçek zamanlı**
  görünmesi.
- Görevli paneli: yalnızca atanmış talepler, personel seçimi, tam durum zinciri.
- Gecikme süresi (20 dk), gizli gerekçe ve görevli mesajı; koordinatör onayıyla
  seans 09:30 → 09:50, alınma 09:00 → 09:20; görevli kartı/notification'da yeni
  saat ve mesaj, gerekçe yok.
- Araç yoldayken hasta gecikmesi: görevli + koordinatöre anında bilgi; onay ve
  buton kilidi yok; görevli saha onayıyla “Hasta alındı” yaptı.
- Görevli gecikmesi: 15 dk + gizli gerekçe; onay yok; koordinatör + hastaya
  anında bilgi; hastaya yalnızca ETA; “Hasta alındı”da gecikme temizlendi.
- OSRM ETA: PLANNED/“Hastaya gidiliyor”da 8+15=23 dk (ev); “Hasta alındı”da
  7 dk (merkez); “Dönüşe başla”da 8 dk (ev).
- Otomatik dönüş planı: 4 saat + 15 dk tampon; koordinatör aynı araç/görevliyle
  onayladı; hasta + görevli bildirimi; onaysız dönüş butonu gizli.
- “Eve bırakıldı” sonrası görevli listesi boşaldı; tamamlanan iş gizlendi.
- Hasta gecikmesi bilgi metni “bilgi verildi” olarak güncellendi; “onay bekliyor”
  yalnızca araç yola çıkmamışken gösteriliyor.
- Hazırlık bildirimi olmayan hastada “Yine de işaretle” onayı.
- Hasta iptali ve gerekçenin kartta görünmesi.
- Refakatçi ad/telefon/yakınlık bilgilerinin formda toplanması ve üç panelde
  gösterilmesi.
- Öncelik seçimi, “doğrulanmadı” durumu, koordinatör belge onayı, doğrulama
  notu ve hasta bildirimi.
- Bildirim merkezi: koordinatör (yeni talep), görevli (atama), hasta (atama ve
  durum), koordinatör/görevli (hazırlık, iptal), görevli/hasta (gecikme onayı),
  hasta (öncelik doğrulama); okunmamış sayacı ve okundu işaretleme.
- Yeni olaylarda cihaz bildirimi (Android bildirim kanalı) ve maskeli adlar.

### Güvenlik kuralları testi (Firestore Emulator)

- `firestore-tests/` klasöründe Node + `@firebase/rules-unit-testing` ile
  77 senaryo çalıştırıldı.
- Sonuç: **77/77 geçti** (2026-10-25; güncel sonuç 88/88 — bkz. 10. bölüm).
- Kapsam: hasta/koordinatör/görevli/yakın okuma; talep oluşturma; hazırlık;
  iptal; durum zinciri; adım atlama; personel yetkisi; onaysız yoldaki hasta
  gecikmesi; gizli hasta ve görevli gecikme raporları (yalnızca koordinatör/
  ilgili kişi okur); randevu erteleme; görevli ETA alanları (PLANNED dahil);
  bacak temizliği; otomatik dönüş planı ve onayı; onaysız dönüş engeli; atomik
  batch'ler; öncelik doğrulama; bildirim hedefi/okuma/okundu; profil gizliliği.

### Kotlin birim testleri

- `GuvenlikTest`: gecikme aralığı (5-180), aynı gün 20 dk ekleme, gece geçişinde
  tarih ilerletme, sıfır/negatif ve geçersiz tarih/saat reddi; dönüş zamanı
  (varış + seans + tampon) ve seans süresi sınırları.
- `GorevliListeFiltresiTest`: iptal/tamamlanan gizleme, gidiş-dönüşte merkez
  kartını koruma, gidiş-dönüşsüzde gizleme, aktif işleri koruma.
- Sonuç: **11/11 geçti** (2026-10-25; güncel sonuç 19/19 —
  `.\gradlew.bat :app:testDebugUnitTest`).

### Test sırasında düzeltilenler

- Hızlı “Durumu ilerlet” dokunuşlarına karşı işlem kilidi eklendi.
- OSM aramasında hızlı il/ilçe değişimine karşı 800 ms debounce eklendi.
- Görevli durum güncellemesi transaction'a alındı; eski durum kontrol edilir.
- Bildirimlerin ilk açılışta gösterilmemesi sorunu düzeltildi; okunmamışlar
  artık cihaz bildirimi olarak da gösterilir.
- Dönüş planı beklerken “Atama bekleniyor” ve “Eve bırakıldı” düğmesi yanlışlıkla
  görünüyordu; dönüş onayı/başlangıcı şartına bağlandı.
- Görevli gecikmesi yeni bacakta temizlenmiyordu; ETA’yı şişiriyordu. Bacak
  temizliği ve hedef kontrolü eklendi.
- Onay kaldırıldıktan sonra görevli kartında “koordinatör onayı bekleniyor”
  metni kalmıştı; tüm yoldaki/aktif durumlarda “bilgi verildi” yapıldı.
- Görevli listesinde biten işler kalıyordu; aktif iş filtresi eklendi.

### Test verileri

- Firestore'da kalan demo hesapları: `test.koordinator@example.com`,
  `test.hasta@example.com`, `test.gorevli@example.com`, `hasta.demo@example.com`.
- `test_arac_1`, `test_arac_2`, `test_arac_3` — test araçları.
- `test_personel_1`, `test_personel_2` — test görevlileri.
- Geçersiz tarihli/bitmiş/bozuk test talepleri, tarihçeleri, bildirimleri ve
  geçici hesaplar silindi.
- Kalan tek talep: `tQtryi7w57VeQkhjYOAs` — 05.11.2026 11:00, geçerli,
  önceliği doğrulanmış, atanmış demo talebi; dört bildirimiyle birlikte bırakıldı.
  ETA denemesi için merkez/ev koordinatları ve yeni alanları eklendi.
- Test sırasında oluşturulan geçici hesaplar (`claude.*`), talepler, durum
  geçmişleri, hasta/görevli gecikme raporları ve bildirimler silindi; uygulama
  verisi temizlenip giriş ekranında bırakıldı.

### Henüz test edilmedi

- Uçak modu/çevrimdışı davranış.
- Ekran döndürme, karanlık mod, klavye/taşma testleri.
- Yanlış şifre/olmayan hesap gibi giriş hataları (kodda ele alınıyor,
  cihazda ayrıca denenmeli).
- İki koordinatörün eşzamanlı ataması.
- Hasta talebi düzenleme (iptal var, düzenleme ekranı yok).
- Hasta yakını görünümü (henüz yer tutucu).
- FCM, harita adres seçimi, araç/personel CRUD (henüz yok).
- Uygulama yeniden açılışında otomatik oturum (henüz yok).

## 10. Test sonuçları (2026-10-27)

### P0 canlı yetki testleri

- Firebase CLI v15.34.1 ile `firestore.rules` canlıya yüklendi; derleme başarılı.
- Oturumsuz okuma: `users`, `transportRequests`, `personnel`, `vehicles`,
  `centers`, `notifications`, `delayReports`, `driverDelayReports` → **403** (9/9).
- Toplam: **106/106** (63 okuma + 43 yazma/durum).
- Doğrulananlar:
  - Hasta yalnızca kendi talebini/profilini/bildirimini/geçmişini okur.
  - Koordinatör tüm talepleri, kullanıcıları, personeli ve raporları okur.
  - Görevli yalnızca kendi `personnelId`'sine atanmış talebi ve kendi personel
    kaydını okur; başka atama ve filtreler reddedilir.
  - Gizli hasta/görevli gecikme raporları yalnızca sahibi + koordinatör tarafından
    okunur; diğer roller **403**.
  - Görevli durum adımı atlayamaz, atama alanlarını değiştiremez; koordinatör
    `patientId`/`createdBy` değiştiremez.
  - Dönüş planı onaysız başlatılamaz; koordinatör onayından sonra başlatılabilir.
- Test talepleri, sayaçlar ve bildirimler silindi; demo talep değişmedi.

### Kapasite özelliği

- Firestore emülatörü: **88/88** (11 yeni `slotAvailability` testi: okuma,
  oluşturma, +1/-1, sıfırın altına inme, fazla artırma, başka alan değiştirme,
  silme, oturumsuz erişim).
- Kotlin birim testleri: **19/19** (`KapasiteTest`: seans sınırları, belge
  kimliği, tarih ekleme, form dönüşümleri, 0-50 aralığı).
- Fiziksel cihaz (Samsung S8+):
  - Formda “Bu seans: 0/4 dolu • 4 yer var” gösterildi.
  - Sayaç 4/4 yapıldığında “DOLU” ve üç alternatif listelendi
    (01.12 14:00, 01.12 19:00, 02.12 09:00).
  - Alternatif seçimi tarih/saati güncelledi; kapasite 0/4'e döndü.
  - Dolu seansa kayıt denemesi sunucu tarafında engellendi; hata mesajı ve
    alternatifler korundu.
  - Koordinatör “Merkez kapasiteleri” diyaloğu kapasiteyi kaydetti; `centers`
    belgesi oluştu.
- Test kaydı, sayaç, bildirim ve test merkez belgesi silindi; canlıda yalnızca
  demo talep ve onun 1/4 doluluk sayacı kaldı.

### Kalan riskler

- `firestore.rules` içinde “maximum 1000 expressions” uyarısı sürüyor; meşru
  yollar geçiyor ancak kural sadeleştirmesi açık iş.
- Doluluk sayacı kuralı herhangi bir giriş yapan kullanıcının ±1 değiştirmesine
  izin verir; iş kuralı istemcide. Bu bir veri sızıntısı değil, kötü niyetli
  istemcide sayaç bozulması riskidir. Atomik artış transaction ile sağlanır.
- Kapasite değişikliği geçmiş talepleri otomatik iptal etmez; tam çizelgeleme,
  slot kilitleme ve araç optimizasyonu MVP dışıdır.
- FCM yok; uygulama kapalıyken bildirim gelmez.

## 11. P1/P2 testleri (2026-10-27)

- **Kural emülatörü:** 99/99 geçti (davet/bağlantı ve hasta tarih/saat/merkez
  değişikliği yasağı dahil).
- **Kotlin birim testleri:** 24/24 geçti (`DavetKoduTest` ve telefon E.164 testi dahil).
- **Cihazda doğrulananlar:** otomatik oturum; durum geçmişi; hasta yakını davet
  kodu üretme; ikinci yakın hesabıyla kod kabul etme; bağlı hastanın talebini
  görme; araç ekleme/düzenleme ve pasifleştirme; koordinatör kapasite diyaloğu.
- **Bulunan hatalar ve düzeltmeler:**
  - Geçmişte öncelik kategorisi `AGE_65_PLUS` ham görünüyordu → Türkçe etikete
    çevrildi.
  - Yakın bağlantısından hemen sonra talep listesi sessizce boş kalabiliyordu →
    dinleyiciye 3 deneme, hata mesajı ve boş bağlantı kontrolü eklendi.
  - Hasta tarih/saat/merkez alanlarını değiştirerek kapasite sayacını
    atlayabiliyordu → kural düzeyinde yasaklandı; değişiklik için iptal + yeni
    talep gerekiyor.
- **Yarım kalan test:** İptal sonrası doluluk sayacının azalması cihazda
  otomasyon gürültüsü nedeniyle tamamlanamadı; transaction ve kurallar emülatörde
  doğrulandı. Gecikme ertelemesinde sayaç taşıma da cihazda ayrıca denenmedi.
- **Google/telefon girişi:** Google hesap seçici açıldı; kişisel hesaba giriş
  yapılmadı. Telefon giriş alanı ve doğrulama akışı görüldü; gerçek SMS
  gönderilmedi. `google-services.json` güncellendi, debug SHA-1/SHA-256
  Firebase'e eklendi. Telefon için Firebase test numarası eklenip uçtan uca
  denenmeli.
- **Harita:** osmdroid Ankara haritası yüklendi; bir noktaya dokunuldu ve
  Nominatim Türkçe adres döndürdü. Harita yüksekliği metinlerin üzerine taşıyordu
  → 360dp'den 260dp'ye düşürülerek düzeltildi.
- **Kapsam dışı:** FCM/push, canlı GPS takibi/canlı trafik.
