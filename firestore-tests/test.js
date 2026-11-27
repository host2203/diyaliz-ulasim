const fs = require('fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  writeBatch,
  deleteDoc,
  collection,
  query,
  where,
} = require('firebase/firestore');

const PROJECT_ID = 'demo-diyaliz';
const path = require('path');
const RULES = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');

const TEMEL_TALEP = {
  patientId: 'P',
  patientName: 'Test Hasta',
  sessionDate: '2026-11-01',
  sessionTime: '09:30',
  sessionDurationMinutes: 240,
  returnBufferMinutes: 15,
  centerName: 'Test Merkez',
  centerLat: 39.933,
  centerLng: 32.878,
  pickupAddress: 'Test Adres 1',
  pickupLat: 0,
  pickupLng: 0,
  roundTrip: true,
  companionRequired: false,
  companionName: '',
  companionPhone: '',
  companionRelation: '',
  wheelchairRequired: false,
  note: '',
  status: 'REQUESTED',
  vehicleId: '',
  vehicleName: '',
  personnelId: '',
  personnel: '',
  personnelPhone: '',
  plannedPickupTime: '',
  readiness: 'NOT_SET',
  delayMinutes: 0,
  readinessUpdatedAt: 0,
  delayOverride: false,
  delayOverrideReason: '',
  delayOverrideBy: '',
  delayOverrideByName: '',
  delayOverrideAt: 0,
  delayRescheduled: false,
  delayPreviousSessionDate: '',
  delayPreviousSessionTime: '',
  delayPreviousPickupTime: '',
  delayDriverMessage: '',
  driverDelayMinutes: 0,
  driverDelayStatus: 'NONE',
  driverDelayTarget: 'NONE',
  driverDelayUpdatedAt: 0,
  driverDelayApprovedAt: 0,
  driverDelayApprovedBy: '',
  driverDelayApprovedByName: '',
  etaBaseMinutes: 0,
  etaMinutes: 0,
  etaTarget: 'NONE',
  etaUpdatedAt: 0,
  etaSource: '',
  centerArrivedAt: 0,
  returnPlannedAt: 0,
  returnPlanStatus: 'NONE',
  returnApprovedAt: 0,
  returnApprovedBy: '',
  returnApprovedByName: '',
  returnStartedAt: 0,
  cancelledBy: '',
  cancelReason: '',
  cancelledAt: 0,
  priorityCategory: 'NONE',
  priorityVerified: false,
  priorityVerifiedBy: '',
  priorityVerifiedByName: '',
  priorityVerifiedAt: 0,
  priorityVerificationNote: '',
  createdBy: 'P',
  createdAt: 1,
  updatedAt: 1,
  slotId: '',
};

function talep(id, ek) {
  return { ...TEMEL_TALEP, ...ek };
}

let testEnv;

async function seed() {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users', 'P'), { role: 'PATIENT', name: 'Test Hasta', email: 'p@test' });
    await setDoc(doc(db, 'users', 'C'), { role: 'COORDINATOR', name: 'Test Koordinator', email: 'c@test' });
    await setDoc(doc(db, 'users', 'D'), {
      role: 'DRIVER', name: 'Test Gorevli', email: 'd@test', personnelId: 'p1',
    });
    await setDoc(doc(db, 'users', 'D2'), {
      role: 'DRIVER', name: 'Diger Gorevli', email: 'd2@test', personnelId: 'p2',
    });
    await setDoc(doc(db, 'users', 'U'), { role: 'PATIENT', name: 'Yabanci', email: 'u@test' });
    await setDoc(doc(db, 'users', 'R'), {
      role: 'RELATIVE', name: 'Test Yakin', email: 'r@test', linkedPatientId: 'P',
    });
    await setDoc(doc(db, 'users', 'R2'), {
      role: 'RELATIVE', name: 'Test Yakin 2', email: 'r2@test', linkedPatientId: '',
    });
    await setDoc(doc(db, 'personnel', 'p1'), {
      name: 'Test Gorevli', phone: '05550000001', active: true, driverUid: 'D',
    });
    await setDoc(doc(db, 'personnel', 'p2'), {
      name: 'Diger Gorevli', phone: '05550000002', active: true, driverUid: 'D2',
    });

    await setDoc(doc(db, 'transportRequests', 'r1'), talep('r1', {
      status: 'PLANNED', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
    }));
    await setDoc(doc(db, 'transportRequests', 'r2'), talep('r2', {
      status: 'ON_THE_WAY', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
      readiness: 'DELAYED', delayMinutes: 15,
    }));
    await setDoc(doc(db, 'transportRequests', 'r3'), talep('r3', {
      status: 'ON_THE_WAY', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
      readiness: 'DELAYED', delayMinutes: 15,
      delayOverride: true, delayOverrideReason: 'Onaylandi',
      delayOverrideBy: 'C', delayOverrideByName: 'Test Koordinator', delayOverrideAt: 2,
    }));
    await setDoc(doc(db, 'transportRequests', 'r4'), talep('r4', {
      patientId: 'U', createdBy: 'U', status: 'PLANNED',
      vehicleId: 'v2', vehicleName: 'Arac 2',
      personnelId: 'p2', personnel: 'Diger Gorevli', plannedPickupTime: '10:00',
    }));
    await setDoc(doc(db, 'transportRequests', 'r5'), talep('r5', {
      status: 'AT_CENTER', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
      centerArrivedAt: 1, returnPlannedAt: 900000,
      returnPlanStatus: 'PENDING',
    }));
    await setDoc(doc(db, 'transportRequests', 'r6'), talep('r6', {
      status: 'PLANNED', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
      etaBaseMinutes: 10, etaMinutes: 10, etaTarget: 'HOME',
    }));
    await setDoc(doc(db, 'transportRequests', 'r7'), talep('r7', {
      status: 'PICKED_UP', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
      etaBaseMinutes: 10, etaMinutes: 10, etaTarget: 'CENTER',
    }));
    await setDoc(doc(db, 'transportRequests', 'r8'), talep('r8', {
      status: 'AT_CENTER', vehicleId: 'v1', vehicleName: 'Arac 1',
      personnelId: 'p1', personnel: 'Test Gorevli', plannedPickupTime: '09:00',
      centerArrivedAt: 1, returnPlannedAt: 900000,
      returnPlanStatus: 'APPROVED', returnApprovedAt: 2,
      returnApprovedBy: 'C', returnApprovedByName: 'Test Koordinator',
    }));
    await setDoc(doc(db, 'notifications', 'n1'), {
      audience: 'DRIVER', userId: 'D', personnelId: 'p1',
      title: 'Yeni yolculuk', message: 'Test', requestId: 'r1',
      read: false, createdBy: 'C', createdAt: 3,
    });
    await setDoc(doc(db, 'delayReports', 'r2'), {
      requestId: 'r2', patientId: 'P', minutes: 15,
      reason: 'Diyaliz cikisi gecikti', driverMessage: '15 dk gecikecegim',
      createdAt: 3, updatedAt: 3,
    });
    await setDoc(doc(db, 'driverDelayReports', 'r6'), {
      requestId: 'r6', driverUid: 'D', personnelId: 'p1',
      minutes: 15, reason: 'Trafik yogunlugu', status: 'INFO',
      createdAt: 3, updatedAt: 3,
    });
  });
}

