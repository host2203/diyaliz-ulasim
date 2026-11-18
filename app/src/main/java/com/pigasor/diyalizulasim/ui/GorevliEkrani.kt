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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pigasor.diyalizulasim.model.DriverDelayStatus
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.ReturnPlanStatus
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.durumEtiketi
import com.pigasor.diyalizulasim.model.driverDelayGorunurMu
import com.pigasor.diyalizulasim.model.etiket
import com.pigasor.diyalizulasim.model.etaGorunurMu
import com.pigasor.diyalizulasim.model.hazirlikEtiketi
import com.pigasor.diyalizulasim.model.oncelikEtiketi
import com.pigasor.diyalizulasim.model.sonrakiDurum
import com.pigasor.diyalizulasim.security.Guvenlik
import com.pigasor.diyalizulasim.viewmodel.GorevliViewModel

@Composable
fun GorevliPaneliEkrani(onCikis: () -> Unit) {
    val gorevliViewModel: GorevliViewModel = viewModel()
    val durum by gorevliViewModel.durum.collectAsStateWithLifecycle()
    var onayTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var gecikmeBildirTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var bildirimAcik by remember { mutableStateOf(false) }
    var gecmisTalepId by remember { mutableStateOf<String?>(null) }
    val hata = durum.hata

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
                text = "Görevli Paneli",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            ) // Ekran başlığı.
            BildirimButonu(
                okunmamisSayisi = durum.okunmamisSayisi,
                onClick = { bildirimAcik = true }
            ) // Bildirim merkezi düğmesi.
            TextButton(onClick = onCikis) {
                Text("Çıkış")
            } // Çıkış düğmesi.
        } // Üst satır.

        Text(
            text = "Merhaba, ${durum.kullanici?.name ?: "görevli"}",
            style = MaterialTheme.typography.titleMedium
        ) // Görevlinin adı.

        durum.islemHatasi?.let { islemHatasi ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = islemHatasi,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // İşlem hatası: durum güncellenemezse gösterilir.

        Spacer(Modifier.height(12.dp))

        when {
            durum.yukleniyor -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } // Yükleniyor durumu.

            hata != null -> {
                Text(
                    text = hata,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            } // Rol/oturum hatası.

            durum.talepler.isEmpty() -> {
                Text(
                    text = "Aktif işiniz yok.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } // Boş durum.

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(durum.talepler, key = { it.id }) { talep ->
                        GorevliTalepKarti(
                            talep = talep,
                            islemYukleniyor = durum.islemYukleniyor,
                            onGecikmeBildir = { gecikmeBildirTalep = talep },
                            onGecmis = { gecmisTalepId = talep.id },
                            onDonuseBasla = { gorevliViewModel.donuseBasla(talep) },
                            onIlerlet = {
                                val sonraki = talep.status.sonrakiDurum()
                                if (sonraki == TransportStatus.PICKED_UP &&
                                    (talep.readiness == ReadinessStatus.DELAYED ||
                                        talep.readiness == ReadinessStatus.NOT_SET)
                                ) {
                                    onayTalep = talep
                                } else {
                                    gorevliViewModel.durumIlerlet(talep)
                                }
                            }
                        )
                    }
                } // LazyColumn: görevlinin atanmış işlerini listeler.
            } // Liste durumu.
        } // Durum seçimi.
    } // Ana düzen.

    onayTalep?.let { talep ->
        val gecikmeli = talep.readiness == ReadinessStatus.DELAYED
        AlertDialog(
            onDismissRequest = { onayTalep = null },
            title = { Text(if (gecikmeli) "Hasta gecikmeli" else "Hasta hazır değil") },
            text = {
                Text(
                    if (gecikmeli) {
                        "Hasta 'Gecikeceğim' bildirdi. Hasta gerçekten araçta mı? " +
                            "'Hasta alındı' olarak işaretlensin mi?"
                    } else {
                        "Hasta 'Hazırım' bildirmedi. Yine de 'Hasta alındı' olarak işaretlensin mi?"
                    }
                )
            }, // Saha doğrulaması: koordinatör onayı değil, görevlinin son kontrolü.
            confirmButton = {
                TextButton(
                    onClick = {
                        gorevliViewModel.durumIlerlet(talep)
                        onayTalep = null
                    }
                ) {
                    Text("Evet, işaretle")
                } // Onay: durumu ilerletir; repository yine de kontrol eder.
            },
            dismissButton = {
                TextButton(onClick = { onayTalep = null }) {
                    Text("Vazgeç")
                } // Vazgeç: işlem yapılmaz.
            }
        ) // Hazır değil onay diyaloğu.
    } // Hasta bildirim yapmadıysa onay istenir.

    gecikmeBildirTalep?.let { talep ->
        GorevliGecikmeDiyalogu(
            talep = talep,
            islemYukleniyor = durum.islemYukleniyor,
            onVazgec = { gecikmeBildirTalep = null },
            onBildir = { dakika, sebep ->
                gorevliViewModel.gorevliGecikmeBildir(talep, dakika, sebep)
                gecikmeBildirTalep = null
            }
        )
    } // Görevli/araç gecikmesi bildirim diyaloğu.

    if (bildirimAcik) {
        BildirimDiyalogu(
            bildirimler = durum.bildirimler,
            onOkunduIsaretle = { gorevliViewModel.bildirimleriOkunduIsaretle() },
            onKapat = { bildirimAcik = false }
        )
    } // Görevlinin bildirim merkezi.

    gecmisTalepId?.let { talepId ->
        TalepGecmisiDiyalogu(talepId = talepId, onKapat = { gecmisTalepId = null })
    } // Durum geçmişi: görevlinin atanmış işindeki olayları gösterir.
} // GorevliPaneliEkrani: görevlinin atanmış işlerini ve durum adımlarını yönettiği ekran.

