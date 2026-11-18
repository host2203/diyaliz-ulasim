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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.ReturnPlanStatus
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.durumEtiketi
import com.pigasor.diyalizulasim.model.etiket
import com.pigasor.diyalizulasim.model.etaGorunurMu
import com.pigasor.diyalizulasim.model.hazirlikEtiketi
import com.pigasor.diyalizulasim.model.oncelikEtiketi
import com.pigasor.diyalizulasim.security.Guvenlik
import com.pigasor.diyalizulasim.viewmodel.HastaViewModel

@Composable
fun HastaPaneliEkrani(
    onCikis: () -> Unit,
    onYeniTalep: () -> Unit
) {
    val hastaViewModel: HastaViewModel = viewModel()
    val durum by hastaViewModel.durum.collectAsStateWithLifecycle()
    val hata = durum.hata
    var bildirimAcik by remember { mutableStateOf(false) }
    var iptalTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var gecikmeTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var gecmisTalepId by remember { mutableStateOf<String?>(null) }
    var davetAcik by remember { mutableStateOf(false) }

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
                text = "Hasta Paneli",
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
            } // Çıkış düğmesi: oturumu kapatıp giriş ekranına döner.
        } // Üst satır: başlık, bildirimler ve çıkış.

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Merhaba, ${durum.kullanici?.name ?: "hasta"}",
            style = MaterialTheme.typography.titleMedium
        ) // Karşılama: oturum açan hastanın adını gösterir.

        durum.islemHatasi?.let { islemHatasi ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = islemHatasi,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // İşlem hatası: hazırlık/iptal kaydedilemezse kullanıcıyı bilgilendirir.

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onYeniTalep,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Yeni Ulaşım Talebi")
        } // Yeni talep düğmesi: talep formu ekranını açar.

        TextButton(
            onClick = {
                davetAcik = true
                hastaViewModel.davetOlustur()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hasta yakını daveti oluştur")
        } // Davet: yakının bağlanacağı tek kullanımlık kodu üretir.

        Spacer(Modifier.height(16.dp))

        when {
            durum.yukleniyor -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } // Yükleniyor durumu: veriler gelene kadar ilerleme gösterir.

            hata != null -> {
                Text(
                    text = hata,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            } // Hata durumu: kullanıcıya veri yüklenemediğini gösterir.

            durum.talepler.isEmpty() -> {
                Text(
                    text = "Henüz bir ulaşım talebin yok.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } // Boş durum: hasta henüz talep oluşturmadıysa bilgi verir.

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(durum.talepler, key = { it.id }) { talep ->
                        TalepKarti(
                            talep = talep,
                            onHazir = {
                                hastaViewModel.hazirlikGuncelle(
                                    talep = talep,
                                    hazirlik = ReadinessStatus.READY
                                )
                            },
                            onGecikme = { gecikmeTalep = talep },
                            onIptal = { iptalTalep = talep },
                            onGecmis = { gecmisTalepId = talep.id }
                        )
                    }
                } // LazyColumn: talepleri kaydırılabilir kart listesi olarak gösterir.
            } // Liste durumu: hastanın talepleri varsa kartlar hâlinde listelenir.
        } // Durum seçimi: yükleniyor, hata, boş veya liste görünümünü belirler.
    } // Hasta paneli düzeni.

    if (bildirimAcik) {
        BildirimDiyalogu(
            bildirimler = durum.bildirimler,
            onOkunduIsaretle = { hastaViewModel.bildirimleriOkunduIsaretle() },
            onKapat = { bildirimAcik = false }
        )
    } // Bildirim merkezi.

    iptalTalep?.let { talep ->
        IptalDiyalogu(
            talep = talep,
            islemYukleniyor = durum.islemYukleniyor,
            onVazgec = { iptalTalep = null },
            onIptal = { sebep ->
                hastaViewModel.talebiIptalEt(talep, sebep)
                iptalTalep = null
            }
        )
    } // Talebi iptal diyaloğu.

    gecikmeTalep?.let { talep ->
        GecikmeBildirDiyalogu(
            talep = talep,
            islemYukleniyor = durum.islemYukleniyor,
            onVazgec = { gecikmeTalep = null },
            onBildir = { dakika, sebep, mesaj ->
                hastaViewModel.hazirlikGuncelle(
                    talep = talep,
                    hazirlik = ReadinessStatus.DELAYED,
                    gecikmeDakika = dakika,
                    gecikmeSebebi = sebep,
                    gorevliMesaji = mesaj
                )
                gecikmeTalep = null
            }
        )
    } // Gecikme süresi, gizli gerekçe ve görevli mesajı diyaloğu.

    gecmisTalepId?.let { talepId ->
        TalepGecmisiDiyalogu(talepId = talepId, onKapat = { gecmisTalepId = null })
    } // Durum geçmişi: yalnızca rolün okuyabildiği denetim izini gösterir.

    if (davetAcik) {
        HastaYakiniDavetDiyalogu(
            kod = durum.davetKodu,
            yukleniyor = durum.davetYukleniyor,
            hata = durum.davetHatasi,
            onYenile = { hastaViewModel.davetOlustur() },
            onKapat = {
                davetAcik = false
                hastaViewModel.davetTemizle()
            }
        )
    } // Hasta yakını daveti: kodu gösterir ve panoya kopyalama sağlar.
} // HastaPaneliEkrani: hastanın taleplerini canlı listeleyen ana ekranı oluşturur.

