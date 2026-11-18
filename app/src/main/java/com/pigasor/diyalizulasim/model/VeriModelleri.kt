package com.pigasor.diyalizulasim.model

// Uygulamadaki üç kullanıcı rolü: hasta, koordinatör ve hasta yakını.
enum class UserRole {
    PATIENT,
    COORDINATOR,
    RELATIVE,
    DRIVER
} // UserRole: Firestore'da rol bilgisini temsil eden sabit küme; DRIVER saha görevlisidir.

// Rol kodlarının ekranda gösterilecek Türkçe karşılıkları.
val UserRole.rolEtiketi: String
    get() = when (this) {
        UserRole.PATIENT -> "Hasta"
        UserRole.COORDINATOR -> "Koordinatör"
        UserRole.RELATIVE -> "Hasta yakını"
        UserRole.DRIVER -> "Görevli"
    } // rolEtiketi: seçilen rolü kullanıcıya anlaşılır Türkçe metinle gösterir.

// Yolculuğun geçebileceği tüm durumlar; son durum iptaldir.
enum class TransportStatus {
    REQUESTED,
    PLANNED,
    ON_THE_WAY,
    PICKED_UP,
    AT_CENTER,
    DROPPED_HOME,
    CANCELLED
} // TransportStatus: talep ve yolculuk yaşam döngüsünü temsil eder.

// Taşıma durumlarının kullanıcıya gösterilecek Türkçe etiketleri.
val TransportStatus.durumEtiketi: String
    get() = when (this) {
        TransportStatus.REQUESTED -> "Talep oluşturuldu"
        TransportStatus.PLANNED -> "Planlandı"
        TransportStatus.ON_THE_WAY -> "Hastaya gidiliyor"
        TransportStatus.PICKED_UP -> "Hasta alındı"
        TransportStatus.AT_CENTER -> "Merkeze ulaştı"
        TransportStatus.DROPPED_HOME -> "Eve bırakıldı"
        TransportStatus.CANCELLED -> "İptal edildi"
    } // durumEtiketi: durum kodunu ekranda okunacak metne çevirir.

// Durum zincirinde bir sonraki adımı döndürür; son adımda veya iptalde null olur.
fun TransportStatus.sonrakiDurum(): TransportStatus? = when (this) {
    TransportStatus.REQUESTED -> TransportStatus.PLANNED
    TransportStatus.PLANNED -> TransportStatus.ON_THE_WAY
    TransportStatus.ON_THE_WAY -> TransportStatus.PICKED_UP
    TransportStatus.PICKED_UP -> TransportStatus.AT_CENTER
    TransportStatus.AT_CENTER -> TransportStatus.DROPPED_HOME
    TransportStatus.DROPPED_HOME -> null
    TransportStatus.CANCELLED -> null
} // sonrakiDurum: koordinatörün sıradaki durum düğmesini belirlemesini sağlar.

// Hastanın araç gelmeden önceki hazırlık bildirimi.
enum class ReadinessStatus {
    NOT_SET,
    READY,
    DELAYED
} // ReadinessStatus: hastanın hazır/gecikecek bildirimini temsil eder.

// Hazırlık durumlarının Türkçe etiketleri.
val ReadinessStatus.hazirlikEtiketi: String
    get() = when (this) {
        ReadinessStatus.NOT_SET -> "Bildirmedi"
        ReadinessStatus.READY -> "Hazırım"
        ReadinessStatus.DELAYED -> "Gecikeceğim"
    } // hazirlikEtiketi: hazırlık kodunu koordinatörün göreceği metne çevirir.

// Geçmiş kaydının türü: durum, hazırlık, atama, iptal, gecikme/öncelik onayı veya serbest not.
enum class HistoryType {
    STATUS,
    READINESS,
    ASSIGNMENT,
    CANCELLATION,
    OVERRIDE,
    PRIORITY,
    DRIVER_DELAY,
    RETURN,
    NOTE
} // HistoryType: durum geçmişindeki olay çeşitlerini ayırır.

