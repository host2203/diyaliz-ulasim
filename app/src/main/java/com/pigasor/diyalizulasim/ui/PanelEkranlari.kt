package com.pigasor.diyalizulasim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pigasor.diyalizulasim.data.DavetKodu
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.ReturnPlanStatus
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.durumEtiketi
import com.pigasor.diyalizulasim.model.etiket
import com.pigasor.diyalizulasim.model.etaGorunurMu
import com.pigasor.diyalizulasim.model.hazirlikEtiketi
import com.pigasor.diyalizulasim.security.Guvenlik
import com.pigasor.diyalizulasim.viewmodel.YakinViewModel
import java.util.Locale

// Hasta yakınının davet koduyla bağlanıp bağlı hastanın taleplerini izlediği ekran.
@Composable
fun YakinEkrani(onCikis: () -> Unit) {
    val yakinViewModel: YakinViewModel = viewModel()
    val durum by yakinViewModel.durum.collectAsStateWithLifecycle()
    var kod by remember { mutableStateOf("") }
    var gecmisTalepId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hasta Yakını",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCikis) {
                Text("Çıkış")
            }
        }

        Text(
            text = "Merhaba, ${durum.kullanici?.name ?: "hasta yakını"}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(12.dp))

        when {
            durum.yukleniyor -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            durum.hata != null -> {
                Text(
                    text = durum.hata.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            durum.kullanici?.linkedPatientId.isNullOrBlank() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Hastanızın panelinden oluşturduğu 6 haneli davet kodunu girin.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = kod,
                            onValueChange = { yeni ->
                                kod = yeni
                                    .uppercase(Locale.ROOT)
                                    .filter { it.isLetterOrDigit() }
                                    .take(DavetKodu.UZUNLUK)
                            },
                            label = { Text("Davet kodu") },
                            placeholder = { Text("Örn. A7K2M9") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Text
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { yakinViewModel.davetKabulEt(kod) },
                            enabled = DavetKodu.gecerliMi(kod) && !durum.islemYukleniyor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (durum.islemYukleniyor) {
                                CircularProgressIndicator()
                            } else {
                                Text("Bağlan")
                            }
                        }
                        durum.islemHatasi?.let { hata ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = hata,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            else -> {
                val hastaAdi = durum.baglanti?.patientName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Bağlı hasta"
                Text(
                    text = "Bağlı hasta: $hastaAdi",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                durum.islemHatasi?.let { hata ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = hata,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (durum.talepler.isEmpty()) {
                    Text(
                        text = "Bağlı hastanın henüz ulaşım talebi yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(durum.talepler, key = { it.id }) { talep ->
                            YakinTalepKarti(
                                talep = talep,
                                onGecmis = { gecmisTalepId = talep.id }
                            )
                        }
                    }
                }
            }
        }
    } // Ana düzen.

    gecmisTalepId?.let { talepId ->
        TalepGecmisiDiyalogu(talepId = talepId, onKapat = { gecmisTalepId = null })
    } // Bağlı hastanın talebinin denetim izi.
} // YakinEkrani: davet koduyla bağlanan hasta yakınının salt okunur görünümü.

// Yakının bağlı hastasının talebini salt okunur biçimde gösterir.
@Composable
private fun YakinTalepKarti(
    talep: TransportRequest,
    onGecmis: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} • ${talep.sessionTime}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = talep.centerName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = talep.pickupAddress,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Durum: ${talep.status.durumEtiketi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Hazırlık: ${talep.readiness.hazirlikEtiketi}",
                style = MaterialTheme.typography.bodySmall
            )
            if (talep.vehicleName.isNotBlank()) {
                Text(
                    text = "Araç: ${talep.vehicleName} • Görevli: ${talep.personnel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (talep.status == TransportStatus.CANCELLED) {
                Text(
                    text = "İptal gerekçesi: ${talep.cancelReason.ifBlank { "belirtilmedi" }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (talep.readiness == ReadinessStatus.DELAYED) {
                Text(
                    text = "Gecikme: ${talep.delayMinutes} dk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (talep.etaGorunurMu) {
                Text(
                    text = "Tahmini varış: ${talep.etaMinutes} dk • ${talep.etaTarget.etiket}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (talep.roundTrip && talep.returnPlanStatus != ReturnPlanStatus.NONE) {
                Text(
                    text = if (talep.returnPlanStatus == ReturnPlanStatus.APPROVED) {
                        "Dönüş planlandı: ${Guvenlik.kisaZamanGorunen(talep.returnPlannedAt)}"
                    } else {
                        "Dönüş planı hazırlanıyor."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (talep.returnPlanStatus == ReturnPlanStatus.APPROVED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            TextButton(onClick = onGecmis) {
                Text("Durum geçmişi")
            }
        }
    }
} // YakinTalepKarti: yakına kişisel olmayan durum özeti sunar; eylem düğmesi yoktur.
