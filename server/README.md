# Benim Asistanım API

Bu klasör, Android uygulamasının fotoğrafı güvenli şekilde gönderdiği sunucu tarafını içerir.

## Render ile yayınlama

1. Render'da **New > Web Service** oluştur.
2. GitHub'daki `sezoysr12-max/Benim-asistan-m-` deposunu bağla.
3. Root Directory: `server`
4. Build Command: `npm install`
5. Start Command: `npm start`
6. Environment Variables bölümüne `OPENAI_API_KEY` ekle ve OpenAI API anahtarını buraya gir.
7. Deploy tamamlanınca oluşan `https://....onrender.com` adresini al.
8. Android'deki `MainActivity.kt` içinde `SERVER_URL` değerini bu adresin sonuna `/api/listing/draft` ekleyerek güncelle.

API anahtarı Android APK'ya veya GitHub koduna yazılmamalıdır. Render ortam değişkeni olarak tutulmalıdır.

Sağlık kontrolü:
`https://....onrender.com/health`

Render, GitHub'a bağlı web servislerinde yeni push'larla otomatik yeniden dağıtım yapabilir.