// Görevli/araç gecikmesinin herkese açık durumu; gizli gerekçe ayrı belgede tutulur.
enum class DriverDelayStatus {
    NONE,
    PENDING,
    INFO,
    APPROVED
} // DriverDelayStatus: onay bekleyen, bilgi amaçlı veya onaylanmış görevli gecikmesini ayırır.

// Görevli gecikme durumunun ekranda gösterilecek etiketleri.
val DriverDelayStatus.etiket: String
    get() = when (this) {
        DriverDelayStatus.NONE -> "Gecikme yok"
        DriverDelayStatus.PENDING -> "Koordinatör onayı bekliyor"
        DriverDelayStatus.INFO -> "Bilgi verildi"
        DriverDelayStatus.APPROVED -> "Onaylandı"
    } // etiket: görevli gecikmesinin kullanıcıya gösterilecek kısa durumunu üretir.

// Dönüş planının durumu: plan yok, koordinatör onayı bekliyor veya onaylandı.
enum class ReturnPlanStatus {
    NONE,
    PENDING,
    APPROVED
} // ReturnPlanStatus: merkeze ulaşıldıktan sonraki dönüş planının aşamasını tutar.

// Dönüş planı durumunun ekranda gösterilecek etiketi.
val ReturnPlanStatus.etiket: String
    get() = when (this) {
        ReturnPlanStatus.NONE -> "Dönüş planı yok"
        ReturnPlanStatus.PENDING -> "Onay bekliyor"
        ReturnPlanStatus.APPROVED -> "Onaylandı"
    } // etiket: dönüş planı durumunu kullanıcıya anlaşılır biçimde gösterir.

// Tahmini varışın hedefi: hastanın evi veya diyaliz merkezi.
enum class EtaHedefi {
    NONE,
    HOME,
    CENTER
} // EtaHedefi: ETA'nın hangi ayak için hesaplandığını belirtir.

// ETA hedefinin ekranda gösterilecek adı.
val EtaHedefi.etiket: String
    get() = when (this) {
        EtaHedefi.NONE -> ""
        EtaHedefi.HOME -> "Hasta evi"
        EtaHedefi.CENTER -> "Diyaliz merkezi"
    } // etiket: ETA satırında hedefin anlaşılır adını gösterir.

// Tamamlanan bacakta eski ETA'yı gizler; dönüş başlayınca yeni ETA'yı gösterir.
val TransportRequest.etaGorunurMu: Boolean
    get() = etaMinutes > 0 &&
        etaTarget != EtaHedefi.NONE &&
        status != TransportStatus.CANCELLED &&
        status != TransportStatus.DROPPED_HOME &&
        !(status == TransportStatus.AT_CENTER && returnStartedAt == 0L)

// Görevli gecikmesinin hangi bacak için geçerli olduğunu gösterir.
val TransportRequest.aktifEtaHedefi: EtaHedefi
    get() = when (status) {
        TransportStatus.PLANNED, TransportStatus.ON_THE_WAY -> EtaHedefi.HOME
        TransportStatus.PICKED_UP -> EtaHedefi.CENTER
        TransportStatus.AT_CENTER -> EtaHedefi.HOME
        else -> EtaHedefi.NONE
    } // aktifEtaHedefi: görevli gecikmesi ve ETA'nın hangi ayağa ait olduğunu belirler.

// Görevli gecikmesi mevcut bacakla eşleşiyorsa gösterilir.
val TransportRequest.driverDelayGorunurMu: Boolean
    get() = driverDelayStatus != DriverDelayStatus.NONE &&
        driverDelayTarget != EtaHedefi.NONE &&
        driverDelayTarget == aktifEtaHedefi &&
        status != TransportStatus.CANCELLED &&
        status != TransportStatus.DROPPED_HOME