const hasta = () => testEnv.authenticatedContext('P').firestore();
const koordinator = () => testEnv.authenticatedContext('C').firestore();
const gorevli = () => testEnv.authenticatedContext('D').firestore();
const yabanci = () => testEnv.authenticatedContext('U').firestore();
const yakin = () => testEnv.authenticatedContext('R').firestore();
const yakin2 = () => testEnv.authenticatedContext('R2').firestore();
const anonim = () => testEnv.unauthenticatedContext().firestore();

function bildirim(ek) {
  return {
    audience: 'COORDINATOR', userId: '', personnelId: '',
    title: 'Test', message: 'Test mesaj', requestId: 'r1',
    read: false, createdBy: 'P', createdAt: 5, ...ek,
  };
}

const testler = [];
function test(ad, fn) { testler.push({ ad, fn }); }

// --- Talep okuma ---
test('Hasta kendi talebini okur', async () => {
  await assertSucceeds(getDoc(doc(hasta(), 'transportRequests/r1')));
});
test('Hasta baskasinin talebini okuyamaz', async () => {
  await assertFails(getDoc(doc(hasta(), 'transportRequests/r4')));
});
test('Hasta kendi taleplerini sorgular', async () => {
  await assertSucceeds(getDocs(query(
    collection(hasta(), 'transportRequests'), where('patientId', '==', 'P')
  )));
});
test('Hasta tum talepleri sorgulayamaz', async () => {
  await assertFails(getDocs(collection(hasta(), 'transportRequests')));
});
test('Gorevli kendi atanmis islerini sorgular', async () => {
  await assertSucceeds(getDocs(query(
    collection(gorevli(), 'transportRequests'), where('personnelId', '==', 'p1')
  )));
});
test('Gorevli baska gorevlinin talebini okuyamaz', async () => {
  await assertFails(getDoc(doc(gorevli(), 'transportRequests/r4')));
});
test('Koordinitor tum talepleri okur', async () => {
  await assertSucceeds(getDocs(collection(koordinator(), 'transportRequests')));
});

// --- Talep olusturma ---
test('Hasta temiz baslangicla talep olusturur', async () => {
  await assertSucceeds(setDoc(doc(hasta(), 'transportRequests/yeni1'), talep('yeni1')));
});
test('Hasta PLANLANDI durumuyla talep olusturamaz', async () => {
  await assertFails(setDoc(doc(hasta(), 'transportRequests/yeni2'), talep('yeni2', {
    status: 'PLANNED',
  })));
});
test('Gorevli talep olusturamaz', async () => {
  await assertFails(setDoc(doc(gorevli(), 'transportRequests/yeni3'), talep('yeni3', {
    patientId: 'D', createdBy: 'D',
  })));
});

// --- Hasta islemleri ---
test('Hasta hazirlik bildirimini gunceller', async () => {
  await assertSucceeds(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    readiness: 'READY', delayMinutes: 0, readinessUpdatedAt: 2,
    delayOverride: false, delayOverrideReason: '', delayOverrideBy: '',
    delayOverrideByName: '', delayOverrideAt: 0,
    delayRescheduled: false, delayPreviousSessionDate: '',
    delayPreviousSessionTime: '', delayPreviousPickupTime: '',
    delayDriverMessage: '', updatedAt: 2,
  }));
});
test('Hasta kendine gecikme onayi veremez', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    readiness: 'READY', delayMinutes: 0, readinessUpdatedAt: 2,
    delayOverride: true, delayOverrideReason: 'Kendim onayladim',
    delayOverrideBy: 'P', delayOverrideByName: 'Test Hasta', delayOverrideAt: 2, updatedAt: 2,
  }));
});
test('Hasta talebini iptal eder', async () => {
  await assertSucceeds(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    status: 'CANCELLED', cancelledBy: 'P', cancelReason: 'Plan degisti',
    cancelledAt: 2, updatedAt: 2,
  }));
});
test('Hasta yoldaki talebi iptal edemez', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r2'), {
    status: 'CANCELLED', cancelledBy: 'P', cancelReason: 'Plan degisti',
    cancelledAt: 2, updatedAt: 2,
  }));
});
test('Hasta durum alanini degistiremez', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    status: 'ON_THE_WAY', updatedAt: 2,
  }));
});
test('Hasta seans tarih saat merkez degistiremez', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    sessionTime: '10:00', updatedAt: 2,
  }));
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    sessionDate: '2026-11-02', updatedAt: 3,
  }));
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    centerName: 'Baska Merkez', updatedAt: 4,
  }));
});

