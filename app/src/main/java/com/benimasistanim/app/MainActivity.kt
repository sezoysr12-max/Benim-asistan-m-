package com.benimasistanim.app

import android.net.Uri
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var image by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ürün fotoğrafını seç ve ilanı hazırlat.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Benim Asistanım") }) }) { pad ->
            LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Sahibinden İlan Asistanı", style = MaterialTheme.typography.headlineSmall); Text(status) }
                item { OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Text("Ürün fotoğrafı seç") } }
                item { image?.let { AsyncImage(it, "Ürün fotoğrafı", Modifier.fillMaxWidth().heightIn(max=360.dp)) } }
                item { OutlinedTextField(title, { title=it }, label={Text("İlan başlığı")}, modifier=Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description=it }, label={Text("İlan açıklaması")}, minLines=5, modifier=Modifier.fillMaxWidth()) }
                item { OutlinedTextField(price, { price=it }, label={Text("Önerilen fiyat")}, modifier=Modifier.fillMaxWidth()) }
                item {
                    Button({
                        status = if (image == null) "Önce ürün fotoğrafını seç." else {
                            if (title.isBlank()) title="Ürün ilanı"
                            if (description.isBlank()) description="Ürün bilgileri analiz edilerek açıklama hazırlanacak."
                            if (price.isBlank()) price="Piyasa araştırması sonrası belirlenecek"
                            "Taslak ilan hazırlandı. Yayınlamadan önce onayın bekleniyor."
                        }
                    }, Modifier.fillMaxWidth()) { Text("İlanı hazırla") }
                }
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("Güvenlik", style=MaterialTheme.typography.titleMedium)
                    Text("OpenAI API anahtarı uygulamaya veya GitHub'a gömülmez.")
                } } }
            }
        }
    }
}
