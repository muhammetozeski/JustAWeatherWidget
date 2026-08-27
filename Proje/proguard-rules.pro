# R8 zaten sinif/uye adlarini kisaltiyor; bu iki satir kalan paket agacini da
# duzlestiriyor ve erisim degistiricilerini gevsetip daha fazla inline'a izin veriyor.
# Kod davranisini degistirmez, yalnizca dex'i kucultur.
-repackageclasses ''
-allowaccessmodification

# Uygulama yalnizca manifest'ten cagriliyor (receiver + activity'ler); disariya
# acilan bir API'si yok, o yuzden ekstra -keep kurali gerekmiyor.