// --- Gizli gecikme raporu ---
function gecikmeRaporu(ek) {
  return {
    requestId: 'r1', patientId: 'P', minutes: 20,
    reason: 'Kontrol sonrasi gecikme', driverMessage: '20 dk gecikecegim',
    createdAt: 2, updatedAt: 2, ...ek,
  };
}
test('Hasta kendi gecikme raporunu olusturur', async () => {
  await assertSucceeds(setDoc(doc(hasta(), 'delayReports/r1'), gecikmeRaporu({})));
});
test('Hasta baska hastanin talebine rapor yazamaz', async () => {
  await assertFails(setDoc(doc(hasta(), 'delayReports/r4'), gecikmeRaporu({
    requestId: 'r4',
  })));
});
test('Gorevli gecikme raporu olusturamaz', async () => {
  await assertFails(setDoc(doc(gorevli(), 'delayReports/r1'), gecikmeRaporu({})));
});
test('Gecersiz gecikme suresi reddedilir', async () => {
  await assertFails(setDoc(doc(hasta(), 'delayReports/r1'), gecikmeRaporu({
    minutes: 2,
  })));
});
test('Hasta kendi gecikme raporunu gunceller', async () => {
  await assertSucceeds(updateDoc(doc(hasta(), 'delayReports/r2'), {
    minutes: 25, reason: 'Yeni gerekce', driverMessage: '25 dk',
    updatedAt: 4,
  }));
});
test('Koordinator gecikme raporunu okur', async () => {
  await assertSucceeds(getDoc(doc(koordinator(), 'delayReports/r2')));
});
test('Gorevli gecikme raporunu okuyamaz', async () => {
  await assertFails(getDoc(doc(gorevli(), 'delayReports/r2')));
});
test('Yakin gecikme raporunu okuyamaz', async () => {
  await assertFails(getDoc(doc(yakin(), 'delayReports/r2')));
});
test('Hasta baska hastanin gecikme raporunu okuyamaz', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'delayReports/r4'), gecikmeRaporu({
      requestId: 'r4', patientId: 'U', driverMessage: '',
    }));
  });
  await assertFails(getDoc(doc(hasta(), 'delayReports/r4')));
});
test('Hasta gorevli mesajini talep belgesine yazamaz', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    delayDriverMessage: 'Onaysiz mesaj', updatedAt: 2,
  }));
});
test('Koordinator randevuyu erteler', async () => {
  await assertSucceeds(updateDoc(doc(koordinator(), 'transportRequests/r2'), {
    sessionDate: '2026-11-01', sessionTime: '10:05',
    plannedPickupTime: '09:35', delayOverride: true,
    delayRescheduled: true, delayPreviousSessionDate: '2026-11-01',
    delayPreviousSessionTime: '09:50', delayPreviousPickupTime: '09:20',
    delayDriverMessage: 'Hasta mesaji', updatedAt: 2,
  }));
});
test('Gorevli randevu saatini degistiremez', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r2'), {
    sessionTime: '10:05', updatedAt: 2,
  }));
});
test('Hasta gecikme bildirimini atomik yazar', async () => {
  const db = hasta();
  const batch = writeBatch(db);
  batch.update(doc(db, 'transportRequests/r1'), {
    readiness: 'DELAYED', delayMinutes: 20, readinessUpdatedAt: 2,
    delayOverride: false, delayOverrideReason: '', delayOverrideBy: '',
    delayOverrideByName: '', delayOverrideAt: 0,
    delayRescheduled: false, delayPreviousSessionDate: '',
    delayPreviousSessionTime: '', delayPreviousPickupTime: '',
    delayDriverMessage: '', updatedAt: 2,
  });
  batch.set(doc(db, 'delayReports/r1'), gecikmeRaporu({}));
  batch.set(doc(db, 'notifications/gecikme_koord'), bildirim({}));
  await assertSucceeds(batch.commit());
});
test('Koordinitor gecikme onayini atomik yazar', async () => {
  const db = koordinator();
  const batch = writeBatch(db);
  batch.update(doc(db, 'transportRequests/r2'), {
    sessionDate: '2026-11-01', sessionTime: '10:05',
    plannedPickupTime: '09:35', delayOverride: true,
    delayOverrideReason: '', delayOverrideBy: 'C',
    delayOverrideByName: 'Test Koordinator', delayOverrideAt: 2,
    delayRescheduled: true, delayPreviousSessionDate: '2026-11-01',
    delayPreviousSessionTime: '09:50', delayPreviousPickupTime: '09:20',
    delayDriverMessage: 'Hasta mesaji', updatedAt: 2,
  });
  batch.set(doc(db, 'transportRequests/r2/history/onay1'), {
    type: 'OVERRIDE', value: 'DELAY_APPROVED', note: '15 dk onaylandi',
    updatedBy: 'C', updatedByName: 'Test Koordinator', timestamp: 2,
  });
  batch.set(doc(db, 'notifications/onay_driver'), {
    audience: 'DRIVER', userId: '', personnelId: 'p1',
    title: 'Gecikme onaylandi', message: 'Hasta mesaji', requestId: 'r2',
    read: false, createdBy: 'C', createdAt: 2,
  });
  batch.set(doc(db, 'notifications/onay_patient'), {
    audience: 'PATIENT', userId: 'P', personnelId: '',
    title: 'Gecikmeniz onaylandi', message: 'Seans guncellendi', requestId: 'r2',
    read: false, createdBy: 'C', createdAt: 2,
  });
  await assertSucceeds(batch.commit());
});

