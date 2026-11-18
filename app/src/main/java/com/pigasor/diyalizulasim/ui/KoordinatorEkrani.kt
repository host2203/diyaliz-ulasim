package com.pigasor.diyalizulasim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.pigasor.diyalizulasim.data.Kapasite
import com.pigasor.diyalizulasim.model.DelayReport
import com.pigasor.diyalizulasim.model.DiyalizMerkezi
import com.pigasor.diyalizulasim.model.DriverDelayReport
import com.pigasor.diyalizulasim.model.DriverDelayStatus
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.Personnel
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.ReturnPlanStatus
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.Vehicle
import com.pigasor.diyalizulasim.model.durumEtiketi
import com.pigasor.diyalizulasim.model.etiket
import com.pigasor.diyalizulasim.model.etaGorunurMu
import com.pigasor.diyalizulasim.model.hazirlikEtiketi
import com.pigasor.diyalizulasim.model.oncelikEtiketi
import com.pigasor.diyalizulasim.security.Guvenlik
import com.pigasor.diyalizulasim.viewmodel.KoordinatorViewModel
import kotlin.math.abs

@Composable
fun KoordinatorPaneliEkrani(onCikis: () -> Unit) {
    val koordinatorViewModel: KoordinatorViewModel = viewModel()
    val durum by koordinatorViewModel.durum.collectAsStateWithLifecycle()
    var atamaTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var onayTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var returnTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var oncelikTalep by remember { mutableStateOf<TransportRequest?>(null) }
    var bildirimAcik by remember { mutableStateOf(false) }
    var kapasiteAcik by remember { mutableStateOf(false) }
    var aracYonetimAcik by remember { mutableStateOf(false) }
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
                text = "Koordinatör Paneli",
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
            text = "Merhaba, ${durum.kullanici?.name ?: "koordinatör"}",
            style = MaterialTheme.typography.titleMedium
        ) // Koordinatörün adı.

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { kapasiteAcik = true }) {
                Text("Merkez kapasiteleri")
            } // Kapasite diyaloğunu açar; merkez adı ve seans başına hasta sayısı girilir.
            TextButton(onClick = { aracYonetimAcik = true }) {
                Text("Araç/personel")
            } // Araç ve personel ekleme/düzenleme diyaloğunu açar.
        }

        durum.islemHatasi?.let { islemHatasi ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = islemHatasi,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // İşlem hatası: atama/durum güncellemesi başarısızsa gösterilir.

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
                    text = "Henüz ulaşım talebi yok. Hastaların oluşturduğu talepler burada görünecek.",
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
                        KoordinatorTalepKarti(
                            talep = talep,
                            gecikmeRaporu = durum.gecikmeRaporlari[talep.id],
                            gorevliGecikmeRaporu = durum.gorevliGecikmeRaporlari[talep.id],
                            onAtama = { atamaTalep = talep },
                            onGecikmeOnayla = { onayTalep = talep },
                            onReturnPlaniOnayla = { returnTalep = talep },
                            onOncelikDogrula = { oncelikTalep = talep },
                            onGecmis = { gecmisTalepId = talep.id }
                        )
                    }
                } // LazyColumn: tüm talepleri gerçek zamanlı kart listesi olarak gösterir.
            } // Liste durumu.
        } // Durum seçimi.
    } // Ana düzen.

    val secilenTalep = atamaTalep
    if (secilenTalep != null) {
        AtamaDiyalogu(
            talep = secilenTalep,
            araclar = durum.araclar,
            personeller = durum.personeller,
            tumTalepler = durum.talepler,
            yukleniyor = durum.islemYukleniyor,
            onKapat = { atamaTalep = null },
            onKaydet = { arac, personel, saat ->
                koordinatorViewModel.atamaYap(secilenTalep, arac, personel, saat)
                atamaTalep = null
            }
        )
    } // Seçilen talep varsa atama diyaloğu açılır.

    if (bildirimAcik) {
        BildirimDiyalogu(
            bildirimler = durum.bildirimler,
            onOkunduIsaretle = { koordinatorViewModel.bildirimleriOkunduIsaretle() },
            onKapat = { bildirimAcik = false }
        )
    } // Bildirim merkezi.

    onayTalep?.let { talep ->
        GecikmeOnayDiyalogu(
            talep = talep,
            gecikmeRaporu = durum.gecikmeRaporlari[talep.id],
            islemYukleniyor = durum.islemYukleniyor,
            onVazgec = { onayTalep = null },
            onOnayla = {
                koordinatorViewModel.gecikmeOnayla(talep)
                onayTalep = null
            }
        )
    } // Gecikmeli hasta için onay ve randevu erteleme diyaloğu.

    returnTalep?.let { talep ->
        DonusPlaniOnayDiyalogu(
            talep = talep,
            araclar = durum.araclar,
            personeller = durum.personeller,
            islemYukleniyor = durum.islemYukleniyor,
            onVazgec = { returnTalep = null },
            onOnayla = { arac, personel ->
                koordinatorViewModel.returnPlaniOnayla(talep, arac, personel)
                returnTalep = null
            }
        )
    } // Otomatik dönüş planı onay diyaloğu.

    oncelikTalep?.let { talep ->
        OncelikDogrulaDiyalogu(
            talep = talep,
            islemYukleniyor = durum.islemYukleniyor,
            onVazgec = { oncelikTalep = null },
            onDogrula = { not ->
                koordinatorViewModel.oncelikDogrula(talep, not)
                oncelikTalep = null
            }
        )
    } // Öncelik doğrulama diyaloğu.

    if (kapasiteAcik) {
        KapasiteDiyalogu(
            merkezler = durum.merkezler,
            yukleniyor = durum.islemYukleniyor,
            onKapat = { kapasiteAcik = false },
            onKaydet = { merkez, kapasite ->
                koordinatorViewModel.merkezKapasitesiKaydet(merkez, kapasite)
            }
        )
    } // Merkez kapasitesi diyaloğu.

    if (aracYonetimAcik) {
        AracPersonelYonetimDiyalogu(
            araclar = durum.tumAraclar,
            personeller = durum.tumPersoneller,
            yukleniyor = durum.islemYukleniyor,
            onAracKaydet = { arac -> koordinatorViewModel.aracKaydet(arac) },
            onPersonelKaydet = { personel ->
                koordinatorViewModel.personelKaydet(personel)
            },
            onKapat = { aracYonetimAcik = false }
        )
    } // Araç/personel yönetim diyaloğu.

    gecmisTalepId?.let { talepId ->
        TalepGecmisiDiyalogu(talepId = talepId, onKapat = { gecmisTalepId = null })
    } // Durum geçmişi: denetim izini koordinatöre gösterir.
} // KoordinatorPaneliEkrani: koordinatörün talepleri ve merkez kapasitelerini yönettiği ekran.

