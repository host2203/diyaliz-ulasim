package com.pigasor.diyalizulasim.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pigasor.diyalizulasim.model.Bildirim
import com.pigasor.diyalizulasim.model.BildirimHedefi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Uygulama içi bildirimleri gerçek zamanlı okur ve okundu durumunu günceller.
class BildirimRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    // Rolün bildirim kutusunu canlı dinler; koordinatör tüm koordinatör bildirimlerini görür.
    fun bildirimleriDinle(
        hedef: BildirimHedefi,
        userId: String,
        personnelId: String
    ): Flow<List<Bildirim>> = callbackFlow {
        var sorgu: Query = firestore.collection("notifications")
            .whereEqualTo("audience", hedef.name)
        if (hedef == BildirimHedefi.PATIENT) {
            sorgu = sorgu.whereEqualTo("userId", userId)
        } else if (hedef == BildirimHedefi.DRIVER) {
            sorgu = sorgu.whereEqualTo("personnelId", personnelId)
        } // Hasta ve görevli yalnızca kendi bildirimlerini sorgular.
        val kayit = sorgu.addSnapshotListener { snapshot, hata ->
            if (hata != null) {
                close(hata)
                return@addSnapshotListener
            }
            val bildirimler = snapshot?.documents
                ?.mapNotNull { it.toBildirim() }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(bildirimler)
        }
        awaitClose { kayit.remove() }
    } // bildirimleriDinle: çift eşitlik filtresi kullanır; ek sıralama indeksi gerekmez.

    // Seçilen bildirimleri okundu olarak işaretler.
    suspend fun okunduIsaretle(bildirimIdleri: List<String>): Result<Unit> {
        if (bildirimIdleri.isEmpty()) return Result.success(Unit)
        return try {
            val simdi = System.currentTimeMillis()
            val batch = firestore.batch()
            bildirimIdleri.forEach { id ->
                batch.update(
                    firestore.collection("notifications").document(id),
                    mapOf("read" to true, "readAt" to simdi)
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Bildirimler okundu olarak işaretlenemedi."))
        }
    } // okunduIsaretle: yalnızca okundu alanlarını değiştirir; kural da bunu doğrular.

    // Firestore bildirim belgesini uygulama modeline dönüştürür.
    private fun DocumentSnapshot.toBildirim(): Bildirim? {
        val baslik = getString("title") ?: return null
        val hedef = runCatching {
            BildirimHedefi.valueOf(getString("audience") ?: "")
        }.getOrDefault(BildirimHedefi.PATIENT)
        return Bildirim(
            id = id,
            hedef = hedef,
            userId = getString("userId") ?: "",
            personnelId = getString("personnelId") ?: "",
            baslik = baslik,
            mesaj = getString("message") ?: "",
            talepId = getString("requestId") ?: "",
            okundu = getBoolean("read") ?: false,
            createdBy = getString("createdBy") ?: "",
            createdAt = sayiAl("createdAt")
        )
    } // toBildirim: eksik alanlarda varsayılan kullanarak bildirim listesinin çökmesini önler.

    // Firestore sayı alanlarını Long/Double ayrımından bağımsız okur.
    private fun DocumentSnapshot.sayiAl(alan: String): Long {
        return when (val deger = get(alan)) {
            is Long -> deger
            is Number -> deger.toLong()
            else -> 0L
        }
    } // sayiAl: konsoldan girilen sayı türü farklı olsa da çökme olmadan değer okur.
}
