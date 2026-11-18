package com.pigasor.diyalizulasim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pigasor.diyalizulasim.model.Bildirim
import com.pigasor.diyalizulasim.security.Guvenlik

// Panel başlığında bildirim merkezini açan düğme.
@Composable
fun BildirimButonu(
    okunmamisSayisi: Int,
    onClick: () -> Unit
) {
    val etiket = if (okunmamisSayisi > 0) "Bildirimler ($okunmamisSayisi)" else "Bildirimler"
    TextButton(onClick = onClick) {
        Text(
            text = etiket,
            color = if (okunmamisSayisi > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    } // Bildirim düğmesi: okunmamış sayısını gösterir.
} // BildirimButonu: üç panelde ortak kullanılan bildirim giriş noktası.

// Bildirimleri listeleyen ve okundu işaretleme sunan diyalog.
@Composable
fun BildirimDiyalogu(
    bildirimler: List<Bildirim>,
    onOkunduIsaretle: () -> Unit,
    onKapat: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Bildirimler") },
        text = {
            if (bildirimler.isEmpty()) {
                Text("Henüz bildirim yok.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bildirimler, key = { it.id }) { bildirim ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = bildirim.baslik,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (bildirim.okundu) FontWeight.Normal else FontWeight.Bold
                            ) // Bildirim başlığı; okunmamışsa kalın gösterilir.
                            Text(
                                text = bildirim.mesaj,
                                style = MaterialTheme.typography.bodySmall
                            ) // Bildirim ayrıntısı.
                            if (bildirim.createdAt > 0L) {
                                Text(
                                    text = Guvenlik.kisaZamanGorunen(bildirim.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ) // Bildirimin geliş zamanı.
                            }
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider()
                        } // Tek bildirim bloğu.
                    }
                } // LazyColumn: bildirim geçmişini kaydırılabilir gösterir.
            }
        },
        confirmButton = {
            TextButton(onClick = onKapat) {
                Text("Kapat")
            }
        },
        dismissButton = if (bildirimler.any { !it.okundu }) {
            {
                TextButton(
                    onClick = {
                        onOkunduIsaretle()
                        onKapat()
                    }
                ) {
                    Text("Tümünü okundu işaretle")
                }
            }
        } else {
            null
        }
    ) // Bildirim diyaloğu.
} // BildirimDiyalogu: uygulama içi bildirim merkezinin ortak arayüzü.
