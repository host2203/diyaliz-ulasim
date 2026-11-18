package com.pigasor.diyalizulasim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pigasor.diyalizulasim.data.AuthRepository
import com.pigasor.diyalizulasim.data.DemoMerkezler
import com.pigasor.diyalizulasim.data.OpenStreetMapRepository
import com.pigasor.diyalizulasim.data.SlotDoluException
import com.pigasor.diyalizulasim.data.TransportRepository
import com.pigasor.diyalizulasim.model.DiyalizMerkezi
import com.pigasor.diyalizulasim.model.OncelikKategorisi
import com.pigasor.diyalizulasim.model.SlotDurumu
import com.pigasor.diyalizulasim.model.SlotOnerisi
import com.pigasor.diyalizulasim.model.TransportRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Talep formunun kaydetme durumunu ve sonucunu tutar.
data class TalepFormuDurumu(
    val yukleniyor: Boolean = false,
    val hata: String? = null,
    val basarili: Boolean = false,
    val merkezler: List<DiyalizMerkezi> = emptyList(),
    val merkezAramaYukleniyor: Boolean = false,
    val merkezAramaHatasi: String? = null,
    val kapasiteKontrolEdiliyor: Boolean = false,
    val kapasiteDurumu: SlotDurumu? = null,
    val kapasiteHatasi: String? = null,
    val alternatifler: List<SlotOnerisi> = emptyList(),
    val slotDolu: Boolean = false
) // TalepFormuDurumu: form, merkez arama ve seans doluluk durumlarını yönetir.

