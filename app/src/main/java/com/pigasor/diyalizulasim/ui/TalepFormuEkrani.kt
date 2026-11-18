package com.pigasor.diyalizulasim.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pigasor.diyalizulasim.data.Kapasite
import com.pigasor.diyalizulasim.data.OpenStreetMapRepository
import com.pigasor.diyalizulasim.data.YerVerisi
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.oncelikEtiketi
import com.pigasor.diyalizulasim.security.Guvenlik
import com.pigasor.diyalizulasim.viewmodel.TalepFormuViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

// Merkez listesinde yoksa kullanıcının adı elle girmesini sağlayan seçenek.
private const val MANUEL_MERKEZ = "Listede yok / elle gir"

// Refakatçinin hasta ile yakınlık derecesi seçenekleri.
private val YAKINLIK_SECENEKLERI = listOf("Eş", "Çocuk", "Ebeveyn", "Kardeş", "Diğer")

@Composable
fun TalepFormuEkrani(
    onGeri: () -> Unit,
    onKaydedildi: () -> Unit
) {
    var tarihRakam by rememberSaveable { mutableStateOf("") }
    var saatRakam by rememberSaveable { mutableStateOf("") }
    var seansSuresiMetni by rememberSaveable { mutableStateOf("240") }
    var secilenIl by rememberSaveable { mutableStateOf<String?>(null) }
    var secilenIlce by rememberSaveable { mutableStateOf<String?>(null) }
    var secilenMerkez by rememberSaveable { mutableStateOf<String?>(null) }
    var elleMerkez by rememberSaveable { mutableStateOf("") }
    var adres by rememberSaveable { mutableStateOf("") }
    var adresEnlem by rememberSaveable { mutableStateOf(0.0) }
    var adresBoylam by rememberSaveable { mutableStateOf(0.0) }
    var haritaAcik by remember { mutableStateOf(false) }
    var gidisDonus by rememberSaveable { mutableStateOf(true) }
    var refakat by rememberSaveable { mutableStateOf(false) }
    var refakatciAd by rememberSaveable { mutableStateOf("") }
    var refakatciTelefon by rememberSaveable { mutableStateOf("") }
    var refakatciYakinlik by rememberSaveable { mutableStateOf<String?>(null) }
    var tekerlekli by rememberSaveable { mutableStateOf(false) }
    var oncelikAdi by rememberSaveable { mutableStateOf(OncelikKategorisi.NONE.name) }
    var not by rememberSaveable { mutableStateOf("") }
    var mesaj by rememberSaveable { mutableStateOf("") }
    val oncelik = OncelikKategorisi.valueOf(oncelikAdi)

    val formViewModel: TalepFormuViewModel = viewModel()
    val durum by formViewModel.durum.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val iller = remember(context) { YerVerisi.illeriYukle(context) }
    val ilceler = iller.firstOrNull { it.il == secilenIl }?.ilceler.orEmpty()
    val ilgiliMerkezler = durum.merkezler.filter { it.il == secilenIl && it.ilce == secilenIlce }
    val manuelMerkezGerekli = ilgiliMerkezler.isEmpty() || secilenMerkez == MANUEL_MERKEZ
    val secilenMerkezAdi = secilenMerkez
    val secilenMerkezVerisi = ilgiliMerkezler.firstOrNull { it.ad == secilenMerkezAdi }
    val merkezAdi = if (secilenMerkezAdi != null && secilenMerkezAdi != MANUEL_MERKEZ) {
        secilenMerkezAdi
    } else {
        Guvenlik.metniTemizle(elleMerkez)
    }
    val merkezKapasitesi = durum.merkezler
        .firstOrNull { it.ad == merkezAdi }
        ?.kapasitePerSeans ?: 0
    val isoTarih = Guvenlik.tarihRakamdanIso(tarihRakam)
    val gorunenSaat = Guvenlik.saatRakamdanGorunen(saatRakam)
    val tarihHatasi = if (tarihRakam.length == 8) Guvenlik.tarihHataMesaji(tarihRakam) else null
    val saatHatasi = if (saatRakam.length == 4) Guvenlik.saatHataMesaji(saatRakam) else null
    val seansSuresi = seansSuresiMetni.toIntOrNull()
    val seansSuresiGecerli = seansSuresi != null && Guvenlik.seansSuresiGecerliMi(seansSuresi)
    val gecmisZaman = tarihHatasi == null && saatHatasi == null &&
        tarihRakam.length == 8 && saatRakam.length == 4 &&
        !Guvenlik.tarihSaatGelecekteMi(tarihRakam, saatRakam)

    LaunchedEffect(durum.basarili) {
        if (durum.basarili) {
            onKaydedildi()
            formViewModel.basariTuketildi()
        }
    } // LaunchedEffect: kayıt başarılı olunca hasta paneli ekranına geri döner.

    LaunchedEffect(isoTarih, gorunenSaat, merkezAdi, merkezKapasitesi) {
        formViewModel.kapasiteKontrolEt(
            merkezAdi = merkezAdi,
            kapasite = merkezKapasitesi,
            tarihIso = isoTarih.orEmpty(),
            saat = gorunenSaat.orEmpty()
        )
    } // LaunchedEffect: merkez/tarih/saat değiştikçe doluluk özetini günceller.

    LaunchedEffect(secilenIl, secilenIlce) {
        val il = secilenIl
        val ilce = secilenIlce
        if (il != null && ilce != null) {
            delay(800)
            formViewModel.merkezleriAra(il, ilce)
        }
    } // LaunchedEffect: hızlı il/ilçe değişimlerinde önceki aramayı iptal eder, OSM'ye tek istek atar.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yeni Ulaşım Talebi",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            ) // Form başlığı.
            TextButton(onClick = onGeri, enabled = !durum.yukleniyor) {
                Text("Geri")
            } // Geri düğmesi: kaydetmeden hasta paneline döner.
        } // Üst satır.

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = tarihRakam,
            onValueChange = { yeni -> tarihRakam = yeni.filter { it.isDigit() }.take(8) },
            label = { Text("Seans tarihi (gün ay yıl)") },
            placeholder = { Text("05.11.2026") },
            singleLine = true,
            visualTransformation = TarihGorselDonusturucu(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        ) // Tarih alanı: kullanıcı bitişik 8 rakam yazar, ekranda otomatik GG.AA.YYYY görünür.

        if (tarihHatasi != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = tarihHatasi,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // Tarih hatası: geçersiz gün/ay bilgisini açıklar.

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = saatRakam,
            onValueChange = { yeni -> saatRakam = yeni.filter { it.isDigit() }.take(4) },
            label = { Text("Seans saati") },
            placeholder = { Text("09:30") },
            singleLine = true,
            visualTransformation = SaatGorselDonusturucu(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        ) // Saat alanı: kullanıcı 4 rakam yazar, ekranda otomatik SS:DD görünür.

        if (saatHatasi != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = saatHatasi,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // Saat hatası: geçersiz saat/dakika bilgisini açıklar.

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = seansSuresiMetni,
            onValueChange = { yeni ->
                seansSuresiMetni = yeni.filter { it.isDigit() }.take(3)
            },
            label = { Text("Tahmini seans süresi (dakika)") },
            supportingText = {
                Text(
                    if (seansSuresi != null && !seansSuresiGecerli) {
                        "${Guvenlik.MIN_SEANS_DAKIKA}-${Guvenlik.MAKS_SEANS_DAKIKA} dakika girin."
                    } else {
                        "Varsayılan 240 dk; dönüş planı bu süreye göre hesaplanır."
                    }
                )
            },
            isError = seansSuresiMetni.isNotBlank() && !seansSuresiGecerli,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        ) // Seans süresi: dönüş planının otomatik hesaplanmasında kullanılır.

        if (seansSuresiMetni.isNotBlank() && !seansSuresiGecerli) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Seans süresi ${Guvenlik.MIN_SEANS_DAKIKA}-" +
                    "${Guvenlik.MAKS_SEANS_DAKIKA} dakika arasında olmalı.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // Seans süresi hatası.

        if (gecmisZaman) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Geçmiş bir tarih/saat girdiniz. Gelecekteki bir seans seçin.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } // Geçmiş zaman uyarısı: kullanıcıyı kaydetmeden önce uyarır.

        Spacer(Modifier.height(16.dp))

        SecimMenusu(
            etiket = "İl",
            secilen = secilenIl,
            secenekler = iller.map { it.il },
            onSecim = { secilen ->
                secilenIl = secilen
                secilenIlce = null
                secilenMerkez = null
                elleMerkez = ""
            }
        ) // İl menüsü: 81 ilden birini seçtirir; değişince ilçe ve merkez sıfırlanır.

        Spacer(Modifier.height(12.dp))

        SecimMenusu(
            etiket = "İlçe",
            secilen = secilenIlce,
            secenekler = ilceler,
            onSecim = { secilen ->
                secilenIlce = secilen
                secilenMerkez = null
                elleMerkez = ""
            },
            enabled = secilenIl != null
        ) // İlçe menüsü: seçilen ile bağlı ilçeleri gösterir.

        Spacer(Modifier.height(12.dp))

        if (durum.merkezAramaYukleniyor) {
            Text(
                text = "Merkezler aranıyor...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
        } // Arama durumu: Places isteği sürerken kullanıcıya bilgi verir.

        durum.merkezAramaHatasi?.let { hata ->
            Text(
                text = "Merkez araması: $hata",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
        } // Arama hatası: API anahtarı, kota veya bağlantı sorununu gösterir.

        if (ilgiliMerkezler.isNotEmpty()) {
            SecimMenusu(
                etiket = "Diyaliz merkezi",
                secilen = secilenMerkez,
                secenekler = ilgiliMerkezler.map { it.ad } + MANUEL_MERKEZ,
                onSecim = { secilenMerkez = it },
                enabled = secilenIlce != null
            ) // Merkez menüsü: seçilen ilçedeki merkezleri ve elle giriş seçeneğini sunar.
        } else if (secilenIlce != null) {
            Text(
                text = "Bu ilçede kayıtlı merkez yok. Merkez adını aşağıya elle girebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) // Bilgi: merkez listesi boşsa kullanıcıyı elle girişe yönlendirir.
        } // Merkez seçimi veya bilgilendirme gösterilir.

        if (manuelMerkezGerekli) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = elleMerkez,
                onValueChange = { elleMerkez = it },
                label = { Text("Merkez adı (elle)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            ) // Elle merkez alanı: listede olmayan merkezlerin adının yazılmasını sağlar.
        } // Merkez listede yoksa veya elle giriş seçildiyse gösterilir.

        if (merkezAdi.isNotBlank() && merkezKapasitesi > 0) {
            Spacer(Modifier.height(8.dp))
            val slot = durum.kapasiteDurumu
            when {
                durum.kapasiteKontrolEdiliyor -> Text(
                    text = "Doluluk kontrol ediliyor...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                slot != null -> Text(
                    text = "Bu seans: ${slot.dolu}/${slot.kapasite} dolu • " +
                        if (slot.doluMu) "DOLU" else "${slot.kalan} yer var",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (slot.doluMu) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                durum.kapasiteHatasi != null -> Text(
                    text = "Doluluk kontrolü: ${durum.kapasiteHatasi}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if ((slot?.doluMu == true || durum.slotDolu) && durum.alternatifler.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bu seans dolu. En yakın uygun seanslar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                durum.alternatifler.forEach { oneri ->
                    OutlinedButton(
                        onClick = {
                            Kapasite.tarihIsoDanRakam(oneri.tarih)?.let { tarihRakam = it }
                            Kapasite.saatDanRakam(oneri.saat)?.let { saatRakam = it }
                            formViewModel.hataTemizle()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${Guvenlik.tarihIsoDanTurkce(oneri.tarih)} ${oneri.saat} • " +
                                "${oneri.seans.etiket} • ${oneri.kalan} yer"
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        } else if (merkezAdi.isNotBlank() && durum.merkezler.any { it.ad == merkezAdi }) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Bu merkez için kapasite tanımlı değil; doluluk kontrolü yapılmıyor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } // Kapasite özeti ve dolu seans alternatifleri.

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = adres,
            onValueChange = { adres = it },
            label = { Text("Alınacağı adres") },
            modifier = Modifier.fillMaxWidth()
        ) // Adres alanı: elle girilebilir veya haritadan seçilebilir.

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { haritaAcik = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Haritadan adres seç")
        } // Harita: seçilen nokta Nominatim ile adrese çevrilir.

        if (adresEnlem != 0.0 || adresBoylam != 0.0) {
            Text(
                text = "Seçilen konum: %.5f, %.5f".format(adresEnlem, adresBoylam),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        AyarSatiri(
            etiket = "Gidiş-dönüş gerekli",
            secili = gidisDonus,
            onDegisim = { gidisDonus = it }
        ) // Gidiş-dönüş ihtiyacı.

        AyarSatiri(
            etiket = "Refakatçi gerekli",
            secili = refakat,
            onDegisim = { refakat = it }
        ) // Refakatçi ihtiyacı; araç kapasitesi kontrolünde kullanılacak.

        if (refakat) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = refakatciAd,
                onValueChange = { refakatciAd = it.take(Guvenlik.MAKS_AD_UZUNLUGU) },
                label = { Text("Refakatçi adı soyadı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            ) // Refakatçi adı: görevlinin kiminle muhatap olacağını bilmesini sağlar.
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = refakatciTelefon,
                onValueChange = { yeni -> refakatciTelefon = yeni.filter { it.isDigit() }.take(11) },
                label = { Text("Refakatçi telefonu") },
                placeholder = { Text("05XXXXXXXXX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            ) // Telefon: yalnızca rakam kabul eder; görevli acil durumda ulaşabilsin diye alınır.
            Spacer(Modifier.height(8.dp))
            SecimMenusu(
                etiket = "Yakınlık derecesi",
                secilen = refakatciYakinlik,
                secenekler = YAKINLIK_SECENEKLERI,
                onSecim = { refakatciYakinlik = it }
            ) // Yakınlık: refakatçinin hasta ile ilişkisini kaydeder.
        } // Refakatçi gerekiyorsa iletişim bilgileri zorunlu alan olarak gösterilir.

        AyarSatiri(
            etiket = "Tekerlekli sandalye gerekli",
            secili = tekerlekli,
            onDegisim = { tekerlekli = it }
        ) // Erişilebilirlik ihtiyacı; uygun araç kontrolünde kullanılacak.

        Spacer(Modifier.height(12.dp))

        SecimMenusu(
            etiket = "Ulaşım önceliği (doğrulama gerektirir)",
            secilen = oncelik.oncelikEtiketi,
            secenekler = OncelikKategorisi.values().map { it.oncelikEtiketi },
            onSecim = { secilen ->
                oncelikAdi = OncelikKategorisi.values()
                    .first { it.oncelikEtiketi == secilen }.name
            }
        ) // Öncelik seçimi: tanı değil, ulaşım ihtiyacı kategorisi toplanır.
        Text(
            text = "Öncelik, koordinatör resmi belgeyi kontrol edip doğruladıktan sonra geçerli olur. Belge veya tanı bilgisi uygulamaya yüklenmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ) // Bilgilendirme: önceliğin kendiliğinden verilmediğini açıklar.

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = not,
            onValueChange = { not = it },
            label = { Text("Not (isteğe bağlı)") },
            modifier = Modifier.fillMaxWidth()
        ) // Not alanı: ek ulaşım bilgisi için isteğe bağlı.

        Spacer(Modifier.height(16.dp))

        val gorunenMesaj = durum.hata ?: mesaj
        if (gorunenMesaj.isNotEmpty()) {
            Text(
                text = gorunenMesaj,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        } // Hata veya doğrulama mesajı gösterilir.

        if (durum.yukleniyor) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Button(
                onClick = {
                    val temizAdres = Guvenlik.metniTemizle(adres)
                    val temizNot = Guvenlik.metniTemizle(not)
                    val temizRefakatciAd = Guvenlik.metniTemizle(refakatciAd)
                    val temizRefakatciTelefon = refakatciTelefon.filter { it.isDigit() }
                    mesaj = ""
                    formViewModel.hataTemizle()
                    when {
                        secilenIl == null ->
                            mesaj = "İl seçin."
                        secilenIlce == null ->
                            mesaj = "İlçe seçin."
                        tarihHatasi != null ->
                            mesaj = tarihHatasi
                        saatHatasi != null ->
                            mesaj = saatHatasi
                        isoTarih == null ->
                            mesaj = "Tarihi 8 rakam olarak girin (GGAAYYYY)."
                        gorunenSaat == null ->
                            mesaj = "Saati 4 rakam olarak girin (SSDD)."
                        !seansSuresiGecerli ->
                            mesaj = "Seans süresini ${Guvenlik.MIN_SEANS_DAKIKA}-" +
                                "${Guvenlik.MAKS_SEANS_DAKIKA} dakika arasında girin."
                        !Guvenlik.tarihSaatGelecekteMi(tarihRakam, saatRakam) ->
                            mesaj = "Geçmiş bir tarih/saat için talep oluşturamazsınız."
                        !Guvenlik.metinGecerliMi(merkezAdi, Guvenlik.MAKS_MERKEZ_UZUNLUGU) ->
                            mesaj = "Diyaliz merkezini seçin veya adını girin."
                        !Guvenlik.metinGecerliMi(temizAdres, Guvenlik.MAKS_ADRES_UZUNLUGU) ->
                            mesaj = "Alınacağı adresi girin."
                        refakat && !Guvenlik.metinGecerliMi(
                            temizRefakatciAd,
                            Guvenlik.MAKS_AD_UZUNLUGU
                        ) ->
                            mesaj = "Refakatçi adını ve soyadını girin."
                        refakat && !Guvenlik.telefonGecerliMi(temizRefakatciTelefon) ->
                            mesaj = "Refakatçi telefonunu 05XXXXXXXXX biçiminde girin."
                        refakat && refakatciYakinlik == null ->
                            mesaj = "Refakatçinin yakınlık derecesini seçin."
                        temizNot.length > Guvenlik.MAKS_NOT_UZUNLUGU ->
                            mesaj = "Not en fazla ${Guvenlik.MAKS_NOT_UZUNLUGU} karakter olabilir."
                        else -> formViewModel.kaydet(
                            tarih = isoTarih,
                            saat = gorunenSaat,
                            seansSuresiDakika = seansSuresi ?: Guvenlik.VARSAYILAN_SEANS_DAKIKA,
                            merkez = merkezAdi,
                            merkezKapasitesi = merkezKapasitesi,
                            merkezEnlem = secilenMerkezVerisi?.enlem ?: 0.0,
                            merkezBoylam = secilenMerkezVerisi?.boylam ?: 0.0,
                            adres = temizAdres,
                            adresEnlem = adresEnlem,
                            adresBoylam = adresBoylam,
                            gidisDonus = gidisDonus,
                            refakat = refakat,
                            refakatciAd = temizRefakatciAd,
                            refakatciTelefon = temizRefakatciTelefon,
                            refakatciYakinlik = refakatciYakinlik.orEmpty(),
                            tekerlekli = tekerlekli,
                            oncelik = oncelik,
                            not = temizNot
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Talebi Kaydet")
            } // Kaydet düğmesi: seçimleri ve girişleri doğrular, ardından Firestore'a yazar.
        } // Yükleme sırasında düğme yerine ilerleme göstergesi gösterilir.
    } // Form düzeni.

    if (haritaAcik) {
        val baslangicEnlem = when {
            adresEnlem != 0.0 -> adresEnlem
            (secilenMerkezVerisi?.enlem ?: 0.0) != 0.0 -> secilenMerkezVerisi?.enlem ?: 39.933
            else -> 39.933
        }
        val baslangicBoylam = when {
            adresBoylam != 0.0 -> adresBoylam
            (secilenMerkezVerisi?.boylam ?: 0.0) != 0.0 -> secilenMerkezVerisi?.boylam ?: 32.858
            else -> 32.858
        }
        HaritaSecDiyalogu(
            baslangicEnlem = baslangicEnlem,
            baslangicBoylam = baslangicBoylam,
            il = secilenIl.orEmpty(),
            ilce = secilenIlce.orEmpty(),
            ilIlceyeOdaklan = adresEnlem == 0.0 &&
                adresBoylam == 0.0 &&
                (secilenMerkezVerisi?.enlem ?: 0.0) == 0.0 &&
                secilenIl != null &&
                secilenIlce != null,
            onAdresSec = { secilenAdres, enlem, boylam ->
                adres = secilenAdres
                adresEnlem = enlem
                adresBoylam = boylam
                haritaAcik = false
            },
            onKapat = { haritaAcik = false }
        )
    } // Harita seçimi: adres ve koordinatları forma taşır.
} // TalepFormuEkrani: il/ilçe/merkez seçimli ve otomatik biçimli talep formunu gösterir.

@Composable
private fun SecimMenusu(
    etiket: String,
    secilen: String?,
    secenekler: List<String>,
    onSecim: (String) -> Unit,
    enabled: Boolean = true
) {
    var acik by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = etiket, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { acik = true },
            enabled = enabled && secenekler.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = secilen ?: "Seçiniz")
        } // Seçim düğmesi: mevcut seçimi gösterir ve menüyü açar.
        DropdownMenu(expanded = acik, onDismissRequest = { acik = false }) {
            secenekler.forEach { secenek ->
                DropdownMenuItem(
                    text = { Text(secenek) },
                    onClick = {
                        onSecim(secenek)
                        acik = false
                    }
                ) // Menü öğesi: seçilen değeri üst bileşene iletir ve menüyü kapatır.
            }
        } // DropdownMenu: il/ilçe/merkez seçeneklerini kaydırılabilir liste olarak gösterir.
    } // Seçim alanı.
} // SecimMenusu: il, ilçe ve merkez için tekrar kullanılabilir açılır seçim bileşeni.

@Composable
private fun AyarSatiri(
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
        Text(text = etiket, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = secili, onCheckedChange = onDegisim)
    } // Ayar satırı: metin ile anahtarı yan yana gösterir.
} // AyarSatiri: refakat/tekerlekli sandalye gibi ikili seçenekleri gösterir.

// OSM haritasından adres ve koordinat seçimini sağlar; tek seferlik konum alır.
@Composable
private fun HaritaSecDiyalogu(
    baslangicEnlem: Double,
    baslangicBoylam: Double,
    il: String,
    ilce: String,
    ilIlceyeOdaklan: Boolean,
    onAdresSec: (String, Double, Double) -> Unit,
    onKapat: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { OpenStreetMapRepository() }
    val scope = rememberCoroutineScope()
    var nokta by remember { mutableStateOf(GeoPoint(baslangicEnlem, baslangicBoylam)) }
    var adres by remember { mutableStateOf("") }
    var yukleniyor by remember { mutableStateOf(false) }
    var hata by remember { mutableStateOf<String?>(null) }
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }
    var merkezBilgisi by remember {
        mutableStateOf(if (il.isNotBlank() && ilce.isNotBlank()) "$ilce, $il" else "")
    }

    LaunchedEffect(il, ilce, ilIlceyeOdaklan) {
        if (ilIlceyeOdaklan && il.isNotBlank() && ilce.isNotBlank()) {
            repo.konumAra(il, ilce).fold(
                onSuccess = { (enlem, boylam) ->
                    nokta = GeoPoint(enlem, boylam)
                    merkezBilgisi = "$ilce, $il"
                },
                onFailure = {
                    merkezBilgisi = "$ilce, $il merkezi bulunamadı; haritadan seçin."
                }
            )
        }
    } // LaunchedEffect: seçilen il/ilçe merkezini haritanın başlangıcına taşır.

    fun adresGetir(hedef: GeoPoint) {
        scope.launch {
            yukleniyor = true
            hata = null
            repo.adresTersineCevir(hedef.latitude, hedef.longitude).fold(
                onSuccess = { adres = it },
                onFailure = {
                    adres = ""
                    hata = it.message
                }
            )
            yukleniyor = false
        }
    } // adresGetir: seçilen koordinatı Nominatim ile adrese çevirir.

    fun izinVarMi(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    } // izinVarMi: konum izinlerinden en az biri verilmiş mi?

    fun sonKonum(): Location? {
        if (!izinVarMi()) return null
        return runCatching {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
    } // sonKonum: canlı takip değil, cihazın bilinen son konumunu kullanır.

    fun konumdanSec(konum: Location?) {
        if (konum == null) {
            hata = "Son konum bulunamadı; haritadan bir noktaya dokunun."
            return
        }
        nokta = GeoPoint(konum.latitude, konum.longitude)
        adresGetir(nokta)
    } // konumdanSec: son konumu haritaya ve adres alanına taşır.

    val izinLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sonuc ->
        if (sonuc.values.any { it }) {
            konumdanSec(sonKonum())
        } else {
            hata = "Konum izni verilmedi; haritadan seçebilirsiniz."
        }
    } // izinLauncher: konum izni sonucunu işler.

    fun konumAl() {
        if (!izinVarMi()) {
            izinLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            konumdanSec(sonKonum())
        }
    } // konumAl: izin varsa son konumu seçer, yoksa izin ister.

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Haritadan adres seç") },
        text = {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        factory = { ctx ->
                            OsmConfiguration.getInstance().load(
                                ctx,
                                PreferenceManager.getDefaultSharedPreferences(ctx)
                            )
                            OsmConfiguration.getInstance().userAgentValue = ctx.packageName
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(14.0)
                                controller.setCenter(nokta)
                                overlays.add(
                                    MapEventsOverlay(
                                        object : MapEventsReceiver {
                                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                                nokta = p
                                                adresGetir(p)
                                                return true
                                            }

                                            override fun longPressHelper(p: GeoPoint): Boolean = false
                                        }
                                    )
                                )
                            }
                        },
                        update = { map ->
                            map.controller.setCenter(nokta)
                        }
                    )
                }
                if (merkezBilgisi.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Harita konumu: $merkezBilgisi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        yukleniyor -> "Adres alınıyor..."
                        adres.isNotBlank() -> adres
                        else -> "Haritada bir noktaya dokunun."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (hata != null) {
                    Text(
                        text = hata.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row {
                    TextButton(onClick = { konumAl() }) {
                        Text("Konumumu kullan")
                    }
                    TextButton(onClick = { adresGetir(nokta) }) {
                        Text("Adresi yenile")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (adres.isNotBlank()) {
                        onAdresSec(adres, nokta.latitude, nokta.longitude)
                    }
                },
                enabled = adres.isNotBlank() && !yukleniyor
            ) {
                Text("Bu adresi kullan")
            }
        },
        dismissButton = {
            TextButton(onClick = onKapat) {
                Text("Vazgeç")
            }
        }
    )
} // HaritaSecDiyalogu: OSM haritası, son konum ve ters geocoding'i tek akışta sunar.
