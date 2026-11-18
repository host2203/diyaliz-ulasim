package com.pigasor.diyalizulasim.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Hasta yakını davet kodu biçiminin sınırlarını denetler.
class DavetKoduTest {

    @Test
    fun uretilenKodAltıKarakterVeGecerli() {
        repeat(50) {
            val kod = DavetKodu.uret()
            assertEquals(DavetKodu.UZUNLUK, kod.length)
            assertTrue(DavetKodu.gecerliMi(kod))
        }
    }

    @Test
    fun normalizeBoslukVeKucukHarfiDuzeltir() {
        assertEquals("AB7K2M", DavetKodu.normalize(" ab7k2m "))
    }

    @Test
    fun karisikKarakterlerVeYanlisUzunlukReddedilir() {
        assertFalse(DavetKodu.gecerliMi("AB7K2"))
        assertFalse(DavetKodu.gecerliMi("AB7K2M9"))
        assertFalse(DavetKodu.gecerliMi("AB0K2M"))
        assertFalse(DavetKodu.gecerliMi("ABIK2M"))
        assertTrue(DavetKodu.gecerliMi("AB7K2M"))
    }

    @Test
    fun yediGunlukGecerlilikHesaplanir() {
        val baslangic = 1_000_000L
        assertEquals(
            baslangic + 7L * 24L * 60L * 60L * 1000L,
            DavetKodu.sonGecerlilik(baslangic)
        )
    }
}