@Composable
private fun TalepKarti(
    talep: TransportRequest,
    onHazir: () -> Unit,
    onGecikme: () -> Unit,
    onIptal: () -> Unit,
    onGecmis: () -> Unit
) {
    val iptalEdilebilir = talep.status == TransportStatus.REQUESTED ||
        talep.status == TransportStatus.PLANNED

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} • ${talep.sessionTime}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            ) // Seans tarihi ve saati.
            Text(
                text = talep.centerName,
                style = MaterialTheme.typography.bodyMedium
            ) // Diyaliz merkezi.
            Text(
                text = talep.pickupAddress,
                style = MaterialTheme.typography.bodySmall
            ) // Alınacağı adres.

            if (talep.companionRequired) {
                val refakat = listOf(talep.companionName, talep.companionPhone, talep.companionRelation)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                Text(
                    text = if (refakat.isBlank()) "Refakatçi gerekli" else "Refakatçi: $refakat",
                    style = MaterialTheme.typography.bodySmall
                ) // Refakatçi adı, telefonu ve yakınlık derecesi.
            } // Refakatçi gerekiyorsa gösterilir.

            if (talep.priorityCategory != OncelikKategorisi.NONE) {
                Text(
                    text = "Öncelik: ${talep.priorityCategory.oncelikEtiketi} • " +
                        if (talep.priorityVerified) "doğrulandı" else "doğrulama bekliyor",
                    style = MaterialTheme.typography.bodySmall
                ) // Öncelik talebinin doğrulama durumu.
            } // Öncelik seçildiyse gösterilir.

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Durum: ${talep.status.durumEtiketi}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                ) // Yolculuğun güncel durumu.
                Text(
                    text = "Hazırlık: ${talep.readiness.hazirlikEtiketi}",
                    style = MaterialTheme.typography.bodySmall
                ) // Hastanın hazırlık bildirimi.
                if (talep.vehicleName.isNotBlank()) {
                    Text(
                        text = "Araç: ${talep.vehicleName} • Görevli: ${talep.personnel}",
                        style = MaterialTheme.typography.bodySmall
                    ) // Koordinatör araç/görevli atadıysa gösterir.
                } // Atama varsa gösterilir.
                if (talep.status == TransportStatus.CANCELLED) {
                    Text(
                        text = "İptal gerekçesi: ${talep.cancelReason.ifBlank { "belirtilmedi" }}",
                        style = MaterialTheme.typography.bodySmall
                    ) // İptal gerekçesi.
                } // İptal edildiyse gösterilir.
                if (talep.readiness == ReadinessStatus.DELAYED) {
                    Text(
                        text = "Gecikme: ${talep.delayMinutes} dk • " + when {
                            talep.delayOverride -> "koordinatör onayladı"
                            talep.status != TransportStatus.REQUESTED &&
                                talep.status != TransportStatus.PLANNED -> "bilgi verildi"
                            else -> "koordinatör onayı bekleniyor"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (talep.delayOverride ||
                            (talep.status != TransportStatus.REQUESTED &&
                                talep.status != TransportStatus.PLANNED)
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    ) // Gecikme süresi ve onay/bilgi durumu; gizli gerekçe gösterilmez.
                } // Gecikme bildirimi varsa gösterilir.
                if (talep.delayRescheduled) {
                    Text(
                        text = "Randevu ertelendi: " +
                            "${Guvenlik.tarihIsoDanTurkce(talep.delayPreviousSessionDate)} " +
                            "${talep.delayPreviousSessionTime} → " +
                            "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    ) // Onaylı gecikme nedeniyle seans saatinin yeni hali.
                } // Randevu ertelendiyse gösterilir.
                if (talep.delayOverride && talep.delayDriverMessage.isNotBlank()) {
                    Text(
                        text = "Görevliye mesajınız: ${talep.delayDriverMessage}",
                        style = MaterialTheme.typography.bodySmall
                    ) // Yalnızca onaydan sonra görevliyle paylaşılan mesaj.
                } // Görevliye mesaj varsa gösterilir.
                if (talep.etaGorunurMu) {
                    Text(
                        text = "Tahmini varış: ${talep.etaMinutes} dk • ${talep.etaTarget.etiket}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    ) // Görevli gecikmesinde hasta yalnızca ETA'yı görür.
                } // ETA hesaplandıysa gösterilir.
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
                    ) // Dönüş planı onaylanınca saat gösterilir; araç yoksa endişe verilmez.
                } // Gidiş-dönüş planı varsa gösterilir.
            } // Durum bilgileri.

            val hazirlikDegistirilebilir = talep.status == TransportStatus.REQUESTED ||
                talep.status == TransportStatus.PLANNED ||
                talep.status == TransportStatus.ON_THE_WAY
            if (hazirlikDegistirilebilir) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onHazir) {
                        Text("Hazırım")
                    } // Hazırım: hastanın hazır olduğunu koordinatöre bildirir.
                    TextButton(onClick = onGecikme) {
                        Text("Gecikeceğim")
                    } // Gecikeceğim: süre, gerekçe ve görevli mesajı diyaloğunu açar.
                } // Hazırlık düğmeleri.
            } // Yolculuk tamamlanmadıysa hazırlık bildirimi yapılabilir.

            TextButton(onClick = onGecmis) {
                Text("Durum geçmişi")
            } // Geçmiş: talep yaşam döngüsündeki tüm olayları gösterir.

            if (iptalEdilebilir) {
                TextButton(onClick = onIptal) {
                    Text("Talebi iptal et")
                } // İptal düğmesi: onay diyaloğunu açar.
            } // Yalnızca henüz yola çıkılmamış talepler iptal edilebilir.
        } // Kart içeriği.
    } // Talep kartı.
} // TalepKarti: tek bir talebi özetleyen; hazırlık ve iptal eylemlerini sunan kart.

