import express from "express";
import OpenAI from "openai";

const app = express();
app.use(express.json({ limit: "12mb" }));
const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

app.get("/health", (_req, res) => res.json({ ok: true }));

app.post("/api/listing/draft", async (req, res) => {
  try {
    const { imageDataUrl, product } = req.body;
    if (!imageDataUrl) return res.status(400).json({ error: "imageDataUrl gerekli" });

    const response = await client.responses.create({
      model: "gpt-5.6-luna",
      tools: [{ type: "web_search_preview" }],
      input: [{
        role: "user",
        content: [
          {
            type: "input_text",
            text:
              "Bu fotoğraftaki ikinci el ürünü analiz et. Türkçe bir Sahibinden ilan taslağı hazırla. " +
              "Fotoğrafta kesin görülen özellikleri kullan, bilinmeyen marka/model/özellikleri uydurma. " +
              "Web aramasıyla Türkiye'deki güncel ikinci el benzer ilanları ve fiyatlarını araştır. " +
              "Fiyatı TL olarak önerilen tek satış fiyatı şeklinde belirle. " +
              "Çıktıyı SADECE şu üç satır formatında ver:\n" +
              "BAŞLIK: ...\nAÇIKLAMA: ...\nFİYAT: ...\n" +
              "Ürün notu: " + (product || "Fotoğraftaki ürünü analiz et.")
          },
          { type: "input_image", image_url: imageDataUrl }
        ]
      }]
    });

    res.json({ draft: response.output_text });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "İlan taslağı oluşturulamadı." });
  }
});

app.listen(process.env.PORT || 3000, () => console.log("Benim Asistanım sunucusu hazır."));
