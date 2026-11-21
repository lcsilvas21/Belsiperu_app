
package pe.belsiperu.cotizaciones

import android.app.*
import android.os.*
import android.content.*
import android.database.sqlite.*
import android.graphics.*
import android.net.Uri
import android.text.InputType
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class Store(ctx: Context): SQLiteOpenHelper(ctx,"belsiperu.db",null,2) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT,name TEXT,price REAL,currency TEXT,stock INTEGER,min_stock INTEGER,photo_uri TEXT)")
  db.execSQL("CREATE TABLE clients(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,document TEXT,address TEXT)")
  db.execSQL("CREATE TABLE quotes(id INTEGER PRIMARY KEY AUTOINCREMENT,client TEXT,product TEXT,qty INTEGER,unit_price REAL,discount REAL,igv INTEGER,currency TEXT,total REAL,status TEXT,created TEXT,product_photo TEXT,discount_amount REAL DEFAULT 0)")
  db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY AUTOINCREMENT,product TEXT,qty INTEGER,reason TEXT,created TEXT)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
  if(oldVersion<2) {
   db.execSQL("ALTER TABLE products ADD COLUMN photo_uri TEXT")
   db.execSQL("ALTER TABLE quotes ADD COLUMN product_photo TEXT")
   db.execSQL("ALTER TABLE quotes ADD COLUMN discount_amount REAL DEFAULT 0")
  }
 }
}
class MainActivity:Activity() {
 private lateinit var db:Store
 private lateinit var body:LinearLayout
 private val navy=Color.rgb(18,52,90)
 private val green=Color.rgb(20,138,104)
 private val gold=Color.rgb(213,165,74)
 private var pendingPdf:Long?=null
 private var pendingPhotoProduct:Long?=null
 private var pdfPaper="A4 vertical"
 private var pdfStyle="Moderno"
 private fun today()=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date())
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);db=Store(this); lock() }
 private fun screen(title:String) {
  val outer=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(243,246,249))}
  val head=TextView(this).apply {text="BELSIPERU  •  $title";textSize=21f;setTextColor(Color.WHITE);setBackgroundColor(navy);setPadding(24,38,18,30)}
  outer.addView(head)
  val scroll=ScrollView(this);body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,18,22,22)}
  scroll.addView(body);outer.addView(scroll);setContentView(outer)
 }
 private fun button(label:String,action:()->Unit) {
  val b=Button(this).apply{text=label;setTextColor(Color.WHITE);setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));setOnClickListener{action()}}
  body.addView(b)
 }
 private fun label(t:String) {body.addView(TextView(this).apply{text=t;textSize=17f;setTextColor(navy);setPadding(4,14,4,14)})}
 private fun input(hint:String,number:Boolean=false):EditText {
  val e=EditText(this).apply{this.hint=hint;setSingleLine(true);if(number) inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
  body.addView(e);return e
 }
 private fun choice(items:List<String>):Spinner {
  val s=Spinner(this);s.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items);body.addView(s);return s
 }
 private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
 private fun lock(){
  screen("Acceso seguro")
  label("Versión inicial: PIN local. La autenticación biométrica y el cifrado se incorporarán en una versión posterior.")
  val prefs=getSharedPreferences("security",MODE_PRIVATE)
  if(!prefs.contains("pin"))label("Crea tu PIN de 4 a 6 dígitos")
  val e=input("PIN")
  e.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
  button(if(prefs.contains("pin")) "Entrar" else "Crear PIN y entrar") {
   val pin=e.text.toString()
   if(!prefs.contains("pin")) {
    if(pin.length !in 4..6) toast("Usa entre 4 y 6 dígitos")
    else {prefs.edit().putString("pin",pin).apply();home()}
   } else if(pin==prefs.getString("pin",""))home() else toast("PIN incorrecto")
  }
 }
 private fun home(){
  screen("Inicio")
  label("Cotizaciones • Inventario • Clientes")
  button("＋ NUEVA COTIZACIÓN"){newQuote()}
  button("Productos / Catálogo"){products()}
  button("Clientes"){clients()}
  button("Historial de cotizaciones"){quotes()}
  button("Kardex y alertas"){inventory()}
  button("Bloquear"){lock()}
 }
 private fun products(){
  screen("Productos");button("← Inicio"){home()}
  val code=input("Código");val name=input("Descripción");val price=input("Precio base",true)
  val currency=choice(listOf("PEN","USD"));val stock=input("Stock inicial",true);val min=input("Stock mínimo",true)
  button("Guardar producto"){
   if(name.text.isBlank())toast("Indica el producto") else {
    val values=android.content.ContentValues().apply{
     put("code",code.text.toString());put("name",name.text.toString());put("price",price.text.toString().toDoubleOrNull()?:0.0)
     put("currency",currency.selectedItem.toString());put("stock",stock.text.toString().toIntOrNull()?:0);put("min_stock",min.text.toString().toIntOrNull()?:0)
    }
    db.writableDatabase.insert("products",null,values);products()
   }
  }
  val c=db.readableDatabase.rawQuery("SELECT id,code,name,price,currency,stock,min_stock,photo_uri FROM products ORDER BY id DESC",null)
  while(c.moveToNext()){
   val id=c.getLong(0)
   label("${c.getString(1)} • ${c.getString(2)}\n${c.getString(4)} ${c.getDouble(3)}  |  Stock ${c.getInt(5)}  |  Mín. ${c.getInt(6)}")
   button(if(c.isNull(7)) "Añadir fotografía" else "Cambiar fotografía"){selectPhoto(id)}
  }
  c.close()
 }
 private fun clients(){
  screen("Clientes");button("← Inicio"){home()}
  val name=input("Nombre o razón social");val doc=input("RUC o DNI");val addr=input("Dirección")
  button("Guardar cliente"){
   if(name.text.isBlank())toast("Indica nombre") else {
    db.writableDatabase.execSQL("INSERT INTO clients(name,document,address) VALUES(?,?,?)",arrayOf(name.text.toString(),doc.text.toString(),addr.text.toString()));clients()
   }
  }
  val c=db.readableDatabase.rawQuery("SELECT name,document,address FROM clients ORDER BY id DESC",null)
  while(c.moveToNext())label("${c.getString(0)} • ${c.getString(1)}\n${c.getString(2)}")
  c.close()
 }
 private fun newQuote(){
  screen("Nueva cotización");button("← Inicio"){home()}
  val clients=mutableListOf("Cliente general")
  val cc=db.readableDatabase.rawQuery("SELECT name FROM clients ORDER BY name",null)
  while(cc.moveToNext())clients.add(cc.getString(0));cc.close()
  label("Cliente");val client=choice(clients)
  val names=mutableListOf<String>();val ids=mutableListOf<Long>();val prices=mutableListOf<Double>();val currencies=mutableListOf<String>()
  val c=db.readableDatabase.rawQuery("SELECT id,name,price,currency FROM products ORDER BY name",null)
  while(c.moveToNext()){ids.add(c.getLong(0));names.add(c.getString(1));prices.add(c.getDouble(2));currencies.add(c.getString(3))};c.close()
  if(names.isEmpty()){label("Primero registra un producto.");button("Ir a productos"){products()};return}
  label("Producto");val product=choice(names)
  val qty=input("Cantidad",true);val price=input("Precio de venta (opcional)",true)
  val discount=input("Descuento en %",true)
  val fixedDiscount=input("Descuento adicional en monto",true)
  val currency=choice(listOf("PEN","USD"))
  val fx=input("Tipo de cambio: soles por dólar",true);fx.setText("3.75")
  val igv=CheckBox(this).apply{text="Agregar IGV 18 % (precios sin IGV)";isChecked=false};body.addView(igv)
  product.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){price.setText(prices[position].toString());currency.setSelection(if(currencies[position]=="USD")1 else 0)}
  }
  button("Guardar cotización"){
   val i=product.selectedItemPosition;val q=qty.text.toString().toIntOrNull()?:0
   val rate=fx.text.toString().toDoubleOrNull()?:0.0
   if(q<=0 || rate<=0){toast("Revisa cantidad y tipo de cambio");return@button}
   val base=price.text.toString().toDoubleOrNull()?:prices[i]
   val original=currencies[i];val target=currency.selectedItem.toString()
   val converted=if(original==target)base else if(original=="USD")base*rate else base/rate
   val d=(discount.text.toString().toDoubleOrNull()?:0.0).coerceIn(0.0,100.0)
   val fixed=(fixedDiscount.text.toString().toDoubleOrNull()?:0.0).coerceAtLeast(0.0)
   val subtotal=(q*converted*(1-d/100.0)-fixed).coerceAtLeast(0.0)
   val total=subtotal*(if(igv.isChecked)1.18 else 1.0)
   val photoCur=db.readableDatabase.rawQuery("SELECT photo_uri FROM products WHERE id=?",arrayOf(ids[i].toString()))
   val photo=if(photoCur.moveToFirst())photoCur.getString(0) else null
   photoCur.close()
   val values=android.content.ContentValues().apply{
    put("client",client.selectedItem.toString());put("product",names[i]);put("qty",q);put("unit_price",converted)
    put("discount",d);put("igv",if(igv.isChecked)1 else 0);put("currency",target);put("total",total)
    put("status","PENDIENTE");put("created",today());put("product_photo",photo);put("discount_amount",fixed)
   }
   val id=db.writableDatabase.insert("quotes",null,values)
   toast("Cotización COT-${id.toString().padStart(6,'0')} guardada")
   quotes()
  }
  label("Versión de desarrollo: una línea por cotización. Fotos, descuento fijo y formatos PDF disponibles; faltan varias líneas y maquetación avanzada.")
 }
 private fun quotes(){
  screen("Cotizaciones");button("← Inicio"){home()}
  val c=db.readableDatabase.rawQuery("SELECT id,client,product,qty,total,currency,status FROM quotes ORDER BY id DESC",null)
  while(c.moveToNext()){
   val id=c.getLong(0);val status=c.getString(6)
   label("COT-${id.toString().padStart(6,'0')} • $status\n${c.getString(1)}\n${c.getInt(3)} × ${c.getString(2)}\n${c.getString(5)} ${"%.2f".format(Locale.US,c.getDouble(4))}")
   button("Exportar PDF COT-${id}"){pdfOptions(id)}
   if(status=="PENDIENTE")button("Confirmar venta COT-${id}"){confirm(id)}
  };c.close()
 }
 private fun confirm(id:Long){
  AlertDialog.Builder(this).setTitle("Confirmar venta").setMessage("Se descontará el stock una sola vez. ¿Continuar?")
   .setNegativeButton("Cancelar",null).setPositiveButton("Confirmar"){_,_->
    val w=db.writableDatabase;w.beginTransaction()
    try{
     val c=w.rawQuery("SELECT product,qty,status FROM quotes WHERE id=?",arrayOf(id.toString()))
     if(c.moveToFirst() && c.getString(2)=="PENDIENTE"){
      val product=c.getString(0);val qty=c.getInt(1)
      w.execSQL("UPDATE products SET stock=stock-? WHERE name=?",arrayOf(qty,product))
      w.execSQL("INSERT INTO movements(product,qty,reason,created) VALUES(?,?,?,?)",arrayOf(product,-qty,"VENTA COT-$id",today()))
      w.execSQL("UPDATE quotes SET status='VENDIDA' WHERE id=?",arrayOf(id))
     };c.close();w.setTransactionSuccessful()
    }finally{w.endTransaction()}
    quotes()
   }.show()
 }
 private fun inventory(){
  screen("Kardex / Stock");button("← Inicio"){home()}
  val c=db.readableDatabase.rawQuery("SELECT name,stock,min_stock FROM products ORDER BY name",null)
  while(c.moveToNext()){val s=c.getInt(1);val m=c.getInt(2);label("${c.getString(0)}: $s unidades ${if(s<=m)"⚠ REPOSICIÓN SUGERIDA: ${m-s}" else ""}")}
  c.close()
  label("Últimos movimientos")
  val moves=db.readableDatabase.rawQuery("SELECT product,qty,reason,created FROM movements ORDER BY id DESC LIMIT 30",null)
  while(moves.moveToNext())label("${moves.getString(3)} • ${moves.getString(0)} • ${moves.getInt(1)} • ${moves.getString(2)}")
  moves.close()
 }
 private fun selectPhoto(id:Long){
  pendingPhotoProduct=id
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE);type="image/*"
  }
  startActivityForResult(intent,92)
 }
 private fun pdfOptions(id:Long){
  screen("Opciones PDF");button("← Cotizaciones"){quotes()}
  label("Diseño");val style=choice(listOf("Moderno","Corporativo","Prémium"))
  label("Tamaño de papel");val paper=choice(listOf("A4 vertical","A4 horizontal","A5 vertical","A5 horizontal","Ticket 80 mm"))
  button("Generar PDF"){
   pdfStyle=style.selectedItem.toString()
   pdfPaper=paper.selectedItem.toString()
   exportPdf(id)
  }
 }
 private fun exportPdf(id:Long){
  pendingPdf=id
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf";putExtra(Intent.EXTRA_TITLE,"BELSIPERU_COT-${id.toString().padStart(6,'0')}.pdf")}
  startActivityForResult(intent,91)
 }
 @Deprecated("Legacy activity result used for minimal SDK example")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==92 && resultCode==RESULT_OK && data?.data!=null){
   val id=pendingPhotoProduct?:return
   val uri=data.data!!
   try{
    contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val v=android.content.ContentValues().apply{put("photo_uri",uri.toString())}
    db.writableDatabase.update("products",v,"id=?",arrayOf(id.toString()))
    products()
   }catch(e:Exception){toast("No se pudo guardar la foto: ${e.message}")}
  }
  if(requestCode==91 && resultCode==RESULT_OK && data?.data!=null){
   val id=pendingPdf?:return
   try{contentResolver.openOutputStream(data.data!!)?.use{stream->writePdf(id,stream)};toast("PDF guardado")}catch(e:Exception){toast("Error PDF: ${e.message}")}
  }
 }
 private fun writePdf(id:Long,out:java.io.OutputStream){
  val c=db.readableDatabase.rawQuery("SELECT client,product,qty,unit_price,discount,igv,currency,total,created,product_photo,discount_amount FROM quotes WHERE id=?",arrayOf(id.toString()))
  if(!c.moveToFirst()){c.close();return}
  val size=when(pdfPaper){
   "A4 horizontal"->Pair(842,595)
   "A5 vertical"->Pair(420,595)
   "A5 horizontal"->Pair(595,420)
   "Ticket 80 mm"->Pair(227,650)
   else->Pair(595,842)
  }
  val w=size.first.toFloat();val h=size.second.toFloat()
  val compact=w<300f
  val margin=if(compact)12f else 28f
  val pdf=android.graphics.pdf.PdfDocument()
  val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(size.first,size.second,1).create())
  val canvas=page.canvas;val p=Paint(3)
  val main=when(pdfStyle){"Corporativo"->Color.rgb(18,52,90);"Prémium"->Color.rgb(12,39,58);else->Color.rgb(18,52,90)}
  p.color=main;canvas.drawRect(0f,0f,w,if(compact)100f else 120f,p)
  // Logo extraído del documento comercial de referencia.
  if(!compact) {
   try {
    assets.open("belsiperu_logo.png").use { stream ->
     val logo=BitmapFactory.decodeStream(stream)
     if(logo!=null) {
      canvas.drawBitmap(logo,null,RectF(w-margin-82f,12f,w-margin,91f),Paint(3))
      logo.recycle()
     }
    }
   }catch(_:Exception){}
  }
  p.color=Color.WHITE;p.textSize=if(compact)17f else 24f;p.isFakeBoldText=true
  canvas.drawText("BELSIPERU S.A.C.",margin,if(compact)33f else 48f,p)
  p.textSize=if(compact)9f else 12f;p.isFakeBoldText=false
  canvas.drawText("RUC 20605060634",margin,if(compact)52f else 73f,p)
  p.color=Color.rgb(213,165,74);p.textSize=if(compact)10f else 15f
  canvas.drawText("COT-${id.toString().padStart(6,'0')}",margin,if(compact)75f else 100f,p)
  // Marca de agua muy tenue.
  p.color=Color.argb(16,18,52,90);p.textSize=if(compact)29f else 65f;p.isFakeBoldText=true
  canvas.save();canvas.rotate(-25f,w/2,h/2)
  canvas.drawText("BELSIPERU",margin,h/2,p);canvas.restore()
  p.isFakeBoldText=false;p.color=main;p.textSize=if(compact)10f else 13f
  val top=if(compact)125f else 158f
  canvas.drawText("CLIENTE: ${c.getString(0).take(if(compact)25 else 52)}",margin,top,p)
  canvas.drawText("FECHA: ${c.getString(8)}",margin,top+23f,p)
  val bar=top+43f
  p.color=Color.rgb(20,138,104);canvas.drawRect(margin,bar,w-margin,bar+28f,p)
  p.color=Color.WHITE;p.textSize=if(compact)10f else 12f
  canvas.drawText("PRODUCTO",margin+8f,bar+19f,p)
  val productY=bar+52f
  p.color=main;p.textSize=if(compact)10f else 12f
  val product=c.getString(1)
  canvas.drawText(product.take(if(compact)27 else 55),margin+8f,productY,p)
  canvas.drawText("Cantidad: ${c.getInt(2)}",margin+8f,productY+23f,p)
  canvas.drawText("Precio unitario: ${c.getString(6)} ${"%.2f".format(Locale.US,c.getDouble(3))}",margin+8f,productY+44f,p)
  canvas.drawText("Descuento: ${c.getDouble(4)} % + ${"%.2f".format(Locale.US,c.getDouble(10))}",margin+8f,productY+65f,p)
  canvas.drawText("IGV: ${if(c.getInt(5)==1)"18 %" else "No aplicado"}",margin+8f,productY+86f,p)
  val photo=c.getString(9)
  if(!photo.isNullOrBlank() && !compact) {
   try{
    contentResolver.openInputStream(Uri.parse(photo))?.use{stream->
     val bitmap=BitmapFactory.decodeStream(stream)
     if(bitmap!=null){
      val maxSide=if(w<500)85f else 130f
      val ratio=minOf(maxSide/bitmap.width,maxSide/bitmap.height)
      val bw=bitmap.width*ratio;val bh=bitmap.height*ratio
      val rect=RectF(w-margin-bw,productY+8f,w-margin,productY+8f+bh)
      canvas.drawBitmap(bitmap,null,rect,Paint(3));bitmap.recycle()
     }
    }
   }catch(_:Exception){}
  }
  val totalY=minOf(h-82f,productY+145f)
  p.color=main;p.textSize=if(compact)14f else 20f;p.isFakeBoldText=true
  canvas.drawText("TOTAL ${c.getString(6)} ${"%.2f".format(Locale.US,c.getDouble(7))}",margin,totalY,p)
  p.isFakeBoldText=false;p.textSize=if(compact)8f else 10f
  if(!compact && h>560f) {
   p.color=main;p.textSize=9f;p.isFakeBoldText=false
   canvas.drawText("CONDICIÓN DE PAGO: CONTADO",margin,h-145f,p)
   canvas.drawText("Interbank soles: 20030088110506",margin,h-129f,p)
   canvas.drawText("Interbank dólares: 2003008810513",margin,h-113f,p)
   canvas.drawText("Entrega referencial: 48 horas para productos en stock, salvo venta previa.",margin,h-97f,p)
  }
  p.color=main;p.textSize=if(compact)8f else 10f
  canvas.drawText("Proforma comercial - no es comprobante tributario.",margin,h-35f,p)
  pdf.finishPage(page);pdf.writeTo(out);pdf.close();c.close()
 }
}
