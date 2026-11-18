package com.pigasor.diyalizulasim.data

import android.content.Context
import com.pigasor.diyalizulasim.model.IlVerisi
import org.json.JSONArray

// İl/ilçe verisini uygulamaya gömülü JSON dosyasından okur.
object YerVerisi {

    // assets/iller-ilceler.json dosyasını okuyup il ve ilçe listesine dönüştürür.
    fun illeriYukle(context: Context): List<IlVerisi> {
        return try {
            val json = context.assets
                .open("iller-ilceler.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val dizi = JSONArray(json)
            (0 until dizi.length()).map { index ->
                val nesne = dizi.getJSONObject(index)
                val ilcelerDizisi = nesne.getJSONArray("ilceler")
                val ilceler = (0 until ilcelerDizisi.length()).map { ilcelerDizisi.getString(it) }
                IlVerisi(
                    il = nesne.getString("il"),
                    ilceler = ilceler
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    } // illeriYukle: uygulamanın internet olmadan il/ilçe listesi göstermesini sağlar.
}
