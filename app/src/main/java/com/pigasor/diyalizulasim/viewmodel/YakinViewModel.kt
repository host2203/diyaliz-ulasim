package com.pigasor.diyalizulasim.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pigasor.diyalizulasim.data.AuthRepository
import com.pigasor.diyalizulasim.data.DavetKodu
import com.pigasor.diyalizulasim.data.TransportRepository
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.RelativeLink
import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.UserRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Hasta yakını ekranının bağlantı, talep ve işlem durumunu tutar.
data class YakinDurumu(
    val kullanici: AppUser? = null,
    val baglanti: RelativeLink? = null,
    val talepler: List<TransportRequest> = emptyList(),
    val yukleniyor: Boolean = true,
    val hata: String? = null,
    val islemYukleniyor: Boolean = false,
    val islemHatasi: String? = null
) // YakinDurumu: bağlı hasta, talepler ve davet formunun durumunu tek yerde toplar.

// Hasta yakınının davet kabul etmesini ve bağlı hastanın taleplerini izlemesini yönetir.
class YakinViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val transportRepository: TransportRepository = TransportRepository()
) : ViewModel() {

    private val _durum = MutableStateFlow(YakinDurumu())
    val durum: StateFlow<YakinDurumu> = _durum
    private var talepIs: Job? = null

    init {
        yukle()
    } // init: yakın rolünü ve varsa mevcut bağlantıyı yükler.

    private fun yukle() {
        viewModelScope.launch {
            try {
                val kullanici = authRepository.mevcutKullanici()
                if (kullanici == null) {
                    _durum.update { it.copy(yukleniyor = false, hata = "Oturum bulunamadı.") }
                    return@launch
                }
                if (kullanici.role != UserRole.RELATIVE) {
                    _durum.update {
                        it.copy(
                            kullanici = kullanici,
                            yukleniyor = false,
                            hata = "Bu ekran yalnızca hasta yakını içindir."
                        )
                    }
                    return@launch
                }
                val baglanti = transportRepository.mevcutBaglantiyiGetir(kullanici.uid)
                    .getOrNull()
                val hastaId = kullanici.linkedPatientId.ifBlank {
                    baglanti?.patientId.orEmpty()
                }
                if (hastaId.isNotBlank() && kullanici.linkedPatientId != hastaId) {
                    // Link belgesi varsa yarım kalan profil yazımını onar.
                    transportRepository.baglantiyiProfileYaz(kullanici.uid, hastaId)
                }
                val guncel = kullanici.copy(linkedPatientId = hastaId)
                _durum.update {
                    it.copy(
                        kullanici = guncel,
                        baglanti = baglanti ?: if (hastaId.isNotBlank()) {
                            RelativeLink(
                                relativeUid = kullanici.uid,
                                patientId = hastaId
                            )
                        } else {
                            null
                        },
                        yukleniyor = false,
                        hata = null
                    )
                }
                if (hastaId.isNotBlank()) {
                    talepleriDinle(hastaId)
                }
            } catch (e: Exception) {
                _durum.update {
                    it.copy(yukleniyor = false, hata = "Bağlantı bilgisi yüklenemedi.")
                }
            }
        }
    } // yukle: bağlantıyı doğrular, yarım kalan yazımı onarır ve talepleri dinler.

    // Davet kodunu kabul eder ve bağlı hastanın taleplerini dinlemeye geçer.
    fun davetKabulEt(kod: String) {
        val kullanici = _durum.value.kullanici ?: return
        if (_durum.value.islemYukleniyor) return
        if (!DavetKodu.gecerliMi(kod)) {
            _durum.update { it.copy(islemHatasi = "Davet kodu 12 karakter olmalı.") }
            return
        }
        _durum.update { it.copy(islemYukleniyor = true, islemHatasi = null) }
        viewModelScope.launch {
            val sonuc = transportRepository.davetiKabulEt(kod, kullanici)
            if (sonuc.isFailure) {
                // Link oluştuysa profil yazımını kurtarmayı dene.
                val link = transportRepository.mevcutBaglantiyiGetir(kullanici.uid).getOrNull()
                if (link != null && link.patientId.isNotBlank()) {
                    val yazSonucu = transportRepository.baglantiyiProfileYaz(
                        kullanici.uid,
                        link.patientId
                    )
                    if (yazSonucu.isFailure) {
                        _durum.update {
                            it.copy(
                                islemYukleniyor = false,
                                islemHatasi = "Bağlantı oluştu ancak profil güncellenemedi. " +
                                    "Lütfen tekrar deneyin."
                            )
                        }
                        return@launch
                    }
                    _durum.update {
                        it.copy(
                            kullanici = kullanici.copy(linkedPatientId = link.patientId),
                            baglanti = link,
                            islemYukleniyor = false,
                            islemHatasi = null
                        )
                    }
                    talepleriDinle(link.patientId)
                    return@launch
                }
            }
            sonuc.fold(
                onSuccess = {
                    val link = transportRepository.mevcutBaglantiyiGetir(kullanici.uid)
                        .getOrNull()
                    if (link == null || link.patientId.isBlank()) {
                        _durum.update {
                            it.copy(
                                islemYukleniyor = false,
                                islemHatasi = "Bağlantı kaydı okunamadı. Lütfen tekrar deneyin."
                            )
                        }
                        return@launch
                    }
                    val hastaId = link.patientId
                    _durum.update {
                        it.copy(
                            kullanici = kullanici.copy(linkedPatientId = hastaId),
                            baglanti = link,
                            islemYukleniyor = false,
                            islemHatasi = null
                        )
                    }
                    talepleriDinle(hastaId)
                },
                onFailure = { hata ->
                    _durum.update {
                        it.copy(islemYukleniyor = false, islemHatasi = hata.message)
                    }
                }
            )
        }
    } // davetKabulEt: daveti tüketir, bağlantıyı yükler ve canlı taleplere geçer.

    // Bağlı hastanın taleplerini gerçek zamanlı izler; kural gecikmesinde yeniden dener.
    private fun talepleriDinle(patientId: String) {
        talepIs?.cancel()
        talepIs = viewModelScope.launch {
            var sonHata: Exception? = null
            repeat(3) { deneme ->
                try {
                    transportRepository.hastaTalepleriDinle(patientId).collect { liste ->
                        _durum.update {
                            it.copy(talepler = liste, islemHatasi = null)
                        }
                    }
                    return@launch
                } catch (e: Exception) {
                    sonHata = e
                    Log.e("YakinViewModel", "Talep dinleme hatası (deneme ${deneme + 1})", e)
                    delay(1000)
                }
            }
            _durum.update {
                it.copy(
                    islemHatasi = "Bağlı hastanın talepleri yüklenemedi. " +
                        (sonHata?.message ?: "")
                )
            }
        }
    } // talepleriDinle: yalnızca bağlı hastanın taleplerini gösterir; sessiz boş listeyi engeller.

    // İşlem hatasını temizler.
    fun islemHatasiniTemizle() {
        _durum.update { it.copy(islemHatasi = null) }
    } // islemHatasiniTemizle: yeni deneme öncesi eski hatayı kaldırır.
}
