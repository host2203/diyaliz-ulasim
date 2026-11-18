package com.pigasor.diyalizulasim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.PhoneAuthCredential
import com.pigasor.diyalizulasim.data.AuthRepository
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Giriş ekranının yükleniyor, hata ve başarı durumlarını tek nesnede tutar.
data class AuthDurumu(
    val yukleniyor: Boolean = false,
    val hata: String? = null,
    val girisYapan: AppUser? = null
) // AuthDurumu: arayüzün hangi durumu göstereceğini belirleyen değişmez veri sınıfı.

// Arayüzden gelen giriş/kayıt isteklerini AuthRepository'ye iletir.
class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _durum = MutableStateFlow(AuthDurumu())
    val durum: StateFlow<AuthDurumu> = _durum

    // E-posta ve şifre ile giriş isteğini arka planda çalıştırır.
    fun girisYap(email: String, sifre: String) {
        _durum.update { it.copy(yukleniyor = true, hata = null, girisYapan = null) }
        viewModelScope.launch {
            val sonuc = repository.girisYap(email, sifre)
            _durum.update {
                sonuc.fold(
                    onSuccess = { kullanici -> it.copy(yukleniyor = false, girisYapan = kullanici) },
                    onFailure = { hata -> it.copy(yukleniyor = false, hata = hata.message) }
                )
            }
        }
    } // girisYap: sonucu StateFlow'a yazarak giriş ekranını günceller.

    // Google ID token'ıyla giriş isteğini arka planda çalıştırır.
    fun girisYapGoogle(idToken: String) {
        _durum.update { it.copy(yukleniyor = true, hata = null, girisYapan = null) }
        viewModelScope.launch {
            val sonuc = repository.girisYapGoogle(idToken)
            _durum.update {
                sonuc.fold(
                    onSuccess = { kullanici -> it.copy(yukleniyor = false, girisYapan = kullanici) },
                    onFailure = { hata -> it.copy(yukleniyor = false, hata = hata.message) }
                )
            }
        }
    } // girisYapGoogle: sosyal giriş sonucunu giriş ekranına taşır.

    // Telefon doğrulama kimliğiyle giriş isteğini arka planda çalıştırır.
    fun girisYapTelefon(kimlik: PhoneAuthCredential) {
        _durum.update { it.copy(yukleniyor = true, hata = null, girisYapan = null) }
        viewModelScope.launch {
            val sonuc = repository.girisYapTelefon(kimlik)
            _durum.update {
                sonuc.fold(
                    onSuccess = { kullanici -> it.copy(yukleniyor = false, girisYapan = kullanici) },
                    onFailure = { hata -> it.copy(yukleniyor = false, hata = hata.message) }
                )
            }
        }
    } // girisYapTelefon: SMS doğrulama sonucunu giriş ekranına taşır.

    // Yeni hesap oluşturma isteğini arka planda çalıştırır.
    fun kayitOl(ad: String, email: String, sifre: String, rol: UserRole) {
        _durum.update { it.copy(yukleniyor = true, hata = null, girisYapan = null) }
        viewModelScope.launch {
            val sonuc = repository.kayitOl(ad, email, sifre, rol)
            _durum.update {
                sonuc.fold(
                    onSuccess = { kullanici -> it.copy(yukleniyor = false, girisYapan = kullanici) },
                    onFailure = { hata -> it.copy(yukleniyor = false, hata = hata.message) }
                )
            }
        }
    } // kayitOl: başarılı kayıtta kullanıcıyı yayınlayarak role göre yönlendirmeyi tetikler.

    // Kullanıcı yeni bir işlem yapmadan önce eski hata mesajını temizler.
    fun hataTemizle() {
        _durum.update { it.copy(hata = null) }
    } // hataTemizle: ekranda eski hata mesajının kalmasını engeller.

    // Yönlendirme yapıldıktan sonra başarı durumunu sıfırlar.
    fun girisTuketildi() {
        _durum.update { it.copy(girisYapan = null) }
    } // girisTuketildi: aynı kullanıcı için yönlendirmenin tekrar tetiklenmesini önler.
}
