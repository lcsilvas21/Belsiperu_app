
package pe.belsiperu.cotizaciones

import android.app.*
import android.os.*
import android.content.*
import android.database.sqlite.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import androidx.core.content.FileProvider
import android.graphics.Typeface
import android.view.Gravity
import android.net.Uri
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class Store(ctx: Context): SQLiteOpenHelper(ctx,"belsiperu.db",null,8) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT,name TEXT,price REAL,currency TEXT,stock INTEGER,min_stock INTEGER,photo_uri TEXT,kind TEXT NOT NULL DEFAULT 'PRODUCTO',barcode TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT '',brand TEXT NOT NULL DEFAULT '',unit TEXT NOT NULL DEFAULT 'UNIDADES',purchase_price REAL NOT NULL DEFAULT 0,sale_includes_igv INTEGER NOT NULL DEFAULT 0,purchase_includes_igv INTEGER NOT NULL DEFAULT 0,tax_affectation TEXT NOT NULL DEFAULT 'GRAVADO',isc_type TEXT NOT NULL DEFAULT 'NO APLICA',sunat_code TEXT NOT NULL DEFAULT '',icbper INTEGER NOT NULL DEFAULT 0,sell_without_stock INTEGER NOT NULL DEFAULT 0,weight REAL NOT NULL DEFAULT 0)")
  db.execSQL("CREATE TABLE product_price_variants(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,label TEXT NOT NULL,price REAL NOT NULL,FOREIGN KEY(product_id) REFERENCES products(id))")
  db.execSQL("CREATE TABLE clients(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,document TEXT,address TEXT,phone TEXT NOT NULL DEFAULT '',email TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',is_supplier INTEGER NOT NULL DEFAULT 0,is_driver INTEGER NOT NULL DEFAULT 0,is_carrier INTEGER NOT NULL DEFAULT 0)")
  db.execSQL("CREATE TABLE client_addresses(id INTEGER PRIMARY KEY AUTOINCREMENT,client_id INTEGER NOT NULL,label TEXT NOT NULL DEFAULT 'Principal',address TEXT NOT NULL,ubigeo TEXT NOT NULL DEFAULT '',is_main INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(client_id) REFERENCES clients(id))")
  db.execSQL("CREATE INDEX idx_client_addresses_client ON client_addresses(client_id)")
  db.execSQL("CREATE TABLE quotes(id INTEGER PRIMARY KEY AUTOINCREMENT,client TEXT,product TEXT,qty INTEGER,unit_price REAL,discount REAL,igv INTEGER,currency TEXT,total REAL,status TEXT,created TEXT,product_photo TEXT,discount_amount REAL DEFAULT 0,tax_affectation TEXT NOT NULL DEFAULT 'INAFECTO',product_id INTEGER,include_product_photo INTEGER NOT NULL DEFAULT 1,client_id INTEGER,series TEXT NOT NULL DEFAULT '0002',quote_number INTEGER)")
  db.execSQL("CREATE TABLE quote_sequences(series TEXT PRIMARY KEY,next_number INTEGER NOT NULL CHECK(next_number>0))")
  db.execSQL("INSERT INTO quote_sequences(series,next_number) VALUES('0002',1)")
  db.execSQL("CREATE UNIQUE INDEX idx_quotes_series_number ON quotes(series,quote_number)")
  db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY AUTOINCREMENT,product TEXT,qty INTEGER,reason TEXT,created TEXT)")
  db.execSQL("CREATE TABLE quote_items(id INTEGER PRIMARY KEY AUTOINCREMENT,quote_id INTEGER NOT NULL,product_id INTEGER,code TEXT NOT NULL DEFAULT '',name TEXT NOT NULL,unit TEXT NOT NULL DEFAULT 'UNIDADES',qty INTEGER NOT NULL,unit_price REAL NOT NULL,discount_pct REAL NOT NULL DEFAULT 0,discount_amount REAL NOT NULL DEFAULT 0,igv INTEGER NOT NULL DEFAULT 0,tax_affectation TEXT NOT NULL DEFAULT 'INAFECTO',line_total REAL NOT NULL,photo_uri TEXT,include_product_photo INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(quote_id) REFERENCES quotes(id))")
  db.execSQL("CREATE INDEX idx_quote_items_quote ON quote_items(quote_id)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
  if(oldVersion<2) {
   db.execSQL("ALTER TABLE products ADD COLUMN photo_uri TEXT")
   db.execSQL("ALTER TABLE quotes ADD COLUMN product_photo TEXT")
   db.execSQL("ALTER TABLE quotes ADD COLUMN discount_amount REAL DEFAULT 0")
  }
  if(oldVersion<3){
   val additions=listOf(
    "kind TEXT NOT NULL DEFAULT 'PRODUCTO'",
    "barcode TEXT NOT NULL DEFAULT ''",
    "category TEXT NOT NULL DEFAULT ''",
    "brand TEXT NOT NULL DEFAULT ''",
    "unit TEXT NOT NULL DEFAULT 'UNIDADES'",
    "purchase_price REAL NOT NULL DEFAULT 0",
    "sale_includes_igv INTEGER NOT NULL DEFAULT 0",
    "purchase_includes_igv INTEGER NOT NULL DEFAULT 0",
    "tax_affectation TEXT NOT NULL DEFAULT 'GRAVADO'",
    "isc_type TEXT NOT NULL DEFAULT 'NO APLICA'",
    "sunat_code TEXT NOT NULL DEFAULT ''",
    "icbper INTEGER NOT NULL DEFAULT 0",
    "sell_without_stock INTEGER NOT NULL DEFAULT 0",
    "weight REAL NOT NULL DEFAULT 0"
   )
   for(definition in additions)db.execSQL("ALTER TABLE products ADD COLUMN "+definition)
   db.execSQL("CREATE TABLE IF NOT EXISTS product_price_variants(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,label TEXT NOT NULL,price REAL NOT NULL,FOREIGN KEY(product_id) REFERENCES products(id))")
  }
  if(oldVersion<4){
   db.execSQL("ALTER TABLE quotes ADD COLUMN tax_affectation TEXT NOT NULL DEFAULT 'INAFECTO'")
   db.execSQL("ALTER TABLE quotes ADD COLUMN product_id INTEGER")
  }
  if(oldVersion<5){
   // Historial previo conserva su comportamiento original de incluir fotografías.
   db.execSQL("ALTER TABLE quotes ADD COLUMN include_product_photo INTEGER NOT NULL DEFAULT 1")
  }
  if(oldVersion<6){
   // No se eliminan clientes ni sus direcciones históricas.
   db.execSQL("ALTER TABLE clients ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE clients ADD COLUMN email TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE clients ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE clients ADD COLUMN is_supplier INTEGER NOT NULL DEFAULT 0")
   db.execSQL("ALTER TABLE clients ADD COLUMN is_driver INTEGER NOT NULL DEFAULT 0")
   db.execSQL("ALTER TABLE clients ADD COLUMN is_carrier INTEGER NOT NULL DEFAULT 0")
   db.execSQL("CREATE TABLE IF NOT EXISTS client_addresses(id INTEGER PRIMARY KEY AUTOINCREMENT,client_id INTEGER NOT NULL,label TEXT NOT NULL DEFAULT 'Principal',address TEXT NOT NULL,ubigeo TEXT NOT NULL DEFAULT '',is_main INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(client_id) REFERENCES clients(id))")
   db.execSQL("CREATE INDEX IF NOT EXISTS idx_client_addresses_client ON client_addresses(client_id)")
   // Las direcciones antes guardadas en clients.address quedan como direcciones principales.
   db.execSQL("INSERT INTO client_addresses(client_id,label,address,ubigeo,is_main) SELECT id,'Principal',address,'',1 FROM clients WHERE TRIM(COALESCE(address,''))<>''")
  }
  if(oldVersion<7){
   db.execSQL("CREATE TABLE IF NOT EXISTS quote_items(id INTEGER PRIMARY KEY AUTOINCREMENT,quote_id INTEGER NOT NULL,product_id INTEGER,code TEXT NOT NULL DEFAULT '',name TEXT NOT NULL,unit TEXT NOT NULL DEFAULT 'UNIDADES',qty INTEGER NOT NULL,unit_price REAL NOT NULL,discount_pct REAL NOT NULL DEFAULT 0,discount_amount REAL NOT NULL DEFAULT 0,igv INTEGER NOT NULL DEFAULT 0,tax_affectation TEXT NOT NULL DEFAULT 'INAFECTO',line_total REAL NOT NULL,photo_uri TEXT,include_product_photo INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(quote_id) REFERENCES quotes(id))")
   db.execSQL("CREATE INDEX IF NOT EXISTS idx_quote_items_quote ON quote_items(quote_id)")
   db.execSQL("ALTER TABLE quotes ADD COLUMN client_id INTEGER")
   // Los presupuestos antiguos tenían una sola línea; se conserva su detalle.
   db.execSQL("INSERT INTO quote_items(quote_id,product_id,code,name,unit,qty,unit_price,discount_pct,discount_amount,igv,tax_affectation,line_total,photo_uri,include_product_photo) SELECT q.id,q.product_id,COALESCE((SELECT p.code FROM products p WHERE p.id=q.product_id),''),q.product,'UNIDADES',q.qty,q.unit_price,q.discount,q.discount_amount,q.igv,q.tax_affectation,q.total,q.product_photo,q.include_product_photo FROM quotes q")
  }
  if(oldVersion<8){
   // No cambiar retroactivamente los números ya emitidos: los históricos
   // conservan serie 0002 y el identificador usado antes como correlativo.
   db.execSQL("ALTER TABLE quotes ADD COLUMN series TEXT NOT NULL DEFAULT '0002'")
   db.execSQL("ALTER TABLE quotes ADD COLUMN quote_number INTEGER")
   db.execSQL("UPDATE quotes SET quote_number=id WHERE quote_number IS NULL")
   db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_quotes_series_number ON quotes(series,quote_number)")
   db.execSQL("CREATE TABLE IF NOT EXISTS quote_sequences(series TEXT PRIMARY KEY,next_number INTEGER NOT NULL CHECK(next_number>0))")
   db.execSQL("INSERT OR IGNORE INTO quote_sequences(series,next_number) SELECT '0002',COALESCE(MAX(quote_number),0)+1 FROM quotes WHERE series='0002'")
  }
 }
}
class MainActivity:Activity() {
 private lateinit var db:Store
 private lateinit var security:AppSecurity
 private var sessionAuthenticated=false
 private var backgroundSince=0L
 private var pendingBackupPassword:CharArray?=null
 private lateinit var body:LinearLayout
 private val navy=Color.rgb(13,43,91)
 private val green=Color.rgb(14,122,77)
 private val gold=Color.rgb(212,175,55)
 private val pale=Color.rgb(246,249,247)
 private val ink=Color.rgb(27,46,60)
 private val muted=Color.rgb(103,119,130)
 private val border=Color.rgb(225,233,235)
 private var pendingPdf:Long?=null
 private var pendingCatalogExportTemplate=false
 private var pendingPhotoProduct:Long?=null
 private var pendingDraftPhotoUri:String?=null
 private var draftPhotoPreview:ImageView?=null
 private var pdfPaper="Carta vertical"
 private var pdfStyle="Proforma BELSIPERU"
 private var pdfExpiry=""
 private var pdfSeller="Asesor comercial"
 private var pdfPayment="CONTADO"
 private var companyLogoPreview:ImageView?=null
 private fun companyPrefs()=getSharedPreferences("belsiperu_company_pdf",MODE_PRIVATE)
 private data class CompanyPdfInfo(
  val ruc:String,val legal:String,val commercial:String,val fiscal:String,
  val business:String,val regime:String,val header:String,val banks:String,
  val footer:String,val decimals:Int
 )
 private fun companyInfo():CompanyPdfInfo{
  val prefs=companyPrefs()
  fun get(key:String,otherwise:String="")=prefs.getString(key,otherwise)?:otherwise
  return CompanyPdfInfo(
   get("ruc",""),
   get("legal","Mi negocio"),
   get("commercial",""),
   get("fiscal",""),
   get("business","Comercio y servicios"),
   get("regime",""),
   get("header","GESTIÓN COMERCIAL · COTIZACIONES · INVENTARIO"),
   get("banks",""),
   get("footer","Precios sujetos a confirmación y disponibilidad."),
   prefs.getInt("decimals",2).coerceIn(2,4)
  )
 }
 private fun quoteDisplayNumber(id:Long):String{
  val cursor=db.readableDatabase.rawQuery(
   "SELECT series,quote_number FROM quotes WHERE id=?",arrayOf(id.toString()))
  try{
   if(cursor.moveToFirst()){
    val serie=cursor.getString(0)?:"0002"
    val number=if(cursor.isNull(1))id else cursor.getLong(1)
    return serie+"-"+number.toString().padStart(7,'0')
   }
  }finally{cursor.close()}
  return "0002-"+id.toString().padStart(7,'0')
 }
 private fun activeQuoteSequence():Pair<String,Long>{
  val prefs=getSharedPreferences("quote_sequence",MODE_PRIVATE)
  val serie=prefs.getString("active_series","0002")?:"0002"
  val cursor=db.readableDatabase.rawQuery(
   "SELECT next_number FROM quote_sequences WHERE series=?",arrayOf(serie))
  try{if(cursor.moveToFirst())return Pair(serie,cursor.getLong(0))}
  finally{cursor.close()}
  return Pair(serie,1L)
 }
 private fun assignNextQuoteSequence(database:SQLiteDatabase):Pair<String,Long>{
  // Se llama DENTRO de la misma transacción que registra la cotización.
  val serie=getSharedPreferences("quote_sequence",MODE_PRIVATE)
   .getString("active_series","0002")?:"0002"
  val cursor=database.rawQuery(
   "SELECT next_number FROM quote_sequences WHERE series=?",arrayOf(serie))
  val configured=try{if(cursor.moveToFirst())cursor.getLong(0) else 1L}
   finally{cursor.close()}
  val latest=database.rawQuery(
   "SELECT COALESCE(MAX(quote_number),0) FROM quotes WHERE series=?",arrayOf(serie))
  val max=try{if(latest.moveToFirst())latest.getLong(0) else 0L}
   finally{latest.close()}
  val current=maxOf(configured,max+1L)
  if(current>9999999L)throw IllegalStateException("Se agotó el correlativo de la serie.")
  database.execSQL(
   "INSERT OR REPLACE INTO quote_sequences(series,next_number) VALUES(?,?)",
   arrayOf(serie,current+1L))
  return Pair(serie,current)
 }
 private fun pdfNumber(value:Double,decimals:Int=companyInfo().decimals)=
  String.format(Locale.US,"%,."+decimals+"f",value)
 private fun companyLogoFile()=java.io.File(filesDir,"company_pdf_logo.png")
 private fun loadCompanyLogoBitmap():Bitmap?{
  val file=companyLogoFile()
  try{
   if(file.isFile){
    val bmp=BitmapFactory.decodeFile(file.absolutePath)
    if(bmp!=null)return bmp
   }
  }catch(_:Exception){}catch(_:OutOfMemoryError){}
  return try{assets.open("belsiperu_logo.png").use{BitmapFactory.decodeStream(it)}}
   catch(_:Exception){null}catch(_:OutOfMemoryError){null}
 }
 // Corrige logos con márgenes blancos: detecta el dibujo real,
 // elimina solo el fondo blanco opaco y recorta el área sobrante.
 // No modifica el archivo original de configuración de la empresa.
 private fun logoSinFondo(source:Bitmap):Bitmap{
  val width=source.width;val height=source.height
  if(width<=1||height<=1)return source
  val data=IntArray(width*height)
  source.getPixels(data,0,width,0,0,width,height)
  val corners=intArrayOf(data[0],data[width-1],
   data[(height-1)*width],data[height*width-1])
  val whiteBackground=corners.count{
   Color.alpha(it)>=235&&Color.red(it)>=238&&
   Color.green(it)>=238&&Color.blue(it)>=238
  }>=3
  var minX=width;var minY=height;var maxX=-1;var maxY=-1
  for(y in 0 until height){
   val offset=y*width
   for(x in 0 until width){
    val color=data[offset+x]
    val visible=if(whiteBackground){
     Color.alpha(color)>20 &&
      minOf(Color.red(color),Color.green(color),Color.blue(color))<231
    }else Color.alpha(color)>20
    if(visible){
     if(x<minX)minX=x
     if(x>maxX)maxX=x
     if(y<minY)minY=y
     if(y>maxY)maxY=y
    }
   }
  }
  if(maxX<minX||maxY<minY)return source
  val padX=((maxX-minX+1)*0.035f).toInt().coerceAtLeast(2)
  val padY=((maxY-minY+1)*0.12f).toInt().coerceAtLeast(2)
  minX=(minX-padX).coerceAtLeast(0)
  maxX=(maxX+padX).coerceAtMost(width-1)
  minY=(minY-padY).coerceAtLeast(0)
  maxY=(maxY+padY).coerceAtMost(height-1)
  val cropW=maxX-minX+1;val cropH=maxY-minY+1
  val pixels=IntArray(cropW*cropH)
  for(y in 0 until cropH){
   for(x in 0 until cropW){
    val old=data[(minY+y)*width+minX+x]
    if(!whiteBackground){pixels[y*cropW+x]=old;continue}
    val minChannel=minOf(Color.red(old),Color.green(old),Color.blue(old))
    val opacity=((255-minChannel)*255/29).coerceIn(0,255)
    val a=Color.alpha(old)*opacity/255
    pixels[y*cropW+x]=(old and 0x00FFFFFF) or (a shl 24)
   }
  }
  return Bitmap.createBitmap(cropW,cropH,Bitmap.Config.ARGB_8888).apply{
   setPixels(pixels,0,cropW,0,0,cropW,cropH)
  }
 }
 private fun drawCompanyLogo(canvas:Canvas,rect:RectF,sinFondo:Boolean=false){
  // El logo de la plataforma no se usa como identidad del negocio.
  if(!companyLogoFile().isFile)return
  val original=loadCompanyLogoBitmap()?:return
  var processed:Bitmap?=null
  try{
   if(sinFondo){
    processed=try{logoSinFondo(original)}
     catch(_:Exception){original}catch(_:OutOfMemoryError){original}
   }
   drawProductImage(canvas,processed?:original,rect)
  }finally{
   if(processed!=null&&processed!==original)processed.recycle()
   original.recycle()
  }
 }
 private fun pdfTextLines(content:String,maxWidth:Float,size:Float,limit:Int=16):List<String>{
  if(content.isBlank())return emptyList()
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=size;typeface=Typeface.DEFAULT}
  val result=mutableListOf<String>()
  for(part in content.replace("\r","").split("\n")){
   if(part.isBlank()){if(result.size<limit)result.add("");continue}
   var current=""
   for(word in part.trim().split(Regex("\\s+"))){
    if(word.isEmpty())continue
    val attempt=if(current.isEmpty())word else current+" "+word
    if(current.isNotEmpty()&&p.measureText(attempt)>maxWidth){
     result.add(current);current=""
    }
    if(p.measureText(word)>maxWidth){
     var remaining=word
     while(remaining.isNotEmpty()){
      var i=1
      while(i<remaining.length&&p.measureText(remaining.take(i+1))<=maxWidth)i++
      result.add(remaining.take(i))
      remaining=remaining.drop(i)
      if(result.size>=limit)return result
     }
    }else current=if(current.isEmpty())word else current+" "+word
    if(result.size>=limit)return result
   }
   if(current.isNotEmpty())result.add(current)
   if(result.size>=limit)return result
  }
  return result.take(limit)
 }
 private fun addBankContinuationPages(pdf:android.graphics.pdf.PdfDocument,
  remaining:List<String>,pageW:Int,pageH:Int,fromPage:Int=2){
  if(remaining.isEmpty())return
  val isTicket=pageW<300
  val margin=if(isTicket)12f else 28f
  val pointSize=if(isTicket)7f else 9f
  val step=if(isTicket)12f else 15f
  val available=pageW.toFloat()-margin*2
  val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{
   color=Color.BLACK;textSize=pointSize
  }
  var idx=0;var pageNumber=fromPage
  while(idx<remaining.size){
   val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(
    pageW,pageH,pageNumber++).create())
   val canvas=page.canvas
   text.typeface=Typeface.DEFAULT_BOLD;text.textSize=if(isTicket)10f else 15f
   canvas.drawText("CUENTAS BANCARIAS · CONTINUACIÓN",margin,42f,text)
   text.typeface=Typeface.DEFAULT;text.textSize=pointSize
   var y=68f
   while(idx<remaining.size&&y<pageH-65f){
    val value=remaining[idx++]
    // Los datos se imprimen exactamente como fueron ingresados; no se inventan bancos.
    val safe=pdfTextLines(value,available,pointSize,30)
    for(line in safe){
     if(y>=pageH-65f){idx--;break}
     canvas.drawText(line,margin,y,text);y+=step
    }
   }
   text.typeface=Typeface.DEFAULT;text.textSize=if(isTicket)6f else 8f
   canvas.drawText("Documento comercial - no es comprobante tributario.",margin,
    pageH-20f,text)
   pdf.finishPage(page)
  }
 }
 private fun settingTextArea(title:String,value:String,lines:Int=4):EditText{
  label(title)
  val e=EditText(this).apply{
   textSize=15f;setTextColor(ink);setHintTextColor(muted)
   setPadding(d(12),d(12),d(12),d(12))
   setSingleLine(false);minLines=lines;maxLines=12;gravity=Gravity.TOP
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
   background=round(Color.WHITE,12,border)
   setText(value)
  }
  body.addView(e,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(4),0,d(8))})
  return e
 }
 private fun today()=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date())

 // Carga la foto del catálogo más reciente; para cotizaciones antiguas usa
 // la foto guardada con la propia cotización como respaldo.
 private fun loadQuoteProductBitmap(quoteId:Long):Bitmap?{
  val candidates=mutableListOf<String>()
  var productName=""
  var productId:Long?=null
  val snapshot=db.readableDatabase.rawQuery(
   "SELECT product,product_photo,product_id,include_product_photo FROM quotes WHERE id=?",arrayOf(quoteId.toString()))
  try{
   if(snapshot.moveToFirst()){
    // La elección de cada cotización prevalece sobre las fotos existentes en el catálogo.
    if(snapshot.getInt(3)==0)return null
    productName=snapshot.getString(0)?:""
    val previous=snapshot.getString(1)
    if(!snapshot.isNull(2))productId=snapshot.getLong(2)
    if(!previous.isNullOrBlank())candidates.add(previous)
   }
  }finally{snapshot.close()}
  if(productName.isNotBlank()||productId!=null){
   val catalog=if(productId!=null)
    db.readableDatabase.rawQuery("SELECT photo_uri FROM products WHERE id=? LIMIT 1",
      arrayOf(productId.toString()))
   else db.readableDatabase.rawQuery(
    "SELECT photo_uri FROM products WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(productName))
   try{
    if(catalog.moveToFirst()){
     val current=catalog.getString(0)
     if(!current.isNullOrBlank()){
      candidates.remove(current)
      candidates.add(0,current)
     }
    }
   }finally{catalog.close()}
  }
  for(path in candidates){
   try{
    val uri=Uri.parse(path)
    if(uri.scheme!="content")continue
    val dimensions=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,dimensions)}
    if(dimensions.outWidth<=0 || dimensions.outHeight<=0)continue
    var sample=1
    while(dimensions.outWidth/sample>1280 || dimensions.outHeight/sample>1280)sample*=2
    val options=BitmapFactory.Options().apply{inSampleSize=sample}
    val picture=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
    if(picture!=null)return picture
   }catch(_:Exception){
    // La imagen puede haber sido eliminada o perder permisos; probar otra ruta.
   }catch(_:OutOfMemoryError){
    // Nunca se debe interrumpir la cotización por una imagen demasiado grande.
   }
  }
  return null
 }
 private fun drawProductImage(canvas:Canvas,bitmap:Bitmap,box:RectF){
  if(bitmap.isRecycled || bitmap.width<=0 || bitmap.height<=0)return
  val padding=2f
  val innerW=(box.width()-padding*2).coerceAtLeast(1f)
  val innerH=(box.height()-padding*2).coerceAtLeast(1f)
  val scale=minOf(innerW/bitmap.width.toFloat(),innerH/bitmap.height.toFloat())
  val dw=bitmap.width*scale
  val dh=bitmap.height*scale
  val cx=box.centerX();val cy=box.centerY()
  val target=RectF(cx-dw/2f,cy-dh/2f,cx+dw/2f,cy+dh/2f)
  val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  canvas.save()
  canvas.clipRect(box)
  canvas.drawBitmap(bitmap,null,target,paint)
  canvas.restore()
 }


 private fun d(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
 private fun round(c:Int,r:Int=16,edge:Int?=null)=GradientDrawable().apply{
  shape=GradientDrawable.RECTANGLE
  setColor(c)
  cornerRadius=d(r).toFloat()
  if(edge!=null)setStroke(d(1),edge)
 }
 private fun line(t:String,size:Float=15f,color:Int=ink,bold:Boolean=false)=TextView(this).apply{
  text=t; textSize=size; setTextColor(color)
  typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
  setLineSpacing(d(2).toFloat(),1.02f)
 }
 // Logo de cabecera: respeta el logo configurado, recorta el fondo y
 // agranda la marca sin estirarla. La opción no funciona en acceso con PIN.
 private fun logo(goHome:Boolean)=ImageView(this).apply{
  try{
   val original=loadCompanyLogoBitmap()
   if(original!=null){
    val prepared=try{logoSinFondo(original)}catch(_:Exception){original}
    setImageBitmap(prepared)
    if(prepared!==original)original.recycle()
   }
  }catch(_:Exception){}
  scaleType=ImageView.ScaleType.FIT_CENTER
  background=round(Color.WHITE,13)
  setPadding(d(3),d(3),d(3),d(3))
  contentDescription=if(goHome)"Logo BELSIPERU: volver al Inicio" else "Logotipo BELSIPERU"
  if(goHome){
   isClickable=true
   isFocusable=true
   setOnClickListener{home()}
  }
 }
 private fun card()=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL;setPadding(d(15),d(15),d(15),d(15))
  background=round(Color.WHITE,16,border)
  elevation=d(2).toFloat()
 }
 private fun heading(title:String,detail:String=""){
  body.addView(line(title,18f,navy,true),LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(17),0,d(5))})
  if(detail.isNotEmpty())body.addView(line(detail,12f,muted))
 }
 private fun info(title:String,detail:String,highlight:Int=green){
  val c=card()
  c.addView(line(title,15f,highlight,true))
  c.addView(line(detail,13f,muted))
  body.addView(c,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(7),0,d(5))})
 }
 private fun tile(label:String,sub:String,icon:Int,action:()->Unit)=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL
  gravity=Gravity.CENTER
  setPadding(d(9),d(15),d(9),d(12))
  background=round(Color.WHITE,17,border)
  elevation=d(2).toFloat()
  isClickable=true;isFocusable=true;setOnClickListener{action()}
  addView(ImageView(this@MainActivity).apply{setImageResource(icon);setColorFilter(green)},
    LinearLayout.LayoutParams(d(32),d(32)))
  addView(line(label,15f,navy,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(10),0,0)})
  addView(line(sub,11f,muted).apply{gravity=Gravity.CENTER})
 }
 private fun tileRow(a:LinearLayout,b:LinearLayout){
  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  row.addView(a,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(0,d(5),d(5),d(8))})
  row.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(d(5),d(5),0,d(8))})
  body.addView(row)
 }
 private fun navItem(parent:LinearLayout,name:String,icon:Int,selected:Boolean,action:()->Unit){
  val col=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
   isClickable=true;isFocusable=true;setOnClickListener{action()}
   contentDescription=name
  }
  col.addView(ImageView(this).apply{setImageResource(icon);setColorFilter(if(selected)green else muted)},LinearLayout.LayoutParams(d(22),d(22)))
  col.addView(line(name,10f,if(selected)green else muted,selected).apply{gravity=Gravity.CENTER;setSingleLine(true)})
  parent.addView(col,LinearLayout.LayoutParams(0,-1,1f))
 }
 private fun queryCount(sql:String):Int{
  val cur=db.readableDatabase.rawQuery(sql,null)
  val n=if(cur.moveToFirst())cur.getInt(0) else 0
  cur.close();return n
 }

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=navy
  db=Store(this)
  security=AppSecurity(this)
  lock()
 }
 override fun onStop(){
  super.onStop()
  backgroundSince=android.os.SystemClock.elapsedRealtime()
 }
 override fun onStart(){
  super.onStart()
  if(backgroundSince>0 && sessionAuthenticated &&
   android.os.SystemClock.elapsedRealtime()-backgroundSince>90_000L){
   sessionAuthenticated=false
   lock()
  }
  backgroundSince=0L
 }

 private fun screen(title:String) {
  val login=title=="Acceso seguro" || !sessionAuthenticated
  val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(navy)}
  if(Build.VERSION.SDK_INT>=35){
   outer.setOnApplyWindowInsetsListener{_,ins->
    val bars=ins.getInsets(WindowInsets.Type.systemBars())
    outer.setPadding(bars.left,bars.top,bars.right,bars.bottom)
    ins
   }
  }
  val top=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   setPadding(d(15),d(13),d(15),d(15));setBackgroundColor(navy)
  }
  top.addView(logo(!login),LinearLayout.LayoutParams(d(123),d(65)))
  val names=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(d(12),0,0,0)
  }
  names.addView(line(if(login)"Acceso seguro" else title,19f,Color.WHITE,true))
  names.addView(line("GESTIÓN COMERCIAL",11f,Color.rgb(207,230,232)))
  top.addView(names,LinearLayout.LayoutParams(0,-2,1f))
  outer.addView(top)
  val scroll=ScrollView(this).apply{setBackgroundColor(pale);isFillViewport=true}
  body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(17),d(16),d(17),d(25))}
  scroll.addView(body)
  outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  if(!login){
   val nav=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;setPadding(d(8),d(7),d(8),d(5));gravity=Gravity.CENTER
    background=round(Color.WHITE,0)
   }
   navItem(nav,"Inicio",android.R.drawable.ic_menu_view,title=="Inicio"){home()}
   navItem(nav,"Cotizaciones",android.R.drawable.ic_menu_edit,title=="Cotizaciones"){quotes()}
   navItem(nav,"Productos",android.R.drawable.ic_menu_agenda,title=="Productos"){products()}
   navItem(nav,"Inventario",android.R.drawable.ic_menu_sort_by_size,title.contains("Kardex")){inventory()}
   outer.addView(nav,LinearLayout.LayoutParams(-1,d(65)))
  }
  setContentView(outer)
 }
 private fun button(label:String,action:()->Unit){
  val b=line(label,15f,Color.WHITE,true).apply{
   gravity=Gravity.CENTER;minHeight=d(52)
   setPadding(d(12),d(13),d(12),d(13))
   background=round(green,13)
   isClickable=true;isFocusable=true;setOnClickListener{action()}
   elevation=d(2).toFloat()
  }
  body.addView(b,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(9),0,d(5))})
 }
 private fun label(t:String){
  body.addView(line(t,15f,navy,true),LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(12),0,d(7))})
 }
 private fun input(hint:String,number:Boolean=false):EditText{
  val e=EditText(this).apply{
   this.hint=hint;setSingleLine(true);textSize=15f;setTextColor(ink)
   setHintTextColor(muted);setPadding(d(13),d(9),d(13),d(9))
   background=round(Color.WHITE,12,border)
   if(number)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
   minHeight=d(50)
  }
  body.addView(e,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(3),0,d(7))});return e
 }
 private fun choice(items:List<String>):Spinner {
  val s=Spinner(this);s.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items);body.addView(s);return s
 }
 private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()

 // Google Code Scanner muestra la cámara y reconoce códigos sin pedir
 // permiso CAMERA a la app. El módulo puede descargarse la primera vez.
 private fun scanBarcode(target:EditText){
  val options=GmsBarcodeScannerOptions.Builder()
   .enableAutoZoom()
   .build()
  GmsBarcodeScanning.getClient(this,options).startScan()
   .addOnSuccessListener{scanned->
    val value=scanned.rawValue?.trim().orEmpty()
    if(value.isEmpty()){
     toast("No se pudo leer un código. Intenta de nuevo.")
    }else{
     target.setText(value)
     target.setSelection(value.length)
     toast("Código de barras leído correctamente")
    }
   }
   .addOnCanceledListener{
    // Conservar el valor que hubiera escrito el usuario.
   }
   .addOnFailureListener{
    toast("No se pudo abrir el escáner. Verifica Google Play Services e inténtalo con conexión la primera vez.")
   }
 }
 private fun securePinField(hint:String):EditText=
  input(hint).apply{
   inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
   filters=arrayOf(android.text.InputFilter.LengthFilter(6))
  }
 private fun unlockHome(){
  sessionAuthenticated=true
  home()
  if(!security.hasRecoveryCode()){
   try{showRecoveryCode(security.rotateRecoveryCode(),
    "Guarda tu código de emergencia")}
   catch(e:Exception){toast("No se pudo generar el código: "+(e.message?:"Error"))}
  }
 }
 private fun showRecoveryCode(code:String,title:String){
  AlertDialog.Builder(this).setTitle(title)
   .setMessage("CÓDIGO DE EMERGENCIA:\n\n"+code+
    "\n\nEs de un solo uso y permite crear un PIN nuevo si lo olvidas. "+
    "Guárdalo FUERA de este celular, por ejemplo en un gestor de contraseñas o en papel. "+
    "No se podrá volver a consultar; sí podrás generar otro desde Configuración tras verificar tu PIN.")
   .setCancelable(false)
   .setPositiveButton("Ya guardé el código"){_,_->if(sessionAuthenticated)home() else lock()}
   .show()
 }
 private fun lock(){
  sessionAuthenticated=false
  screen("Acceso seguro")
  heading("Protección BELSIPERU",
   "PIN privado de 4 a 6 dígitos. El acceso con huella se puede activar después en Configuración.")
  if(!security.hasPin()){
   label("Crear PIN")
   val first=securePinField("Nuevo PIN de 4 a 6 dígitos")
   label("Confirmar PIN")
   val second=securePinField("Repite el PIN")
   button("Crear PIN y entrar"){
    if(first.text.toString()!=second.text.toString()){
     toast("Los PIN no coinciden.");return@button
    }
    try{
     val code=security.initialize(first.text.toString())
     first.text.clear();second.text.clear()
     sessionAuthenticated=true
     showRecoveryCode(code,"Tu código de recuperación: guárdalo ahora")
    }catch(e:Exception){toast(e.message?:"PIN inválido")}
   }
   return
  }
  label("PIN de acceso")
  val pin=securePinField("Ingresa tu PIN")
  button("Entrar"){
   val wait=security.lockedSeconds()
   if(wait>0){
    toast("Demasiados intentos. Inténtalo en "+wait+" segundos.")
    return@button
   }
   if(security.verifyPin(pin.text.toString())){
    pin.text.clear()
    unlockHome()
   }else{
    pin.text.clear()
    val delay=security.lockedSeconds()
    toast(if(delay>0)"Acceso bloqueado durante "+delay+" segundos."
     else "PIN incorrecto")
   }
  }
  if(security.isBiometricEnabled()){
   button("◉ Entrar con huella digital"){authenticateBiometric(false)}
  }
  button("¿Olvidaste tu PIN?"){recoverPinScreen()}
  button("Privacidad y tratamiento de datos"){privacyNotice()}
 }
 private fun recoverPinScreen(){
  sessionAuthenticated=false
  screen("Recuperar PIN")
  button("← Volver al acceso"){lock()}
  if(!security.hasRecoveryCode()){
   info("No se configuró un código de emergencia",
    "Esta instalación no tiene recuperación segura activada. "+
    "No borres datos ni desinstales la aplicación: podrías perder el inventario. "+
    "El correo no puede restablecer el PIN sin un servicio de verificación previamente configurado.",
    Color.rgb(173,105,19))
   return
  }
  heading("Restablecer PIN con código de emergencia",
   "Introduce el código que recibiste cuando creaste o actualizaste tu acceso.")
  label("Código de emergencia")
  val code=input("XXXXX-XXXXX-XXXXX-XXXXX")
  code.filters=arrayOf(android.text.InputFilter.LengthFilter(27))
  label("PIN nuevo")
  val p1=securePinField("PIN nuevo de 4 a 6 números")
  label("Confirmar PIN nuevo")
  val p2=securePinField("Repite el PIN")
  button("Restablecer PIN"){
   if(p1.text.toString()!=p2.text.toString()){
    toast("Los PIN no coinciden.");return@button
   }
   if(security.lockedSeconds()>0){
    toast("Demasiados intentos; inténtalo más tarde.");return@button
   }
   try{
    val next=security.recoverPin(code.text.toString(),p1.text.toString())
    if(next==null){
     toast("Código no válido o PIN fuera de rango.");return@button
    }
    code.text.clear();p1.text.clear();p2.text.clear()
    sessionAuthenticated=true
    showRecoveryCode(next,"PIN restablecido: guarda tu NUEVO código")
   }catch(e:Exception){toast("No se pudo recuperar: "+(e.message?:"Error"))}
  }
 }

 private fun home(){
  if(!sessionAuthenticated){lock();return}
  if(!companyConfigured()){companySetup();return}
  screen("Inicio")
  val hero=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(d(19),d(20),d(19),d(20))
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(navy,Color.rgb(8,87,93))).apply{cornerRadius=d(19).toFloat()}
  }
  hero.addView(line("¡Hola! Bienvenido a "+welcomeDisplayName(),21f,Color.WHITE,true))
  hero.addView(line("Tu negocio organizado, desde un solo lugar.",13f,Color.WHITE))
  body.addView(hero)
  button("＋  NUEVA COTIZACIÓN"){newQuote()}
  heading("Accesos rápidos","Herramientas para administrar tu negocio")
  tileRow(tile("Cotizaciones","Historial y PDF",android.R.drawable.ic_menu_edit){quotes()},tile("Productos","Catálogo y fotos",android.R.drawable.ic_menu_agenda){products()})
  tileRow(tile("Inventario","Stock y Kardex",android.R.drawable.ic_menu_sort_by_size){inventory()},tile("Clientes","Datos y contactos",android.R.drawable.ic_menu_myplaces){clients()})
  tileRow(tile("Compras","Ingresos de stock",android.R.drawable.ic_input_add){purchases()},tile("Reportes","Resumen de gestión",android.R.drawable.ic_menu_info_details){reports()})
  heading("Resumen del negocio")
  val q=queryCount("SELECT COUNT(*) FROM quotes")
  val p=queryCount("SELECT COUNT(*) FROM products")
  val low=queryCount("SELECT COUNT(*) FROM products WHERE kind='PRODUCTO' AND min_stock>0 AND stock<=min_stock")
  tileRow(tile(q.toString()+" cotizaciones","Historial guardado",android.R.drawable.ic_menu_edit){quotes()},tile(p.toString()+" productos","En catálogo",android.R.drawable.ic_menu_agenda){products()})
  if(low>0)info("Atención: "+low+" producto(s) con stock bajo","Revisa Inventario para reponer existencias.",Color.rgb(169,105,19))
  button("⚙  Configuración"){settings()}
  body.addView(line("BELSIPERU Gestión Comercial · Datos locales y copias de seguridad",11f,muted).apply{gravity=Gravity.CENTER})
 }
 private fun purchases(){
  screen("Compras")
  heading("Ingreso de mercadería","Registro básico de stock y Kardex")
  val ids=mutableListOf<Long>();val names=mutableListOf<String>()
  val cur=db.readableDatabase.rawQuery("SELECT id,name FROM products WHERE kind='PRODUCTO' ORDER BY name",null)
  while(cur.moveToNext()){ids.add(cur.getLong(0));names.add(cur.getString(1))}
  cur.close()
  if(names.isEmpty()){info("Sin productos","Registra primero un producto en el catálogo.");button("Ir a productos"){products()};return}
  label("Producto");val product=choice(names)
  val qty=input("Unidades recibidas",true)
  val reason=input("Referencia de compra o proveedor")
  button("Registrar ingreso"){
   val count=qty.text.toString().toIntOrNull()?:0
   if(count<=0){toast("Ingresa una cantidad válida");return@button}
   val idx=product.selectedItemPosition
   val database=db.writableDatabase;database.beginTransaction()
   try{
    database.execSQL("UPDATE products SET stock=stock+? WHERE id=?",arrayOf(count,ids[idx]))
    database.execSQL("INSERT INTO movements(product,qty,reason,created) VALUES(?,?,?,?)",arrayOf(names[idx],count,"INGRESO: "+reason.text.toString(),today()))
    database.setTransactionSuccessful()
   }finally{database.endTransaction()}
   toast("Ingreso registrado correctamente");inventory()
  }
  info("Módulo inicial","Las órdenes de compra y los proveedores completos aún están en desarrollo.",Color.rgb(169,105,19))
 }
 private fun reports(){
  screen("Reportes")
  heading("Resumen comercial","Indicadores básicos guardados en este teléfono")
  val q=queryCount("SELECT COUNT(*) FROM quotes")
  val sold=queryCount("SELECT COUNT(*) FROM quotes WHERE status='VENDIDA'")
  val units=queryCount("SELECT COALESCE(SUM(stock),0) FROM products WHERE kind='PRODUCTO'")
  val low=queryCount("SELECT COUNT(*) FROM products WHERE kind='PRODUCTO' AND min_stock>0 AND stock<=min_stock")
  info("Cotizaciones",q.toString()+" registros")
  info("Ventas confirmadas",sold.toString()+" operaciones")
  info("Unidades en stock",units.toString()+" unidades")
  info("Productos con stock bajo",low.toString()+" alertas",if(low>0)Color.rgb(169,105,19) else green)
  button("📦 Inventario: listado, PDF y Excel"){stockReportScreen()}
  button("📋 Kárdex: movimientos registrados"){inventoryMovementsScreen()}
  info("Próximamente","Los reportes de rentabilidad se incorporarán más adelante.")
 }
 private fun settings(){
  screen("Configuración")
  heading("Datos de la empresa e impresión",
   "Personaliza el encabezado, el logotipo y la información comercial de las proformas PDF.")
  button("🏢 Empresa · Datos para el encabezado"){companySettings()}
  button("🖨 Impresión · Bancos, cabecera y pie de página"){printingSettings()}
  button("🔢 Numeración automática de cotizaciones"){quoteNumberSettings()}
  button("👋 Personalizar mensaje de bienvenida"){welcomeMessageSettings()}
  button("🔐 Seguridad y recuperación"){securitySettings()}
  button("🔏 Privacidad y datos locales"){privacyNotice()}
  heading("Otros ajustes")
  button("Configurar consulta RUC / DNI"){rucSettings()}
  info("Protección de datos","Mantén protegidas tus claves y haz copias de seguridad del catálogo. Esta APK de prueba guarda su configuración en el dispositivo.",Color.rgb(169,105,19))
  button("Bloquear aplicación"){lock()}
  button("Volver al inicio"){home()}
 }

 // Texto mostrado solo en la tarjeta de inicio; no cambia la razón social,
 // el logotipo, la numeración ni los datos impresos en PDF.

 private fun securitySettings(){
  if(!sessionAuthenticated){lock();return}
  screen("Seguridad y recuperación")
  button("← Configuración"){settings()}
  heading("Seguridad de acceso",
   "Administra tu PIN, huella y código de recuperación sin afectar las cotizaciones.")
  info("Protección local",
   "Tu PIN y el código de emergencia se almacenan como verificadores derivados con sal aleatoria. "+
   "Tras varios intentos fallidos, el acceso se bloquea temporalmente.")
  button("🔑 Cambiar PIN"){changeAccessPinScreen()}
  button("🛟 Regenerar código de emergencia"){recoveryCodeSettings()}
  if(android.os.Build.VERSION.SDK_INT>=28){
   button(if(security.isBiometricEnabled())"◉ Desactivar huella digital" else
    "◉ Activar huella digital"){
    if(security.isBiometricEnabled())disableBiometricScreen()
    else activateBiometricScreen()
   }
  }else info("Huella no disponible",
   "Esta función requiere Android 9 o posterior y biometría fuerte configurada en el teléfono.")
  heading("Copias de seguridad",
   "Exporta una copia cifrada del inventario, clientes, cotizaciones, ajustes y fotos disponibles.")
  button("⬇ Crear respaldo cifrado (.belsbak)"){backupExportScreen()}
  button("⬆ Restaurar respaldo cifrado"){backupRestoreScreen()}
  info("Precaución antes de restaurar",
   "La restauración reemplaza los datos comerciales actuales de esta instalación. "+
   "Conserva tu PIN y código de recuperación actual. Guarda primero una copia propia.")
  heading("Correo de recuperación","Preparación para un servicio de verificación futuro")
  val emailPrefs=getSharedPreferences("belsiperu_recovery_contact",MODE_PRIVATE)
  val email=input("Correo de contacto")
  email.inputType=InputType.TYPE_CLASS_TEXT or
   InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
  email.setText(emailPrefs.getString("email","")?:"")
  button("Guardar correo de contacto"){
   val value=email.text.toString().trim()
   if(value.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()){
    email.error="Introduce un correo válido.";return@button
   }
   emailPrefs.edit().putString("email",value).apply()
   toast("Correo de contacto guardado. No está habilitado para recuperar el PIN.")
  }
  info("Recuperación por correo todavía no operativa",
   "Guardar un correo no verifica su titularidad ni envía códigos. "+
   "Para restablecer un PIN por email se necesita un servicio remoto de verificación seguro. "+
   "Por ahora utiliza el código de emergencia o la huella activada previamente.",
   Color.rgb(171,106,17))
 }
 private fun changeAccessPinScreen(){
  screen("Cambiar PIN")
  button("← Seguridad"){securitySettings()}
  heading("Actualizar PIN","El PIN anterior es obligatorio.")
  label("PIN actual");val old=securePinField("PIN actual")
  label("Nuevo PIN");val fresh=securePinField("Nuevo PIN de 4 a 6 dígitos")
  label("Confirma PIN nuevo");val confirmed=securePinField("Repite el PIN")
  button("✓ Cambiar PIN"){
   if(fresh.text.toString()!=confirmed.text.toString()){
    toast("Los nuevos PIN no coinciden.");return@button
   }
   if(security.changePin(old.text.toString(),fresh.text.toString())){
    old.text.clear();fresh.text.clear();confirmed.text.clear()
    toast("PIN actualizado correctamente")
    securitySettings()
   }else toast("No se pudo cambiar: verifica el PIN anterior y los dígitos.")
  }
 }
 private fun recoveryCodeSettings(){
  screen("Código de emergencia")
  button("← Seguridad"){securitySettings()}
  heading("Generar código nuevo",
   "El código anterior dejará de funcionar. Solo se mostrará una vez.")
  label("Confirma tu PIN actual")
  val pin=securePinField("PIN actual")
  button("Generar nuevo código"){
   if(!security.verifyPin(pin.text.toString())){
    toast("PIN incorrecto o acceso temporalmente bloqueado.");return@button
   }
   pin.text.clear()
   try{showRecoveryCode(security.rotateRecoveryCode(),"Tu nuevo código de emergencia")}
   catch(e:Exception){toast("No se pudo guardar el nuevo código: "+(e.message?:"Error"))}
  }
 }
 private fun biometricIsAvailable():Boolean{
  if(android.os.Build.VERSION.SDK_INT<28)return false
  return try{
   if(android.os.Build.VERSION.SDK_INT>=30){
    val manager=getSystemService(android.hardware.biometrics.BiometricManager::class.java)
    manager?.canAuthenticate(
     android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)==
     android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
   }else if(android.os.Build.VERSION.SDK_INT>=29){
    @Suppress("DEPRECATION")
    val manager=getSystemService(android.hardware.biometrics.BiometricManager::class.java)
    @Suppress("DEPRECATION")
    manager?.canAuthenticate()==android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
   }else{
    @Suppress("DEPRECATION")
    val manager=getSystemService(Context.FINGERPRINT_SERVICE) as?
     android.hardware.fingerprint.FingerprintManager
    @Suppress("DEPRECATION")
    manager?.isHardwareDetected==true && manager?.hasEnrolledFingerprints()==true
   }
  }catch(_:Exception){false}
 }
 private fun activateBiometricScreen(){
  screen("Activar huella")
  button("← Seguridad"){securitySettings()}
  heading("Protección con huella",
   "Se requiere la biometría fuerte del teléfono, vinculada a una clave de Android Keystore.")
  if(!biometricIsAvailable()){
   info("No hay huella configurada",
    "Activa una huella digital compatible en Ajustes del teléfono y vuelve a intentarlo.")
   return
  }
  label("PIN actual")
  val pin=securePinField("Confirma el PIN actual")
  button("Verificar PIN y registrar huella"){
   if(!security.verifyPin(pin.text.toString())){
    toast("PIN incorrecto o acceso bloqueado.");return@button
   }
   pin.text.clear()
   authenticateBiometric(true)
  }
 }
 private fun disableBiometricScreen(){
  screen("Desactivar huella")
  button("← Seguridad"){securitySettings()}
  heading("Desactivar acceso biométrico",
   "El acceso con PIN seguirá funcionando.")
  label("PIN actual")
  val pin=securePinField("Confirma tu PIN")
  button("Confirmar desactivación"){
   if(!security.verifyPin(pin.text.toString())){
    toast("PIN incorrecto.");return@button
   }
   security.disableBiometric()
   toast("Huella desactivada")
   securitySettings()
  }
 }
 private fun authenticateBiometric(enroll:Boolean){
  if(android.os.Build.VERSION.SDK_INT<28||!biometricIsAvailable()){
   toast("No hay biometría fuerte disponible en este dispositivo.")
   return
  }
  try{
   val cipher=if(enroll)security.createEnrollCipher()
    else security.createUnlockCipher()
   val prompt=android.hardware.biometrics.BiometricPrompt.Builder(this)
    .setTitle(if(enroll)"Activar huella BELSIPERU" else "Entrar a BELSIPERU")
    .setSubtitle("Utiliza la huella registrada en Android")
    .setNegativeButton("Cancelar",mainExecutor){_,_->}
    .build()
   val cancel=android.os.CancellationSignal()
   prompt.authenticate(
    android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher),
    cancel,mainExecutor,
    object:android.hardware.biometrics.BiometricPrompt.AuthenticationCallback(){
     override fun onAuthenticationSucceeded(
      result:android.hardware.biometrics.BiometricPrompt.AuthenticationResult){
      val verifiedCipher=result.cryptoObject?.cipher
      if(verifiedCipher==null){
       toast("No se recibió la verificación criptográfica.");return
      }
      try{
       if(enroll){
        if(security.finishEnrollment(verifiedCipher)){
         toast("Huella activada correctamente.")
         securitySettings()
        }else toast("No se pudo activar la huella.")
       }else{
        if(security.finishUnlock(verifiedCipher))unlockHome()
        else toast("No se pudo verificar la huella. Usa tu PIN.")
       }
      }catch(e:Exception){
       toast("No se pudo completar la verificación: "+(e.message?:"Error"))
      }
     }
     override fun onAuthenticationError(errorCode:Int,errString:CharSequence){
      if(errorCode!=android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
       errorCode!=android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED){
       toast("Biometría no disponible: "+errString.toString()+". Usa tu PIN.")
      }
     }
    })
  }catch(e:Exception){
   toast("No se pudo iniciar la huella: "+(e.message?:"Usa tu PIN"))
  }
 }
 private fun backupPasswordField(labelText:String):EditText{
  label(labelText)
  return input(labelText).apply{
   inputType=InputType.TYPE_CLASS_TEXT or
    InputType.TYPE_TEXT_VARIATION_PASSWORD
   filters=arrayOf(android.text.InputFilter.LengthFilter(128))
  }
 }
 private fun backupExportScreen(){
  screen("Crear respaldo cifrado")
  button("← Seguridad"){securitySettings()}
  heading("Copia de seguridad protegida",
   "Una contraseña independiente del PIN protege tu archivo usando AES-256-GCM. "+
   "Consérvala: sin ella no se puede descifrar el respaldo.")
  info("Incluye","Productos, clientes, cotizaciones, inventario, "+
   "configuración comercial y fotografías que todavía puedan leerse.")
  info("No incluye","PIN, código de emergencia ni claves biométricas. "+
   "El acceso de esta instalación seguirá siendo el mismo tras restaurar.")
  val pass=backupPasswordField("Contraseña del respaldo (mínimo 10 caracteres)")
  val confirm=backupPasswordField("Repite la contraseña")
  button("Elegir dónde guardar respaldo (.belsbak)"){
   val first=pass.text.toString()
   if(first.length<10||first!=confirm.text.toString()){
    toast("La contraseña debe tener 10 caracteres o más y coincidir.")
    return@button
   }
   pendingBackupPassword?.fill('\u0000')
   pendingBackupPassword=first.toCharArray()
   pass.text.clear();confirm.text.clear()
   val filename="BELSIPERU_RESPALDO_"+
    SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(Date())+".belsbak"
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
    addCategory(Intent.CATEGORY_OPENABLE)
    type="application/octet-stream"
    putExtra(Intent.EXTRA_TITLE,filename)
   },111)
  }
 }
 private fun backupRestoreScreen(){
  screen("Restaurar respaldo cifrado")
  button("← Seguridad"){securitySettings()}
  heading("Importar respaldo BELSIPERU",
   "Únicamente archivos .belsbak creados desde la nueva función de respaldo.")
  info("Atención: reemplazo de información",
   "Se reemplazarán todos los clientes, cotizaciones, inventario y ajustes comerciales "+
   "de ESTA instalación. Tu PIN y código de emergencia actuales se conservarán.",
   Color.rgb(168,105,20))
  val pass=backupPasswordField("Contraseña con la que protegiste el respaldo")
  button("Elegir archivo .belsbak"){
   val value=pass.text.toString()
   if(value.length<10){toast("Introduce la contraseña completa.");return@button}
   pendingBackupPassword?.fill('\u0000')
   pendingBackupPassword=value.toCharArray()
   pass.text.clear()
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
    addCategory(Intent.CATEGORY_OPENABLE)
    type="*/*"
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   },112)
  }
 }
 private fun backupExportFile(uri:Uri,password:CharArray){
  Thread{
   try{
    val stream=contentResolver.openOutputStream(uri)
     ?:throw IllegalStateException("No se pudo abrir el archivo.")
    val result=stream.use{SecureBackup.export(this,db,it,password)}
    runOnUiThread{
     toast("Respaldo cifrado creado: "+result.photoCount+
      " fotografías. "+result.inaccessiblePhotos+" no disponibles.")
     securitySettings()
    }
   }catch(e:Exception){
    runOnUiThread{toast("Error al crear respaldo: "+(e.message?:"Archivo inválido"))}
   }finally{password.fill('\u0000')}
  }.start()
 }
 private fun backupRestoreFile(uri:Uri,password:CharArray){
  // Confirmación adicional previa a cualquier modificación de datos.
  AlertDialog.Builder(this).setTitle("Reemplazar datos actuales")
   .setMessage("Esta operación sustituirá la base de datos y ajustes comerciales de esta "+
    "instalación. Los datos que existen ahora desaparecerán salvo que tengas "+
    "otro respaldo.\n\n¿Restaurar el archivo seleccionado?")
   .setNegativeButton("Cancelar"){_,_->password.fill('\u0000')}
   .setPositiveButton("Sí, restaurar"){_,_->
    Thread{
     try{
      val stream=contentResolver.openInputStream(uri)
       ?:throw IllegalArgumentException("No se pudo abrir el archivo.")
      val result=stream.use{SecureBackup.restore(this,db,it,password)}
      runOnUiThread{
       db=Store(this)
       toast("Respaldo restaurado; "+result.photoCount+" fotos recuperadas.")
       sessionAuthenticated=false
       lock()
      }
     }catch(e:Exception){
      runOnUiThread{
       db=Store(this)
       toast("Restauración fallida: "+(e.message?:"Archivo inválido"))
       securitySettings()
      }
     }finally{password.fill('\u0000')}
    }.start()
   }.show()
 }

 private fun welcomeDisplayName():String{
  val value=getSharedPreferences("belsiperu_home_ui",MODE_PRIVATE)
   .getString("welcome_name",companyInfo().commercial.ifBlank{companyInfo().legal})?.trim().orEmpty()
  return value.ifBlank{companyInfo().commercial.ifBlank{companyInfo().legal}}
 }
 private fun welcomeMessageSettings(){
  screen("Configuración · Bienvenida")
  button("← Configuración"){settings()}
  heading("Personalizar bienvenida",
   "Cambia únicamente el nombre mostrado después de «¡Hola! Bienvenido a». "+
   "No modifica el nombre legal de BELSIPERU ni sus documentos PDF.")
  label("Palabra o nombre para mostrar en el inicio")
  val name=input("Ejemplo: Mi Empresa")
  name.filters=arrayOf(android.text.InputFilter.LengthFilter(40))
  name.setText(welcomeDisplayName())
  val preview=line("¡Hola! Bienvenido a "+welcomeDisplayName(),18f,navy,true)
  body.addView(preview,LinearLayout.LayoutParams(-1,-2).apply{
   setMargins(0,d(12),0,d(12))
  })
  name.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    val label=s?.toString()?.trim().orEmpty().ifBlank{companyInfo().legal}
    preview.text="¡Hola! Bienvenido a "+label
   }
   override fun afterTextChanged(s:Editable?){}
  })
  button("✓ Guardar mensaje de bienvenida"){
   val label=name.text.toString().trim().replace(Regex("[\\r\\n\\t]+")," ")
    .replace(Regex(" {2,}")," ")
   if(label.isBlank()){
    name.error="Ingresa una palabra o nombre.";return@button
   }
   getSharedPreferences("belsiperu_home_ui",MODE_PRIVATE).edit()
    .putString("welcome_name",label).apply()
   toast("Mensaje de bienvenida actualizado")
   home()
  }
  button("Usar el nombre de mi empresa"){
   getSharedPreferences("belsiperu_home_ui",MODE_PRIVATE).edit()
    .remove("welcome_name").apply()
   toast("Se restauró la bienvenida original")
   home()
  }
 }
 private fun quoteNumberSettings(){
  screen("Configuración · Numeración")
  button("← Configuración"){settings()}
  heading("Numeración personalizada y automática",
   "Define la serie y el próximo número. Cada nueva cotización usa el siguiente correlativo, sin alterar las emitidas.")
  val current=activeQuoteSequence()
  label("Serie (1 a 6 dígitos)")
  val serie=input("Ejemplo: 0002")
  serie.inputType=InputType.TYPE_CLASS_NUMBER
  serie.filters=arrayOf(android.text.InputFilter.LengthFilter(6))
  serie.setText(current.first)
  label("Próximo correlativo")
  val next=input("Ejemplo: 50009")
  next.inputType=InputType.TYPE_CLASS_NUMBER
  next.filters=arrayOf(android.text.InputFilter.LengthFilter(7))
  next.setText(current.second.toString())
  val preview=line("Próxima cotización: "+current.first+"-"+
   current.second.toString().padStart(7,'0'),17f,navy,true)
  body.addView(preview)
  fun refreshPreview(){
   val serieText=serie.text.toString()
   val n=next.text.toString().toLongOrNull()
   preview.text="Próxima cotización: "+serieText+"-"+
    (n?:0L).toString().padStart(7,'0')
  }
  for(view in listOf(serie,next)){
   view.addTextChangedListener(object:TextWatcher{
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshPreview()}
    override fun afterTextChanged(s:Editable?){}
   })
  }
  info("Ejemplo",
   "Serie 0002, próximo número 50009 → 0002-0005009. El siguiente será 0002-0005010. Cada serie lleva su propio correlativo.")
  button("✓ Guardar numeración automática"){
   val newSerie=serie.text.toString().trim()
   val number=next.text.toString().toLongOrNull()
   if(!newSerie.matches(Regex("[0-9]{1,6}"))){
    serie.error="Usa únicamente 1 a 6 dígitos.";return@button
   }
   if(number==null||number !in 1L..9999999L){
    next.error="El correlativo debe estar entre 1 y 9999999.";return@button
   }
   val database=db.writableDatabase
   try{
    database.beginTransaction()
    val used=database.rawQuery(
     "SELECT COALESCE(MAX(quote_number),0) FROM quotes WHERE series=?",
     arrayOf(newSerie))
    val max=try{if(used.moveToFirst())used.getLong(0) else 0L}
     finally{used.close()}
    if(number<=max)throw IllegalArgumentException(
     "El próximo número debe ser mayor que "+max+" en esta serie.")
    database.execSQL(
     "INSERT OR REPLACE INTO quote_sequences(series,next_number) VALUES(?,?)",
     arrayOf(newSerie,number))
    database.setTransactionSuccessful()
   }catch(e:Exception){
    toast("No se pudo guardar: "+(e.message?:"Número inválido"));return@button
   }finally{if(database.inTransaction())database.endTransaction()}
   getSharedPreferences("quote_sequence",MODE_PRIVATE)
    .edit().putString("active_series",newSerie).apply()
   toast("Serie activa: "+newSerie+"-"+number.toString().padStart(7,'0'))
   settings()
  }
 }
 private fun companyConfigured():Boolean{
  val prefs=companyPrefs()
  return prefs.getBoolean("setup_complete",false)||
   !prefs.getString("legal",null).isNullOrBlank()
 }
 private fun companySetup(){
  screen("Configura tu negocio")
  heading("Bienvenido a BELSIPERU Gestión Comercial",
   "Para empezar, registra los datos de TU negocio. Tus clientes y productos "+
   "quedarán guardados en este dispositivo, no en una cuenta compartida.")
  info("Datos de cada negocio",
   "Puedes utilizar la aplicación en una ferretería, tienda, taller o empresa de "+
   "servicios. Los PDF mostrarán el nombre y el logotipo que configures.")
  info("Uso inicial",
   "El nombre de la plataforma no sustituye tu razón social. El RUC es opcional "+
   "si todavía no lo tienes; la aplicación no emite comprobantes electrónicos.")
  button("🏢 Configurar datos de mi negocio"){companySettings()}
  button("Consultar privacidad y tratamiento de datos"){privacyNotice()}
 }
 // Privacidad accesible también desde la pantalla de PIN.
 // No se declara inexistente transmisión alguna: el usuario puede usar
 // consultas externas voluntarias y compartir PDFs con otras aplicaciones.
 private fun privacyNotice(){
  screen("Privacidad y datos")
  button("← Regresar"){if(sessionAuthenticated)home() else lock()}
  heading("Tu negocio, tus datos",
   "BELSIPERU Gestión Comercial es una herramienta local para negocios. "+
   "Cada instalación conserva su catálogo, clientes, cotizaciones, "+
   "inventario, imágenes y preferencias en el dispositivo.")
  info("Almacenamiento",
   "Los datos comerciales se conservan localmente en la aplicación. "+
   "El PIN se verifica con un hash derivado; los respaldos que crees se "+
   "protegen con la contraseña que elijas. No hay cuentas de usuario "+
   "ni sincronización automática entre dispositivos.")
  info("Servicios opcionales",
   "Al utilizar las consultas de RUC o DNI, la aplicación envía el número "+
   "consultado al servidor HTTPS que TÚ hayas configurado. El escáner "+
   "emplea componentes de Google Play Services. Al compartir un PDF "+
   "por correo o mensajería, el archivo se entrega a la aplicación elegida "+
   "por ti y puede quedar sujeto a sus propias políticas.")
  info("Permisos y control",
   "Puedes seleccionar archivos, compartir documentos y activar biometría "+
   "en Android. No debes escribir claves de SUNAT ni credenciales bancarias "+
   "en los campos comerciales. Los archivos que exportes permanecen en "+
   "la ubicación que selecciones hasta que los borres.")
  info("Copias de seguridad",
   "Puedes crear un respaldo cifrado en Seguridad y recuperación. "+
   "Guarda la contraseña por separado: sin ella no es posible restaurarlo.")
  info("Política pública",
   "Antes de publicar la aplicación se habilitará también un enlace HTTPS "+
   "estable a la política de privacidad. La ficha de Google Play deberá "+
   "declarar los datos transmitidos por funciones externas y bibliotecas.")
  if(sessionAuthenticated){
   button("Crear respaldo cifrado"){backupExportScreen()}
   button("Eliminar datos de esta instalación"){deleteLocalDataScreen()}
  }
 }
 private fun deleteLocalDataScreen(){
  if(!sessionAuthenticated){lock();return}
  screen("Borrar datos locales")
  button("← Privacidad"){privacyNotice()}
  heading("Eliminar información de esta instalación",
   "Elimina los clientes, productos, cotizaciones, movimientos, fotos "+
   "internas, ajustes, PIN y claves de recuperación. Esta acción no "+
   "elimina archivos que hayas exportado a otras carpetas o aplicaciones.")
  info("Haz un respaldo ANTES de borrar",
   "Si no tienes un archivo cifrado accesible y su contraseña, "+
   "no podrás recuperar tus datos tras la eliminación.",Color.rgb(169,64,40))
  label("Confirma tu PIN")
  val pin=securePinField("PIN actual")
  label("Escribe ELIMINAR")
  val confirmation=input("ELIMINAR")
  button("Solicitar eliminación definitiva"){
   if(confirmation.text.toString().trim()!="ELIMINAR"){
    confirmation.error="Escribe ELIMINAR";return@button
   }
   if(!security.verifyPin(pin.text.toString())){
    toast("PIN incorrecto o acceso bloqueado.");return@button
   }
   android.app.AlertDialog.Builder(this)
    .setTitle("¿Eliminar todos los datos locales?")
    .setMessage("No se puede deshacer. ¿Confirmas que ya guardaste una "+
     "copia externa de tus datos y su contraseña?")
    .setNegativeButton("Cancelar",null)
    .setPositiveButton("Sí, eliminar"){_,_->
     try{
      db.close()
      if(!deleteDatabase("belsiperu.db"))throw IllegalStateException(
       "No fue posible eliminar la base de datos.")
      listOf("belsiperu_company_pdf","quote_sequence","belsiperu_home_ui",
       "ruc_service","belsiperu_recovery_contact","security")
       .forEach{getSharedPreferences(it,MODE_PRIVATE).edit().clear().commit()}
      filesDir.listFiles()?.forEach{it.deleteRecursively()}
      cacheDir.listFiles()?.forEach{it.deleteRecursively()}
      db=Store(this)
      security=AppSecurity(this)
      sessionAuthenticated=false
      toast("Datos locales eliminados")
      lock()
     }catch(e:Exception){
      toast("No se completó el borrado: "+(e.message?:"Error"))
     }
    }.show()
  }
 }
 private fun companySettings(){
  screen("Configuración · Empresa")
  button("← Volver a Configuración"){settings()}
  heading("Información de la empresa",
   "Estos datos se mostrarán en el encabezado de tus próximas proformas PDF.")
  val previous=companyInfo()
  label("Logotipo de la empresa")
  val preview=ImageView(this).apply{
   scaleType=ImageView.ScaleType.FIT_CENTER
   background=round(Color.WHITE,14,border)
   setPadding(d(10),d(10),d(10),d(10))
  }
  companyLogoPreview=preview
  if(companyLogoFile().isFile)loadCompanyLogoBitmap()?.let{preview.setImageBitmap(it)}
  body.addView(preview,LinearLayout.LayoutParams(-1,d(145)).apply{
   setMargins(0,d(6),0,d(4))
  })
  info("Medidas recomendadas para el logotipo",
   "Formato PNG con fondo transparente, idealmente 1200 × 600 píxeles (relación 2:1), hasta 3 MB. Se adapta sin deformarse al encabezado del PDF.")
  button("Seleccionar logotipo desde el teléfono"){
   val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
    addCategory(Intent.CATEGORY_OPENABLE)
    type="image/*"
   }
   startActivityForResult(intent,94)
  }
  button("Quitar logotipo de esta empresa"){
   companyLogoFile().delete()
   companyLogoPreview?.setImageDrawable(null)
   toast("No se usará un logotipo en los PDF.")
  }
  label("RUC de la empresa (opcional; 11 dígitos si lo ingresas)")
  val ruc=input("11 dígitos")
  ruc.inputType=InputType.TYPE_CLASS_NUMBER
  ruc.filters=arrayOf(android.text.InputFilter.LengthFilter(11))
  ruc.setText(previous.ruc)
  label("Razón social *")
  val legal=input("Nombre legal de la empresa")
  legal.setText(previous.legal)
  label("Nombre comercial")
  val commercial=input("Marca o nombre comercial")
  commercial.setText(previous.commercial)
  val fiscal=settingTextArea("Dirección fiscal (opcional)",previous.fiscal,3)
  label("Rubro del negocio")
  val business=input("Actividad comercial")
  business.setText(previous.business)
  label("Régimen tributario")
  val regime=input("Régimen de la empresa")
  regime.setText(previous.regime)
  button("✓ Guardar información de empresa"){
   val tax=ruc.text.toString().trim()
   val name=legal.text.toString().trim()
   val addr=fiscal.text.toString().trim()
   if(tax.isNotBlank()&&!tax.matches(Regex("[0-9]{11}"))){
    ruc.error="El RUC requiere 11 dígitos.";ruc.requestFocus();return@button
   }
   if(name.isBlank()){legal.error="Ingresa la razón social";legal.requestFocus();return@button}
      val ok=companyPrefs().edit()
    .putString("ruc",tax).putString("legal",name)
    .putString("commercial",commercial.text.toString().trim())
    .putString("fiscal",addr)
    .putString("business",business.text.toString().trim())
    .putString("regime",regime.text.toString().trim())
    .putBoolean("setup_complete",true)
    .commit()
   if(ok){toast("Datos de tu empresa guardados");home()}
   else toast("No se pudo guardar la configuración.")
  }
 }
 private fun printingSettings(){
  screen("Configuración · Impresión")
  button("← Volver a Configuración"){settings()}
  heading("Configuración básica",
   "Personaliza el texto de las proformas; puedes modificarlo sin consultar SUNAT.")
  val old=companyInfo()
  label("Número máximo de decimales")
  val decimals=choice(listOf("2 decimales","3 decimales","4 decimales"))
  decimals.setSelection(old.decimals-2)
  val header=settingTextArea("Información de cabecera",old.header,3)
  info("Consejo","La cabecera puede incluir un lema o descripción breve de la actividad comercial.")
  val banks=settingTextArea("Cuentas bancarias (todas las cuentas corrientes y CCI)",old.banks,7)
  info("Cómo ingresar las cuentas",
   "Escribe una cuenta por línea: Banco | Moneda | Cuenta corriente | CCI. Puedes añadir tantas líneas como necesites. No ingreses contraseñas, tarjetas ni claves bancarias.")
  val footer=settingTextArea("Información del pie de página",old.footer,4)
  button("✓ Guardar configuración de impresión"){
   val ok=companyPrefs().edit()
    .putInt("decimals",decimals.selectedItemPosition+2)
    .putString("header",header.text.toString().trim())
    .putString("banks",banks.text.toString().trim())
    .putString("footer",footer.text.toString().trim())
    .commit()
   if(ok){toast("Impresión configurada. Se aplicará a los nuevos PDF.");settings()}
   else toast("No se pudieron guardar los ajustes de impresión.")
  }
 }
 private fun rucSettings(){
  screen("Consulta de RUC")
  heading("Conexión a SUNAT mediante endpoint.pe",
    "La app consulta un servicio privado. La clave de endpoint.pe debe permanecer en el servidor.")
  val prefs=getSharedPreferences("ruc_service",MODE_PRIVATE)
  label("URL del servidor (HTTPS)")
  val base=input("https://mi-servicio.workers.dev")
  base.setText(prefs.getString("proxy_url",""))
  label("Código de acceso al servidor")
  val secret=input("No es la clave de endpoint.pe")
  secret.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
  secret.setText(prefs.getString("proxy_access",""))
  info("Importante","No pegues tu clave de endpoint.pe en la app ni en GitHub. Este código es diferente al del proveedor.",Color.rgb(169,105,19))
  button("Guardar conexión"){
   val url=base.text.toString().trim().trimEnd('/')
   val code=secret.text.toString().trim()
   val parsed=runCatching{Uri.parse(url)}.getOrNull()
   if(parsed?.scheme!="https" || parsed.host.isNullOrBlank() || !parsed.userInfo.isNullOrBlank() ||
      !parsed.query.isNullOrBlank() || !parsed.fragment.isNullOrBlank()){
    toast("Ingresa una dirección HTTPS válida, sin claves ni parámetros.")
   }else if(code.length<16){
    toast("El código de acceso debe tener 16 caracteres o más.")
   }else{
    prefs.edit().putString("proxy_url",url).putString("proxy_access",code).apply()
    toast("Conexión guardada")
    clients()
   }
  }
  button("Volver a Clientes"){clients()}
 }
 private data class RucDatos(val razon:String,val direccion:String,val estado:String,val condicion:String)
 private fun consultaRuc(ruc:String,done:(RucDatos?,String?)->Unit){
  val prefs=getSharedPreferences("ruc_service",MODE_PRIVATE)
  val base=prefs.getString("proxy_url","")?.trim()?.trimEnd('/')?:""
  val access=prefs.getString("proxy_access","")?:""
  if(base.isBlank() || access.length<16){
   done(null,"Configura primero el servidor RUC.");return
  }
  val parsed=runCatching{Uri.parse(base)}.getOrNull()
  if(parsed?.scheme!="https" || parsed.host.isNullOrBlank() || !parsed.userInfo.isNullOrBlank()){
   done(null,"La dirección de consulta debe usar HTTPS.");return
  }
  Thread{
   var result:RucDatos?=null
   var error:String?=null
   var connection:HttpURLConnection?=null
   try{
    connection=URL(base+"/ruc/"+ruc).openConnection() as HttpURLConnection
    connection.requestMethod="GET"
    connection.connectTimeout=8000
    connection.readTimeout=12000
    connection.setRequestProperty("Accept","application/json")
    connection.setRequestProperty("X-Belsiperu-Access",access)
    connection.instanceFollowRedirects=false
    val status=connection.responseCode
    val stream=if(status in 200..299)connection.inputStream else connection.errorStream
    val raw=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:""
    if(status==200){
     val obj=JSONObject(raw)
     if(!obj.optBoolean("success",false)){
      error=obj.optString("message","No se encontró información del RUC.")
     }else{
      val data=obj.optJSONObject("data")?:throw IllegalStateException("Respuesta incompleta.")
      val legal=data.optString("razon_social","").trim()
      if(legal.isBlank())throw IllegalStateException("No se recibió una razón social.")
      val direction=data.optString("direccion_completa","").takeIf{it.isNotBlank()&&it!="null"}
        ?:data.optString("direccion","")
      result=RucDatos(legal,direction,data.optString("estado",""),data.optString("condicion",""))
     }
    }else{
     error=when(status){
      400,422->"El RUC no tiene un formato válido."
      401,403->"El servidor de consulta no está autorizado."
      402->"El proveedor no tiene créditos disponibles."
      404->"Este RUC no figura en la consulta."
      429->"Demasiadas consultas. Intenta más tarde."
      in 500..599->"El servicio no está disponible temporalmente."
      else->"No se pudo consultar el RUC. Código HTTP $status."
     }
    }
   }catch(_:java.net.SocketTimeoutException){
    error="La consulta tardó demasiado. Comprueba tu conexión."
   }catch(_:java.net.UnknownHostException){
    error="No se pudo encontrar el servidor de consulta."
   }catch(_:Exception){
    error="No fue posible realizar la consulta. Puedes ingresar el cliente manualmente."
   }finally{connection?.disconnect()}
   runOnUiThread{if(!isFinishing)done(result,error)}
  }.start()
 }

 private data class DniDatos(val nombre:String)
 private fun consultaDni(dni:String,done:(DniDatos?,String?)->Unit){
  // Reutiliza la URL y el código de acceso existentes para la consulta RUC.
  val prefs=getSharedPreferences("ruc_service",MODE_PRIVATE)
  val base=prefs.getString("proxy_url","")?.trim()?.trimEnd('/')?:""
  val access=prefs.getString("proxy_access","")?:""
  if(base.isBlank() || access.length<16){
   done(null,"Configura primero el servidor RUC y DNI.");return
  }
  val parsed=runCatching{Uri.parse(base)}.getOrNull()
  if(parsed?.scheme!="https" || parsed.host.isNullOrBlank() || !parsed.userInfo.isNullOrBlank()){
   done(null,"La dirección de consulta debe usar HTTPS.");return
  }
  if(!dni.matches(Regex("[0-9]{8}"))){
   done(null,"Ingresa un DNI válido de 8 dígitos.");return
  }
  Thread{
   var result:DniDatos?=null
   var error:String?=null
   var connection:HttpURLConnection?=null
   try{
    connection=URL(base+"/dni/"+dni).openConnection() as HttpURLConnection
    connection.requestMethod="GET"
    connection.connectTimeout=8000
    connection.readTimeout=12000
    connection.setRequestProperty("Accept","application/json")
    connection.setRequestProperty("X-Belsiperu-Access",access)
    connection.instanceFollowRedirects=false
    val code=connection.responseCode
    val stream=if(code in 200..299)connection.inputStream else connection.errorStream
    val raw=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:""
    if(code==200){
     val payload=JSONObject(raw)
     if(!payload.optBoolean("success",false)){
      error=payload.optString("message","No se encontró información del DNI.")
     }else{
      val data=payload.optJSONObject("data")?:throw IllegalStateException("Respuesta incompleta.")
      val full=data.optString("nombre_completo","").trim()
      val parts=listOf(data.optString("nombres",""),
       data.optString("apellido_paterno",""),data.optString("apellido_materno",""))
      val fallback=parts.filter{it.isNotBlank()&&it!="null"}.joinToString(" ").trim()
      val nombre=full.takeIf{it.isNotBlank()&&it!="null"}?:fallback
      if(nombre.isBlank())throw IllegalStateException("No se recibieron nombres.")
      result=DniDatos(nombre)
     }
    }else{
     error=when(code){
      400,422->"El DNI debe contener exactamente 8 dígitos."
      401,403->"El servidor no está autorizado. Revisa el código de acceso."
      402->"Tu cuenta de endpoint.pe no tiene créditos disponibles."
      404->"No se encontró información para este DNI."
      429->"Demasiadas consultas. Intenta más tarde."
      in 500..599->"El servicio no está disponible temporalmente."
      else->"No se pudo consultar el DNI. Código HTTP $code."
     }
    }
   }catch(_:java.net.SocketTimeoutException){
    error="La consulta tardó demasiado. Comprueba tu conexión."
   }catch(_:java.net.UnknownHostException){
    error="No se encontró el servidor de consulta."
   }catch(_:Exception){
    error="No fue posible consultar el DNI. Puedes ingresar el cliente manualmente."
   }finally{connection?.disconnect()}
   runOnUiThread{if(!isFinishing)done(result,error)}
  }.start()
 }
 private data class PriceVariant(val label:String,val price:Double)
 private fun productSection(title:String,content:()->Unit){
  val root=body
  val section=card()
  section.addView(line(title,18f,navy,true))
  root.addView(section,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(9),0,d(9))})
  body=section
  try{content()}finally{body=root}
 }
 private fun suggestionField(hint:String,items:List<String>):AutoCompleteTextView{
  val view=AutoCompleteTextView(this).apply{
   this.hint=hint
   textSize=15f
   setTextColor(ink);setHintTextColor(muted)
   setSingleLine(true)
   minHeight=d(51)
   setPadding(d(13),d(9),d(13),d(9))
   background=round(Color.WHITE,12,border)
   threshold=0
   setAdapter(ArrayAdapter(this@MainActivity,android.R.layout.simple_dropdown_item_1line,items))
   setOnClickListener{if(hasFocus())showDropDown()}
  }
  body.addView(view,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(6),0,d(6))})
  return view
 }
 private fun catalogSuggestions(field:String):List<String>{
  val defaults=when(field){
   "category"->listOf("Amoladora","Taladro","Soldadora","Herramientas","Accesorios","Repuestos","Máquinas","Servicios")
   "brand"->listOf("Bosch","Makita","DeWalt","Stanley","Truper","Total","Sin marca")
   else->emptyList()
  }
  if(field!="category" && field!="brand")return defaults
  val list=defaults.toMutableList()
  val cursor=db.readableDatabase.rawQuery(
   "SELECT DISTINCT "+field+" FROM products WHERE "+field+"<>'' ORDER BY "+field,null)
  try{while(cursor.moveToNext()){
   val value=cursor.getString(0)?:continue
   if(list.none{it.equals(value,ignoreCase=true)})list.add(value)
  }}finally{cursor.close()}
  return list
 }
 private fun showProductImage(target:ImageView,rawUri:String?){
  target.scaleType=ImageView.ScaleType.FIT_CENTER
  if(rawUri.isNullOrBlank()){
   target.setImageResource(android.R.drawable.ic_menu_gallery)
   return
  }
  try{
   val uri=Uri.parse(rawUri)
   if(uri.scheme!="content"){target.setImageResource(android.R.drawable.ic_menu_gallery);return}
   val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
   contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
   if(options.outWidth<1||options.outHeight<1)throw IllegalArgumentException("Imagen vacía")
   var sample=1
   while(options.outWidth/sample>420 || options.outHeight/sample>420)sample*=2
   val decoded=contentResolver.openInputStream(uri)?.use{
    BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})
   }
   if(decoded!=null)target.setImageBitmap(decoded)
   else target.setImageResource(android.R.drawable.ic_menu_gallery)
  }catch(_:Exception){
   target.setImageResource(android.R.drawable.ic_menu_gallery)
  }catch(_:OutOfMemoryError){
   target.setImageResource(android.R.drawable.ic_menu_gallery)
  }
 }



 private data class CatalogExcelRow(
  val code:String,val line:Int,val values:android.content.ContentValues
 )
 private fun selectCatalogExcel(){
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE)
   type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  startActivityForResult(intent,95)
 }
 private fun exportCatalogExcel(template:Boolean){
  pendingCatalogExportTemplate=template
  val filename=if(template)"BELSIPERU_PLANTILLA_PRODUCTOS.xlsx"
   else "BELSIPERU_CATALOGO_PRODUCTOS.xlsx"
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE)
   type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
   putExtra(Intent.EXTRA_TITLE,filename)
  }
  startActivityForResult(intent,96)
 }
 private fun writeCatalogExcel(uri:Uri,template:Boolean){
  Thread{
   try{
    val data=mutableListOf<List<String>>()
    if(!template){
     val cursor=db.readableDatabase.rawQuery(
      "SELECT code,name,kind,barcode,category,brand,unit,currency,price,"+
      "purchase_price,stock,min_stock,sale_includes_igv,purchase_includes_igv,"+
      "tax_affectation,isc_type,sunat_code,icbper,sell_without_stock,weight "+
      "FROM products ORDER BY name COLLATE NOCASE",null)
     try{
      while(cursor.moveToNext()){
       val row=mutableListOf<String>()
       for(i in 0 until cursor.columnCount){
        row.add(if(cursor.isNull(i))"" else
         if(i in listOf(12,13,17,18)){
          if(cursor.getInt(i)==1)"SI" else "NO"
         }else cursor.getString(i)?:"")
       }
       data.add(row)
      }
     }finally{cursor.close()}
    }
    val stream=contentResolver.openOutputStream(uri)
     ?:throw IllegalStateException("No se puede abrir el archivo de destino")
    stream.use{CatalogExcel.write(it,data)}
    runOnUiThread{
     toast(if(template)"Plantilla Excel guardada" else
      "Catálogo Excel exportado: "+data.size+" producto(s).")
    }
   }catch(e:Exception){
    runOnUiThread{toast("No se pudo exportar Excel: "+(e.message?:"Archivo inválido"))}
   }
  }.start()
 }
 private fun importCellNumber(value:String,default:Double,row:Int,field:String):Double{
  if(value.isBlank())return default
  val normalized=value.trim().replace(" ","").replace(',', '.')
  val number=normalized.toDoubleOrNull()
  if(number==null||!number.isFinite()||number<0)
   throw IllegalArgumentException("Fila "+row+": "+field+" debe ser número no negativo.")
  return number
 }
 private fun importCatalogExcel(uri:Uri){
  Thread{
   try{
    val input=contentResolver.openInputStream(uri)
     ?:throw IllegalStateException("No se pudo abrir el Excel.")
    val rows=input.use{CatalogExcel.read(it)}
    if(rows.isEmpty())throw IllegalArgumentException("No hay productos en el archivo.")
    val approved=mutableListOf<CatalogExcelRow>()
    val failures=mutableListOf<String>()
    val uniqueCodes=mutableSetOf<String>()
    for(row in rows){
     val line=row["_fila"]?.toIntOrNull()?:0
     try{
      val code=row["codigo"]?.trim().orEmpty()
      val name=row["nombre"]?.trim().orEmpty()
      if(code.isEmpty()||name.isEmpty())throw IllegalArgumentException(
       "Fila "+line+": Código y Nombre son obligatorios.")
      if(code.length>120||name.length>500)throw IllegalArgumentException(
       "Fila "+line+": código o nombre demasiado largo.")
      if(!uniqueCodes.add(code.uppercase(Locale.ROOT)))
       throw IllegalArgumentException("Fila "+line+": código duplicado en Excel.")
      val kind=(row["tipo"]?:"PRODUCTO").trim().uppercase(Locale.ROOT).ifEmpty{"PRODUCTO"}
      if(kind !in listOf("PRODUCTO","SERVICIO"))
       throw IllegalArgumentException("Fila "+line+": Tipo debe ser PRODUCTO o SERVICIO.")
      val currency=(row["moneda"]?:"PEN").trim().uppercase(Locale.ROOT).ifEmpty{"PEN"}
      if(currency !in listOf("PEN","USD"))
       throw IllegalArgumentException("Fila "+line+": moneda debe ser PEN o USD.")
      val tax=(row["afectacion_igv"]?:"GRAVADO").trim().uppercase(Locale.ROOT)
       .ifEmpty{"GRAVADO"}
      if(tax !in listOf("GRAVADO","EXONERADO","INAFECTO"))
       throw IllegalArgumentException("Fila "+line+": afectación IGV inválida.")
      val isc=(row["tipo_isc"]?:"NO APLICA").trim().uppercase(Locale.ROOT)
       .ifEmpty{"NO APLICA"}
      val sale=importCellNumber(row["precio_venta"].orEmpty(),0.0,line,"Precio venta")
      val buy=importCellNumber(row["precio_compra"].orEmpty(),0.0,line,"Precio compra")
      val stock=importCellNumber(row["stock"].orEmpty(),0.0,line,"Stock")
      val minimum=importCellNumber(row["stock_minimo"].orEmpty(),0.0,line,"Stock mínimo")
      val weight=importCellNumber(row["peso_kg"].orEmpty(),0.0,line,"Peso")
      if(stock%1.0!=0.0||minimum%1.0!=0.0||
       stock>Int.MAX_VALUE||minimum>Int.MAX_VALUE)
       throw IllegalArgumentException("Fila "+line+": stock debe ser número entero.")
      fun yes(field:String,default:Boolean=false):Boolean{
       val text=(row[field]?:if(default)"SI" else "NO").trim()
        .uppercase(Locale.ROOT)
       return text in listOf("SI","SÍ","1","TRUE","VERDADERO","YES")
      }
      val service=kind=="SERVICIO"
      val values=android.content.ContentValues().apply{
       put("code",code);put("name",name);put("kind",kind)
       put("barcode",row["codigo_barras"].orEmpty())
       put("category",row["categoria"].orEmpty());put("brand",row["marca"].orEmpty())
       put("unit",row["unidad"]?.ifBlank{"UNIDADES"}?:"UNIDADES")
       put("currency",currency);put("price",sale);put("purchase_price",buy)
       put("stock",if(service)0 else stock.toInt())
       put("min_stock",if(service)0 else minimum.toInt())
       put("sale_includes_igv",if(tax=="GRAVADO"&&yes("venta_incluye_igv",true))1 else 0)
       put("purchase_includes_igv",if(tax=="GRAVADO"&&yes("compra_incluye_igv",true))1 else 0)
       put("tax_affectation",tax);put("isc_type",isc)
       put("sunat_code",row["codigo_sunat"].orEmpty())
       put("icbper",if(yes("icbper"))1 else 0)
       put("sell_without_stock",if(service||yes("vender_sin_stock"))1 else 0)
       put("weight",weight)
      }
      approved.add(CatalogExcelRow(code,line,values))
     }catch(e:Exception){failures.add(e.message?:"Fila "+line+": datos incorrectos.")}
    }
    if(approved.isEmpty())throw IllegalArgumentException(
     "No hay filas válidas. "+failures.take(2).joinToString(" "))
    val existing=db.readableDatabase.rawQuery("SELECT code FROM products",null)
    val codes=mutableSetOf<String>()
    try{while(existing.moveToNext()){
     codes.add((existing.getString(0)?:"").uppercase(Locale.ROOT))
    }}finally{existing.close()}
    val found=approved.count{it.code.uppercase(Locale.ROOT) in codes}
    val fresh=approved.size-found
    val details=buildString{
     append("Registros correctos: ").append(approved.size)
     append("\nNuevos: ").append(fresh)
     append("\nCódigos existentes: ").append(found)
     append("\nFilas inválidas u omitidas: ").append(failures.size)
     if(failures.isNotEmpty()){
      append("\n\nEjemplos:\n").append(failures.take(4).joinToString("\n"))
     }
     append("\n\nActualizar existentes reemplaza sus datos comerciales y stock. Conserva las fotos y las variantes de precio.")
    }
    runOnUiThread{
     AlertDialog.Builder(this)
      .setTitle("Importar catálogo de Excel")
      .setMessage(details)
      .setNegativeButton("Cancelar",null)
      .setNeutralButton("Solo productos nuevos"){_,_->saveCatalogImport(approved,false)}
      .setPositiveButton("Nuevos y actualizar existentes"){_,_->
       AlertDialog.Builder(this).setTitle("Confirmar actualización de stock")
        .setMessage("Se modificarán precios y existencias de los códigos presentes en el Excel. Esta acción no crea movimientos de Kardex. ¿Confirmas?")
        .setNegativeButton("Cancelar",null)
        .setPositiveButton("Sí, actualizar"){_,_->saveCatalogImport(approved,true)}
        .show()
      }.show()
    }
   }catch(e:Exception){
    runOnUiThread{toast("Error importando Excel: "+(e.message?:"Archivo inválido"))}
   }
  }.start()
 }
 private fun saveCatalogImport(records:List<CatalogExcelRow>,updateExisting:Boolean){
  Thread{
   var added=0;var updated=0;var skipped=0
   val database=db.writableDatabase
   try{
    database.beginTransaction()
    for(item in records){
     val c=database.rawQuery(
      "SELECT id FROM products WHERE code=? COLLATE NOCASE LIMIT 1",
      arrayOf(item.code))
     val id=try{if(c.moveToFirst())c.getLong(0) else null}finally{c.close()}
     if(id==null){
      database.insertOrThrow("products",null,item.values);added++
     }else if(updateExisting){
      if(database.update("products",item.values,"id=?",arrayOf(id.toString()))==1)
       updated++ else skipped++
     }else skipped++
    }
    database.setTransactionSuccessful()
    runOnUiThread{
     toast("Excel importado: "+added+" nuevos, "+updated+
      " actualizados, "+skipped+" omitidos.")
     products()
    }
   }catch(e:Exception){
    runOnUiThread{toast("No se importó ningún cambio: "+(e.message?:"Error de base de datos"))}
   }finally{if(database.inTransaction())database.endTransaction()}
  }.start()
 }

 private fun products(){
  screen("Catálogo")
  button("＋ Nuevo producto"){productEditor()}
  button("⬇ Importar productos desde Excel (.xlsx)"){selectCatalogExcel()}
  button("⬆ Exportar catálogo a Excel (.xlsx)"){exportCatalogExcel(false)}
  button("▤ Descargar plantilla Excel (.xlsx)"){exportCatalogExcel(true)}
  heading("Productos y servicios","Busca, consulta fotos y edita las fichas guardadas.")
  label("Buscar en catálogo")
  val search=input("Nombre, código, marca, categoría o código de barras")
  label("Mostrar")
  val filter=choice(listOf("Todos","Productos","Servicios"))
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(list,LinearLayout.LayoutParams(-1,-2))
  fun refresh(){
   list.removeAllViews()
   val text=search.text.toString().trim()
   val chosen=filter.selectedItemPosition
   val c=db.readableDatabase.rawQuery(
    "SELECT id,code,name,price,currency,stock,min_stock,photo_uri,kind,category,brand,unit,barcode,purchase_price FROM products ORDER BY id DESC",null)
   var found=0
   try{
    while(c.moveToNext()){
     val id=c.getLong(0)
     val code=c.getString(1)?:""
     val name=c.getString(2)?:""
     val price=c.getDouble(3)
     val currency=c.getString(4)?:"PEN"
     val currentStock=c.getInt(5)
     val min=c.getInt(6)
     val photo=c.getString(7)
     val kind=c.getString(8)?:"PRODUCTO"
     val category=c.getString(9)?:""
     val brand=c.getString(10)?:""
     val unit=c.getString(11)?:"UNIDADES"
     val barcode=c.getString(12)?:""
     val purchase=c.getDouble(13)
     if(chosen==1&&kind=="SERVICIO")continue
     if(chosen==2&&kind!="SERVICIO")continue
     val searchable=listOf(code,name,category,brand,barcode).joinToString(" ")
     if(text.isNotEmpty()&&!searchable.contains(text,ignoreCase=true))continue
     found++
     val entry=card()
     val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
     val preview=ImageView(this@MainActivity).apply{
      background=round(pale,10,border)
      setPadding(d(4),d(4),d(4),d(4))
      contentDescription="Fotografía: "+name
     }
     showProductImage(preview,photo)
     row.addView(preview,LinearLayout.LayoutParams(d(78),d(80)))
     val details=LinearLayout(this@MainActivity).apply{
      orientation=LinearLayout.VERTICAL;setPadding(d(11),0,0,0)
     }
     details.addView(line(name,15f,navy,true))
     details.addView(line(code+" · "+(if(kind=="SERVICIO")"Servicio" else "Producto"),12f,muted))
     if(category.isNotBlank()||brand.isNotBlank()){
      details.addView(line(listOf(category,brand).filter{it.isNotBlank()}.joinToString(" · "),12f,muted))
     }
     details.addView(line((if(currency=="USD")"US$ " else "S/ ")+
       String.format(Locale.US,"%,.2f",price),17f,green,true))
     details.addView(line(if(kind=="SERVICIO")"Servicio — sin stock físico" else
       ("Stock: "+currentStock+" "+unit.lowercase(Locale.ROOT)+
        if(min>0&&currentStock<=min)" · Bajo mínimo" else ""),12f,muted))
     row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
     entry.addView(row)
     val actions=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.END}
     fun action(title:String,click:()->Unit){
      val link=line(title,13f,green,true).apply{
       setPadding(d(12),d(11),d(12),d(11))
       isClickable=true;isFocusable=true;setOnClickListener{click()}
      }
      actions.addView(link)
     }
     action("Editar"){productEditor(id)}
     action(if(photo.isNullOrBlank())"Añadir foto" else "Cambiar foto"){selectPhoto(id)}
     entry.addView(actions)
     list.addView(entry,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(8),0,d(6))})
    }
   }finally{c.close()}
   if(found==0){
    list.addView(line("No hay productos que coincidan con la búsqueda.",14f,muted))
   }else{
    list.addView(line(found.toString()+" registro(s) en el catálogo.",12f,muted))
   }
  }
  search.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()}
   override fun afterTextChanged(s:Editable?){}
  })
  filter.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){refresh()}
  }
  refresh()
 }
 private fun productEditor(editId:Long?=null){
  val saved=mutableMapOf<String,String>()
  if(editId!=null){
   val old=db.readableDatabase.rawQuery("SELECT * FROM products WHERE id=?",arrayOf(editId.toString()))
   try{
    if(!old.moveToFirst()){toast("No se encontró el producto");products();return}
    for(i in 0 until old.columnCount)saved[old.getColumnName(i)]=old.getString(i)?:""
   }finally{old.close()}
  }
  fun previous(key:String,default:String="")=saved[key]?.takeIf{it.isNotBlank()}?:default
  fun bool(key:String)=previous(key)=="1"
  screen(if(editId==null)"Nuevo producto" else "Editar producto")
  button("← Volver al catálogo"){draftPhotoPreview=null;products()}
  heading("Ficha del producto","Registra todos los datos comerciales y de inventario.")
  pendingDraftPhotoUri=previous("photo_uri").ifBlank{null}
  val image=ImageView(this).apply{
   background=round(Color.WHITE,16,border)
   setPadding(d(12),d(12),d(12),d(12))
   contentDescription="Fotografía del producto"
  }
  showProductImage(image,pendingDraftPhotoUri)
  draftPhotoPreview=image
  body.addView(image,LinearLayout.LayoutParams(-1,d(160)).apply{setMargins(0,d(8),0,d(5))})
  button("＋ Elegir o cambiar fotografía"){
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
    addCategory(Intent.CATEGORY_OPENABLE);type="image/*"
   },93)
  }
  lateinit var kind:Spinner
  lateinit var code:EditText
  lateinit var barcode:EditText
  lateinit var category:AutoCompleteTextView
  lateinit var brand:AutoCompleteTextView
  lateinit var unit:Spinner
  lateinit var name:EditText
  productSection("Información general"){
   label("Tipo")
   kind=choice(listOf("Producto","Servicio"))
   kind.setSelection(if(previous("kind","PRODUCTO")=="SERVICIO")1 else 0)
   label("Código único *")
   code=input("Ejemplo: GWS-850")
   if(editId==null){
    val nextId=queryCount("SELECT COALESCE(MAX(id),0)+1 FROM products")
    code.setText("BLP"+nextId.toString().padStart(6,'0'))
   }else code.setText(previous("code"))
   label("Código de barras")
   val barcodeRow=LinearLayout(this@MainActivity).apply{
    orientation=LinearLayout.HORIZONTAL
    gravity=Gravity.CENTER_VERTICAL
   }
   barcode=EditText(this@MainActivity).apply{
    hint="Opcional; ingresar el código impreso"
    setSingleLine(true)
    textSize=15f
    setTextColor(ink)
    setHintTextColor(muted)
    setPadding(d(13),d(9),d(13),d(9))
    background=round(Color.WHITE,12,border)
    inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    minHeight=d(50)
    setText(previous("barcode"))
   }
   barcodeRow.addView(barcode,LinearLayout.LayoutParams(0,d(50),1f))
   val scanButton=ImageView(this@MainActivity).apply{
    setImageResource(android.R.drawable.ic_menu_camera)
    setColorFilter(Color.WHITE)
    setPadding(d(14),d(12),d(14),d(12))
    background=round(green,12)
    contentDescription="Escanear código de barras con la cámara"
    isClickable=true
    isFocusable=true
    setOnClickListener{scanBarcode(barcode)}
   }
   barcodeRow.addView(scanButton,LinearLayout.LayoutParams(d(56),d(50)).apply{
    setMargins(d(8),0,0,0)
   })
   body.addView(barcodeRow,LinearLayout.LayoutParams(-1,-2).apply{
    setMargins(0,d(3),0,d(7))
   })
   body.addView(line("Toca la cámara para escanear, o escribe el código manualmente.",12f,muted))
   label("Categoría")
   category=suggestionField("Escribe o elige una categoría",catalogSuggestions("category"))
   category.setText(previous("category"))
   label("Marca")
   brand=suggestionField("Escribe o elige una marca",catalogSuggestions("brand"))
   brand.setText(previous("brand"))
   label("Unidad de medida")
   val units=mutableListOf("UNIDADES","CAJAS","PAQUETES","DOCENAS","JUEGOS",
     "METROS","KILOGRAMOS","GRAMOS","LITROS","PARES","BOLSAS","SERVICIOS")
   val savedUnit=previous("unit","UNIDADES")
   if(savedUnit !in units)units.add(savedUnit)
   unit=choice(units)
   unit.setSelection(units.indexOf(savedUnit).coerceAtLeast(0))
   label("Nombre o descripción *")
   name=input("Ejemplo: Amoladora angular Bosch GWS 850")
   name.setText(previous("name"))
  }
  lateinit var currency:Spinner
  lateinit var salePrice:EditText
  lateinit var purchasePrice:EditText
  lateinit var saleIgV:CheckBox
  lateinit var purchaseIgV:CheckBox
  productSection("Precios"){
   label("Moneda")
   currency=choice(listOf("Soles (PEN)","Dólares (USD)"))
   currency.setSelection(if(previous("currency","PEN")=="USD")1 else 0)
   label("Precio de venta")
   salePrice=input("0.00",true)
   salePrice.setText(previous("price","0.00"))
   saleIgV=CheckBox(this@MainActivity).apply{
    text="Precio de venta incluye IGV 18 %"
    isChecked=if(editId==null)true else bool("sale_includes_igv")
   }
   body.addView(saleIgV)
   label("Precio de compra")
   purchasePrice=input("0.00",true)
   purchasePrice.setText(previous("purchase_price","0.00"))
   purchaseIgV=CheckBox(this@MainActivity).apply{
    text="Precio de compra incluye IGV 18 %"
    isChecked=if(editId==null)true else bool("purchase_includes_igv")
   }
   body.addView(purchaseIgV)
  }
  val variants=mutableListOf<PriceVariant>()
  if(editId!=null){
   val cv=db.readableDatabase.rawQuery(
    "SELECT label,price FROM product_price_variants WHERE product_id=? ORDER BY id",
    arrayOf(editId.toString()))
   try{while(cv.moveToNext())variants.add(PriceVariant(cv.getString(0),cv.getDouble(1)))}
   finally{cv.close()}
  }
  val variantList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  fun redrawVariants(){
   variantList.removeAllViews()
   if(variants.isEmpty()){
    variantList.addView(line("No hay variantes de precio registradas.",13f,muted))
   }else{
    variants.forEachIndexed{index,variant->
     val row=LinearLayout(this@MainActivity).apply{
      orientation=LinearLayout.HORIZONTAL
      gravity=Gravity.CENTER_VERTICAL
      setPadding(d(5),d(6),d(5),d(6))
     }
     row.addView(line(variant.label+" — "+String.format(Locale.US,"%.2f",variant.price),14f,ink),
      LinearLayout.LayoutParams(0,-2,1f))
     val remove=line("Quitar",13f,Color.rgb(167,57,44),true).apply{
      setPadding(d(7),d(10),d(7),d(10))
      isClickable=true
      setOnClickListener{variants.removeAt(index);redrawVariants()}
     }
     row.addView(remove)
     variantList.addView(row)
    }
   }
  }
  productSection("Variantes de precio"){
   body.addView(line("Puedes agregar precios mayoristas, por volumen u otros.",13f,muted))
   body.addView(variantList)
   label("Nombre de variante")
   val variantLabel=input("Ejemplo: Precio mayorista")
   label("Precio de variante")
   val variantPrice=input("0.00",true)
   button("＋ Agregar variante"){
    val labelText=variantLabel.text.toString().trim()
    val priceValue=variantPrice.text.toString().toDoubleOrNull()
    if(labelText.isBlank()||priceValue==null||priceValue<0){
     toast("Ingresa un nombre y un precio válido para la variante")
    }else if(variants.any{it.label.equals(labelText,true)}){
     toast("Ya hay una variante con ese nombre")
    }else{
     variants.add(PriceVariant(labelText,priceValue))
     variantLabel.setText("");variantPrice.setText("")
     redrawVariants()
    }
   }
  }
  redrawVariants()
  lateinit var tax:Spinner
  lateinit var isc:Spinner
  lateinit var sunatCode:EditText
  lateinit var icbper:CheckBox
  productSection("Configuración SUNAT"){
   body.addView(line("Estos son datos de clasificación; la app todavía no emite comprobantes electrónicos.",12f,muted))
   label("Tipo de afectación IGV")
   tax=choice(listOf("Gravado","Exonerado","Inafecto"))
   tax.setSelection(when(previous("tax_affectation","GRAVADO")){
    "EXONERADO"->1;"INAFECTO"->2;else->0
   })
   label("Tipo de afectación ISC")
   val iscItems=listOf("No aplica","Sistema al valor","Monto fijo","Precio venta público")
   isc=choice(iscItems)
   isc.setSelection(when(previous("isc_type","NO APLICA")){
    "SISTEMA AL VALOR"->1;"MONTO FIJO"->2;"PRECIO VENTA PÚBLICO"->3;else->0
   })
   label("Código de producto SUNAT")
   sunatCode=input("Opcional; código UNSPSC / SUNAT")
   sunatCode.setText(previous("sunat_code"))
   icbper=CheckBox(this@MainActivity).apply{
    text="Afectación ICBPER"
    isChecked=bool("icbper")
   }
   body.addView(icbper)
  }
  lateinit var sellWithoutStock:CheckBox
  lateinit var weight:EditText
  lateinit var stock:EditText
  lateinit var minStock:EditText
  productSection("Opciones e inventario"){
   sellWithoutStock=CheckBox(this@MainActivity).apply{
    text="Permitir ventas sin stock"
    isChecked=bool("sell_without_stock")
   }
   body.addView(sellWithoutStock)
   label("Peso por unidad (kg) — opcional")
   weight=input("0.00",true)
   weight.setText(previous("weight","0.00"))
   label("Stock inicial")
   stock=input("0",true)
   stock.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
   stock.setText(previous("stock","0"))
   label("Stock mínimo")
   minStock=input("0",true)
   minStock.inputType=InputType.TYPE_CLASS_NUMBER
   minStock.setText(previous("min_stock","0"))
   body.addView(line("Los servicios no manejan stock físico.",12f,muted))
  }
  tax.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){
    val taxable=position==0
    if(!taxable){
     saleIgV.isChecked=false
     purchaseIgV.isChecked=false
    }
    saleIgV.isEnabled=taxable
    purchaseIgV.isEnabled=taxable
   }
  }
  fun refreshKind(){
   val service=kind.selectedItemPosition==1
   stock.isEnabled=!service
   minStock.isEnabled=!service
   sellWithoutStock.isEnabled=!service
   stock.alpha=if(service)0.45f else 1f
   minStock.alpha=if(service)0.45f else 1f
  }
  kind.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){refreshKind()}
  }
  refreshKind()
  button(if(editId==null)"✓ Guardar producto" else "✓ Guardar cambios"){
   val codigo=code.text.toString().trim()
   val nombre=name.text.toString().trim()
   if(codigo.isBlank() || nombre.isBlank()){
    toast("Código único y descripción son obligatorios");return@button
   }
   val sale=salePrice.text.toString().toDoubleOrNull()
   val buy=purchasePrice.text.toString().toDoubleOrNull()
   val peso=weight.text.toString().toDoubleOrNull()
   val currentStock=stock.text.toString().toIntOrNull()
   val minimum=minStock.text.toString().toIntOrNull()
   if(sale==null||sale<0||buy==null||buy<0||peso==null||peso<0||
      currentStock==null||currentStock<0||minimum==null||minimum<0){
    toast("Revisa los precios, el peso y el stock: deben ser números válidos y no negativos.")
    return@button
   }
   val database=db.writableDatabase
   val duplicated=database.rawQuery(
    "SELECT id FROM products WHERE code=? AND id<>? LIMIT 1",
    arrayOf(codigo,(editId?:-1L).toString()))
   val exists=try{duplicated.moveToFirst()}finally{duplicated.close()}
   if(exists){toast("Este código único ya existe. Ingresa otro.");return@button}
   val service=kind.selectedItemPosition==1
   val taxMode=when(tax.selectedItemPosition){1->"EXONERADO";2->"INAFECTO";else->"GRAVADO"}
   val iscMode=when(isc.selectedItemPosition){
    1->"SISTEMA AL VALOR";2->"MONTO FIJO";3->"PRECIO VENTA PÚBLICO";else->"NO APLICA"
   }
   val values=android.content.ContentValues().apply{
    put("code",codigo)
    put("name",nombre)
    put("kind",if(service)"SERVICIO" else "PRODUCTO")
    put("barcode",barcode.text.toString().trim())
    put("category",category.text.toString().trim())
    put("brand",brand.text.toString().trim())
    put("unit",unit.selectedItem.toString())
    put("price",sale)
    put("purchase_price",buy)
    put("currency",if(currency.selectedItemPosition==1)"USD" else "PEN")
    put("sale_includes_igv",if(taxMode=="GRAVADO"&&saleIgV.isChecked)1 else 0)
    put("purchase_includes_igv",if(taxMode=="GRAVADO"&&purchaseIgV.isChecked)1 else 0)
    put("tax_affectation",taxMode)
    put("isc_type",iscMode)
    put("sunat_code",sunatCode.text.toString().trim())
    put("icbper",if(icbper.isChecked)1 else 0)
    put("sell_without_stock",if(service||sellWithoutStock.isChecked)1 else 0)
    put("weight",peso)
    put("stock",if(service)0 else currentStock)
    put("min_stock",if(service)0 else minimum)
    put("photo_uri",pendingDraftPhotoUri)
   }
   var success=false
   try{
    database.beginTransaction()
    val productId=if(editId==null){
     database.insertOrThrow("products",null,values)
    }else{
     val updated=database.update("products",values,"id=?",arrayOf(editId.toString()))
     if(updated!=1)throw IllegalStateException("Producto no encontrado")
     editId
    }
    database.delete("product_price_variants","product_id=?",arrayOf(productId.toString()))
    variants.forEach{v->
     database.insertOrThrow("product_price_variants",null,android.content.ContentValues().apply{
      put("product_id",productId);put("label",v.label);put("price",v.price)
     })
    }
    database.setTransactionSuccessful()
    success=true
   }catch(e:Exception){
    toast("No se pudo guardar el producto: "+(e.message?:"Error inesperado"))
   }finally{
    if(database.inTransaction())database.endTransaction()
   }
   if(success){
    pendingDraftPhotoUri=null;draftPhotoPreview=null
    toast(if(editId==null)"Producto registrado" else "Producto actualizado")
    products()
   }
  }
 }

 private data class ClientAddress(
  var id:Long?,var label:String,var address:String,var ubigeo:String,var isMain:Boolean
 )

 private fun clients(){
  screen("Clientes")
  heading("Clientes registrados","Consulta, edita y administra tu agenda de clientes.")
  button("＋ Nuevo cliente"){manualNewClient()}
  label("Buscar cliente")
  val search=input("Nombre, RUC, DNI, teléfono o correo")
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(list)
  fun refresh(){
   list.removeAllViews()
   val term=search.text.toString().trim()
   val cur=db.readableDatabase.rawQuery(
    "SELECT id,name,document,address,phone,email FROM clients ORDER BY name COLLATE NOCASE,id DESC",null)
   var matches=0
   try{
    while(cur.moveToNext()){
     val id=cur.getLong(0)
     val name=cur.getString(1)?:""
     val document=cur.getString(2)?:""
     val address=cur.getString(3)?:""
     val phone=cur.getString(4)?:""
     val email=cur.getString(5)?:""
     if(term.isNotBlank()&&!listOf(name,document,address,phone,email)
       .any{it.contains(term,ignoreCase=true)})continue
     matches++
     val entry=card()
     entry.addView(line(name.ifBlank{"Cliente sin nombre"},17f,navy,true))
     entry.addView(line("Documento: "+document.ifBlank{"No indicado"},13f,muted))
     if(phone.isNotBlank())entry.addView(line("Teléfono: "+phone,13f,ink))
     if(email.isNotBlank())entry.addView(line("Correo: "+email,13f,ink))
     if(address.isNotBlank())entry.addView(line("Principal: "+address,12f,muted))
     val actions=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
     fun action(title:String,click:()->Unit){
      val v=line(title,13f,green,true).apply{
       gravity=Gravity.CENTER;minHeight=d(43)
       isClickable=true;isFocusable=true
       setOnClickListener{click()}
       background=round(pale,10,border)
      }
      actions.addView(v,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(d(3),d(10),d(3),0)})
     }
     action("Ver"){clientDetail(id)}
     action("Editar"){clientEditor(id)}
     action("Eliminar"){deleteClient(id)}
     entry.addView(actions)
     list.addView(entry,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(8),0,d(8))})
    }
   }finally{cur.close()}
   if(matches==0)list.addView(line("No se encontraron clientes.",14f,muted))
  }
  search.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()}
   override fun afterTextChanged(s:Editable?){}
  })
  refresh()
 }

 private fun deleteClient(id:Long){
  val cur=db.readableDatabase.rawQuery("SELECT name FROM clients WHERE id=?",arrayOf(id.toString()))
  val name=try{if(cur.moveToFirst())cur.getString(0)?:"este cliente" else "este cliente"}finally{cur.close()}
  AlertDialog.Builder(this).setTitle("Eliminar cliente")
   .setMessage("¿Eliminar a "+name+" y sus direcciones? El historial de cotizaciones ya guardadas no se modificará.")
   .setNegativeButton("Cancelar",null)
   .setPositiveButton("Eliminar"){_,_->
    val database=db.writableDatabase
    try{
     database.beginTransaction()
     database.delete("client_addresses","client_id=?",arrayOf(id.toString()))
     database.delete("clients","id=?",arrayOf(id.toString()))
     database.setTransactionSuccessful()
     toast("Cliente eliminado")
    }catch(_:Exception){toast("No se pudo eliminar el cliente.")}
    finally{if(database.inTransaction())database.endTransaction()}
    clients()
   }.show()
 }

 private fun clientDetail(id:Long){
  val cur=db.readableDatabase.rawQuery("SELECT name,document,phone,email,notes,is_supplier,is_driver,is_carrier FROM clients WHERE id=?",
   arrayOf(id.toString()))
  val details=try{
   if(cur.moveToFirst())(0..7).map{i->cur.getString(i)?:""} else null
  }finally{cur.close()}
  if(details==null){toast("No se encontró el cliente.");clients();return}
  screen("Detalle del cliente")
  heading(details[0], "Documento: "+details[1].ifBlank{"No indicado"})
  button("✎ Editar contacto"){clientEditor(id)}
  heading("Información de contacto")
  info("Teléfono",details[2].ifBlank{"Sin registrar"})
  info("Correo electrónico",details[3].ifBlank{"Sin registrar"})
  info("Observaciones",details[4].ifBlank{"Sin observaciones"})
  heading("Direcciones")
  val curAddr=db.readableDatabase.rawQuery(
   "SELECT label,address,ubigeo,is_main FROM client_addresses WHERE client_id=? ORDER BY is_main DESC,id",
   arrayOf(id.toString()))
  var count=0
  try{while(curAddr.moveToNext()){
   count++
   val name=curAddr.getString(0)?:"Dirección"
   val address=curAddr.getString(1)?:""
   val ubigeo=curAddr.getString(2)?:""
   val primary=curAddr.getInt(3)==1
   info(name+(if(primary)" · Principal" else ""),address+
    if(ubigeo.isNotBlank())"\nUbigeo: "+ubigeo else "")
  }}finally{curAddr.close()}
  if(count==0)info("Direcciones","Sin direcciones registradas")
  heading("Opciones")
  info("Proveedor",if(details[5]=="1")"Sí" else "No")
  info("Conductor",if(details[6]=="1")"Sí" else "No")
  info("Empresa de transporte",if(details[7]=="1")"Sí" else "No")
  button("Eliminar cliente"){deleteClient(id)}
  button("← Volver a clientes"){clients()}
 }


 // Registro rápido sin depender de SUNAT ni de endpoint.pe.
 // Los tres datos obligatorios corresponden a DOCUMENTO, CLIENTE y DIRECCIÓN
 // que se imprimen en el modelo de proforma BELSIPERU.
 private fun manualNewClient(returnToQuote:Boolean=false){
  screen("Nuevo cliente")
  button(if(returnToQuote)"← Volver a cotización" else "← Volver a clientes"){
   if(returnToQuote)newQuote() else clients()
  }
  heading("Registro manual de cliente",
   "Completa los datos marcados con * y guarda el contacto. No es obligatorio consultar DNI o RUC.")
  label("DNI o RUC *")
  val doc=input("Ingresa 8 dígitos para DNI u 11 para RUC")
  doc.inputType=InputType.TYPE_CLASS_NUMBER
  doc.filters=arrayOf(android.text.InputFilter.LengthFilter(11))
  val status=line("Puedes ingresar el documento manualmente y guardar sin consultar.",12f,muted)
  body.addView(status)
  doc.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    val value=s?.toString()?.trim().orEmpty()
    status.text=when{
     value.matches(Regex("[0-9]{8}"))->"DNI detectado (8 dígitos). Puedes guardar o consultar."
     value.matches(Regex("[0-9]{11}"))->"RUC detectado (11 dígitos). Puedes guardar o consultar."
     value.isBlank()->"Ingresa un DNI o un RUC."
     else->"Documento incompleto. DNI: 8 dígitos · RUC: 11 dígitos."
    }
   }
   override fun afterTextChanged(s:Editable?){}
  })
  label("Nombres y apellidos / Razón social *")
  val name=input("Nombre completo o razón social")
  label("Dirección principal para la proforma *")
  val address=input("Calle, número, urbanización, distrito, provincia")
  address.setSingleLine(false)
  address.minLines=2
  address.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
   InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
  button("Consultar DNI / RUC (opcional)"){
   val document=doc.text.toString().trim()
   when{
    document.matches(Regex("[0-9]{8}"))->{
     status.text="Consultando DNI…"
     consultaDni(document){data,error->
      if(doc.text.toString().trim()!=document){
       status.text="El documento cambió. Vuelve a consultar."
      }else if(data!=null){
       name.setText(data.nombre)
       status.text="DNI consultado. Completa manualmente la dirección principal."
      }else status.text=(error?:"No se pudo consultar el DNI.")+
       " Puedes guardar los datos manualmente."
     }
    }
    document.matches(Regex("[0-9]{11}"))->{
     status.text="Consultando RUC…"
     consultaRuc(document){data,error->
      if(doc.text.toString().trim()!=document){
       status.text="El documento cambió. Vuelve a consultar."
      }else if(data!=null){
       name.setText(data.razon)
       if(address.text.toString().isBlank())address.setText(data.direccion)
       status.text="RUC consultado. Revisa los datos antes de guardar."
      }else status.text=(error?:"No se pudo consultar el RUC.")+
       " Puedes guardar los datos manualmente."
     }
    }
    else->{doc.error="Ingresa un DNI de 8 dígitos o un RUC de 11 dígitos."}
   }
  }
  heading("Datos de contacto","Estos campos son opcionales y no impiden generar una proforma.")
  label("Teléfono")
  val phone=input("Celular o teléfono")
  phone.inputType=InputType.TYPE_CLASS_PHONE
  label("Correo electrónico")
  val email=input("cliente@correo.com")
  email.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
  label("Observaciones")
  val notes=EditText(this).apply{
   hint="Referencia de entrega u otras observaciones (opcional)"
   textSize=15f;setTextColor(ink);setHintTextColor(muted)
   setPadding(d(13),d(12),d(13),d(12))
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
   setSingleLine(false);minLines=3;gravity=Gravity.TOP
   background=round(Color.WHITE,12,border)
  }
  body.addView(notes,LinearLayout.LayoutParams(-1,d(112)).apply{
   setMargins(0,d(4),0,d(8))
  })
  button("✓ Guardar cliente manualmente"){
   val documento=doc.text.toString().trim()
   val nombre=name.text.toString().trim()
   val direccion=address.text.toString().trim()
   val correo=email.text.toString().trim()
   if(!documento.matches(Regex("[0-9]{8}|[0-9]{11}"))){
    doc.error="El DNI debe tener 8 dígitos o el RUC 11."
    doc.requestFocus();toast("Ingresa un DNI o RUC válido.");return@button
   }
   if(nombre.isBlank()){
    name.error="Este nombre es obligatorio para el PDF."
    name.requestFocus();toast("Ingresa el nombre o razón social.");return@button
   }
   if(direccion.isBlank()){
    address.error="La dirección principal es obligatoria para la proforma."
    address.requestFocus();toast("Ingresa la dirección principal.");return@button
   }
   if(correo.isNotBlank()&&!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()){
    email.error="Revisa el correo electrónico.";email.requestFocus();return@button
   }
   val database=db.writableDatabase
   val existing=database.rawQuery("SELECT id FROM clients WHERE document=? LIMIT 1",
    arrayOf(documento))
   val existingId=try{if(existing.moveToFirst())existing.getLong(0) else null}
    finally{existing.close()}
   if(existingId!=null){
    AlertDialog.Builder(this).setTitle("El cliente ya existe")
     .setMessage("Ese DNI o RUC ya está registrado. Abre su ficha para editarlo, en lugar de crear un duplicado.")
     .setNegativeButton("Cancelar",null)
     .setPositiveButton("Editar cliente"){_,_->clientEditor(existingId,returnToQuote)}
     .show()
    return@button
   }
   val values=android.content.ContentValues().apply{
    put("name",nombre);put("document",documento);put("address",direccion)
    put("phone",phone.text.toString().trim());put("email",correo)
    put("notes",notes.text.toString().trim())
   }
   var savedClientId:Long?=null
   try{
    database.beginTransaction()
    val id=database.insertOrThrow("clients",null,values)
    database.insertOrThrow("client_addresses",null,android.content.ContentValues().apply{
     put("client_id",id);put("label","Principal")
     put("address",direccion);put("ubigeo","");put("is_main",1)
    })
    database.setTransactionSuccessful()
    savedClientId=id
   }catch(e:Exception){toast("No se pudo guardar el cliente: "+(e.message?:"Error"))}
   finally{if(database.inTransaction())database.endTransaction()}
   if(savedClientId!=null){
    toast("Cliente guardado correctamente. Disponible para cotizaciones.")
    if(returnToQuote)newQuote(savedClientId) else clients()
   }
  }
  label("Más direcciones, ubigeo y clasificación: abre Editar después de guardar al cliente.")
 }
 private fun clientEditor(editId:Long?=null,returnToQuote:Boolean=false){
  val saved=mutableMapOf<String,String>()
  if(editId!=null){
   val old=db.readableDatabase.rawQuery("SELECT * FROM clients WHERE id=?",arrayOf(editId.toString()))
   try{
    if(!old.moveToFirst()){toast("No se encontró el cliente.");clients();return}
    for(i in 0 until old.columnCount)saved[old.getColumnName(i)]=old.getString(i)?:""
   }finally{old.close()}
  }
  fun previous(key:String)=saved[key]?:""
  val addresses=mutableListOf<ClientAddress>()
  if(editId!=null){
   val ac=db.readableDatabase.rawQuery(
    "SELECT id,label,address,ubigeo,is_main FROM client_addresses WHERE client_id=? ORDER BY is_main DESC,id",
    arrayOf(editId.toString()))
   try{
    while(ac.moveToNext()){
     addresses.add(ClientAddress(ac.getLong(0),ac.getString(1)?:"Principal",
      ac.getString(2)?:"",ac.getString(3)?:"",ac.getInt(4)==1))
    }
   }finally{ac.close()}
   // También protege direcciones de registros creados en versiones anteriores.
   if(addresses.isEmpty()&&previous("address").isNotBlank()){
    addresses.add(ClientAddress(null,"Principal",previous("address"),"",true))
   }
  }
  if(addresses.isNotEmpty()&&addresses.none{it.isMain})addresses[0].isMain=true
  screen(if(editId==null)"Nuevo cliente" else "Editar contacto")
  button(if(returnToQuote)"← Volver a cotización" else "← Volver a clientes"){
   if(returnToQuote)newQuote() else clients()
  }
  heading("Datos del contacto","Puedes escribir los datos sin usar la consulta DNI/RUC. El documento, el nombre y la dirección son necesarios para la proforma.")
  label("Número de documento")
  val doc=input("DNI (8) o RUC (11)")
  doc.inputType=InputType.TYPE_CLASS_NUMBER
  doc.filters=arrayOf(android.text.InputFilter.LengthFilter(11))
  val status=line("Escribe 8 dígitos para DNI u 11 para RUC.",12f,muted)
  body.addView(status)
  label("Nombre o razón social *")
  val name=input("Nombre o razón social")
  name.setText(previous("name"))
  label("Dirección principal para la proforma *")
  val mainAddress=input("Dirección completa de entrega o fiscal")
  mainAddress.setSingleLine(false)
  mainAddress.minLines=2
  mainAddress.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
   InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
  mainAddress.setText(addresses.firstOrNull{it.isMain}?.address?:previous("address"))
  label("Correo electrónico")
  val email=input("nombre@correo.com")
  email.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
  email.setText(previous("email"))
  label("Teléfono")
  val phone=input("Número de teléfono, p. ej. 999 999 999")
  phone.inputType=InputType.TYPE_CLASS_PHONE
  phone.setText(previous("phone"))
  label("Observaciones")
  val notes=EditText(this).apply{
   hint="Notas e indicaciones del cliente (opcional)"
   textSize=15f;setTextColor(ink);setHintTextColor(muted)
   setPadding(d(13),d(12),d(13),d(12))
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
   setSingleLine(false);minLines=3;gravity=Gravity.TOP
   background=round(Color.WHITE,12,border)
   setText(previous("notes"))
  }
  body.addView(notes,LinearLayout.LayoutParams(-1,d(112)).apply{setMargins(0,d(4),0,d(8))})
  heading("Direcciones","Agrega, modifica o elimina direcciones. Una será la principal.")
  val addressList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(addressList)
  lateinit var redrawAddresses:()->Unit

  fun editAddress(index:Int?){
   val current=if(index!=null)addresses.getOrNull(index) else null
   val fields=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL
    setPadding(d(16),d(6),d(16),d(6))
   }
   fun field(title:String,value:String,multiline:Boolean=false):EditText{
    fields.addView(line(title,14f,navy,true))
    return EditText(this).apply{
     hint=title;setText(value);textSize=15f;setTextColor(ink)
     if(multiline){setSingleLine(false);minLines=2}
     else{setSingleLine(true)}
     setPadding(d(10),d(8),d(10),d(8))
     background=round(Color.WHITE,10,border)
     fields.addView(this,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(3),0,d(10))})
    }
   }
   val tag=field("Tipo (Casa, Principal, Sucursal, Almacén)",current?.label?:"")
   val address=field("Dirección completa *",current?.address?:"",true)
   val ubigeo=field("Ubigeo (opcional)",current?.ubigeo?:"")
   ubigeo.inputType=InputType.TYPE_CLASS_NUMBER
   ubigeo.filters=arrayOf(android.text.InputFilter.LengthFilter(6))
   val main=CheckBox(this).apply{
    text="Utilizar como dirección principal"
    isChecked=current?.isMain?:addresses.isEmpty()
   }
   fields.addView(main)
   val scroll=ScrollView(this).apply{addView(fields)}
   val dialog=AlertDialog.Builder(this)
    .setTitle(if(index==null)"Agregar dirección" else "Editar dirección")
    .setView(scroll).setNegativeButton("Cancelar",null)
    .setPositiveButton("Guardar",null).create()
   dialog.setOnShowListener{
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
     val addressText=address.text.toString().trim()
     if(addressText.isBlank()){
      address.error="Ingresa la dirección"
     }else{
      val newIsMain=main.isChecked||addresses.isEmpty()
      if(newIsMain)addresses.forEach{it.isMain=false}
      val item=ClientAddress(current?.id,
       tag.text.toString().trim().ifBlank{if(newIsMain)"Principal" else "Otra dirección"},
       addressText,ubigeo.text.toString().trim(),newIsMain)
      if(index==null)addresses.add(item) else addresses[index]=item
      if(addresses.isNotEmpty()&&addresses.none{it.isMain})addresses[0].isMain=true
      redrawAddresses();dialog.dismiss()
     }
    }
   }
   dialog.show()
  }
  redrawAddresses={
   addressList.removeAllViews()
   if(addresses.isEmpty()){
    addressList.addView(line("No hay direcciones. Puedes agregar una cuando lo necesites.",13f,muted))
   }
   addresses.forEachIndexed{index,item->
    val entry=card()
    entry.addView(line(item.label+(if(item.isMain)" · Principal" else ""),15f,navy,true))
    entry.addView(line(item.address,14f,ink))
    entry.addView(line(if(item.ubigeo.isBlank())"Sin ubigeo" else "Ubigeo: "+item.ubigeo,12f,muted))
    val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    fun action(t:String,task:()->Unit){
     val link=line(t,13f,green,true).apply{
      gravity=Gravity.CENTER;minHeight=d(42)
      isClickable=true;isFocusable=true;setOnClickListener{task()}
     }
     row.addView(link,LinearLayout.LayoutParams(0,-2,1f))
    }
    action("Editar"){editAddress(index)}
    if(!item.isMain)action("Principal"){
     addresses.forEach{it.isMain=false}
     addresses[index].isMain=true
     redrawAddresses()
    }
    action("Eliminar"){
     AlertDialog.Builder(this).setTitle("Eliminar dirección")
      .setMessage("¿Quitar esta dirección de la ficha del cliente?")
      .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar"){_,_->
       addresses.removeAt(index)
       if(addresses.isNotEmpty()&&addresses.none{it.isMain})addresses[0].isMain=true
       redrawAddresses()
      }.show()
    }
    entry.addView(row)
    addressList.addView(entry,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(5),0,d(7))})
   }
  }
  redrawAddresses()
  button("＋ Agregar dirección"){editAddress(null)}
  heading("Opciones")
  val supplier=Switch(this).apply{text="¿Es un proveedor?";isChecked=previous("is_supplier")=="1"}
  val driver=Switch(this).apply{text="¿Es un conductor?";isChecked=previous("is_driver")=="1"}
  val carrier=Switch(this).apply{text="¿Es una empresa de transporte?";isChecked=previous("is_carrier")=="1"}
  for(opt in listOf(supplier,driver,carrier)){
   opt.textSize=15f;opt.setTextColor(ink)
   opt.setPadding(d(9),d(9),d(9),d(9))
   body.addView(opt,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(2),0,d(2))})
  }
  doc.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    val value=s?.toString()?.trim().orEmpty()
    status.text=when{
     value.matches(Regex("[0-9]{8}"))->"DNI detectado (8 dígitos). Pulsa Consultar documento."
     value.matches(Regex("[0-9]{11}"))->"RUC detectado (11 dígitos). Pulsa Consultar documento."
     value.isBlank()->"Escribe 8 dígitos para DNI u 11 para RUC."
     else->"Documento incompleto: DNI de 8 o RUC de 11 dígitos."
    }
   }
   override fun afterTextChanged(s:Editable?){}
  })
  doc.setText(previous("document"))
  button("Consultar documento"){
   val document=doc.text.toString().trim()
   when{
    document.matches(Regex("[0-9]{8}"))->{
     status.text="Consultando DNI…"
     consultaDni(document){data,error->
      if(doc.text.toString().trim()!=document){
       status.text="El documento cambió durante la consulta. Vuelve a consultar."
      }else if(data!=null){
       name.setText(data.nombre)
       status.text="DNI consultado. Revisa los nombres y guarda el cliente."
      }else{
       status.text=error?:"No se pudo consultar el DNI."
       if(error?.contains("Configura primero")==true){
        AlertDialog.Builder(this).setTitle("Configurar consultas")
         .setMessage("Configura Cloudflare para consultar DNI y RUC. Puedes registrar el cliente manualmente.")
         .setNegativeButton("Después",null)
         .setPositiveButton("Configurar"){_,_->rucSettings()}.show()
       }
      }
     }
    }
    document.matches(Regex("[0-9]{11}"))->{
     status.text="Consultando RUC…"
     consultaRuc(document){data,error->
      if(doc.text.toString().trim()!=document){
       status.text="El documento cambió durante la consulta. Vuelve a consultar."
      }else if(data!=null){
       name.setText(data.razon)
       var applied=false
       if(data.direccion.isNotBlank()){
        val mainIdx=addresses.indexOfFirst{it.isMain}
        if(mainIdx<0){
         addresses.add(ClientAddress(null,"Principal",data.direccion,"",true))
         applied=true
        }else if(addresses[mainIdx].address.isBlank()){
         addresses[mainIdx].address=data.direccion
         applied=true
        }
       }
       if(applied){
        mainAddress.setText(addresses.firstOrNull{it.isMain}?.address?:data.direccion)
        redrawAddresses()
       }
       status.text="RUC consultado: "+data.estado.ifBlank{"Sin estado"}+
        " · "+data.condicion.ifBlank{"Sin condición"}+
        (if(applied)". Dirección principal agregada." else ". Dirección existente conservada.")
       if(data.estado.uppercase(Locale.ROOT)!="ACTIVO"){
        AlertDialog.Builder(this).setTitle("Estado del contribuyente")
         .setMessage("El RUC figura como "+data.estado+". Comprueba su situación.")
         .setPositiveButton("Entendido",null).show()
       }
      }else{
       status.text=error?:"No se pudo consultar el RUC."
       if(error?.contains("Configura primero")==true){
        AlertDialog.Builder(this).setTitle("Configurar consultas")
         .setMessage("Configura Cloudflare para consultar DNI y RUC. Puedes registrar el cliente manualmente.")
         .setNegativeButton("Después",null)
         .setPositiveButton("Configurar"){_,_->rucSettings()}.show()
       }
      }
     }
    }
    else->status.text="Ingresa un DNI de 8 dígitos o RUC de 11 dígitos."
   }
  }
  // El botón de consulta se sitúa junto al campo DNI/RUC sin alterar su callback.
  val consultButton=body.getChildAt(body.childCount-1)
  body.removeView(consultButton)
  body.addView(consultButton,body.indexOfChild(status)+1)
  button(if(editId==null)"✓ Guardar cliente" else "✓ Guardar cambios"){
   val nombre=name.text.toString().trim()
   val documento=doc.text.toString().trim()
   val correo=email.text.toString().trim()
   if(nombre.isBlank()){toast("Ingresa el nombre o razón social.");return@button}
   if(!documento.matches(Regex("[0-9]{8}|[0-9]{11}"))){
    doc.error="El DNI necesita 8 dígitos o el RUC 11."
    toast("Ingresa un DNI o RUC para la proforma.");return@button
   }
   val principal=mainAddress.text.toString().trim()
   if(principal.isBlank()){
    mainAddress.error="La dirección principal es necesaria para el PDF."
    toast("Ingresa la dirección principal del cliente.");return@button
   }
   val mainIndex=addresses.indexOfFirst{it.isMain}
   if(mainIndex>=0)addresses[mainIndex].address=principal
   else{
    addresses.forEach{it.isMain=false}
    addresses.add(0,ClientAddress(null,"Principal",principal,"",true))
   }
   if(correo.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()){
    email.error="Revisa el correo electrónico";return@button
   }
   val database=db.writableDatabase
   if(documento.isNotBlank()){
    val duplicate=database.rawQuery("SELECT id FROM clients WHERE document=? AND id<>? LIMIT 1",
     arrayOf(documento,(editId?:-1L).toString()))
    val exists=try{duplicate.moveToFirst()}finally{duplicate.close()}
    if(exists){toast("Este documento ya pertenece a otro cliente. Edítalo desde la lista.");return@button}
   }
   val primary=addresses.firstOrNull{it.isMain}?.address
    ?:addresses.firstOrNull()?.address?:""
   val values=android.content.ContentValues().apply{
    put("name",nombre);put("document",documento);put("address",primary)
    put("phone",phone.text.toString().trim());put("email",correo)
    put("notes",notes.text.toString().trim())
    put("is_supplier",if(supplier.isChecked)1 else 0)
    put("is_driver",if(driver.isChecked)1 else 0)
    put("is_carrier",if(carrier.isChecked)1 else 0)
   }
   var success=false
   var savedClientId:Long?=null
   try{
    database.beginTransaction()
    val clientId=if(editId==null){
     database.insertOrThrow("clients",null,values)
    }else{
     val updated=database.update("clients",values,"id=?",arrayOf(editId.toString()))
     if(updated!=1)throw IllegalStateException("Cliente no encontrado")
     editId
    }
    // Actualiza las direcciones de forma atómica, manteniendo una única principal.
    database.delete("client_addresses","client_id=?",arrayOf(clientId.toString()))
    addresses.forEachIndexed{index,a->
     database.insertOrThrow("client_addresses",null,android.content.ContentValues().apply{
      put("client_id",clientId)
      put("label",a.label);put("address",a.address);put("ubigeo",a.ubigeo)
      put("is_main",if(a.isMain || (index==0 && addresses.none{it.isMain}))1 else 0)
     })
    }
    database.setTransactionSuccessful();savedClientId=clientId;success=true
   }catch(e:Exception){
    toast("No se pudo guardar el cliente: "+(e.message?:"Error inesperado"))
   }finally{if(database.inTransaction())database.endTransaction()}
   if(success){
    toast(if(editId==null)"Cliente registrado" else "Cliente actualizado")
    if(returnToQuote)newQuote(savedClientId) else clients()
   }
  }
  button("Configurar consulta RUC / DNI"){rucSettings()}
 }

 private data class CatalogQuoteProduct(
  val id:Long,val code:String,val name:String,val price:Double,val currency:String,
  val saleIncludesIgv:Boolean,val taxType:String,val photoUri:String?,val kind:String,
  val unit:String,val barcode:String,val brand:String,val category:String,val stock:Int
 )
 private data class QuoteLineDraft(
  val product:CatalogQuoteProduct,val quantity:Int,val netUnitPrice:Double,
  val discountPct:Double,val discountAmount:Double,val applyIgv:Boolean,
  val includePhoto:Boolean
 )
 private fun quoteLineNumbers(line:QuoteLineDraft,target:String,rate:Double):Pair<Double,Double>{
  val factor=if(line.product.currency==target)1.0 else if(line.product.currency=="USD")rate else 1.0/rate
  val base=(line.quantity*line.netUnitPrice*(1.0-line.discountPct/100.0)-line.discountAmount)
   .coerceAtLeast(0.0)*factor
  val taxable=line.product.taxType=="GRAVADO"&&line.applyIgv
  return Pair(base,base*(if(taxable)1.18 else 1.0))
 }
 private fun newQuote(preselectedClientId:Long?=null,editQuoteId:Long?=null){
  if(!companyConfigured()){companySetup();return}
  var editClientId:Long?=null
  var editCurrency="PEN"
  if(editQuoteId!=null){
   val existing=db.readableDatabase.rawQuery(
    "SELECT client_id,client,currency,status FROM quotes WHERE id=?",
    arrayOf(editQuoteId.toString()))
   try{
    if(!existing.moveToFirst()){
     toast("No se encontró la proforma.");quotes();return
    }
    if(existing.getString(3)!="PENDIENTE"){
     toast("Solo se pueden editar proformas pendientes.");quotes();return
    }
    editClientId=if(existing.isNull(0))null else existing.getLong(0)
    if(editClientId==null){
     val matches=db.readableDatabase.rawQuery(
      "SELECT id FROM clients WHERE name=? ORDER BY id DESC LIMIT 1",
      arrayOf(existing.getString(1)?:""))
     try{if(matches.moveToFirst())editClientId=matches.getLong(0)}
      finally{matches.close()}
    }
    editCurrency=existing.getString(2)?:"PEN"
   }finally{existing.close()}
  }
  screen(if(editQuoteId==null)"Nueva cotización" else
   "Editar proforma "+quoteDisplayNumber(editQuoteId))
  button("← Inicio"){home()}
  heading("Información del cliente","Busca un cliente ya registrado por nombre, RUC o DNI.")
  label("Buscar cliente")
  val clientSearch=input("Nombre, DNI o RUC")
  val clientInfo=line("Selecciona un cliente de la lista para continuar.",13f,muted)
  body.addView(clientInfo)
  val clientResults=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(clientResults)
  var selectedClientId:Long?=null
  var selectedClientName=""
  var changingClient=false
  val customers=mutableListOf<Triple<Long,String,String>>()
  val customerCursor=db.readableDatabase.rawQuery(
   "SELECT id,name,document FROM clients ORDER BY name COLLATE NOCASE",null)
  try{while(customerCursor.moveToNext()){
   customers.add(Triple(customerCursor.getLong(0),customerCursor.getString(1)?:"",
    customerCursor.getString(2)?:""))
  }}finally{customerCursor.close()}
  fun renderClientResults(){
   clientResults.removeAllViews()
   val query=clientSearch.text.toString().trim()
   if(query.isBlank()){
    clientResults.addView(line("Escribe un nombre o documento para buscar entre tus clientes.",12f,muted))
    return
   }
   val found=customers.filter{it.second.contains(query,ignoreCase=true)||it.third.contains(query)}
   if(found.isEmpty()){
    clientResults.addView(line("No hay clientes que coincidan. Puedes registrar uno nuevo.",13f,muted))
   }
   for(item in found.take(15)){
    val row=line(item.second+"  ·  "+item.third,14f,navy,true).apply{
     setPadding(d(12),d(12),d(12),d(12))
     background=round(Color.WHITE,12,border)
     isClickable=true;isFocusable=true
     setOnClickListener{
      selectedClientId=item.first
      selectedClientName=item.second
      changingClient=true
      clientSearch.setText(item.second)
      changingClient=false
      clientInfo.text="Cliente seleccionado: "+item.second+
       (if(item.third.isNotBlank())" · "+item.third else "")
      clientInfo.setTextColor(green)
      clientResults.removeAllViews()
     }
    }
    clientResults.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(3),0,d(3))})
   }
   if(found.size>15)clientResults.addView(line(
    "Hay "+found.size+" coincidencias. Escribe más letras para filtrar.",12f,muted))
  }
  clientSearch.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    if(!changingClient){
     selectedClientId=null;selectedClientName=""
     clientInfo.text="Elige un cliente de los resultados."
     clientInfo.setTextColor(muted)
     renderClientResults()
    }
   }
   override fun afterTextChanged(s:Editable?){}
  })
  (preselectedClientId?:editClientId)?.let{id->
   customers.firstOrNull{it.first==id}?.let{saved->
    changingClient=true
    clientSearch.setText(saved.second)
    changingClient=false
    selectedClientId=saved.first;selectedClientName=saved.second
    clientInfo.text="Cliente seleccionado: "+saved.second+
     (if(saved.third.isNotBlank())" · "+saved.third else "")
    clientInfo.setTextColor(green)
   }
  }
  renderClientResults()
  button("＋ Registrar cliente nuevo"){manualNewClient(returnToQuote=true)}

  heading("Productos de la cotización","Busca por nombre, código, marca o código de barras y agrégalos a la proforma.")
  label("Moneda de la cotización")
  val currency=choice(listOf("PEN","USD"))
  currency.setSelection(if(editCurrency=="USD")1 else 0)
  label("Tipo de cambio (S/ por US$)")
  val fx=input("Ejemplo: 3.75",true)
  fx.setText("3.75")
  label("Buscar producto registrado")
  val productSearch=input("Nombre, código, código de barras o marca")
  button("📷 Escanear código de barras"){scanBarcode(productSearch)}
  val catalog=mutableListOf<CatalogQuoteProduct>()
  val productCursor=db.readableDatabase.rawQuery(
   "SELECT id,code,name,price,currency,sale_includes_igv,tax_affectation,photo_uri,kind,unit,barcode,brand,category,stock FROM products ORDER BY name COLLATE NOCASE",
   null)
  try{while(productCursor.moveToNext()){
   catalog.add(CatalogQuoteProduct(
    productCursor.getLong(0),productCursor.getString(1)?:"",productCursor.getString(2)?:"",
    productCursor.getDouble(3),productCursor.getString(4)?:"PEN",
    productCursor.getInt(5)==1,productCursor.getString(6)?:"GRAVADO",
    productCursor.getString(7),productCursor.getString(8)?:"PRODUCTO",
    productCursor.getString(9)?:"UNIDADES",productCursor.getString(10)?:"",
    productCursor.getString(11)?:"",productCursor.getString(12)?:"",
    productCursor.getInt(13)
   ))
  }}finally{productCursor.close()}
  val picked=mutableListOf<QuoteLineDraft>()
  if(editQuoteId!=null){
   val saved=db.readableDatabase.rawQuery(
    "SELECT product_id,code,name,unit,qty,unit_price,discount_pct,"+
    "discount_amount,igv,photo_uri,include_product_photo,tax_affectation "+
    "FROM quote_items WHERE quote_id=? ORDER BY id",
    arrayOf(editQuoteId.toString()))
   try{while(saved.moveToNext()){
    val pid=if(saved.isNull(0))null else saved.getLong(0)
    val code=saved.getString(1)?:""
    val name=saved.getString(2)?:"Producto"
    val unit=saved.getString(3)?:"UNIDADES"
    val photoUri=saved.getString(9)
    val item=pid?.let{p->catalog.firstOrNull{it.id==p}}
     ?:CatalogQuoteProduct(-1L,code,name,saved.getDouble(5),
      editCurrency,false,saved.getString(11)?:"INAFECTO",
      photoUri,"PRODUCTO",unit,"","","",0)
    val price=saved.getDouble(5)
    val effective=item.copy(currency=editCurrency,price=price,
     code=code,name=name,unit=unit,photoUri=photoUri?:item.photoUri)
    picked.add(QuoteLineDraft(effective,saved.getInt(4),price,
     saved.getDouble(6),saved.getDouble(7),saved.getInt(8)==1,
     saved.getInt(10)==1))
   }}finally{saved.close()}
  }
  val selectedList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val totalView=line("Total: S/ 0.00",20f,green,true)
  val resultsList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  var editPickedLine:(Int)->Unit={}
  fun targetCurrency()=currency.selectedItem?.toString()?:"PEN"
  fun currentRate()=fx.text.toString().toDoubleOrNull()?:0.0
  fun money(value:Double)=String.format(Locale.US,"%,.2f",value)
  fun refreshPicked(){
   selectedList.removeAllViews()
   val target=targetCurrency();val rate=currentRate()
   var total=0.0
   if(picked.isEmpty())selectedList.addView(line("Todavía no agregaste productos.",13f,muted))
   for((i,draft) in picked.withIndex()){
    val subtotal=if(rate>0)quoteLineNumbers(draft,target,rate).second else 0.0
    total+=subtotal
    val entry=card()
    entry.addView(line(draft.product.name,15f,navy,true))
    entry.addView(line(
     draft.quantity.toString()+" "+draft.product.unit+" × "+draft.product.currency+" "+
     money(draft.netUnitPrice)+" · Desc. "+money(draft.discountPct)+" %",12f,muted))
    entry.addView(line("Total: "+target+" "+money(subtotal),14f,green,true))
    val remove=line("Quitar",13f,Color.rgb(162,45,38),true).apply{
     setPadding(d(8),d(9),d(8),d(9))
     isClickable=true;isFocusable=true
     setOnClickListener{picked.removeAt(i);refreshPicked()}
    }
    val editControl=line("✎ Editar producto",13f,navy,true).apply{
     setPadding(d(8),d(10),d(8),d(10))
     isClickable=true;isFocusable=true
     setOnClickListener{editPickedLine(i)}
    }
    entry.addView(editControl)
    entry.addView(remove)
    selectedList.addView(entry,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(5),0,d(7))})
   }
   totalView.text="TOTAL "+target+" "+money(total)
  }
  fun openProduct(product:CatalogQuoteProduct,editIndex:Int?=null){
   val existingLine=editIndex?.let{picked.getOrNull(it)}
   val form=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL;setPadding(d(15),d(6),d(15),d(6))
   }
   fun field(title:String,initial:String):EditText{
    form.addView(line(title,13f,navy,true))
    return EditText(this).apply{
     hint=title;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
     setSingleLine(true);setText(initial)
     background=round(Color.WHITE,10,border)
     setPadding(d(10),d(8),d(10),d(8))
     form.addView(this,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(3),0,d(9))})
    }
   }
   val variants=mutableListOf(Pair("Precio de venta",product.price))
   val variantsCursor=db.readableDatabase.rawQuery(
    "SELECT label,price FROM product_price_variants WHERE product_id=? ORDER BY id",
    arrayOf(product.id.toString()))
   try{while(variantsCursor.moveToNext()){
    variants.add(Pair(variantsCursor.getString(0)?:"Variante",variantsCursor.getDouble(1)))
   }}finally{variantsCursor.close()}
   form.addView(line("Tarifa del catálogo ("+product.currency+")",13f,navy,true))
   val priceSpinner=Spinner(this).apply{
    adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,
     variants.map{it.first+" — "+money(it.second)})
   }
   form.addView(priceSpinner)
   val qty=field("Cantidad",existingLine?.quantity?.toString()?:"1")
   qty.inputType=InputType.TYPE_CLASS_NUMBER
   val starting=if(product.saleIncludesIgv&&product.taxType=="GRAVADO")
    product.price/1.18 else product.price
   val price=field("Precio unitario sin IGV ("+product.currency+")",
    String.format(Locale.US,"%.4f",existingLine?.netUnitPrice?:starting))
   var chosenPriceVariant=editIndex==null
   priceSpinner.setOnTouchListener{_,_->chosenPriceVariant=true;false}
   priceSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
    override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
    override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){
     if(!chosenPriceVariant)return
     val nominal=variants[position].second
     val net=if(product.saleIncludesIgv&&product.taxType=="GRAVADO")nominal/1.18 else nominal
     price.setText(String.format(Locale.US,"%.4f",net))
    }
   }
   val discount=field("Descuento (%)",existingLine?.discountPct?.toString()?:"0")
   val amount=field("Descuento en monto ("+product.currency+")",
    existingLine?.discountAmount?.toString()?:"0")
   val applyIgv=CheckBox(this).apply{
    text="Aplicar IGV 18 %"
    isChecked=existingLine?.applyIgv?: (product.taxType=="GRAVADO")
    isEnabled=product.taxType=="GRAVADO"
   }
   form.addView(applyIgv)
   if(product.taxType!="GRAVADO")
    form.addView(line("Afectación: "+product.taxType+". No se aplicará IGV.",12f,muted))
   val photo=CheckBox(this).apply{
    text="Incluir fotografía del catálogo en el PDF"
    isChecked=existingLine?.includePhoto?:(!product.photoUri.isNullOrBlank())
    isEnabled=!product.photoUri.isNullOrBlank()
   }
   form.addView(photo)
   if(product.photoUri.isNullOrBlank())form.addView(line(
    "Producto sin foto registrada; se puede cotizar sin fotografía.",12f,muted))
   val scroll=ScrollView(this).apply{addView(form)}
   val dialog=AlertDialog.Builder(this)
    .setTitle((if(editIndex==null)"Agregar " else "Editar ")+product.name)
    .setView(scroll).setNegativeButton("Cancelar",null)
    .setPositiveButton(if(editIndex==null)"Agregar" else "Guardar cambios",null).create()
   dialog.setOnShowListener{
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
     val quantity=qty.text.toString().toIntOrNull()
     val unitPrice=price.text.toString().toDoubleOrNull()
     val pct=discount.text.toString().toDoubleOrNull()
     val fixed=amount.text.toString().toDoubleOrNull()
     if(quantity==null||quantity<=0||unitPrice==null||!unitPrice.isFinite()||unitPrice<0||
       pct==null||!pct.isFinite()||pct !in 0.0..100.0||
       fixed==null||!fixed.isFinite()||fixed<0){
      toast("Comprueba cantidad, precio y descuentos.")
     }else{
      val updated=QuoteLineDraft(product,quantity,unitPrice,pct,fixed,
       applyIgv.isChecked,photo.isChecked)
      if(editIndex!=null && editIndex in picked.indices)picked[editIndex]=updated
      else picked.add(updated)
      refreshPicked()
      dialog.dismiss()
      // Después de seleccionar un producto, cerrar la lista para evitar
      // mostrar todos los artículos mientras no se realiza otra búsqueda.
      productSearch.setText("")
      toast(if(editIndex==null)"Producto añadido a la cotización" else "Producto actualizado")
     }
    }
   }
   dialog.show()
  }
  editPickedLine={index->
   if(index in picked.indices)openProduct(picked[index].product,index)
  }
  fun renderProducts(){
   resultsList.removeAllViews()
   val query=productSearch.text.toString().trim()
   // No ofrecer el catálogo completo al abrir la pantalla.
   if(query.isBlank()){
    resultsList.addView(line(
     if(catalog.isEmpty())
      "No hay productos registrados en esta instalación. Abre Productos para añadirlos."
     else "Escribe parte del nombre o código de un producto para ver coincidencias.",
     13f,muted))
    return
   }
   val filtered=catalog.filter{
    listOf(it.name,it.code,it.barcode,it.brand,it.category)
     .any{field->field.contains(query,ignoreCase=true)}
   }
   if(filtered.isEmpty()){
    val msg=if(catalog.isEmpty())
     "No hay productos guardados en esta instalación de BELSIPERU. Entra a Productos para registrar aquí tus artículos."
    else
     "No hay coincidencias para «"+query+"» entre los "+catalog.size+
      " productos de esta instalación. Prueba con otro nombre, código o marca."
    resultsList.addView(line(msg,13f,muted))
   }
   for(product in filtered.take(24)){
    val row=card()
    val horizontal=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    val photo=ImageView(this).apply{contentDescription="Fotografía de "+product.name}
    showProductImage(photo,product.photoUri)
    horizontal.addView(photo,LinearLayout.LayoutParams(d(55),d(55)))
    val detail=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(9),0,0,0)}
    detail.addView(line(product.name,14f,navy,true))
    detail.addView(line(product.code+
     (if(product.barcode.isNotBlank())" · "+product.barcode else "")+
     " · "+product.currency+" "+money(product.price),12f,muted))
    detail.addView(line(if(product.kind=="SERVICIO")"Servicio" else
     "Stock: "+product.stock+" "+product.unit,12f,muted))
    horizontal.addView(detail,LinearLayout.LayoutParams(0,-2,1f))
    row.addView(horizontal)
    val add=line("＋ Agregar producto",13f,green,true).apply{
     gravity=Gravity.CENTER;minHeight=d(43)
     isClickable=true;isFocusable=true
     setOnClickListener{openProduct(product)}
    }
    row.addView(add)
    resultsList.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(4),0,d(6))})
   }
   if(filtered.size>24)resultsList.addView(line(
    "Hay "+filtered.size+" productos. Escribe más para encontrar el que buscas.",12f,muted))
  }
  productSearch.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){renderProducts()}
   override fun afterTextChanged(s:Editable?){}
  })
  currency.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){refreshPicked()}
  }
  fx.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshPicked()}
   override fun afterTextChanged(s:Editable?){}
  })
  // Antes se rellenaba resultsList, pero nunca se agregaba al layout.
  // Sin esta vista, ningún producto del catálogo podía seleccionarse.
  body.addView(line(
   if(catalog.isEmpty())
    "Catálogo vacío en esta instalación. Los productos registrados en otras APK de prueba no se transfieren automáticamente."
   else
    "Catálogo disponible: "+catalog.size+" artículo(s). Escribe un nombre o código para buscarlos.",
   13f,if(catalog.isEmpty())Color.rgb(162,84,30) else muted))
  if(catalog.isEmpty()){
   button("＋ Registrar productos en esta instalación"){products()}
  }
  body.addView(resultsList,LinearLayout.LayoutParams(-1,-2).apply{
   setMargins(0,d(6),0,d(10))
  })
  renderProducts()
  heading("Productos agregados")
  body.addView(selectedList)
  body.addView(totalView,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(10),0,d(10))})
  refreshPicked()

  button(if(editQuoteId==null)"✓ Guardar cotización" else "✓ Guardar cambios de la proforma"){
   val clientId=selectedClientId
   if(clientId==null){toast("Selecciona un cliente registrado o crea uno nuevo.");return@button}
   if(picked.isEmpty()){
    AlertDialog.Builder(this).setTitle("Faltan productos")
     .setMessage(if(catalog.isEmpty())
      "Esta instalación no contiene productos registrados. Abre Productos para crearlos aquí. Los catálogos de otras APK de prueba se guardan por separado."
      else "Primero busca el producto en la lista visible, toca «＋ Agregar producto» e indica la cantidad. Después podrás guardar la cotización.")
     .setPositiveButton("Entendido",null).show()
    return@button
   }
   val rate=currentRate();val target=targetCurrency()
   if(rate<=0.0||!rate.isFinite()){toast("Indica un tipo de cambio válido.");return@button}
   val customer=db.readableDatabase.rawQuery(
    "SELECT name,document,address FROM clients WHERE id=?",arrayOf(clientId.toString()))
   var customerName:String?=null
   var customerDoc=""
   var customerAddress=""
   try{if(customer.moveToFirst()){
    customerName=customer.getString(0)
    customerDoc=customer.getString(1)?:""
    customerAddress=customer.getString(2)?:""
   }}finally{customer.close()}
   if(customerName.isNullOrBlank()){toast("El cliente ya no está registrado.");return@button}
   if(!customerDoc.matches(Regex("[0-9]{8}|[0-9]{11}")) || customerAddress.isBlank()){
    AlertDialog.Builder(this).setTitle("Completa los datos para el PDF")
     .setMessage("Este cliente necesita DNI o RUC válido y dirección principal. Puedes corregirlo manualmente antes de generar la cotización.")
     .setNegativeButton("Después",null)
     .setPositiveButton("Editar cliente"){_,_->clientEditor(clientId,returnToQuote=true)}
     .show()
    return@button
   }
   val calculated=picked.map{quoteLineNumbers(it,target,rate)}
   val total=calculated.sumOf{it.second}
   if(!total.isFinite()||total<0){toast("Revisa las cantidades y los descuentos.");return@button}
   val first=picked.first()
   val firstFactor=if(first.product.currency==target)1.0 else
    if(first.product.currency=="USD")rate else 1.0/rate
   val firstTaxable=first.product.taxType=="GRAVADO"&&first.applyIgv
   val firstTax=if(firstTaxable)"GRAVADO" else
    if(first.product.taxType=="GRAVADO")"INAFECTO" else first.product.taxType
   val values=android.content.ContentValues().apply{
    put("client_id",clientId);put("client",customerName);put("product",first.product.name)
    put("product_id",first.product.id);put("qty",first.quantity)
    put("unit_price",first.netUnitPrice*firstFactor)
    put("discount",first.discountPct);put("discount_amount",first.discountAmount*firstFactor)
    put("igv",if(firstTaxable)1 else 0);put("tax_affectation",firstTax)
    put("currency",target);put("total",total)
    if(editQuoteId==null){
     put("status","PENDIENTE");put("created",today())
    }
    put("product_photo",if(first.includePhoto)first.product.photoUri else null)
    put("include_product_photo",if(first.includePhoto)1 else 0)
   }
   val database=db.writableDatabase
   var createdId=-1L;var error:String?=null
   try{
    database.beginTransaction()
    if(editQuoteId==null){
     val (serie,nextNumber)=assignNextQuoteSequence(database)
     values.put("series",serie)
     values.put("quote_number",nextNumber)
     createdId=database.insertOrThrow("quotes",null,values)
    }else{
     val status=database.rawQuery(
      "SELECT status FROM quotes WHERE id=?",arrayOf(editQuoteId.toString()))
     try{
      if(!status.moveToFirst()||status.getString(0)!="PENDIENTE")
       throw IllegalStateException("La proforma ya no está pendiente.")
     }finally{status.close()}
     createdId=editQuoteId
     if(database.update("quotes",values,"id=? AND status='PENDIENTE'",
       arrayOf(createdId.toString()))!=1)
      throw IllegalStateException("No se pudieron actualizar los datos.")
     database.delete("quote_items","quote_id=?",arrayOf(createdId.toString()))
    }
    picked.forEachIndexed{index,item->
     val factor=if(item.product.currency==target)1.0 else
      if(item.product.currency=="USD")rate else 1.0/rate
     val applyTax=item.product.taxType=="GRAVADO"&&item.applyIgv
     val taxType=if(applyTax)"GRAVADO" else
      if(item.product.taxType=="GRAVADO")"INAFECTO" else item.product.taxType
     database.insertOrThrow("quote_items",null,android.content.ContentValues().apply{
      put("quote_id",createdId)
      if(item.product.id>0)put("product_id",item.product.id) else putNull("product_id")
      put("code",item.product.code);put("name",item.product.name);put("unit",item.product.unit)
      put("qty",item.quantity);put("unit_price",item.netUnitPrice*factor)
      put("discount_pct",item.discountPct);put("discount_amount",item.discountAmount*factor)
      put("igv",if(applyTax)1 else 0);put("tax_affectation",taxType)
      put("line_total",calculated[index].second)
      put("photo_uri",if(item.includePhoto)item.product.photoUri else null)
      put("include_product_photo",if(item.includePhoto)1 else 0)
     })
    }
    database.setTransactionSuccessful()
   }catch(e:Exception){error=e.message?:"Error al guardar cotización"}
   finally{if(database.inTransaction())database.endTransaction()}
   if(error!=null){toast("No se pudo guardar la cotización: "+error);return@button}
   toast(if(editQuoteId==null)"Cotización guardada" else "Proforma actualizada")
   if(editQuoteId==null)quotes() else pdfOptions(createdId)
  }
  label("Las cotizaciones se guardan al pulsar el botón. Las consultas y selecciones no modifican el inventario.")
 }
 private fun quotes(){
  screen("Cotizaciones")
  button("＋ Nueva cotización"){newQuote()}
  heading("Historial de cotizaciones","Consulta productos incluidos, exporta proformas y confirma ventas.")
  val c=db.readableDatabase.rawQuery(
   "SELECT id,client,total,currency,status FROM quotes ORDER BY id DESC",null)
  try{
   while(c.moveToNext()){
    val quoteId=c.getLong(0)
    val client=c.getString(1)?:""
    val total=c.getDouble(2)
    val currency=c.getString(3)?:"PEN"
    val status=c.getString(4)?:"PENDIENTE"
    val cardView=card()
    cardView.addView(line(quoteDisplayNumber(quoteId)+" · "+status,15f,navy,true))
    cardView.addView(line(client,14f,ink))
    var count=0;var withPhoto=false
    val lines=db.readableDatabase.rawQuery(
     "SELECT qty,name,include_product_photo FROM quote_items WHERE quote_id=? ORDER BY id",
     arrayOf(quoteId.toString()))
    try{while(lines.moveToNext()){
     count++
     if(count<=4)cardView.addView(line(
      lines.getInt(0).toString()+" × "+(lines.getString(1)?:"Producto"),12f,muted))
     if(lines.getInt(2)==1)withPhoto=true
    }}finally{lines.close()}
    if(count>4)cardView.addView(line("+"+(count-4)+" producto(s) más",12f,muted))
    cardView.addView(line(currency+" "+String.format(Locale.US,"%,.2f",total)+
     " · "+count+" producto(s)"+(if(withPhoto)" · Con fotos" else ""),
     16f,green,true))
    // Acciones con fondo, altura táctil y contraste real: no parecen mensajes.
    val actions=LinearLayout(this@MainActivity).apply{
     orientation=LinearLayout.HORIZONTAL
     gravity=Gravity.CENTER_VERTICAL
     setPadding(0,d(8),0,d(2))
    }
    fun actionButton(text:String,color:Int,description:String,onTap:()->Unit){
     val control=line(text,13f,Color.WHITE,true).apply{
      gravity=Gravity.CENTER
      minHeight=d(52)
      setPadding(d(6),d(12),d(6),d(12))
      background=round(color,12)
      elevation=d(2).toFloat()
      isClickable=true
      isFocusable=true
      contentDescription=description
      setOnClickListener{onTap()}
     }
     actions.addView(control,LinearLayout.LayoutParams(0,-2,1f).apply{
      setMargins(d(3),d(3),d(3),d(3))
     })
    }
    actionButton("Gestionar PDF",navy,
     "Exportar PDF de la cotización "+quoteId){pdfOptions(quoteId)}
    if(status=="PENDIENTE"){
     actionButton("Confirmar venta",green,
      "Confirmar venta de la cotización "+quoteId){confirm(quoteId)}
    }
    cardView.addView(actions)
    body.addView(cardView,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,d(6),0,d(8))})
   }
  }finally{c.close()}
 }

 private fun confirm(id:Long){
  AlertDialog.Builder(this).setTitle("Confirmar venta")
   .setMessage("Se descontará el stock de todos los productos físicos de esta cotización una sola vez. ¿Continuar?")
   .setNegativeButton("Cancelar",null).setPositiveButton("Confirmar"){_,_->
    val database=db.writableDatabase
    var error:String?=null
    try{
     database.beginTransaction()
     val quote=database.rawQuery("SELECT status FROM quotes WHERE id=?",arrayOf(id.toString()))
     val pending=try{quote.moveToFirst()&&quote.getString(0)=="PENDIENTE"}
      finally{quote.close()}
     if(!pending)throw IllegalStateException("La cotización ya fue confirmada o no existe.")
     val requirements=mutableMapOf<Long,Int>()
     val names=mutableMapOf<Long,String>()
     val items=database.rawQuery(
      "SELECT product_id,name,qty FROM quote_items WHERE quote_id=?",arrayOf(id.toString()))
     var found=0
     try{
      while(items.moveToNext()){
       found++
       val name=items.getString(1)?:""
       val qty=items.getInt(2)
       if(qty<=0)throw IllegalStateException("La cotización contiene cantidades incorrectas.")
       val source=if(!items.isNull(0))
        database.rawQuery("SELECT id FROM products WHERE id=? LIMIT 1",
         arrayOf(items.getLong(0).toString()))
       else database.rawQuery(
        "SELECT id FROM products WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(name))
       val productId=try{if(source.moveToFirst())source.getLong(0) else null}
        finally{source.close()}
       if(productId==null)throw IllegalStateException(
        "El producto "+name+" ya no está disponible en el catálogo.")
       requirements[productId]=(requirements[productId]?:0)+qty
       names[productId]=name
      }
     }finally{items.close()}
     if(found==0)throw IllegalStateException("La cotización no tiene productos.")
     // Verifica TODAS las cantidades antes de descontar cualquiera.
     for((productId,qty) in requirements){
      val product=database.rawQuery(
       "SELECT kind,stock,sell_without_stock FROM products WHERE id=?",
       arrayOf(productId.toString()))
      try{
       if(!product.moveToFirst())throw IllegalStateException("Producto no encontrado.")
       val service=product.getString(0)=="SERVICIO"
       val stock=product.getInt(1)
       val allowOversell=product.getInt(2)==1
       if(!service && qty>stock && !allowOversell)
        throw IllegalStateException("Stock insuficiente para "+(names[productId]?:"producto")+
         ": disponibles "+stock+", solicitadas "+qty+".")
      }finally{product.close()}
     }
     for((productId,qty) in requirements){
      val product=database.rawQuery("SELECT kind FROM products WHERE id=?",
       arrayOf(productId.toString()))
      val kind=try{if(product.moveToFirst())product.getString(0)?:"PRODUCTO" else ""}
       finally{product.close()}
      if(kind!="SERVICIO"){
       database.execSQL("UPDATE products SET stock=stock-? WHERE id=?",
        arrayOf(qty,productId))
       database.execSQL("INSERT INTO movements(product,qty,reason,created) VALUES(?,?,?,?)",
        arrayOf(names[productId]?:"Producto",-qty,"VENTA COT-"+id,today()))
      }
     }
     database.execSQL("UPDATE quotes SET status='VENDIDA' WHERE id=? AND status='PENDIENTE'",
      arrayOf(id))
     database.setTransactionSuccessful()
    }catch(e:Exception){error=e.message?:"Error al confirmar venta"}
    finally{if(database.inTransaction())database.endTransaction()}
    if(error!=null)toast(error!!) else{toast("Venta confirmada");quotes()}
   }.show()
 }

 private fun inventory(){
  stockReportScreen()
 }

 private fun stockReportScreen(){
  val report=try{StockReport.load(db.readableDatabase)}
   catch(e:Exception){toast("No se pudo leer el stock: "+e.message);return}
  screen("Inventario · Existencias")
  button("← Inicio"){home()}
  heading("Existencias actuales",
   "Todos los productos físicos registrados, con unidades disponibles, mínimo y alerta de reposición.")
  info("Resumen del inventario",
   report.products.size.toString()+" productos · "+report.units+
   " unidades · "+report.out+" sin stock · "+report.low+" bajo mínimo",
   if(report.low>0||report.out>0)Color.rgb(166,106,26) else green)
  button("📄 Ver, imprimir o guardar PDF A4 horizontal"){stockPdfActions()}
  button("📊 Exportar existencias y Kárdex a Excel (.xlsx)"){stockExcelDocument()}
  button("📋 Ver Kárdex (movimientos registrados)"){inventoryMovementsScreen()}
  heading("Listado completo",
   "Busca por descripción, código o marca. La exportación incluye todos los productos.")
  label("Buscar producto")
  val search=input("Código, nombre, marca o categoría")
  label("Filtrar")
  val filter=choice(listOf("Todos","Stock bajo","Sin stock","Con stock"))
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(list,LinearLayout.LayoutParams(-1,-2))
  var shown=80
  fun render(){
   list.removeAllViews()
   val term=search.text.toString().trim()
   val selected=filter.selectedItemPosition
   val found=report.products.filter{p->
    (term.isEmpty()||listOf(p.name,p.code,p.brand,p.category)
     .any{it.contains(term,ignoreCase=true)})&&
    when(selected){
     1->p.minimum>0&&p.stock<=p.minimum
     2->p.stock<=0
     3->p.stock>0
     else->true
    }
   }
   list.addView(line("Resultados: "+found.size+" productos",13f,muted,true))
   if(found.isEmpty()){
    list.addView(line("No hay productos para este filtro.",13f,muted))
    return
   }
   for(p in found.take(shown)){
    val entry=card()
    entry.addView(line(p.code+" · "+p.name,15f,navy,true))
    val detail=listOf(p.brand,p.category,p.unit).filter{it.isNotBlank()}
     .joinToString(" · ")
    if(detail.isNotBlank())entry.addView(line(detail,12f,muted))
    val stockColor=when(p.status){
     "SIN STOCK"->Color.rgb(172,49,48)
     "STOCK BAJO"->Color.rgb(176,107,16)
     else->green
    }
    entry.addView(line("Stock: "+p.stock+" · Mínimo: "+p.minimum+
     " · "+p.status,13.5f,stockColor,true))
    if(p.shortage>0&&p.minimum>0){
     entry.addView(line("Unidades para alcanzar el mínimo: "+p.shortage,12f,muted))
    }
    list.addView(entry,LinearLayout.LayoutParams(-1,-2).apply{
     setMargins(0,d(6),0,d(6))
    })
   }
   if(found.size>shown){
    val more=line("Mostrar 80 productos más ("+(found.size-shown)+
     " restantes)",14f,green,true).apply{
     setPadding(d(12),d(13),d(12),d(13))
     gravity=Gravity.CENTER
     isClickable=true;isFocusable=true
     setOnClickListener{shown+=80;render()}
    }
    list.addView(more)
   }
  }
  search.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    shown=80;render()
   }
   override fun afterTextChanged(s:Editable?){}
  })
  filter.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,position:Int,id:Long){
    shown=80;render()
   }
  }
  render()
 }
 private fun inventoryMovementsScreen(){
  val report=try{StockReport.load(db.readableDatabase)}
   catch(e:Exception){toast("No se pudo leer el Kárdex: "+e.message);return}
  screen("Kárdex · Movimientos")
  button("← Volver a existencias"){stockReportScreen()}
  heading("Historial de movimientos",
   "Ingresos por compras y salidas por ventas registrados en esta instalación.")
  info("Importante sobre los saldos históricos",
   "No es posible reconstruir saldos de movimientos antiguos. Los cambios manuales de stock o importaciones anteriores pueden no haber generado un movimiento.")
  button("📄 Ver o imprimir PDF de inventario y movimientos"){stockPdfActions()}
  button("📊 Exportar Excel con las dos hojas"){stockExcelDocument()}
  label("Buscar movimientos")
  val search=input("Nombre de producto, fecha o referencia")
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  body.addView(list,LinearLayout.LayoutParams(-1,-2))
  var shown=80
  fun render(){
   list.removeAllViews()
   val term=search.text.toString().trim()
   val found=report.movements.filter{m->
    term.isEmpty()||listOf(m.product,m.date,m.reason,m.type).any{
     it.contains(term,ignoreCase=true)
    }
   }
   list.addView(line(found.size.toString()+" movimientos registrados",13f,muted,true))
   if(found.isEmpty()){
    list.addView(line("No hay movimientos para mostrar.",13f,muted))
    return
   }
   found.take(shown).forEach{m->
    val entry=card()
    entry.addView(line(m.product,14f,navy,true))
    entry.addView(line(m.date+" · "+m.type+" · "+
     (if(m.qty>0)"+" else "")+m.qty.toString()+" unidades",
     13f,if(m.qty<0)Color.rgb(170,55,53) else green,true))
    entry.addView(line(m.reason,12.5f,muted))
    list.addView(entry,LinearLayout.LayoutParams(-1,-2).apply{
     setMargins(0,d(5),0,d(5))
    })
   }
   if(found.size>shown){
    val more=line("Mostrar 80 movimientos más",14f,green,true).apply{
     isClickable=true;isFocusable=true
     gravity=Gravity.CENTER
     setPadding(d(10),d(13),d(10),d(13))
     setOnClickListener{shown+=80;render()}
    }
    list.addView(more)
   }
  }
  search.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    shown=80;render()
   }
   override fun afterTextChanged(s:Editable?){}
  })
  render()
 }
 private fun stockTempPdf():java.io.File{
  val dir=java.io.File(cacheDir,"shared_proformas")
  if(!dir.isDirectory&&!dir.mkdirs())throw java.io.IOException(
   "No se pudo crear carpeta de PDF.")
  val file=java.io.File.createTempFile("BELSIPERU_STOCK_","_KARDEX.pdf",dir)
  try{
   val snapshot=StockReport.load(db.readableDatabase)
   file.outputStream().use{output->
    val c=companyInfo()
    StockReport.writePdf(snapshot,c.legal,c.ruc,output)
   }
   if(file.length()<100L)throw java.io.IOException("PDF vacío")
   return file
  }catch(e:Exception){file.delete();throw e}
 }
 private fun stockPdfActions(){
  Thread{
   try{
    val file=stockTempPdf()
    runOnUiThread{
     AlertDialog.Builder(this)
      .setTitle("Reporte PDF de inventario")
      .setMessage("Contiene existencias actuales y movimientos registrados. A4 horizontal, con varias páginas si hace falta.")
      .setItems(arrayOf("Visualizar PDF","Imprimir PDF","Guardar PDF en el teléfono")){_,which->
       when(which){
        0->previewQuotePdf(file)
        1->printQuotePdf(file,true)
        2->stockPdfDocument()
       }
      }.setNegativeButton("Cerrar",null).show()
    }
   }catch(e:Exception){
    runOnUiThread{toast("No se pudo generar PDF: "+(e.message?:"Error"))}
   }
  }.start()
 }
 private fun stockPdfDocument(){
  val filename="BELSIPERU_REPORTE_STOCK_KARDEX_"+
   SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())+".pdf"
  startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE)
   type="application/pdf"
   putExtra(Intent.EXTRA_TITLE,filename)
  },97)
 }
 private fun stockExcelDocument(){
  val filename="BELSIPERU_REPORTE_STOCK_KARDEX_"+
   SimpleDateFormat("yyyyMMdd",Locale.US).format(Date())+".xlsx"
  startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE)
   type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
   putExtra(Intent.EXTRA_TITLE,filename)
  },98)
 }
 private fun writeStockDocument(uri:Uri,pdf:Boolean){
  Thread{
   try{
    val snapshot=StockReport.load(db.readableDatabase)
    val output=contentResolver.openOutputStream(uri)
     ?:throw java.io.IOException("No se pudo abrir el archivo de destino.")
    output.use{stream->
     if(pdf){
      val c=companyInfo()
      StockReport.writePdf(snapshot,c.legal,c.ruc,stream)
     }else StockReport.writeExcel(snapshot,stream)
    }
    runOnUiThread{
     toast((if(pdf)"PDF" else "Excel")+" guardado: "+
      snapshot.products.size+" productos y "+
      snapshot.movements.size+" movimientos.")
    }
   }catch(e:Exception){
    runOnUiThread{toast("No se pudo guardar informe: "+(e.message?:"Error"))}
   }
  }.start()
 }
 private fun selectPhoto(id:Long){
  pendingPhotoProduct=id
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE);type="image/*"
  }
  startActivityForResult(intent,92)
 }

 private fun pdfOptions(id:Long){
  if(!companyConfigured()){companySetup();return}
  screen("Opciones PDF");button("← Cotizaciones"){quotes()}
  info("Modelo de proforma BELSIPERU","Se conserva el diseño aprobado. Si el producto tiene una fotografía accesible, aparecerá en el PDF.")
  label("Diseño")
  val style=choice(listOf("Proforma BELSIPERU","Moderno","Corporativo"))
  info("Estilos disponibles",
   "Proforma BELSIPERU conserva el formato habitual. Moderno tiene un encabezado comercial y Corporativo una presentación formal. Los dos nuevos diseños están adaptados a los datos de tu empresa.")
  label("Tamaño de papel")
  val paper=choice(listOf("Carta vertical","A4 vertical","A4 horizontal","A5 vertical","A5 horizontal","Ticket 80 mm"))
  label("Vencimiento de la proforma (dd/MM/aaaa, opcional)")
  val expiry=input("Toca aquí para elegir una fecha")
  // El campo entero y el ícono abren el calendario. No hace falta teclear.
  body.removeView(expiry)
  expiry.setText(pdfExpiry)
  expiry.isFocusable=false
  expiry.isFocusableInTouchMode=false
  expiry.isClickable=true
  expiry.keyListener=null
  expiry.contentDescription="Seleccionar vencimiento desde el calendario"
  val expiryRow=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
  }
  val expiryParams=LinearLayout.LayoutParams(0,-2,1f)
  expiryRow.addView(expiry,expiryParams)
  val openCalendar={
   val selectedDate=Calendar.getInstance()
   val savedDate=expiry.text.toString().trim()
   if(savedDate.isNotEmpty()){
    val parser=SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).apply{isLenient=false}
    runCatching{parser.parse(savedDate)}.getOrNull()?.let{selectedDate.time=it}
   }
   DatePickerDialog(this@MainActivity,{_,year,month,day->
    expiry.setText(String.format(Locale("es","PE"),"%02d/%02d/%04d",
     day,month+1,year))
   },selectedDate.get(Calendar.YEAR),selectedDate.get(Calendar.MONTH),
    selectedDate.get(Calendar.DAY_OF_MONTH)).show()
  }
  expiry.setOnClickListener{openCalendar()}
  val calendarIcon=line("📅",21f,green,true).apply{
   gravity=Gravity.CENTER
   minHeight=d(50)
   contentDescription="Abrir calendario de vencimiento"
   background=round(Color.WHITE,12,border)
   isClickable=true
   isFocusable=true
   setOnClickListener{openCalendar()}
  }
  expiryRow.addView(calendarIcon,LinearLayout.LayoutParams(d(54),d(50)).apply{
   setMargins(d(8),0,0,0)
  })
  body.addView(expiryRow,LinearLayout.LayoutParams(-1,-2).apply{
   setMargins(0,d(3),0,d(4))
  })
  val clearExpiry=line("Quitar fecha de vencimiento",12f,green,true).apply{
   isClickable=true
   isFocusable=true
   minHeight=d(36)
   gravity=Gravity.CENTER_VERTICAL
   contentDescription="Quitar vencimiento; la fecha es opcional"
   setOnClickListener{expiry.setText("")}
  }
  body.addView(clearExpiry)
  label("Responsable")
  val seller=input("Usuario o vendedor")
  seller.setText(pdfSeller)
  label("Condición de pago")
  val payment=input("CONTADO / CRÉDITO")
  payment.setText(pdfPayment)
  button("Generar PDF"){
   val selectedStyle=style.selectedItem.toString()
   if(paper.selectedItem.toString()=="Ticket 80 mm" && selectedStyle!="Proforma BELSIPERU"){
    AlertDialog.Builder(this).setTitle("Tamaño de papel")
     .setMessage("Los diseños Moderno y Corporativo necesitan papel Carta, A4 o A5 para conservar sus tablas. Selecciona otro tamaño o elige Proforma BELSIPERU para un ticket de 80 mm.")
     .setPositiveButton("Entendido",null).show()
    return@button
   }
   val dateText=expiry.text.toString().trim()
   if(dateText.isNotEmpty()){
    val parser=SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).apply{isLenient=false}
    if(runCatching{parser.parse(dateText)}.getOrNull()==null){
     toast("Selecciona una fecha válida desde el calendario.")
     return@button
    }
   }
   pdfStyle=selectedStyle
   pdfPaper=paper.selectedItem.toString()
   pdfExpiry=dateText
   pdfSeller=seller.text.toString().trim().ifEmpty{"Asesor comercial"}
   pdfPayment=payment.text.toString().trim().ifEmpty{"CONTADO"}
   documentActions(id)
  }
 }

 // PDF temporal para acciones: no obliga a guardar antes de visualizar o compartir.
 private fun preparedProforma(id:Long):java.io.File{
  val dir=java.io.File(cacheDir,"shared_proformas")
  if(!dir.isDirectory&&!dir.mkdirs())throw java.io.IOException("No se pudo crear la carpeta temporal")
  val style=pdfStyle.replace(Regex("[^A-Za-z0-9]"),"_")
  val paper=pdfPaper.replace(Regex("[^A-Za-z0-9]"),"_")
  val file=java.io.File(dir,"BELSIPERU_"+quoteDisplayNumber(id)+
   "_"+style+"_"+paper+".pdf")
  try{
   file.outputStream().use{writePdf(id,it)}
   if(file.length()<100L)throw java.io.IOException("El documento generado está vacío")
   return file
  }catch(e:Exception){file.delete();throw e}
 }
 private fun sharedPdfUri(file:java.io.File):Uri=
  FileProvider.getUriForFile(this,packageName+".fileprovider",file)

 private fun previewQuotePdf(file:java.io.File){
  val uri=sharedPdfUri(file)
  val view=Intent(Intent.ACTION_VIEW).apply{
   setDataAndType(uri,"application/pdf")
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   clipData=ClipData.newRawUri("Proforma BELSIPERU",uri)
  }
  try{startActivity(Intent.createChooser(view,"Visualizar proforma"))}
  catch(e:Exception){toast("No hay visor PDF disponible: "+(e.message?:""))}
 }
 private fun quoteRecipientEmail(id:Long):String{
  val q=db.readableDatabase.rawQuery(
   "SELECT c.email FROM quotes q LEFT JOIN clients c ON c.id=q.client_id WHERE q.id=?",
   arrayOf(id.toString()))
  try{return if(q.moveToFirst())q.getString(0)?:"" else ""}finally{q.close()}
 }
 private fun shareQuotePdf(id:Long,file:java.io.File,email:Boolean){
  val uri=sharedPdfUri(file)
  val number=quoteDisplayNumber(id)
  val intent=Intent(Intent.ACTION_SEND).apply{
   type=if(email)"message/rfc822" else "application/pdf"
   putExtra(Intent.EXTRA_STREAM,uri)
   putExtra(Intent.EXTRA_SUBJECT,"Cotización "+number+" · "+companyInfo().legal)
   putExtra(Intent.EXTRA_TEXT,"Adjunto la cotización "+number+" de "+companyInfo().legal+".")
   if(email){
    val recipient=quoteRecipientEmail(id)
    if(recipient.isNotBlank())putExtra(Intent.EXTRA_EMAIL,arrayOf(recipient))
   }
   clipData=ClipData.newRawUri("Proforma BELSIPERU",uri)
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  try{startActivity(Intent.createChooser(intent,
   if(email)"Enviar proforma por correo" else "Compartir PDF por WhatsApp / Otros"))}
  catch(e:Exception){toast("No se pudo compartir: "+(e.message?:""))}
 }
 private fun printQuotePdf(file:java.io.File,stockA4Landscape:Boolean=false){
  val manager=getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
  val adapter=object:android.print.PrintDocumentAdapter(){
   override fun onLayout(oldAttributes:android.print.PrintAttributes?,
    newAttributes:android.print.PrintAttributes?,
    cancellationSignal:android.os.CancellationSignal,
    callback:LayoutResultCallback,extras:Bundle?){
    if(cancellationSignal.isCanceled){callback.onLayoutCancelled();return}
    callback.onLayoutFinished(android.print.PrintDocumentInfo.Builder(file.name)
     .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
     .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),true)
   }
   override fun onWrite(pages:Array<out android.print.PageRange>,
    destination:android.os.ParcelFileDescriptor,
    cancellationSignal:android.os.CancellationSignal,callback:WriteResultCallback){
    if(cancellationSignal.isCanceled){callback.onWriteCancelled();return}
    try{
     file.inputStream().use{input->
      java.io.FileOutputStream(destination.fileDescriptor).use{output->
       val buffer=ByteArray(16*1024)
       while(true){
        if(cancellationSignal.isCanceled){callback.onWriteCancelled();return}
        val n=input.read(buffer);if(n<0)break
        output.write(buffer,0,n)
       }
       output.flush()
      }
     }
     callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
    }catch(e:Exception){callback.onWriteFailed(e.message?:"No se pudo imprimir")}
   }
  }
  val media=when{
   stockA4Landscape->android.print.PrintAttributes.MediaSize.ISO_A4
   pdfPaper=="A4 vertical"||pdfPaper=="A4 horizontal"->
    android.print.PrintAttributes.MediaSize.ISO_A4
   pdfPaper=="A5 vertical"||pdfPaper=="A5 horizontal"->
    android.print.PrintAttributes.MediaSize.ISO_A5
   else->android.print.PrintAttributes.MediaSize.NA_LETTER
  }
  val oriented=if(stockA4Landscape||pdfPaper.endsWith("horizontal"))
   media.asLandscape() else media.asPortrait()
  val attrs=android.print.PrintAttributes.Builder().setMediaSize(oriented).build()
  try{manager.print("Proforma BELSIPERU "+file.name,adapter,attrs)}
  catch(e:Exception){toast("Error al abrir impresión: "+(e.message?:""))}
 }
 private fun documentActions(id:Long){
  val file=try{preparedProforma(id)}
   catch(e:Exception){toast("Error al generar PDF: "+(e.message?:""));return}
  val q=db.readableDatabase.rawQuery(
   "SELECT client,total,currency,status,created FROM quotes WHERE id=?",
   arrayOf(id.toString()))
  var client="";var total=0.0;var currency="PEN";var status="";var created=""
  try{
   if(!q.moveToFirst()){toast("Proforma no encontrada");return}
   client=q.getString(0)?:"";total=q.getDouble(1)
   currency=q.getString(2)?:"PEN";status=q.getString(3)?:""
   created=q.getString(4)?:""
  }finally{q.close()}
  screen("Documento interno")
  button("← Opciones PDF"){pdfOptions(id)}
  val summary=card()
  summary.addView(line("PROFORMA",19f,navy,true).apply{gravity=Gravity.CENTER})
  summary.addView(line(quoteDisplayNumber(id),18f,muted).apply{gravity=Gravity.CENTER})
  summary.addView(line((if(currency=="USD")"US$ " else "S/ ")+
   pdfNumber(total),24f,ink,true).apply{gravity=Gravity.CENTER})
  summary.addView(line("Cliente",14f,navy,true))
  summary.addView(line(client,15f,ink))
  summary.addView(line("Fecha: "+created.take(10)+" · "+status,12f,muted))
  summary.addView(line("Diseño: "+pdfStyle+" · Papel: "+pdfPaper,12f,muted))
  body.addView(summary)
  fun action(symbol:String,title:String,subtitle:String,onClick:()->Unit){
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
    setPadding(d(16),d(15),d(15),d(15))
    background=round(Color.WHITE,18,border)
    elevation=d(2).toFloat()
    isClickable=true;isFocusable=true;contentDescription=title
    setOnClickListener{onClick()}
   }
   row.addView(line(symbol,22f,Color.rgb(54,83,168),true),
    LinearLayout.LayoutParams(d(42),-2))
   val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   labels.addView(line(title,16f,ink,true))
   labels.addView(line(subtitle,11.5f,muted))
   row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
   row.addView(line("›",24f,muted))
   body.addView(row,LinearLayout.LayoutParams(-1,-2).apply{
    setMargins(0,d(6),0,d(6))
   })
  }
  if(status=="PENDIENTE")
   action("✎","Editar proforma","Cambiar cliente, productos, cantidades, precios y descuentos"){
    newQuote(editQuoteId=id)
   }
  action("✉","Enviar por email","Adjuntar automáticamente el PDF"){shareQuotePdf(id,file,true)}
  action("➤","Enviar por WhatsApp / Otros","Compartir con tus aplicaciones"){shareQuotePdf(id,file,false)}
  action("◉","Visualizar PDF","Abrir el documento completo"){previewQuotePdf(file)}
  action("▣","Imprimir","Impresión de Android con formato elegido"){printQuotePdf(file)}
  action("↓","Guardar PDF","Seleccionar carpeta o proveedor de archivos"){exportPdf(id)}
 }

 private fun exportPdf(id:Long){
  pendingPdf=id
  val filename="BELSIPERU_"+quoteDisplayNumber(id)+"_"+
   pdfPaper.replace(Regex("[^A-Za-z0-9]"),"_")+".pdf"
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
   addCategory(Intent.CATEGORY_OPENABLE)
   type="application/pdf"
   putExtra(Intent.EXTRA_TITLE,filename)
  }
  startActivityForResult(intent,91)
 }
 @Deprecated("Legacy activity result used for minimal SDK example")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==111 || requestCode==112){
   val pass=pendingBackupPassword
   pendingBackupPassword=null
   if(pass!=null){
    if(resultCode==RESULT_OK && data?.data!=null){
     if(requestCode==111)backupExportFile(data.data!!,pass)
     else backupRestoreFile(data.data!!,pass)
    }else pass.fill('\u0000')
   }
   return
  }
  if(requestCode==97 && resultCode==RESULT_OK && data?.data!=null){
   writeStockDocument(data.data!!,true)
   return
  }
  if(requestCode==98 && resultCode==RESULT_OK && data?.data!=null){
   writeStockDocument(data.data!!,false)
   return
  }
  if(requestCode==95 && resultCode==RESULT_OK && data?.data!=null){
   importCatalogExcel(data.data!!)
   return
  }
  if(requestCode==96 && resultCode==RESULT_OK && data?.data!=null){
   writeCatalogExcel(data.data!!,pendingCatalogExportTemplate)
   return
  }
  if(requestCode==94 && resultCode==RESULT_OK && data?.data!=null){
   try{
    val uri=data.data!!
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
    if(bounds.outWidth<=0||bounds.outHeight<=0||
       bounds.outWidth>18000||bounds.outHeight>18000)
     throw IllegalArgumentException("La imagen no es válida o tiene dimensiones excesivas.")
    var sample=1
    while(bounds.outWidth/sample>1400||bounds.outHeight/sample>1400)sample*=2
    val bmp=contentResolver.openInputStream(uri)?.use{
     BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})
    }?:throw IllegalArgumentException("No fue posible leer la imagen.")
    val tmp=java.io.File(filesDir,"company_pdf_logo.tmp")
    try{
     val written=tmp.outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
     if(!written)throw IllegalStateException("No se pudo guardar el logotipo.")
     if(tmp.length()>3L*1024*1024)throw IllegalArgumentException(
      "El logotipo procesado supera 3 MB. Elige uno más ligero.")
     val target=companyLogoFile()
     if(!tmp.renameTo(target)){
      tmp.copyTo(target,overwrite=true);tmp.delete()
     }
     companyLogoPreview?.setImageBitmap(bmp)
     toast("Logotipo guardado para la cabecera del PDF.")
    }finally{tmp.delete()}
   }catch(e:Exception){toast("No se pudo cargar el logo: "+(e.message?:"Archivo no válido"))}
    catch(_:OutOfMemoryError){toast("La imagen es demasiado grande; usa un PNG más pequeño.")}
  }
  if(requestCode==93 && resultCode==RESULT_OK && data?.data!=null){
   val uri=data.data!!
   try{
    contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
    pendingDraftPhotoUri=uri.toString()
    draftPhotoPreview?.let{showProductImage(it,pendingDraftPhotoUri)}
    toast("Fotografía seleccionada. Guarda el producto para conservarla.")
   }catch(e:Exception){
    toast("No se pudo acceder a la foto: "+(e.message?:"Comprueba los permisos"))
   }
  }
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

 // Diseño de proforma comercial basado en el PDF de referencia del usuario.
 // Papel carta: 612x792 puntos (el documento proporcionado tiene este tamaño).

 // A4/A5 apaisados tienen una composición apaisada real;
 // el ticket de 80 mm usa columnas estrechas y una página larga de recibo.

 private data class PdfQuoteLine(
  val code:String,val name:String,val unit:String,val qty:Int,val price:Double,
  val pct:Double,val discount:Double,val taxable:Boolean,val lineTotal:Double,
  val photoUri:String?,val includePhoto:Boolean
 )
 private fun writePdfMultiple(id:Long,out:java.io.OutputStream){
  val company=companyInfo()
  val dbRead=db.readableDatabase
  val header=dbRead.rawQuery(
   "SELECT client,client_id,currency,total,created FROM quotes WHERE id=?",
   arrayOf(id.toString()))
  var client="";var clientId:Long?=null;var currency="PEN";var total=0.0;var created=""
  try{
   if(!header.moveToFirst())throw IllegalStateException("La cotización no existe.")
   client=header.getString(0)?:"Cliente"
   if(!header.isNull(1))clientId=header.getLong(1)
   currency=header.getString(2)?:"PEN"
   total=header.getDouble(3);created=header.getString(4)?:""
  }finally{header.close()}
  val customer=if(clientId!=null)dbRead.rawQuery(
   "SELECT document,address FROM clients WHERE id=? LIMIT 1",arrayOf(clientId.toString()))
  else dbRead.rawQuery("SELECT document,address FROM clients WHERE name=? ORDER BY id DESC LIMIT 1",
   arrayOf(client))
  var document="";var address=""
  try{if(customer.moveToFirst()){
   document=customer.getString(0)?:"";address=customer.getString(1)?:""
  }}finally{customer.close()}
  val items=mutableListOf<PdfQuoteLine>()
  val cur=dbRead.rawQuery(
   "SELECT code,name,unit,qty,unit_price,discount_pct,discount_amount,igv,line_total,photo_uri,include_product_photo FROM quote_items WHERE quote_id=? ORDER BY id",
   arrayOf(id.toString()))
  try{while(cur.moveToNext()){
   items.add(PdfQuoteLine(cur.getString(0)?:"",cur.getString(1)?:"Producto",
    cur.getString(2)?:"UNIDADES",cur.getInt(3),cur.getDouble(4),
    cur.getDouble(5),cur.getDouble(6),cur.getInt(7)==1,
    cur.getDouble(8),cur.getString(9),cur.getInt(10)==1))
  }}finally{cur.close()}
  if(items.isEmpty())throw IllegalStateException("No hay productos en esta cotización.")
  val ticket=pdfPaper=="Ticket 80 mm"
  val paper=when(pdfPaper){
   "A4 horizontal"->Pair(842,595)
   "A4 vertical"->Pair(595,842)
   "A5 vertical"->Pair(420,595)
   "A5 horizontal"->Pair(595,420)
   "Ticket 80 mm"->Pair(227,minOf(12000,maxOf(620,340+items.size*75)))
   else->Pair(612,792)
  }
  val width=paper.first.toFloat();val height=paper.second.toFloat()
  val margin=if(ticket)10f else if(width<500)20f else 28f
  val contentWidth=width-2f*margin
  val pdf=android.graphics.pdf.PdfDocument()
  val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  val currencyMark=if(currency=="USD")"US$" else "S/"
  val labelColor=Color.rgb(18,52,90)
  fun fmt(value:Double)=pdfNumber(value,company.decimals)
  fun font(size:Float,bold:Boolean=false,color:Int=Color.BLACK){
   paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size
   paint.typeface=Typeface.create(Typeface.DEFAULT,if(bold)Typeface.BOLD else Typeface.NORMAL)
  }
  var number=0
  var page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(paper.first,paper.second,++number).create())
  var canvas=page.canvas
  fun text(value:String,x:Float,y:Float,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
   font(size,bold,color);canvas.drawText(value,x,y,paint)
  }
  fun right(value:String,x:Float,y:Float,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
   font(size,bold,color);canvas.drawText(value,x-paint.measureText(value),y,paint)
  }
  fun fitted(value:String,x:Float,y:Float,maxWidth:Float,size:Float=9f,bold:Boolean=false){
   font(size,bold)
   var shown=value.replace("\n"," ")
   if(paint.measureText(shown)>maxWidth){
    while(shown.isNotEmpty()&&paint.measureText(shown+"…")>maxWidth)shown=shown.dropLast(1)
    shown+="…"
   }
   canvas.drawText(shown,x,y,paint)
  }
  fun fillRect(left:Float,top:Float,right:Float,bottom:Float,color:Int){
   paint.color=color;paint.style=Paint.Style.FILL
   canvas.drawRect(left,top,right,bottom,paint)
  }
  fun rule(y:Float){fillRect(margin,y,width-margin,y+0.7f,Color.LTGRAY)}
  fun drawHeader():Float{
   val top=if(ticket)11f else 16f
   if(!ticket)drawCompanyLogo(canvas,RectF(margin,top,margin+73f,top+57f))
   val titleX=if(ticket)margin else margin+83f
   fitted(company.legal,titleX,top+17f,width-titleX-margin,if(ticket)12f else 15f,true)
   text("RUC "+company.ruc,titleX,top+34f,if(ticket)9f else 10f)
   if(company.fiscal.isNotBlank())
    fitted(company.fiscal,titleX,top+43f,width-titleX-margin,7f)
   text("PROFORMA "+quoteDisplayNumber(id),titleX,top+55f,if(ticket)9f else 11f,true)
   rule(top+65f)
   fitted("CLIENTE: "+client,margin,top+82f,contentWidth,if(ticket)8f else 9f,true)
   text("DOC.: "+(if(document.isBlank())"—" else document),margin,top+98f,8.5f)
   fitted("DIRECCIÓN: "+address.ifBlank{"No registrada"},margin,top+113f,contentWidth,8f)
   val date=created.take(10)
   text("EMISIÓN: "+date,margin,top+128f,8f)
   if(pdfExpiry.isNotBlank())right("VENCE: "+pdfExpiry,width-margin,top+128f,8f)
   text("MONEDA: "+currency,margin,top+143f,8f)
   rule(top+151f)
   val labelsY=top+168f
   if(ticket){
    text("ARTÍCULOS / SERVICIOS",margin,labelsY,9f,true,labelColor)
   }else{
    text("CANT.",margin,labelsY,8f,true,labelColor)
    text("PRODUCTO / SERVICIO",margin+contentWidth*0.13f,labelsY,8f,true,labelColor)
    right("P. UNIT.",margin+contentWidth*0.80f,labelsY,8f,true,labelColor)
    right("IMPORTE",width-margin,labelsY,8f,true,labelColor)
   }
   rule(labelsY+6f)
   return labelsY+17f
  }
  fun drawPhoto(uriText:String?,box:RectF){
   if(uriText.isNullOrBlank())return
   try{
    val uri=Uri.parse(uriText)
    if(uri.scheme!="content")return
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
    if(bounds.outWidth<=0||bounds.outHeight<=0)return
    var sample=1
    while(bounds.outWidth/sample>260||bounds.outHeight/sample>260)sample*=2
    val bitmap=contentResolver.openInputStream(uri)?.use{
     BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})
    }
    if(bitmap!=null){drawProductImage(canvas,bitmap,box);bitmap.recycle()}
   }catch(_:Exception){}catch(_:OutOfMemoryError){}
  }
  fun rowHeight(item:PdfQuoteLine):Float=if(ticket){
   if(item.includePhoto&&!item.photoUri.isNullOrBlank())108f else 56f
  }else{
   if(item.includePhoto&&!item.photoUri.isNullOrBlank())76f else 45f
  }
  var y=drawHeader()
  fun endPage(next:Boolean){
   val footerSize=if(ticket)6.5f else 7.8f
   val footerLines=pdfTextLines(company.footer,contentWidth,footerSize,2)
   footerLines.forEachIndexed{i,value->
    font(footerSize,false,labelColor)
    canvas.drawText(value,margin+(contentWidth-paint.measureText(value))/2f,
     height-42f+i*10f,paint)
   }
   text("Proforma comercial. No constituye comprobante tributario.",margin,height-16f,
    if(ticket)6f else 7f,false,labelColor)
   if(!ticket)right("Página "+number,width-margin,height-16f,7f,false,labelColor)
   pdf.finishPage(page)
   if(next){
    number++
    page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(
     paper.first,paper.second,number).create())
    canvas=page.canvas
    y=drawHeader()
   }
  }
  try{
   items.forEachIndexed{index,item->
    val rowH=rowHeight(item)
    if(!ticket&&y+rowH+115f>height){
     endPage(true)
    }
    val top=y
    if(ticket){
     fitted((index+1).toString()+". "+item.name,margin,y+10f,contentWidth,9f,true)
     fitted(item.code+" · "+item.unit,margin,y+24f,contentWidth,8f)
     text("Cantidad: "+item.qty,margin,y+39f,8f)
     right(currencyMark+" "+fmt(item.lineTotal),width-margin,y+39f,9f,true)
     if(item.includePhoto){
      drawPhoto(item.photoUri,RectF(margin+contentWidth-67f,top+42f,
       width-margin,top+103f))
     }
    }else{
     val descX=margin+contentWidth*0.13f
     val descWidth=contentWidth*0.49f
     text(item.qty.toString(),margin,y+12f,8.5f)
     fitted(item.name,descX,y+11f,descWidth,9f,true)
     fitted(item.code+" · "+item.unit,descX,y+24f,descWidth,7.5f)
     if(item.discount>0.001 || item.pct>0.001)
      fitted("Descuento: "+fmt(item.pct)+"% + "+fmt(item.discount),
       descX,y+35f,descWidth,7.5f)
     right(currencyMark+" "+fmt(item.price),margin+contentWidth*0.80f,y+12f,8.5f)
     right(currencyMark+" "+fmt(item.lineTotal),width-margin,y+12f,9f,true)
     if(item.includePhoto){
      drawPhoto(item.photoUri,RectF(descX,y+38f,descX+49f,y+rowH-5f))
     }
    }
    y+=rowH
    rule(y)
    y+=5f
   }
   if(!ticket&&y+112f>height)endPage(true)
   y+=16f
   val subtotal=items.sumOf{if(it.taxable)it.lineTotal/1.18 else it.lineTotal}
   val tax=(total-subtotal).coerceAtLeast(0.0)
   right("SUBTOTAL: "+currencyMark+" "+fmt(subtotal),width-margin,y,10f,true)
   y+=19f
   right("IGV: "+currencyMark+" "+fmt(tax),width-margin,y,10f)
   y+=22f
   right("TOTAL: "+currencyMark+" "+fmt(total),width-margin,y,if(ticket)13f else 15f,true,labelColor)
   y+=20f
   fitted("CONDICIÓN DE PAGO: "+pdfPayment,margin,y,contentWidth,8f)
   y+=14f
   fitted("RESPONSABLE: "+pdfSeller,margin,y,contentWidth,8f)
   y+=14f
   val headerLines=pdfTextLines(company.header,contentWidth,8f,3)
   for(h in headerLines){
    if(y+50f>height)endPage(true)
    fitted(h,margin,y,contentWidth,8f);y+=11f
   }
   val bankLines=pdfTextLines(company.banks,contentWidth,if(ticket)7.2f else 8f,80)
   if(bankLines.isNotEmpty()){
    if(y+58f>height)endPage(true)
    text("CUENTAS BANCARIAS",margin,y,9f,true,labelColor)
    y+=14f
    for(bank in bankLines){
     if(y+47f>height)endPage(true)
     fitted(bank,margin,y,contentWidth,if(ticket)7.2f else 8f)
     y+=11f
    }
   }
   endPage(false)
   pdf.writeTo(out)
  }finally{pdf.close()}
 }

 private fun quoteClientDocumentAddress(quoteId:Long,clientName:String):Pair<String,String>{
  val idCur=db.readableDatabase.rawQuery(
   "SELECT client_id FROM quotes WHERE id=?",arrayOf(quoteId.toString()))
  val clientId=try{
   if(idCur.moveToFirst()&&!idCur.isNull(0))idCur.getLong(0) else null
  }finally{idCur.close()}
  val cc=if(clientId!=null)db.readableDatabase.rawQuery(
   "SELECT document,address FROM clients WHERE id=? LIMIT 1",arrayOf(clientId.toString()))
  else db.readableDatabase.rawQuery(
   "SELECT document,address FROM clients WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(clientName))
  return try{
   if(cc.moveToFirst())Pair(cc.getString(0)?:"",cc.getString(1)?:"")
   else Pair("","")
  }finally{cc.close()}
 }
 private fun writePdfProformaAdaptada(id:Long,out:java.io.OutputStream){
  val company=companyInfo()
  val cur=db.readableDatabase.rawQuery(
    "SELECT client,product,qty,unit_price,discount,igv,currency,total,created,discount_amount,tax_affectation FROM quotes WHERE id=?",
    arrayOf(id.toString()))
  if(!cur.moveToFirst()){cur.close();throw IllegalStateException("Cotización no encontrada")}
  try{
   val client=cur.getString(0)?:"Cliente general"
   val item=cur.getString(1)?:"Producto"
   val qty=cur.getInt(2).toDouble()
   val price=cur.getDouble(3)
   val discountPct=cur.getDouble(4).coerceIn(0.0,100.0)
   val igv=cur.getInt(5)==1
   val currency=cur.getString(6)?:"PEN"
   val total=cur.getDouble(7)
   val rawDate=cur.getString(8)?:""
   val discountAmount=cur.getDouble(9).coerceAtLeast(0.0)
   val taxLabel=if(igv)"GRAVADA" else if(cur.getString(10)=="EXONERADO")"EXONERADA" else "INAFECTO"
   val taxable=if(igv)total/1.18 else total
   val tax=(total-taxable).coerceAtLeast(0.0)
   val discount=(qty*price*discountPct/100.0+discountAmount).coerceAtLeast(0.0)
   val productPicture=loadQuoteProductBitmap(id)
   var code="";var document="";var address=""
   val prod=db.readableDatabase.rawQuery("SELECT code FROM products WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(item))
   if(prod.moveToFirst())code=prod.getString(0)?:""
   prod.close()
   val customerInfo=quoteClientDocumentAddress(id,client)
   document=customerInfo.first;address=customerInfo.second
   val timestamp=try{SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).parse(rawDate)}catch(_:Exception){null}
   val date=if(timestamp!=null)SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).format(timestamp) else rawDate.take(10)
   val time=if(timestamp!=null)SimpleDateFormat("hh:mm a",Locale.US).format(timestamp) else ""
   val money=if(currency=="USD")"US$" else "S/"
   val currName=if(currency=="USD")"DÓLARES" else "SOLES"
   val expiry=pdfExpiry.ifBlank{"POR DEFINIR"}
   val docType=when(document.length){11->"RUC ";8->"DNI ";else->""}
   fun fmt(v:Double)=pdfNumber(v,company.decimals)
   fun words(v:Int):String{
    if(v==0)return "CERO"
    val u=arrayOf("","UN","DOS","TRES","CUATRO","CINCO","SEIS","SIETE","OCHO","NUEVE",
     "DIEZ","ONCE","DOCE","TRECE","CATORCE","QUINCE","DIECISÉIS","DIECISIETE","DIECIOCHO","DIECINUEVE",
     "VEINTE","VEINTIÚN","VEINTIDÓS","VEINTITRÉS","VEINTICUATRO","VEINTICINCO",
     "VEINTISÉIS","VEINTISIETE","VEINTIOCHO","VEINTINUEVE")
    val tens=arrayOf("","","","TREINTA","CUARENTA","CINCUENTA","SESENTA","SETENTA","OCHENTA","NOVENTA")
    val hundreds=arrayOf("","","DOSCIENTOS","TRESCIENTOS","CUATROCIENTOS","QUINIENTOS",
     "SEISCIENTOS","SETECIENTOS","OCHOCIENTOS","NOVECIENTOS")
    fun below100(n:Int):String=if(n<30)u[n] else tens[n/10]+(if(n%10==0)"" else " Y "+u[n%10])
    fun below1000(n:Int):String{
     if(n<100)return below100(n)
     if(n==100)return "CIEN"
     val stem=if(n/100==1)"CIENTO" else hundreds[n/100]
     return stem+(if(n%100==0)"" else " "+below100(n%100))
    }
    if(v<1000)return below1000(v)
    if(v<1000000){
     val th=v/1000;val rest=v%1000
     val first=if(th==1)"MIL" else below1000(th)+" MIL"
     return first+(if(rest==0)"" else " "+below1000(rest))
    }
    return v.toString()
   }


   val cents=(total.coerceAtLeast(0.0)*100).roundToInt()
   val literal="SON "+words(cents/100)+" Y "+(cents%100).toString().padStart(2,'0')+"/100 "+currName

   val ticket=pdfPaper=="Ticket 80 mm"
   val extraTicketHeight=if(ticket && productPicture!=null)105f else 0f
   val pageWidth=if(ticket)227 else if(pdfPaper=="A5 horizontal")595 else 842
   val pageHeight=if(ticket)825+extraTicketHeight.toInt() else if(pdfPaper=="A5 horizontal")420 else 595
   val designW=if(ticket)227f else 842f
   val designH=if(ticket)825f+extraTicketHeight else 595f
   val scale=minOf(pageWidth/designW,pageHeight/designH)
   val pdf=android.graphics.pdf.PdfDocument()
   try{
    val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth,pageHeight,1).create())
    val canvas=page.canvas
    canvas.save()
    canvas.translate((pageWidth-designW*scale)/2f,(pageHeight-designH*scale)/2f)
    canvas.scale(scale,scale)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    fun font(size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
     p.style=Paint.Style.FILL;p.color=color;p.textSize=size
     p.typeface=if(bold)Typeface.create(Typeface.DEFAULT,Typeface.BOLD) else Typeface.DEFAULT
     p.isFakeBoldText=false
    }
    fun text(v:String,x:Float,y:Float,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
     font(size,bold,color);canvas.drawText(v,x,y,p)
    }
    fun center(v:String,x1:Float,x2:Float,y:Float,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
     font(size,bold,color);canvas.drawText(v,x1+(x2-x1-p.measureText(v))/2f,y,p)
    }
    fun right(v:String,x:Float,y:Float,size:Float=9f,bold:Boolean=false){
     font(size,bold);canvas.drawText(v,x-p.measureText(v),y,p)
    }
    fun fit(v:String,x:Float,y:Float,width:Float,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
     var s=size
     font(s,bold,color)
     while(s>6f&&p.measureText(v)>width){s-=0.25f;font(s,bold,color)}
     var t=v
     while(t.isNotEmpty()&&p.measureText(t)>width-2f){t=t.dropLast(1)}
     if(t!=v){t=t.trimEnd()+"…"}
     canvas.drawText(t,x,y,p)
    }
    fun wrapped(value:String,available:Float,size:Float,maxLines:Int):List<String>{
     font(size)
     val parts=mutableListOf<String>()
     var line=""
     for(w in value.replace('\n',' ').split(" ").filter{it.isNotBlank()}){
      if(p.measureText(w)>available && line.isEmpty()){
       var remaining=w
       while(remaining.isNotEmpty()&&parts.size<maxLines){
        var n=remaining.length
        while(n>1&&p.measureText(remaining.take(n))>available)n--
        parts.add(remaining.take(n))
        remaining=remaining.drop(n)
       }
       continue
      }
      val candidate=if(line.isEmpty())w else line+" "+w
      if(line.isNotEmpty()&&p.measureText(candidate)>available){
       parts.add(line);line=w
       if(parts.size>=maxLines)break
      }else line=candidate
     }
     if(parts.size<maxLines&&line.isNotEmpty())parts.add(line)
     return parts.take(maxLines)
    }
    fun stroke(x1:Float,y1:Float,x2:Float,y2:Float,width:Float=0.8f){
     p.color=Color.BLACK;p.style=Paint.Style.STROKE;p.strokeWidth=width
     canvas.drawLine(x1,y1,x2,y2,p);p.style=Paint.Style.FILL
    }
    fun rect(x:Float,y:Float,x2:Float,y2:Float,color:Int){
     p.color=color;p.style=Paint.Style.FILL;canvas.drawRect(x,y,x2,y2,p)
    }
    fun logo(box:RectF){drawCompanyLogo(canvas,box)}
    val num=quoteDisplayNumber(id)
    if(ticket){
     // Recibo legible a 80 mm, de longitud suficiente para las cuentas bancarias.
     logo(RectF(61f,8f,166f,62f))
     fit(company.legal,9f,72f,209f,12f,true)
     center("RUC "+company.ruc,8f,219f,88f,10f)
     rect(9f,96f,218f,121f,Color.rgb(235,235,235))
     center("PROFORMA",9f,218f,113f,15f,true)
     center(num,9f,218f,138f,10.5f)
     fit(company.fiscal,9f,153f,207f,7.4f)
     fit(company.header,9f,165f,207f,7.2f)
     stroke(9f,174f,218f,174f)
     text("EMISIÓN:",9f,190f,8f,true);right(date,217f,190f,8f)
     text("VENCIMIENTO:",9f,205f,8f,true);fit(expiry,101f,205f,117f,8f)
     text("MONEDA:",9f,220f,8f,true);right(currName,217f,220f,8f)
     text("CLIENTE:",9f,238f,8f,true)
     wrapped(client,206f,8.2f,2).forEachIndexed{i,t->text(t,9f,251f+i*11f,8.2f)}
     text("DOCUMENTO: "+(if(document.isBlank())"—" else docType+document),9f,280f,8f)
     val addr=wrapped(if(address.isBlank())"Dirección no registrada" else address,207f,8f,2)
     addr.forEachIndexed{i,t->text(t,9f,294f+i*11f,8f)}
     stroke(9f,319f,218f,319f)
     text("PRODUCTO",9f,337f,9.5f,true)
     text("CÓDIGO: "+code,9f,350f,8f)
     wrapped(item,207f,9f,if(productPicture!=null)2 else 4)
      .forEachIndexed{i,t->text(t,9f,365f+i*12f,9f)}
     if(productPicture!=null){
      drawProductImage(canvas,productPicture,RectF(68f,385f,159f,485f))
     }
     canvas.save()
     canvas.translate(0f,extraTicketHeight)
     stroke(9f,422f,218f,422f)
     text("CANT.: "+fmt(qty),9f,438f,8.5f)
     right("P. UNIT.: "+money+" "+fmt(price),218f,438f,8.5f)
     text("DESCUENTO",9f,454f,8.5f)
     right(money+" "+fmt(discount),218f,454f,8.5f)
     stroke(9f,466f,218f,466f)
     text(taxLabel,9f,484f,9f,true);right(money+" "+fmt(taxable),218f,484f,9f)
     text("IGV 18%",9f,499f,9f,true);right(money+" "+fmt(tax),218f,499f,9f)
     text("TOTAL",9f,518f,11f,true);right(money+" "+fmt(total),218f,518f,11f,true)
     wrapped(literal,205f,7.6f,3).forEachIndexed{i,t->center(t,9f,218f,540f+i*10f,7.6f)}
     stroke(9f,572f,218f,572f)
     fit("USUARIO: "+pdfSeller,9f,586f,207f,8f)
     fit("PAGO: "+pdfPayment,9f,601f,207f,8f)
      val ticketBanks=pdfTextLines(company.banks,205f,7.7f,75)
      text("CUENTAS BANCARIAS",9f,619f,8f,true)
      ticketBanks.take(6).forEachIndexed{i,value->fit(value,9f,633f+i*11f,207f,7.7f)}
      stroke(9f,706f,218f,706f)
      pdfTextLines(company.footer,205f,7.5f,3)
       .forEachIndexed{i,value->center(value,9f,218f,721f+i*11f,7.5f)}
      center(company.commercial.ifBlank{company.legal},9f,218f,776f,10f,true)
     center("Proforma comercial. No es comprobante tributario.",9f,218f,793f,6.5f)
     canvas.restore()
    } else {
     // Composición apaisada para A4 y A5: la tabla ocupa el ancho útil de la hoja.
     val left=20f;val rightEdge=822f
     logo(RectF(22f,18f,132f,96f))
     fit(company.legal,144f,31f,460f,16f,true)
     fit(company.fiscal,144f,48f,455f,9f)
     fit(company.business+" · "+company.regime,144f,62f,455f,8f)
     fit(company.header,144f,86f,470f,10f)
     stroke(621f,17f,821f,17f);stroke(621f,105f,821f,105f)
     stroke(621f,17f,621f,105f);stroke(821f,17f,821f,105f)
     center("RUC "+company.ruc,621f,821f,37f,13f)
     rect(621f,47f,821f,77f,Color.rgb(235,235,235))
     center("PROFORMA",621f,821f,69f,17f,true)
     center(num,621f,821f,98f,13f)
     text("DOCUMENTO",20f,122f,9f,true)
     text("CLIENTE",20f,136f,9f,true)
     text("DIRECCIÓN",20f,150f,9f,true)
     fit(if(document.isEmpty())"—" else docType+document,104f,122f,423f,9f)
     fit(client,104f,136f,423f,9f)
     fit(if(address.isEmpty())"NO REGISTRADA" else address,104f,150f,435f,9f)
     text("FECHA EMISIÓN",561f,122f,9f,true);right(date,816f,122f,9f)
     text("VENCIMIENTO",561f,136f,9f,true);right(expiry,816f,136f,9f)
     text("MONEDA",561f,150f,9f,true);right(currName,816f,150f,9f)
     // Único conjunto de columnas para cuadrícula y textos.
     val xs=floatArrayOf(left,49f,131f,199f,557f,628f,701f,763f,rightEdge)
     val top=164f;val headerBottom=184f;val bottom=350f
     val heads=arrayOf("Nº","UNIDAD","CÓDIGO","DESCRIPCIÓN","CANT.","V. UNIT.","DSCTO","TOTAL")
     rect(left,top,rightEdge,headerBottom,Color.BLACK)
     fun cell(col:Int,value:String,y:Float,align:Int,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
      val x1=xs[col]+4f;val x2=xs[col+1]-4f
      var sz=size;font(sz,bold,color)
      while(sz>6f && p.measureText(value)>x2-x1){sz-=0.2f;font(sz,bold,color)}
      var txt=value
      while(txt.isNotEmpty()&&p.measureText(txt)>x2-x1-2f)txt=txt.dropLast(1)
      if(txt!=value)txt+="…"
      val width=p.measureText(txt)
      val x=when(align){1->x1+(x2-x1-width)/2f;2->x2-width;else->x1}
      canvas.save();canvas.clipRect(xs[col]+1f,top,xs[col+1]-1f,bottom)
      canvas.drawText(txt,x,y,p);canvas.restore()
     }
     for(i in heads.indices)cell(i,heads[i],178f,1,9f,true,Color.WHITE)
     for(x in xs)stroke(x,headerBottom,x,bottom,0.8f)
     stroke(left,bottom,rightEdge,bottom,0.8f)
     cell(0,"1",197f,1)
     cell(1,"UNIDADES",197f,1,8f)
     cell(2,code,197f,1,8.5f)
     wrapped(item,xs[4]-xs[3]-9f,9f,if(productPicture!=null)4 else 16)
      .forEachIndexed{i,t->if(197f+i*10f<bottom-3f)cell(3,t,197f+i*10f,0,9f)}
     if(productPicture!=null){
      drawProductImage(canvas,productPicture,RectF(xs[3]+12f,247f,xs[3]+156f,341f))
     }
     cell(4,fmt(qty),197f,2,9f)
     cell(5,fmt(price),197f,2,9f)
     cell(6,fmt(discount),197f,2,9f)
     cell(7,fmt(taxable),197f,2,9f)
     stroke(left,372f,rightEdge,372f)
     stroke(left,bottom,left,446f);stroke(rightEdge,bottom,rightEdge,446f)
     val amountText=wrapped(literal,770f,9f,1).firstOrNull()?:""
     center(amountText,left,rightEdge,366f,9f)
     stroke(left,446f,rightEdge,446f)
     text(taxLabel,610f,397f,10f,true)
     text("I.G.V. 18%",610f,415f,10f,true)
     text("TOTAL",610f,435f,11f,true)
     text(money,700f,397f,10f);text(money,700f,415f,10f);text(money,700f,435f,11f,true)
     right(fmt(taxable),811f,397f,10f)
     right(fmt(tax),811f,415f,10f)
     right(fmt(total),811f,435f,11f,true)
     text("USUARIO",20f,464f,8.8f,true)
     text("CONDICIÓN DE PAGO",20f,477f,8.8f,true)
     text("CUENTAS BANCARIAS",20f,490f,8.8f,true)
     fit(pdfSeller+" - "+date+" "+time,151f,464f,420f,9f)
     fit(pdfPayment,151f,477f,405f,9f)
      fit(company.legal,151f,490f,660f,8.8f)
      val landscapeBanks=pdfTextLines(company.banks,660f,7.8f,75)
      landscapeBanks.take(5).forEachIndexed{i,value->fit(value,151f,500f+i*9f,659f,7.8f)}
      pdfTextLines(company.footer,790f,8.2f,2)
       .forEachIndexed{i,value->center(value,left,rightEdge,551f+i*11f,8.2f)}
      center(company.commercial.ifBlank{company.legal},left,rightEdge,578f,11f,true)
     center("Proforma comercial generada mediante BELSIPERU. No es comprobante de pago electrónico.",left,rightEdge,591f,7.2f)
    }
    canvas.restore()
    productPicture?.recycle()
    pdf.finishPage(page)
    val allBanks=pdfTextLines(company.banks,if(ticket)205f else 660f,
     if(ticket)7.7f else 7.8f,300)
    addBankContinuationPages(pdf,allBanks.drop(if(ticket)6 else 5),
     pageWidth,pageHeight)
    pdf.writeTo(out)
   }finally{pdf.close()}
  }finally{cur.close()}
 }

 private fun writePdfProforma(id:Long,out:java.io.OutputStream){
  val company=companyInfo()
  if(pdfPaper=="A4 horizontal" || pdfPaper=="A5 horizontal" || pdfPaper=="Ticket 80 mm") {
   writePdfProformaAdaptada(id,out)
   return
  }
  val c=db.readableDatabase.rawQuery(
   "SELECT client,product,qty,unit_price,discount,igv,currency,total,created,product_photo,discount_amount,tax_affectation FROM quotes WHERE id=?",
   arrayOf(id.toString()))
  if(!c.moveToFirst()){c.close();throw IllegalStateException("No existe esta cotización")}
  try{
   val client=c.getString(0)?:"Cliente general"
   val product=c.getString(1)?:"Producto"
   val qty=c.getInt(2).toDouble()
   val unitPrice=c.getDouble(3)
   val pct=c.getDouble(4).coerceIn(0.0,100.0)
   val applyTax=c.getInt(5)==1
   val currency=c.getString(6)?:"PEN"
   val total=c.getDouble(7)
   val created=c.getString(8)?:""
   val fixed=c.getDouble(10).coerceAtLeast(0.0)
   val taxLabel=if(applyTax)"GRAVADA" else if(c.getString(11)=="EXONERADO")"EXONERADA" else "INAFECTO"
   val net=if(applyTax)total/1.18 else total
   val tax=(total-net).coerceAtLeast(0.0)
   val discount=(qty*unitPrice*pct/100.0+fixed).coerceAtLeast(0.0)
   val productPicture=loadQuoteProductBitmap(id)
   var code="";var document="";var address=""
   val prod=db.readableDatabase.rawQuery(
    "SELECT code FROM products WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(product))
   if(prod.moveToFirst())code=prod.getString(0)?:""
   prod.close()
   val clientInfo=quoteClientDocumentAddress(id,client)
   document=clientInfo.first;address=clientInfo.second
   val curDate=try{
    val input=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US)
    val d=input.parse(created)
    if(d==null)created.take(10) else SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).format(d)
   }catch(_:Exception){created.take(10)}
   val curTime=try{
    val d=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).parse(created)
    if(d==null)"" else SimpleDateFormat("hh:mm a",Locale.US).format(d)
   }catch(_:Exception){""}
   val expiry=if(pdfExpiry.isBlank())"POR DEFINIR" else pdfExpiry
   val docTag=when(document.length){11->"RUC ";8->"DNI ";else->""}
   val money=if(currency=="USD")"US$" else "S/"
   val nameCurrency=if(currency=="USD")"DÓLARES" else "SOLES"
   fun amount(n:Double)=pdfNumber(n,company.decimals)
   val pdf=android.graphics.pdf.PdfDocument()
   // La carta conserva exactamente el diseño aprobado. A4 y A5 vertical
   // imprimen la misma maqueta con dimensiones físicas reales y escala uniforme.
   val pageSize=when(pdfPaper){
    "A4 vertical"->Pair(595,842)
    "A5 vertical"->Pair(420,595)
    else->Pair(612,792)
   }
   val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageSize.first,pageSize.second,1).create())
   val canvas=page.canvas
   val scale=minOf(pageSize.first/612f,pageSize.second/792f)
   val padX=(pageSize.first-612f*scale)/2f
   val padY=(pageSize.second-792f*scale)/2f
   val horizontalOffset=(612f-(15.05f+544.62f))/2f
   canvas.save()
   canvas.translate(padX,padY)
   canvas.scale(scale,scale)
   canvas.translate(horizontalOffset,0f)
   val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;typeface=Typeface.DEFAULT}
   fun font(size:Float,bold:Boolean=false,color:Int=Color.BLACK){
    p.textSize=size;p.color=color;p.style=Paint.Style.FILL
    p.typeface=if(bold)Typeface.create(Typeface.DEFAULT,Typeface.BOLD) else Typeface.DEFAULT
    p.isFakeBoldText=false
   }
   fun text(value:String,x:Float,y:Float,size:Float=9f,bold:Boolean=false,color:Int=Color.BLACK){
    font(size,bold,color);canvas.drawText(value,x,y,p)
   }
   fun fit(value:String,x:Float,y:Float,maxW:Float,size:Float=9f,bold:Boolean=false){
    var sz=size
    font(sz,bold)
    while(sz>6.5f && p.measureText(value)>maxW){sz-=0.3f;font(sz,bold)}
    var t=value
    while(t.isNotEmpty() && p.measureText(t)>maxW-5f)t=t.dropLast(1)
    if(t!=value)t=t.trimEnd()+"…"
    canvas.drawText(t,x,y,p)
   }
   fun center(value:String,x:Float,right:Float,y:Float,size:Float=9f,bold:Boolean=false){
    font(size,bold);canvas.drawText(value,x+(right-x-p.measureText(value))/2f,y,p)
   }
   fun right(value:String,right:Float,y:Float,size:Float=9f,bold:Boolean=false){
    font(size,bold);canvas.drawText(value,right-p.measureText(value),y,p)
   }
   fun line(x1:Float,y1:Float,x2:Float,y2:Float,width:Float=0.8f){
    p.color=Color.BLACK;p.style=Paint.Style.STROKE;p.strokeWidth=width
    canvas.drawLine(x1,y1,x2,y2,p);p.style=Paint.Style.FILL
   }
   fun rect(x:Float,y:Float,right:Float,bottom:Float,fill:Int?=null){
    p.style=Paint.Style.FILL;p.color=fill?:Color.BLACK
    canvas.drawRect(x,y,right,bottom,p)
   }
   fun wrap(value:String,maxWidth:Float,size:Float,maxLines:Int):List<String>{
    font(size)
    val result=mutableListOf<String>()
    var current=""
    val words=value.replace("\n"," ").split(" ").filter{it.isNotBlank()}
    for(word in words){
     val proposed=if(current.isEmpty())word else current+" "+word
     if(current.isNotEmpty()&&p.measureText(proposed)>maxWidth){
      result.add(current);current=word
     }else current=proposed
     if(result.size>=maxLines)break
    }
    if(result.size<maxLines && current.isNotEmpty())result.add(current)
    return result.take(maxLines)
   }
   fun words(v:Int):String{
    if(v==0)return "CERO"
    val u=arrayOf("","UN","DOS","TRES","CUATRO","CINCO","SEIS","SIETE","OCHO","NUEVE",
     "DIEZ","ONCE","DOCE","TRECE","CATORCE","QUINCE","DIECISÉIS","DIECISIETE","DIECIOCHO","DIECINUEVE",
     "VEINTE","VEINTIÚN","VEINTIDÓS","VEINTITRÉS","VEINTICUATRO","VEINTICINCO",
     "VEINTISÉIS","VEINTISIETE","VEINTIOCHO","VEINTINUEVE")
    val tens=arrayOf("","","","TREINTA","CUARENTA","CINCUENTA","SESENTA","SETENTA","OCHENTA","NOVENTA")
    val hundreds=arrayOf("","","DOSCIENTOS","TRESCIENTOS","CUATROCIENTOS","QUINIENTOS",
     "SEISCIENTOS","SETECIENTOS","OCHOCIENTOS","NOVECIENTOS")
    fun below100(n:Int):String=if(n<30)u[n] else tens[n/10]+(if(n%10==0)"" else " Y "+u[n%10])
    fun below1000(n:Int):String{
     if(n<100)return below100(n)
     if(n==100)return "CIEN"
     val stem=if(n/100==1)"CIENTO" else hundreds[n/100]
     return stem+(if(n%100==0)"" else " "+below100(n%100))
    }
    if(v<1000)return below1000(v)
    if(v<1000000){
     val th=v/1000;val rest=v%1000
     val first=if(th==1)"MIL" else below1000(th)+" MIL"
     return first+(if(rest==0)"" else " "+below1000(rest))
    }
    return v.toString()
   }
   val cents=(total.coerceAtLeast(0.0)*100).roundToInt()
   val literal="SON "+words(cents/100)+" Y "+(cents%100).toString().padStart(2,'0')+"/100 "+nameCurrency

   // Encabezado: logotipo, razón social, dirección y recuadro de proforma.
   drawCompanyLogo(canvas,RectF(15.05f,15.05f,109.11f,109.11f))
   fit(company.legal,116.6f,33.5f,226f,13.2f,true)
   pdfTextLines(company.fiscal,226f,8.47f,3).forEachIndexed{i,value->
    fit(value,116.6f,51f+i*11f,226f,8.47f)
   }
   // Dirección fiscal configurable en las líneas anteriores.
   // No se inserta una dirección fija en la proforma.
   pdfTextLines(company.header,226f,8.47f,2).forEachIndexed{i,value->
    fit(value,116.6f,93f+i*11f,226f,8.47f)
   }
   // Cabecera editable desde Configuración → Impresión.
   rect(348.03f,44.21f,543.68f,74.31f,Color.rgb(238,238,238))
   line(347.09f,15.52f,544.62f,15.52f,0.94f);line(347.09f,103.0f,544.62f,103.0f,0.94f)
   line(347.56f,15.05f,347.56f,103.47f,0.94f);line(544.15f,15.05f,544.15f,103.47f,0.94f)
   center("RUC "+company.ruc,347.09f,544.62f,35.7f,13.17f)
   center("PROFORMA",347.09f,544.62f,65.0f,15.05f,true)
   center(quoteDisplayNumber(id),347.09f,544.62f,94f,13.17f)
   // Ficha cliente y emisión.
   text("DOCUMENTO",15.05f,133.3f,8.47f,true)
   text("CLIENTE",15.05f,145.5f,8.47f,true)
   text("DIRECCIÓN",15.05f,157.7f,8.47f,true)
   fit(if(document.isBlank())"—" else docTag+document,90.3f,133.3f,257f,8.47f)
   fit(client,90.3f,145.5f,266f,8.47f)
   val addrLines=wrap(if(address.isBlank())"NO REGISTRADA" else address,266f,8.47f,2)
   addrLines.forEachIndexed{index,v->fit(v,90.3f,157.7f+8.5f*index,266f,8.47f)}
   text("FECHA EMISIÓN",365.9f,133.3f,8.47f,true);right(curDate,520f,133.3f,8.47f)
   text("FECHA VENCIMIENTO",365.9f,145.5f,8.47f,true);right(expiry,520f,145.5f,8.47f)
   text("MONEDA",365.9f,157.7f,8.47f,true);fit(if(currency=="USD")"DÓLARES" else "SOLES",469.4f,157.7f,71f,8.47f)
   // Geometría ÚNICA para las ocho columnas: bordes, títulos y valores.
   // Cada valor se centra o alinea dentro de su propia celda, sin coordenadas manuales.
   val tableLeft=15.05f
   val tableRight=544.62f
   val topHeader=186.24f
   val bottomHeader=200.35f
   val tableBottom=473.13f
   val xs=floatArrayOf(tableLeft,36.68f,86.54f,134.51f,360.26f,399.76f,455.26f,501.35f,tableRight)
   val heads=arrayOf("Nº","UNIDAD","CÓDIGO","DESCRIPCIÓN","CANT.","V. UNIT.","DSCTO","TOTAL")
   // Columna (0-7); alineación (0 izquierda, 1 centro, 2 derecha).
   fun tableCell(col:Int,value:String,baseline:Float,alignment:Int,size:Float=8f,
                 bold:Boolean=false,color:Int=Color.BLACK){
    val left=xs[col]+4f
    val rightEdge=xs[col+1]-4f
    val available=rightEdge-left
    var textSize=size
    font(textSize,bold,color)
    while(textSize>6.2f && p.measureText(value)>available){
     textSize-=0.25f
     font(textSize,bold,color)
    }
    var label=value
    if(p.measureText(label)>available){
     while(label.isNotEmpty()&&p.measureText(label+"…")>available)label=label.dropLast(1)
     label+="…"
    }
    val textWidth=p.measureText(label)
    val x=when(alignment){
     1->left+(available-textWidth)/2f
     2->rightEdge-textWidth
     else->left
    }
    canvas.save()
    canvas.clipRect(xs[col]+1f,topHeader,xs[col+1]-1f,tableBottom)
    canvas.drawText(label,x,baseline,p)
    canvas.restore()
   }
   // Barra negra y bordes externos con trazos consistentes.
   rect(tableLeft,topHeader,tableRight,bottomHeader,Color.BLACK)
   for(k in heads.indices){
    tableCell(k,heads[k],197.3f,1,if(k==3)9f else 8.5f,true,Color.WHITE)
   }
   for(x in xs)line(x,bottomHeader,x,tableBottom,0.8f)
   line(tableLeft,tableBottom,tableRight,tableBottom,0.8f)
   // Todos los renglones usan la misma línea base para que no se vean desparejos.
   val rowBaseline=211.0f
   tableCell(0,"1",rowBaseline,1,7.8f)
   tableCell(1,"UNIDADES",rowBaseline,1,7.8f)
   tableCell(2,code,rowBaseline,1,7.8f)
   val descriptionLines=wrap(product,xs[4]-xs[3]-8f,7.8f,if(productPicture!=null)14 else 29)
   descriptionLines.forEachIndexed{k,wordLine->
    val baseline=rowBaseline+k*8.6f
    if(baseline<tableBottom-3f)tableCell(3,wordLine,baseline,0,7.8f)
   }
   if(productPicture!=null){
    val pictureTop=maxOf(259f,rowBaseline+descriptionLines.size*8.6f+13f)
    val pictureBottom=minOf(tableBottom-7f,pictureTop+130f)
    if(pictureBottom-pictureTop>32f){
     drawProductImage(canvas,productPicture,RectF(xs[3]+8f,pictureTop,xs[3]+163f,pictureBottom))
    }
   }
   tableCell(4,amount(qty),rowBaseline,2,7.8f)
   tableCell(5,amount(unitPrice),rowBaseline,2,7.8f)
   tableCell(6,amount(discount),rowBaseline,2,7.8f)
   tableCell(7,amount(net),rowBaseline,2,7.8f)
      // Total en letras y resumen.
   line(15.05f,489.59f,544.62f,489.59f,0.94f)
   line(15.52f,473.13f,15.52f,490.06f,0.94f);line(544.15f,473.13f,544.15f,490.06f,0.94f)
   var literalSize=8.47f;font(literalSize)
   while(literalSize>6.4f && p.measureText(literal)>512f){literalSize-=0.2f;font(literalSize)}
   center(literal,15.05f,544.62f,484.3f,literalSize)
   line(15.52f,489.12f,15.52f,541.8f,0.94f);line(544.15f,489.12f,544.15f,541.8f,0.94f)
   line(15.05f,541.33f,544.62f,541.33f,0.94f)
   right(taxLabel,418f,506f,9.4f,true)
   right("I.G.V. 18%",418f,519.1f,9.4f,true)
   right("TOTAL",418f,532.3f,9.4f,true)
   text(money,447.7f,506f,9.4f)
   text(money,447.7f,519.1f,9.4f)
   text(money,447.7f,532.3f,9.4f,true)
   right(amount(net),536.4f,506f,9.4f)
   right(amount(tax),536.4f,519.1f,9.4f)
   right(amount(total),536.4f,532.3f,9.4f,true)
   // Información comercial y bancaria de la empresa.
   text("USUARIO",15.05f,564f,8.47f,true)
   text("CONDICIÓN DE PAGO",15.05f,576.2f,8.47f,true)
   text("CUENTAS BANCARIAS",15.05f,588.4f,8.47f,true)
   fit(pdfSeller+" - "+curDate+" "+curTime,156.1f,564f,373f,8.47f)
   fit(pdfPayment,156.1f,576.2f,373f,8.47f)
   fit(company.legal,156.1f,588.4f,370f,8.47f)
   val singleBankLines=pdfTextLines(company.banks,365f,8f,300)
   singleBankLines.take(9).forEachIndexed{i,value->
    fit(value,156.1f,602f+i*9.8f,365f,8f)
   }
   // Centrar cada línea del pie sin alterar el texto configurable.
   pdfTextLines(company.footer,520f,8.4f,2).forEachIndexed{i,value->
    center(value,15.05f,544.62f,710f+i*11f,8.4f)
   }
   center(company.commercial.ifBlank{company.legal},15.05f,544.62f,753f,11.3f,true)
   center("Proforma comercial emitida mediante BELSIPERU. No constituye comprobante de pago electrónico.",15.05f,544.62f,766f,7.5f)
   canvas.restore()
   productPicture?.recycle()
   pdf.finishPage(page)
   addBankContinuationPages(pdf,singleBankLines.drop(9),
    pageSize.first,pageSize.second)
   try{pdf.writeTo(out)}finally{pdf.close()}
  }finally{c.close()}
 }


 // Diseños comerciales: referencias visuales aportadas por el usuario.
 // Moderno y Corporativo usan el mismo catálogo, importes, moneda, clientes y
 // configuración empresarial. La Proforma BELSIPERU original no se altera.
 private data class StyledPdfItem(
  val code:String,val name:String,val unit:String,val qty:Int,
  val price:Double,val discountPct:Double,val discountAmount:Double,
  val taxable:Boolean,val total:Double,val photoUri:String?,
  val includePhoto:Boolean,val brand:String
 )
 private fun writePdfStyled(id:Long,out:java.io.OutputStream,modern:Boolean){
  val config=companyInfo()
  val database=db.readableDatabase
  val head=database.rawQuery(
   "SELECT client,client_id,currency,total,created FROM quotes WHERE id=?",
   arrayOf(id.toString()))
  var customerName="";var customerId:Long?=null
  var quoteCurrency="PEN";var quoteTotal=0.0;var quoteDate=""
  try{
   if(!head.moveToFirst())throw IllegalArgumentException("Cotización inexistente.")
   customerName=head.getString(0)?:""
   if(!head.isNull(1))customerId=head.getLong(1)
   quoteCurrency=head.getString(2)?:"PEN"
   quoteTotal=head.getDouble(3)
   quoteDate=head.getString(4)?:""
  }finally{head.close()}
  val customer=if(customerId!=null)
   database.rawQuery("SELECT document,address,phone,email,notes FROM clients WHERE id=?",
    arrayOf(customerId.toString()))
  else database.rawQuery(
   "SELECT document,address,phone,email,notes FROM clients WHERE name=? ORDER BY id DESC LIMIT 1",
   arrayOf(customerName))
  var document="";var address="";var phone="";var email="";var notes=""
  try{
   if(customer.moveToFirst()){
    document=customer.getString(0)?:""
    address=customer.getString(1)?:""
    phone=customer.getString(2)?:""
    email=customer.getString(3)?:""
    notes=customer.getString(4)?:""
   }
  }finally{customer.close()}
  val items=mutableListOf<StyledPdfItem>()
  val rows=database.rawQuery(
   "SELECT qi.code,qi.name,qi.unit,qi.qty,qi.unit_price,"+
    "qi.discount_pct,qi.discount_amount,qi.igv,qi.line_total,"+
    "COALESCE(NULLIF(qi.photo_uri,''),p.photo_uri),qi.include_product_photo,"+
    "COALESCE(p.brand,'') FROM quote_items qi LEFT JOIN products p ON p.id=qi.product_id "+
    "WHERE qi.quote_id=? ORDER BY qi.id",
   arrayOf(id.toString()))
  try{
   while(rows.moveToNext()){
    items.add(StyledPdfItem(
     rows.getString(0)?:"",rows.getString(1)?:"Producto",
     rows.getString(2)?:"UNIDADES",rows.getInt(3),rows.getDouble(4),
     rows.getDouble(5),rows.getDouble(6),rows.getInt(7)==1,
     rows.getDouble(8),rows.getString(9),rows.getInt(10)==1,
     rows.getString(11)?:""
    ))
   }
  }finally{rows.close()}
  if(items.isEmpty())throw IllegalArgumentException("La cotización no tiene artículos.")
  val paper=when(pdfPaper){
   "A4 vertical"->Pair(595,842)
   "A4 horizontal"->Pair(842,595)
   "A5 vertical"->Pair(420,595)
   "A5 horizontal"->Pair(595,420)
   else->Pair(612,792)
  }
  // La plantilla original usa casi 792 pt de altura. Al imprimir en A4
  // horizontal o A5, la página física es más baja; adaptamos TODO el diseño
  // con escala uniforme (tipografía, fotografías, columnas y pie incluidos).
  // Así 1 artículo + resumen cabe en una página sin deformar el contenido.
  val pageWidth=paper.first.toFloat()
  val pageHeight=paper.second.toFloat()
  val pageScale=minOf(1f,pageHeight/792f)
  val w=pageWidth/pageScale
  val h=pageHeight/pageScale
  val compact=w<500f || h<600f
  val margin=if(compact)20f else 28f
  val cw=w-2f*margin
  val navy=Color.rgb(19,49,93)
  val teal=Color.rgb(15,118,93)
  val gold=Color.rgb(241,179,42)
  val inkColor=Color.rgb(34,44,62)
  val subdued=Color.rgb(91,102,117)
  val light=Color.rgb(243,246,249)
  val primary=if(modern)teal else navy
  val currencyMark=if(quoteCurrency=="USD")"US$" else "S/"
  val documentCode=if(modern)"COTIZACIÓN N° " else "PROFORMA DE VENTA · "
  val quoteNumber=quoteDisplayNumber(id)
  val formattedDate=runCatching{
   val parse=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).apply{isLenient=false}
   val date=parse.parse(quoteDate)
   if(date!=null)SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).format(date)
   else quoteDate.take(10)
  }.getOrDefault(quoteDate.take(10))
  val documentName=if(document.length==11)"RUC" else if(document.length==8)"DNI" else "Documento"
  val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  val pdf=android.graphics.pdf.PdfDocument()
  fun font(size:Float,bold:Boolean=false,color:Int=inkColor){
   paint.style=Paint.Style.FILL
   paint.color=color;paint.textSize=size
   paint.typeface=Typeface.create(Typeface.DEFAULT,if(bold)Typeface.BOLD else Typeface.NORMAL)
  }
  lateinit var page:android.graphics.pdf.PdfDocument.Page
  lateinit var canvas:Canvas
  var pageNumber=0
  var y=0f
  fun rect(x1:Float,y1:Float,x2:Float,y2:Float,color:Int){
   paint.style=Paint.Style.FILL;paint.color=color
   canvas.drawRect(x1,y1,x2,y2,paint)
  }
  fun text(value:String,x:Float,baseline:Float,size:Float=9f,
   bold:Boolean=false,color:Int=inkColor){
   font(size,bold,color)
   canvas.drawText(value,x,baseline,paint)
  }
  fun textRight(value:String,right:Float,baseline:Float,size:Float=9f,
   bold:Boolean=false,color:Int=inkColor){
   font(size,bold,color)
   canvas.drawText(value,right-paint.measureText(value),baseline,paint)
  }
  fun textCenter(value:String,cx:Float,baseline:Float,size:Float=9f,
   bold:Boolean=false,color:Int=inkColor){
   font(size,bold,color)
   canvas.drawText(value,cx-paint.measureText(value)/2f,baseline,paint)
  }
  fun clipped(value:String,x:Float,baseline:Float,maxW:Float,size:Float=9f,
   bold:Boolean=false,color:Int=inkColor){
   font(size,bold,color)
   var chosen=value.replace("\n"," ").trim()
   if(paint.measureText(chosen)>maxW){
    while(chosen.isNotEmpty()&&paint.measureText(chosen+"…")>maxW)
     chosen=chosen.dropLast(1)
    chosen+="…"
   }
   if(chosen.isNotBlank())canvas.drawText(chosen,x,baseline,paint)
  }
  fun horizontal(atY:Float,color:Int=Color.rgb(216,222,227)){
   rect(margin,atY,w-margin,atY+0.9f,color)
  }
  fun drawPhoto(uriText:String?,box:RectF){
   if(uriText.isNullOrBlank())return
   try{
    val uri=Uri.parse(uriText)
    if(uri.scheme!="content")return
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
    if(bounds.outWidth<=0||bounds.outHeight<=0)return
    var sample=1
    while(bounds.outWidth/sample>320||bounds.outHeight/sample>320)sample*=2
    val bitmap=contentResolver.openInputStream(uri)?.use{
     BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})
    }
    if(bitmap!=null){
     drawProductImage(canvas,bitmap,box)
     bitmap.recycle()
    }
   }catch(_:Exception){}catch(_:OutOfMemoryError){}
  }
  // Cada modelo tiene columnas y encabezados propios, como las referencias del cliente.
  val colRatio=if(modern)
   floatArrayOf(0.055f,0.115f,0.065f,0.08f,0.31f,0.13f,0.13f,0.115f)
  else floatArrayOf(0.075f,0.075f,0.37f,0.10f,0.08f,0.08f,0.11f,0.11f)
  val xs=FloatArray(9)
  xs[0]=margin
  for(i in colRatio.indices)xs[i+1]=xs[i]+colRatio[i]*cw
  val tableFont=if(compact)6.3f else 7.6f
  val labels=if(modern)
   listOf("ÍTEM","CÓDIGO","CANT.","UND.","DESCRIPCIÓN / MARCA",
    "P. UNIT.","P. TOTAL","ENTR.")
  else listOf("CANT.","UND.","DESCRIPCIÓN","P. BRUTO",
   "% DTO 01","% DTO 02","P. NETO","TOTAL")
  fun tableHeader(){
   val paperColor=if(modern)Color.rgb(250,172,31) else Color.rgb(248,194,40)
   paint.style=Paint.Style.FILL;paint.color=paperColor
   canvas.drawRoundRect(RectF(margin,y,w-margin,y+24f),7f,7f,paint)
   paint.style=Paint.Style.STROKE;paint.strokeWidth=0.7f
   paint.color=Color.rgb(55,52,46)
   canvas.drawRoundRect(RectF(margin,y,w-margin,y+24f),7f,7f,paint)
   paint.style=Paint.Style.FILL
   for(i in labels.indices){
    clipped(labels[i],xs[i]+3f,y+16f,xs[i+1]-xs[i]-5f,
     if(compact)5.75f else 6.6f,true,inkColor)
   }
   y+=26f
  }
  fun firstPageHeader(){
   if(modern){
    // IFITSA: gran banner comercial, ángulo naranja, tira de rubros.
    rect(0f,0f,w,108f,navy)
    val angle=Path().apply{
     moveTo(0f,0f);lineTo(w*0.295f,0f);lineTo(w*0.235f,108f)
     lineTo(0f,108f);close()
    }
    paint.style=Paint.Style.FILL;paint.color=Color.rgb(250,168,29)
    canvas.drawPath(angle,paint)
    // Logo transparente, ampliado sobre el panel naranja; sin caja blanca.
    drawCompanyLogo(canvas,RectF(margin+2f,22f,margin+123f,86f),true)
    // Deja el nombre, rubro y RUC íntegramente sobre el fondo azul,
    // sin invadir el trapecio naranja ni el espacio del logotipo.
    val x=maxOf(w*0.32f,margin+147f)
    clipped(config.legal,x,43f,w-margin-x,if(compact)12f else 16f,true,Color.WHITE)
    clipped(config.business.ifBlank{"Herramientas y maquinaria industrial"},
     x,61f,w-margin-x,if(compact)7f else 8.5f,false,Color.WHITE)
    clipped("RUC "+config.ruc,x,80f,w-margin-x,8f,false,Color.WHITE)
    rect(0f,108f,w,125f,Color.rgb(244,246,248))
    textCenter(config.commercial.ifBlank{config.legal}+"  •  GESTIÓN COMERCIAL",
     w/2f,120f,if(compact)6.3f else 8.1f,true,teal)
    textCenter("COTIZACIÓN N° "+quoteNumber,w/2f,151f,
     if(compact)12.5f else 17.3f,true,inkColor)
    text("Fecha: "+formattedDate,margin,173f,8.3f,false,subdued)
    text("Señores",margin+8f,191f,8f,true)
    clipped(customerName.ifBlank{"Cliente"},margin+84f,191f,cw*0.52f,8.3f)
    text(documentName,margin+8f,205f,8f,true)
    clipped(document,margin+84f,205f,cw*0.48f,8.1f)
    text("Atención",margin+8f,219f,8f,true)
    clipped(customerName,margin+84f,219f,cw*0.50f,8.1f)
    text("Dirección",margin+8f,233f,8f,true)
    clipped(address,margin+84f,233f,cw*0.60f,7.9f)
    if(phone.isNotBlank()){
     text("Tel.",w-margin-cw*0.26f,192f,7.5f,true)
     textRight(phone,w-margin,192f,7.5f)
    }
    if(email.isNotBlank())
     clipped(email,w-margin-cw*0.30f,207f,cw*0.30f,7.1f)
    text("De nuestra consideración:",margin,261f,8f)
    text("Nos es grato dirigirnos a ustedes para hacerles llegar nuestra oferta comercial.",
     margin,275f,if(compact)6.4f else 8.0f)
    y=290f
   }else{
    // FULLTRADE: franja azul angular, título centrado y ficha sin recuadro.
    rect(0f,0f,w,83f,navy)
    val angle=Path().apply{
     moveTo(w-61f,83f);lineTo(w,9f);lineTo(w,83f);close()
    }
    paint.style=Paint.Style.FILL;paint.color=Color.WHITE
    canvas.drawPath(angle,paint)
    // El logo se recorta y ocupa el panel dorado, sin fondo blanco.
    val brandPanel=Path().apply{
     moveTo(margin,11f);lineTo(margin+146f,11f)
     lineTo(margin+131f,73f);lineTo(margin,73f);close()
    }
    paint.style=Paint.Style.FILL;paint.color=Color.rgb(245,182,43)
    canvas.drawPath(brandPanel,paint)
    drawCompanyLogo(canvas,RectF(margin+5f,19f,margin+122f,67f),true)
    val x=margin+154f
    clipped(config.legal,x,32f,w-margin-x-42f,
     if(compact)11.8f else 17f,true,Color.WHITE)
    clipped(config.header,x,48f,w-margin-x-42f,
     if(compact)5.3f else 7.2f,true,Color.WHITE)
    clipped("RUC "+config.ruc,x,64f,w-margin-x-42f,8f,false,Color.WHITE)
    textRight("Fecha: "+formattedDate,w-margin,107f,8.5f,false,subdued)
    textCenter("PROFORMA DE VENTA",w/2f,148f,
     if(compact)15f else 20.5f,false,inkColor)
    textCenter(quoteNumber,w/2f,169f,10.8f)
    text("Señor(es)",margin+8f,192f,8f,true)
    clipped(customerName,margin+93f,192f,cw-112f,8.2f)
    text("Atención",margin+8f,208f,8f,true)
    clipped(customerName,margin+93f,208f,cw-112f,8.2f)
    text("Dirección",margin+8f,224f,8f,true)
    clipped(address,margin+93f,224f,cw-112f,8.2f)
    text(documentName,margin+8f,240f,8f,true)
    clipped(document,margin+93f,240f,cw-112f,8.2f)
    if(phone.isNotBlank()){
     text("Teléfono",margin+8f,256f,8f,true)
     text(phone,margin+93f,256f,8.2f)
    }
    if(email.isNotBlank())clipped(email,w-margin-cw*0.38f,256f,cw*0.38f,7.5f)
    text("Por la presente, nos es grato cotizarle lo siguiente:",
     margin,283f,8f)
    y=295f
   }
  }
  fun nextPageHeader(){
   rect(0f,0f,w,62f,navy)
   rect(0f,61f,w,65f,if(modern)teal else gold)
   rect(margin,9f,margin+98f,55f,
    if(modern)Color.rgb(250,168,29) else Color.rgb(245,182,43))
   drawCompanyLogo(canvas,RectF(margin+4f,13f,margin+94f,51f),true)
   clipped(config.legal,margin+106f,29f,cw-106f,11f,true,Color.WHITE)
   clipped("Continuación · "+quoteNumber,margin+106f,45f,cw-106f,8.2f,false,Color.WHITE)
   y=84f
  }
  fun beginPage(continued:Boolean,withTable:Boolean=true){
   pageNumber+=1
   page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(
    paper.first,paper.second,pageNumber).create())
   canvas=page.canvas
   canvas.scale(pageScale,pageScale)
   rect(0f,0f,w,h,Color.WHITE)
   if(continued)nextPageHeader() else firstPageHeader()
   // Evitar encabezados de productos vacíos en una página de solo totales.
   if(withTable)tableHeader()
  }
  fun finishPage(){
   if(modern){
    rect(margin,h-43f,w-margin,h-41.5f,inkColor)
    textCenter(config.legal+"  |  RUC "+config.ruc,
     w/2f,h-30f,8f,true,inkColor)
    // El visor del teléfono coloca su 1/1 en la esquina inferior derecha.
    // Evitar superponer una segunda cifra en esa misma zona.
    text("Página "+pageNumber,margin+3f,h-17f,7f,true,subdued)
   }else{
    // Pie comercial propio de FULLTRADE con líneas discontinuas y firmas.
    val dashColor=Color.rgb(85,88,93)
    for(k in 0..((cw/7f).toInt())){
     val xx=margin+k*7f
     rect(xx,h-127f,xx+3f,h-126.3f,dashColor)
     rect(xx,h-80f,xx+3f,h-79.3f,dashColor)
    }
    text("Asesor de ventas:",margin+7f,h-113f,7.7f,true,inkColor)
    clipped(pdfSeller,margin+112f,h-113f,cw*0.33f,7.7f)
    text("Correo:",w/2f+14f,h-113f,7.7f,true,inkColor)
    clipped(email.ifBlank{config.commercial},w/2f+62f,h-113f,cw*0.30f,7.2f)
    text("WhatsApp:",margin+7f,h-94f,7.7f,true)
    clipped(phone,margin+112f,h-94f,cw*0.36f,7.7f)
    textCenter(config.commercial.ifBlank{config.legal}+"  •  GESTIÓN COMERCIAL",
     w/2f,h-59f,if(compact)6.8f else 10f,true,navy)
    textCenter(config.header,w/2f,h-43f,
     if(compact)5f else 7.2f,false,subdued)
    textCenter(config.legal+"  |  RUC "+config.ruc,
     w/2f,h-26f,7.9f,true,inkColor)
   }
   textCenter("Proforma comercial · No es comprobante de pago electrónico",
    w/2f,h-12f,6.5f,false,subdued)
   if(!modern){
    // Numeración propia en el margen inferior izquierdo; separada del
    // contador superpuesto por el lector PDF de Android.
    text("Página "+pageNumber,margin+3f,h-17f,7f,true,subdued)
   }
   pdf.finishPage(page)
  }
  fun ensureSpace(required:Float,withTableOnNextPage:Boolean=true){
   val protectedFooter=if(modern)60f else 140f
   if(y+required>h-protectedFooter){
    finishPage()
    beginPage(true,withTableOnNextPage)
   }
  }
  fun money(v:Double)=currencyMark+" "+pdfNumber(v,config.decimals)
  try{
   beginPage(false)
   for((index,item) in items.withIndex()){
    val nameCol=if(modern)4 else 2
    val descX=xs[nameCol]+4f
    val descWidth=xs[nameCol+1]-xs[nameCol]-9f
    val detailLines=pdfTextLines(item.name,descWidth,tableFont,10)
    val brandLines=pdfTextLines(listOf(item.brand,item.unit).filter{it.isNotBlank()}
     .joinToString(" · "),descWidth,tableFont-0.7f,3)
    val showPicture=item.includePhoto&&!item.photoUri.isNullOrBlank()
    // La foto pasa de 43x33 pt a hasta 110x78 pt (con proporción preservada).
    // Reservamos la altura real para que no choque con la fila siguiente.
    val imageWidth=minOf((descWidth-4f).coerceAtLeast(28f),
     if(compact)88f else 110f)
    val imageHeight=if(compact)64f else 78f
    val photoHeight=if(showPicture)imageHeight+8f else 0f
    val discountHeight=if(item.discountPct>0.0||item.discountAmount>0.0)10f else 0f
    val rowHeight=maxOf(38f,18f+detailLines.size*10f+
     brandLines.size*9f+photoHeight+discountHeight)
    ensureSpace(rowHeight+5f)
    val top=y
    if(index%2==1)rect(margin,top,w-margin,top+rowHeight,light)
    val base=top+13f
    if(modern){
     text((index+1).toString(),xs[0]+3f,base,tableFont)
     clipped(item.code,xs[1]+2f,base,xs[2]-xs[1]-4f,tableFont-0.2f)
     text(item.qty.toString(),xs[2]+3f,base,tableFont)
     clipped(item.unit,xs[3]+2f,base,xs[4]-xs[3]-4f,tableFont-0.8f)
     textRight(money(item.price),xs[6]-3f,base,tableFont-0.5f)
     textRight(money(item.total),xs[7]-3f,base,tableFont-0.4f,true)
     clipped("48 H",xs[7]+3f,base,xs[8]-xs[7]-6f,tableFont-0.5f)
    }else{
     text(item.qty.toString(),xs[0]+3f,base,tableFont)
     clipped(item.unit,xs[1]+3f,base,xs[2]-xs[1]-5f,tableFont-0.3f)
     textRight(money(item.price),xs[4]-3f,base,tableFont-0.8f)
     textRight(pdfNumber(item.discountPct,0)+"%",xs[5]-3f,base,tableFont-0.7f)
     textRight("0%",xs[6]-3f,base,tableFont-0.7f)
     val netUnit=(item.price*(1-item.discountPct/100.0)-
      item.discountAmount/item.qty.coerceAtLeast(1)).coerceAtLeast(0.0)
     textRight(money(netUnit),xs[7]-3f,base,tableFont-0.8f)
     textRight(money(item.total),xs[8]-3f,base,tableFont-0.7f,true)
    }
    var descY=base
    for(line in detailLines){
     clipped(line,descX,descY,descWidth,tableFont,true)
     descY+=10f
    }
    for(line in brandLines){
     clipped(line,descX,descY,descWidth,tableFont-0.7f,false,subdued)
     descY+=9f
    }
    if(item.discountPct>0.0||item.discountAmount>0.0){
     val discountCaption="Dcto.: "+pdfNumber(item.discountPct,1)+"%"+
      if(item.discountAmount>0.0)" + "+money(item.discountAmount) else ""
     clipped(discountCaption,descX,descY,descWidth,
      tableFont-0.8f,false,subdued)
     descY+=10f
    }
    if(showPicture){
     drawPhoto(item.photoUri,RectF(descX,descY+2f,
      descX+imageWidth,descY+2f+imageHeight))
    }
    y=top+rowHeight
    horizontal(y)
    y+=4f
   }
   val taxableAmount=items.sumOf{if(it.taxable)it.total/1.18 else 0.0}
   val igvAmount=items.sumOf{if(it.taxable)it.total-it.total/1.18 else 0.0}
   val nonTaxableAmount=items.sumOf{if(!it.taxable)it.total else 0.0}
   val subtotal=taxableAmount+nonTaxableAmount
   if(modern){
    // Como en IFITSA: gran espacio libre, condiciones abajo a la izquierda
    // e importes apilados en el sector inferior derecho.
    if(!compact&&pageNumber==1&&y<h-263f)y=h-263f
    ensureSpace(174f,false)
    val baseY=y
    val boxX=w-margin-191f
    text("Glosa:",margin+4f,baseY+8f,8f,true,inkColor)
    text("CONDICIONES COMERCIALES",margin+4f,baseY+29f,
     if(compact)8.3f else 10f,true,inkColor)
    val conditions=listOf(
     "Moneda: "+if(quoteCurrency=="USD")"Dólares (US$)" else "Soles (S/)",
     "Impuestos: IGV "+if(igvAmount>0.005)"18 %" else "No aplicado",
     "Forma de pago: "+pdfPayment,
     "Validez: "+pdfExpiry.ifBlank{"No especificada"},
     "Entrega: A COORDINAR / 48 HORAS"
    )
    for((i,value) in conditions.withIndex())
     clipped(value,margin+7f,baseY+46f+i*15f,boxX-margin-14f,7.9f)
    val totals=listOf(
     Pair("SUBTOTAL",money(subtotal)),
     Pair("IGV 18 %",money(igvAmount)),
     Pair("TOTAL",money(quoteTotal))
    )
    for((i,pair) in totals.withIndex()){
     val top=baseY+15f+i*28f
     paint.style=Paint.Style.FILL
     paint.color=if(i==2)Color.rgb(250,168,29) else Color.rgb(245,247,250)
     canvas.drawRoundRect(RectF(boxX,top,boxX+75f,top+24f),3f,3f,paint)
     paint.color=Color.rgb(245,247,250)
     canvas.drawRoundRect(RectF(boxX+79f,top,w-margin,top+24f),3f,3f,paint)
     text(pair.first,boxX+5f,top+16f,8f,i==2)
     textRight(pair.second,w-margin-6f,top+16f,8.4f,true)
    }
    // Firma en marco visible; no se mezcla con el resumen comercial.
    val signTop=baseY+106f
    paint.style=Paint.Style.STROKE;paint.color=inkColor;paint.strokeWidth=0.8f
    canvas.drawRect(RectF(boxX,signTop,w-margin,signTop+58f),paint)
    paint.style=Paint.Style.FILL
    text("Atentamente,",boxX+8f,signTop+15f,8.2f,true)
    textRight(config.legal,w-margin-8f,signTop+33f,7.7f,true)
    textRight(pdfSeller,w-margin-8f,signTop+48f,7.5f)
    if(config.footer.isNotBlank()){
     for((j,v) in pdfTextLines(config.footer,boxX-margin-14f,7.3f,3)
       .withIndex())
      text(v,margin+6f,baseY+137f+j*10f,7.3f,true)
    }
    y=baseY+174f
    if(config.banks.isNotBlank()){
     ensureSpace(30f,false)
     text("CUENTAS BANCARIAS",margin,y,8.5f,true,teal);y+=15f
     for(value in pdfTextLines(config.banks,cw-10f,7.8f,150)){
      ensureSpace(13f,false);text(value,margin+5f,y,7.8f);y+=12f
     }
    }
   }else{
    // Como en FULLTRADE: resumen verde horizontal y separadores punteados.
    // El bloque comercial ocupa ~140 pt; reservar 190 provocaba saltos
    // innecesarios a la página 2 incluso cuando había una sola fotografía.
    ensureSpace(154f,false)
    y+=8f
    val greenBand=Color.rgb(83,232,51)
    paint.style=Paint.Style.FILL;paint.color=greenBand
    canvas.drawRoundRect(RectF(margin,y,w-margin,y+25f),8f,8f,paint)
    paint.style=Paint.Style.STROKE;paint.color=inkColor;paint.strokeWidth=.7f
    canvas.drawRoundRect(RectF(margin,y,w-margin,y+25f),8f,8f,paint)
    paint.style=Paint.Style.FILL
    val totalHeaders=listOf("TIPO DE MONEDA","TOTAL NETO","TOTAL IGV 18 %","TOTAL DOC.")
    val tx=listOf(margin+8f,margin+cw*.48f,margin+cw*.70f,w-margin-6f)
    for((i,v) in totalHeaders.withIndex()){
     if(i==3)textRight(v,tx[i],y+16f,if(compact)6.1f else 7.6f,true)
     else text(v,tx[i],y+16f,if(compact)6.1f else 7.6f,true)
    }
    y+=39f
    text(if(quoteCurrency=="USD")"Dólares" else "Soles",margin+8f,y,8.5f)
    textRight(money(subtotal),margin+cw*.58f,y,8.2f)
    textRight(money(igvAmount),margin+cw*.80f,y,8.2f)
    textRight(money(quoteTotal),w-margin-6f,y,8.7f,true)
    y+=13f
    for(k in 0..((cw/7f).toInt())){
     val xx=margin+k*7f
     rect(xx,y,xx+3f,y+0.6f,subdued)
    }
    y+=22f
    text("Condición de pago:",margin+8f,y,8.5f)
    text(pdfPayment,margin+127f,y,8.3f,true)
    textRight("Entrega: 48 horas (salvo venta previa)",w-margin,y,7.7f)
    y+=19f
    text("Validez:",margin+8f,y,8.3f)
    text(pdfExpiry.ifBlank{"No especificada"},margin+127f,y,8.3f)
    y+=20f
    if(notes.isNotBlank()){
     text("Observaciones:",margin+8f,y,8f,true);y+=14f
     for(value in pdfTextLines(notes,cw-14f,7.5f,80)){
      ensureSpace(12f,false);text(value,margin+8f,y,7.5f);y+=12f
     }
    }
    if(config.footer.isNotBlank()){
     y+=6f
     for(value in pdfTextLines(config.footer,cw-15f,7.5f,50)){
      ensureSpace(12f,false);text(value,margin+8f,y,7.5f);y+=12f
     }
    }
    if(config.banks.isNotBlank()){
     ensureSpace(30f,false);y+=5f
     text("CUENTAS BANCARIAS",margin+8f,y,8.5f,true,navy);y+=15f
     for(value in pdfTextLines(config.banks,cw-16f,7.4f,150)){
      ensureSpace(13f,false);text(value,margin+8f,y,7.4f);y+=12f
     }
    }
   }
   finishPage()
   pdf.writeTo(out)
  }finally{pdf.close()}
 }

 private fun writePdf(id:Long,out:java.io.OutputStream){
  if(pdfStyle=="Moderno" || pdfStyle=="Corporativo"){
   writePdfStyled(id,out,pdfStyle=="Moderno")
   return
  }
  val count=db.readableDatabase.rawQuery("SELECT COUNT(*) FROM quote_items WHERE quote_id=?",
   arrayOf(id.toString()))
  val multi=try{count.moveToFirst()&&count.getInt(0)>1}finally{count.close()}
  if(multi){writePdfMultiple(id,out);return}

  if(pdfStyle=="Proforma BELSIPERU"){writePdfProforma(id,out);return}
   val company=companyInfo()
  val c=db.readableDatabase.rawQuery("SELECT client,product,qty,unit_price,discount,igv,currency,total,created,product_photo,discount_amount FROM quotes WHERE id=?",arrayOf(id.toString()))
  if(!c.moveToFirst()){c.close();return}
  val size=when(pdfPaper){
   "A4 horizontal"->Pair(842,595)
   "A5 vertical"->Pair(420,595)
   "A5 horizontal"->Pair(595,420)
   "Ticket 80 mm"->Pair(227,650)
   "Carta vertical"->Pair(612,792)
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
  if(!compact)drawCompanyLogo(canvas,RectF(w-margin-82f,12f,w-margin,91f))
  p.color=Color.WHITE;p.textSize=if(compact)17f else 24f;p.isFakeBoldText=true
  canvas.drawText(company.legal.take(if(compact)18 else 35),margin,if(compact)33f else 48f,p)
  p.textSize=if(compact)9f else 12f;p.isFakeBoldText=false
  canvas.drawText("RUC "+company.ruc,margin,if(compact)52f else 73f,p)
  p.color=Color.rgb(213,165,74);p.textSize=if(compact)10f else 15f
  canvas.drawText("COT-${id.toString().padStart(6,'0')}",margin,if(compact)75f else 100f,p)
  // Marca de agua muy tenue.
  p.color=Color.argb(16,18,52,90);p.textSize=if(compact)29f else 65f;p.isFakeBoldText=true
  canvas.save();canvas.rotate(-25f,w/2,h/2)
  canvas.drawText(company.commercial.ifBlank{company.legal}.take(20),margin,h/2,p);canvas.restore()
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
  canvas.drawText("Precio unitario: ${c.getString(6)} ${pdfNumber(c.getDouble(3),company.decimals)}",margin+8f,productY+44f,p)
  canvas.drawText("Descuento: ${c.getDouble(4)} % + ${pdfNumber(c.getDouble(10),company.decimals)}",margin+8f,productY+65f,p)
  canvas.drawText("IGV: ${if(c.getInt(5)==1)"18 %" else "No aplicado"}",margin+8f,productY+86f,p)
  val totalY=minOf(h-82f,productY+145f)
  val productPicture=loadQuoteProductBitmap(id)
  if(productPicture!=null){
   val box=if(compact){
    // En ticket de los estilos anteriores, bajo el total, sin cubrir cifras.
    RectF(w-margin-78f,totalY+16f,w-margin,totalY+95f)
   }else{
    val side=if(w<500)85f else 120f
    RectF(w-margin-side,productY+8f,w-margin,
      minOf(totalY-10f,productY+8f+side))
   }
   if(box.width()>20f && box.height()>20f){
    drawProductImage(canvas,productPicture,box)
   }
   productPicture.recycle()
  }
  p.color=main;p.textSize=if(compact)14f else 20f;p.isFakeBoldText=true
  canvas.drawText("TOTAL ${c.getString(6)} ${pdfNumber(c.getDouble(7),company.decimals)}",margin,totalY,p)
  p.isFakeBoldText=false;p.textSize=if(compact)8f else 10f
  val genericBanks=pdfTextLines(company.banks,w-margin*2,8.2f,300)
  if(!compact && h>560f) {
   p.color=main;p.textSize=9f;p.isFakeBoldText=false
   canvas.drawText("CONDICIÓN DE PAGO: "+pdfPayment,margin,h-165f,p)
   genericBanks.take(3).forEachIndexed{i,value->
    p.textSize=8.2f;canvas.drawText(value,margin,h-147f+i*13f,p)
   }
   pdfTextLines(company.footer,w-margin*2,8f,2).forEachIndexed{i,value->
    p.textSize=8f;p.isFakeBoldText=false
    canvas.drawText(value,(w-p.measureText(value))/2f,h-88f+i*12f,p)
   }
  }else{
   pdfTextLines(company.footer,w-margin*2,if(compact)6.8f else 8f,2).forEachIndexed{i,value->
    p.color=main;p.textSize=if(compact)6.8f else 8f;p.isFakeBoldText=false
    canvas.drawText(value,(w-p.measureText(value))/2f,h-58f+i*10f,p)
   }
  }
p.color=main;p.textSize=if(compact)8f else 10f
  canvas.drawText("Proforma comercial - no es comprobante tributario.",margin,h-35f,p)
  pdf.finishPage(page)
  addBankContinuationPages(pdf,genericBanks.drop(if(!compact && h>560f)3 else 0),
   size.first,size.second)
  pdf.writeTo(out);pdf.close();c.close()
 }
}
