package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Görevli paneli filtresinin tamamlanan/aktif işleri doğru ayırdığını denetler.
class GorevliListeFiltresiTest {

    private fun talep(
        id: String,
        status: TransportStatus,
        roundTrip: Boolean = true
    ) = TransportRequest(
        id = id,
        patientId = "P",
        patientName = "Test Hasta",
        sessionDate = "2026-11-01",
        sessionTime = "09:30",
        roundTrip = roundTrip,
        status = status
    )

    @Test
    fun iptalVeTamamlananIslerGizlenir() {
        val liste = listOf(
            talep("a", TransportStatus.PLANNED),
            talep("b", TransportStatus.CANCELLED),
            talep("c", TransportStatus.DROPPED_HOME)
        )
        val sonuc = gorevliAktifTalepleri(liste)
        assertEquals(listOf("a"), sonuc.map { it.id })
    }

    @Test
    fun gidisDonussuzTalepMerkezdeGizlenir() {
        val liste = listOf(
            talep("tek", TransportStatus.AT_CENTER, roundTrip = false),
            talep("cift", TransportStatus.AT_CENTER, roundTrip = true)
        )
        val sonuc = gorevliAktifTalepleri(liste)
        assertEquals(listOf("cift"), sonuc.map { it.id })
    }

    @Test
    fun aktifYolculuklarKorunur() {
        val liste = listOf(
            talep("plan", TransportStatus.PLANNED),
            talep("yolda", TransportStatus.ON_THE_WAY),
            talep("alindi", TransportStatus.PICKED_UP),
            talep("merkez", TransportStatus.AT_CENTER, roundTrip = true)
        )
        val sonuc = gorevliAktifTalepleri(liste)
        assertEquals(4, sonuc.size)
        assertTrue(sonuc.any { it.id == "merkez" })
    }
}