@Composable
private fun KoordinatorTalepKarti(
    talep: TransportRequest,
    gecikmeRaporu: DelayReport?,
    gorevliGecikmeRaporu: DriverDelayReport?,
    onAtama: () -> Unit,
    onGecikmeOnayla: () -> Unit,
    onReturnPlaniOnayla: () -> Unit,
    onOncelikDogrula: () -> Unit,
    onGecmis: () -> Unit
) {
    val seansZamani = Guvenlik.seansZamani(talep.sessionDate, talep.sessionTime)
    val kalan = seansZamani?.let { it - System.currentTimeMillis() }
    val yakinVeAtanmadi = talep.status == TransportStatus.REQUESTED &&
        kalan != null && kalan in 0..(24L * 60L * 60L * 1000L)
    val gecikmeOnaylandi = talep.readiness == ReadinessStatus.DELAYED && talep.delayOverride

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = talep.patientName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            ) // Hasta adı.
            Text(
                text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} • ${talep.sessionTime} • ${talep.centerName}",
                style = MaterialTheme.typography.bodySmall
            ) // Seans tarihi, saati ve merkez.
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
                ) // Erişilebilirlik ve refakat ihtiyacı.
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
                    ) // Refakatçi iletişim bilgisi; yalnızca koordinatör görür.
                } // Bilgi girilmişse gösterilir.
            } // Refakatçi gerekiyorsa gösterilir.

            if (talep.priorityCategory != OncelikKategorisi.NONE) {
                Text(
                    text = "Öncelik: ${talep.priorityCategory.oncelikEtiketi} • " +
                        if (talep.priorityVerified) "doğrulandı" else "doğrulanmadı",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (talep.priorityVerified) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                ) // Öncelik kategorisi ve doğrulama durumu.
                if (talep.priorityVerified && talep.priorityVerificationNote.isNotBlank()) {
                    Text(
                        text = "Doğrulama notu: ${talep.priorityVerificationNote}",
                        style = MaterialTheme.typography.bodySmall
                    ) // Belge içeriği değil, yalnızca doğrulama notu gösterilir.
                } // Doğrulama notu varsa gösterilir.
            } // Öncelik varsa gösterilir.

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Durum: ${talep.status.durumEtiketi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            ) // Güncel taşıma durumu.
            Text(
                text = "Hazırlık: ${talep.readiness.hazirlikEtiketi}",
                style = MaterialTheme.typography.bodySmall
            ) // Hasta hazırlık bildirimi.
            if (talep.vehicleName.isNotBlank()) {
                Text(
                    text = "Araç: ${talep.vehicleName} • Görevli: ${talep.personnel}",
                    style = MaterialTheme.typography.bodySmall
                ) // Atanan araç ve görevli.
                if (talep.plannedPickupTime.isNotBlank()) {
                    Text(
                        text = "Planlanan alınma: ${talep.plannedPickupTime}",
                        style = MaterialTheme.typography.bodySmall
                    ) // Planlanan alınma saati.
                } // Saat varsa gösterilir.
            } // Atama varsa gösterilir.

            if (yakinVeAtanmadi) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Uyarı: Seans yaklaşıyor, talep hâlâ atanmadı.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } // Yanıtsız talep uyarısı.


            if (talep.readiness == ReadinessStatus.DELAYED) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Gecikme: ${talep.delayMinutes} dk • " + when {
                        gecikmeOnaylandi -> "onaylandı"
                        talep.status != TransportStatus.REQUESTED &&
                            talep.status != TransportStatus.PLANNED -> "bilgi verildi"
                        else -> "onay bekliyor"
                    },
                    color = if (gecikmeOnaylandi ||
                        (talep.status != TransportStatus.REQUESTED &&
                            talep.status != TransportStatus.PLANNED)
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall
                ) // Gecikme süresi ve onay/bilgi durumu.
                Text(
                    text = "Gecikme nedeni: " +
                        (gecikmeRaporu?.reason?.ifBlank { "belirtilmedi" } ?: "kayıt yok"),
                    style = MaterialTheme.typography.bodySmall
                ) // Gizli gerekçe yalnızca koordinatör kartında gösterilir.
                val hastaMesaji = gecikmeRaporu?.driverMessage
                if (!hastaMesaji.isNullOrBlank()) {
                    Text(
                        text = "Görevliye iletilecek mesaj: $hastaMesaji",
                        style = MaterialTheme.typography.bodySmall
                    ) // Hasta bu mesajın görevliyle paylaşılmasını açıkça istemiştir.
                } // Mesaj varsa gösterilir.
                if (gecikmeOnaylandi && talep.delayRescheduled) {
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
                    ) // Onayla birlikte yapılan saat ertelemesi.
                } else if (gecikmeOnaylandi) {
                    Text(
                        text = "Onaylandı; araç yolda olduğu için seans saati değiştirilmedi.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    ) // Yola çıkmış araçta yalnızca alım onayı verilir.
                } // Onay varsa erteleme bilgisi gösterilir.
            } // Gecikme bildirimi varsa gösterilir.

            if (talep.etaGorunurMu) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tahmini varış: ${talep.etaMinutes} dk • ${talep.etaTarget.etiket}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                ) // OSRM tabanlı gerçek rota süresi; varsa görevli gecikmesi dahil.
            } // ETA varsa gösterilir.

            if (talep.returnStartedAt > 0L) {
                Text(
                    text = "Dönüş yolculuğu başladı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                ) // Ayrı bir durum eklemeden dönüş ayağının başladığını gösterir.
            } // Dönüş başladıysa gösterilir.

            if (talep.driverDelayStatus != DriverDelayStatus.NONE) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Görevli gecikmesi: ${talep.driverDelayMinutes} dk • " +
                        talep.driverDelayStatus.etiket,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                ) // Görevli gecikmesi bilgi amaçlıdır; koordinatör onayı gerekmez.
                val gorevliNeden = gorevliGecikmeRaporu?.reason
                if (!gorevliNeden.isNullOrBlank()) {
                    Text(
                        text = "Görevli gecikme nedeni: $gorevliNeden",
                        style = MaterialTheme.typography.bodySmall
                    ) // Gizli gerekçe yalnızca koordinatör kartında görünür.
                } // Gerekçe varsa gösterilir.
            } // Görevli gecikmesi varsa gösterilir.

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
                ) // Otomatik hesaplanan dönüş saati ve onay durumu.
                if (talep.returnPlanStatus == ReturnPlanStatus.APPROVED) {
                    Text(
                        text = "Dönüş aracı: ${talep.vehicleName} • Görevli: ${talep.personnel}",
                        style = MaterialTheme.typography.bodySmall
                    ) // Dönüş için onaylanan araç ve görevli.
                } // Onaylandıysa atama bilgisi gösterilir.
            } // Gidiş-dönüş planı varsa gösterilir.

            Spacer(Modifier.height(4.dp))

            Column {
                if (talep.status == TransportStatus.REQUESTED ||
                    talep.status == TransportStatus.PLANNED
                ) {
                    TextButton(onClick = onAtama) {
                        Text(if (talep.vehicleName.isBlank()) "Araç/Görevli Ata" else "Atamayı Düzenle")
                    } // Atama düğmesi: diyaloğu açar.
                } // Yalnızca yolculuk başlamadan atama değiştirilebilir.
                if (talep.readiness == ReadinessStatus.DELAYED &&
                    !talep.delayOverride &&
                    talep.delayMinutes > 0 &&
                    (talep.status == TransportStatus.REQUESTED ||
                        talep.status == TransportStatus.PLANNED)
                ) {
                    TextButton(onClick = onGecikmeOnayla) {
                        Text("Gecikmeyi onayla")
                    } // Onay: aracın durumuna göre randevuyu erteler ve alımı açar.
                } // Onay bekleyen gecikme varsa düğme gösterilir.
                if (talep.returnPlanStatus == ReturnPlanStatus.PENDING) {
                    TextButton(onClick = onReturnPlaniOnayla) {
                        Text("Dönüş planını onayla")
                    } // Otomatik dönüş planını uygun araç/görevliyle onaylar.
                } // Dönüş planı onay bekliyorsa gösterilir.
                if (talep.priorityCategory != OncelikKategorisi.NONE && !talep.priorityVerified) {
                    TextButton(onClick = onOncelikDogrula) {
                        Text("Önceliği doğrula")
                    } // Öncelik doğrulama: resmi belge kontrolü sonrası işaretlenir.
                } // Doğrulanmamış öncelik varsa gösterilir.
                TextButton(onClick = onGecmis) {
                    Text("Durum geçmişi")
                } // Talep yaşam döngüsündeki tüm olayları gösterir.
                // Durum adımlarını koordinatör değil, atanan görevli günceller.
            } // Eylem düğmeleri.
        } // Kart içeriği.
    } // Talep kartı.
} // KoordinatorTalepKarti: koordinatörün gördüğü talep özeti ve eylemleri.

