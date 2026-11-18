package com.pigasor.diyalizulasim.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pigasor.diyalizulasim.bildirim.YerelBildirim
import com.pigasor.diyalizulasim.data.AuthRepository
import com.pigasor.diyalizulasim.data.BildirimRepository
import com.pigasor.diyalizulasim.data.TransportRepository
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.Bildirim
import com.pigasor.diyalizulasim.model.BildirimHedefi
import com.pigasor.diyalizulasim.model.ReadinessStatus
import com.pigasor.diyalizulasim.model.TransportRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Hasta panelinin kullanıcı, talep listesi, bildirimler ve hata durumlarını tutar.
data class HastaDurumu(
    val kullanici: AppUser? = null,
    val talepler: List<TransportRequest> = emptyList(),
    val bildirimler: List<Bildirim> = emptyList(),
    val yukleniyor: Boolean = true,
    val hata: String? = null,
    val islemYukleniyor: Boolean = false,
    val islemHatasi: String? = null,
    val davetKodu: String? = null,
    val davetYukleniyor: Boolean = false,
    val davetHatasi: String? = null
) { // HastaDurumu: arayüzün hangi veriyi göstereceğini tek nesnede belirler.
    val okunmamisSayisi: Int get() = bildirimler.count { !it.okundu }
}

// Hasta ekranının verilerini yükler ve Firestore değişikliklerini canlı izler.
class HastaViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val transportRepository: TransportRepository = TransportRepository(),
    private val bildirimRepository: BildirimRepository = BildirimRepository()
) : AndroidViewModel(application) {

    private val _durum = MutableStateFlow(HastaDurumu())
    val durum: StateFlow<HastaDurumu> = _durum
    private var dinleyiciIs: Job? = null
    private var bildirimIs: Job? = null

    init {
        yukle()
    } // init: ekran ilk açıldığında kullanıcı ve talep yüklemesini başlatır.

    private fun yukle() {
        viewModelScope.launch {
            try {
                val kullanici = authRepository.mevcutKullanici()
                if (kullanici == null) {
                    _durum.update { it.copy(yukleniyor = false, hata = "Oturum bulunamadı.") }
                    return@launch
                }
                _durum.update { it.copy(kullanici = kullanici, yukleniyor = false, hata = null) }
                dinleyiciIs?.cancel()
                dinleyiciIs = viewModelScope.launch {
                    transportRepository.hastaTalepleriDinle(kullanici.uid).collect { talepler ->
                        _durum.update { it.copy(talepler = talepler) }
                    }
                }
                bildirimleriDinle(kullanici)
            } catch (e: Exception) {
                _durum.update { it.copy(yukleniyor = false, hata = "Veriler yüklenemedi.") }
            }
        }
    } // yukle: kullanıcı profilini okur ve talepleri gerçek zamanlı dinlemeye başlar.

    // Hastanın bildirim kutusunu dinler; yeni bildirimlerde cihaz bildirimi de gösterir.
    private fun bildirimleriDinle(kullanici: AppUser) {
        bildirimIs?.cancel()
        bildirimIs = viewModelScope.launch {
            bildirimRepository.bildirimleriDinle(
                hedef = BildirimHedefi.PATIENT,
                userId = kullanici.uid,
                personnelId = ""
            ).collect { liste ->
                val oncekiIdler = _durum.value.bildirimler.map { it.id }.toSet()
                val yeniler = liste.filter { it.id !in oncekiIdler && !it.okundu }
                _durum.update { it.copy(bildirimler = liste) }
                // Okunmamış bildirimler ilk açılışta da cihaz bildirimi olarak gösterilir.
                yeniler.take(3).forEach { YerelBildirim.goster(getApplication(), it) }
            }
        }
    } // bildirimleriDinle: ilk açılışta eski bildirimleri cihaz bildirimi olarak göstermez.

    // Hastanın "Hazırım / Gecikeceğim" bildirimini Firestore'a yazar.
    fun hazirlikGuncelle(
        talep: TransportRequest,
        hazirlik: ReadinessStatus,
        gecikmeDakika: Int = 0,
        gecikmeSebebi: String = "",
        gorevliMesaji: String = ""
    ) {
        val kullanici = _durum.value.kullanici
        if (kullanici == null) {
            _durum.update { it.copy(islemHatasi = "Oturum bulunamadı.") }
            return
        }
        if (_durum.value.islemYukleniyor) return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.hazirlikGuncelle(
                talep = talep,
                hazirlik = hazirlik,
                gecikmeDakika = gecikmeDakika,
                gecikmeSebebi = gecikmeSebebi,
                gorevliMesaji = gorevliMesaji,
                guncelleyen = kullanici
            )
            sonuc.fold(
                onSuccess = {
                    _durum.update { it.copy(islemYukleniyor = false) }
                },
                onFailure = { hata ->
                    _durum.update { it.copy(islemYukleniyor = false, islemHatasi = hata.message) }
                }
            )
        }
    } // hazirlikGuncelle: hasta bildirimini geçmiş ve bildirimlerle birlikte kaydeder.

    // Hastanın kendi talebini gerekçesiyle iptal etmesini sağlar.
    fun talebiIptalEt(talep: TransportRequest, sebep: String) {
        val kullanici = _durum.value.kullanici
        if (kullanici == null) {
            _durum.update { it.copy(islemHatasi = "Oturum bulunamadı.") }
            return
        }
        if (_durum.value.islemYukleniyor) return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.talepIptalEt(
                talep = talep,
                sebep = sebep,
                guncelleyen = kullanici
            )
            sonuc.fold(
                onSuccess = {
                    _durum.update { it.copy(islemYukleniyor = false) }
                },
                onFailure = { hata ->
                    _durum.update { it.copy(islemYukleniyor = false, islemHatasi = hata.message) }
                }
            )
        }
    } // talebiIptalEt: iptal sonucunu ve olası kural hatasını arayüze taşır.

    // Hasta yakını için tek kullanımlık davet kodu üretir.
    fun davetOlustur() {
        val kullanici = _durum.value.kullanici
        if (kullanici == null) {
            _durum.update { it.copy(davetHatasi = "Oturum bulunamadı.") }
            return
        }
        if (_durum.value.davetYukleniyor) return
        _durum.update { it.copy(davetYukleniyor = true, davetHatasi = null) }
        viewModelScope.launch {
            transportRepository.davetOlustur(kullanici).fold(
                onSuccess = { davet ->
                    _durum.update {
                        it.copy(davetYukleniyor = false, davetKodu = davet.code)
                    }
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(davetYukleniyor = false, davetHatasi = hata.message)
                    }
                }
            )
        }
    } // davetOlustur: yakının bağlanabileceği kodu Firestore'a yazar.

    // Davet diyaloğu kapanırken eski kodu temizler.
    fun davetTemizle() {
        _durum.update { it.copy(davetKodu = null, davetHatasi = null) }
    } // davetTemizle: aynı kodun yeni diyalogda yanlışlıkla gösterilmesini önler.

    // Okunmamış bildirimleri okundu olarak işaretler.
    fun bildirimleriOkunduIsaretle() {
        val okunmamis = _durum.value.bildirimler.filter { !it.okundu }.map { it.id }
        if (okunmamis.isEmpty()) return
        viewModelScope.launch {
            bildirimRepository.okunduIsaretle(okunmamis)
        }
    } // bildirimleriOkunduIsaretle: kullanıcı bildirim merkezini açtığında çağrılır.
}
