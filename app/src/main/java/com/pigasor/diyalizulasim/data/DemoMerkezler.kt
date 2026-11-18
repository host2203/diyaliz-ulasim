package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.model.DiyalizMerkezi

// Uygulamayla gelen başlangıç merkez listesi.
// Gerçek merkezler Firestore'daki centers koleksiyonundan eklenebilir; bu liste demo içindir.
// Kapasite değerleri kurgusal demo verisidir; koordinatör panelinden değiştirilebilir.
object DemoMerkezler {
    val liste = listOf(
        DiyalizMerkezi(
            il = "İstanbul",
            ilce = "Üsküdar",
            ad = "Haydarpaşa Numune Eğitim ve Araştırma Hastanesi Diyaliz Ünitesi",
            kapasitePerSeans = 4
        ),
        DiyalizMerkezi(
            il = "İstanbul",
            ilce = "Şişli",
            ad = "Şişli Hamidiye Etfal Eğitim ve Araştırma Hastanesi Diyaliz Ünitesi",
            kapasitePerSeans = 5
        ),
        DiyalizMerkezi(
            il = "İstanbul",
            ilce = "Bakırköy",
            ad = "Bakırköy Dr. Sadi Konuk Eğitim ve Araştırma Hastanesi Diyaliz Ünitesi",
            kapasitePerSeans = 6
        ),
        DiyalizMerkezi(
            il = "Ankara",
            ilce = "Çankaya",
            ad = "Ankara Üniversitesi Tıp Fakültesi Cebeci Hastanesi Diyaliz Ünitesi",
            kapasitePerSeans = 4
        ),
        DiyalizMerkezi(
            il = "İzmir",
            ilce = "Bornova",
            ad = "Ege Üniversitesi Tıp Fakültesi Hastanesi Diyaliz Ünitesi",
            kapasitePerSeans = 6
        ),
        DiyalizMerkezi(
            il = "Bursa",
            ilce = "Nilüfer",
            ad = "Uludağ Üniversitesi Tıp Fakültesi Hastanesi Diyaliz Ünitesi",
            kapasitePerSeans = 5
        )
    )
} // DemoMerkezler: seçim menüsünün boş kalmaması için başlangıç verisi sağlar.