// Taşıma önceliği kategorileri; tıbbi tanı değil, ulaşım ihtiyacı gerekçesidir.
enum class OncelikKategorisi {
    NONE,
    AGE_65_PLUS,
    DISABILITY,
    MARTYR_VETERAN,
    SERIOUS_HEALTH
} // OncelikKategorisi: doğrulanabilir öncelik nedenlerini sınırlı ve tanısız biçimde tutar.

// Öncelik kategorilerinin ekranda gösterilecek adları.
val OncelikKategorisi.oncelikEtiketi: String
    get() = when (this) {
        OncelikKategorisi.NONE -> "Öncelik yok"
        OncelikKategorisi.AGE_65_PLUS -> "65 yaş ve üzeri"
        OncelikKategorisi.DISABILITY -> "Engellilik / hareket kısıtlılığı"
        OncelikKategorisi.MARTYR_VETERAN -> "Şehit/gazi yakını"
        OncelikKategorisi.SERIOUS_HEALTH -> "Ciddi sağlık/fonksiyonel ihtiyaç"
    } // oncelikEtiketi: kategori kodunu koordinatörün anlayacağı metne çevirir.

// Doğrulanmış önceliklerin sıralama puanı; tanı bilgisi içermez.
val OncelikKategorisi.oncelikPuani: Int
    get() = when (this) {
        OncelikKategorisi.NONE -> 0
        OncelikKategorisi.AGE_65_PLUS -> 1
        OncelikKategorisi.MARTYR_VETERAN -> 2
        OncelikKategorisi.DISABILITY -> 3
        OncelikKategorisi.SERIOUS_HEALTH -> 4
    } // oncelikPuani: aynı gün içinde doğrulanmış önceliklerin sırasını belirler.

// Diyaliz merkezindeki seans dilimleri; kapasite kontrolü bu üç dilim üzerinden yapılır.
enum class SeansDilimi(val etiket: String, val varsayilanSaat: String) {
    SABAH("Sabah", "09:00"),
    OGLEDEN_SONRA("Öğleden sonra", "14:00"),
    AKSAM("Akşam", "19:00")
} // SeansDilimi: günü sabah, öğleden sonra ve akşam olarak üç kapasite dilimine ayırır.

// Bir merkez/tarih/seans için anlık doluluk özeti; kişisel veri içermez.
data class SlotDurumu(
    val merkezAdi: String = "",
    val tarih: String = "",
    val seans: SeansDilimi = SeansDilimi.SABAH,
    val kapasite: Int = 0,
    val dolu: Int = 0
) { // SlotDurumu: hastaya yalnızca toplam doluluk sayısını gösterir.
    val kalan: Int get() = (kapasite - dolu).coerceAtLeast(0)
    val doluMu: Boolean get() = kapasite > 0 && dolu >= kapasite
}

// Dolu seans yerine hastaya önerilecek uygun tarih/seans.
data class SlotOnerisi(
    val tarih: String = "",
    val saat: String = "",
    val seans: SeansDilimi = SeansDilimi.SABAH,
    val kalan: Int = 0
) // SlotOnerisi: otomatik alternatif listesindeki tek bir uygun seansı temsil eder.

// Hasta tarafından üretilen, hasta yakınının bağlanmasını sağlayan tek kullanımlık davet.
data class RelativeInvite(
    val code: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val usedBy: String = "",
    val usedAt: Long = 0L,
    val active: Boolean = true
) // RelativeInvite: hasta rızasını temsil eden kısa ömürlü bağlantı kodu.

// Hasta yakınının onaylanmış hasta bağlantısı; davet koduyla oluşturulur.
data class RelativeLink(
    val relativeUid: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val inviteCode: String = "",
    val createdAt: Long = 0L
) // RelativeLink: yakının hangi hastaya bağlı olduğunu değiştirilemez biçimde tutar.