@Composable
private fun AtamaDiyalogu(
    talep: TransportRequest,
    araclar: List<Vehicle>,
    personeller: List<Personnel>,
    tumTalepler: List<TransportRequest>,
    yukleniyor: Boolean,
    onKapat: () -> Unit,
    onKaydet: (Vehicle, Personnel, String) -> Unit
) {
    var secilenArac by remember(talep.id) {
        mutableStateOf(araclar.firstOrNull { it.id == talep.vehicleId })
    }
    var secilenPersonel by remember(talep.id) {
        mutableStateOf(personeller.firstOrNull { it.id == talep.personnelId })
    }
    var saatRakam by remember(talep.id) {
        mutableStateOf(talep.plannedPickupTime.filter { it.isDigit() }.take(4))
    }
    val saatHatasi = if (saatRakam.length == 4) Guvenlik.saatHataMesaji(saatRakam) else null
    val planlananSaat = Guvenlik.saatRakamdanGorunen(saatRakam)
    val arac = secilenArac
    val tekerlekUyarisi = arac != null &&
        talep.wheelchairRequired && !arac.wheelchairAccessible
    val kapasiteUyarisi = arac != null &&
        (1 + if (talep.companionRequired) 1 else 0) > arac.capacity
    val cakismaUyarisi = if (arac != null && planlananSaat != null) {
        cakismaVarMi(
            talepler = tumTalepler,
            secilenTalep = talep,
            aracId = arac.id,
            gorevli = secilenPersonel?.name ?: "",
            planlananSaat = planlananSaat
        )
    } else {
        false
    }

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Araç ve görevli ata") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${talep.patientName} • ${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime}",
                    style = MaterialTheme.typography.bodySmall
                ) // Atanacak talep özeti.

                Spacer(Modifier.height(8.dp))

                if (araclar.isEmpty()) {
                    Text(
                        text = "Kayıtlı araç yok. Firestore'daki vehicles koleksiyonuna araç ekleyin.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Araç seçin",
                        style = MaterialTheme.typography.labelLarge
                    ) // Araç başlığı.
                    araclar.forEach { aracSecenek ->
                        FilterChip(
                            selected = arac?.id == aracSecenek.id,
                            onClick = { secilenArac = aracSecenek },
                            label = { Text(aracEtiketi(aracSecenek)) },
                            modifier = Modifier.fillMaxWidth()
                        ) // Araç çipi: kapasite ve erişilebilirlik bilgisiyle birlikte seçim sunar.
                    }
                } // Araç listesi veya uyarı gösterilir.

                Spacer(Modifier.height(8.dp))

                if (personeller.isEmpty()) {
                    Text(
                        text = "Kayıtlı görevli yok. Firestore'daki personnel koleksiyonuna görevli ekleyin.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Görevli seçin",
                        style = MaterialTheme.typography.labelLarge
                    ) // Görevli başlığı.
                    personeller.forEach { personelSecenek ->
                        FilterChip(
                            selected = secilenPersonel?.id == personelSecenek.id,
                            onClick = { secilenPersonel = personelSecenek },
                            label = { Text(personelEtiketi(personelSecenek)) },
                            modifier = Modifier.fillMaxWidth()
                        ) // Görevli çipi: ad ve telefonla birlikte seçim sunar.
                    }
                } // Görevli listesi veya uyarı gösterilir.

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = saatRakam,
                    onValueChange = { yeni -> saatRakam = yeni.filter { it.isDigit() }.take(4) },
                    label = { Text("Planlanan alınma saati") },
                    placeholder = { Text("09:30") },
                    singleLine = true,
                    visualTransformation = SaatGorselDonusturucu(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                ) // Alınma saati: 4 rakam yazılır, ekranda SS:DD görünür.

                if (saatHatasi != null) {
                    Text(
                        text = saatHatasi,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } // Saat hatası.

                if (tekerlekUyarisi) {
                    Text(
                        text = "Uyarı: Seçilen araç tekerlekli sandalye uyumlu değil.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } // Erişilebilirlik uyarısı.

                if (kapasiteUyarisi) {
                    Text(
                        text = "Uyarı: Araç kapasitesi hasta + refakatçi için yetersiz.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } // Kapasite uyarısı.

                if (cakismaUyarisi) {
                    Text(
                        text = "Uyarı: Bu araç veya görevli aynı saatte başka bir talebe atanmış.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } // Çakışma uyarısı.
            } // Diyalog içeriği.
        },
        confirmButton = {
            Button(
                onClick = {
                    val seciliArac = secilenArac
                    val seciliPersonel = secilenPersonel
                    val saat = planlananSaat
                    if (seciliArac != null && seciliPersonel != null && saat != null) {
                        onKaydet(seciliArac, seciliPersonel, saat)
                    }
                },
                enabled = !yukleniyor && secilenArac != null &&
                    secilenPersonel != null && planlananSaat != null
            ) {
                Text("Atamayı Kaydet")
            } // Kaydet düğmesi: uyarılar olsa bile koordinatörün kararıyla atamayı kaydeder.
        },
        dismissButton = {
            TextButton(onClick = onKapat) {
                Text("İptal")
            } // İptal düğmesi.
        }
    ) // Atama diyaloğu.
} // AtamaDiyalogu: araç, görevli ve alınma saatini seçtirir; uyarıları gösterir.

@Composable
private fun GecikmeOnayDiyalogu(
    talep: TransportRequest,
    gecikmeRaporu: DelayReport?,
    islemYukleniyor: Boolean,
    onVazgec: () -> Unit,
    onOnayla: () -> Unit
) {
    val saatErtelenecek = talep.status == TransportStatus.REQUESTED ||
        talep.status == TransportStatus.PLANNED
    val yeniSeans = if (saatErtelenecek) {
        Guvenlik.zamaniErtele(talep.sessionDate, talep.sessionTime, talep.delayMinutes)
    } else {
        null
    }
    val yeniAlinma = if (saatErtelenecek && talep.plannedPickupTime.isNotBlank()) {
        Guvenlik.zamaniErtele(talep.sessionDate, talep.plannedPickupTime, talep.delayMinutes)
            ?.second
    } else {
        null
    }
    val onaylanabilir = !islemYukleniyor && (!saatErtelenecek || yeniSeans != null)

    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text("Gecikmeyi onayla") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} ${talep.sessionTime} • ${talep.patientName}",
                    style = MaterialTheme.typography.bodySmall
                ) // Onay verilecek talep özeti.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hasta ${talep.delayMinutes} dakika gecikeceğini bildirdi.",
                    style = MaterialTheme.typography.bodySmall
                ) // Hastanın belirttiği süre.
                Text(
                    text = "Gecikme nedeni: " +
                        (gecikmeRaporu?.reason?.ifBlank { "belirtilmedi" } ?: "kayıt yok"),
                    style = MaterialTheme.typography.bodySmall
                ) // Gizli gerekçe yalnızca koordinatöre gösterilir.
                val hastaMesaji = gecikmeRaporu?.driverMessage
                if (!hastaMesaji.isNullOrBlank()) {
                    Text(
                        text = "Onay sonrası görevliye iletilecek mesaj: $hastaMesaji",
                        style = MaterialTheme.typography.bodySmall
                    ) // Mesaj, hastanın görevliyle paylaşmayı seçtiği metindir.
                } // Mesaj varsa gösterilir.
                Spacer(Modifier.height(8.dp))
                if (saatErtelenecek) {
                    Text(
                        text = if (talep.status == TransportStatus.REQUESTED) {
                            "Araç henüz atanmadı. Onaylarsanız seans saati:"
                        } else {
                            "Araç henüz yola çıkmadı. Onaylarsanız seans saati:"
                        },
                        style = MaterialTheme.typography.bodySmall
                    ) // Ertelemenin hangi durumda yapılacağını açıklar.
                    if (yeniSeans != null) {
                        Text(
                            text = "${Guvenlik.tarihIsoDanTurkce(talep.sessionDate)} " +
                                "${talep.sessionTime} → " +
                                "${Guvenlik.tarihIsoDanTurkce(yeniSeans.first)} ${yeniSeans.second}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        ) // Onay sonrası oluşacak yeni seans zamanı.
                    } else {
                        Text(
                            text = "Saat hesaplanamadı; onay verilemez.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!yeniAlinma.isNullOrBlank()) {
                        Text(
                            text = "Alınma saati: ${talep.plannedPickupTime} → $yeniAlinma",
                            style = MaterialTheme.typography.bodySmall
                        ) // Araç atanmışsa alınma saati de aynı süre kadar kayar.
                    }
                } else {
                    Text(
                        text = "Araç yolda. Seans saati değiştirilmeyecek; yalnızca hasta alımına onay verilecek.",
                        style = MaterialTheme.typography.bodySmall
                    ) // Yola çıkmış araçta randevu saati değiştirilmez.
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Gerekçe görevliye gösterilmez. Onaydan sonra görevliye yalnızca " +
                        "süre, yeni saat ve hastanın mesajı iletilir.",
                    style = MaterialTheme.typography.bodySmall
                ) // Gizlilik kuralını koordinatöre hatırlatır.
            }
        },
        confirmButton = {
            Button(
                onClick = onOnayla,
                enabled = onaylanabilir
            ) {
                Text(if (saatErtelenecek) "Onayla ve ertele" else "Onayla")
            } // Onay düğmesi: uygun değilse erteleme yapılamaz.
        },
        dismissButton = {
            TextButton(onClick = onVazgec, enabled = !islemYukleniyor) {
                Text("Vazgeç")
            }
        }
    ) // Gecikme onay ve erteleme diyaloğu.
} // GecikmeOnayDiyalogu: gizli gerekçeyi gösterir, araç durumuna göre erteleme önizler.