@Composable
private fun GecikmeBildirDiyalogu(
    talep: TransportRequest,
    islemYukleniyor: Boolean,
    onVazgec: () -> Unit,
    onBildir: (Int, String, String) -> Unit
) {
    var dakikaMetni by remember(talep.id) { mutableStateOf("15") }
    var sebep by remember(talep.id) { mutableStateOf("") }
    var mesaj by remember(talep.id) { mutableStateOf("") }
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
                    text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime} • ${talep.centerName}",
                    style = MaterialTheme.typography.bodySmall
                ) // Gecikmenin bildirileceği seans.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dakikaMetni,
                    onValueChange = { yeni ->
                        dakikaMetni = yeni.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Gecikme süresi (dakika)") },
                    supportingText = {
                        Text(
                            if (dakika != null && !dakikaGecerli) {
                                "${Guvenlik.MIN_GECIKME_DAKIKA}-${Guvenlik.MAKS_GECIKME_DAKIKA} dakika girin."
                            } else {
                                "${Guvenlik.MIN_GECIKME_DAKIKA}-${Guvenlik.MAKS_GECIKME_DAKIKA} dakika"
                            }
                        )
                    },
                    isError = dakikaMetni.isNotBlank() && !dakikaGecerli,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                ) // Süre: hastanın ne kadar gecikeceğini belirtmesi zorunlu.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sebep,
                    onValueChange = { sebep = it.take(Guvenlik.MAKS_GECIKME_SEBEBI) },
                    label = { Text("Gecikme nedeni") },
                    isError = sebep.isNotBlank() && !sebepGecerli,
                    modifier = Modifier.fillMaxWidth()
                ) // Gerekçe ayrı koleksiyonda tutulur; görevli okuyamaz.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mesaj,
                    onValueChange = { mesaj = it.take(Guvenlik.MAKS_GOREVLI_MESAJI) },
                    label = { Text("Görevliye mesaj (isteğe bağlı)") },
                    modifier = Modifier.fillMaxWidth()
                ) // Bu mesaj yalnızca koordinatör onayından sonra görevliye iletilir.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Koordinatör onayından sonra araç henüz yola çıkmadıysa " +
                        "seans saatiniz gecikme süresi kadar ileri alınır. " +
                        "Gerekçeniz görevliyle paylaşılmaz; yalnızca yazdığınız mesaj ve " +
                        "yeni saat görevliye bildirilir.",
                    style = MaterialTheme.typography.bodySmall
                ) // Onay ve gizlilik kuralını açıklar.
            }
        },
        confirmButton = {
            Button(
                onClick = { onBildir(dakika ?: 0, sebep, mesaj) },
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
    ) // Gecikme bildirim diyaloğu.
} // GecikmeBildirDiyalogu: süre, gizli gerekçe ve isteğe bağlı görevli mesajını toplar.

