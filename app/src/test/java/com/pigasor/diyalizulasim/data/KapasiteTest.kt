package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.model.SeansDilimi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Seans dilimi, belge kimliği ve tarih yardımcılarının sınırlarını denetler.
class KapasiteTest {

    @Test
    fun seansSinirlariDogruDilimeGider() {
        assertEquals(SeansDilimi.SABAH, Kapasite.seansDilimi("11:59"))
        assertEquals(SeansDilimi.OGLEDEN_SONRA, Kapasite.seansDilimi("12:00"))
        assertEquals(SeansDilimi.OGLEDEN_SONRA, Kapasite.seansDilimi("16:59"))
        assertEquals(SeansDilimi.AKSAM, Kapasite.seansDilimi("17:00"))
        assertEquals(SeansDilimi.AKSAM, Kapasite.seansDilimi("23:59"))
    }

    @Test
    fun gecersizSaatNullDoner() {
        assertNull(Kapasite.seansDilimi("25:00"))
        assertNull(Kapasite.seansDilimi(""))
        assertNull(Kapasite.seansDilimi("12"))
    }

    @Test
    fun slotKimligiAyniGirdideAyniKalir() {
        val ilk = Kapasite.slotBelgesiId("Test Merkez", "2026-11-05", "09:30")
        val ikinci = Kapasite.slotBelgesiId("Test Merkez", "2026-11-05", "11:00")
        assertEquals(ilk, ikinci)
        assertEquals(
            ilk,
            Kapasite.slotBelgesiId("Test Merkez", "2026-11-05", SeansDilimi.SABAH)
        )
    }

    @Test
    fun farkliMerkezTarihVeyaSeansFarkliKimlikUretir() {
        val temel = Kapasite.slotBelgesiId("Test Merkez", "2026-11-05", "09:00")
        assertNotEquals(temel, Kapasite.slotBelgesiId("Diger Merkez", "2026-11-05", "09:00"))
        assertNotEquals(temel, Kapasite.slotBelgesiId("Test Merkez", "2026-11-06", "09:00"))
        assertNotEquals(temel, Kapasite.slotBelgesiId("Test Merkez", "2026-11-05", "19:00"))
    }

    @Test
    fun bosMerkezVeyaGecersizTarihNullDoner() {
        assertNull(Kapasite.slotBelgesiId("", "2026-11-05", "09:00"))
        assertNull(Kapasite.slotBelgesiId("Test Merkez", "05.11.2026", "09:00"))
    }

    @Test
    fun ayVeYilGecisiTarihEkleme() {
        assertEquals("2027-01-01", Kapasite.tarihEkle("2026-12-31", 1))
        assertEquals("2028-02-29", Kapasite.tarihEkle("2028-02-28", 1))
        assertEquals("2026-11-05", Kapasite.tarihEkle("2026-11-05", 0))
        assertNull(Kapasite.tarihEkle("2026-13-40", 1))
    }

    @Test
    fun formGirdilerineDonusum() {
        assertEquals("05112026", Kapasite.tarihIsoDanRakam("2026-11-05"))
        assertNull(Kapasite.tarihIsoDanRakam("2026/11/05"))
        assertEquals("0900", Kapasite.saatDanRakam("09:00"))
        assertEquals("1930", Kapasite.saatDanRakam("19:30"))
        assertNull(Kapasite.saatDanRakam("25:00"))
    }

    @Test
    fun kapasiteAraligiSifirIleElli() {
        assertTrue(Kapasite.kapasiteGecerliMi(0))
        assertTrue(Kapasite.kapasiteGecerliMi(50))
        assertFalse(Kapasite.kapasiteGecerliMi(-1))
        assertFalse(Kapasite.kapasiteGecerliMi(51))
    }
}