@Composable
private fun DonusPlaniOnayDiyalogu(
    talep: TransportRequest,
    araclar: List<Vehicle>,
    personeller: List<Personnel>,
    islemYukleniyor: Boolean,
    onVazgec: () -> Unit,
    onOnayla: (Vehicle, Personnel) -> Unit
) {
    var secilenArac by remember(talep.id) {
        mutableStateOf(araclar.firstOrNull { it.id == talep.vehicleId } ?: araclar.firstOrNull())
    }
    var secilenPersonel by remember(talep.id) {
        mutableStateOf(
            personeller.firstOrNull { it.id == talep.personnelId } ?: personeller.firstOrNull()
        )
    }
    val arac = secilenArac
    val tekerlekUygun = arac == null || !talep.wheelchairRequired || arac.wheelchairAccessible
    val gerekliKapasite = 1 + if (talep.companionRequired) 1 else 0
    val kapasiteUygun = arac == null || arac.capacity >= gerekliKapasite
    val uygun = arac != null && secilenPersonel != null && tekerlekUygun && kapasiteUygun

    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text("Dönüş planını onayla") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${talep.patientName} • ${Guvenlik.kisaZamanGorunen(talep.returnPlannedAt)}",
                    style = MaterialTheme.typography.bodySmall
                ) // Dönüş planı yapılacak hasta ve saat.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Seans süresi: ${talep.sessionDurationMinutes} dk • " +
                        "Tampon: ${talep.returnBufferMinutes} dk",
                    style = MaterialTheme.typography.bodySmall
                ) // Dönüş saatinin nasıl hesaplandığını gösterir.
                val ihtiyaclar = buildList {
                    if (talep.wheelchairRequired) add("tekerlekli sandalye")
                    if (talep.companionRequired) add("refakatçi")
                }.joinToString(", ")
                if (ihtiyaclar.isNotBlank()) {
                    Text(
                        text = "Dönüş ihtiyacı: $ihtiyaclar",
                        style = MaterialTheme.typography.bodySmall
                    ) // Dönüş aracında sağlanması gereken ihtiyaçlar.
                } // İhtiyaç varsa gösterilir.
                Spacer(Modifier.height(8.dp))
                if (araclar.isEmpty() || personeller.isEmpty()) {
                    Text(
                        text = "Uygun araç/görevli bulunamadı. Plan onay bekliyor olarak kalacak.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Dönüş aracı", style = MaterialTheme.typography.labelLarge)
                    araclar.forEach { secenek ->
                        FilterChip(
                            selected = arac?.id == secenek.id,
                            onClick = { secilenArac = secenek },
                            label = { Text(aracEtiketi(secenek)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Dönüş görevlisi", style = MaterialTheme.typography.labelLarge)
                    personeller.forEach { secenek ->
                        FilterChip(
                            selected = secilenPersonel?.id == secenek.id,
                            onClick = { secilenPersonel = secenek },
                            label = { Text(personelEtiketi(secenek)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } // Araç/görevli listesi veya uyarı.
                if (!tekerlekUygun) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Seçilen araç tekerlekli sandalye uyumlu değil; onaylanamaz.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } // Erişilebilirlik engeli.
                if (!kapasiteUygun) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Seçilen araç hasta + refakatçi kapasitesine uygun değil; onaylanamaz.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } // Kapasite engeli.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Onaylandığında hasta ve dönüş görevlisi bilgilendirilir; " +
                        "dönüş ETA'sı görevli yola çıkınca hesaplanır.",
                    style = MaterialTheme.typography.bodySmall
                ) // Onayın sonucunu açıklar.
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val secili = secilenArac
                    val gorevli = secilenPersonel
                    if (secili != null && gorevli != null) {
                        onOnayla(secili, gorevli)
                    }
                },
                enabled = !islemYukleniyor && uygun
            ) {
                Text("Onayla ve ata")
            } // Uygun olmayan araç/görevliyle onay verilemez.
        },
        dismissButton = {
            TextButton(onClick = onVazgec, enabled = !islemYukleniyor) {
                Text("Vazgeç")
            }
        }
    ) // Dönüş planı onay diyaloğu.
} // DonusPlaniOnayDiyalogu: uygunluk kontrolüyle dönüş aracı/görevlisini onaylatır.