@Composable
private fun IptalDiyalogu(
    talep: TransportRequest,
    islemYukleniyor: Boolean,
    onVazgec: () -> Unit,
    onIptal: (String) -> Unit
) {
    var sebep by remember(talep.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text("Talebi iptal et") },
        text = {
            Column {
                Text(
                    text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime} • ${talep.centerName}",
                    style = MaterialTheme.typography.bodySmall
                ) // İptal edilecek talep özeti.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sebep,
                    onValueChange = { sebep = it.take(300) },
                    label = { Text("İptal gerekçesi (isteğe bağlı)") },
                    modifier = Modifier.fillMaxWidth()
                ) // Gerekçe: koordinatör ve görevlinin nedenini anlamasını sağlar.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Koordinatör ve atanmış görevli bilgilendirilecek.",
                    style = MaterialTheme.typography.bodySmall
                ) // Bilgilendirme: iptalin kimlere bildirileceğini açıklar.
            }
        },
        confirmButton = {
            Button(
                onClick = { onIptal(sebep) },
                enabled = !islemYukleniyor
            ) {
                Text("İptal et")
            } // İptal onayı.
        },
        dismissButton = {
            TextButton(onClick = onVazgec, enabled = !islemYukleniyor) {
                Text("Vazgeç")
            }
        }
    ) // İptal diyaloğu.
} // IptalDiyalogu: kullanıcıdan gerekçe alıp iptali onaylatır.

@Composable
private fun HastaYakiniDavetDiyalogu(
    kod: String?,
    yukleniyor: Boolean,
    hata: String?,
    onYenile: () -> Unit,
    onKapat: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Hasta yakını daveti") },
        text = {
            Column {
                Text(
                    text = "Hasta yakınınız bu kodla hesabına bağlanabilir. " +
                        "Kod 7 gün geçerlidir ve yalnızca bir kez kullanılabilir.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                when {
                    yukleniyor -> {
                        CircularProgressIndicator()
                    }
                    kod != null -> {
                        Text(
                            text = kod,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(kod))
                                }
                            ) {
                                Text("Kopyala")
                            }
                            TextButton(onClick = onYenile) {
                                Text("Yeni kod")
                            }
                        }
                    }
                    else -> {
                        Button(onClick = onYenile) {
                            Text("Kod oluştur")
                        }
                    }
                }
                if (hata != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hata,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKapat) {
                Text("Kapat")
            }
        }
    )
} // HastaYakiniDavetDiyalogu: kodu gösterir, kopyalar ve gerekirse yeniler.
