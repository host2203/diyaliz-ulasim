package com.pigasor.diyalizulasim.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Hasta yakını davet kodu biçiminin sınırlarını denetler.
class DavetKoduTest {

    @Test
    fun uretilenKodOnIkiKarakterVeGecerli() {
        repeat(50) {
            val kod = DavetKodu.uret()
            assertEquals(DavetKodu.UZUNLUK, kod.length)
            assertTrue(DavetKodu.gecerliMi(kod))
        }
    }

    @Test
    fun normalizeBoslukVeKucukHarfiDuzeltir() {
        assertEquals("AB7K2M9PQ4XR", DavetKodu.normalize(" ab7k2m9pq4xr "))
    }

    @Test
    fun karisikKarakterlerVeYanlisUzunlukReddedilir() {
        assertFalse(DavetKodu.gecerliMi("AB7K2M9PQ4X"))
        assertFalse(DavetKodu.gecerliMi("AB7K2M9PQ4XRT"))
        assertFalse(DavetKodu.gecerliMi("AB0K2M9PQ4XR"))
        assertFalse(DavetKodu.gecerliMi("ABIK2M9PQ4XR"))
        assertTrue(DavetKodu.gecerliMi("AB7K2M9PQ4XR"))
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
