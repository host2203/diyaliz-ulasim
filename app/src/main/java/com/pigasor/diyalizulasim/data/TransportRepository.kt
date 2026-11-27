package com.pigasor.diyalizulasim.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.BildirimHedefi
import com.pigasor.diyalizulasim.model.DelayReport
import com.pigasor.diyalizulasim.model.DiyalizMerkezi
import com.pigasor.diyalizulasim.model.DriverDelayReport
import com.pigasor.diyalizulasim.model.DriverDelayStatus
import com.pigasor.diyalizulasim.model.EtaHedefi
import com.pigasor.diyalizulasim.model.HistoryEvent
import com.pigasor.diyalizulasim.model.HistoryType
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.oncelikEtiketi
import com.pigasor.diyalizulasim.model.oncelikPuani
import com.pigasor.diyalizulasim.model.Personnel
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.RelativeInvite
import com.pigasor.diyalizulasim.model.RelativeLink
import com.pigasor.diyalizulasim.model.ReturnPlanStatus
import com.pigasor.diyalizulasim.model.SeansDilimi
import com.pigasor.diyalizulasim.model.SlotDurumu
import com.pigasor.diyalizulasim.model.SlotOnerisi
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.UserRole
import com.pigasor.diyalizulasim.model.Vehicle
import com.pigasor.diyalizulasim.security.Guvenlik
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Seçilen merkez/tarih/seans kapasitesi dolduğunda fırlatılır.
class SlotDoluException(
    val merkezAdi: String,
    val tarih: String,
    val seans: SeansDilimi
) : Exception("Bu seans dolu.")