// Kullanıcı profili; Firestore'da users/{uid} belgesine karşılık gelir.
data class AppUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.PATIENT,
    val linkedPatientId: String = "",
    val personnelId: String = "",
    val visibleStatuses: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) // AppUser: giriş yapan kişinin kimlik, rol, yakın-izin ve görevli-personel bilgilerini taşır.

// Araç bilgisi; erişilebilirlik ve kapasite kontrollerinde kullanılır.
data class Vehicle(
    val id: String = "",
    val name: String = "",
    val plate: String = "",
    val wheelchairAccessible: Boolean = false,
    val capacity: Int = 1,
    val active: Boolean = true
) // Vehicle: atama sırasında kapasite ve tekerlekli sandalye uygunluğunu denetler.

// Ulaşım talebi; hastanın seans ve yolculuk bilgilerini tek belgede toplar.
data class TransportRequest(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val sessionDate: String = "",
    val sessionTime: String = "",
    val sessionDurationMinutes: Int = 240,
    val centerName: String = "",
    val centerCapacity: Int = 0,
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val pickupAddress: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val roundTrip: Boolean = true,
    val companionRequired: Boolean = false,
    val companionName: String = "",
    val companionPhone: String = "",
    val companionRelation: String = "",
    val wheelchairRequired: Boolean = false,
    val note: String = "",
    val status: TransportStatus = TransportStatus.REQUESTED,
    val vehicleId: String = "",
    val vehicleName: String = "",
    val personnelId: String = "",
    val personnel: String = "",
    val personnelPhone: String = "",
    val plannedPickupTime: String = "",
    val readiness: ReadinessStatus = ReadinessStatus.NOT_SET,
    val delayMinutes: Int = 0,
    val readinessUpdatedAt: Long = 0L,
    val delayOverride: Boolean = false,
    val delayOverrideReason: String = "",
    val delayOverrideBy: String = "",
    val delayOverrideByName: String = "",
    val delayOverrideAt: Long = 0L,
    // Koordinatör onayı randevuyu ertelediyse eski ve yeni zamanı göstermek için tutulur.
    val delayRescheduled: Boolean = false,
    val delayPreviousSessionDate: String = "",
    val delayPreviousSessionTime: String = "",
    val delayPreviousPickupTime: String = "",
    // Yalnızca koordinatör onayından sonra görevliyle paylaşılan hasta mesajı.
    val delayDriverMessage: String = "",
    // Görevli/araç gecikmesinin herkese açık özeti; gizli gerekçe driverDelayReports'ta.
    val driverDelayMinutes: Int = 0,
    val driverDelayStatus: DriverDelayStatus = DriverDelayStatus.NONE,
    val driverDelayTarget: EtaHedefi = EtaHedefi.NONE,
    val driverDelayUpdatedAt: Long = 0L,
    val driverDelayApprovedAt: Long = 0L,
    val driverDelayApprovedBy: String = "",
    val driverDelayApprovedByName: String = "",
    // OSRM'den gelen temel süre ve varsa görevli gecikmesiyle birlikte gösterilen toplam.
    val etaBaseMinutes: Int = 0,
    val etaMinutes: Int = 0,
    val etaTarget: EtaHedefi = EtaHedefi.NONE,
    val etaUpdatedAt: Long = 0L,
    val etaSource: String = "",
    // Merkeze varış ve otomatik dönüş planı.
    val centerArrivedAt: Long = 0L,
    val returnBufferMinutes: Int = 15,
    val returnPlannedAt: Long = 0L,
    val returnPlanStatus: ReturnPlanStatus = ReturnPlanStatus.NONE,
    val returnApprovedAt: Long = 0L,
    val returnApprovedBy: String = "",
    val returnApprovedByName: String = "",
    // Dönüş yolculuğunun başlangıç zamanı; ayrı bir durum eklemeden ETA'yı tetikler.
    val returnStartedAt: Long = 0L,
    val cancelledBy: String = "",
    val cancelReason: String = "",
    val cancelledAt: Long = 0L,
    val priorityCategory: OncelikKategorisi = OncelikKategorisi.NONE,
    val priorityVerified: Boolean = false,
    val priorityVerifiedBy: String = "",
    val priorityVerifiedByName: String = "",
    val priorityVerifiedAt: Long = 0L,
    val priorityVerificationNote: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) // TransportRequest: talep, atama, hazırlık, gecikme onayı ve iptal bilgilerini tek yerde tutar.

