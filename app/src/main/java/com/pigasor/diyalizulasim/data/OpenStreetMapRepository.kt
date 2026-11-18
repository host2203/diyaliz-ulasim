package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.model.DiyalizMerkezi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// OpenStreetMap/Nominatim üzerinden seçilen ilçedeki diyaliz merkezlerini arar.
// Ücretsizdir; Nominatim kullanım ilkesine uygun olarak istek başına bir arama yapılır.
class OpenStreetMapRepository {

    // "diyaliz <ilçe> <il>" araması yapar ve sonuçları uygulama modeline çevirir.
    suspend fun merkezAra(il: String, ilce: String): Result<List<DiyalizMerkezi>> =
        withContext(Dispatchers.IO) {
            var baglanti: HttpURLConnection? = null
            try {
                val sorgu = URLEncoder.encode("diyaliz $ilce $il", "UTF-8")
                val url = URL(
                    "https://nominatim.openstreetmap.org/search" +
                        "?format=jsonv2&countrycodes=tr&limit=20&accept-language=tr&q=$sorgu"
                )
                baglanti = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "DiyalizUlasim/1.0 (student project)")
                    setRequestProperty("Accept", "application/json")
                } // Bağlantı: Nominatim'e uygulama adı içeren User-Agent ile istek hazırlanır.

                val kod = baglanti.responseCode
                val akis = if (kod in 200..299) baglanti.inputStream else baglanti.errorStream
                val yanit = akis?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (kod !in 200..299) {
                    return@withContext Result.failure(
                        Exception("Merkez araması başarısız (HTTP $kod).")
                    )
                } // Hata durumu: servis veya bağlantı sorununda kullanıcıya bilgi verilir.

                val dizi = JSONArray(yanit)
                val merkezler = (0 until dizi.length()).mapNotNull { index ->
                    val yer = dizi.optJSONObject(index) ?: return@mapNotNull null
                    val tamAdres = yer.optString("display_name", "")
                    val ad = yer.optString("name").ifBlank {
                        tamAdres.substringBefore(",").trim()
                    }
                    if (ad.isBlank()) return@mapNotNull null
                    DiyalizMerkezi(
                        il = il,
                        ilce = ilce,
                        ad = ad,
                        adres = tamAdres,
                        placeId = yer.optString("osm_id", ""),
                        enlem = yer.optString("lat").toDoubleOrNull() ?: 0.0,
                        boylam = yer.optString("lon").toDoubleOrNull() ?: 0.0
                    ) // Merkez: OSM sonucundaki ad, adres ve koordinatları modele taşır.
                }
                Result.success(merkezler)
            } catch (e: Exception) {
                Result.failure(
                    Exception("Merkez araması yapılamadı. İnternet bağlantınızı kontrol edin.")
                )
            } finally {
                baglanti?.disconnect()
            }
        } // merkezAra: ücretsiz OSM aramasını arka planda yapar ve arayüzü kilitlemez.

    // Seçilen il/ilçe merkezini haritanın başlangıç noktası için geocoder.
    suspend fun konumAra(il: String, ilce: String): Result<Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            if (il.isBlank() || ilce.isBlank()) {
                return@withContext Result.failure(Exception("İl/ilçe seçilmedi."))
            }
            var baglanti: HttpURLConnection? = null
            try {
                val sorgu = URLEncoder.encode("$ilce, $il, Türkiye", "UTF-8")
                val url = URL(
                    "https://nominatim.openstreetmap.org/search" +
                        "?format=jsonv2&countrycodes=tr&limit=1&accept-language=tr&q=$sorgu"
                )
                baglanti = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "DiyalizUlasim/1.0 (student project)")
                    setRequestProperty("Accept", "application/json")
                }
                val kod = baglanti.responseCode
                val akis = if (kod in 200..299) baglanti.inputStream else baglanti.errorStream
                val yanit = akis?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (kod !in 200..299) {
                    return@withContext Result.failure(
                        Exception("İl/ilçe konumu alınamadı (HTTP $kod).")
                    )
                }
                val dizi = JSONArray(yanit)
                if (dizi.length() == 0) {
                    return@withContext Result.failure(Exception("İl/ilçe konumu bulunamadı."))
                }
                val yer = dizi.getJSONObject(0)
                val enlem = yer.optString("lat").toDoubleOrNull()
                val boylam = yer.optString("lon").toDoubleOrNull()
                if (enlem == null || boylam == null) {
                    Result.failure(Exception("İl/ilçe koordinatı okunamadı."))
                } else {
                    Result.success(enlem to boylam)
                }
            } catch (e: Exception) {
                Result.failure(Exception("İl/ilçe konumu alınamadı. İnterneti kontrol edin."))
            } finally {
                baglanti?.disconnect()
            }
        } // konumAra: haritayı seçilen il/ilçe merkezinde açmak için kullanılır.

    // Haritada seçilen koordinatı okunabilir adrese çevirir.
    suspend fun adresTersineCevir(enlem: Double, boylam: Double): Result<String> =
        withContext(Dispatchers.IO) {
            if (enlem !in -90.0..90.0 || boylam !in -180.0..180.0) {
                return@withContext Result.failure(Exception("Koordinat geçersiz."))
            }
            var baglanti: HttpURLConnection? = null
            try {
                val url = URL(
                    "https://nominatim.openstreetmap.org/reverse" +
                        "?format=jsonv2&zoom=18&accept-language=tr" +
                        "&lat=$enlem&lon=$boylam"
                )
                baglanti = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "DiyalizUlasim/1.0 (student project)")
                    setRequestProperty("Accept", "application/json")
                }
                val kod = baglanti.responseCode
                val akis = if (kod in 200..299) baglanti.inputStream else baglanti.errorStream
                val yanit = akis?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (kod !in 200..299) {
                    return@withContext Result.failure(
                        Exception("Adres çevrilemedi (HTTP $kod).")
                    )
                }
                val adres = JSONObject(yanit).optString("display_name", "").trim()
                if (adres.isBlank()) {
                    Result.failure(Exception("Bu noktada adres bulunamadı."))
                } else {
                    Result.success(adres)
                }
            } catch (e: Exception) {
                Result.failure(Exception("Adres çevrilemedi. İnterneti kontrol edin."))
            } finally {
                baglanti?.disconnect()
            }
        } // adresTersineCevir: harita seçimini Nominatim üzerinden adrese çevirir.
}
