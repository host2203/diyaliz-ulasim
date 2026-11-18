package com.pigasor.diyalizulasim.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.pigasor.diyalizulasim.model.AppUser
import com.pigasor.diyalizulasim.model.UserRole
import com.pigasor.diyalizulasim.model.rolEtiketi
import com.pigasor.diyalizulasim.security.Guvenlik
import com.pigasor.diyalizulasim.viewmodel.AuthViewModel
import java.util.concurrent.TimeUnit

@Composable
fun GirisEkrani(onGirisBasarili: (AppUser) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var sifre by rememberSaveable { mutableStateOf("") }
    var adSoyad by rememberSaveable { mutableStateOf("") }
    var secilenRolAdi by rememberSaveable { mutableStateOf(UserRole.PATIENT.name) }
    var kayitModu by rememberSaveable { mutableStateOf(false) }
    var mesaj by rememberSaveable { mutableStateOf("") }
    val secilenRol = UserRole.valueOf(secilenRolAdi)

    val authViewModel: AuthViewModel = viewModel()
    val durum by authViewModel.durum.collectAsStateWithLifecycle()
    var telefonModu by rememberSaveable { mutableStateOf(false) }
    var telefon by rememberSaveable { mutableStateOf("") }
    var smsKodu by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var kodGonderiliyor by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    val serverClientId = remember(context) {
        val kaynak = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (kaynak == 0) "" else context.getString(kaynak)
    } // serverClientId: google-services.json içindeki web OAuth istemcisinden gelir.

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { sonuc ->
        try {
            val hesap = GoogleSignIn.getSignedInAccountFromIntent(sonuc.data)
                .getResult(ApiException::class.java)
            val idToken = hesap?.idToken
            if (idToken.isNullOrBlank()) {
                mesaj = "Google kimlik anahtarı alınamadı."
            } else {
                mesaj = ""
                authViewModel.girisYapGoogle(idToken)
            }
        } catch (e: ApiException) {
            mesaj = "Google girişi başarısız (kod ${e.statusCode})."
        }
    } // googleLauncher: Google hesap seçimi sonucunu Firebase ID token'ına çevirir.

    // Telefon numarasına SMS kodu gönderir.
    fun telefonKoduGonder() {
        val e164 = Guvenlik.telefonE164(telefon)
        if (e164 == null) {
            mesaj = "Telefonu 05XXXXXXXXX biçiminde girin."
            return
        }
        val aktifActivity = activity
        if (aktifActivity == null) {
            mesaj = "Telefon doğrulaması için etkin bir ekran gerekli."
            return
        }
        mesaj = ""
        kodGonderiliyor = true
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(kimlik: PhoneAuthCredential) {
                kodGonderiliyor = false
                authViewModel.girisYapTelefon(kimlik)
            }

            override fun onVerificationFailed(hata: FirebaseException) {
                kodGonderiliyor = false
                mesaj = hata.message ?: "Telefon doğrulanamadı."
            }

            override fun onCodeSent(
                kimlik: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                kodGonderiliyor = false
                verificationId = kimlik
                mesaj = "SMS kodu gönderildi."
            }
        }
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(aktifActivity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    } // telefonKoduGonder: Firebase telefon doğrulamasını başlatır.

    // Gelen SMS kodunu Firebase kimliğine çevirir.
    fun smsKodunuDogrula() {
        val kimlik = verificationId
        if (kimlik.isNullOrBlank()) {
            mesaj = "Önce kod gönderin."
            return
        }
        if (smsKodu.length < 6) {
            mesaj = "6 haneli SMS kodunu girin."
            return
        }
        mesaj = ""
        val credential = PhoneAuthProvider.getCredential(kimlik, smsKodu)
        authViewModel.girisYapTelefon(credential)
    } // smsKodunuDogrula: SMS kodunu PhoneAuthCredential'a çevirir.

    LaunchedEffect(durum.girisYapan) {
        durum.girisYapan?.let { kullanici ->
            onGirisBasarili(kullanici)
            authViewModel.girisTuketildi()
        }
    } // LaunchedEffect: giriş başarılı olduğunda role göre yönlendirmeyi bir kez tetikler.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Diyaliz Ulaşım",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        ) // Uygulama başlığı: ekranın en üstünde marka adını gösterir.

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Diyaliz seansı ulaşımını hasta, hasta yakını ve koordinatör arasında görünür kılar.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        ) // Kısa açıklama: uygulamanın amacını ilk açılışta anlatır.

        Spacer(Modifier.height(24.dp))

        if (kayitModu) {
            OutlinedTextField(
                value = adSoyad,
                onValueChange = { adSoyad = it },
                label = { Text("Ad soyad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            ) // Ad alanı: yalnızca kayıt modunda görünür ve zorunludur.

            Spacer(Modifier.height(12.dp))
        } // Kayıt modunda ad alanı gösterilir; giriş modunda gizlenir.

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-posta") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        ) // E-posta alanı: Firebase kimlik doğrulamasında kullanılacak adresi toplar.

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = sifre,
            onValueChange = { sifre = it },
            label = { Text("Şifre") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        ) // Şifre alanı: girilen değeri ekranda gizleyerek omuz üstü bakışa karşı korur.

        if (kayitModu) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Rol seçin",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            ) // Rol başlığı: kayıt sırasında hangi hesap türünün seçileceğini bildirir.

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(UserRole.PATIENT, UserRole.RELATIVE).forEach { rol ->
                    FilterChip(
                        selected = secilenRol == rol,
                        onClick = { secilenRolAdi = rol.name },
                        label = { Text(rol.rolEtiketi) },
                        modifier = Modifier.fillMaxWidth()
                    ) // Rol çipi: dokunulduğunda seçili rolü günceller.
                }
            } // Rol seçenekleri: güvenlik nedeniyle yalnızca hasta ve yakın rolleri sunulur.

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Koordinatör hesapları yönetici tarafından oluşturulur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) // Güvenlik notu: kullanıcının kendini koordinatör yapamayacağını açıklar.
        } // Kayıt modunda rol seçenekleri ve güvenlik notu gösterilir.

        Spacer(Modifier.height(20.dp))

        if (durum.yukleniyor) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val temizEmail = Guvenlik.metniTemizle(email)
                    val temizAd = Guvenlik.metniTemizle(adSoyad)
                    mesaj = ""
                    authViewModel.hataTemizle()
                    when {
                        !Guvenlik.emailGecerliMi(temizEmail) ->
                            mesaj = "Geçerli bir e-posta adresi girin."
                        !Guvenlik.sifreGecerliMi(sifre) ->
                            mesaj = "Şifre en az 8 karakter olmalı ve harf ile rakam içermeli."
                        kayitModu && !Guvenlik.metinGecerliMi(temizAd, Guvenlik.MAKS_AD_UZUNLUGU) ->
                            mesaj = "Ad soyad girin."
                        kayitModu ->
                            authViewModel.kayitOl(temizAd, temizEmail, sifre, secilenRol)
                        else ->
                            authViewModel.girisYap(temizEmail, sifre)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (kayitModu) "Kayıt ol" else "Giriş yap")
            } // Gönder düğmesi: alanları doğrular ve uygun Firebase işlemini başlatır.
        } // Yükleme sırasında düğme yerine ilerleme göstergesi gösterilir.

        TextButton(
            onClick = {
                if (!durum.yukleniyor) {
                    kayitModu = !kayitModu
                    mesaj = ""
                    authViewModel.hataTemizle()
                }
            },
            enabled = !durum.yukleniyor
        ) {
            Text(if (kayitModu) "Zaten hesabım var, giriş yap" else "Hesabım yok, kayıt ol")
        } // Mod değiştirme düğmesi: giriş ile kayıt formu arasında geçiş yapar.

        if (!kayitModu) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    mesaj = ""
                    authViewModel.hataTemizle()
                    if (serverClientId.isBlank()) {
                        mesaj = "Google girişi için google-services.json yeniden indirilmeli " +
                            "(web OAuth istemcisi yok)."
                    } else {
                        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(serverClientId)
                            .requestEmail()
                            .build()
                        googleLauncher.launch(
                            GoogleSignIn.getClient(context, options).signInIntent
                        )
                    }
                },
                enabled = !durum.yukleniyor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Google ile giriş yap")
            } // Google: hesap seçiciyi açar ve ID token'ı Firebase'e verir.

            if (serverClientId.isBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Google girişi için Firebase'den güncel google-services.json indirip " +
                        "app/ içine koyun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            TextButton(
                onClick = {
                    telefonModu = !telefonModu
                    mesaj = ""
                    authViewModel.hataTemizle()
                },
                enabled = !durum.yukleniyor
            ) {
                Text(if (telefonModu) "Telefon girişini kapat" else "Telefon ile giriş yap")
            } // Telefon: numara ve SMS kodu alanlarını açar/kapatır.

            if (telefonModu) {
                OutlinedTextField(
                    value = telefon,
                    onValueChange = { yeni ->
                        telefon = yeni.filter { it.isDigit() }.take(11)
                    },
                    label = { Text("Telefon (05XXXXXXXXX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { telefonKoduGonder() },
                    enabled = !kodGonderiliyor && !durum.yukleniyor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (kodGonderiliyor) "Kod gönderiliyor..." else "SMS kodu gönder")
                }
                if (verificationId != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smsKodu,
                        onValueChange = { yeni ->
                            smsKodu = yeni.filter { it.isDigit() }.take(6)
                        },
                        label = { Text("SMS kodu") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { smsKodunuDogrula() },
                        enabled = !durum.yukleniyor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Doğrula ve giriş yap")
                    }
                }
            }

            Text(
                text = "Google veya telefonla ilk girişte hasta profili oluşturulur. " +
                    "Koordinatör/görevli hesapları yönetici tarafından atanır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } // Sosyal/telefon girişi yalnızca giriş modunda gösterilir.

        val gorunenMesaj = durum.hata ?: mesaj
        if (gorunenMesaj.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = gorunenMesaj,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            ) // Mesaj alanı: doğrulama veya Firebase hatasını kullanıcıya gösterir.
        } // Boş olmayan hata/uyarı mesajı varsa ekranda gösterilir.

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Bu prototipte gerçek hasta verisi kullanılmaz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        ) // Gizlilik notu: demo verisi kullanıldığını kullanıcıya hatırlatır.
    } // Giriş ekranı düzeni: tüm alanları dikey ve kaydırılabilir biçimde yerleştirir.
} // GirisEkrani: Firebase giriş/kayıt işlemini yönetir ve başarıda rol bazlı yönlendirme yapar.