// --- Gorevli durum zinciri ---
test('Gorevli PLANLANDI -> YOLDA yapar', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r1'), {
    status: 'ON_THE_WAY', updatedAt: 2,
  }));
});
test('Gorevli durum adimini atlayamaz', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r1'), {
    status: 'PICKED_UP', updatedAt: 2,
  }));
});
test('Gecikmeli hastada HASTA ALINDI artik serbest', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r2'), {
    status: 'PICKED_UP', updatedAt: 2,
  }));
});
test('Koordinitor onayli gecikmede HASTA ALINDI yapilir', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r3'), {
    status: 'PICKED_UP', updatedAt: 2,
  }));
});
test('Gorevli baska talebi guncelleyemez', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r4'), {
    status: 'ON_THE_WAY', updatedAt: 2,
  }));
});
test('Gorevli durum disindaki alani degistiremez', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r1'), {
    status: 'ON_THE_WAY', note: 'Degistirildi', updatedAt: 2,
  }));
});

// --- Gorevli/arac gecikmesi ve ETA ---
test('Gorevli kendi gecikme raporunu atomik olusturur', async () => {
  const db = gorevli();
  const batch = writeBatch(db);
  batch.update(doc(db, 'transportRequests/r1'), {
    driverDelayMinutes: 15, driverDelayStatus: 'INFO', driverDelayTarget: 'HOME',
    driverDelayUpdatedAt: 2, driverDelayApprovedAt: 0,
    driverDelayApprovedBy: '', driverDelayApprovedByName: '',
    etaMinutes: 15, etaTarget: 'HOME', etaUpdatedAt: 2, updatedAt: 2,
  });
  batch.set(doc(db, 'driverDelayReports/r1'), {
    requestId: 'r1', driverUid: 'D', personnelId: 'p1',
    minutes: 15, reason: 'Trafik yogunlugu', status: 'INFO',
    createdAt: 2, updatedAt: 2,
  });
  await assertSucceeds(batch.commit());
});
test('Gorevli baska talebe gecikme raporu yazamaz', async () => {
  await assertFails(setDoc(doc(gorevli(), 'driverDelayReports/r4'), {
    requestId: 'r4', driverUid: 'D', personnelId: 'p1',
    minutes: 15, reason: 'Trafik yogunlugu', status: 'INFO',
    createdAt: 2, updatedAt: 2,
  }));
});
test('Gorevli kendi gecikme raporunu okur', async () => {
  await assertSucceeds(getDoc(doc(gorevli(), 'driverDelayReports/r6')));
});
test('Diger gorevli gecikme raporunu okuyamaz', async () => {
  const db2 = testEnv.authenticatedContext('D2').firestore();
  await assertFails(getDoc(doc(db2, 'driverDelayReports/r6')));
});
test('Hasta gorevli gecikme raporunu okuyamaz', async () => {
  await assertFails(getDoc(doc(hasta(), 'driverDelayReports/r6')));
});
test('Yakin gorevli gecikme raporunu okuyamaz', async () => {
  await assertFails(getDoc(doc(yakin(), 'driverDelayReports/r6')));
});
test('Koordinator gorevli gecikme raporunu okur', async () => {
  await assertSucceeds(getDoc(doc(koordinator(), 'driverDelayReports/r6')));
});
test('Koordinator tum gorevli gecikme raporlarini okur', async () => {
  await assertSucceeds(getDocs(collection(koordinator(), 'driverDelayReports')));
});
test('Gorevli ETA alanlarini gunceller', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r2'), {
    etaBaseMinutes: 20, etaMinutes: 35, etaTarget: 'HOME',
    etaUpdatedAt: 2, etaSource: 'OSRM', updatedAt: 2,
  }));
});
test('Gorevli yanlis ETA hedefi secemez', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r2'), {
    etaBaseMinutes: 20, etaMinutes: 20, etaTarget: 'CENTER',
    etaUpdatedAt: 2, etaSource: 'OSRM', updatedAt: 2,
  }));
});
test('Gorevli PLANNED durumda ETA gunceller', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r6'), {
    etaBaseMinutes: 20, etaMinutes: 20, etaTarget: 'HOME',
    etaUpdatedAt: 2, etaSource: 'OSRM', updatedAt: 2,
  }));
});
test('Gorevli onayli donusu baslatir', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r8'), {
    returnStartedAt: 2, etaBaseMinutes: 0, etaMinutes: 0,
    etaTarget: 'NONE', etaUpdatedAt: 0, etaSource: '', updatedAt: 2,
  }));
});
test('Gorevli onaysiz donusu baslatamaz', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r5'), {
    returnStartedAt: 2, etaBaseMinutes: 0, etaMinutes: 0,
    etaTarget: 'NONE', etaUpdatedAt: 0, etaSource: '', updatedAt: 2,
  }));
});
test('Gorevli merkeze varista donus plani olusturur', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'transportRequests/r7'), {
    status: 'AT_CENTER', centerArrivedAt: 2, returnPlannedAt: 900000,
    returnPlanStatus: 'PENDING',
    driverDelayMinutes: 0, driverDelayStatus: 'NONE', driverDelayTarget: 'NONE',
    driverDelayUpdatedAt: 2, etaBaseMinutes: 0, etaMinutes: 0,
    etaTarget: 'NONE', etaUpdatedAt: 0, etaSource: '', updatedAt: 2,
  }));
});
test('Hasta gorevli gecikme alanlarini yazamaz', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    driverDelayMinutes: 15, driverDelayStatus: 'INFO', updatedAt: 2,
  }));
});
test('Gorevli kendi gecikmesini onaylayamaz', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'transportRequests/r6'), {
    driverDelayStatus: 'APPROVED', driverDelayApprovedAt: 2,
    driverDelayApprovedBy: 'D', driverDelayApprovedByName: 'Test Gorevli',
    updatedAt: 2,
  }));
});
test('Koordinator donus planini onaylar', async () => {
  await assertSucceeds(updateDoc(doc(koordinator(), 'transportRequests/r5'), {
    vehicleId: 'v1', vehicleName: 'Arac 1', personnelId: 'p1',
    personnel: 'Test Gorevli', personnelPhone: '05550000001',
    returnPlanStatus: 'APPROVED', returnApprovedAt: 2,
    returnApprovedBy: 'C', returnApprovedByName: 'Test Koordinator',
    updatedAt: 2,
  }));
});

