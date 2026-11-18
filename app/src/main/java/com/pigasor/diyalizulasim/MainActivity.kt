package com.pigasor.diyalizulasim

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pigasor.diyalizulasim.ui.DiyalizUlasimTheme

class MainActivity : ComponentActivity() {

    // Android 13+ için bildirim izni isteğini sonuçlandırır.
    private val bildirimIzni = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* İzin verilmezse uygulama içi bildirim merkezi kullanılmaya devam eder. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bildirimIzniIste()
        setContent {
            DiyalizUlasimTheme {
                AppNavigation()
            }
        }
    } // onCreate: izin isteğini başlatır, temayı ve gezinme grafiğini yükler.

    // Yalnızca Android 13 ve üzerinde çalışma zamanı bildirim izni ister.
    private fun bildirimIzniIste() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bildirimIzni.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    } // bildirimIzniIste: eski Android sürümlerinde gereksiz izin penceresi açmaz.
} // MainActivity: uygulamanın tek giriş noktasıdır; Compose arayüzünü yükler.