// Talep formundan gelen bilgileri Firestore'a kaydeder.
class TalepFormuViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val transportRepository: TransportRepository = TransportRepository(),
    private val openStreetMapRepository: OpenStreetMapRepository = OpenStreetMapRepository()
) : ViewModel() {

    private val _durum = MutableStateFlow(TalepFormuDurumu())
    val durum: StateFlow<TalepFormuDurumu> = _durum
    private var kapasiteIs: Job? = null

    init {
        merkezleriYukle()
    } // init: form açıldığında il/ilçe seçiminde kullanılacak merkezleri yükler.

    // Firestore'daki merkezleri demo listeyle birleştirip forma sunar.
    private fun merkezleriYukle() {
        viewModelScope.launch {
            val uzakMerkezler = transportRepository.merkezleriGetir().getOrNull().orEmpty()
            val birlesik = (uzakMerkezler + DemoMerkezler.liste)
                .distinctBy { "${it.il}|${it.ilce}|${it.ad}" }
            _durum.update { it.copy(merkezler = birlesik) }
        }
    } // merkezleriYukle: Firestore boşsa demo merkezlerin görünmesini sağlar.

    // Seçilen il/ilçe için OpenStreetMap üzerinden gerçek merkezleri arar.
    fun merkezleriAra(il: String, ilce: String) {
        _durum.update { it.copy(merkezAramaYukleniyor = true, merkezAramaHatasi = null) }
        viewModelScope.launch {
            val sonuc = openStreetMapRepository.merkezAra(il, ilce)
            sonuc.fold(
                onSuccess = { bulunanlar ->
                    _durum.update { durum ->
                        val ayniIlce = durum.merkezler.filter { it.il == il && it.ilce == ilce }
                        val digerleri = durum.merkezler.filterNot { it.il == il && it.ilce == ilce }
                        val birlesik = (digerleri + ayniIlce + bulunanlar)
                            .distinctBy { "${it.il}|${it.ilce}|${it.ad}" }
                        durum.copy(
                            merkezler = birlesik,
                            merkezAramaYukleniyor = false,
                            merkezAramaHatasi = null
                        )
                    }
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(merkezAramaYukleniyor = false, merkezAramaHatasi = hata.message)
                    }
                }
            )
        }
    } // merkezleriAra: OSM sonuçlarını mevcut Firestore/demo listesiyle birleştirir.

    // Seçili merkez/tarih/saat için doluluk kontrolünü gecikmeli olarak başlatır.
    fun kapasiteKontrolEt(merkezAdi: String, kapasite: Int, tarihIso: String, saat: String) {
        kapasiteIs?.cancel()
        if (merkezAdi.isBlank() || kapasite <= 0 || tarihIso.isBlank() || saat.isBlank()) {
            _durum.update {
                it.copy(
                    kapasiteKontrolEdiliyor = false,
                    kapasiteDurumu = null,
                    kapasiteHatasi = null,
                    alternatifler = emptyList(),
                    slotDolu = false
                )
            }
            return
        }
        kapasiteIs = viewModelScope.launch {
            delay(500)
            _durum.update {
                it.copy(
                    kapasiteKontrolEdiliyor = true,
                    kapasiteHatasi = null,
                    slotDolu = false
                )
            }
            transportRepository.slotDurumuGetir(merkezAdi, tarihIso, saat, kapasite).fold(
                onSuccess = { slot ->
                    _durum.update {
                        it.copy(kapasiteKontrolEdiliyor = false, kapasiteDurumu = slot)
                    }
                    if (slot?.doluMu == true) {
                        alternatifleriYukle(merkezAdi, tarihIso, saat, kapasite)
                    }
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(
                            kapasiteKontrolEdiliyor = false,
                            kapasiteDurumu = null,
                            kapasiteHatasi = hata.message
                        )
                    }
                }
            )
        }
    } // kapasiteKontrolEt: her tuş vuruşunda istek atmadan doluluk özetini günceller.

    // Dolu seans için en yakın uygun alternatifleri getirir.
    fun alternatifleriYukle(merkezAdi: String, tarihIso: String, saat: String, kapasite: Int) {
        viewModelScope.launch {
            transportRepository.alternatifSlotlarGetir(merkezAdi, tarihIso, saat, kapasite)
                .onSuccess { liste ->
                    _durum.update { it.copy(alternatifler = liste) }
                }
        }
    } // alternatifleriYukle: dolu seans yerine gösterilecek en fazla üç dilimi hazırlar.

    // Dolu seans diyaloğu kapatıldığında uyarı durumunu temizler.
    fun slotDoluTuketildi() {
        _durum.update { it.copy(slotDolu = false) }
    } // slotDoluTuketildi: aynı uyarının tekrar tekrar açılmasını önler.

    // Form verisini alır, oturum sahibini bulur ve talebi Firestore'a yazar.
    fun kaydet(
        tarih: String,
        saat: String,
        seansSuresiDakika: Int,
        merkez: String,
        merkezKapasitesi: Int,
        merkezEnlem: Double,
        merkezBoylam: Double,
        adres: String,
        adresEnlem: Double,
        adresBoylam: Double,
        gidisDonus: Boolean,
        refakat: Boolean,
        refakatciAd: String,
        refakatciTelefon: String,
        refakatciYakinlik: String,
        tekerlekli: Boolean,
        oncelik: OncelikKategorisi,
        not: String
    ) {
        _durum.update {
            it.copy(yukleniyor = true, hata = null, basarili = false, slotDolu = false)
        }
        viewModelScope.launch {
            try {
                val kullanici = authRepository.mevcutKullanici()
                if (kullanici == null) {
                    _durum.update { it.copy(yukleniyor = false, hata = "Oturum bulunamadı.") }
                    return@launch
                }
                val talep = TransportRequest(
                    patientId = kullanici.uid,
                    patientName = kullanici.name,
                    sessionDate = tarih,
                    sessionTime = saat,
                    sessionDurationMinutes = seansSuresiDakika,
                    centerName = merkez,
                    centerLat = merkezEnlem,
                    centerLng = merkezBoylam,
                    pickupAddress = adres,
                    pickupLat = adresEnlem,
                    pickupLng = adresBoylam,
                    roundTrip = gidisDonus,
                    companionRequired = refakat,
                    companionName = if (refakat) refakatciAd else "",
                    companionPhone = if (refakat) refakatciTelefon else "",
                    companionRelation = if (refakat) refakatciYakinlik else "",
                    wheelchairRequired = tekerlekli,
                    priorityCategory = oncelik,
                    note = not
                )
                val sonuc = transportRepository.talepOlustur(talep, merkezKapasitesi)
                sonuc.fold(
                    onSuccess = {
                        _durum.update { it.copy(yukleniyor = false, basarili = true) }
                    },
                    onFailure = { hata ->
                        if (hata is SlotDoluException) {
                            _durum.update {
                                it.copy(
                                    yukleniyor = false,
                                    hata = "Seçilen seans dolu. Alternatiflerden birini seçin.",
                                    slotDolu = true
                                )
                            }
                            alternatifleriYukle(merkez, tarih, saat, merkezKapasitesi)
                        } else {
                            _durum.update {
                                it.copy(yukleniyor = false, hata = hata.message)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                _durum.update { it.copy(yukleniyor = false, hata = "Talep kaydedilemedi.") }
            }
        }
    } // kaydet: doğrulanmış form verisini hastanın UID'siyle birlikte kaydeder.

    // Yeni denemeden önce eski hata mesajını temizler.
    fun hataTemizle() {
        _durum.update { it.copy(hata = null) }
    } // hataTemizle: formda eski hata mesajının kalmasını engeller.

    // Yönlendirme yapıldıktan sonra başarı durumunu sıfırlar.
    fun basariTuketildi() {
        _durum.update { it.copy(basarili = false) }
    } // basariTuketildi: aynı başarı durumunun tekrar yönlendirme tetiklemesini önler.
}
