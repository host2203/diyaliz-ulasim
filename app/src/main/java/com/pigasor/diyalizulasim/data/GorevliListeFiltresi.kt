package com.pigasor.diyalizulasim.data

import com.pigasor.diyalizulasim.model.TransportRequest
import com.pigasor.diyalizulasim.model.TransportStatus

// Görevli panelinde yalnızca aktif işleri bırakır.
// - İptal ve eve bırakılmış talepler her zaman gizlenir.
// - Gidiş-dönüş yoksa merkeze ulaşınca iş biter.
// - Gidiş-dönüş varsa dönüş tamamlanana kadar kart görünür.
fun gorevliAktifTalepleri(talepler: List<TransportRequest>): List<TransportRequest> {
    return talepler
        .filter { talep ->
            talep.status != TransportStatus.CANCELLED &&
                talep.status != TransportStatus.DROPPED_HOME &&
                !(talep.status == TransportStatus.AT_CENTER && !talep.roundTrip)
        }
        .sortedWith(compareBy({ it.sessionDate }, { it.sessionTime }))
} // gorevliAktifTalepleri: tamamlanan/iptal işleri listeden çıkarır; dönüşü olanı korur.