// --- Koordinator ---
test('Koordinitor atama alanlarini gunceller', async () => {
  await assertSucceeds(updateDoc(doc(koordinator(), 'transportRequests/r1'), {
    vehicleId: 'v1', vehicleName: 'Arac 1', personnelId: 'p1',
    personnel: 'Test Gorevli', personnelPhone: '05550000001',
    plannedPickupTime: '09:15', status: 'PLANNED', updatedAt: 2,
  }));
});
test('Koordinitor gecikme onayi verir', async () => {
  await assertSucceeds(updateDoc(doc(koordinator(), 'transportRequests/r2'), {
    delayOverride: true, delayOverrideReason: 'Kabul edildi',
    delayOverrideBy: 'C', delayOverrideByName: 'Test Koordinator',
    delayOverrideAt: 2, updatedAt: 2,
  }));
});
test('Koordinitor oncelik talebini dogrular', async () => {
  await assertSucceeds(updateDoc(doc(koordinator(), 'transportRequests/r1'), {
    priorityVerified: true, priorityVerifiedBy: 'C',
    priorityVerifiedByName: 'Test Koordinator', priorityVerifiedAt: 2,
    priorityVerificationNote: 'Saglik kurulu raporu kontrol edildi', updatedAt: 2,
  }));
});
test('Hasta kendine oncelik dogrulamasi veremez', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    priorityVerified: true, priorityVerifiedBy: 'P',
    priorityVerifiedByName: 'Test Hasta', priorityVerifiedAt: 2, updatedAt: 2,
  }));
});
test('Hasta oncelik kategorisini degistiremez', async () => {
  await assertFails(updateDoc(doc(hasta(), 'transportRequests/r1'), {
    priorityCategory: 'SERIOUS_HEALTH', updatedAt: 2,
  }));
});
test('Hasta oncelik kategorisiyle talep olusturur', async () => {
  await assertSucceeds(setDoc(doc(hasta(), 'transportRequests/yeni4'), talep('yeni4', {
    priorityCategory: 'SERIOUS_HEALTH', priorityVerified: false,
  })));
});
test('Hasta dogrulanmis oncelikle talep olusturamaz', async () => {
  await assertFails(setDoc(doc(hasta(), 'transportRequests/yeni5'), talep('yeni5', {
    priorityCategory: 'SERIOUS_HEALTH', priorityVerified: true,
  })));
});
test('Gecersiz oncelik kategorisi reddedilir', async () => {
  await assertFails(setDoc(doc(hasta(), 'transportRequests/yeni6'), talep('yeni6', {
    priorityCategory: 'UYDURMA', priorityVerified: false,
  })));
});

// --- Personel ---
test('Koordinitor personel listesini okur', async () => {
  await assertSucceeds(getDocs(collection(koordinator(), 'personnel')));
});
test('Gorevli kendi personel kaydini okur', async () => {
  await assertSucceeds(getDoc(doc(gorevli(), 'personnel/p1')));
});
test('Gorevli baska personel kaydini okuyamaz', async () => {
  await assertFails(getDoc(doc(gorevli(), 'personnel/p2')));
});
test('Hasta personel listesini okuyamaz', async () => {
  await assertFails(getDocs(collection(hasta(), 'personnel')));
});
test('Gorevli personel kaydi olusturamaz', async () => {
  await assertFails(setDoc(doc(gorevli(), 'personnel/p3'), {
    name: 'Sahte', phone: '0555', active: true, driverUid: 'D',
  }));
});

