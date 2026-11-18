package com.pigasor.diyalizulasim.security

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Kullanıcı girdilerini doğrulayan ve kişisel verileri maskeleyen yardımcılar.
object Guvenlik {

    const val MAKS_AD_UZUNLUGU = 80
    const val MAKS_ADRES_UZUNLUGU = 250
    const val MAKS_MERKEZ_UZUNLUGU = 100
    const val MAKS_NOT_UZUNLUGU = 500
    const val MIN_SIFRE_UZUNLUGU = 8
    const val MIN_GECIKME_DAKIKA = 5
    const val MAKS_GECIKME_DAKIKA = 180
    const val MAKS_GECIKME_SEBEBI = 300
    const val MAKS_GOREVLI_MESAJI = 300
    const val MIN_SEANS_DAKIKA = 60
    const val MAKS_SEANS_DAKIKA = 720
    const val VARSAYILAN_SEANS_DAKIKA = 240
    const val VARSAYILAN_DONUS_TAMPONU = 15

    // E-posta adresinin temel biçim kurallarına uyup uymadığını kontrol eder.
    fun emailGecerliMi(email: String): Boolean {
        val temizEmail = email.trim()
        return temizEmail.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
    } // emailGecerliMi: boş veya hatalı e-postaların Firebase'e gönderilmesini engeller.

    // Şifrenin en az 8 karakter olmasını ve harf ile rakam içermesini ister.
    fun sifreGecerliMi(sifre: String): Boolean {
        return sifre.length >= MIN_SIFRE_UZUNLUGU &&
            sifre.any { it.isLetter() } &&
            sifre.any { it.isDigit() }
    } // sifreGecerliMi: zayıf şifrelerin hesap oluşturmasını engeller.

    // Tarih rakamlarının hata nedenini açıklar; geçerliyse null döndürür.
    fun tarihHataMesaji(rakamlar: String): String? {
        if (rakamlar.length != 8 || !rakamlar.all { it.isDigit() }) {
            return "Tarihi 8 rakam olarak girin (GGAAYYYY)."
        }
        val gun = rakamlar.substring(0, 2).toInt()
        val ay = rakamlar.substring(2, 4).toInt()
        val yil = rakamlar.substring(4, 8).toInt()
        if (yil !in 1900..2100) return "Yıl 1900 ile 2100 arasında olmalı."
        if (ay !in 1..12) return "Ay 1 ile 12 arasında olmalı."
        if (gun < 1) return "Gün 1 ile 31 arasında olmalı."
        val aylar = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val artikYil = (yil % 4 == 0 && yil % 100 != 0) || yil % 400 == 0
        val maksGun = if (ay == 2 && artikYil) 29 else aylar[ay - 1]
        if (gun > maksGun) {
            val ayAdi = arrayOf(
                "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
            )[ay - 1]
            return "$ayAdi ayı $maksGun gündür. Lütfen günü kontrol edin."
        }
        return null
    } // tarihHataMesaji: 31 Kasım gibi tarihlerde kullanıcıya ayın kaç gün olduğunu söyler.

    // Tarihin geçerli olup olmadığını boolean olarak döndürür.
    fun tarihRakamlariGecerliMi(rakamlar: String): Boolean {
        return tarihHataMesaji(rakamlar) == null
    } // tarihRakamlariGecerliMi: diğer doğrulamalar için hata mesajını boolean'a çevirir.

    // GGAAYYYY rakamlarını Firestore'da tutulacak YYYY-AA-GG biçimine çevirir.
    fun tarihRakamdanIso(rakamlar: String): String? {
        if (!tarihRakamlariGecerliMi(rakamlar)) return null
        val gun = rakamlar.substring(0, 2)
        val ay = rakamlar.substring(2, 4)
        val yil = rakamlar.substring(4, 8)
        return "$yil-$ay-$gun"
    } // tarihRakamdanIso: kullanıcının gün-ay-yıl girişini veritabanı biçimine dönüştürür.

    // Firestore'daki YYYY-AA-GG tarihini ekranda GG.AA.YYYY olarak gösterir.
    fun tarihIsoDanTurkce(iso: String): String {
        val parcalar = iso.split("-")
        return if (parcalar.size == 3) {
            "${parcalar[2]}.${parcalar[1]}.${parcalar[0]}"
        } else {
            iso
        }
    } // tarihIsoDanTurkce: kullanıcıya tanıdık gün-ay-yıl biçimini gösterir.

    // Saat rakamlarının hata nedenini açıklar; geçerliyse null döndürür.
    fun saatHataMesaji(rakamlar: String): String? {
        if (rakamlar.length != 4 || !rakamlar.all { it.isDigit() }) {
            return "Saati 4 rakam olarak girin (SSDD)."
        }
        val saat = rakamlar.substring(0, 2).toInt()
        val dakika = rakamlar.substring(2, 4).toInt()
        if (saat !in 0..23) return "Saat 00 ile 23 arasında olmalı."
        if (dakika !in 0..59) return "Dakika 00 ile 59 arasında olmalı."
        return null
    } // saatHataMesaji: 25:70 gibi saatlerde nedeni açıkça söyler.

