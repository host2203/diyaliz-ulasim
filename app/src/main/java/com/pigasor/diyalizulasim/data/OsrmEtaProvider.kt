package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.security.Guvenlik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.ceil

// ETA sağlayıcılarının ortak arayüzü; ileride Google/kendi OSRM sunucusuna
// geçerken yalnızca bu arayüzün yeni uygulaması yazılır.
interface EtaProvider {
    suspend fun rotaSuresiDakika(
        baslangicEnlem: Double,
        baslangicBoylam: Double,
        hedefEnlem: Double,
        hedefBoylam: Double
    ): Result<Int>
}

// Ücretsiz OSRM public sunucusundan gerçek yol rotasına dayalı sürüş süresi alır.
// API anahtarı ve faturalandırma gerektirmez; canlı trafik verisi içermez.
class OsrmEtaProvider : EtaProvider {

    override suspend fun rotaSuresiDakika(
        baslangicEnlem: Double,
        baslangicBoylam: Double,
        hedefEnlem: Double,
        hedefBoylam: Double
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!Guvenlik.konumGecerliMi(baslangicEnlem, baslangicBoylam) ||
            !Guvenlik.konumGecerliMi(hedefEnlem, hedefBoylam)
        ) {
            return@withContext Result.failure(Exception("Geçersiz koordinat."))
        }
        var baglanti: HttpURLConnection? = null
        try {
            val baslangic = "${koordinat(baslangicBoylam)},${koordinat(baslangicEnlem)}"
            val hedef = "${koordinat(hedefBoylam)},${koordinat(hedefEnlem)}"
            val url = URL(
                "https://router.project-osrm.org/route/v1/driving/$baslangic;$hedef" +
                    "?overview=false&alternatives=false&steps=false"
            )
            baglanti = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "DiyalizUlasim/1.0 (student project)")
                setRequestProperty("Accept", "application/json")
            } // Bağlantı: OSRM'e uygulama adı içeren User-Agent ile istek hazırlanır.

            val kod = baglanti.responseCode
            val akis = if (kod in 200..299) baglanti.inputStream else baglanti.errorStream
            val yanit = akis?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (kod !in 200..299) {
                return@withContext Result.failure(
                    Exception("Tahmini varış alınamadı (HTTP $kod).")
                )
            } // HTTP hatası: ETA gösterilmez, kullanıcıya manuel giriş istenmez.

            val json = JSONObject(yanit)
            if (json.optString("code") != "Ok") {
                return@withContext Result.failure(Exception("Rota bulunamadı."))
            }
            val rota = json.optJSONArray("routes")?.optJSONObject(0)
                ?: return@withContext Result.failure(Exception("Rota bulunamadı."))
            val saniye = rota.optDouble("duration", 0.0)
            if (!saniye.isFinite() || saniye <= 0.0) {
                return@withContext Result.failure(Exception("Rota süresi hesaplanamadı."))
            }
            val dakika = ceil(saniye / 60.0).toInt().coerceAtLeast(1)
            Result.success(dakika)
        } catch (e: Exception) {
            Result.failure(
                Exception("Tahmini varış alınamadı. İnternet bağlantınızı kontrol edin.")
            )
        } finally {
            baglanti?.disconnect()
        }
    } // rotaSuresiDakika: iki koordinat arasındaki gerçek OSRM sürüş süresini dakikaya çevirir.

    // OSRM'nin beklediği ondalık biçimde koordinat üretir.
    private fun koordinat(deger: Double): String {
        return String.format(Locale.US, "%.6f", deger)
    } // koordinat: cihaz diline göre virgül ondalık ayırıcı üretilmesini engeller.
}
