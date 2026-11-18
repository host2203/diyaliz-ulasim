package com.pigasor.diyalizulasim.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.UserRole
import kotlinx.coroutines.tasks.await

// Firebase Authentication ve Firestore kullanıcı profil işlemlerini yönetir.
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // E-posta ve şifre ile giriş yapar, ardından Firestore'dan rol bilgisini yükler.
    suspend fun girisYap(email: String, sifre: String): Result<AppUser> {
        return try {
            val sonuc = auth.signInWithEmailAndPassword(email.trim(), sifre).await()
            val uid = sonuc.user?.uid
                ?: return Result.failure(Exception("Kullanıcı bilgisi alınamadı."))
            val kullanici = kullaniciGetir(uid)
            if (kullanici == null) {
                auth.signOut()
                Result.failure(Exception("Kullanıcı profili bulunamadı. Yöneticiye başvurun."))
            } else {
                Result.success(kullanici)
            }
        } catch (e: Exception) {
            Result.failure(Exception(hataMesaji(e)))
        }
    } // girisYap: Firebase Auth oturumunu açar ve kullanıcının rolünü Firestore'dan getirir.

    // Google kimlik anahtarıyla giriş yapar; profili yoksa hasta profili oluşturur.
    suspend fun girisYapGoogle(idToken: String): Result<AppUser> {
        return try {
            val kimlik = GoogleAuthProvider.getCredential(idToken, null)
            val sonuc = auth.signInWithCredential(kimlik).await()
            val kullanici = sonuc.user
                ?: return Result.failure(Exception("Google kullanıcı bilgisi alınamadı."))
            profilGetirVeyaOlustur(
                kullanici = kullanici,
                ad = kullanici.displayName.orEmpty(),
                email = kullanici.email.orEmpty(),
                telefon = kullanici.phoneNumber.orEmpty()
            )
        } catch (e: Exception) {
            Result.failure(Exception(hataMesaji(e)))
        }
    } // girisYapGoogle: Google ID token'ını Firebase oturumuna çevirir.

    // Telefon doğrulama kimliğiyle giriş yapar; profili yoksa hasta profili oluşturur.
    suspend fun girisYapTelefon(kimlik: PhoneAuthCredential): Result<AppUser> {
        return try {
            val sonuc = auth.signInWithCredential(kimlik).await()
            val kullanici = sonuc.user
                ?: return Result.failure(Exception("Telefon kullanıcı bilgisi alınamadı."))
            profilGetirVeyaOlustur(
                kullanici = kullanici,
                ad = "Hasta",
                email = kullanici.email.orEmpty(),
                telefon = kullanici.phoneNumber.orEmpty()
            )
        } catch (e: Exception) {
            Result.failure(Exception(hataMesaji(e)))
        }
    } // girisYapTelefon: SMS doğrulamasını Firebase oturumuna çevirir.

    // Firebase kullanıcısının profilini getirir; ilk sosyal girişte hasta profili yazar.
    private suspend fun profilGetirVeyaOlustur(
        kullanici: FirebaseUser,
        ad: String,
        email: String,
        telefon: String
    ): Result<AppUser> {
        val mevcut = kullaniciGetir(kullanici.uid)
        if (mevcut != null) return Result.success(mevcut)
        val yeni = AppUser(
            uid = kullanici.uid,
            name = ad.trim().ifBlank { "Hasta" },
            email = email.trim(),
            phone = telefon.trim(),
            role = UserRole.PATIENT,
            createdAt = System.currentTimeMillis()
        )
        return try {
            firestore.collection("users").document(kullanici.uid).set(yeni.toMap()).await()
            Result.success(yeni)
        } catch (e: Exception) {
            // Profil yazılamazsa yarım sosyal oturumu kapat.
            runCatching { auth.signOut() }
            Result.failure(Exception("Profil oluşturulamadı. Lütfen tekrar deneyin."))
        }
    } // profilGetirVeyaOlustur: sosyal/telefon kullanıcılarına güvenli başlangıç profili verir.

    // Yeni kullanıcı hesabı ve users/{uid} profil belgesini birlikte oluşturur.
    suspend fun kayitOl(ad: String, email: String, sifre: String, rol: UserRole): Result<AppUser> {
        return try {
            val sonuc = auth.createUserWithEmailAndPassword(email.trim(), sifre).await()
            val uid = sonuc.user?.uid
                ?: return Result.failure(Exception("Kullanıcı oluşturulamadı."))
            val yeniKullanici = AppUser(
                uid = uid,
                name = ad.trim(),
                email = email.trim(),
                role = rol,
                createdAt = System.currentTimeMillis()
            )
            try {
                firestore.collection("users").document(uid).set(yeniKullanici.toMap()).await()
            } catch (e: Exception) {
                // Profil yazılamazsa yarım kalan Auth hesabını temizlemeye çalış.
                try {
                    sonuc.user?.delete()?.await()
                } catch (_: Exception) {
                }
                auth.signOut()
                return Result.failure(Exception("Profil oluşturulamadı. Lütfen tekrar deneyin."))
            }
            Result.success(yeniKullanici)
        } catch (e: Exception) {
            Result.failure(Exception(hataMesaji(e)))
        }
    } // kayitOl: yeni Auth hesabı ile Firestore profilini tek işlemde oluşturur.

    // Oturumu kapatır; uygulama yeniden açıldığında giriş ekranı gösterilir.
    fun cikisYap() {
        auth.signOut()
    } // cikisYap: Firebase oturumunu sonlandırır.

    // O an oturum açmış kullanıcının profilini Firestore'dan getirir.
    suspend fun mevcutKullanici(): AppUser? {
        val uid = auth.currentUser?.uid ?: return null
        return kullaniciGetir(uid)
    } // mevcutKullanici: rol ekranlarının hasta/koordinatör bilgisini yeniden yüklemesini sağlar.

    // UID ile Firestore profilini okur; belge yoksa null döndürür.
    private suspend fun kullaniciGetir(uid: String): AppUser? {
        val belge = firestore.collection("users").document(uid).get().await()
        if (!belge.exists()) return null
        return belge.toAppUser()
    } // kullaniciGetir: users/{uid} belgesini okuyup uygulama modeline dönüştürür.

    // Firestore belgesindeki alanları güvenli varsayılanlarla AppUser'a çevirir.
    private fun DocumentSnapshot.toAppUser(): AppUser? {
        val rolMetni = getString("role") ?: return null
        val rol = try {
            UserRole.valueOf(rolMetni)
        } catch (_: Exception) {
            return null
        }
        val gorunenDurumlar = (get("visibleStatuses") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val kayitZamani = when (val deger = get("createdAt")) {
            is Long -> deger
            is Number -> deger.toLong()
            else -> 0L
        }
        return AppUser(
            uid = id,
            name = getString("name") ?: "",
            email = getString("email") ?: "",
            phone = getString("phone") ?: "",
            role = rol,
            linkedPatientId = getString("linkedPatientId") ?: "",
            personnelId = getString("personnelId") ?: "",
            visibleStatuses = gorunenDurumlar,
            createdAt = kayitZamani
        )
    } // toAppUser: eksik veya hatalı alanlarda uygulamanın çökmemesini sağlar.

    // Yeni kullanıcı profilini Firestore'a yazılacak map biçimine çevirir.
    private fun AppUser.toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "role" to role.name,
            "linkedPatientId" to linkedPatientId,
            "personnelId" to personnelId,
            "visibleStatuses" to visibleStatuses,
            "createdAt" to createdAt
        )
    } // toMap: enum ve listeleri Firestore'un kabul ettiği temel tiplere dönüştürür.

    // Firebase hata kodlarını kullanıcıya gösterilecek güvenli mesajlara çevirir.
    private fun hataMesaji(e: Exception): String {
        val kod = (e as? FirebaseAuthException)?.errorCode
        return when (kod) {
            "ERROR_INVALID_EMAIL" -> "E-posta adresi geçersiz."
            "ERROR_WRONG_PASSWORD" -> "Şifre hatalı."
            "ERROR_USER_NOT_FOUND" -> "Bu e-posta ile kayıtlı kullanıcı yok."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Bu e-posta zaten kayıtlı."
            "ERROR_WEAK_PASSWORD" -> "Şifre çok zayıf."
            "ERROR_NETWORK_REQUEST_FAILED" -> "İnternet bağlantınızı kontrol edin."
            "ERROR_TOO_MANY_REQUESTS" -> "Çok fazla deneme yapıldı. Biraz sonra tekrar deneyin."
            else -> "İşlem başarısız oldu. Lütfen tekrar deneyin."
        }
    } // hataMesaji: teknik hata ayrıntılarını kullanıcıya sızdırmadan Türkçe mesaj üretir.
}
