package com.pigasor.diyalizulasim.data

import java.util.Locale
import java.security.SecureRandom

// Hasta yakını davet kodlarının üretimi ve doğrulaması.
object DavetKodu {

    const val UZUNLUK = 12
    private const val ALFABE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val GECERLILIK_GUN = 7L
    private val rastgele = SecureRandom()

    // Karışabilecek harfleri (I, O, 0, 1) dışlayan 12 karakterli kod üretir.
    // SecureRandom kullanılır; 32^12 ≈ 2^60 ihtimal olduğundan kod tahmin edilemez.
    fun uret(): String {
        return buildString(UZUNLUK) {
            repeat(UZUNLUK) {
                append(ALFABE[rastgele.nextInt(ALFABE.length)])
            }
        }
    } // uret: telefonda okunması kolay, tahmin edilmesi zor kod üretir.

    // Kullanıcı girişini büyük harfe çevirip boşlukları temizler.
    fun normalize(kod: String): String {
        return kod.trim().uppercase(Locale.ROOT)
    } // normalize: küçük harfle girilen kodu standart biçime getirir.

    // Kodun beklenen uzunluk ve karakter kümesinde olup olmadığını kontrol eder.
    fun gecerliMi(kod: String): Boolean {
        val temiz = normalize(kod)
        return temiz.length == UZUNLUK && temiz.all { it in ALFABE }
    } // gecerliMi: biçimi bozuk kodların Firestore'a gönderilmesini engeller.

    // Davetin son geçerlilik zamanını üretir.
    fun sonGecerlilik(baslangic: Long): Long {
        return baslangic + GECERLILIK_GUN * 24L * 60L * 60L * 1000L
    } // sonGecerlilik: kodun varsayılan 7 gün geçerli olmasını sağlar.
}