// Ulaşım talepleri ve araçlarla ilgili Firestore işlemlerini yönetir.
class TransportRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val etaProvider: EtaProvider = OsrmEtaProvider()
) {

    // Yeni ulaşım talebini seans kapasitesini atomik olarak kontrol ederek Firestore'a yazar.
    suspend fun talepOlustur(talep: TransportRequest, kapasite: Int): Result<String> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Oturum bulunamadı."))
            val simdi = System.currentTimeMillis()
            val talepRef = firestore.collection("transportRequests").document()
            val seans = Kapasite.seansDilimi(talep.sessionTime)
            val slotId = if (kapasite > 0 && seans != null) {
                Kapasite.slotBelgesiId(talep.centerName, talep.sessionDate, seans)
            } else {
                null
            }
            if (slotId == null) {
                // Kapasite tanımlı değilse mevcut davranış korunur; doluluk kontrolü yapılmaz.
                talepRef.set(talepBelgesiVerisi(talep, uid, simdi, kapasite)).await()
            } else {
                val seciliSeans = requireNotNull(seans)
                val slotRef = firestore.collection("slotAvailability").document(slotId)
                firestore.runTransaction { islem ->
                    val slot = islem.get(slotRef)
                    val dolu = if (slot.exists()) {
                        (slot.get("booked") as? Number)?.toInt() ?: 0
                    } else {
                        0
                    }
                    if (dolu >= kapasite) {
                        throw SlotDoluException(talep.centerName, talep.sessionDate, seciliSeans)
                    }
                    if (slot.exists()) {
                        islem.update(
                            slotRef,
                            mapOf("booked" to dolu + 1, "updatedAt" to simdi)
                        )
                    } else {
                        islem.set(
                            slotRef,
                            mapOf(
                                "centerName" to talep.centerName,
                                "date" to talep.sessionDate,
                                "session" to seciliSeans.name,
                                "booked" to 1,
                                "updatedAt" to simdi
                            )
                        )
                    }
                    islem.set(talepRef, talepBelgesiVerisi(talep, uid, simdi, kapasite))
                    Unit
                }.await()
            }
            // Koordinatör bildirimi talep yazıldıktan sonra gönderilir; bildirim hatası talebi bozmaz.
            runCatching {
                bildirimGonder(
                    hedef = BildirimHedefi.COORDINATOR,
                    userId = "",
                    personnelId = "",
                    baslik = "Yeni ulaşım talebi",
                    mesaj = "${Guvenlik.adMaskele(talep.patientName)} • " +
                        "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime} • " +
                        talep.centerName,
                    talepId = talepRef.id,
                    olusturanUid = uid
                )
            }
            Result.success(talepRef.id)
        } catch (e: SlotDoluException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Talep oluşturulamadı. Lütfen tekrar deneyin."))
        }
    } // talepOlustur: hastanın talebini, dolu seansı ve eşzamanlı yarışı engelleyerek kaydeder.

    // Yeni talebin tüm Firestore alanlarını tek yerde üretir.
    private fun talepBelgesiVerisi(
        talep: TransportRequest,
        uid: String,
        simdi: Long,
        kapasite: Int
    ): Map<String, Any> {
        return mapOf(
            "patientId" to uid,
            "patientName" to talep.patientName,
            "sessionDate" to talep.sessionDate,
            "sessionTime" to talep.sessionTime,
            "sessionDurationMinutes" to talep.sessionDurationMinutes,
            "returnBufferMinutes" to talep.returnBufferMinutes,
            "centerName" to talep.centerName,
            "centerCapacity" to kapasite,
            "centerLat" to talep.centerLat,
            "centerLng" to talep.centerLng,
            "pickupAddress" to talep.pickupAddress,
            "pickupLat" to talep.pickupLat,
            "pickupLng" to talep.pickupLng,
            "roundTrip" to talep.roundTrip,
            "companionRequired" to talep.companionRequired,
            "companionName" to talep.companionName,
            "companionPhone" to talep.companionPhone,
            "companionRelation" to talep.companionRelation,
            "wheelchairRequired" to talep.wheelchairRequired,
            "note" to talep.note,
            "status" to TransportStatus.REQUESTED.name,
            "vehicleId" to "",
            "vehicleName" to "",
            "personnelId" to "",
            "personnel" to "",
            "personnelPhone" to "",
            "plannedPickupTime" to "",
            "readiness" to ReadinessStatus.NOT_SET.name,
            "delayMinutes" to 0,
            "readinessUpdatedAt" to 0L,
            "delayOverride" to false,
            "delayOverrideReason" to "",
            "delayOverrideBy" to "",
            "delayOverrideByName" to "",
            "delayOverrideAt" to 0L,
            "delayRescheduled" to false,
            "delayPreviousSessionDate" to "",
            "delayPreviousSessionTime" to "",
            "delayPreviousPickupTime" to "",
            "delayDriverMessage" to "",
            "driverDelayMinutes" to 0,
            "driverDelayStatus" to DriverDelayStatus.NONE.name,
            "driverDelayTarget" to EtaHedefi.NONE.name,
            "driverDelayUpdatedAt" to 0L,
            "driverDelayApprovedAt" to 0L,
            "driverDelayApprovedBy" to "",
            "driverDelayApprovedByName" to "",
            "etaBaseMinutes" to 0,
            "etaMinutes" to 0,
            "etaTarget" to EtaHedefi.NONE.name,
            "etaUpdatedAt" to 0L,
            "etaSource" to "",
            "centerArrivedAt" to 0L,
            "returnPlannedAt" to 0L,
            "returnPlanStatus" to ReturnPlanStatus.NONE.name,
            "returnApprovedAt" to 0L,
            "returnApprovedBy" to "",
            "returnApprovedByName" to "",
            "returnStartedAt" to 0L,
            "cancelledBy" to "",
            "cancelReason" to "",
            "cancelledAt" to 0L,
            "priorityCategory" to talep.priorityCategory.name,
            "priorityVerified" to false,
            "priorityVerifiedBy" to "",
            "priorityVerifiedByName" to "",
            "priorityVerifiedAt" to 0L,
            "priorityVerificationNote" to "",
            "createdBy" to uid,
            "createdAt" to simdi,
            "updatedAt" to simdi
        )
    } // talepBelgesiVerisi: talep oluşturma ile kapasite sayacı aynı işlemde yazılsın diye ortak map üretir.

    // Hastanın taleplerini gerçek zamanlı dinler; veri değişince listeyi günceller.
    fun hastaTalepleriDinle(patientId: String): Flow<List<TransportRequest>> = callbackFlow {
        val kayit = firestore.collection("transportRequests")
            .whereEqualTo("patientId", patientId)
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) {
                    close(hata)
                    return@addSnapshotListener
                }
                val talepler = snapshot?.documents
                    ?.mapNotNull { it.toTransportRequest() }
                    ?: emptyList()
                trySend(talepler.sortedByDescending { it.createdAt })
            }
        awaitClose { kayit.remove() }
    } // hastaTalepleriDinle: hastanın ekranını canlı tutar; ayrı bir yenileme gerekmez.

    // Koordinatör için tüm talepleri gerçek zamanlı dinler.
    fun tumTalepleriDinle(): Flow<List<TransportRequest>> = callbackFlow {
        val kayit = firestore.collection("transportRequests")
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) {
                    close(hata)
                    return@addSnapshotListener
                }
                val talepler = snapshot?.documents
                    ?.mapNotNull { it.toTransportRequest() }
                    ?: emptyList()
                trySend(
                    talepler.sortedWith(
                        compareBy<TransportRequest>(
                            { it.sessionDate },
                            {
                                if (it.priorityVerified) -it.priorityCategory.oncelikPuani
                                else 0
                            },
                            { it.sessionTime }
                        )
                    )
                )
            }
        awaitClose { kayit.remove() }
    } // tumTalepleriDinle: aynı gün içinde doğrulanmış öncelikli talepleri üste taşır.

    // Görevlinin kendisine atanan talepleri gerçek zamanlı dinler.
    fun gorevliTalepleriDinle(personnelId: String): Flow<List<TransportRequest>> = callbackFlow {
        val kayit = firestore.collection("transportRequests")
            .whereEqualTo("personnelId", personnelId)
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) {
                    close(hata)
                    return@addSnapshotListener
                }
                val talepler = snapshot?.documents
                    ?.mapNotNull { it.toTransportRequest() }
                    ?: emptyList()
                trySend(gorevliAktifTalepleri(talepler))
            }
        awaitClose { kayit.remove() }
    } // gorevliTalepleriDinle: yalnızca aktif işleri ve bekleyen dönüşleri gösterir.

    // Bir talebin durum geçmişini zaman sırasıyla canlı dinler.
    fun talepGecmisiDinle(talepId: String): Flow<List<HistoryEvent>> = callbackFlow {
        val kayit = firestore.collection("transportRequests")
            .document(talepId)
            .collection("history")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) {
                    close(hata)
                    return@addSnapshotListener
                }
                val olaylar = snapshot?.documents
                    ?.mapNotNull { it.toHistoryEvent() }
                    ?: emptyList()
                trySend(olaylar)
            }
        awaitClose { kayit.remove() }
    } // talepGecmisiDinle: hasta/koordinatör/görevli/yakın için denetim izini canlı tutar.

    // Koordinatörün gecikme gerekçelerini canlı okur; görevli bu koleksiyona erişemez.
    fun gecikmeRaporlariniDinle(): Flow<List<DelayReport>> = callbackFlow {
        val kayit = firestore.collection("delayReports")
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) {
                    close(hata)
                    return@addSnapshotListener
                }
                val raporlar = snapshot?.documents
                    ?.mapNotNull { it.toDelayReport() }
                    ?: emptyList()
                trySend(raporlar)
            }
        awaitClose { kayit.remove() }
    } // gecikmeRaporlariniDinle: koordinatör panelindeki gizli gerekçeleri canlı tutar.

    // Koordinatörün görevli/araç gecikmesi gerekçelerini canlı okur.
    fun gorevliGecikmeRaporlariniDinle(): Flow<List<DriverDelayReport>> = callbackFlow {
        val kayit = firestore.collection("driverDelayReports")
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) {
                    close(hata)
                    return@addSnapshotListener
                }
                val raporlar = snapshot?.documents
                    ?.mapNotNull { it.toDriverDelayReport() }
                    ?: emptyList()
                trySend(raporlar)
            }
        awaitClose { kayit.remove() }
    } // gorevliGecikmeRaporlariniDinle: gizli görevli gerekçelerini koordinatöre canlı taşır.

    // Aktif araçları Firestore'dan okur; atama ekranında kullanılır.
    suspend fun araclariGetir(aktifSadece: Boolean = true): Result<List<Vehicle>> {
        return try {
            val belgeler = firestore.collection("vehicles").get().await()
            val araclar = belgeler.documents
                .mapNotNull { it.toVehicle() }
                .filter { !aktifSadece || it.active }
                .sortedBy { it.name }
            Result.success(araclar)
        } catch (e: Exception) {
            Result.failure(Exception("Araç listesi alınamadı."))
        }
    } // araclariGetir: atama için aktifleri, yönetim için tüm kayıtları yükler.

    // Firestore'daki centers koleksiyonundan diyaliz merkezlerini okur.
    suspend fun merkezleriGetir(): Result<List<DiyalizMerkezi>> {
        return try {
            val belgeler = firestore.collection("centers").get().await()
            val merkezler = belgeler.documents.mapNotNull { it.toDiyalizMerkezi() }
            Result.success(merkezler)
        } catch (e: Exception) {
            Result.failure(Exception("Merkez listesi alınamadı."))
        }
    } // merkezleriGetir: koordinatörün eklediği merkezleri forma taşır.

    // Koordinatörün merkez için seans başına hasta kapasitesini kaydeder.
    // Demo merkezler ilk kayıtta centers koleksiyonuna sabit kimlikle yazılır.
    suspend fun merkezKapasitesiKaydet(
        merkez: DiyalizMerkezi,
        kapasite: Int
    ): Result<Unit> {
        if (!Kapasite.kapasiteGecerliMi(kapasite)) {
            return Result.failure(
                Exception("Kapasite 0-${Kapasite.MAKS_SEANS_KAPASITESI} arasında olmalı.")
            )
        }
        val belgeId = merkez.id.ifBlank {
            Kapasite.merkezBelgesiId(merkez.ad) ?: ""
        }
        if (belgeId.isBlank()) {
            return Result.failure(Exception("Merkez adı geçersiz."))
        }
        return try {
            firestore.collection("centers").document(belgeId).set(
                mapOf<String, Any>(
                    "name" to merkez.ad,
                    "province" to merkez.il,
                    "district" to merkez.ilce,
                    "address" to merkez.adres,
                    "capacityPerSession" to kapasite,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Kapasite kaydedilemedi. Lütfen tekrar deneyin."))
        }
    } // merkezKapasitesiKaydet: merkez belgesini oluşturur/günceller; hastalar bu belgeyi okur.

    // Seçili merkez/tarih/seans için toplam doluluk bilgisini okur.
    suspend fun slotDurumuGetir(
        merkezAdi: String,
        tarihIso: String,
        saat: String,
        kapasite: Int
    ): Result<SlotDurumu?> {
        if (kapasite <= 0) return Result.success(null)
        val seans = Kapasite.seansDilimi(saat) ?: return Result.success(null)
        val slotId = Kapasite.slotBelgesiId(merkezAdi, tarihIso, seans)
            ?: return Result.success(null)
        return try {
            val belge = firestore.collection("slotAvailability").document(slotId).get().await()
            val dolu = if (belge.exists()) {
                (belge.get("booked") as? Number)?.toInt() ?: 0
            } else {
                0
            }
            Result.success(SlotDurumu(merkezAdi, tarihIso, seans, kapasite, dolu))
        } catch (e: Exception) {
            Result.failure(Exception("Doluluk bilgisi alınamadı."))
        }
    } // slotDurumuGetir: kişisel veri göstermeden yalnızca toplam doluluk sayısını döndürür.

    // Dolu seans yerine aynı merkezdeki en yakın uygun tarih/seansları önerir.
    suspend fun alternatifSlotlarGetir(
        merkezAdi: String,
        seciliTarih: String,
        seciliSaat: String,
        kapasite: Int,
        gunSayisi: Int = 14,
        adet: Int = 3
    ): Result<List<SlotOnerisi>> {
        if (kapasite <= 0) return Result.success(emptyList())
        val seciliSeans = Kapasite.seansDilimi(seciliSaat)
        val seciliZaman = Guvenlik.seansZamani(seciliTarih, seciliSaat)
        val simdi = System.currentTimeMillis()
        return try {
            val oneriler = mutableListOf<SlotOnerisi>()
            for (gun in 0 until gunSayisi) {
                val tarih = Kapasite.tarihEkle(seciliTarih, gun) ?: break
                for (seans in SeansDilimi.values()) {
                    if (gun == 0 && seans == seciliSeans) continue
                    val saat = seans.varsayilanSaat
                    val zaman = Guvenlik.seansZamani(tarih, saat) ?: continue
                    if (zaman <= simdi) continue
                    if (gun == 0 && seciliZaman != null && zaman <= seciliZaman) continue
                    val slotId = Kapasite.slotBelgesiId(merkezAdi, tarih, seans) ?: continue
                    val belge = firestore.collection("slotAvailability")
                        .document(slotId)
                        .get()
                        .await()
                    val dolu = if (belge.exists()) {
                        (belge.get("booked") as? Number)?.toInt() ?: 0
                    } else {
                        0
                    }
                    if (dolu < kapasite) {
                        oneriler.add(
                            SlotOnerisi(
                                tarih = tarih,
                                saat = saat,
                                seans = seans,
                                kalan = (kapasite - dolu).coerceAtLeast(0)
                            )
                        )
                        if (oneriler.size >= adet) return Result.success(oneriler)
                    }
                }
            }
            Result.success(oneriler)
        } catch (e: Exception) {
            Result.failure(Exception("Alternatif seanslar alınamadı."))
        }
    } // alternatifSlotlarGetir: dolu seansı takiben en fazla üç uygun dilim sunar.

    // Hasta için tek kullanımlık hasta yakını davet kodu üretir.
    suspend fun davetOlustur(hasta: AppUser): Result<RelativeInvite> {
        if (hasta.role != UserRole.PATIENT) {
            return Result.failure(Exception("Yalnızca hasta davet oluşturabilir."))
        }
        val simdi = System.currentTimeMillis()
        return try {
            repeat(5) {
                val kod = DavetKodu.uret()
                val ref = firestore.collection("relativeInvites").document(kod)
                if (!ref.get().await().exists()) {
                    val davet = RelativeInvite(
                        code = kod,
                        patientId = hasta.uid,
                        patientName = hasta.name,
                        createdAt = simdi,
                        expiresAt = DavetKodu.sonGecerlilik(simdi),
                        usedBy = "",
                        usedAt = 0L,
                        active = true
                    )
                    ref.set(
                        mapOf<String, Any>(
                            "patientId" to davet.patientId,
                            "patientName" to davet.patientName,
                            "createdAt" to davet.createdAt,
                            "expiresAt" to davet.expiresAt,
                            "usedBy" to davet.usedBy,
                            "usedAt" to davet.usedAt,
                            "active" to davet.active
                        )
                    ).await()
                    return Result.success(davet)
                }
            }
            Result.failure(Exception("Davet kodu üretilemedi. Lütfen tekrar deneyin."))
        } catch (e: Exception) {
            Result.failure(Exception("Davet oluşturulamadı. Lütfen tekrar deneyin."))
        }
    } // davetOlustur: yedi gün geçerli, tek kullanımlık ve rastgele bir kod yazar.

    // Hasta yakınının davet kodunu kullanarak hastaya bağlanmasını sağlar.
    suspend fun davetiKabulEt(kod: String, yakin: AppUser): Result<Unit> {
        if (yakin.role != UserRole.RELATIVE) {
            return Result.failure(Exception("Bu hesap hasta yakını değil."))
        }
        val temizKod = DavetKodu.normalize(kod)
        if (!DavetKodu.gecerliMi(temizKod)) {
            return Result.failure(Exception("Davet kodu 12 karakter olmalı."))
        }
        val simdi = System.currentTimeMillis()
        val davetRef = firestore.collection("relativeInvites").document(temizKod)
        val baglantiRef = firestore.collection("relativeLinks").document(yakin.uid)
        return try {
            val mevcutBaglanti = baglantiRef.get().await()
            val hastaId: String
            val hastaAdi: String
            if (mevcutBaglanti.exists()) {
                hastaId = mevcutBaglanti.getString("patientId") ?: ""
                hastaAdi = mevcutBaglanti.getString("patientName") ?: ""
            } else {
                val sonuc = firestore.runTransaction { islem ->
                    val davet = islem.get(davetRef)
                    if (!davet.exists()) throw IllegalStateException("Davet kodu bulunamadı.")
                    if (davet.getBoolean("active") != true) {
                        throw IllegalStateException("Bu davet kodu daha önce kullanılmış.")
                    }
                    if (!davet.getString("usedBy").isNullOrBlank()) {
                        throw IllegalStateException("Bu davet kodu daha önce kullanılmış.")
                    }
                    val sonTarih = (davet.get("expiresAt") as? Number)?.toLong() ?: 0L
                    if (sonTarih > 0L && sonTarih < simdi) {
                        throw IllegalStateException("Davet kodunun süresi dolmuş.")
                    }
                    val link = islem.get(baglantiRef)
                    if (link.exists()) {
                        throw IllegalStateException("Bu hesap zaten bir hastaya bağlı.")
                    }
                    val hastaId = davet.getString("patientId") ?: ""
                    val hastaAdi = davet.getString("patientName") ?: ""
                    if (hastaId.isBlank()) throw IllegalStateException("Davet geçersiz.")
                    islem.set(
                        baglantiRef,
                        mapOf<String, Any>(
                            "patientId" to hastaId,
                            "patientName" to hastaAdi,
                            "inviteCode" to temizKod,
                            "createdAt" to simdi
                        )
                    )
                    islem.update(
                        davetRef,
                        mapOf<String, Any>(
                            "usedBy" to yakin.uid,
                            "usedAt" to simdi,
                            "active" to false
                        )
                    )
                    hastaId to hastaAdi
                }.await()
                hastaId = sonuc.first
                hastaAdi = sonuc.second
            }
            // Profil güncellemesi link belgesi oluştuktan sonra yapılır; kural linki doğrular.
            firestore.collection("users").document(yakin.uid)
                .update(
                    mapOf(
                        "linkedPatientId" to hastaId,
                        "updatedAt" to simdi
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Davet kabul edilemedi."
            Result.failure(Exception(mesaj))
        }
    } // davetiKabulEt: daveti tüketir, değiştirilemez bağlantıyı ve profil alanını yazar.

    // Yakının daha önce oluşmuş bağlantısını okur (profil güncellemesi yarım kaldıysa).
    suspend fun mevcutBaglantiyiGetir(uid: String): Result<RelativeLink?> {
        return try {
            val belge = firestore.collection("relativeLinks").document(uid).get().await()
            if (!belge.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    RelativeLink(
                        relativeUid = uid,
                        patientId = belge.getString("patientId") ?: "",
                        patientName = belge.getString("patientName") ?: "",
                        inviteCode = belge.getString("inviteCode") ?: "",
                        createdAt = (belge.get("createdAt") as? Number)?.toLong() ?: 0L
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("Bağlantı bilgisi okunamadı."))
        }
    } // mevcutBaglantiyiGetir: yarım kalan profil yazımını onarmak için kullanılır.

    // Var olan bağlantıyı kullanıcı profiline yeniden yazar.
    suspend fun baglantiyiProfileYaz(uid: String, hastaId: String): Result<Unit> {
        return try {
            firestore.collection("users").document(uid)
                .update(
                    mapOf(
                        "linkedPatientId" to hastaId,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Hasta bağlantısı profile yazılamadı."))
        }
    } // baglantiyiProfileYaz: link belgesi varsa profil alanını onarır.

    // Koordinatörün araç eklemesini/güncellemesini kaydeder.
    suspend fun aracKaydet(arac: Vehicle): Result<Unit> {
        if (arac.name.isBlank()) {
            return Result.failure(Exception("Araç adı zorunlu."))
        }
        if (arac.capacity !in 1..20) {
            return Result.failure(Exception("Araç kapasitesi 1-20 arasında olmalı."))
        }
        val veri = mapOf<String, Any>(
            "name" to arac.name.trim(),
            "plate" to arac.plate.trim(),
            "wheelchairAccessible" to arac.wheelchairAccessible,
            "capacity" to arac.capacity,
            "active" to arac.active
        )
        return try {
            val koleksiyon = firestore.collection("vehicles")
            if (arac.id.isBlank()) {
                koleksiyon.add(veri).await()
            } else {
                koleksiyon.document(arac.id).set(veri, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Araç kaydedilemedi. Lütfen tekrar deneyin."))
        }
    } // aracKaydet: ad, kapasite ve erişilebilirlik bilgisini koordinatör yetkisiyle yazar.

    // Koordinatörün personel eklemesini/güncellemesini kaydeder.
    suspend fun personelKaydet(personel: Personnel): Result<Unit> {
        if (personel.name.isBlank()) {
            return Result.failure(Exception("Personel adı zorunlu."))
        }
        val veri = mapOf<String, Any>(
            "name" to personel.name.trim(),
            "phone" to personel.phone.trim(),
            "active" to personel.active,
            "driverUid" to personel.driverUid.trim()
        )
        return try {
            val koleksiyon = firestore.collection("personnel")
            if (personel.id.isBlank()) {
                koleksiyon.add(veri).await()
            } else {
                koleksiyon.document(personel.id).set(veri, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Personel kaydedilemedi. Lütfen tekrar deneyin."))
        }
    } // personelKaydet: ad, telefon, aktiflik ve hesap bağlantısını yazar.

    // Aktif görevlileri Firestore'dan okur; koordinatör atama listesinde kullanılır.
    suspend fun personelGetir(aktifSadece: Boolean = true): Result<List<Personnel>> {
        return try {
            val belgeler = firestore.collection("personnel").get().await()
            val personeller = belgeler.documents
                .mapNotNull { it.toPersonnel() }
                .filter { !aktifSadece || it.active }
                .sortedBy { it.name }
            Result.success(personeller)
        } catch (e: Exception) {
            Result.failure(Exception("Görevli listesi alınamadı."))
        }
    } // personelGetir: atama için aktifleri, yönetim için tüm kayıtları getirir.

    // Koordinatörün araç/görevli atamasını kaydeder ve talebi PLANLANDI yapar.
    suspend fun atamaYap(
        talep: TransportRequest,
        arac: Vehicle,
        personel: Personnel,
        planlananAlinmaSaati: String,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talep.id)
            val simdi = System.currentTimeMillis()
            val gecikmeBekliyor = talep.readiness == ReadinessStatus.DELAYED &&
                !talep.delayOverride
            val batch = firestore.batch()
            batch.set(
                talepRef,
                mapOf<String, Any>(
                    "vehicleId" to arac.id,
                    "vehicleName" to arac.name,
                    "personnelId" to personel.id,
                    "personnel" to personel.name,
                    "personnelPhone" to personel.phone,
                    "plannedPickupTime" to planlananAlinmaSaati,
                    "status" to TransportStatus.PLANNED.name,
                    // Yeni atamada eski gecikme onayı geçersizdir; görevli yeniden onay bekler.
                    "delayOverride" to false,
                    "delayOverrideReason" to "",
                    "delayOverrideBy" to "",
                    "delayOverrideByName" to "",
                    "delayOverrideAt" to 0L,
                    "updatedAt" to simdi
                ),
                SetOptions.merge()
            )
            batch.set(
                talepRef.collection("history").document(),
                mapOf<String, Any>(
                    "type" to HistoryType.ASSIGNMENT.name,
                    "value" to TransportStatus.PLANNED.name,
                    "note" to "Araç: ${arac.name}, görevli: ${personel.name}",
                    "updatedBy" to guncelleyen.uid,
                    "updatedByName" to guncelleyen.name,
                    "timestamp" to simdi
                )
            )
            batch.set(
                firestore.collection("notifications").document(),
                bildirimVerisi(
                    hedef = BildirimHedefi.DRIVER,
                    userId = personel.driverUid,
                    personnelId = personel.id,
                    baslik = "Yeni yolculuk atandı",
                    mesaj = "${Guvenlik.adMaskele(talep.patientName)} • " +
                        "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime} • " +
                        "Alınma $planlananAlinmaSaati" +
                        if (gecikmeBekliyor) {
                            " • Hasta gecikmeli; koordinatör onayı bekleniyor"
                        } else {
                            ""
                        },
                    talepId = talep.id,
                    olusturanUid = guncelleyen.uid,
                    zaman = simdi
                )
            )
            batch.set(
                firestore.collection("notifications").document(),
                bildirimVerisi(
                    hedef = BildirimHedefi.PATIENT,
                    userId = talep.patientId,
                    personnelId = "",
                    baslik = "Araç ve görevli atandı",
                    mesaj = "${arac.name} • ${personel.name} • Alınma $planlananAlinmaSaati",
                    talepId = talep.id,
                    olusturanUid = guncelleyen.uid,
                    zaman = simdi
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Atama kaydedilemedi. Lütfen tekrar deneyin."))
        }
    } // atamaYap: atamayı, geçmişi ve iki tarafın bildirimlerini tek işlemde yazar.

    // Koordinatörün durum zincirindeki bir sonraki adıma geçmesini sağlar.
    suspend fun durumGuncelle(
        talepId: String,
        yeniDurum: TransportStatus,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talepId)
            val simdi = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.set(
                talepRef,
                mapOf<String, Any>(
                    "status" to yeniDurum.name,
                    "updatedAt" to simdi
                ),
                SetOptions.merge()
            )
            batch.set(
                talepRef.collection("history").document(),
                mapOf<String, Any>(
                    "type" to HistoryType.STATUS.name,
                    "value" to yeniDurum.name,
                    "note" to "",
                    "updatedBy" to guncelleyen.uid,
                    "updatedByName" to guncelleyen.name,
                    "timestamp" to simdi
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Durum güncellenemedi. Lütfen tekrar deneyin."))
        }
    } // durumGuncelle: durum değişikliğini ve zaman damgalı geçmişi birlikte kaydeder.

    // Görevlinin durum güncellemesini transaction içinde doğrular ve kaydeder.
    // Yoldaki hasta gecikmesi artık alımı kilitlemez; merkeze varışta dönüş planı hazırlanır.
    suspend fun durumGuncelleGuvenli(
        talepId: String,
        beklenenDurum: TransportStatus,
        yeniDurum: TransportStatus,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talepId)
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) {
                    throw IllegalStateException("Talep bulunamadı.")
                }
                if (belge.getString("status") != beklenenDurum.name) {
                    throw IllegalStateException("Talep durumu değişmiş. Listeyi yenileyin.")
                }
                val atananPersonelId = belge.getString("personnelId") ?: ""
                if (guncelleyen.role == UserRole.DRIVER &&
                    atananPersonelId != guncelleyen.personnelId
                ) {
                    throw IllegalStateException("Bu talep size atanmamış.")
                }
                val hastaId = belge.getString("patientId") ?: ""
                val hastaAdi = belge.getString("patientName") ?: ""
                val simdi = System.currentTimeMillis()
                val guncellemeler = mutableMapOf<String, Any>(
                    "status" to yeniDurum.name,
                    "updatedAt" to simdi
                )
                var donusPlaniZamani = 0L
                if (yeniDurum == TransportStatus.PICKED_UP ||
                    yeniDurum == TransportStatus.AT_CENTER
                ) {
                    // Yeni bacakta eski gecikme/ETA taşınmaz.
                    guncellemeler["driverDelayMinutes"] = 0
                    guncellemeler["driverDelayStatus"] = DriverDelayStatus.NONE.name
                    guncellemeler["driverDelayTarget"] = EtaHedefi.NONE.name
                    guncellemeler["driverDelayUpdatedAt"] = simdi
                    guncellemeler["etaBaseMinutes"] = 0
                    guncellemeler["etaMinutes"] = 0
                    guncellemeler["etaTarget"] = EtaHedefi.NONE.name
                    guncellemeler["etaUpdatedAt"] = 0L
                    guncellemeler["etaSource"] = ""
                }
                if (yeniDurum == TransportStatus.AT_CENTER) {
                    guncellemeler["centerArrivedAt"] = simdi
                    val roundTrip = belge.getBoolean("roundTrip") ?: false
                    if (roundTrip) {
                        val seansDakika = (belge.get("sessionDurationMinutes") as? Number)
                            ?.toInt() ?: Guvenlik.VARSAYILAN_SEANS_DAKIKA
                        val tamponDakika = (belge.get("returnBufferMinutes") as? Number)
                            ?.toInt() ?: Guvenlik.VARSAYILAN_DONUS_TAMPONU
                        val returnZamani = Guvenlik.donusZamaniHesapla(
                            merkezeVaris = simdi,
                            seansDakika = seansDakika,
                            tamponDakika = tamponDakika
                        ) ?: throw IllegalStateException(
                            "Dönüş zamanı hesaplanamadı; seans süresini kontrol edin."
                        )
                        donusPlaniZamani = returnZamani
                        guncellemeler["returnPlannedAt"] = returnZamani
                        guncellemeler["returnPlanStatus"] = ReturnPlanStatus.PENDING.name
                    }
                }
                islem.update(talepRef, guncellemeler)
                islem.set(
                    talepRef.collection("history").document(),
                    mapOf<String, Any>(
                        "type" to HistoryType.STATUS.name,
                        "value" to yeniDurum.name,
                        "note" to "",
                        "updatedBy" to guncelleyen.uid,
                        "updatedByName" to guncelleyen.name,
                        "timestamp" to simdi
                    )
                )
                val bildirim = durumBildirimi(yeniDurum, hastaAdi)
                if (bildirim != null && hastaId.isNotBlank()) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.COORDINATOR,
                            userId = "",
                            personnelId = "",
                            baslik = bildirim.first,
                            mesaj = bildirim.second,
                            talepId = talepId,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.PATIENT,
                            userId = hastaId,
                            personnelId = "",
                            baslik = bildirim.first,
                            mesaj = bildirim.second,
                            talepId = talepId,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                if (donusPlaniZamani > 0L) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.COORDINATOR,
                            userId = "",
                            personnelId = "",
                            baslik = "Dönüş planı onayı bekliyor",
                            mesaj = "${Guvenlik.adMaskele(hastaAdi)} • Dönüş: " +
                                Guvenlik.kisaZamanGorunen(donusPlaniZamani),
                            talepId = talepId,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Durum güncellenemedi."
            Result.failure(Exception(mesaj))
        }
    } // durumGuncelleGuvenli: eşzamanlılık, dönüş planı ve gecikme temizliğini yönetir.

    // Hastanın "Hazırım / Gecikeceğim" bildirimini kaydeder.
    // Gecikme gerekçesi ayrı bir koleksiyonda tutulur; görevli bu gerekçeyi okuyamaz.
    suspend fun hazirlikGuncelle(
        talep: TransportRequest,
        hazirlik: ReadinessStatus,
        gecikmeDakika: Int,
        gecikmeSebebi: String,
        gorevliMesaji: String,
        guncelleyen: AppUser
    ): Result<Unit> {
        val temizSebep = Guvenlik.metniTemizle(gecikmeSebebi)
            .take(Guvenlik.MAKS_GECIKME_SEBEBI)
        val temizMesaj = Guvenlik.metniTemizle(gorevliMesaji)
            .take(Guvenlik.MAKS_GOREVLI_MESAJI)
        if (hazirlik == ReadinessStatus.DELAYED) {
            if (!Guvenlik.gecikmeDakikaGecerliMi(gecikmeDakika)) {
                return Result.failure(
                    Exception(
                        "Gecikme süresi ${Guvenlik.MIN_GECIKME_DAKIKA}-" +
                            "${Guvenlik.MAKS_GECIKME_DAKIKA} dakika arasında olmalı."
                    )
                )
            }
            if (temizSebep.length < 3) {
                return Result.failure(Exception("Gecikme nedenini en az 3 karakterle yazın."))
            }
        }
        return try {
            val talepRef = firestore.collection("transportRequests").document(talep.id)
            val simdi = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.set(
                talepRef,
                mapOf<String, Any>(
                    "readiness" to hazirlik.name,
                    "delayMinutes" to if (hazirlik == ReadinessStatus.DELAYED) {
                        gecikmeDakika
                    } else {
                        0
                    },
                    "readinessUpdatedAt" to simdi,
                    // Hasta yeni bildirim yaptığında önceki koordinatör onayı geçersizleşir.
                    "delayOverride" to false,
                    "delayOverrideReason" to "",
                    "delayOverrideBy" to "",
                    "delayOverrideByName" to "",
                    "delayOverrideAt" to 0L,
                    "delayRescheduled" to false,
                    "delayPreviousSessionDate" to "",
                    "delayPreviousSessionTime" to "",
                    "delayPreviousPickupTime" to "",
                    // Görevliye mesaj yalnızca koordinatör onayından sonra talep belgesine taşınır.
                    "delayDriverMessage" to "",
                    "updatedAt" to simdi
                ),
                SetOptions.merge()
            )
            batch.set(
                talepRef.collection("history").document(),
                mapOf<String, Any>(
                    "type" to HistoryType.READINESS.name,
                    "value" to hazirlik.name,
                    "note" to if (hazirlik == ReadinessStatus.DELAYED) {
                        "$gecikmeDakika dk"
                    } else {
                        ""
                    },
                    "updatedBy" to guncelleyen.uid,
                    "updatedByName" to guncelleyen.name,
                    "timestamp" to simdi
                )
            )
            if (hazirlik == ReadinessStatus.DELAYED) {
                // Gizli gerekçe talep belgesine değil, yalnızca koordinatörün okuyabildiği
                // ayrı belgeye yazılır. Böylece görevli Firestore üzerinden de göremez.
                batch.set(
                    firestore.collection("delayReports").document(talep.id),
                    mapOf<String, Any>(
                        "requestId" to talep.id,
                        "patientId" to guncelleyen.uid,
                        "minutes" to gecikmeDakika,
                        "reason" to temizSebep,
                        "driverMessage" to temizMesaj,
                        "createdAt" to simdi,
                        "updatedAt" to simdi
                    )
                )
            }
            val baslik = if (hazirlik == ReadinessStatus.READY) {
                "Hasta hazır"
            } else {
                "Hasta gecikecek"
            }
            val mesaj = if (hazirlik == ReadinessStatus.READY) {
                "${Guvenlik.adMaskele(talep.patientName)} hazır olduğunu bildirdi."
            } else {
                "${Guvenlik.adMaskele(talep.patientName)} " +
                    "$gecikmeDakika dk gecikeceğini bildirdi. " +
                    "Gecikme nedeni koordinatör panelinde."
            }
            batch.set(
                firestore.collection("notifications").document(),
                bildirimVerisi(
                    hedef = BildirimHedefi.COORDINATOR,
                    userId = "",
                    personnelId = "",
                    baslik = baslik,
                    mesaj = mesaj,
                    talepId = talep.id,
                    olusturanUid = guncelleyen.uid,
                    zaman = simdi
                )
            )
            // Araç yoldayken hasta gecikmesi bildirimi onay beklemeden görevliye gider.
            // Araç henüz yola çıkmadıysa görevli, koordinatör onayından sonra bilgilendirilir.
            val soforeHemen = hazirlik == ReadinessStatus.READY ||
                (hazirlik == ReadinessStatus.DELAYED &&
                    talep.status == TransportStatus.ON_THE_WAY)
            if (soforeHemen && talep.personnelId.isNotBlank()) {
                batch.set(
                    firestore.collection("notifications").document(),
                    bildirimVerisi(
                        hedef = BildirimHedefi.DRIVER,
                        userId = "",
                        personnelId = talep.personnelId,
                        baslik = baslik,
                        mesaj = mesaj,
                        talepId = talep.id,
                        olusturanUid = guncelleyen.uid,
                        zaman = simdi
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Hazırlık bildirimi kaydedilemedi. Lütfen tekrar deneyin."))
        }
    } // hazirlikGuncelle: hazırlık, gizli gecikme raporu, geçmiş ve bildirimleri birlikte yazar.

    // Koordinatörün öncelik talebini resmi belge kontrolü sonrası doğrulamasını kaydeder.
    // Belge içeriği veya tanı saklanmaz; yalnızca doğrulama bilgisi ve kısa not tutulur.
    suspend fun oncelikDogrula(
        talep: TransportRequest,
        not: String,
        guncelleyen: AppUser
    ): Result<Unit> {
        val temizNot = Guvenlik.metniTemizle(not).take(200)
        return try {
            val talepRef = firestore.collection("transportRequests").document(talep.id)
            val simdi = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.set(
                talepRef,
                mapOf<String, Any>(
                    "priorityVerified" to true,
                    "priorityVerifiedBy" to guncelleyen.uid,
                    "priorityVerifiedByName" to guncelleyen.name,
                    "priorityVerifiedAt" to simdi,
                    "priorityVerificationNote" to temizNot,
                    "updatedAt" to simdi
                ),
                SetOptions.merge()
            )
            batch.set(
                talepRef.collection("history").document(),
                mapOf<String, Any>(
                    "type" to HistoryType.PRIORITY.name,
                    "value" to talep.priorityCategory.name,
                    "note" to temizNot,
                    "updatedBy" to guncelleyen.uid,
                    "updatedByName" to guncelleyen.name,
                    "timestamp" to simdi
                )
            )
            batch.set(
                firestore.collection("notifications").document(),
                bildirimVerisi(
                    hedef = BildirimHedefi.PATIENT,
                    userId = talep.patientId,
                    personnelId = "",
                    baslik = "Öncelik talebiniz doğrulandı",
                    mesaj = "${talep.priorityCategory.oncelikEtiketi} kategorisindeki önceliğiniz koordinatör tarafından doğrulandı.",
                    talepId = talep.id,
                    olusturanUid = guncelleyen.uid,
                    zaman = simdi
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Öncelik doğrulanamadı. Lütfen tekrar deneyin."))
        }
    } // oncelikDogrula: doğrulama bayrağını, geçmişi ve hasta bildirimini birlikte yazar.

    // Hastanın talebini iptal eder; seans doluluğunu azaltır ve taraflara haber verir.
    suspend fun talepIptalEt(
        talep: TransportRequest,
        sebep: String,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talep.id)
            val simdi = System.currentTimeMillis()
            val temizSebep = Guvenlik.metniTemizle(sebep).take(300)
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) throw IllegalStateException("Talep bulunamadı.")
                if (belge.getString("status") == TransportStatus.CANCELLED.name) {
                    // Zaten iptal edilmişse doluluk sayacı ikinci kez azaltılmaz.
                    return@runTransaction Unit
                }
                val merkezAdi = belge.getString("centerName") ?: talep.centerName
                val tarih = belge.getString("sessionDate") ?: talep.sessionDate
                val saat = belge.getString("sessionTime") ?: talep.sessionTime
                val slotId = Kapasite.slotBelgesiId(merkezAdi, tarih, saat)
                if (slotId != null) {
                    val slotRef = firestore.collection("slotAvailability").document(slotId)
                    val slot = islem.get(slotRef)
                    if (slot.exists()) {
                        val dolu = (slot.get("booked") as? Number)?.toInt() ?: 0
                        if (dolu > 0) {
                            islem.update(
                                slotRef,
                                mapOf("booked" to dolu - 1, "updatedAt" to simdi)
                            )
                        }
                    }
                }
                islem.update(
                    talepRef,
                    mapOf<String, Any>(
                        "status" to TransportStatus.CANCELLED.name,
                        "cancelledBy" to guncelleyen.uid,
                        "cancelReason" to temizSebep,
                        "cancelledAt" to simdi,
                        "updatedAt" to simdi
                    )
                )
                islem.set(
                    talepRef.collection("history").document(),
                    mapOf<String, Any>(
                        "type" to HistoryType.CANCELLATION.name,
                        "value" to TransportStatus.CANCELLED.name,
                        "note" to temizSebep,
                        "updatedBy" to guncelleyen.uid,
                        "updatedByName" to guncelleyen.name,
                        "timestamp" to simdi
                    )
                )
                val baslik = "Ulaşım talebi iptal edildi"
                val mesaj = "${Guvenlik.adMaskele(talep.patientName)} • " +
                    "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime} talebini iptal etti."
                islem.set(
                    firestore.collection("notifications").document(),
                    bildirimVerisi(
                        hedef = BildirimHedefi.COORDINATOR,
                        userId = "",
                        personnelId = "",
                        baslik = baslik,
                        mesaj = mesaj,
                        talepId = talep.id,
                        olusturanUid = guncelleyen.uid,
                        zaman = simdi
                    )
                )
                if (talep.personnelId.isNotBlank()) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.DRIVER,
                            userId = "",
                            personnelId = talep.personnelId,
                            baslik = baslik,
                            mesaj = mesaj,
                            talepId = talep.id,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Talep iptal edilemedi. Lütfen tekrar deneyin."))
        }
    } // talepIptalEt: iptal, doluluk azaltma, gerekçe geçmişi ve bildirimleri tek işlemde kaydeder.

    // Koordinatörün gecikmeli hastaya rağmen alım onayı vermesini sağlar.
    // Araç henüz yola çıkmadıysa seans ve alınma saati gecikme kadar ileri alınır.
    // Gizli gerekçe talep belgesine veya bildirim metnine yazılmaz.
    suspend fun gecikmeOnayla(
        talep: TransportRequest,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talep.id)
            val raporRef = firestore.collection("delayReports").document(talep.id)
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) {
                    throw IllegalStateException("Talep bulunamadı.")
                }
                val durum = runCatching {
                    TransportStatus.valueOf(belge.getString("status") ?: "")
                }.getOrNull() ?: throw IllegalStateException("Talep durumu okunamadı.")
                if (durum != TransportStatus.REQUESTED &&
                    durum != TransportStatus.PLANNED
                ) {
                    throw IllegalStateException(
                        "Araç yola çıktıktan sonra hasta gecikmesi bilgi amaçlıdır; " +
                            "koordinatör onayı gerekmez."
                    )
                }
                if (belge.getString("readiness") != ReadinessStatus.DELAYED.name) {
                    throw IllegalStateException("Hasta gecikmeli bildirmedi.")
                }
                if (belge.getBoolean("delayOverride") == true) {
                    throw IllegalStateException("Gecikme zaten onaylanmış.")
                }
                val dakika = (belge.get("delayMinutes") as? Number)?.toInt() ?: 0
                if (!Guvenlik.gecikmeDakikaGecerliMi(dakika)) {
                    throw IllegalStateException(
                        "Gecikme süresi geçersiz. Hasta yeniden bildirim yapmalı."
                    )
                }
                val eskiTarih = belge.getString("sessionDate") ?: ""
                val eskiSaat = belge.getString("sessionTime") ?: ""
                val eskiAlinma = belge.getString("plannedPickupTime") ?: ""
                val hastaId = belge.getString("patientId") ?: ""
                val hastaAdi = belge.getString("patientName") ?: ""
                val personelId = belge.getString("personnelId") ?: ""
                val merkezAdi = belge.getString("centerName") ?: ""
                val merkezKapasitesi = (belge.get("centerCapacity") as? Number)?.toInt() ?: 0
                // Onay yalnızca araç yola çıkmadan verilir; seans/alınma saati kayar.
                val yeniSeans = Guvenlik.zamaniErtele(eskiTarih, eskiSaat, dakika)
                    ?: throw IllegalStateException("Yeni seans saati hesaplanamadı.")
                val yeniAlinma = if (eskiAlinma.isNotBlank()) {
                    Guvenlik.zamaniErtele(eskiTarih, eskiAlinma, dakika)?.second ?: ""
                } else {
                    ""
                }
                val raporBelgesi = islem.get(raporRef)
                val raporMesaji = if (raporBelgesi.exists()) {
                    Guvenlik.metniTemizle(raporBelgesi.getString("driverMessage") ?: "")
                        .take(Guvenlik.MAKS_GOREVLI_MESAJI)
                } else {
                    ""
                }
                val simdi = System.currentTimeMillis()
                val guncellemeler = mutableMapOf<String, Any>(
                    "delayOverride" to true,
                    // Eski sürümden kalan gerekçe alanı temizlenir; gizli metin burada tutulmaz.
                    "delayOverrideReason" to "",
                    "delayOverrideBy" to guncelleyen.uid,
                    "delayOverrideByName" to guncelleyen.name,
                    "delayOverrideAt" to simdi,
                    "delayRescheduled" to true,
                    "delayPreviousSessionDate" to eskiTarih,
                    "delayPreviousSessionTime" to eskiSaat,
                    "delayPreviousPickupTime" to if (eskiAlinma.isNotBlank()) eskiAlinma else "",
                    // Görevliye yalnızca hastanın açıkça paylaştığı mesaj aktarılır.
                    "delayDriverMessage" to raporMesaji,
                    "updatedAt" to simdi
                )
                guncellemeler["sessionDate"] = yeniSeans.first
                guncellemeler["sessionTime"] = yeniSeans.second
                if (yeniAlinma.isNotBlank()) {
                    guncellemeler["plannedPickupTime"] = yeniAlinma
                }
                // Seans dilimi değişiyorsa doluluk sayacını eski dilimden yeni dilime taşı.
                // Tüm okumalar yazmadan önce yapılır; Firestore transaction kuralı budur.
                val eskiSlotId = Kapasite.slotBelgesiId(merkezAdi, eskiTarih, eskiSaat)
                val yeniSlotId = Kapasite.slotBelgesiId(merkezAdi, yeniSeans.first, yeniSeans.second)
                val eskiSlotRef = eskiSlotId?.let {
                    firestore.collection("slotAvailability").document(it)
                }
                val yeniSlotRef = yeniSlotId?.let {
                    firestore.collection("slotAvailability").document(it)
                }
                val eskiSlot = eskiSlotRef?.let { islem.get(it) }
                val yeniSlot = yeniSlotRef?.let { islem.get(it) }
                if (merkezKapasitesi > 0 &&
                    eskiSlotRef != null && yeniSlotRef != null &&
                    eskiSlotId != yeniSlotId
                ) {
                    if (eskiSlot?.exists() == true) {
                        val eskiDolu = (eskiSlot.get("booked") as? Number)?.toInt() ?: 0
                        if (eskiDolu > 0) {
                            islem.update(
                                eskiSlotRef,
                                mapOf("booked" to eskiDolu - 1, "updatedAt" to simdi)
                            )
                        }
                    }
                    val yeniDolu = if (yeniSlot?.exists() == true) {
                        (yeniSlot.get("booked") as? Number)?.toInt() ?: 0
                    } else {
                        0
                    }
                    if (yeniDolu >= merkezKapasitesi) {
                        throw IllegalStateException(
                            "Yeni seans dolu; gecikme onayı verilemez. " +
                                "Hastanın talebini farklı bir seansa taşıyın."
                        )
                    }
                    if (yeniSlot?.exists() == true) {
                        islem.update(
                            yeniSlotRef,
                            mapOf("booked" to yeniDolu + 1, "updatedAt" to simdi)
                        )
                    } else {
                        islem.set(
                            yeniSlotRef,
                            mapOf(
                                "centerName" to merkezAdi,
                                "date" to yeniSeans.first,
                                "session" to Kapasite.seansDilimi(yeniSeans.second)?.name.orEmpty(),
                                "booked" to 1,
                                "updatedAt" to simdi
                            )
                        )
                    }
                }
                islem.update(talepRef, guncellemeler)

                val seansMetni = "${Guvenlik.tarihIsoDanTurkce(eskiTarih)} $eskiSaat → " +
                    "${Guvenlik.tarihIsoDanTurkce(yeniSeans.first)} ${yeniSeans.second}"
                val alinmaMetni = if (yeniAlinma.isNotBlank()) {
                    "; alınma $eskiAlinma → $yeniAlinma"
                } else {
                    ""
                }
                islem.set(
                    talepRef.collection("history").document(),
                    mapOf<String, Any>(
                        "type" to HistoryType.OVERRIDE.name,
                        "value" to "DELAY_APPROVED",
                        "note" to "$dakika dk gecikme onaylandı; seans $seansMetni$alinmaMetni",
                        "updatedBy" to guncelleyen.uid,
                        "updatedByName" to guncelleyen.name,
                        "timestamp" to simdi
                    )
                )

                val maske = Guvenlik.adMaskele(hastaAdi)
                if (personelId.isNotBlank()) {
                    val soforMesaji = buildString {
                        append("$maske • $dakika dk gecikme onaylandı.")
                        append(
                            " Yeni seans: ${Guvenlik.tarihIsoDanTurkce(yeniSeans.first)} " +
                                "${yeniSeans.second}."
                        )
                        if (yeniAlinma.isNotBlank()) {
                            append(" Alınma: $yeniAlinma.")
                        }
                        if (raporMesaji.isNotBlank()) {
                            append(" Hasta mesajı: $raporMesaji")
                        }
                    }
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.DRIVER,
                            userId = "",
                            personnelId = personelId,
                            baslik = "Gecikme onaylandı",
                            mesaj = soforMesaji,
                            talepId = talep.id,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }

                val hastaMesaji = "Gecikmeniz onaylandı. Seans saatiniz " +
                    "${Guvenlik.tarihIsoDanTurkce(yeniSeans.first)} ${yeniSeans.second} " +
                    "olarak güncellendi."
                if (hastaId.isNotBlank()) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.PATIENT,
                            userId = hastaId,
                            personnelId = "",
                            baslik = "Gecikmeniz onaylandı",
                            mesaj = hastaMesaji,
                            talepId = talep.id,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Gecikme onayı kaydedilemedi."
            Result.failure(Exception(mesaj))
        }
    } // gecikmeOnayla: onayı, saat ertelemesini, geçmişi ve gizli olmayan bildirimleri yazar.

    // Görevlinin/aracın gecikeceğini süre ve gizli gerekçeyle bildirir.
    // Araç henüz yola çıkmadıysa koordinatör onayı beklenir; yola çıkmışsa bilgi anındadır.
    suspend fun gorevliGecikmeBildir(
        talepId: String,
        dakika: Int,
        sebep: String,
        guncelleyen: AppUser
    ): Result<Unit> {
        if (!Guvenlik.gecikmeDakikaGecerliMi(dakika)) {
            return Result.failure(
                Exception(
                    "Gecikme süresi ${Guvenlik.MIN_GECIKME_DAKIKA}-" +
                        "${Guvenlik.MAKS_GECIKME_DAKIKA} dakika arasında olmalı."
                )
            )
        }
        val temizSebep = Guvenlik.metniTemizle(sebep)
            .take(Guvenlik.MAKS_GECIKME_SEBEBI)
        if (temizSebep.length < 3) {
            return Result.failure(Exception("Gecikme nedenini en az 3 karakterle yazın."))
        }
        return try {
            val talepRef = firestore.collection("transportRequests").document(talepId)
            val raporRef = firestore.collection("driverDelayReports").document(talepId)
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) throw IllegalStateException("Talep bulunamadı.")
                val durum = runCatching {
                    TransportStatus.valueOf(belge.getString("status") ?: "")
                }.getOrNull() ?: throw IllegalStateException("Talep durumu okunamadı.")
                if (durum != TransportStatus.PLANNED &&
                    durum != TransportStatus.ON_THE_WAY &&
                    durum != TransportStatus.PICKED_UP &&
                    durum != TransportStatus.AT_CENTER
                ) {
                    throw IllegalStateException("Bu aşamada görevli gecikmesi bildirilemez.")
                }
                val personelId = belge.getString("personnelId") ?: ""
                if (guncelleyen.role == UserRole.DRIVER &&
                    personelId != guncelleyen.personnelId
                ) {
                    throw IllegalStateException("Bu talep size atanmamış.")
                }
                val hastaId = belge.getString("patientId") ?: ""
                val hastaAdi = belge.getString("patientName") ?: ""
                val etaBase = (belge.get("etaBaseMinutes") as? Number)?.toInt() ?: 0
                val yeniEta = if (etaBase > 0) etaBase + dakika else dakika
                val etaHedefi = when (durum) {
                    TransportStatus.ON_THE_WAY, TransportStatus.PLANNED -> EtaHedefi.HOME
                    TransportStatus.PICKED_UP -> EtaHedefi.CENTER
                    TransportStatus.AT_CENTER -> EtaHedefi.HOME
                    else -> EtaHedefi.HOME
                }
                val simdi = System.currentTimeMillis()
                islem.update(
                    talepRef,
                    mapOf<String, Any>(
                        "driverDelayMinutes" to dakika,
                        "driverDelayStatus" to DriverDelayStatus.INFO.name,
                        "driverDelayTarget" to etaHedefi.name,
                        "driverDelayUpdatedAt" to simdi,
                        "driverDelayApprovedAt" to 0L,
                        "driverDelayApprovedBy" to "",
                        "driverDelayApprovedByName" to "",
                        "etaMinutes" to yeniEta,
                        "etaTarget" to etaHedefi.name,
                        "etaUpdatedAt" to simdi,
                        "updatedAt" to simdi
                    )
                )
                islem.set(
                    raporRef,
                    mapOf<String, Any>(
                        "requestId" to talepId,
                        "driverUid" to guncelleyen.uid,
                        "personnelId" to personelId,
                        "minutes" to dakika,
                        "reason" to temizSebep,
                        "status" to DriverDelayStatus.INFO.name,
                        "createdAt" to simdi,
                        "updatedAt" to simdi
                    )
                )
                islem.set(
                    talepRef.collection("history").document(),
                    mapOf<String, Any>(
                        "type" to HistoryType.DRIVER_DELAY.name,
                        "value" to DriverDelayStatus.INFO.name,
                        "note" to "$dakika dk",
                        "updatedBy" to guncelleyen.uid,
                        "updatedByName" to guncelleyen.name,
                        "timestamp" to simdi
                    )
                )
                val maske = Guvenlik.adMaskele(hastaAdi)
                islem.set(
                    firestore.collection("notifications").document(),
                    bildirimVerisi(
                        hedef = BildirimHedefi.COORDINATOR,
                        userId = "",
                        personnelId = "",
                        baslik = "Görevli gecikecek",
                        mesaj = "$maske • $dakika dk gecikme bildirdi. " +
                            "Gecikme nedeni koordinatör panelinde.",
                        talepId = talepId,
                        olusturanUid = guncelleyen.uid,
                        zaman = simdi
                    )
                )
                if (hastaId.isNotBlank()) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.PATIENT,
                            userId = hastaId,
                            personnelId = "",
                            baslik = "Araç gecikmesi",
                            mesaj = "Tahmini varış: $yeniEta dk",
                            talepId = talepId,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Görevli gecikmesi kaydedilemedi."
            Result.failure(Exception(mesaj))
        }
    } // gorevliGecikmeBildir: süre/gizli gerekçeyi ve onay/bilgi durumunu birlikte yazar.

    // OSRM'den gerçek rota süresini alır ve talebin ETA alanlarını günceller.
    suspend fun etaGuncelle(
        talep: TransportRequest,
        hedef: EtaHedefi,
        baslangicEnlem: Double,
        baslangicBoylam: Double,
        hedefEnlem: Double,
        hedefBoylam: Double
    ): Result<Int> {
        if ((baslangicEnlem == 0.0 && baslangicBoylam == 0.0) ||
            (hedefEnlem == 0.0 && hedefBoylam == 0.0)
        ) {
            return Result.failure(Exception("Konum bilgisi eksik; ETA hesaplanamadı."))
        }
        val sure = etaProvider.rotaSuresiDakika(
            baslangicEnlem = baslangicEnlem,
            baslangicBoylam = baslangicBoylam,
            hedefEnlem = hedefEnlem,
            hedefBoylam = hedefBoylam
        ).getOrElse { hata ->
            return Result.failure(hata)
        }
        return try {
            val talepRef = firestore.collection("transportRequests").document(talep.id)
            var toplam = sure
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) throw IllegalStateException("Talep bulunamadı.")
                val durum = runCatching {
                    DriverDelayStatus.valueOf(belge.getString("driverDelayStatus") ?: "")
                }.getOrDefault(DriverDelayStatus.NONE)
                val dakika = (belge.get("driverDelayMinutes") as? Number)?.toInt() ?: 0
                val gecikmeHedefi = runCatching {
                    EtaHedefi.valueOf(belge.getString("driverDelayTarget") ?: "")
                }.getOrDefault(EtaHedefi.NONE)
                val gecikmeAktif = (durum == DriverDelayStatus.INFO ||
                    durum == DriverDelayStatus.APPROVED) &&
                    gecikmeHedefi == hedef
                toplam = if (gecikmeAktif) sure + dakika else sure
                val simdi = System.currentTimeMillis()
                islem.update(
                    talepRef,
                    mapOf<String, Any>(
                        "etaBaseMinutes" to sure,
                        "etaMinutes" to toplam,
                        "etaTarget" to hedef.name,
                        "etaUpdatedAt" to simdi,
                        "etaSource" to "OSRM",
                        "updatedAt" to simdi
                    )
                )
                Unit
            }.await()
            Result.success(toplam)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Tahmini varış kaydedilemedi."
            Result.failure(Exception(mesaj))
        }
    } // etaGuncelle: ücretsiz OSRM süresini, varsa onaylı görevli gecikmesiyle birleştirir.

    // Görevlinin hastaneden eve dönüş yolculuğunu başlatmasını sağlar.
    // Ayrı bir durum eklemeden ETA'nın dönüş ayağını tetikler.
    suspend fun donuseBasla(
        talepId: String,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talepId)
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) throw IllegalStateException("Talep bulunamadı.")
                if (belge.getString("status") != TransportStatus.AT_CENTER.name) {
                    throw IllegalStateException("Dönüş yalnızca merkeze ulaşıldıktan sonra başlar.")
                }
                if ((belge.get("returnStartedAt") as? Number)?.toLong() ?: 0L > 0L) {
                    throw IllegalStateException("Dönüş zaten başlatılmış.")
                }
                if (belge.getString("returnPlanStatus") != ReturnPlanStatus.APPROVED.name) {
                    throw IllegalStateException("Dönüş planı henüz koordinatör tarafından onaylanmadı.")
                }
                val personelId = belge.getString("personnelId") ?: ""
                if (guncelleyen.role == UserRole.DRIVER &&
                    personelId != guncelleyen.personnelId
                ) {
                    throw IllegalStateException("Bu talep size atanmamış.")
                }
                val simdi = System.currentTimeMillis()
                islem.update(
                    talepRef,
                    mapOf<String, Any>(
                        "returnStartedAt" to simdi,
                        "etaBaseMinutes" to 0,
                        "etaMinutes" to 0,
                        "etaTarget" to EtaHedefi.NONE.name,
                        "etaUpdatedAt" to 0L,
                        "etaSource" to "",
                        "updatedAt" to simdi
                    )
                )
                islem.set(
                    talepRef.collection("history").document(),
                    mapOf<String, Any>(
                        "type" to HistoryType.STATUS.name,
                        "value" to "RETURN_STARTED",
                        "note" to "Dönüş yolculuğu başladı",
                        "updatedBy" to guncelleyen.uid,
                        "updatedByName" to guncelleyen.name,
                        "timestamp" to simdi
                    )
                )
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Dönüş başlatılamadı."
            Result.failure(Exception(mesaj))
        }
    } // donuseBasla: dönüş zamanını ve ETA tetiklemesini hazırlar.

    // Koordinatörün otomatik dönüş planını uygun araç/görevliyle onaylamasını sağlar.
    suspend fun returnPlaniOnayla(
        talepId: String,
        arac: Vehicle,
        personel: Personnel,
        guncelleyen: AppUser
    ): Result<Unit> {
        return try {
            val talepRef = firestore.collection("transportRequests").document(talepId)
            firestore.runTransaction { islem ->
                val belge = islem.get(talepRef)
                if (!belge.exists()) throw IllegalStateException("Talep bulunamadı.")
                if (belge.getString("status") != TransportStatus.AT_CENTER.name) {
                    throw IllegalStateException("Dönüş planı yalnızca merkeze ulaşıldıktan sonra onaylanır.")
                }
                if (belge.getBoolean("roundTrip") != true) {
                    throw IllegalStateException("Bu talep gidiş-dönüş içermiyor.")
                }
                if (belge.getString("returnPlanStatus") != ReturnPlanStatus.PENDING.name) {
                    throw IllegalStateException("Onay bekleyen dönüş planı yok.")
                }
                val tekerlekli = belge.getBoolean("wheelchairRequired") ?: false
                val refakat = belge.getBoolean("companionRequired") ?: false
                if (tekerlekli && !arac.wheelchairAccessible) {
                    throw IllegalStateException(
                        "Seçilen araç tekerlekli sandalye uyumlu değil."
                    )
                }
                val gerekliKapasite = 1 + if (refakat) 1 else 0
                if (arac.capacity < gerekliKapasite) {
                    throw IllegalStateException(
                        "Seçilen araç hastanın ve refakatçinin kapasitesine uygun değil."
                    )
                }
                val returnZamani = (belge.get("returnPlannedAt") as? Number)?.toLong() ?: 0L
                if (returnZamani <= 0L) {
                    throw IllegalStateException("Dönüş zamanı bulunamadı.")
                }
                val hastaId = belge.getString("patientId") ?: ""
                val hastaAdi = belge.getString("patientName") ?: ""
                val simdi = System.currentTimeMillis()
                islem.update(
                    talepRef,
                    mapOf<String, Any>(
                        "vehicleId" to arac.id,
                        "vehicleName" to arac.name,
                        "personnelId" to personel.id,
                        "personnel" to personel.name,
                        "personnelPhone" to personel.phone,
                        "returnPlanStatus" to ReturnPlanStatus.APPROVED.name,
                        "returnApprovedAt" to simdi,
                        "returnApprovedBy" to guncelleyen.uid,
                        "returnApprovedByName" to guncelleyen.name,
                        "updatedAt" to simdi
                    )
                )
                islem.set(
                    talepRef.collection("history").document(),
                    mapOf<String, Any>(
                        "type" to HistoryType.RETURN.name,
                        "value" to ReturnPlanStatus.APPROVED.name,
                        "note" to "Dönüş: ${Guvenlik.kisaZamanGorunen(returnZamani)} • " +
                            "${arac.name}, ${personel.name}",
                        "updatedBy" to guncelleyen.uid,
                        "updatedByName" to guncelleyen.name,
                        "timestamp" to simdi
                    )
                )
                val saat = Guvenlik.kisaZamanGorunen(returnZamani)
                if (hastaId.isNotBlank()) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.PATIENT,
                            userId = hastaId,
                            personnelId = "",
                            baslik = "Dönüş planı onaylandı",
                            mesaj = "Dönüş: $saat • ${arac.name} • ${personel.name}",
                            talepId = talepId,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                if (personel.id.isNotBlank()) {
                    islem.set(
                        firestore.collection("notifications").document(),
                        bildirimVerisi(
                            hedef = BildirimHedefi.DRIVER,
                            userId = personel.driverUid,
                            personnelId = personel.id,
                            baslik = "Dönüş görevi onaylandı",
                            mesaj = "${Guvenlik.adMaskele(hastaAdi)} • Dönüş: $saat",
                            talepId = talepId,
                            olusturanUid = guncelleyen.uid,
                            zaman = simdi
                        )
                    )
                }
                Unit
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            val mesaj = e.cause?.message ?: e.message ?: "Dönüş planı onaylanamadı."
            Result.failure(Exception(mesaj))
        }
    } // returnPlaniOnayla: uygunluk kontrolü, dönüş ataması, geçmiş ve bildirimleri yazar.

    // Bildirim belgesini tek başına gönderir; çağıran tarafta hata yutulabilir.
    private suspend fun bildirimGonder(
        hedef: BildirimHedefi,
        userId: String,
        personnelId: String,
        baslik: String,
        mesaj: String,
        talepId: String,
        olusturanUid: String,
        zaman: Long = System.currentTimeMillis()
    ) {
        firestore.collection("notifications").add(
            bildirimVerisi(
                hedef = hedef,
                userId = userId,
                personnelId = personnelId,
                baslik = baslik,
                mesaj = mesaj,
                talepId = talepId,
                olusturanUid = olusturanUid,
                zaman = zaman
            )
        ).await()
    } // bildirimGonder: talep dışındaki tek seferlik bildirimler için kullanılır.

    // Bildirim belgesinin Firestore alanlarını üretir.
    private fun bildirimVerisi(
        hedef: BildirimHedefi,
        userId: String,
        personnelId: String,
        baslik: String,
        mesaj: String,
        talepId: String,
        olusturanUid: String,
        zaman: Long
    ): Map<String, Any> {
        return mapOf(
            "audience" to hedef.name,
            "userId" to userId,
            "personnelId" to personnelId,
            "title" to baslik,
            "message" to mesaj,
            "requestId" to talepId,
            "read" to false,
            "createdBy" to olusturanUid,
            "createdAt" to zaman
        )
    } // bildirimVerisi: Firestore'a yazılacak bildirim alanlarını tek yerde tanımlar.

    // Durum adımına göre koordinatör ve hastaya gösterilecek bildirim metnini üretir.
    private fun durumBildirimi(
        durum: TransportStatus,
        hastaAdi: String
    ): Pair<String, String>? {
        val maske = Guvenlik.adMaskele(hastaAdi)
        return when (durum) {
            TransportStatus.ON_THE_WAY -> "Görevli yolda" to "$maske için görevli yola çıktı."
            TransportStatus.PICKED_UP -> "Hasta alındı" to "$maske araca alındı."
            TransportStatus.AT_CENTER -> "Merkeze ulaşıldı" to "$maske diyaliz merkezine ulaştı."
            TransportStatus.DROPPED_HOME -> "Yolculuk tamamlandı" to "$maske eve bırakıldı."
            else -> null
        }
    } // durumBildirimi: yalnızca anlamlı durum adımlarında bildirim üretir.

    // Firestore belgesini uygulama modeline dönüştürür; eksik alanlarda güvenli varsayılan kullanır.
    private fun DocumentSnapshot.toTransportRequest(): TransportRequest? {
        val patientId = getString("patientId") ?: return null
        val durum = runCatching {
            TransportStatus.valueOf(getString("status") ?: "")
        }.getOrDefault(TransportStatus.REQUESTED)
        val hazirlik = runCatching {
            ReadinessStatus.valueOf(getString("readiness") ?: "")
        }.getOrDefault(ReadinessStatus.NOT_SET)

        return TransportRequest(
            id = id,
            patientId = patientId,
            patientName = getString("patientName") ?: "",
            sessionDate = getString("sessionDate") ?: "",
            sessionTime = getString("sessionTime") ?: "",
            sessionDurationMinutes = sayiAl("sessionDurationMinutes").toInt()
                .takeIf { it > 0 } ?: Guvenlik.VARSAYILAN_SEANS_DAKIKA,
            centerName = getString("centerName") ?: "",
            centerCapacity = sayiAl("centerCapacity").toInt(),
            centerLat = ondalikAl("centerLat"),
            centerLng = ondalikAl("centerLng"),
            pickupAddress = getString("pickupAddress") ?: "",
            pickupLat = ondalikAl("pickupLat"),
            pickupLng = ondalikAl("pickupLng"),
            roundTrip = getBoolean("roundTrip") ?: false,
            companionRequired = getBoolean("companionRequired") ?: false,
            companionName = getString("companionName") ?: "",
            companionPhone = getString("companionPhone") ?: "",
            companionRelation = getString("companionRelation") ?: "",
            wheelchairRequired = getBoolean("wheelchairRequired") ?: false,
            note = getString("note") ?: "",
            status = durum,
            vehicleId = getString("vehicleId") ?: "",
            vehicleName = getString("vehicleName") ?: "",
            personnelId = getString("personnelId") ?: "",
            personnel = getString("personnel") ?: "",
            personnelPhone = getString("personnelPhone") ?: "",
            plannedPickupTime = getString("plannedPickupTime") ?: "",
            readiness = hazirlik,
            delayMinutes = sayiAl("delayMinutes").toInt(),
            readinessUpdatedAt = sayiAl("readinessUpdatedAt"),
            delayOverride = getBoolean("delayOverride") ?: false,
            delayOverrideReason = getString("delayOverrideReason") ?: "",
            delayOverrideBy = getString("delayOverrideBy") ?: "",
            delayOverrideByName = getString("delayOverrideByName") ?: "",
            delayOverrideAt = sayiAl("delayOverrideAt"),
            delayRescheduled = getBoolean("delayRescheduled") ?: false,
            delayPreviousSessionDate = getString("delayPreviousSessionDate") ?: "",
            delayPreviousSessionTime = getString("delayPreviousSessionTime") ?: "",
            delayPreviousPickupTime = getString("delayPreviousPickupTime") ?: "",
            delayDriverMessage = getString("delayDriverMessage") ?: "",
            driverDelayMinutes = sayiAl("driverDelayMinutes").toInt(),
            driverDelayStatus = runCatching {
                DriverDelayStatus.valueOf(getString("driverDelayStatus") ?: "")
            }.getOrDefault(DriverDelayStatus.NONE),
            driverDelayTarget = runCatching {
                EtaHedefi.valueOf(getString("driverDelayTarget") ?: "")
            }.getOrDefault(EtaHedefi.NONE),
            driverDelayUpdatedAt = sayiAl("driverDelayUpdatedAt"),
            driverDelayApprovedAt = sayiAl("driverDelayApprovedAt"),
            driverDelayApprovedBy = getString("driverDelayApprovedBy") ?: "",
            driverDelayApprovedByName = getString("driverDelayApprovedByName") ?: "",
            etaBaseMinutes = sayiAl("etaBaseMinutes").toInt(),
            etaMinutes = sayiAl("etaMinutes").toInt(),
            etaTarget = runCatching {
                EtaHedefi.valueOf(getString("etaTarget") ?: "")
            }.getOrDefault(EtaHedefi.NONE),
            etaUpdatedAt = sayiAl("etaUpdatedAt"),
            etaSource = getString("etaSource") ?: "",
            centerArrivedAt = sayiAl("centerArrivedAt"),
            returnBufferMinutes = sayiAl("returnBufferMinutes").toInt()
                .takeIf { it in 0..60 } ?: Guvenlik.VARSAYILAN_DONUS_TAMPONU,
            returnPlannedAt = sayiAl("returnPlannedAt"),
            returnPlanStatus = runCatching {
                ReturnPlanStatus.valueOf(getString("returnPlanStatus") ?: "")
            }.getOrDefault(ReturnPlanStatus.NONE),
            returnApprovedAt = sayiAl("returnApprovedAt"),
            returnApprovedBy = getString("returnApprovedBy") ?: "",
            returnApprovedByName = getString("returnApprovedByName") ?: "",
            returnStartedAt = sayiAl("returnStartedAt"),
            cancelledBy = getString("cancelledBy") ?: "",
            cancelReason = getString("cancelReason") ?: "",
            cancelledAt = sayiAl("cancelledAt"),
            priorityCategory = runCatching {
                OncelikKategorisi.valueOf(getString("priorityCategory") ?: "")
            }.getOrDefault(OncelikKategorisi.NONE),
            priorityVerified = getBoolean("priorityVerified") ?: false,
            priorityVerifiedBy = getString("priorityVerifiedBy") ?: "",
            priorityVerifiedByName = getString("priorityVerifiedByName") ?: "",
            priorityVerifiedAt = sayiAl("priorityVerifiedAt"),
            priorityVerificationNote = getString("priorityVerificationNote") ?: "",
            createdBy = getString("createdBy") ?: "",
            createdAt = sayiAl("createdAt"),
            updatedAt = sayiAl("updatedAt")
        )
    } // toTransportRequest: Firestore'daki ham veriyi uygulamanın tip güvenli modeline çevirir.

    // Durum geçmişi belgesini modele dönüştürür.
    private fun DocumentSnapshot.toHistoryEvent(): HistoryEvent? {
        val turMetni = getString("type") ?: return null
        val tur = runCatching { HistoryType.valueOf(turMetni) }
            .getOrDefault(HistoryType.NOTE)
        return HistoryEvent(
            id = id,
            type = tur,
            value = getString("value") ?: "",
            note = getString("note") ?: "",
            updatedBy = getString("updatedBy") ?: "",
            updatedByName = getString("updatedByName") ?: "",
            timestamp = sayiAl("timestamp")
        )
    } // toHistoryEvent: geçmiş alt koleksiyonunu okunabilir modele taşır.

    // Gizli gecikme raporu belgesini modele dönüştürür.
    private fun DocumentSnapshot.toDelayReport(): DelayReport? {
        val requestId = getString("requestId") ?: return null
        return DelayReport(
            requestId = requestId,
            patientId = getString("patientId") ?: "",
            minutes = sayiAl("minutes").toInt(),
            reason = getString("reason") ?: "",
            driverMessage = getString("driverMessage") ?: "",
            createdAt = sayiAl("createdAt"),
            updatedAt = sayiAl("updatedAt")
        )
    } // toDelayReport: koordinatörün gördüğü süre, gerekçe ve görevli mesajını modele taşır.

    // Görevli gecikme raporunu modele dönüştürür.
    private fun DocumentSnapshot.toDriverDelayReport(): DriverDelayReport? {
        val requestId = getString("requestId") ?: return null
        return DriverDelayReport(
            requestId = requestId,
            driverUid = getString("driverUid") ?: "",
            personnelId = getString("personnelId") ?: "",
            minutes = sayiAl("minutes").toInt(),
            reason = getString("reason") ?: "",
            status = runCatching {
                DriverDelayStatus.valueOf(getString("status") ?: "")
            }.getOrDefault(DriverDelayStatus.PENDING),
            createdAt = sayiAl("createdAt"),
            updatedAt = sayiAl("updatedAt")
        )
    } // toDriverDelayReport: koordinatörün göreceği gizli görevli gerekçesini modele taşır.

    // Firestore belgesini araç modeline dönüştürür.
    private fun DocumentSnapshot.toVehicle(): Vehicle? {
        val ad = getString("name") ?: return null
        return Vehicle(
            id = id,
            name = ad,
            plate = getString("plate") ?: "",
            wheelchairAccessible = getBoolean("wheelchairAccessible") ?: false,
            capacity = sayiAl("capacity").toInt().coerceAtLeast(1),
            active = getBoolean("active") ?: true
        )
    } // toVehicle: araç belgesindeki kapasite ve erişilebilirlik bilgisini modele taşır.

    // Firestore personel belgesini uygulama modeline dönüştürür.
    private fun DocumentSnapshot.toPersonnel(): Personnel? {
        val ad = getString("name") ?: return null
        return Personnel(
            id = id,
            name = ad,
            phone = getString("phone") ?: "",
            active = getBoolean("active") ?: true,
            driverUid = getString("driverUid") ?: ""
        )
    } // toPersonnel: görevli listesindeki ad, telefon ve hesap bağlantısını modele taşır.

    // Firestore merkez belgesini uygulama modeline dönüştürür.
    private fun DocumentSnapshot.toDiyalizMerkezi(): DiyalizMerkezi? {
        val ad = getString("name") ?: return null
        return DiyalizMerkezi(
            il = getString("province") ?: "",
            ilce = getString("district") ?: "",
            ad = ad,
            adres = getString("address") ?: "",
            id = id,
            kapasitePerSeans = sayiAl("capacityPerSession").toInt()
        )
    } // toDiyalizMerkezi: centers koleksiyonundaki merkezleri il/ilçe seçimine uygun hale getirir.

    // Firestore sayı alanlarını Long/Double ayrımından bağımsız okur.
    private fun DocumentSnapshot.sayiAl(alan: String): Long {
        return when (val deger = get(alan)) {
            is Long -> deger
            is Number -> deger.toLong()
            else -> 0L
        }
    } // sayiAl: konsoldan girilen sayı türü farklı olsa da çökme olmadan değer okur.

    // Firestore koordinat alanlarını ondalık sayı olarak okur.
    private fun DocumentSnapshot.ondalikAl(alan: String): Double {
        return when (val deger = get(alan)) {
            is Double -> deger
            is Number -> deger.toDouble()
            else -> 0.0
        }
    } // ondalikAl: harita koordinatlarının tip farklılıklarında güvenli dönüşüm sağlar.
}