// --- Bildirimler ---
test('Hasta talebi icin koordinatore bildirim yazar', async () => {
  await assertSucceeds(setDoc(doc(hasta(), 'notifications/y1'), bildirim({})));
});
test('Gorevli atandigi talep icin bildirim yazar', async () => {
  await assertSucceeds(setDoc(doc(gorevli(), 'notifications/y2'), bildirim({
    audience: 'DRIVER', personnelId: 'p1', createdBy: 'D',
  })));
});
test('Yabanci kullanici talep icin bildirim yazamaz', async () => {
  await assertFails(setDoc(doc(yabanci(), 'notifications/y3'), bildirim({
    createdBy: 'U',
  })));
});
test('Gorevli kendi bildirimini okur', async () => {
  await assertSucceeds(getDoc(doc(gorevli(), 'notifications/n1')));
});
test('Hasta gorevli bildirimini okuyamaz', async () => {
  await assertFails(getDoc(doc(hasta(), 'notifications/n1')));
});
test('Gorevli kendi bildirim sorgusunu yapar', async () => {
  await assertSucceeds(getDocs(query(
    collection(gorevli(), 'notifications'),
    where('audience', '==', 'DRIVER'),
    where('personnelId', '==', 'p1')
  )));
});
test('Gorevli bildirimi okundu isaretler', async () => {
  await assertSucceeds(updateDoc(doc(gorevli(), 'notifications/n1'), {
    read: true, readAt: 4,
  }));
});
test('Gorevli bildirim metnini degistiremez', async () => {
  await assertFails(updateDoc(doc(gorevli(), 'notifications/n1'), {
    message: 'Degistirildi',
  }));
});
test('Koordinitor koordinator bildirimlerini okur', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'notifications/n2'), {
      audience: 'COORDINATOR', userId: '', personnelId: '',
      title: 'Test', message: 'Test', requestId: 'r1',
      read: false, createdBy: 'P', createdAt: 6,
    });
  });
  await assertSucceeds(getDocs(query(
    collection(koordinator(), 'notifications'), where('audience', '==', 'COORDINATOR')
  )));
});
test('Hasta koordinator bildirimlerini okuyamaz', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'notifications/n3'), {
      audience: 'COORDINATOR', userId: '', personnelId: '',
      title: 'Test', message: 'Test', requestId: 'r1',
      read: false, createdBy: 'P', createdAt: 6,
    });
  });
  await assertFails(getDocs(query(
    collection(hasta(), 'notifications'), where('audience', '==', 'COORDINATOR')
  )));
});

// --- Kullanici profilleri ---
test('Hasta baska kullanicinin profilini okuyamaz', async () => {
  await assertFails(getDoc(doc(hasta(), 'users/C')));
});
test('Koordinitor kullanici profilini okur', async () => {
  await assertSucceeds(getDoc(doc(koordinator(), 'users/P')));
});

// --- Seans doluluk sayaçları ---
// Sayaç yalnızca aynı işlemdeki gerçek bir talep değişikliğiyle (oluşturma, iptal, taşıma)
// ±1 oynatılabilir; talep belgesindeki slotId alanı hangi sayaca bağlı olduğunu gösterir.
function slot(ek) {
  return {
    centerName: 'Test Merkez',
    date: '2026-11-01',
    session: 'SABAH',
    booked: 1,
    updatedAt: 1,
    lastRequestId: 'seed',
    ...ek,
  };
}

async function slotSeed(veri, id = 's1') {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `slotAvailability/${id}`), veri);
  });
}

// Sayaca bağlı (slotId = s1) talep tohumlar.
async function bagliTalepSeed(id, ek, slotIdYok) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const veri = talep(id, { slotId: 's1', status: 'PLANNED', ...ek });
    if (slotIdYok) delete veri.slotId;
    await setDoc(doc(ctx.firestore(), `transportRequests/${id}`), veri);
  });
}

const IPTAL = {
  status: 'CANCELLED', cancelledBy: 'P', cancelReason: 'Plan degisti',
  cancelledAt: 2, updatedAt: 2,
};

test('Hasta doluluk sayacini okur', async () => {
  await slotSeed(slot());
  await assertSucceeds(getDoc(doc(hasta(), 'slotAvailability/s1')));
});
test('Oturumsuz doluluk sayaci okunamaz', async () => {
  await slotSeed(slot());
  await assertFails(getDoc(doc(anonim(), 'slotAvailability/s1')));
});

// +1: talep oluşturma
test('Hasta talep olustururken yeni sayac olusturur', async () => {
  const db = hasta();
  const b = writeBatch(db);
  b.set(doc(db, 'slotAvailability/s1'), slot({ lastRequestId: 'y1' }));
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: 's1' }));
  await assertSucceeds(b.commit());
});
test('Hasta talep olustururken sayaci bir artirir', async () => {
  await slotSeed(slot());
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 2, updatedAt: 2, lastRequestId: 'y1' });
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: 's1' }));
  await assertSucceeds(b.commit());
});
test('Hasta talep olusturmadan sayaci artiramaz', async () => {
  await slotSeed(slot());
  await assertFails(updateDoc(doc(hasta(), 'slotAvailability/s1'), {
    booked: 2, updatedAt: 2, lastRequestId: 'yok',
  }));
});
test('Hasta var olan talebi gostererek sayaci artiramaz', async () => {
  await slotSeed(slot());
  await assertFails(updateDoc(doc(hasta(), 'slotAvailability/s1'), {
    booked: 2, updatedAt: 2, lastRequestId: 'r1',
  }));
});
test('Hasta kendi mevcut talebini gostererek sayaci tekrar artiramaz', async () => {
  await slotSeed(slot());
  await bagliTalepSeed('b1', {});
  await assertFails(updateDoc(doc(hasta(), 'slotAvailability/s1'), {
    booked: 2, updatedAt: 2, lastRequestId: 'b1',
  }));
});
test('Hasta sayaci talep belgesi olmadan olusturamaz', async () => {
  await assertFails(setDoc(doc(hasta(), 'slotAvailability/s1'), slot({ lastRequestId: 'y1' })));
});
test('Hasta baska merkezin sayacini kendi talebiyle artiramaz', async () => {
  await slotSeed(slot({ centerName: 'Baska Merkez' }));
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 2, updatedAt: 2, lastRequestId: 'y1' });
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: 's1' }));
  await assertFails(b.commit());
});
test('Hasta baska seans diliminin sayacini artiramaz', async () => {
  await slotSeed(slot({ session: 'AKSAM' }));
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 2, updatedAt: 2, lastRequestId: 'y1' });
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: 's1' }));
  await assertFails(b.commit());
});
test('Hasta slotId baglamayan talebe sayac artisi yazamaz', async () => {
  await slotSeed(slot());
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 2, updatedAt: 2, lastRequestId: 'y1' });
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: '' }));
  await assertFails(b.commit());
});
test('Hasta iki kez ayni talep icin sayac artiramaz', async () => {
  await slotSeed(slot());
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 3, updatedAt: 2, lastRequestId: 'y1' });
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: 's1' }));
  await assertFails(b.commit());
});
test('Talep slotId alani olmadan olusturulamaz', async () => {
  const veri = talep('y2');
  delete veri.slotId;
  await assertFails(setDoc(doc(hasta(), 'transportRequests/y2'), veri));
});

