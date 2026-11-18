package com.pigasor.diyalizulasim.bildirim

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pigasor.diyalizulasim.R
import com.pigasor.diyalizulasim.model.Bildirim

// Uygulama açıkken gelen bildirimleri cihazın bildirim alanında gösterir.
object YerelBildirim {

    private const val KANAL_ID = "ulasim_guncellemeleri"
    private const val KANAL_ADI = "Ulaşım güncellemeleri"

    // Bildirim kanalını yalnızca gerektiğinde bir kez oluşturur.
    private fun kanalOlustur(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val yonetici = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mevcut = yonetici.getNotificationChannel(KANAL_ID)
        if (mevcut == null) {
            val kanal = NotificationChannel(
                KANAL_ID,
                KANAL_ADI,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Talep, atama ve yolculuk durumu bildirimleri"
            }
            yonetici.createNotificationChannel(kanal)
        }
    } // kanalOlustur: Android 8+ için zorunlu bildirim kanalını hazırlar.

    // Tek bir uygulama içi bildirimi cihaz bildirimi olarak gösterir.
    fun goster(context: Context, bildirim: Bildirim) {
        kanalOlustur(context)
        val cihazBildirimi = NotificationCompat.Builder(context, KANAL_ID)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle(bildirim.baslik)
            .setContentText(bildirim.mesaj)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bildirim.mesaj))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(bildirim.id.hashCode(), cihazBildirimi)
        } catch (_: SecurityException) {
            // Android 13+ izni verilmediyse uygulama içi bildirim merkezi çalışmaya devam eder.
        }
    } // goster: aynı bildirim kimliği tekrar gelirse önceki kaydı günceller.
}
