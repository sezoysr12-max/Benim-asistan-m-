import express from "express";
import OpenAI from "openai";
const app=express();
app.use(express.json({limit:"10mb"}));
const client=new OpenAI({apiKey:process.env.OPENAI_API_KEY});
app.get("/health",(_req,res)=>res.json({ok:true}));
app.post("/api/listing/draft",async(req,res)=>{
 try{
  const {product}=req.body;
  if(!product)return res.status(400).json({error:"product gerekli"});
  const r=await client.responses.create({
   model:"gpt-5.6",
   input:"İkinci el ürün ilanı hazırla. Türkçe, kısa ve dürüst yaz. Bilinmeyen özellikleri uydurma. Ürün: "+product
  });
  res.json({draft:r.output_text});
 }catch(e){console.error(e);res.status(500).json({error:"İlan taslağı oluşturulamadı."});}
});
app.listen(process.env.PORT||3000,()=>console.log("Benim Asistanım sunucusu hazır."));