// -1: iptal
test('Hasta talebini iptal ederken sayaci bir azaltir', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', {});
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 1, updatedAt: 2, lastRequestId: 'i1' });
  b.update(doc(db, 'transportRequests/i1'), IPTAL);
  await assertSucceeds(b.commit());
});
test('Hasta iptal etmeden sayaci azaltamaz', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', {});
  await assertFails(updateDoc(doc(hasta(), 'slotAvailability/s1'), {
    booked: 1, updatedAt: 2, lastRequestId: 'i1',
  }));
});
test('Zaten iptal edilmis talep icin sayac ikinci kez azaltilamaz', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', { status: 'CANCELLED' });
  await assertFails(updateDoc(doc(hasta(), 'slotAvailability/s1'), {
    booked: 1, updatedAt: 2, lastRequestId: 'i1',
  }));
});
test('Sayaca bagli olmayan talebin iptaliyle sayac azaltilamaz', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', { slotId: '' });
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 1, updatedAt: 2, lastRequestId: 'i1' });
  b.update(doc(db, 'transportRequests/i1'), IPTAL);
  await assertFails(b.commit());
});
test('Hasta baska hastanin talebi uzerinden sayaci azaltamaz', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', { patientId: 'U', createdBy: 'U' });
  await assertFails(updateDoc(doc(hasta(), 'slotAvailability/s1'), {
    booked: 1, updatedAt: 2, lastRequestId: 'i1',
  }));
});
test('Eski (slotId alani olmayan) talep iptalinde sayac azalir', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', {}, true);
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 1, updatedAt: 2, lastRequestId: 'i1' });
  b.update(doc(db, 'transportRequests/i1'), IPTAL);
  await assertSucceeds(b.commit());
});
test('Koordinator talep iptalinde sayaci bir azaltir', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('i1', {});
  const db = koordinator();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 1, updatedAt: 2, lastRequestId: 'i1' });
  b.update(doc(db, 'transportRequests/i1'), {
    status: 'CANCELLED', cancelledBy: 'C', cancelReason: 'Iptal', cancelledAt: 2, updatedAt: 2,
  });
  await assertSucceeds(b.commit());
});
test('Hasta sayaci sifirin altina indiremez', async () => {
  await slotSeed(slot({ booked: 0 }));
  await bagliTalepSeed('i1', {});
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: -1, updatedAt: 2, lastRequestId: 'i1' });
  b.update(doc(db, 'transportRequests/i1'), IPTAL);
  await assertFails(b.commit());
});

// Taşıma (koordinatör gecikme onayı)
test('Koordinator talebi baska dilime tasirken sayaclari gunceller', async () => {
  await slotSeed(slot({ booked: 2 }));
  await slotSeed(slot({ booked: 1, session: 'OGLEDEN_SONRA' }), 's2');
  await bagliTalepSeed('t1', {});
  const db = koordinator();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), { booked: 1, updatedAt: 2, lastRequestId: 't1' });
  b.update(doc(db, 'slotAvailability/s2'), { booked: 2, updatedAt: 2, lastRequestId: 't1' });
  b.update(doc(db, 'transportRequests/t1'), {
    sessionTime: '13:00', slotId: 's2', updatedAt: 2,
  });
  await assertSucceeds(b.commit());
});
test('Koordinator talep tasimadan sayaci oynatamaz', async () => {
  await slotSeed(slot({ booked: 2 }));
  await bagliTalepSeed('t1', {});
  await assertFails(updateDoc(doc(koordinator(), 'slotAvailability/s1'), {
    booked: 1, updatedAt: 2, lastRequestId: 't1',
  }));
});
test('Koordinator talep tasimadan sayaci artiramaz', async () => {
  await slotSeed(slot({ booked: 1 }));
  await bagliTalepSeed('t1', {});
  await assertFails(updateDoc(doc(koordinator(), 'slotAvailability/s1'), {
    booked: 2, updatedAt: 2, lastRequestId: 't1',
  }));
});

