package com.benimasistanim.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

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
    var status by remember { mutableStateOf("Ürün fotoğrafını seç ve ilanı hazırlat.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        image = it
        status = if (it == null) "Fotoğraf seçilmedi." else "Fotoğraf seçildi. İlanı hazırlaya bas."
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Benim Asistanım") }) }) { pad ->
            LazyColumn(
                Modifier.padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Sahibinden İlan Asistanı", style = MaterialTheme.typography.headlineSmall)
                    Text(status)
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
                    Button(
                        onClick = {
                            val selected = image ?: return@Button
                            busy = true
                            status = "Fotoğraf analiz ediliyor ve piyasa araştırılıyor..."
                            scope.launch {
                                try {
                                    val result = createListingDraft(selected)
                                    title = result.optString("title", title)
                                    description = result.optString("description", description)
                                    price = result.optString("price", price)
                                    status = "İlan taslağı hazırlandı. Yayınlamadan önce kontrol et."
                                } catch (e: Exception) {
                                    status = "Hata: ${e.message ?: "Sunucuya ulaşılamadı."}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = image != null && !busy
                    ) {
                        Text(if (busy) "Hazırlanıyor..." else "İlanı hazırla")
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Güvenlik", style = MaterialTheme.typography.titleMedium)
                            Text("OpenAI API anahtarı uygulamaya veya GitHub'a gömülmez.")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun createListingDraft(uri: Uri): JSONObject = withContext(Dispatchers.IO) {
    val bitmap = contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(input)
    } ?: throw IllegalArgumentException("Fotoğraf okunamadı.")

    val output = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, output)
    bitmap.recycle()

    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    val body = JSONObject()
        .put("imageDataUrl", "data:image/jpeg;base64,$base64")
        .put("product", "Fotoğraftaki ürünü analiz et.")

    val request = Request.Builder()
        .url(SERVER_URL)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()

    OkHttpClient().newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = try { JSONObject(text).optString("error") } catch (_: Exception) { "" }
            throw IllegalStateException(message.ifBlank { "Sunucu hatası: ${response.code}" })
        }
        val draft = JSONObject(text).optString("draft")
        if (draft.isBlank()) throw IllegalStateException("Sunucudan ilan taslağı gelmedi.")
        parseDraft(draft)
    }
}

private fun parseDraft(draft: String): JSONObject {
    val result = JSONObject()
    val title = Regex("(?m)^BAŞLIK:\\s*(.+)$").find(draft)?.groupValues?.get(1)?.trim()
    val description = Regex("(?m)^AÇIKLAMA:\\s*([\\s\\S]*?)(?=\\nFİYAT:|$)").find(draft)?.groupValues?.get(1)?.trim()
    val price = Regex("(?m)^FİYAT:\\s*(.+)$").find(draft)?.groupValues?.get(1)?.trim()
    if (!title.isNullOrBlank()) result.put("title", title)
    if (!description.isNullOrBlank()) result.put("description", description)
    if (!price.isNullOrBlank()) result.put("price", price)
    if (result.length() == 0) result.put("description", draft.trim())
    return result
}
