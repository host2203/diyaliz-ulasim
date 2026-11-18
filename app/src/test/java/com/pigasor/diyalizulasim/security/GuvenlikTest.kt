package com.pigasor.diyalizulasim.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Gecikme erteleme ve süre doğrulama yardımcılarının sınır durumlarını denetler.
class GuvenlikTest {

    @Test
    fun yirmiDakikaAyniGunIcindeEklenir() {
        assertEquals(
            "2026-10-26" to "09:50",
            Guvenlik.zamaniErtele("2026-10-26", "09:30", 20)
        )
    }

    @Test
    fun geceYarisiniGecenErtelemeTarihiIlerletir() {
        assertEquals(
            "2026-10-27" to "00:10",
            Guvenlik.zamaniErtele("2026-10-26", "23:50", 20)
        )
    }

    @Test
    fun sifirVeyaNegatifDakikaErtelemez() {
        assertNull(Guvenlik.zamaniErtele("2026-10-26", "09:30", 0))
        assertNull(Guvenlik.zamaniErtele("2026-10-26", "09:30", -5))
    }

    @Test
    fun gecersizTarihVeyaSaatNullDoner() {
        assertNull(Guvenlik.zamaniErtele("2026-13-40", "09:30", 20))
        assertNull(Guvenlik.zamaniErtele("2026-10-26", "25:00", 20))
    }

    @Test
    fun gecikmeSuresiBesIleYuzSeksenDakikaArasinda() {
        assertFalse(Guvenlik.gecikmeDakikaGecerliMi(4))
        assertTrue(Guvenlik.gecikmeDakikaGecerliMi(5))
        assertTrue(Guvenlik.gecikmeDakikaGecerliMi(180))
        assertFalse(Guvenlik.gecikmeDakikaGecerliMi(181))
    }

    @Test
    fun donusZamaniSeansSuresiVeTamponlaHesaplanir() {
        val varis = 1_000_000L
        assertEquals(
            varis + (240 + 15) * 60_000L,
            Guvenlik.donusZamaniHesapla(varis, 240, 15)
        )
    }

    @Test
    fun gecersizDonusSuresiNullDoner() {
        assertNull(Guvenlik.donusZamaniHesapla(1_000_000L, 30, 15))
        assertNull(Guvenlik.donusZamaniHesapla(0L, 240, 15))
        assertNull(Guvenlik.donusZamaniHesapla(1_000_000L, 240, 61))
    }

    @Test
    fun telefonE164BicimineCevrilir() {
        assertEquals("+905551112233", Guvenlik.telefonE164("05551112233"))
        assertEquals("+905551112233", Guvenlik.telefonE164("5551112233"))
        assertEquals("+905551112233", Guvenlik.telefonE164("905551112233"))
        assertNull(Guvenlik.telefonE164("12345"))
        assertNull(Guvenlik.telefonE164(""))
    }

    @Test
    fun seansSuresiBirIleOnIkiSaatArasinda() {
        assertFalse(Guvenlik.seansSuresiGecerliMi(59))
        assertTrue(Guvenlik.seansSuresiGecerliMi(60))
        assertTrue(Guvenlik.seansSuresiGecerliMi(720))
        assertFalse(Guvenlik.seansSuresiGecerliMi(721))
    }
}