@Composable
private fun GorevliTalepKarti(
    talep: TransportRequest,
    islemYukleniyor: Boolean,
    onGecikmeBildir: () -> Unit,
    onGecmis: () -> Unit,
    onDonuseBasla: () -> Unit,
    onIlerlet: () -> Unit
) {
    val sonrakiDurum = talep.status.sonrakiDurum()
    val gecikmeOnaylandi = talep.readiness == ReadinessStatus.DELAYED && talep.delayOverride
    val durumIlerletilebilir = when {
        talep.status == TransportStatus.AT_CENTER && talep.roundTrip ->
            talep.returnPlanStatus == ReturnPlanStatus.APPROVED && talep.returnStartedAt > 0L
        talep.status == TransportStatus.AT_CENTER && !talep.roundTrip -> false
        else -> sonrakiDurum != null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = talep.patientName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            ) // Hasta adı.
            Text(
                text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} • ${talep.sessionTime}",
                style = MaterialTheme.typography.bodySmall
            ) // Seans tarihi ve saati.
            Text(
                text = talep.centerName,
                style = MaterialTheme.typography.bodySmall
            ) // Diyaliz merkezi.
            Text(
                text = talep.pickupAddress,
                style = MaterialTheme.typography.bodySmall
            ) // Alınacağı adres.

            val ihtiyaclar = buildList {
                if (talep.wheelchairRequired) add("tekerlekli sandalye")
                if (talep.companionRequired) add("refakatçi")
            }.joinToString(", ")
            if (ihtiyaclar.isNotBlank()) {
                Text(
                    text = "İhtiyaç: $ihtiyaclar",
                    style = MaterialTheme.typography.bodySmall
                ) // Erişilebilirlik/refakat ihtiyacı.
            } // İhtiyaç varsa gösterilir.

            if (talep.companionRequired) {
                val refakat = listOf(
                    talep.companionName,
                    talep.companionPhone,
                    talep.companionRelation
                ).filter { it.isNotBlank() }.joinToString(" • ")
                if (refakat.isNotBlank()) {
                    Text(
                        text = "Refakatçi: $refakat",
                        style = MaterialTheme.typography.bodySmall
                    ) // Görevlinin refakatçiye ulaşması için iletişim bilgisi.
                } // Bilgi girilmişse gösterilir.
            } // Refakatçi gerekiyorsa gösterilir.

            if (talep.priorityCategory != OncelikKategorisi.NONE && talep.priorityVerified) {
                Text(
                    text = "Öncelik: ${talep.priorityCategory.oncelikEtiketi} (doğrulandı)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                ) // Görevliye yalnızca doğrulanmış öncelik bilgisi gösterilir.
            } // Doğrulanmış öncelik varsa gösterilir.

            if (talep.note.isNotBlank()) {
                Text(
                    text = "Not: ${talep.note}",
                    style = MaterialTheme.typography.bodySmall
                ) // Hastanın ek notu.
            } // Not varsa gösterilir.

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Durum: ${talep.status.durumEtiketi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            ) // Güncel durum.
            Text(
                text = "Hazırlık: ${talep.readiness.hazirlikEtiketi}",
                style = MaterialTheme.typography.bodySmall
            ) // Hasta hazırlık durumu.
            if (talep.vehicleName.isNotBlank()) {
                Text(
                    text = "Araç: ${talep.vehicleName}",
                    style = MaterialTheme.typography.bodySmall
                ) // Atanan araç.
            } // Araç varsa gösterilir.

            if (talep.readiness == ReadinessStatus.DELAYED) {
                Spacer(Modifier.height(4.dp))
                if (gecikmeOnaylandi) {
                    Text(
                        text = "Gecikme onaylandı (${talep.delayMinutes} dk).",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    ) // Onay: alım engeli kalkar.
                    if (talep.delayRescheduled) {
                        Text(
                            text = "Randevu ertelendi: " +
                                "${Guvenlik.tarihIsoDanTurkce(talep.delayPreviousSessionDate)} " +
                                "${talep.delayPreviousSessionTime} → " +
                                "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime}" +
                                if (talep.delayPreviousPickupTime.isNotBlank()) {
                                    " • Alınma: ${talep.delayPreviousPickupTime} → " +
                                        talep.plannedPickupTime
                                } else {
                                    ""
                                },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        ) // Araç yola çıkmadan önce yapılan erteleme.
                    } // Randevu ertelendiyse gösterilir.
                    if (talep.delayDriverMessage.isNotBlank()) {
                        Text(
                            text = "Hasta mesajı: ${talep.delayDriverMessage}",
                            style = MaterialTheme.typography.bodySmall
                        ) // Hastanın görevliyle paylaşmayı seçtiği mesaj.
                    } // Mesaj varsa gösterilir.
                } else if (talep.status != TransportStatus.REQUESTED &&
                    talep.status != TransportStatus.PLANNED
                ) {
                    Text(
                        text = "Hasta gecikmeli bildirdi; bilgi verildi.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    ) // Yoldaki gecikme bilgi amaçlıdır; alımı kilitlemez.
                } else {
                    Text(
                        text = "Hasta gecikmeli bildirdi; koordinatör onayı bekleniyor.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    ) // Araç yola çıkmadan randevu ertelemesi için onay beklenir.
                } // Onay/bilgi durumuna göre metin gösterilir.
            } // Gecikme bildirimi varsa gösterilir.

            if (talep.etaGorunurMu) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tahmini varış: ${talep.etaMinutes} dk • ${talep.etaTarget.etiket}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                ) // OSRM'den gelen gerçek rota süresi; görevli de görür.
            } // ETA hesaplandıysa gösterilir.

            if (talep.driverDelayGorunurMu) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Gecikme bildiriminiz: ${talep.driverDelayMinutes} dk • " +
                        talep.driverDelayStatus.etiket,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                ) // Gecikme yalnızca ilgili bacakta ve bilgi amaçlı gösterilir.
            } // Görevli gecikmesi mevcut bacakla eşleşiyorsa gösterilir.

            if (talep.roundTrip && talep.returnPlanStatus != ReturnPlanStatus.NONE) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Dönüş planı: ${Guvenlik.kisaZamanGorunen(talep.returnPlannedAt)} • " +
                        talep.returnPlanStatus.etiket,
                    color = if (talep.returnPlanStatus == ReturnPlanStatus.PENDING) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodySmall
                ) // Otomatik dönüş planı ve koordinatör onay durumu.
            } // Dönüş planı varsa gösterilir.

            if (talep.returnStartedAt > 0L) {
                Text(
                    text = "Dönüş yolculuğu başladı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                ) // Dönüş ayağının başladığını gösterir.
            } // Dönüş başladıysa gösterilir.

            TextButton(onClick = onGecmis) {
                Text("Durum geçmişi")
            } // İşin tüm durum adımlarını ve zamanlarını gösterir.

            if (talep.status == TransportStatus.PLANNED ||
                talep.status == TransportStatus.ON_THE_WAY ||
                talep.status == TransportStatus.PICKED_UP ||
                talep.status == TransportStatus.AT_CENTER
            ) {
                TextButton(
                    onClick = onGecikmeBildir,
                    enabled = !islemYukleniyor
                ) {
                    Text("Gecikeceğim")
                } // Görevli/araç gecikmesi bildirim diyaloğunu açar.
            } // Aktif yolculukta gecikme bildirilebilir.

            if (talep.status == TransportStatus.AT_CENTER &&
                talep.returnStartedAt == 0L &&
                talep.returnPlanStatus == ReturnPlanStatus.APPROVED
            ) {
                TextButton(
                    onClick = onDonuseBasla,
                    enabled = !islemYukleniyor
                ) {
                    Text("Dönüşe başla")
                } // Dönüş ETA'sını başlatır; durum zincirini değiştirmez.
            } else if (talep.status == TransportStatus.AT_CENTER &&
                talep.returnPlanStatus == ReturnPlanStatus.PENDING
            ) {
                Text(
                    text = "Dönüş planı koordinatör onayı bekliyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                ) // Onaysız dönüş başlatılamaz.
            } // Merkezde dönüş planı durumuna göre buton/bilgi gösterilir.

            if (durumIlerletilebilir && sonrakiDurum != null &&
                talep.status != TransportStatus.REQUESTED
            ) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onIlerlet,
                    enabled = !islemYukleniyor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Durumu ilerlet: ${sonrakiDurum.durumEtiketi}")
                } // Durum düğmesi: sıradaki adıma geçirir.
            } else if (talep.status == TransportStatus.DROPPED_HOME) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Yolculuk tamamlandı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (talep.status == TransportStatus.CANCELLED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Yolculuk iptal edildi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (talep.status == TransportStatus.AT_CENTER) {
                // Dönüş planı durumu yukarıda gösterilir; yanlış "atama bekleniyor" metni yazılmaz.
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Atama bekleniyor.",
                    style = MaterialTheme.typography.bodySmall
                )
            } // Durum düğmesi veya bilgi metni.
        } // Kart içeriği.
    } // Talep kartı.
} // GorevliTalepKarti: görevlinin tek bir işini ve durum eylemini gösterir.

