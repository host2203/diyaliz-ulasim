package com.pigasor.diyalizulasim.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Kullanıcı bitişik rakam yazarken tarihi GG.AA.YYYY biçiminde gösterir.
class TarihGorselDonusturucu : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rakamlar = text.text.filter { it.isDigit() }.take(8)
        val olusan = StringBuilder()
        val noktaOrijinalKonumlari = mutableListOf<Int>()
        val noktaDonusmusKonumlari = mutableListOf<Int>()

        rakamlar.forEachIndexed { index, karakter ->
            if (index == 2 || index == 4) {
                noktaOrijinalKonumlari.add(index)
                noktaDonusmusKonumlari.add(olusan.length)
                olusan.append('.')
            }
            olusan.append(karakter)
        } // Rakamları gezerken gün/ay ve ay/yıl arasına nokta ekler.

        val donusmusMetin = olusan.toString()
        val eslestirme = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val sinir = offset.coerceIn(0, rakamlar.length)
                return sinir + noktaOrijinalKonumlari.count { it <= sinir }
            } // originalToTransformed: imlecin orijinal konumunu noktalı metne taşır.

            override fun transformedToOriginal(offset: Int): Int {
                val sinir = offset.coerceIn(0, donusmusMetin.length)
                return sinir - noktaDonusmusKonumlari.count { it < sinir }
            } // transformedToOriginal: noktalı metindeki imleci rakam konumuna geri çevirir.
        }
        return TransformedText(AnnotatedString(donusmusMetin), eslestirme)
    } // filter: ekranda noktalı tarihi gösterir; alttaki değer yalnızca rakam kalır.
} // TarihGorselDonusturucu: tarih alanına otomatik nokta ekleyen görsel dönüşüm.

// Kullanıcı 4 rakam yazarken saati SS:DD biçiminde gösterir.
class SaatGorselDonusturucu : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rakamlar = text.text.filter { it.isDigit() }.take(4)
        val ikiNoktaVar = rakamlar.length > 2
        val donusmusMetin = if (ikiNoktaVar) {
            "${rakamlar.substring(0, 2)}:${rakamlar.substring(2)}"
        } else {
            rakamlar
        }
        val ikiNoktaKonumu = 2

        val eslestirme = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val sinir = offset.coerceIn(0, rakamlar.length)
                return if (ikiNoktaVar && sinir >= 2) sinir + 1 else sinir
            } // originalToTransformed: saat rakamlarının imlec konumunu iki noktaya göre kaydırır.

            override fun transformedToOriginal(offset: Int): Int {
                val sinir = offset.coerceIn(0, donusmusMetin.length)
                return if (ikiNoktaVar && sinir > ikiNoktaKonumu) sinir - 1 else sinir
            } // transformedToOriginal: iki nokta sonrası imleci doğru rakama eşler.
        }
        return TransformedText(AnnotatedString(donusmusMetin), eslestirme)
    } // filter: ekranda 09:30 gibi saat gösterir; alttaki değer 0930 olarak kalır.
} // SaatGorselDonusturucu: saat alanına otomatik iki nokta ekleyen görsel dönüşüm.