    // Saatin geçerli olup olmadığını boolean olarak döndürür.
    fun saatRakamlariGecerliMi(rakamlar: String): Boolean {
        return saatHataMesaji(rakamlar) == null
    } // saatRakamlariGecerliMi: diğer doğrulamalar için hata mesajını boolean'a çevirir.

    // SSDD rakamlarını Firestore'da tutulacak SS:DD biçimine çevirir.
    fun saatRakamdanGorunen(rakamlar: String): String? {
        if (!saatRakamlariGecerliMi(rakamlar)) return null
        return "${rakamlar.substring(0, 2)}:${rakamlar.substring(2, 4)}"
    } // saatRakamdanGorunen: kullanıcının bitişik saat girişini okunur biçime çevirir.

    // Girilen tarih ve saatin cihazın mevcut zamanından sonra olup olmadığını kontrol eder.
    fun tarihSaatGelecekteMi(tarihRakam: String, saatRakam: String): Boolean {
        if (!tarihRakamlariGecerliMi(tarihRakam) || !saatRakamlariGecerliMi(saatRakam)) return false
        val gun = tarihRakam.substring(0, 2).toInt()
        val ay = tarihRakam.substring(2, 4).toInt()
        val yil = tarihRakam.substring(4, 8).toInt()
        val saat = saatRakam.substring(0, 2).toInt()
        val dakika = saatRakam.substring(2, 4).toInt()
        val takvim = Calendar.getInstance().apply {
            set(Calendar.YEAR, yil)
            set(Calendar.MONTH, ay - 1)
            set(Calendar.DAY_OF_MONTH, gun)
            set(Calendar.HOUR_OF_DAY, saat)
            set(Calendar.MINUTE, dakika)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return takvim.timeInMillis > System.currentTimeMillis()
    } // tarihSaatGelecekteMi: geçmiş tarih/saatle talep oluşturulmasını engeller.

    // Firestore'daki ISO tarih ve HH:MM saatten zaman damgası üretir.
    fun seansZamani(tarihIso: String, saat: String): Long? {
        val tarihParcalari = tarihIso.split("-")
        val saatParcalari = saat.split(":")
        if (tarihParcalari.size != 3 || saatParcalari.size != 2) return null
        val yil = tarihParcalari[0].toIntOrNull() ?: return null
        val ay = tarihParcalari[1].toIntOrNull() ?: return null
        val gun = tarihParcalari[2].toIntOrNull() ?: return null
        val saatDegeri = saatParcalari[0].toIntOrNull() ?: return null
        val dakika = saatParcalari[1].toIntOrNull() ?: return null
        if (ay !in 1..12 || gun !in 1..31 || saatDegeri !in 0..23 || dakika !in 0..59) return null
        val takvim = Calendar.getInstance().apply {
            set(Calendar.YEAR, yil)
            set(Calendar.MONTH, ay - 1)
            set(Calendar.DAY_OF_MONTH, gun)
            set(Calendar.HOUR_OF_DAY, saatDegeri)
            set(Calendar.MINUTE, dakika)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return takvim.timeInMillis
    } // seansZamani: koordinatör ekranında yaklaşan talepleri hesaplamak için kullanılır.

    // HH:MM biçimindeki saati gün içindeki dakikaya çevirir.
    fun saatDakikayaCevir(saat: String): Int? {
        val parcalar = saat.split(":")
        if (parcalar.size != 2) return null
        val saatDegeri = parcalar[0].toIntOrNull() ?: return null
        val dakika = parcalar[1].toIntOrNull() ?: return null
        if (saatDegeri !in 0..23 || dakika !in 0..59) return null
        return saatDegeri * 60 + dakika
    } // saatDakikayaCevir: araç/görevli çakışmalarında saatleri karşılaştırmayı sağlar.

    // Gecikme süresinin kabul edilen aralıkta olup olmadığını kontrol eder.
    fun gecikmeDakikaGecerliMi(dakika: Int): Boolean {
        return dakika in MIN_GECIKME_DAKIKA..MAKS_GECIKME_DAKIKA
    } // gecikmeDakikaGecerliMi: anlamsız/çok büyük sürelerin kaydedilmesini engeller.

    // ISO tarih ve HH:MM saate dakika ekler; gece geçişinde tarihi de ilerletir.
    fun zamaniErtele(tarihIso: String, saat: String, dakika: Int): Pair<String, String>? {
        if (dakika <= 0) return null
        val zaman = seansZamani(tarihIso, saat) ?: return null
        val takvim = Calendar.getInstance().apply {
            timeInMillis = zaman
            add(Calendar.MINUTE, dakika)
        }
        val tarihBicimi = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val saatBicimi = SimpleDateFormat("HH:mm", Locale.US)
        return tarihBicimi.format(takvim.time) to saatBicimi.format(takvim.time)
    } // zamaniErtele: onaylanan gecikme kadar seans/alınma saatini ileri taşır.

    // Merkeze varış zamanına seans süresi ve tampon ekleyerek dönüş zamanını üretir.
    fun donusZamaniHesapla(
        merkezeVaris: Long,
        seansDakika: Int,
        tamponDakika: Int = VARSAYILAN_DONUS_TAMPONU
    ): Long? {
        if (merkezeVaris <= 0L) return null
        if (seansDakika !in MIN_SEANS_DAKIKA..MAKS_SEANS_DAKIKA) return null
        if (tamponDakika !in 0..60) return null
        return merkezeVaris + (seansDakika + tamponDakika) * 60_000L
    } // donusZamaniHesapla: otomatik dönüş planının zaman damgasını üretir.

    // Seans süresinin kabul edilen aralıkta olup olmadığını kontrol eder.
    fun seansSuresiGecerliMi(dakika: Int): Boolean {
        return dakika in MIN_SEANS_DAKIKA..MAKS_SEANS_DAKIKA
    } // seansSuresiGecerliMi: anlamsız seans sürelerini engeller.

    // Türk telefon numarasını rakam dışı karakterlerden arındırıp uzunluğunu denetler.
    fun telefonGecerliMi(telefon: String): Boolean {
        val rakamlar = telefon.filter { it.isDigit() }
        return rakamlar.length in 10..11 &&
            (rakamlar.startsWith("0") || rakamlar.startsWith("5"))
    } // telefonGecerliMi: refakatçi telefonunun eksik veya uydurma olmasını engeller.

    // Türk telefon numarasını Firebase'in beklediği +90XXXXXXXXXX biçimine çevirir.
    fun telefonE164(telefon: String): String? {
        val rakamlar = telefon.filter { it.isDigit() }
        return when {
            rakamlar.length == 12 && rakamlar.startsWith("90") -> "+$rakamlar"
            rakamlar.length == 11 && rakamlar.startsWith("0") ->
                "+90${rakamlar.drop(1)}"
            rakamlar.length == 10 && rakamlar.startsWith("5") ->
                "+90$rakamlar"
            else -> null
        }
    } // telefonE164: ülke kodunu ekleyerek telefon doğrulamasını standartlaştırır.

    // Zaman damgasını bildirim listesinde okunacak kısa biçime çevirir.
    fun kisaZamanGorunen(zaman: Long): String {
        if (zaman <= 0L) return ""
        val bicim = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        return bicim.format(zaman)
    } // kisaZamanGorunen: bildirimin ne zaman geldiğini Türkçe biçimde gösterir.

    // Metnin boş olmamasını ve belirlenen üst sınırı aşmamasını kontrol eder.
    fun metinGecerliMi(metin: String, maksUzunluk: Int): Boolean {
        val temizMetin = metin.trim()
        return temizMetin.isNotEmpty() && temizMetin.length <= maksUzunluk
    } // metinGecerliMi: adres ve not alanlarına aşırı uzun veya boş veri girilmesini önler.

    // Görünmez kontrol karakterlerini temizler; satır sonlarını korur.
    fun metniTemizle(metin: String): String {
        return metin.trim().replace(Regex("[\\p{Cntrl}&&[^\\r\\n\\t]]"), "")
    } // metniTemizle: zararlı/bozuk karakterlerin veritabanına yazılmasını engeller.

    // Bildirimlerde tam ad yerine "A*** Y***" biçiminde maskeli ad üretir.
    fun adMaskele(adSoyad: String): String {
        val parcalar = adSoyad.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parcalar.isEmpty()) return "Hasta"
        return parcalar.joinToString(" ") { "${it.first().uppercaseChar()}***" }
    } // adMaskele: kilit ekranı bildirimlerinde kimlik ifşasını azaltır.

    // Enlem/boylam değerlerinin geçerli dünya koordinatlarında olduğunu doğrular.
    fun konumGecerliMi(enlem: Double, boylam: Double): Boolean {
        return enlem in -90.0..90.0 && boylam in -180.0..180.0
    } // konumGecerliMi: haritadan gelen hatalı koordinatların kaydedilmesini önler.

    // Bildirim metninde tam ad kullanmadan güvenli bir özet üretir.
    fun guvenliBildirimMetni(adSoyad: String): String {
        return "${adMaskele(adSoyad)} için ulaşım durumu güncellendi."
    } // guvenliBildirimMetni: bildirimde kişisel veriyi en aza indirir.
}
