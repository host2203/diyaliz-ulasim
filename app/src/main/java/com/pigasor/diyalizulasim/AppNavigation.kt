package com.pigasor.diyalizulasim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pigasor.diyalizulasim.data.AuthRepository
import com.pigasor.diyalizulasim.model.UserRole
import com.pigasor.diyalizulasim.ui.GirisEkrani
import com.pigasor.diyalizulasim.ui.GorevliPaneliEkrani
import com.pigasor.diyalizulasim.ui.HastaPaneliEkrani
import com.pigasor.diyalizulasim.ui.KoordinatorPaneliEkrani
import com.pigasor.diyalizulasim.ui.TalepFormuEkrani
import com.pigasor.diyalizulasim.ui.YakinEkrani

// Uygulamadaki ekran adresleri tek yerde tutulur; yazım hatalarını önler.
object Rotalar {
    const val GIRIS = "giris"
    const val HASTA = "hasta"
    const val TALEP_FORMU = "talep_formu"
    const val KOORDINATOR = "koordinator"
    const val YAKIN = "yakin"
    const val GOREVLI = "gorevli"
} // Rotalar: ekranlar arası geçişte kullanılan sabit adları tanımlar.

// Kullanıcının rolüne göre açılacak ana ekranın rotasını seçer.
private fun rolRotasi(rol: UserRole): String = when (rol) {
    UserRole.PATIENT -> Rotalar.HASTA
    UserRole.COORDINATOR -> Rotalar.KOORDINATOR
    UserRole.RELATIVE -> Rotalar.YAKIN
    UserRole.DRIVER -> Rotalar.GOREVLI
} // rolRotasi: giriş sonrası doğru rol ekranına yönlendirmeyi sağlar.

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authRepository = remember { AuthRepository() }
    var baslangicHazir by remember { mutableStateOf(false) }
    var baslangicRota by remember { mutableStateOf(Rotalar.GIRIS) }

    LaunchedEffect(Unit) {
        // Firebase oturumu cihazda kalıyorsa kullanıcıyı doğrudan rol ekranına götür.
        baslangicRota = try {
            val kullanici = authRepository.mevcutKullanici()
            if (kullanici != null) rolRotasi(kullanici.role) else Rotalar.GIRIS
        } catch (e: Exception) {
            Rotalar.GIRIS
        }
        baslangicHazir = true
    } // LaunchedEffect: açılışta oturum/profil kontrolünü bir kez yapar.

    val cikisYap: () -> Unit = {
        authRepository.cikisYap()
        navController.navigate(Rotalar.GIRIS) {
            popUpTo(0) { inclusive = true }
        }
    } // cikisYap: Firebase oturumunu kapatır ve tüm geri yığınını temizleyerek girişe döner.

    if (!baslangicHazir) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    } // Oturum kontrolü bitene kadar kısa yükleme ekranı gösterilir.

    NavHost(
        navController = navController,
        startDestination = baslangicRota
    ) {
        composable(Rotalar.GIRIS) {
            GirisEkrani(
                onGirisBasarili = { kullanici ->
                    navController.navigate(rolRotasi(kullanici.role)) {
                        popUpTo(Rotalar.GIRIS) { inclusive = true }
                    }
                }
            )
        } // Giriş rotası: başarılı girişte giriş ekranını kapatıp role göre yönlendirir.

        composable(Rotalar.HASTA) {
            HastaPaneliEkrani(
                onCikis = cikisYap,
                onYeniTalep = { navController.navigate(Rotalar.TALEP_FORMU) }
            )
        } // Hasta rotası: talepleri listeler ve yeni talep formunu açar.

        composable(Rotalar.TALEP_FORMU) {
            TalepFormuEkrani(
                onGeri = { navController.popBackStack() },
                onKaydedildi = { navController.popBackStack() }
            )
        } // Talep formu rotası: kayıt başarılı olunca hasta paneline döner.

        composable(Rotalar.KOORDINATOR) {
            KoordinatorPaneliEkrani(onCikis = cikisYap)
        } // Koordinatör rotası: koordinatör girişinden sonra açılacak ekran.

        composable(Rotalar.YAKIN) {
            YakinEkrani(onCikis = cikisYap)
        } // Yakın rotası: hasta yakını girişinden sonra açılacak ekran.

        composable(Rotalar.GOREVLI) {
            GorevliPaneliEkrani(onCikis = cikisYap)
        } // Görevli rotası: atanan yolculukları ve durum adımlarını gösterir.
    } // NavHost: seçilen role göre doğru ekranı gösteren gezinme grafiğini kurar.
} // AppNavigation: tüm uygulamanın ekran geçişlerini tek yerden yönetir.
