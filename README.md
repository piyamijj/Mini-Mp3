# Levent Mini MP3

Android 4.1 ve üzeri için küçük bir müzik çalar. Cihazdaki müzikleri listeler; önceki, oynat/duraklat ve sonraki düğmeleri ile parçaları çalar ve parça sonunda otomatik ilerler. Ekran döndürülünce çalma sürer.

## Kurulum ve kullanım

`Mini-Mp3.apk` dosyasını telefona aktarın ve açın. Gerekirse cihaz ayarlarından bu kaynak için bilinmeyen kaynaklardan uygulama yüklemeye izin verin. İstendiğinde müzik dosyalarına erişim izni verin.

## Sorun giderme

Parçalar görünmüyorsa ana ekrandaki **İzinler** bölümünde durumu kontrol edin; **İzin iste**'ye dokunun veya izin kalıcı olarak reddedildiyse **Uygulama ayarlarını aç** ile izni verin. Ardından **Yenile**'ye dokunun veya **Dosya seç** ile müziği seçin. Android 13 ve üzeri müzik izni, eski sürümler depolama izni ister. Uygulama medya dizinine ek olarak erişebildiği müzik, indirme, belge ve SD kart klasörlerini tarar. Android sürümüne ve SD kart izinlerine bağlı olarak bazı klasörler doğrudan okunamayabilir; dosya seçiciyi kullanın. Seçicinin verdiği izin kalıcı değilse uygulama yeniden açıldığında dosyayı tekrar seçmeniz gerekebilir. Bozuk veya silinmiş parçalar atlanır; hiçbir parça açılmazsa dosya biçimini ve erişim iznini kontrol edin.

## Derleme

JDK 17, Android SDK platform 34 ve build-tools 34.0.0 ile `ANDROID_HOME` veya `ANDROID_SDK_ROOT` ayarlayın. `./gradlew assembleRelease` küçültülmüş ve varsayılan debug anahtarıyla imzalanmış APK üretir. Kendi anahtarınızı kullanmak için `KEYSTORE_PATH` değişkenini ayarlayın (takma ad `key`, parola `android`). Alternatif olarak aynı anahtarla `KEYSTORE_PATH=/anahtar/yolu ./build.sh` çalıştırılabilir. `build.sh`, JDK ve Android SDK araçlarını kullanır, küçültme yapmaz.

Telif © Piyami Polat
