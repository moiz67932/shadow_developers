package com.example.nasconsports
import android.widget.*
import android.view.View
import android.graphics.pdf.PdfRenderer
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.io.File
fun money(minor:Long)="PKR %,.2f".format(java.util.Locale.US,minor/100.0)
fun MainActivity.merchCard(p:JSONObject,index:Int,parent:LinearLayout):LinearLayout{
    val view=layoutInflater.inflate(R.layout.item_merch_card,parent,false) as LinearLayout
    val images=listOf(R.drawable.merch_thunder,R.drawable.merch_cyber,R.drawable.merch_ball,R.drawable.merch_windbreaker)
    val variants=(p.optJSONArray("variants")?:JSONArray()).objects();val stock=variants.sumOf{it.optInt("stock")}
    view.findViewById<ImageView>(R.id.merch_image).setImageResource(images[index%images.size])
    view.findViewById<TextView>(R.id.merch_tag).text=if(index==3)"LIMITED"else"OFFICIAL"
    view.findViewById<TextView>(R.id.merch_club).text="NASCON ARENA • $stock IN STOCK"
    view.findViewById<TextView>(R.id.merch_title).text=p.optString("name")
    view.findViewById<TextView>(R.id.merch_price).text=money(variants.firstOrNull()?.optLong("price_minor")?:0)
    view.isClickable=true;view.isFocusable=true;view.contentDescription="${p.optString("name")}. Open product details";view.setOnClickListener{productScreen(p)}
    return view
}
fun MainActivity.storeScreen(){
    start("Store");sectionTitle("Tournament store","Official NaSCon championship gear • prices in PKR")
    val promo=card("NaSCon 2026 Official Championship Gear","TOURNAMENT PROMO  •  Use WELCOME10 at checkout\nAuthentic matchday kits, training wear and arena essentials.")
    button("Cart • ${cart.values.sum()} items",promo){cartScreen()};button("Refresh catalogue"){refresh{storeScreen()}}
    sectionTitle("Featured merch","Official teamwear and equipment")
    val products=(data.optJSONArray("products")?:JSONArray()).objects()
    if(products.isEmpty())card("No merchandise available","Refresh when connected to your league server.")
    products.chunked(2).forEachIndexed{rowIndex,pair->
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        content.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
        pair.forEachIndexed{i,p->row.addView(merchCard(p,rowIndex*2+i,row),LinearLayout.LayoutParams(0,-2,1f).apply{if(i==0)rightMargin=dp(6)else leftMargin=dp(6)})}
        if(pair.size==1)row.addView(View(this),LinearLayout.LayoutParams(0,1,1f).apply{leftMargin=dp(6)})
    }
    local.get("receipt:$owner")?.let{button("View last paid receipt"){receiptScreen(it)}}
}
fun MainActivity.productScreen(p:JSONObject){start("Product detail");routeArgs.put("product",p);card("◈   ${p.optString("name")}","Official club teamwear\nPerformance fit • NaSCon 2026");val variants=(p.optJSONArray("variants")?:JSONArray()).objects();val variant=picker("Variant / size",variants.map{it.optString("variant_id").substringAfterLast('-')+" • "+money(it.optLong("price_minor"))});val qty=picker("Quantity",(1..10).map{it.toString()});val stock=text("Choose a variant and check stock.",15f,true);fun check(){if(variants.isEmpty())return;val v=variants[variant.selectedItemPosition];work({api.call("/audit/v1/inventory/"+v.getString("variant_id"))},{r->stock.text="Available stock: ${r.optInt("stock")} • ${money(v.optLong("price_minor"))}"})};button("Check selected stock"){check()};button("Add to cart"){if(variants.isEmpty())return@button;val v=variants[variant.selectedItemPosition];val id=v.getString("variant_id");val n=qty.selectedItemPosition+1;work({api.call("/audit/v1/inventory/$id")},{r->if((cart[id]?:0)+n>r.optInt("stock"))notice("This quantity is unavailable. Stock: ${r.optInt("stock")}")else{cart[id]=(cart[id]?:0)+n;local.put("cart:$owner",JSONObject(cart.toMap()));orderRequest=null;notice("Added $n to your cart.");cartScreen()}})};button("View cart"){cartScreen()}}
fun MainActivity.price(id:String):Long {for(p in (data.optJSONArray("products")?:JSONArray()).objects())for(v in (p.optJSONArray("variants")?:JSONArray()).objects())if(v.optString("variant_id")==id)return v.optLong("price_minor");return 0}
fun MainActivity.cartScreen(){start("Cart & checkout");text("Your matchday kit",26f,true);if(cart.isEmpty()){card("Your cart is empty","Choose a jersey and size to get started.");button("Browse store"){storeScreen()};return};for((id,n)in cart.toMap()){val box=card("Club Jersey • ${id.substringAfterLast('-')}","$n × ${money(price(id))} = ${money(price(id)*n)}");button("Remove",box){cart.remove(id);local.put("cart:$owner",JSONObject(cart.toMap()));orderRequest=null;cartScreen()}};val subtotal=cart.entries.sumOf{price(it.key)*it.value};card("Subtotal ${money(subtotal)}","All prices include the selected quantity.");val discount=field("Discount code (optional)",orderRequest?.optString("discount_code")?.takeIf{it!="null"}?:"");val total=text("Total ${money(subtotal)}",22f,true);button("Preview discount"){val code=discount.text.toString().trim();if(code.isNotEmpty()&&code!="WELCOME10")notice("Discount code is not recognized.")else total.text="Total ${money(if(code=="WELCOME10")subtotal-subtotal*10/100 else subtotal)}"};val payments=listOf("tok_success","tok_declined","tok_capture_fail");val payment=picker("Mock payment result",listOf("Successful payment","Declined authorization","Capture failure"));text("This is the competition's mock gateway. No real card is charged.",13f);button("Pay with mock gateway"){val code=discount.text.toString().trim();val items=JSONArray();for((id,n)in cart)items.put(JSONObject().put("variant_id",id).put("quantity",n));val next=JSONObject().put("customer_id",user.getString("user_id")).put("items",items).put("discount_code",if(code.isBlank())JSONObject.NULL else code).put("payment",JSONObject().put("token",payments[payment.selectedItemPosition]));val old=orderRequest;if(old==null||JSONObject(old.toString()).apply{remove("order_id")}.toString()!=next.toString())orderRequest=next.put("order_id",UUID.randomUUID().toString());val request=orderRequest!!;local.put("checkout:$owner",request);confirm("Submit order for ${cart.values.sum()} items?\nSubtotal ${money(subtotal)}. Any discount is validated by the server."){work({val r=api.call("/audit/v1/checkout",request);local.put("receipt:$owner",r);local.put("checkout:$owner",JSONObject());local.put("cart:$owner",JSONObject());r},{r->cart.clear();orderRequest=null;receiptScreen(r)})}}}
fun MainActivity.receiptScreen(order:JSONObject){start("Paid receipt");card("✓ Payment captured","Order: ${order.optString("order_id")}\nStatus: ${order.optString("status").uppercase()}\nTotal: ${money(order.optLong("total_minor"))}");for(i in (order.optJSONArray("items")?:JSONArray()).objects())text("${i.optString("variant_id")} × ${i.optInt("quantity")}",14f);text("Signed server receipt",20f,true);try{val receipt=order.getJSONObject("receipt");val bytes=Base64.decode(receipt.getString("pdf_base64"),Base64.DEFAULT);require(String(bytes.take(4).toByteArray())=="%PDF");val file=File(filesDir,"receipt.pdf");file.writeBytes(bytes);val fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer(fd).use{renderer->for(index in 0 until renderer.pageCount){renderer.openPage(index).use{page->val width=(resources.displayMetrics.widthPixels-dp(32)).coerceAtLeast(320);val bmp=Bitmap.createBitmap(width,width*page.height/page.width,Bitmap.Config.ARGB_8888);bmp.eraseColor(android.graphics.Color.WHITE);page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);val image=ImageView(this).apply{setImageBitmap(bmp);adjustViewBounds=true;contentDescription="Server-generated signed PDF receipt, page ${index+1}"};content.addView(image,LinearLayout.LayoutParams(-1,-2))}}};text("Server signature: "+receipt.optString("signature"),11f)}catch(_:Exception){card("Receipt could not be rendered","The order is paid. Retry opening the saved receipt.");button("Retry receipt"){receiptScreen(order)}};button("Return to store"){storeScreen()}}
