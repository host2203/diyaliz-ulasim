# Diyaliz Ulaşım — Pilot Önerisi

> Bu belge bir öğrenci/prototip çalışmasının kurumsal pilot önerisidir. Gerçek hasta verisi
> kullanılmamıştır; saha pilotu ancak KVKK, veri işleme ve kurum onayları sonrası yapılabilir.

## 1. Problem
Diyaliz hastaları haftada birkaç kez merkeze gider; ulaşımın hastaya, merkez seansına ve araca
göre koordine edilmesi gerekir. Telefon/WhatsApp ile yürütülen koordinasyon; kaçırılan seans,
gecikme, kapasite aşımı ve denetim izi eksikliği yaratır.

## 2. Çözüm
Hasta, hasta yakını, koordinatör ve görevli için tek uygulama:
- Hasta talebi: tarih, seans, merkez, adres, refakat/tekerlekli sandalye, öncelik.
- Seans kapasitesi: merkez + tarih + seans doluluğu; doluysa en yakın 3 alternatif otomatik önerilir.
- Koordinatör: araç/görevli ataması, merkez kapasitesi, öncelik doğrulama, dönüş planı onayı.
- Görevli: atanan işler ve durum zinciri (yolda → hasta alındı → merkezde → eve bırakıldı).
- Hasta yakını: davet koduyla bağlanır, yalnızca bağlı hastanın taleplerini ve geçmişini okur.
- Denetim izi: her durum/atama/iptal olayı zaman ve kullanıcı ile kaydedilir.
- Gizlilik: gecikme gerekçeleri ayrı koleksiyonlarda; hasta/görevli birbirinin gerekçesini okuyamaz.

## 3. Pilot kapsamı (öneri)
- Tek il/ilçe, tek diyaliz merkezi veya 1–2 merkez, 10–20 gönüllü hasta.
- 4–8 hafta; gerçek hasta verisi yerine önce **kurgusal/test verisiyle** saha provası.
- Koordinatör mevcut iş akışını uygulamaya girer; hasta yalnızca talep/hazırlık/iptal yapar.
- Başarı ölçütleri: kaçırılan seans oranı, ortalama gecikme, kapasite aşımı sayısı,
  koordinatörün telefonla harcadığı süre, kullanıcı memnuniyeti.

## 4. Mimari ve veri akışı
- Android (Kotlin + Jetpack Compose), Firebase Authentication + Cloud Firestore.
- Rol bazlı Firestore güvenlik kuralları; canlı yetki testleri 106/106, kural emülatörü 99/99.
- Seans doluluğu `slotAvailability` toplam sayacıyla; hasta talebi ve sayaç artışı transaction.
- OSRM public ile ücretsiz rota ETA’sı; canlı trafik/GPS yok.
- Bildirimler uygulama içi + yerel; FCM/push yok (sunucusuz ücretsiz kısıt).
- Firestore verisi Google altyapısında; veri bölgesi ve yurt dışı aktarım **hukuk/KVKK incelemesi**
  gerektirir (pilot öncesi netleştirilmeli).

## 5. KVKK ve güvenlik
- Sağlık verisi özel nitelikli kişisel veridir; gerçek veri için açık rıza, aydınlatma,
  saklama/silme politikası ve veri işleme sözleşmesi gerekir.
- Uygulama tanı/belge içeriği saklamaz; öncelik yalnızca koordinatörün doğrulama notuyla tutulur.
- Adres/telefon gibi veriler minimizasyonla toplanır; bildirimlerde ad maskelenir.
- Kurumsal pilot öncesi: sızma testi, App Check, yedekleme, olay müdahale ve denetim planı.
- Pilot verisi ayrı Firebase projesinde tutulmalı; üretim verisiyle karıştırılmamalı.

## 6. Maliyet ve sürdürülebilirlik
- Prototip ücretsiz Firebase kotası + OSRM public ile çalışıyor.
- Kurumsal pilot için barındırma, destek, güvenlik ve hukuk maliyeti oluşur; ücretsiz katman
  üretim için yeterli değildir. Bu kalemler pilot bütçesinde açıkça yer almalı.
- FCM/push ve canlı trafik isteğe bağlı; ücretsiz sunucu relay’i veya kurum bütçesi gerekir.

## 7. Yol haritası
1. Ders MVP’si + kapasite/pilot demosu (tamamlandı).
2. Kurumla ön görüşme; pilot ortağı ve veri sorumlusu netleşmesi.
3. KVKK/güvenlik sertleştirme; ayrı pilot ortamı; App Check/yedekleme.
4. 4–8 haftalık sınırlı saha pilotu; ölçüm ve geri bildirim.
5. Değerlendirme; yaygınlaştırma veya durdurma kararı.

## 8. Kurumdan istenenler
- Pilot için bir irtibat kişisi ve diyaliz merkezi/il sağlık müdürlüğü desteği.
- Veri sorumlusu/veri işleyen rollerinin ve veri bölgesinin netleştirilmesi.
- Gerçek hasta verisi kullanılacaksa hukuki metinler ve onay süreci.
- Pilot başarı ölçütleri ve geri bildirim kanalı.

## 9. Bilinen sınırlar
- FCM yok; uygulama kapalıyken push bildirim gelmez.
- Canlı GPS takibi/canlı trafik yok; ETA OSRM ve bilinen koordinatlarla hesaplanır.
- Haritadan adres seçimi ve tek seferlik “konumumu kullan” vardır; sürüş
  sırasında otomatik ETA yenileme yoktur.
- Google/telefon girişi kod olarak hazırdır; telefon için SHA, test numarası ve
  gerçek SMS testi gerekir.
- Tam çizelgeleme/araç optimizasyonu yok; kapasite kontrolü MVP düzeyindedir.
- Gerçek hasta verisiyle pilot için ek güvenlik ve hukuk çalışması şarttır.
