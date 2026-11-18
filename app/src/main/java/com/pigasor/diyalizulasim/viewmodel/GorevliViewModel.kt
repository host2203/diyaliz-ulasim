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
import com.pigasor.diyalizulasim.model.EtaHedefi
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus
import com.pigasor.diyalizulasim.model.UserRole
import com.pigasor.diyalizulasim.model.sonrakiDurum
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Görevli ekranının kullanıcı, atanmış talepler, bildirimler ve işlem durumlarını tutar.
data class GorevliDurumu(
    val kullanici: AppUser? = null,
    val talepler: List<TransportRequest> = emptyList(),
    val bildirimler: List<Bildirim> = emptyList(),
    val yukleniyor: Boolean = true,
    val hata: String? = null,
    val islemYukleniyor: Boolean = false,
    val islemHatasi: String? = null
) { // GorevliDurumu: görevli panelinin tüm veri ve işlem durumunu tek nesnede toplar.
    val okunmamisSayisi: Int get() = bildirimler.count { !it.okundu }
}

// Görevlinin kendisine atanan işleri yükler ve durum güncellemelerini yürütür.
class GorevliViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val transportRepository: TransportRepository = TransportRepository(),
    private val bildirimRepository: BildirimRepository = BildirimRepository()
) : AndroidViewModel(application) {

    private val _durum = MutableStateFlow(GorevliDurumu())
    val durum: StateFlow<GorevliDurumu> = _durum
    private var dinleyiciIs: Job? = null
    private var bildirimIs: Job? = null

    init {
        yukle()
    } // init: görevli paneli açıldığında kullanıcıyı ve atanmış işleri yükler.

    // Görevli profilini doğrular; atanmış talepleri ve kendi bildirimlerini canlı dinler.
    private fun yukle() {
        viewModelScope.launch {
            try {
                val kullanici = authRepository.mevcutKullanici()
                if (kullanici == null) {
                    _durum.update { it.copy(yukleniyor = false, hata = "Oturum bulunamadı.") }
                    return@launch
                }
                if (kullanici.role != UserRole.DRIVER || kullanici.personnelId.isBlank()) {
                    _durum.update {
                        it.copy(
                            kullanici = kullanici,
                            yukleniyor = false,
                            hata = "Bu ekran yalnızca görevli hesabı içindir."
                        )
                    }
                    return@launch
                }
                _durum.update { it.copy(kullanici = kullanici, yukleniyor = false, hata = null) }
                dinleyiciIs?.cancel()
                dinleyiciIs = viewModelScope.launch {
                    transportRepository.gorevliTalepleriDinle(kullanici.personnelId)
                        .collect { talepler ->
                            _durum.update { it.copy(talepler = talepler) }
                        }
                }
                bildirimleriDinle(kullanici)
            } catch (e: Exception) {
                _durum.update { it.copy(yukleniyor = false, hata = "Veriler yüklenemedi.") }
            }
        }
    } // yukle: görevlinin personel kimliğine göre doğru talepleri dinlemesini sağlar.

    // Görevlinin bildirim kutusunu canlı dinler; yeni atama/iptal/onayları gösterir.
    private fun bildirimleriDinle(kullanici: AppUser) {
        bildirimIs?.cancel()
        bildirimIs = viewModelScope.launch {
            bildirimRepository.bildirimleriDinle(
                hedef = BildirimHedefi.DRIVER,
                userId = "",
                personnelId = kullanici.personnelId
            ).collect { liste ->
                val oncekiIdler = _durum.value.bildirimler.map { it.id }.toSet()
                val yeniler = liste.filter { it.id !in oncekiIdler && !it.okundu }
                _durum.update { it.copy(bildirimler = liste) }
                // Okunmamış bildirimler ilk açılışta da cihaz bildirimi olarak gösterilir.
                yeniler.take(3).forEach { YerelBildirim.goster(getApplication(), it) }
            }
        }
    } // bildirimleriDinle: görevlinin yalnızca kendi personel kimliğine gelen bildirimleri görür.

    // Talebi durum zincirinde bir sonraki adıma geçirir.
    fun durumIlerlet(talep: TransportRequest) {
        if (_durum.value.islemYukleniyor) return
        if (talep.status == TransportStatus.REQUESTED) return
        val yeniDurum = talep.status.sonrakiDurum() ?: return
        val kullanici = _durum.value.kullanici ?: return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.durumGuncelleGuvenli(
                talepId = talep.id,
                beklenenDurum = talep.status,
                yeniDurum = yeniDurum,
                guncelleyen = kullanici
            )
            sonuc.fold(
                onSuccess = {
                    _durum.update { it.copy(islemYukleniyor = false) }
                    val hedef = when (yeniDurum) {
                        TransportStatus.ON_THE_WAY -> EtaHedefi.HOME
                        TransportStatus.PICKED_UP -> EtaHedefi.CENTER
                        else -> EtaHedefi.NONE
                    }
                    if (hedef != EtaHedefi.NONE) {
                        etaHesapla(talep, hedef)
                    }
                },
                onFailure = { hata ->
                    _durum.update { it.copy(islemYukleniyor = false, islemHatasi = hata.message) }
                }
            )
        }
    } // durumIlerlet: transaction ile eşzamanlılık, gecikme ve personel ataması kontrolü yapar.

    // Görevlinin/aracın gecikeceğini süre ve gizli gerekçeyle bildirir.
    fun gorevliGecikmeBildir(talep: TransportRequest, dakika: Int, sebep: String) {
        if (_durum.value.islemYukleniyor) return
        val kullanici = _durum.value.kullanici ?: return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.gorevliGecikmeBildir(
                talepId = talep.id,
                dakika = dakika,
                sebep = sebep,
                guncelleyen = kullanici
            )
            sonuc.fold(
                onSuccess = {
                    _durum.update { it.copy(islemYukleniyor = false) }
                    // Gecikme bilgisi anında gider; ETA gerçek rota + gecikme olarak tazelenir.
                    etaHesapla(talep, etaHedefi(talep))
                },
                onFailure = { hata ->
                    _durum.update { it.copy(islemYukleniyor = false, islemHatasi = hata.message) }
                }
            )
        }
    } // gorevliGecikmeBildir: onay/bilgi sonucunu ve ETA tazelemesini yürütür.

    // Merkeze ulaşıldıktan sonra dönüş yolculuğunu başlatır ve dönüş ETA'sını hesaplar.
    fun donuseBasla(talep: TransportRequest) {
        if (_durum.value.islemYukleniyor) return
        val kullanici = _durum.value.kullanici ?: return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.donuseBasla(
                talepId = talep.id,
                guncelleyen = kullanici
            )
            sonuc.fold(
                onSuccess = {
                    _durum.update { it.copy(islemYukleniyor = false) }
                    etaHesapla(talep, EtaHedefi.HOME)
                },
                onFailure = { hata ->
                    _durum.update { it.copy(islemYukleniyor = false, islemHatasi = hata.message) }
                }
            )
        }
    } // donuseBasla: dönüş başlangıcını ve ETA'yı başlatır.

    // Talebin durumuna göre ETA hedefini seçer.
    private fun etaHedefi(talep: TransportRequest): EtaHedefi {
        return when {
            talep.status == TransportStatus.PLANNED ||
                talep.status == TransportStatus.ON_THE_WAY -> EtaHedefi.HOME
            talep.status == TransportStatus.PICKED_UP -> EtaHedefi.CENTER
            talep.status == TransportStatus.AT_CENTER && talep.returnStartedAt > 0L ->
                EtaHedefi.HOME
            else -> EtaHedefi.NONE
        }
    } // etaHedefi: hangi ayak için OSRM çağrılacağını belirler.

    // OSRM'den gerçek rota süresini alır; hata durumunda durumu bozmadan bilgi verir.
    private fun etaHesapla(talep: TransportRequest, hedef: EtaHedefi) {
        if (hedef == EtaHedefi.NONE) return
        val (baslangicEnlem, baslangicBoylam, hedefEnlem, hedefBoylam) = when (hedef) {
            EtaHedefi.HOME -> listOf(
                talep.centerLat, talep.centerLng, talep.pickupLat, talep.pickupLng
            )
            EtaHedefi.CENTER -> listOf(
                talep.pickupLat, talep.pickupLng, talep.centerLat, talep.centerLng
            )
            EtaHedefi.NONE -> return
        }
        viewModelScope.launch {
            val sonuc = transportRepository.etaGuncelle(
                talep = talep,
                hedef = hedef,
                baslangicEnlem = baslangicEnlem,
                baslangicBoylam = baslangicBoylam,
                hedefEnlem = hedefEnlem,
                hedefBoylam = hedefBoylam
            )
            if (sonuc.isFailure) {
                _durum.update {
                    it.copy(
                        islemHatasi = sonuc.exceptionOrNull()?.message
                            ?: "Tahmini varış alınamadı."
                    )
                }
            }
        }
    } // etaHesapla: ETA'yı arka planda hesaplar; durum güncellemesini geri almaz.

    // Okunmamış bildirimleri okundu olarak işaretler.
    fun bildirimleriOkunduIsaretle() {
        val okunmamis = _durum.value.bildirimler.filter { !it.okundu }.map { it.id }
        if (okunmamis.isEmpty()) return
        viewModelScope.launch {
            bildirimRepository.okunduIsaretle(okunmamis)
        }
    } // bildirimleriOkunduIsaretle: bildirim merkezi açıldığında çağrılır.

    // Ekranda kalan işlem hatasını temizler.
    fun islemHatasiniTemizle() {
        _durum.update { it.copy(islemHatasi = null) }
    } // islemHatasiniTemizle: yeni işlem öncesi eski hata mesajını kaldırır.
}
