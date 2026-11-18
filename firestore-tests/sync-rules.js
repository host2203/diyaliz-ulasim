// Emülatör, kural dosyasının proje klasörü içinde olmasını ister.
// Tek doğruluk kaynağı ../firestore.rules; test öncesi buraya kopyalanır.
const fs = require('fs');
const path = require('path');

const kaynak = path.join(__dirname, '..', 'firestore.rules');
const hedef = path.join(__dirname, 'firestore.rules');
fs.copyFileSync(kaynak, hedef);
console.log('firestore.rules test klasörüne kopyalandı.');