// Diğer roller ve alanlar
test('Gorevli doluluk sayacini artiramaz', async () => {
  await slotSeed(slot());
  await assertFails(updateDoc(doc(gorevli(), 'slotAvailability/s1'), {
    booked: 2, updatedAt: 2, lastRequestId: 'r1',
  }));
});
test('Yakin doluluk sayaci olusturamaz', async () => {
  await assertFails(setDoc(doc(yakin(), 'slotAvailability/s1'), slot()));
});
test('Profili olmayan hesap doluluk sayacini artiramaz', async () => {
  await slotSeed(slot());
  const profilsiz = testEnv.authenticatedContext('X').firestore();
  await assertFails(updateDoc(doc(profilsiz, 'slotAvailability/s1'), {
    booked: 2, updatedAt: 2, lastRequestId: 'r1',
  }));
  await assertFails(setDoc(doc(profilsiz, 'slotAvailability/s2'), slot()));
});
test('Hasta sayacta baska alan degistiremez', async () => {
  await slotSeed(slot());
  const db = hasta();
  const b = writeBatch(db);
  b.update(doc(db, 'slotAvailability/s1'), {
    booked: 2, centerName: 'Baska Merkez', updatedAt: 2, lastRequestId: 'y1',
  });
  b.set(doc(db, 'transportRequests/y1'), talep('y1', { slotId: 's1' }));
  await assertFails(b.commit());
});
test('Hasta doluluk sayacini silemez', async () => {
  await slotSeed(slot());
  await assertFails(deleteDoc(doc(hasta(), 'slotAvailability/s1')));
});
test('Oturumsuz doluluk sayaci olusturamaz', async () => {
  await assertFails(setDoc(doc(anonim(), 'slotAvailability/s1'), slot()));
});

// --- Hasta yakını davet ve bağlantı ---
function davet(ek) {
  return {
    patientId: 'P',
    patientName: 'Test Hasta',
    createdAt: 1,
    expiresAt: 9999999999999,
    usedBy: '',
    usedAt: 0,
    active: true,
    ...ek,
  };
}

function baglanti(ek) {
  return {
    patientId: 'P',
    patientName: 'Test Hasta',
    inviteCode: 'ABCD23EFGH45',
    createdAt: 1,
    ...ek,
  };
}

test('Hasta davet kodu olusturur', async () => {
  await assertSucceeds(setDoc(doc(hasta(), 'relativeInvites/ABCD23EFGH45'), davet()));
});
test('Hasta kullanilmis davet olusturamaz', async () => {
  await assertFails(setDoc(doc(hasta(), 'relativeInvites/ABCD23EFGH45'), davet({
    usedBy: 'R', usedAt: 2, active: false,
  })));
});
test('Yakin davet kodunu okur', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertSucceeds(getDoc(doc(yakin(), 'relativeInvites/ABCD23EFGH45')));
});
test('Yakin davet listesini okuyamaz', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertFails(getDocs(collection(yakin(), 'relativeInvites')));
});
test('Yakin gecerli davetle baglanti olusturur', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertSucceeds(setDoc(doc(yakin(), 'relativeLinks/R'), baglanti()));
  await assertSucceeds(getDoc(doc(yakin(), 'relativeLinks/R')));
});
test('Yakin gecersiz kodla baglanti olusturamaz', async () => {
  await assertFails(setDoc(doc(yakin(), 'relativeLinks/R'), baglanti({
    inviteCode: 'ZZZZZZZZZZZZ',
  })));
});
test('Yakin baska hastaya baglanti yazamaz', async () => {
  const db = yakin2();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertSucceeds(setDoc(doc(db, 'relativeLinks/R2'), baglanti()));
  await assertFails(setDoc(doc(db, 'users/R2'), {
    linkedPatientId: 'U', updatedAt: 2,
  }, { merge: true }));
});
test('Yakin profilini baglantiyla gunceller', async () => {
  const db = yakin2();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertSucceeds(setDoc(doc(db, 'relativeLinks/R2'), baglanti()));
  await assertSucceeds(setDoc(doc(db, 'users/R2'), {
    linkedPatientId: 'P', updatedAt: 2,
  }, { merge: true }));
});
test('Hasta 6 karakterlik zayif davet kodu olusturamaz', async () => {
  await assertFails(setDoc(doc(hasta(), 'relativeInvites/ABC123'), davet()));
});
test('Gorevli davet kodunu sorgulayamaz', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertFails(getDoc(doc(gorevli(), 'relativeInvites/ABCD23EFGH45')));
});
test('Profili olmayan hesap davet kodunu sorgulayamaz', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  const profilsiz = testEnv.authenticatedContext('X').firestore();
  await assertFails(getDoc(doc(profilsiz, 'relativeInvites/ABCD23EFGH45')));
});
test('Yakin davet kodu olusturamaz', async () => {
  await assertFails(setDoc(doc(yakin(), 'relativeInvites/XYZABCXYZABC'), davet()));
});
test('Yakin daveti kullanildi olarak isaretler', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'relativeInvites/ABCD23EFGH45'), davet());
  });
  await assertSucceeds(updateDoc(doc(yakin(), 'relativeInvites/ABCD23EFGH45'), {
    usedBy: 'R', usedAt: 2, active: false,
  }));
});

async function main() {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
  });

  let gecen = 0;
  let kalan = 0;
  for (const t of testler) {
    await seed();
    try {
      await t.fn();
      console.log(`PASS  ${t.ad}`);
      gecen++;
    } catch (e) {
      console.log(`FAIL  ${t.ad}`);
      console.log(`      ${String(e && e.stack ? e.stack : e)}`);
      kalan++;
    }
  }
  console.log(`\nSONUC: ${gecen} gecti, ${kalan} kaldi`);
  await testEnv.cleanup();
  if (kalan > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