@Composable
private fun OncelikDogrulaDiyalogu(
    talep: TransportRequest,
    islemYukleniyor: Boolean,
    onVazgec: () -> Unit,
    onDogrula: (String) -> Unit
) {
    var belgeKontrolEdildi by remember(talep.id) { mutableStateOf(false) }
    var not by remember(talep.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text("Önceliği doğrula") },
        text = {
            Column {
                Text(
                    text = "${talep.patientName} • ${talep.priorityCategory.oncelikEtiketi}",
                    style = MaterialTheme.typography.bodySmall
                ) // Doğrulanacak hasta ve kategori.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Öncelik yalnızca resmi belge (sağlık kurulu raporu, engelli kimlik kartı, şehit/gazi yakını belgesi) kontrol edilerek doğrulanır. Belge veya tanı sisteme yüklenmez.",
                    style = MaterialTheme.typography.bodySmall
                ) // Veri minimizasyonu: belge içeriği saklanmaz.
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = belgeKontrolEdildi,
                        onCheckedChange = { belgeKontrolEdildi = it }
                    )
                    Text(
                        text = "Resmi belgeyi kontrol ettim",
                        style = MaterialTheme.typography.bodySmall
                    ) // Zorunlu onay: doğrulamanın belgeye dayandığını kaydeder.
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = not,
                    onValueChange = { not = it.take(200) },
                    label = { Text("Doğrulama notu (isteğe bağlı)") },
                    modifier = Modifier.fillMaxWidth()
                ) // Not: belge türü gibi kısa bilgi; tanı ayrıntısı istenmez.
            }
        },
        confirmButton = {
            Button(
                onClick = { onDogrula(not) },
                enabled = !islemYukleniyor && belgeKontrolEdildi
            ) {
                Text("Doğrula")
            } // Belge onayı işaretlenmeden doğrulama yapılamaz.
        },
        dismissButton = {
            TextButton(onClick = onVazgec, enabled = !islemYukleniyor) {
                Text("Vazgeç")
            }
        }
    ) // Öncelik doğrulama diyaloğu.
} // OncelikDogrulaDiyalogu: belge kontrolü onayını zorunlu kılar.