// Hastanın gecikme bildirimi; gerekçe yalnızca koordinatör tarafından okunur.
// Talep belgesi görevli tarafından okunabildiği için gerekçe ayrı koleksiyonda tutulur.
data class DelayReport(
    val requestId: String = "",
    val patientId: String = "",
    val minutes: Int = 0,
    val reason: String = "",
    val driverMessage: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) // DelayReport: gecikme süresi, gizli gerekçe ve görevliye iletilecek isteğe bağlı mesajı taşır.

// Görevli/araç gecikmesinin gizli gerekçesini taşıyan rapor.
// Talep belgesini görevli de okuyabildiği için gerekçe ayrı koleksiyonda tutulur.
data class DriverDelayReport(
    val requestId: String = "",
    val driverUid: String = "",
    val personnelId: String = "",
    val minutes: Int = 0,
    val reason: String = "",
    val status: DriverDelayStatus = DriverDelayStatus.PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) // DriverDelayReport: görevlinin süre ve gizli gerekçesini koordinatörle sınırlar.

// Görevli/personel kaydı; koordinatör atama ekranında listeden seçilir.
data class Personnel(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val active: Boolean = true,
    val driverUid: String = ""
) // Personnel: görevli adı, iletişim bilgisi ve bağlı görevli hesabını taşır.

// Uygulama içi bildirimin kimlere gösterileceğini belirler.
enum class BildirimHedefi {
    COORDINATOR,
    DRIVER,
    PATIENT
} // BildirimHedefi: koordinatör, görevli veya hasta bildirim kutusunu ayırır.

// Uygulama içi bildirim; Firestore'da notifications/{id} belgesine karşılık gelir.
data class Bildirim(
    val id: String = "",
    val hedef: BildirimHedefi = BildirimHedefi.PATIENT,
    val userId: String = "",
    val personnelId: String = "",
    val baslik: String = "",
    val mesaj: String = "",
    val talepId: String = "",
    val okundu: Boolean = false,
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) // Bildirim: uygulama içi bildirim merkezinin tek bir kaydını temsil eder.

// Durum geçmişi kaydı; her değişikliğin zamanı ve yapan kişisi burada saklanır.
data class HistoryEvent(
    val id: String = "",
    val type: HistoryType = HistoryType.STATUS,
    val value: String = "",
    val note: String = "",
    val updatedBy: String = "",
    val updatedByName: String = "",
    val timestamp: Long = System.currentTimeMillis()
) // HistoryEvent: denetim izi için kim, ne zaman, neyi değiştirdi bilgisini taşır.

// İl ve ilçe listesi; formdaki seçim menülerinde kullanılır.
data class IlVerisi(
    val il: String = "",
    val ilceler: List<String> = emptyList()
) // IlVerisi: bir ilin adını ve bağlı ilçelerini taşır.

// Diyaliz merkezi kaydı; il/ilçe seçimine göre filtrelenir.
data class DiyalizMerkezi(
    val il: String = "",
    val ilce: String = "",
    val ad: String = "",
    val adres: String = "",
    val placeId: String = "",
    val enlem: Double = 0.0,
    val boylam: Double = 0.0,
    val id: String = "",
    val kapasitePerSeans: Int = 0
) // DiyalizMerkezi: seçilen ilçedeki merkez seçeneklerini, konumunu ve seans kapasitesini temsil eder.
