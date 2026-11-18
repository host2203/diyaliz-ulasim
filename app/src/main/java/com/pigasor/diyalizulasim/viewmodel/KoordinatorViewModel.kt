package com.pigasor.diyalizulasim.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pigasor.diyalizulasim.bildirim.YerelBildirim
import com.pigasor.diyalizulasim.data.AuthRepository
import com.pigasor.diyalizulasim.data.BildirimRepository
import com.pigasor.diyalizulasim.data.DemoMerkezler
import com.pigasor.diyalizulasim.data.Kapasite
import com.pigasor.diyalizulasim.data.TransportRepository
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.Bildirim
import com.pigasor.diyalizulasim.model.BildirimHedefi
import com.pigasor.diyalizulasim.model.DelayReport
import com.pigasor.diyalizulasim.model.DiyalizMerkezi
import com.pigasor.diyalizulasim.model.DriverDelayReport
import com.pigasor.diyalizulasim.model.Personnel
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.UserRole
import com.pigasor.diyalizulasim.model.Vehicle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Koordinatör panelinin kullanıcı, talep, araç, personel, bildirim ve işlem durumlarını tutar.
data class KoordinatorDurumu(
    val kullanici: AppUser? = null,
    val talepler: List<TransportRequest> = emptyList(),
    val araclar: List<Vehicle> = emptyList(),
    val personeller: List<Personnel> = emptyList(),
    val tumAraclar: List<Vehicle> = emptyList(),
    val tumPersoneller: List<Personnel> = emptyList(),
    val merkezler: List<DiyalizMerkezi> = emptyList(),
    val gecikmeRaporlari: Map<String, DelayReport> = emptyMap(),
    val gorevliGecikmeRaporlari: Map<String, DriverDelayReport> = emptyMap(),
    val bildirimler: List<Bildirim> = emptyList(),
    val yukleniyor: Boolean = true,
    val hata: String? = null,
    val islemYukleniyor: Boolean = false,
    val islemHatasi: String? = null
) { // KoordinatorDurumu: panelin tüm veri ve işlem durumunu tek nesnede toplar.
    val okunmamisSayisi: Int get() = bildirimler.count { !it.okundu }
}