// Araç seçeneğinin ad, plaka, kapasite ve erişilebilirlik özetini üretir.
private fun aracEtiketi(arac: Vehicle): String {
    val plaka = if (arac.plate.isNotBlank()) " (${arac.plate})" else ""
    val tekerlek = if (arac.wheelchairAccessible) ", tekerlekli uyumlu" else ""
    return "${arac.name}$plaka • ${arac.capacity} kişi$tekerlek"
} // aracEtiketi: koordinatörün aracı özellikleriyle birlikte seçmesini sağlar.

// Görevli seçeneğinin ad ve telefon özetini üretir.
private fun personelEtiketi(personel: Personnel): String {
    val telefon = if (personel.phone.isNotBlank()) " • ${personel.phone}" else ""
    return "${personel.name}$telefon"
} // personelEtiketi: koordinatörün doğru görevliyi seçmesini sağlar.

// Aynı araç veya görevlinin aynı gün ve yakın saatte başka talebe atanıp atanmadığını kontrol eder.
private fun cakismaVarMi(
    talepler: List<TransportRequest>,
    secilenTalep: TransportRequest,
    aracId: String,
    gorevli: String,
    planlananSaat: String
): Boolean {
    val yeniDakika = Guvenlik.saatDakikayaCevir(planlananSaat) ?: return false
    return talepler.any { diger ->
        if (diger.id == secilenTalep.id) return@any false
        if (diger.sessionDate != secilenTalep.sessionDate) return@any false
        if (diger.status == TransportStatus.CANCELLED ||
            diger.status == TransportStatus.DROPPED_HOME
        ) return@any false
        val ayniArac = diger.vehicleId.isNotBlank() && diger.vehicleId == aracId
        val ayniGorevli = gorevli.isNotBlank() &&
            diger.personnel.isNotBlank() &&
            diger.personnel.equals(gorevli, ignoreCase = true)
        if (!ayniArac && !ayniGorevli) return@any false
        val digerDakika = Guvenlik.saatDakikayaCevir(diger.plannedPickupTime)
            ?: return@any false
        abs(digerDakika - yeniDakika) < 60
    }
} // cakismaVarMi: 60 dakikalık tampon içindeki araç/görevli çakışmalarını bulur.

