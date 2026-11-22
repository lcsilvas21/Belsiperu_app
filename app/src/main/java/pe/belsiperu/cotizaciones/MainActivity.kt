
package pe.belsiperu.cotizaciones

import android.app.*
import android.os.*
import android.content.*
import android.database.sqlite.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.view.Gravity
import android.net.Uri
import android.text.InputType
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

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
 private val navy=Color.rgb(13,43,91)
 private val green=Color.rgb(14,122,77)
 private val gold=Color.rgb(212,175,55)
 private val pale=Color.rgb(246,249,247)
 private val ink=Color.rgb(27,46,60)
 private val muted=Color.rgb(103,119,130)
 private val border=Color.rgb(225,233,235)
 private var pendingPdf:Long?=null
 private var pendingPhotoProduct:Long?=null
 private var pdfPaper="Carta vertical"
 private var pdfStyle="Proforma BELSIPERU"
 private var pdfExpiry=""
 private var pdfSeller="BELSIPERU"
 private var pdfPayment="CONTADO"
 private fun today()=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(Date())

 // Carga la foto del catálogo más reciente; para cotizaciones antiguas usa
 // la foto guardada con la propia cotización como respaldo.
 private fun loadQuoteProductBitmap(quoteId:Long):Bitmap?{
  val candidates=mutableListOf<String>()
  var productName=""
  val snapshot=db.readableDatabase.rawQuery(
   "SELECT product,product_photo FROM quotes WHERE id=?",arrayOf(quoteId.toString()))
  try{
   if(snapshot.moveToFirst()){
    productName=snapshot.getString(0)?:""
    val previous=snapshot.getString(1)
    if(!previous.isNullOrBlank())candidates.add(previous)
   }
  }finally{snapshot.close()}
  if(productName.isNotBlank()){
   val catalog=db.readableDatabase.rawQuery(
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
 private fun logo()=ImageView(this).apply{
  try{assets.open("belsiperu_logo.png").use{setImageBitmap(BitmapFactory.decodeStream(it))}}catch(_:Exception){}
  scaleType=ImageView.ScaleType.FIT_CENTER
  background=round(Color.WHITE,11)
  setPadding(d(6),d(5),d(6),d(5))
  contentDescription="BELSIPERU"
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

 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);window.statusBarColor=navy;db=Store(this); lock() }

 private fun screen(title:String) {
  val login=title=="Acceso seguro"
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
  top.addView(logo(),LinearLayout.LayoutParams(d(102),d(50)))
  val names=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(13),0,0,0)}
  names.addView(line(if(login)"Acceso seguro" else title,19f,Color.WHITE,true))
  names.addView(line("FERRETERÍA  •  COTIZACIONES  •  INVENTARIO",9f,Color.rgb(207,230,232)))
  top.addView(names);outer.addView(top)
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
  val hero=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(d(19),d(20),d(19),d(20))
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(navy,Color.rgb(8,87,93))).apply{cornerRadius=d(19).toFloat()}
  }
  hero.addView(line("¡Hola! Bienvenido a BELSIPERU",21f,Color.WHITE,true))
  hero.addView(line("Tu negocio organizado, desde un solo lugar.",13f,Color.WHITE))
  body.addView(hero)
  button("＋  NUEVA COTIZACIÓN"){newQuote()}
  heading("Accesos rápidos","Todo lo que necesitas para gestionar tu ferretería")
  tileRow(tile("Cotizaciones","Historial y PDF",android.R.drawable.ic_menu_edit){quotes()},tile("Productos","Catálogo y fotos",android.R.drawable.ic_menu_agenda){products()})
  tileRow(tile("Inventario","Stock y Kardex",android.R.drawable.ic_menu_sort_by_size){inventory()},tile("Clientes","Datos y contactos",android.R.drawable.ic_menu_myplaces){clients()})
  tileRow(tile("Compras","Ingresos de stock",android.R.drawable.ic_input_add){purchases()},tile("Reportes","Resumen de gestión",android.R.drawable.ic_menu_info_details){reports()})
  heading("Resumen del negocio")
  val q=queryCount("SELECT COUNT(*) FROM quotes")
  val p=queryCount("SELECT COUNT(*) FROM products")
  val low=queryCount("SELECT COUNT(*) FROM products WHERE stock<=min_stock")
  tileRow(tile(q.toString()+" cotizaciones","Historial guardado",android.R.drawable.ic_menu_edit){quotes()},tile(p.toString()+" productos","En catálogo",android.R.drawable.ic_menu_agenda){products()})
  if(low>0)info("Atención: "+low+" producto(s) con stock bajo","Revisa Inventario para reponer existencias.",Color.rgb(169,105,19))
  button("⚙  Configuración"){settings()}
  body.addView(line("BELSIPERU V2 • Prototipo de uso local",11f,muted).apply{gravity=Gravity.CENTER})
 }
 private fun purchases(){
  screen("Compras")
  heading("Ingreso de mercadería","Registro básico de stock y Kardex")
  val ids=mutableListOf<Long>();val names=mutableListOf<String>()
  val cur=db.readableDatabase.rawQuery("SELECT id,name FROM products ORDER BY name",null)
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
  val units=queryCount("SELECT COALESCE(SUM(stock),0) FROM products")
  val low=queryCount("SELECT COUNT(*) FROM products WHERE stock<=min_stock")
  info("Cotizaciones",q.toString()+" registros")
  info("Ventas confirmadas",sold.toString()+" operaciones")
  info("Unidades en stock",units.toString()+" unidades")
  info("Productos con stock bajo",low.toString()+" alertas",if(low>0)Color.rgb(169,105,19) else green)
  info("Pendiente","Rentabilidad y exportación a Excel se añadirán después.")
 }
 private fun settings(){
  screen("Configuración")
  heading("BELSIPERU V2","Diseño marino, verde y dorado")
  info("Protección del PIN","El PIN actual es de prototipo: no guardes información sensible ni real hasta reforzar la seguridad.",Color.rgb(169,105,19))
  button("Configurar consulta de RUC"){rucSettings()}
  button("Bloquear aplicación"){lock()}
  button("Volver al inicio"){home()}
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
  screen("Clientes")
  heading("Nuevo cliente","Consulta el RUC para completar automáticamente la razón social y la dirección.")
  label("Documento (RUC o DNI)")
  val doc=input("RUC de 11 dígitos / DNI de 8 dígitos")
  doc.inputType=InputType.TYPE_CLASS_NUMBER
  doc.filters=arrayOf(android.text.InputFilter.LengthFilter(11))
  val status=line("La consulta RUC requiere configurar la conexión una sola vez.",12f,muted)
  body.addView(status)
  val name=input("Nombre o razón social")
  val addr=input("Dirección fiscal / dirección del cliente")
  button("Consultar RUC"){
   val ruc=doc.text.toString().trim()
   if(!ruc.matches(Regex("[0-9]{11}"))){
    status.text="Ingresa un RUC de 11 dígitos."
    return@button
   }
   status.text="Consultando RUC…"
   consultaRuc(ruc){data,error->
    if(data!=null){
     if(doc.text.toString().trim()!=ruc){
      status.text="El documento cambió durante la consulta. Vuelve a consultar."
     }else{
      name.setText(data.razon)
      addr.setText(data.direccion)
      status.text="SUNAT: ${data.estado.ifBlank{"Sin estado"}} · ${data.condicion.ifBlank{"Sin condición"}}. Verifica y guarda los datos."
      if(data.estado.uppercase(Locale.ROOT)!="ACTIVO"){
       AlertDialog.Builder(this).setTitle("Estado del contribuyente")
        .setMessage("El RUC figura como ${data.estado}. Comprueba su situación antes de utilizarlo.")
        .setPositiveButton("Entendido",null).show()
      }
     }
    }else{
     status.text=error?:"No se pudo consultar el RUC."
     if(error?.contains("Configura primero")==true){
      AlertDialog.Builder(this).setTitle("Activar consulta RUC")
       .setMessage("Para consultar RUC debes configurar el servicio seguro. Puedes guardar clientes manualmente.")
       .setNegativeButton("Después",null)
       .setPositiveButton("Configurar"){_,_->rucSettings()}.show()
     }
    }
   }
  }
  button("Guardar cliente"){
   val nombre=name.text.toString().trim()
   val documento=doc.text.toString().trim()
   val direccion=addr.text.toString().trim()
   if(nombre.isEmpty()){toast("Indica el nombre o razón social");return@button}
   if(documento.isNotEmpty() &&
     !(documento.matches(Regex("[0-9]{8}")) || documento.matches(Regex("[0-9]{11}")))){
    toast("El DNI requiere 8 dígitos y el RUC 11.");return@button
   }
   val values=android.content.ContentValues().apply{
    put("name",nombre);put("document",documento);put("address",direccion)
   }
   val sql=db.writableDatabase
   val updated=if(documento.isNotEmpty())sql.update("clients",values,"document=?",arrayOf(documento)) else 0
   if(updated==0)sql.insert("clients",null,values)
   toast(if(updated>0)"Cliente actualizado" else "Cliente guardado")
   clients()
  }
  button("Configurar consulta de RUC"){rucSettings()}
  heading("Clientes registrados")
  val c=db.readableDatabase.rawQuery("SELECT name,document,address FROM clients ORDER BY id DESC",null)
  while(c.moveToNext()){
   info(c.getString(0)?:"Sin nombre", "Documento: ${c.getString(1)?:""}\n${c.getString(2)?:""}")
  }
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
  info("Modelo de proforma BELSIPERU","Se conserva el diseño aprobado. Si el producto tiene una fotografía accesible, aparecerá en el PDF.")
  label("Diseño")
  val style=choice(listOf("Proforma BELSIPERU","Moderno","Corporativo","Prémium"))
  label("Tamaño de papel")
  val paper=choice(listOf("Carta vertical","A4 vertical","A4 horizontal","A5 vertical","A5 horizontal","Ticket 80 mm"))
  label("Vencimiento de la proforma (dd/MM/aaaa, opcional)")
  val expiry=input("Ejemplo: 30/11/2026")
  expiry.setText(pdfExpiry)
  label("Responsable")
  val seller=input("Usuario o vendedor")
  seller.setText(pdfSeller)
  label("Condición de pago")
  val payment=input("CONTADO / CRÉDITO")
  payment.setText(pdfPayment)
  button("Generar PDF"){
   pdfStyle=style.selectedItem.toString()
   pdfPaper=paper.selectedItem.toString()
   pdfExpiry=expiry.text.toString().trim()
   pdfSeller=seller.text.toString().trim().ifEmpty{"BELSIPERU"}
   pdfPayment=payment.text.toString().trim().ifEmpty{"CONTADO"}
   exportPdf(id)
  }
 }
 private fun exportPdf(id:Long){
  pendingPdf=id
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf";putExtra(Intent.EXTRA_TITLE,if(pdfStyle=="Proforma BELSIPERU")"BELSIPERU_PROFORMA_0002-${id.toString().padStart(7,'0')}_${when(pdfPaper){"A4 vertical"->"A4V";"A4 horizontal"->"A4H";"A5 vertical"->"A5V";"A5 horizontal"->"A5H";"Ticket 80 mm"->"TICKET80";else->"CARTA"}}.pdf" else "BELSIPERU_COT-${id.toString().padStart(6,'0')}.pdf")}
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

 // Diseño de proforma comercial basado en el PDF de referencia del usuario.
 // Papel carta: 612x792 puntos (el documento proporcionado tiene este tamaño).

 // A4/A5 apaisados tienen una composición apaisada real;
 // el ticket de 80 mm usa columnas estrechas y una página larga de recibo.
 private fun writePdfProformaAdaptada(id:Long,out:java.io.OutputStream){
  val cur=db.readableDatabase.rawQuery(
    "SELECT client,product,qty,unit_price,discount,igv,currency,total,created,discount_amount FROM quotes WHERE id=?",
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
   val taxable=if(igv)total/1.18 else total
   val tax=(total-taxable).coerceAtLeast(0.0)
   val discount=(qty*price*discountPct/100.0+discountAmount).coerceAtLeast(0.0)
   val productPicture=loadQuoteProductBitmap(id)
   var code="";var document="";var address=""
   val prod=db.readableDatabase.rawQuery("SELECT code FROM products WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(item))
   if(prod.moveToFirst())code=prod.getString(0)?:""
   prod.close()
   val cli=db.readableDatabase.rawQuery("SELECT document,address FROM clients WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(client))
   if(cli.moveToFirst()){document=cli.getString(0)?:"";address=cli.getString(1)?:""}
   cli.close()
   val timestamp=try{SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).parse(rawDate)}catch(_:Exception){null}
   val date=if(timestamp!=null)SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).format(timestamp) else rawDate.take(10)
   val time=if(timestamp!=null)SimpleDateFormat("hh:mm a",Locale.US).format(timestamp) else ""
   val money=if(currency=="USD")"US$" else "S/"
   val currName=if(currency=="USD")"DÓLARES" else "SOLES"
   val expiry=pdfExpiry.ifBlank{"POR DEFINIR"}
   val docType=when(document.length){11->"RUC ";8->"DNI ";else->""}
   fun fmt(v:Double)=String.format(Locale.US,"%,.2f",v)
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
    fun logo(box:RectF){
     try{
      assets.open("belsiperu_logo.png").use{ins->
       val bitmap=BitmapFactory.decodeStream(ins)
       if(bitmap!=null){canvas.drawBitmap(bitmap,null,box,Paint(Paint.ANTI_ALIAS_FLAG));bitmap.recycle()}
      }
     }catch(_:Exception){}
    }
    val num="0002-"+id.toString().padStart(7,'0')
    if(ticket){
     // Recibo legible a 80 mm, de longitud suficiente para las cuentas bancarias.
     logo(RectF(61f,8f,166f,62f))
     center("BELSIPERU S.A.C",8f,219f,72f,12f,true)
     center("RUC 20605060634",8f,219f,88f,10f)
     rect(9f,96f,218f,121f,Color.rgb(235,235,235))
     center("PROFORMA",9f,218f,113f,15f,true)
     center(num,9f,218f,138f,10.5f)
     center("JR. LOS TOPACIOS NRO. 2125 INT. T18",9f,218f,153f,7.4f)
     center("SAN JUAN DE LURIGANCHO - LIMA",9f,218f,165f,7.5f)
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
     text(if(igv)"GRAVADA" else "INAFECTO",9f,484f,9f,true);right(money+" "+fmt(taxable),218f,484f,9f)
     text("IGV 18%",9f,499f,9f,true);right(money+" "+fmt(tax),218f,499f,9f)
     text("TOTAL",9f,518f,11f,true);right(money+" "+fmt(total),218f,518f,11f,true)
     wrapped(literal,205f,7.6f,3).forEachIndexed{i,t->center(t,9f,218f,540f+i*10f,7.6f)}
     stroke(9f,572f,218f,572f)
     fit("USUARIO: "+pdfSeller,9f,586f,207f,8f)
     fit("PAGO: "+pdfPayment,9f,601f,207f,8f)
     text("BELSIPERU S.A.C - INTERBANK",9f,619f,8f,true)
     text("Cta. soles: 20030088110506",9f,633f,8f)
     text("CCI soles: 00320000300881050636",9f,646f,8f)
     text("Cta. dólares: 2003008810513",9f,667f,8f)
     text("CCI dólares: 00320000300881051332",9f,680f,8f)
     stroke(9f,693f,218f,693f)
     wrapped("Los productos en stock se entregan en 48 horas, salvo venta previa.",207f,8.2f,3)
      .forEachIndexed{i,t->center(t,9f,218f,714f+i*12f,8.2f)}
     center("BELSIPERU",9f,218f,776f,10f,true)
     center("Proforma comercial. No es comprobante tributario.",9f,218f,793f,6.5f)
     canvas.restore()
    } else {
     // Composición apaisada para A4 y A5: la tabla ocupa el ancho útil de la hoja.
     val left=20f;val rightEdge=822f
     logo(RectF(22f,18f,132f,96f))
     text("BELSIPERU S.A.C",144f,31f,16f,true)
     fit("JR. LOS TOPACIOS NRO. 2125 INT. T18",144f,48f,450f,10f)
     fit("COO. LA HUAYRONA - LIMA - SAN JUAN DE LURIGANCHO",144f,62f,455f,9f)
     text("LO MEJOR EN MÁQUINAS Y HERRAMIENTAS INDUSTRIALES PARA EL PERÚ",144f,86f,10f)
     stroke(621f,17f,821f,17f);stroke(621f,105f,821f,105f)
     stroke(621f,17f,621f,105f);stroke(821f,17f,821f,105f)
     center("RUC 20605060634",621f,821f,37f,13f)
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
     text(if(igv)"GRAVADA" else "INAFECTO",610f,397f,10f,true)
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
     text("BELSIPERU S.A.C",151f,490f,8.8f)
     text("Cuenta soles Interbank: 20030088110506",151f,508f,8.6f)
     text("CCI soles: 00320000300881050636",151f,520f,8.6f)
     text("Cuenta dólares Interbank: 2003008810513",498f,508f,8.6f)
     text("CCI dólares: 00320000300881051332",498f,520f,8.6f)
     center("Los productos en stock se entregan en 48 horas, salvo venta previa.",left,rightEdge,551f,9f)
     center("BELSIPERU",left,rightEdge,578f,11f,true)
     center("Proforma comercial emitida por BELSIPERU. No es comprobante de pago electrónico.",left,rightEdge,591f,7.2f)
    }
    canvas.restore()
    productPicture?.recycle()
    pdf.finishPage(page)
    pdf.writeTo(out)
   }finally{pdf.close()}
  }finally{cur.close()}
 }

 private fun writePdfProforma(id:Long,out:java.io.OutputStream){
  if(pdfPaper=="A4 horizontal" || pdfPaper=="A5 horizontal" || pdfPaper=="Ticket 80 mm") {
   writePdfProformaAdaptada(id,out)
   return
  }
  val c=db.readableDatabase.rawQuery(
   "SELECT client,product,qty,unit_price,discount,igv,currency,total,created,product_photo,discount_amount FROM quotes WHERE id=?",
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
   val net=if(applyTax)total/1.18 else total
   val tax=(total-net).coerceAtLeast(0.0)
   val discount=(qty*unitPrice*pct/100.0+fixed).coerceAtLeast(0.0)
   val productPicture=loadQuoteProductBitmap(id)
   var code="";var document="";var address=""
   val prod=db.readableDatabase.rawQuery(
    "SELECT code FROM products WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(product))
   if(prod.moveToFirst())code=prod.getString(0)?:""
   prod.close()
   val cc=db.readableDatabase.rawQuery(
    "SELECT document,address FROM clients WHERE name=? ORDER BY id DESC LIMIT 1",arrayOf(client))
   if(cc.moveToFirst()){document=cc.getString(0)?:"";address=cc.getString(1)?:""}
   cc.close()
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
   fun amount(n:Double)=String.format(Locale.US,"%,.2f",n)
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
   try{
    assets.open("belsiperu_logo.png").use{stream->
     val bitmap=BitmapFactory.decodeStream(stream)
     if(bitmap!=null){
      canvas.drawBitmap(bitmap,null,RectF(15.05f,15.05f,109.11f,109.11f),Paint(Paint.ANTI_ALIAS_FLAG))
      bitmap.recycle()
     }
    }
   }catch(_:Exception){}
   text("BELSIPERU S.A.C",116.6f,33.5f,13.2f,true)
   fit("PRINCIPAL » JR. LOS TOPACIOS NRO. 2125 INT. T18",116.6f,51f,226f,8.47f)
   fit("COO. LA HUAYRONA - LIMA LIMA SAN JUAN DE",116.6f,62.2f,226f,8.47f)
   text("LURIGANCHO",116.6f,73.5f,8.47f)
   text("LO MEJOR EN MAQUINAS Y HERRAMIENTAS",116.6f,93f,8.47f)
   text("INDUSTRIALES PARA EL PERÚ",116.6f,104f,8.47f)
   rect(348.03f,44.21f,543.68f,74.31f,Color.rgb(238,238,238))
   line(347.09f,15.52f,544.62f,15.52f,0.94f);line(347.09f,103.0f,544.62f,103.0f,0.94f)
   line(347.56f,15.05f,347.56f,103.47f,0.94f);line(544.15f,15.05f,544.15f,103.47f,0.94f)
   center("RUC 20605060634",347.09f,544.62f,35.7f,13.17f)
   center("PROFORMA",347.09f,544.62f,65.0f,15.05f,true)
   center("0002-"+id.toString().padStart(7,'0'),347.09f,544.62f,94f,13.17f)
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
   right(if(applyTax)"GRAVADA" else "INAFECTO",418f,506f,9.4f,true)
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
   text("BELSIPERU S.A.C",156.1f,588.4f,8.47f)
   text("Cuenta Corriente Soles Interbank: 20030088110506",156.1f,613f,8.47f)
   text("Interbancario Soles Interbank: 2003008810506",156.1f,624.3f,8.47f)
   text("00320000300881050636",156.1f,635.6f,8.47f)
   text("Cuenta Corriente Dólares Interbank: 2003008810513",156.1f,658.2f,8.47f)
   text("Interbancario Dólares Interbank: 2003008810513",156.1f,669.5f,8.47f)
   text("00320000300881051332",156.1f,680.8f,8.47f)
   center("Los productos en Stock, la entrega será realizada dentro de las 48 Horas, salvo venta Previa.",15.05f,544.62f,710f,9.4f)
   center("BELSIPERU",15.05f,544.62f,753f,11.3f,true)
   center("Proforma comercial emitida mediante BELSIPERU. No constituye comprobante de pago electrónico.",15.05f,544.62f,766f,7.5f)
   canvas.restore()
   productPicture?.recycle()
   pdf.finishPage(page)
   try{pdf.writeTo(out)}finally{pdf.close()}
  }finally{c.close()}
 }

 private fun writePdf(id:Long,out:java.io.OutputStream){
  if(pdfStyle=="Proforma BELSIPERU"){writePdfProforma(id,out);return}
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
