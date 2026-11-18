package com.pigasor.diyalizulasim.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pigasor.diyalizulasim.data.TransportRepository
import com.pigasor.diyalizulasim.model.DriverDelayStatus
import com.pigasor.diyalizulasim.model.HistoryEvent
import com.pigasor.diyalizulasim.model.HistoryType
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.ReturnPlanStatus
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.durumEtiketi
import com.pigasor.diyalizulasim.model.etiket
import com.pigasor.diyalizulasim.model.hazirlikEtiketi
import com.pigasor.diyalizulasim.model.oncelikEtiketi
import com.pigasor.diyalizulasim.security.Guvenlik

// Talebin zaman damgalı durum geçmişini rolüne göre canlı gösterir.
@Composable
fun TalepGecmisiDiyalogu(talepId: String, onKapat: () -> Unit) {
    val repository = remember { TransportRepository() }
    var olaylar by remember(talepId) { mutableStateOf<List<HistoryEvent>>(emptyList()) }
    var yukleniyor by remember(talepId) { mutableStateOf(true) }
    var hata by remember(talepId) { mutableStateOf<String?>(null) }

    LaunchedEffect(talepId) {
        try {
            repository.talepGecmisiDinle(talepId).collect { liste ->
                olaylar = liste
                yukleniyor = false
                hata = null
            }
        } catch (e: Exception) {
            hata = "Durum geçmişi yüklenemedi."
            yukleniyor = false
        }
    } // LaunchedEffect: diyalog açıkken dinlemeyi başlatır, kapanınca iptal eder.

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Durum geçmişi") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                when {
                    yukleniyor -> {
                        CircularProgressIndicator()
                    }
                    hata != null -> {
                        Text(
                            text = hata.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    olaylar.isEmpty() -> {
                        Text("Henüz geçmiş kaydı yok.")
                    }
                    else -> {
                        LazyColumn {
                            items(olaylar, key = { it.id }) { olay ->
                                GecmisSatiri(olay)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKapat) {
                Text("Kapat")
            }
        }
    )
} // TalepGecmisiDiyalogu: hasta, koordinatör, görevli ve hasta yakını için ortak geçmiş ekranı.

@Composable
private fun GecmisSatiri(olay: HistoryEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = olay.type.gecmisEtiketi(),
            style = MaterialTheme.typography.titleSmall
        )
        val deger = olay.gosterimDegeri()
        if (deger.isNotBlank()) {
            Text(
                text = deger,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (olay.note.isNotBlank()) {
            Text(
                text = olay.note,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "${olay.updatedByName.ifBlank { "Sistem" }} • " +
                Guvenlik.kisaZamanGorunen(olay.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
} // GecmisSatiri: tek bir denetim izini tür, değer, not ve zamanla gösterir.

// Geçmiş olay türünün kullanıcıya gösterilecek adı.
private fun HistoryType.gecmisEtiketi(): String = when (this) {
    HistoryType.STATUS -> "Durum değişikliği"
    HistoryType.READINESS -> "Hazırlık bildirimi"
    HistoryType.ASSIGNMENT -> "Araç/görevli ataması"
    HistoryType.CANCELLATION -> "İptal"
    HistoryType.OVERRIDE -> "Gecikme onayı"
    HistoryType.PRIORITY -> "Öncelik doğrulaması"
    HistoryType.DRIVER_DELAY -> "Görevli gecikmesi"
    HistoryType.RETURN -> "Dönüş planı"
    HistoryType.NOTE -> "Not"
}

// Geçmişteki ham değeri mümkünse Türkçe etikete çevirir.
private fun HistoryEvent.gosterimDegeri(): String {
    if (value.isBlank()) return ""
    TransportStatus.values().firstOrNull { it.name == value }?.let { return it.durumEtiketi }
    ReadinessStatus.values().firstOrNull { it.name == value }?.let { return it.hazirlikEtiketi }
    DriverDelayStatus.values().firstOrNull { it.name == value }?.let { return it.etiket }
    ReturnPlanStatus.values().firstOrNull { it.name == value }?.let { return it.etiket }
    OncelikKategorisi.values().firstOrNull { it.name == value }?.let { return it.oncelikEtiketi }
    return when (value) {
        "RETURN_STARTED" -> "Dönüş yolculuğu başladı"
        "DELAY_APPROVED" -> "Gecikme onaylandı"
        else -> value
    }
}