// Koordinatör ekranının verilerini yükler ve atama/durum işlemlerini yürütür.
class KoordinatorViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository(),
    private val transportRepository: TransportRepository = TransportRepository(),
    private val bildirimRepository: BildirimRepository = BildirimRepository()
) : AndroidViewModel(application) {

    private val _durum = MutableStateFlow(KoordinatorDurumu())
    val durum: StateFlow<KoordinatorDurumu> = _durum
    private var dinleyiciIs: Job? = null
    private var raporIs: Job? = null
    private var gorevliRaporIs: Job? = null
    private var bildirimIs: Job? = null

    init {
        yukle()
    } // init: panel açıldığında koordinatörü, araçları ve talepleri yükler.

    // Koordinatör profilini, araç listesini ve tüm talepleri canlı olarak yükler.
    private fun yukle() {
        viewModelScope.launch {
            try {
                val kullanici = authRepository.mevcutKullanici()
                if (kullanici == null) {
                    _durum.update { it.copy(yukleniyor = false, hata = "Oturum bulunamadı.") }
                    return@launch
                }
                if (kullanici.role != UserRole.COORDINATOR) {
                    _durum.update {
                        it.copy(
                            kullanici = kullanici,
                            yukleniyor = false,
                            hata = "Bu ekran yalnızca koordinatör içindir."
                        )
                    }
                    return@launch
                }
                val tumAraclar = transportRepository.araclariGetir(false)
                    .getOrNull().orEmpty()
                val tumPersoneller = transportRepository.personelGetir(false)
                    .getOrNull().orEmpty()
                val araclar = tumAraclar.filter { it.active }
                val personeller = tumPersoneller.filter { it.active }
                val uzakMerkezler = transportRepository.merkezleriGetir().getOrNull().orEmpty()
                val merkezler = (uzakMerkezler + DemoMerkezler.liste)
                    .distinctBy { "${it.il}|${it.ilce}|${it.ad}" }
                _durum.update {
                    it.copy(
                        kullanici = kullanici,
                        araclar = araclar,
                        personeller = personeller,
                        tumAraclar = tumAraclar,
                        tumPersoneller = tumPersoneller,
                        merkezler = merkezler,
                        yukleniyor = false,
                        hata = null
                    )
                }
                dinleyiciIs?.cancel()
                dinleyiciIs = viewModelScope.launch {
                    transportRepository.tumTalepleriDinle().collect { talepler ->
                        _durum.update { it.copy(talepler = talepler) }
                    }
                }
                raporIs?.cancel()
                raporIs = viewModelScope.launch {
                    transportRepository.gecikmeRaporlariniDinle().collect { raporlar ->
                        _durum.update {
                            it.copy(gecikmeRaporlari = raporlar.associateBy { rapor ->
                                rapor.requestId
                            })
                        }
                    }
                }
                gorevliRaporIs?.cancel()
                gorevliRaporIs = viewModelScope.launch {
                    transportRepository.gorevliGecikmeRaporlariniDinle().collect { raporlar ->
                        _durum.update {
                            it.copy(gorevliGecikmeRaporlari = raporlar.associateBy { rapor ->
                                rapor.requestId
                            })
                        }
                    }
                }
                bildirimleriDinle()
            } catch (e: Exception) {
                _durum.update { it.copy(yukleniyor = false, hata = "Veriler yüklenemedi.") }
            }
        }
    } // yukle: rol kontrolünden sonra gerçek zamanlı talep dinlemesini başlatır.

    // Koordinatörün bildirim kutusunu canlı dinler.
    private fun bildirimleriDinle() {
        bildirimIs?.cancel()
        bildirimIs = viewModelScope.launch {
            bildirimRepository.bildirimleriDinle(
                hedef = BildirimHedefi.COORDINATOR,
                userId = "",
                personnelId = ""
            ).collect { liste ->
                val oncekiIdler = _durum.value.bildirimler.map { it.id }.toSet()
                val yeniler = liste.filter { it.id !in oncekiIdler && !it.okundu }
                _durum.update { it.copy(bildirimler = liste) }
                // Okunmamış bildirimler ilk açılışta da cihaz bildirimi olarak gösterilir.
                yeniler.take(3).forEach { YerelBildirim.goster(getApplication(), it) }
            }
        }
    } // bildirimleriDinle: yeni talep ve hazırlık bildirimlerini canlı gösterir.

    // Seçilen talebe araç/görevli ataması yapar.
    fun atamaYap(
        talep: TransportRequest,
        arac: Vehicle,
        personel: Personnel,
        planlananSaat: String
    ) {
        if (_durum.value.islemYukleniyor) return
        val kullanici = _durum.value.kullanici
        if (kullanici == null) {
            _durum.update { it.copy(islemHatasi = "Oturum bulunamadı.") }
            return
        }
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.atamaYap(
                talep = talep,
                arac = arac,
                personel = personel,
                planlananAlinmaSaati = planlananSaat,
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
    } // atamaYap: atamayı, geçmişi ve tarafların bildirimlerini yazar.

    // Gecikmeli hastanın alınmasına koordinatör onayı verir; uygunsa randevuyu erteler.
    fun gecikmeOnayla(talep: TransportRequest) {
        if (_durum.value.islemYukleniyor) return
        val kullanici = _durum.value.kullanici ?: return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.gecikmeOnayla(
                talep = talep,
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
    } // gecikmeOnayla: onay/erteleme sonucunu ve olası kural hatasını arayüze taşır.

    // Otomatik dönüş planını uygun araç/görevliyle onaylar.
    fun returnPlaniOnayla(talep: TransportRequest, arac: Vehicle, personel: Personnel) {
        if (_durum.value.islemYukleniyor) return
        val kullanici = _durum.value.kullanici ?: return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.returnPlaniOnayla(
                talepId = talep.id,
                arac = arac,
                personel = personel,
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
    } // returnPlaniOnayla: dönüş ataması sonucunu ve olası hatayı arayüze taşır.

    // Öncelik talebini resmi belge kontrolü sonrası doğrular.
    fun oncelikDogrula(talep: TransportRequest, not: String) {
        if (_durum.value.islemYukleniyor) return
        val kullanici = _durum.value.kullanici ?: return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.oncelikDogrula(
                talep = talep,
                not = not,
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
    } // oncelikDogrula: doğrulama sonucunu ve olası hatayı arayüze taşır.

    // Koordinatörün seçtiği merkez için seans başına kapasiteyi kaydeder.
    fun merkezKapasitesiKaydet(merkez: DiyalizMerkezi, kapasite: Int) {
        if (_durum.value.islemYukleniyor) return
        if (_durum.value.kullanici == null) return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            transportRepository.merkezKapasitesiKaydet(merkez, kapasite).fold(
                onSuccess = {
                    val yeniId = merkez.id.ifBlank {
                        Kapasite.merkezBelgesiId(merkez.ad).orEmpty()
                    }
                    val guncel = _durum.value.merkezler.map { mevcut ->
                        if (mevcut.il == merkez.il &&
                            mevcut.ilce == merkez.ilce &&
                            mevcut.ad == merkez.ad
                        ) {
                            mevcut.copy(id = yeniId, kapasitePerSeans = kapasite)
                        } else {
                            mevcut
                        }
                    }
                    _durum.update {
                        it.copy(islemYukleniyor = false, merkezler = guncel)
                    }
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(islemYukleniyor = false, islemHatasi = hata.message)
                    }
                }
            )
        }
    } // merkezKapasitesiKaydet: centers belgesini yazar ve panel listesini günceller.

    // Araç ekleme/güncelleme sonucunu panel listelerine yansıtır.
    fun aracKaydet(arac: Vehicle) {
        if (_durum.value.islemYukleniyor) return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            transportRepository.aracKaydet(arac).fold(
                onSuccess = {
                    yonetimListeleriniYenile()
                    _durum.update { it.copy(islemYukleniyor = false) }
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(islemYukleniyor = false, islemHatasi = hata.message)
                    }
                }
            )
        }
    } // aracKaydet: yeni aracı veya düzenlenen kaydı Firestore'a yazar.

    // Personel ekleme/güncelleme sonucunu panel listelerine yansıtır.
    fun personelKaydet(personel: Personnel) {
        if (_durum.value.islemYukleniyor) return
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            transportRepository.personelKaydet(personel).fold(
                onSuccess = {
                    yonetimListeleriniYenile()
                    _durum.update { it.copy(islemYukleniyor = false) }
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(islemYukleniyor = false, islemHatasi = hata.message)
                    }
                }
            )
        }
    } // personelKaydet: yeni görevliyi veya düzenlenen kaydı Firestore'a yazar.

    // Atama ve yönetim listelerini tüm kayıtlarla birlikte yeniler.
    private suspend fun yonetimListeleriniYenile() {
        val tumAraclar = transportRepository.araclariGetir(false)
            .getOrNull().orEmpty()
        val tumPersoneller = transportRepository.personelGetir(false)
            .getOrNull().orEmpty()
        _durum.update {
            it.copy(
                tumAraclar = tumAraclar,
                tumPersoneller = tumPersoneller,
                araclar = tumAraclar.filter { arac -> arac.active },
                personeller = tumPersoneller.filter { personel -> personel.active }
            )
        }
    } // yonetimListeleriniYenile: pasif kayıtları yönetimde tutar, atamada göstermez.

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
