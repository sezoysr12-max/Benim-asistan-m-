package com.benimasistanim.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val SERVER_URL = "https://benim-asistan-m.onrender.com/api/listing/draft"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var image by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Fotoğrafı seç. Geri kalan işlemleri Asistan yürütsün.") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        image = it
        status = if (it == null) "Fotoğraf seçilmedi." else "Fotoğraf seçildi. Otomatik ilan hazırlığı başlıyor."
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) status = "Mikrofon hazır. Komut dinleniyor."
        else status = "Sesli komut için mikrofon izni gerekli."
    }

    fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                listening = false
                recognizer.destroy()
                if (command.isNotBlank()) {
                    status = "Komut: " + command
                    AutomationBus.runCommand(command)
                }
            }
            override fun onError(error: Int) {
                listening = false
                recognizer.destroy()
                status = "Ses anlaşılamadı. Tekrar dinlemek için bas."
            }
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Komutunuzu söyleyin")
        })
    }

    LaunchedEffect(image) {
        val selected = image ?: return@LaunchedEffect
        if (busy) return@LaunchedEffect

        busy = true
        status = "Fotoğraf analiz ediliyor, fiyat araştırılıyor ve ilan hazırlanıyor..."
        try {
            val result = createListingDraft(context, selected)
            val draftTitle = result.optString("title")
            val draftDescription = result.optString("description")
            val draftPrice = result.optString("price")

            title = draftTitle
            description = draftDescription
            price = draftPrice

            if (draftTitle.isNotBlank() && draftDescription.isNotBlank() && draftPrice.isNotBlank()) {
                AutomationBus.startListing(
                    ListingData(draftTitle, draftDescription, draftPrice, selected)
                )
                status = "İlan hazır. Sahibinden otomasyonu başlatıldı."
            } else {
                status = "Taslak hazırlandı ancak bazı alanlar eksik."
            }
        } catch (e: Exception) {
            status = "Hata: " + (e.message ?: "İlan hazırlanamadı.")
        } finally {
            busy = false
        }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Benim Asistanım") }) }) { pad ->
            LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Telefon Asistanı + İlan Otomasyonu", style = MaterialTheme.typography.headlineSmall)
                    Text(status)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { listen() }, Modifier.weight(1f)) {
                            Text(if (listening) "Dinliyor..." else "Sesli komut")
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }, Modifier.weight(1f)) {
                            Text("Otomasyon izni")
                        }
                    }
                }
                item {
                    Text(
                        "Bir kez Android Erişilebilirlik izni ver. Bu izin olmadan uygulamanın başka uygulamalarda butonlara basması ve alanlara yazması mümkün değildir.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item {
                    OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = !busy) {
                        Text("Ürün fotoğrafı seç")
                    }
                }
                item {
                    image?.let {
                        AsyncImage(it, "Ürün fotoğrafı", Modifier.fillMaxWidth().heightIn(max = 360.dp))
                    }
                }
                item {
                    OutlinedTextField(title, { title = it }, label = { Text("İlan başlığı") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(description, { description = it }, label = { Text("İlan açıklaması") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(price, { price = it }, label = { Text("Önerilen fiyat") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedButton(
                        onClick = {
                            image?.let { selected ->
                                busy = true
                                status = "İlan yeniden hazırlanıyor..."
                                scope.launch {
                                    try {
                                        val result = createListingDraft(context, selected)
                                        title = result.optString("title", title)
                                        description = result.optString("description", description)
                                        price = result.optString("price", price)
                                        AutomationBus.startListing(
                                            ListingData(title, description, price, selected)
                                        )
                                        status = "İlan yenilendi. Sahibinden otomasyonu başlatıldı."
                                    } catch (e: Exception) {
                                        status = "Hata: " + (e.message ?: "Sunucuya ulaşılamadı.")
                                    } finally { busy = false }
                                }
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = image != null && !busy
                    ) {
                        Text("İlanı yeniden hazırla")
                    }
                }
                item {
                    Button(
                        onClick = {
                            AutomationBus.startListing(ListingData(title, description, price, image))
                            status = "Sahibinden açılıyor. Otomasyon başlatıldı."
                        },
                        Modifier.fillMaxWidth(),
                        enabled = image != null && title.isNotBlank() && description.isNotBlank() && price.isNotBlank()
                    ) {
                        Text("Sahibinden'e yükle")
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Güvenlik", style = MaterialTheme.typography.titleMedium)
                            Text("Şifreler APK içine veya GitHub'a yazılmaz. Android'in izin sistemi kullanılır.")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun createListingDraft(context: android.content.Context, uri: Uri): JSONObject = withContext(Dispatchers.IO) {
    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        ?: throw IllegalArgumentException("Fotoğraf okunamadı.")
    val output = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, output)
    bitmap.recycle()
    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    val body = JSONObject()
        .put("imageDataUrl", "data:image/jpeg;base64," + base64)
        .put("product", "Fotoğraftaki ürünü analiz et.")
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url(SERVER_URL)
        .header("Accept", "application/json")
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()
    client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = try { JSONObject(text).optString("error") } catch (_: Exception) { "" }
            throw IllegalStateException(message.ifBlank { "Sunucu hatası: " + response.code })
        }
        val draft = JSONObject(text).optString("draft")
        if (draft.isBlank()) throw IllegalStateException("Sunucudan ilan taslağı gelmedi.")
        parseDraft(draft)
    }
}

private fun parseDraft(draft: String): JSONObject {
    val result = JSONObject()
    Regex("(?m)^BAŞLIK:\\s*(.+)$").find(draft)?.groupValues?.get(1)?.trim()?.let { result.put("title", it) }
    Regex("(?m)^AÇIKLAMA:\\s*([\\s\\S]*?)(?=\\nFİYAT:|$)").find(draft)?.groupValues?.get(1)?.trim()?.let { result.put("description", it) }
    Regex("(?m)^FİYAT:\\s*(.+)$").find(draft)?.groupValues?.get(1)?.trim()?.let { result.put("price", it) }
    if (result.length() == 0) result.put("description", draft.trim())
    return result
}