// Koordinatörün bir merkez için seans başına hasta kapasitesini girmesini sağlar.
@Composable
private fun KapasiteDiyalogu(
    merkezler: List<DiyalizMerkezi>,
    yukleniyor: Boolean,
    onKapat: () -> Unit,
    onKaydet: (DiyalizMerkezi, Int) -> Unit
) {
    val ilkMerkez = merkezler.firstOrNull()
    var secilenEtiket by remember {
        mutableStateOf(ilkMerkez?.let(::merkezEtiketi) ?: "")
    }
    var kapasiteMetni by remember {
        mutableStateOf(ilkMerkez?.kapasitePerSeans?.toString() ?: "")
    }
    var menuAcik by remember { mutableStateOf(false) }
    val secilen = merkezler.firstOrNull { merkezEtiketi(it) == secilenEtiket }
    val kapasite = kapasiteMetni.toIntOrNull()
    val kapasiteGecerli = kapasite != null && Kapasite.kapasiteGecerliMi(kapasite)

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Merkez kapasitesi") },
        text = {
            Column {
                Text(
                    text = "Seans başına hasta kapasitesi. 0 = doluluk kontrolü kapalı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { menuAcik = true },
                    enabled = merkezler.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secilen?.ad ?: "Merkez seçin")
                }
                DropdownMenu(expanded = menuAcik, onDismissRequest = { menuAcik = false }) {
                    merkezler.forEach { merkez ->
                        DropdownMenuItem(
                            text = {
                                Text("${merkez.ad} (${merkez.il}/${merkez.ilce})")
                            },
                            onClick = {
                                secilenEtiket = merkezEtiketi(merkez)
                                kapasiteMetni = merkez.kapasitePerSeans.toString()
                                menuAcik = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = kapasiteMetni,
                    onValueChange = { yeni ->
                        kapasiteMetni = yeni.filter { it.isDigit() }.take(2)
                    },
                    label = {
                        Text("Kapasite (0-${Kapasite.MAKS_SEANS_KAPASITESI})")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (kapasiteMetni.isNotBlank() && !kapasiteGecerli) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "0-${Kapasite.MAKS_SEANS_KAPASITESI} arasında bir sayı girin.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (secilen != null && kapasite != null && kapasiteGecerli) {
                        onKaydet(secilen, kapasite)
                    }
                },
                enabled = !yukleniyor && secilen != null && kapasiteGecerli
            ) {
                Text(if (yukleniyor) "Kaydediliyor..." else "Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onKapat) {
                Text("Kapat")
            }
        }
    )
} // KapasiteDiyalogu: merkez seçimi, kapasite girişi ve doğrulamayı tek diyalogda toplar.

// Merkez listesindeki aynı adlı kayıtları ayırt etmek için il/ilçe ile anahtar üretir.
private fun merkezEtiketi(merkez: DiyalizMerkezi): String {
    return "${merkez.ad}|${merkez.il}|${merkez.ilce}"
}

// Koordinatörün araç ve personel kayıtlarını listeleyip düzenlemesini sağlar.
@Composable
private fun AracPersonelYonetimDiyalogu(
    araclar: List<Vehicle>,
    personeller: List<Personnel>,
    yukleniyor: Boolean,
    onAracKaydet: (Vehicle) -> Unit,
    onPersonelKaydet: (Personnel) -> Unit,
    onKapat: () -> Unit
) {
    var aracForm by remember { mutableStateOf<Vehicle?>(null) }
    var personelForm by remember { mutableStateOf<Personnel?>(null) }

    val secilenArac = aracForm
    if (secilenArac != null) {
        AracFormDiyalogu(
            arac = secilenArac,
            yukleniyor = yukleniyor,
            onVazgec = { aracForm = null },
            onKaydet = {
                onAracKaydet(it)
                aracForm = null
            }
        )
        return
    }
    val secilenPersonel = personelForm
    if (secilenPersonel != null) {
        PersonelFormDiyalogu(
            personel = secilenPersonel,
            yukleniyor = yukleniyor,
            onVazgec = { personelForm = null },
            onKaydet = {
                onPersonelKaydet(it)
                personelForm = null
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Araç ve personel") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Araçlar", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { aracForm = Vehicle() }) {
                        Text("Yeni")
                    }
                }
                if (araclar.isEmpty()) {
                    Text("Kayıtlı araç yok.", style = MaterialTheme.typography.bodySmall)
                }
                araclar.forEach { arac ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(aracEtiketi(arac), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (arac.active) "Aktif" else "Pasif",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (arac.active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { aracForm = arac }) {
                                Text("Düzenle")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Personel", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { personelForm = Personnel() }) {
                        Text("Yeni")
                    }
                }
                if (personeller.isEmpty()) {
                    Text("Kayıtlı personel yok.", style = MaterialTheme.typography.bodySmall)
                }
                personeller.forEach { personel ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(personelEtiketi(personel), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (personel.active) "Aktif" else "Pasif",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (personel.active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { personelForm = personel }) {
                                Text("Düzenle")
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
} // AracPersonelYonetimDiyalogu: araç/personel listesi ve düzenleme formlarını yönetir.

// Araç ekleme/düzenleme formu.
@Composable
private fun AracFormDiyalogu(
    arac: Vehicle,
    yukleniyor: Boolean,
    onVazgec: () -> Unit,
    onKaydet: (Vehicle) -> Unit
) {
    var ad by remember(arac.id) { mutableStateOf(arac.name) }
    var plaka by remember(arac.id) { mutableStateOf(arac.plate) }
    var kapasiteMetni by remember(arac.id) { mutableStateOf(arac.capacity.toString()) }
    var tekerlekli by remember(arac.id) { mutableStateOf(arac.wheelchairAccessible) }
    var aktif by remember(arac.id) { mutableStateOf(arac.active) }
    val kapasite = kapasiteMetni.toIntOrNull()
    val gecerli = ad.trim().isNotEmpty() && kapasite != null && kapasite in 1..20

    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text(if (arac.id.isBlank()) "Yeni araç" else "Aracı düzenle") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = ad,
                    onValueChange = { ad = it.take(80) },
                    label = { Text("Araç adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = plaka,
                    onValueChange = { plaka = it.take(20) },
                    label = { Text("Plaka (isteğe bağlı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = kapasiteMetni,
                    onValueChange = { yeni ->
                        kapasiteMetni = yeni.filter { it.isDigit() }.take(2)
                    },
                    label = { Text("Kapasite (1-20)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AyarAnahtariSatiri(
                    etiket = "Tekerlekli sandalye uyumlu",
                    secili = tekerlekli,
                    onDegisim = { tekerlekli = it }
                )
                AyarAnahtariSatiri(
                    etiket = "Aktif",
                    secili = aktif,
                    onDegisim = { aktif = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (kapasite != null) {
                        onKaydet(
                            arac.copy(
                                name = ad.trim(),
                                plate = plaka.trim(),
                                capacity = kapasite,
                                wheelchairAccessible = tekerlekli,
                                active = aktif
                            )
                        )
                    }
                },
                enabled = gecerli && !yukleniyor
            ) {
                Text(if (yukleniyor) "Kaydediliyor..." else "Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onVazgec) {
                Text("Vazgeç")
            }
        }
    )
} // AracFormDiyalogu: araç adı, plaka, kapasite ve erişilebilirlik alanlarını toplar.

// Personel ekleme/düzenleme formu.
@Composable
private fun PersonelFormDiyalogu(
    personel: Personnel,
    yukleniyor: Boolean,
    onVazgec: () -> Unit,
    onKaydet: (Personnel) -> Unit
) {
    var ad by remember(personel.id) { mutableStateOf(personel.name) }
    var telefon by remember(personel.id) { mutableStateOf(personel.phone) }
    var driverUid by remember(personel.id) { mutableStateOf(personel.driverUid) }
    var aktif by remember(personel.id) { mutableStateOf(personel.active) }

    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text(if (personel.id.isBlank()) "Yeni personel" else "Personeli düzenle") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = ad,
                    onValueChange = { ad = it.take(80) },
                    label = { Text("Ad soyad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = telefon,
                    onValueChange = { yeni -> telefon = yeni.filter { it.isDigit() }.take(11) },
                    label = { Text("Telefon") },
                    placeholder = { Text("05XXXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = driverUid,
                    onValueChange = { driverUid = it.trim().take(128) },
                    label = { Text("Görevli hesabının UID'si (isteğe bağlı)") },
                    supportingText = { Text("Görevli giriş hesabıyla eşleştirmek için kullanılır.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AyarAnahtariSatiri(
                    etiket = "Aktif",
                    secili = aktif,
                    onDegisim = { aktif = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onKaydet(
                        personel.copy(
                            name = ad.trim(),
                            phone = telefon.trim(),
                            driverUid = driverUid.trim(),
                            active = aktif
                        )
                    )
                },
                enabled = ad.trim().isNotEmpty() && !yukleniyor
            ) {
                Text(if (yukleniyor) "Kaydediliyor..." else "Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onVazgec) {
                Text("Vazgeç")
            }
        }
    )
} // PersonelFormDiyalogu: ad, telefon, görevli hesabı ve aktiflik alanlarını toplar.

// Formlarda kullanılan metin + anahtar satırı.
@Composable
private fun AyarAnahtariSatiri(
    etiket: String,
    secili: Boolean,
    onDegisim: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = etiket, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = secili, onCheckedChange = onDegisim)
    }
}