@Composable
private fun GorevliGecikmeDiyalogu(
    talep: TransportRequest,
    islemYukleniyor: Boolean,
    onVazgec: () -> Unit,
    onBildir: (Int, String) -> Unit
) {
    var dakikaMetni by remember(talep.id) { mutableStateOf("15") }
    var sebep by remember(talep.id) { mutableStateOf("") }
    val dakika = dakikaMetni.toIntOrNull()
    val dakikaGecerli = dakika != null && Guvenlik.gecikmeDakikaGecerliMi(dakika)
    val sebepGecerli = sebep.trim().length >= 3
    val gecerli = dakikaGecerli && sebepGecerli

    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text("Gecikeceğim") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${talep.patientName} • ${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime}",
                    style = MaterialTheme.typography.bodySmall
                ) // Gecikmenin bildirileceği hasta ve seans.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Bildirim hastaya ve koordinatöre anında gider; onay gerekmez. " +
                        "Hastaya yalnızca tahmini varış süresi iletilir.",
                    style = MaterialTheme.typography.bodySmall
                ) // Gecikme bilgisinin onay gerektirmediğini açıklar.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dakikaMetni,
                    onValueChange = { yeni ->
                        dakikaMetni = yeni.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Gecikme süresi (dakika)") },
                    supportingText = { Text("5-180 dakika") },
                    isError = dakikaMetni.isNotBlank() && !dakikaGecerli,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                ) // Süre: aracın kaç dakika gecikeceğini belirtir.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sebep,
                    onValueChange = { sebep = it.take(Guvenlik.MAKS_GECIKME_SEBEBI) },
                    label = { Text("Gecikme nedeni") },
                    isError = sebep.isNotBlank() && !sebepGecerli,
                    modifier = Modifier.fillMaxWidth()
                ) // Gerekçe ayrı koleksiyonda tutulur; hastaya gösterilmez.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Gecikme nedeniniz hastaya gösterilmez; yalnızca koordinatör " +
                        "tarafından görülür. Hastaya yalnızca tahmini varış süresi iletilir.",
                    style = MaterialTheme.typography.bodySmall
                ) // Gizlilik kuralını açıklar.
            }
        },
        confirmButton = {
            Button(
                onClick = { onBildir(dakika ?: 0, sebep) },
                enabled = !islemYukleniyor && gecerli
            ) {
                Text("Bildir")
            } // Süre ve gerekçe geçerli olmadan bildirim gönderilemez.
        },
        dismissButton = {
            TextButton(onClick = onVazgec, enabled = !islemYukleniyor) {
                Text("Vazgeç")
            }
        }
    ) // Görevli gecikme bildirim diyaloğu.
} // GorevliGecikmeDiyalogu: süre ve gizli gerekçeyi toplar; hastaya yalnızca ETA gider.
