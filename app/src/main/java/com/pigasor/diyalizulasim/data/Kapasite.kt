package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.model.SeansDilimi
import com.pigasor.diyalizulasim.security.Guvenlik
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Merkez kapasitesi, seans dilimleri ve doluluk belgesi kimlikleri için saf yardımcılar.
object Kapasite {

    const val MAKS_SEANS_KAPASITESI = 50

    // Saati üç seans diliminden birine yerleştirir; geçersiz saatte null döner.
    fun seansDilimi(saat: String): SeansDilimi? {
        val dakika = Guvenlik.saatDakikayaCevir(saat) ?: return null
        return when {
            dakika < 12 * 60 -> SeansDilimi.SABAH
            dakika < 17 * 60 -> SeansDilimi.OGLEDEN_SONRA
            else -> SeansDilimi.AKSAM
        }
    } // seansDilimi: 12:00 ve 17:00 sınırlarını kullanarak günü üçe böler.

    // Kapasite değerinin kabul edilen aralıkta olup olmadığını kontrol eder.
    fun kapasiteGecerliMi(kapasite: Int): Boolean {
        return kapasite in 0..MAKS_SEANS_KAPASITESI
    } // kapasiteGecerliMi: 0 (kontrol kapalı) ile 50 arasındaki değerleri kabul eder.

    // Merkez adından Firestore'da kullanılacak sabit bir belge kimliği üretir.
    fun merkezBelgesiId(merkezAdi: String): String? {
        val temel = temelAnahtar(merkezAdi) ?: return null
        return "mk_$temel"
    } // merkezBelgesiId: demo merkezlerin centers koleksiyonuna yazılmasını sağlar.

    // Merkez/tarih/seans için doluluk belgesi kimliğini üretir.
    fun slotBelgesiId(merkezAdi: String, tarihIso: String, seans: SeansDilimi): String? {
        val temel = temelAnahtar(merkezAdi) ?: return null
        val tarih = tarihIso.takeIf { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) } ?: return null
        return "slot_${temel}_${tarih}_${seans.name.lowercase(Locale.ROOT)}"
    } // slotBelgesiId: aynı merkez/tarih/seans için tüm istemcilerin aynı belgeyi kullanmasını sağlar.

    // Saat bilgisinden doluluk belgesi kimliğini üretir.
    fun slotBelgesiId(merkezAdi: String, tarihIso: String, saat: String): String? {
        val seans = seansDilimi(saat) ?: return null
        return slotBelgesiId(merkezAdi, tarihIso, seans)
    } // slotBelgesiId: formdaki HH:mm saatini seans dilimine çevirir.

    // ISO tarihe gün ekler; geçersiz tarihte null döner.
    fun tarihEkle(tarihIso: String, gun: Int): String? {
        val zaman = Guvenlik.seansZamani(tarihIso, "00:00") ?: return null
        val takvim = Calendar.getInstance().apply {
            timeInMillis = zaman
            add(Calendar.DAY_OF_MONTH, gun)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(takvim.time)
    } // tarihEkle: alternatif seanslar için ay/yıl geçişlerini doğru hesaplar.

    // Bugünün ISO tarihini döndürür.
    fun bugunIso(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    } // bugunIso: seansın geçmişte kalmadığını kontrol etmek için kullanılır.

    // ISO tarihi formun beklediği GGAAYYYY rakamlarına çevirir.
    fun tarihIsoDanRakam(tarihIso: String): String? {
        val parcalar = tarihIso.split("-")
        if (parcalar.size != 3) return null
        val yil = parcalar[0]
        val ay = parcalar[1]
        val gun = parcalar[2]
        if (yil.length != 4 || ay.length != 2 || gun.length != 2) return null
        return "$gun$ay$yil"
    } // tarihIsoDanRakam: alternatif seans seçildiğinde tarih alanını doldurur.

    // HH:mm saatini formun beklediği SSDD rakamlarına çevirir.
    fun saatDanRakam(saat: String): String? {
        val dakika = Guvenlik.saatDakikayaCevir(saat) ?: return null
        return "%02d%02d".format(dakika / 60, dakika % 60)
    } // saatDanRakam: alternatif seans seçildiğinde saat alanını doldurur.

    // Merkez adını küçük harfe indirip Firestore belge kimliğine uygun hale getirir.
    private fun temelAnahtar(merkezAdi: String): String? {
        val temiz = merkezAdi.trim().lowercase(Locale.ROOT)
        if (temiz.isBlank()) return null
        val slug = temiz
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "merkez" }
        val hash = Integer.toHexString(temiz.hashCode())
        return "${slug}_$hash"
    } // temelAnahtar: Türkçe karakterleri güvenli biçime çevirir ve çakışmayı hash ile azaltır.
}
