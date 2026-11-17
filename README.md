# Levent Mini MP3

Android 4.1 ve üzeri için küçük bir müzik çalar. Cihazdaki müzikleri listeler; önceki, oynat/duraklat ve sonraki düğmeleri ile parçaları çalar ve parça sonunda otomatik ilerler. Ekran döndürülünce çalma sürer.

## Kurulum ve kullanım

`Mini-Mp3.apk` dosyasını telefona aktarın ve açın. Gerekirse cihaz ayarlarından bu kaynak için bilinmeyen kaynaklardan uygulama yüklemeye izin verin. İstendiğinde müzik dosyalarına erişim izni verin.

## Derleme

JDK 17, Android SDK platform 34 ve build-tools 34.0.0 ile `ANDROID_HOME` veya `ANDROID_SDK_ROOT` ayarlayın. `./gradlew assembleRelease` küçültülmüş ve varsayılan debug anahtarıyla imzalanmış APK üretir. Kendi anahtarınızı kullanmak için `KEYSTORE_PATH` değişkenini ayarlayın (takma ad `key`, parola `android`). Alternatif olarak aynı anahtarla `KEYSTORE_PATH=/anahtar/yolu ./build.sh` çalıştırılabilir. `build.sh`, JDK ve Android SDK araçlarını kullanır, küçültme yapmaz.

Telif © Piyami Polat
