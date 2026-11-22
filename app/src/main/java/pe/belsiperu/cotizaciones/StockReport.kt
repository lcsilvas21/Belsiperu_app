package pe.belsiperu.cotizaciones

import android.database.sqlite.SQLiteDatabase
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Reporte de existencias y movimientos realmente registrados en SQLite.
 * No reconstruye saldos históricos que la base de datos no ha guardado.
 */
object StockReport {
 data class Product(
  val code:String,val name:String,val category:String,val brand:String,
  val unit:String,val stock:Int,val minimum:Int,val currency:String,
  val salePrice:Double,val purchasePrice:Double
 ){
  val shortage:Int get()=(minimum-stock).coerceAtLeast(0)
  val status:String get()=when{
   stock<=0->"SIN STOCK"
   minimum>0&&stock<=minimum->"STOCK BAJO"
   else->"NORMAL"
  }
 }
 data class Movement(
  val date:String,val product:String,val qty:Int,val reason:String
 ){
  val type:String get()=when{
   qty>0->"INGRESO"
   qty<0->"SALIDA"
   else->"AJUSTE"
  }
 }
 data class Snapshot(val products:List<Product>,val movements:List<Movement>){
  val low:Int get()=products.count{it.minimum>0 && it.stock<=it.minimum}
  val out:Int get()=products.count{it.stock<=0}
  val units:Long get()=products.sumOf{it.stock.toLong()}
 }
 fun load(db:SQLiteDatabase):Snapshot{
  val products=mutableListOf<Product>()
  val p=db.rawQuery(
   "SELECT code,name,category,brand,unit,stock,min_stock,currency,price,"+
    "purchase_price FROM products WHERE kind='PRODUCTO' ORDER BY name COLLATE NOCASE",null)
  try{while(p.moveToNext()){
   products.add(Product(
    p.getString(0)?:"",p.getString(1)?:"",p.getString(2)?:"",
    p.getString(3)?:"",p.getString(4)?:"UNIDADES",
    p.getInt(5),p.getInt(6),p.getString(7)?:"PEN",
    p.getDouble(8),p.getDouble(9)))
  }}finally{p.close()}
  val movements=mutableListOf<Movement>()
  val m=db.rawQuery(
   "SELECT created,product,qty,reason FROM movements ORDER BY id DESC",null)
  try{while(m.moveToNext()){
   movements.add(Movement(
    m.getString(0)?:"",m.getString(1)?:"",
    m.getInt(2),m.getString(3)?:""))
  }}finally{m.close()}
  return Snapshot(products,movements)
 }
 private fun escape(v:String):String=buildString{
  for(c in v)append(when(c){
   '&'->"&amp;";'<'->"&lt;";'>'->"&gt;";'"'->"&quot;";'\''->"&apos;"
   else->if(c.code<32&&c!='\n'&&c!='\t') " " else c.toString()
  })
 }
 private fun col(n:Int):String{
  var a=n+1;var s=""
  while(a>0){a--;s=('A'+a%26)+s;a/=26}
  return s
 }
 private fun sheet(rows:List<List<String>>,numeric:Set<Int>):String{
  val result=StringBuilder(4096+rows.size*160)
  result.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
  result.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
  result.append("<sheetViews><sheetView workbookViewId=\"0\">")
  result.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
  result.append("</sheetView></sheetViews><cols>")
  val count=rows.maxOfOrNull{it.size}?:0
  for(c in 0 until count){
   val width=when(c){
    1->38;2,3,4->22
    else->if(c in numeric)16 else 19
   }
   result.append("<col min=\"").append(c+1).append("\" max=\"").append(c+1)
    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>")
  }
  result.append("</cols><sheetData>")
  rows.forEachIndexed{ri,row->
   result.append("<row r=\"").append(ri+1).append("\">")
   row.forEachIndexed{ci,value->
    if(value.isNotEmpty()){
     val cell=col(ci)+(ri+1)
     val number=if(ri>0&&ci in numeric)
      value.toDoubleOrNull()?.takeIf{it.isFinite()} else null
     if(number!=null){
      result.append("<c r=\"").append(cell).append("\" s=\"2\"><v>")
       .append(number).append("</v></c>")
     }else{
      result.append("<c r=\"").append(cell).append("\" t=\"inlineStr\"")
      if(ri==0)result.append(" s=\"1\"")
      result.append("><is><t xml:space=\"preserve\">")
       .append(escape(value)).append("</t></is></c>")
     }
    }
   }
   result.append("</row>")
  }
  result.append("</sheetData></worksheet>")
  return result.toString()
 }
 private fun entry(zip:ZipOutputStream,name:String,contents:String){
  zip.putNextEntry(ZipEntry(name))
  zip.write(contents.toByteArray(Charsets.UTF_8))
  zip.closeEntry()
 }
 /** Exporta dos hojas separadas para no confundir stock actual con movimientos. */
 fun writeExcel(snapshot:Snapshot,out:OutputStream){
  val products=mutableListOf(
   listOf("Código","Producto","Categoría","Marca","Unidad","Stock actual",
    "Stock mínimo","Faltante","Estado","Moneda","Precio venta","Precio compra"))
  for(p in snapshot.products)products.add(listOf(
   p.code,p.name,p.category,p.brand,p.unit,p.stock.toString(),
   p.minimum.toString(),p.shortage.toString(),p.status,p.currency,
   p.salePrice.toString(),p.purchasePrice.toString()))
  val movements=mutableListOf(
   listOf("Fecha registrada","Producto","Tipo","Cantidad (+/-)","Motivo / referencia"))
  for(m in snapshot.movements)movements.add(listOf(
   m.date,m.product,m.type,m.qty.toString(),m.reason))
  ZipOutputStream(out).use{zip->
   entry(zip,"[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
   entry(zip,"_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
   entry(zip,"xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Existencias" sheetId="1" r:id="rId1"/>
<sheet name="Movimientos registrados" sheetId="2" r:id="rId2"/></sheets>
</workbook>""")
   entry(zip,"xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
   entry(zip,"xl/styles.xml","""<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>
<font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF123662"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
</styleSheet>""")
   entry(zip,"xl/worksheets/sheet1.xml",sheet(products,setOf(5,6,7,10,11)))
   entry(zip,"xl/worksheets/sheet2.xml",sheet(movements,setOf(3)))
  }
 }

 /** PDF A4 horizontal, con páginas, encabezados de tabla y márgenes propios. */
 fun writePdf(snapshot:Snapshot,company:String,ruc:String,out:OutputStream){
  val pdf=PdfDocument()
  val navy=Color.rgb(16,48,94)
  val teal=Color.rgb(13,113,92)
  val muted=Color.rgb(94,105,117)
  val width=842f
  val height=595f
  val left=36f
  val right=806f
  val bottom=554f
  val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  var page:PdfDocument.Page?=null
  lateinit var canvas:Canvas
  var pageNumber=0
  var y=0f
  val generated=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("es","PE")).format(Date())
  fun box(x:Float,t:Float,x2:Float,b:Float,color:Int){
   paint.color=color;paint.style=Paint.Style.FILL
   canvas.drawRect(x,t,x2,b,paint)
  }
  fun txt(value:String,x:Float,baseline:Float,size:Float=9f,
   bold:Boolean=false,color:Int=navy){
   paint.style=Paint.Style.FILL;paint.color=color
   paint.textSize=size
   paint.typeface=if(bold)android.graphics.Typeface.create(
    android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
    else android.graphics.Typeface.DEFAULT
   canvas.drawText(value,x,baseline,paint)
  }
  fun fitted(v:String,x:Float,baseline:Float,max:Float,
   size:Float=8.5f,bold:Boolean=false,color:Int=navy){
   var value=v.replace("\n"," ")
   paint.textSize=size
   paint.typeface=if(bold)android.graphics.Typeface.DEFAULT_BOLD
    else android.graphics.Typeface.DEFAULT
   if(paint.measureText(value)>max){
    while(value.isNotEmpty()&&paint.measureText(value+"…")>max)value=value.dropLast(1)
    value+="…"
   }
   txt(value,x,baseline,size,bold,color)
  }
  fun finish(){
   val current=page?:return
   box(left,height-35f,right,height-34f,teal)
   fitted(company,left,height-22f,450f,8f,true)
   txt("Página "+pageNumber,right-79f,height-22f,8f,false,muted)
   pdf.finishPage(current)
  }
  fun start(){
   if(page!=null)finish()
   pageNumber++
   page=pdf.startPage(PdfDocument.PageInfo.Builder(width.toInt(),
    height.toInt(),pageNumber).create())
   canvas=page!!.canvas
   box(0f,0f,width,height,Color.WHITE)
   box(0f,0f,width,68f,navy)
   fitted(company,left,29f,490f,17f,true,Color.WHITE)
   fitted("RUC "+ruc+"  |  Reporte de existencias y Kárdex",
    left,48f,600f,9.5f,false,Color.WHITE)
   txt(generated,right-111f,48f,9f,false,Color.WHITE)
   y=91f
  }
  fun space(needed:Float){
   if(y+needed>bottom)start()
  }
  fun section(label:String){
   space(47f)
   txt(label,left,y+9f,12f,true,navy)
   y+=23f
  }
  start()
  section("Existencias actuales")
  fitted("Productos: "+snapshot.products.size+"  |  Unidades registradas: "+snapshot.units+
   "  |  Sin stock: "+snapshot.out+"  |  Bajo mínimo: "+snapshot.low,
   left,y,760f,9.2f,false,muted)
  y+=18f
  val px=floatArrayOf(36f,122f,315f,402f,465f,519f,566f,617f,708f,806f)
  fun productHeader(){
   box(left,y-14f,right,y+10f,teal)
   val names=listOf("CÓDIGO","DESCRIPCIÓN","MARCA","UNIDAD","STOCK",
    "MÍN.","FALTA","ESTADO")
   val x=floatArrayOf(40f,124f,318f,405f,468f,522f,570f,619f)
   names.forEachIndexed{i,n->txt(n,x[i],y+2f,7.5f,true,Color.WHITE)}
   y+=19f
  }
  productHeader()
  snapshot.products.forEachIndexed{index,product->
   if(y+26f>bottom){start();section("Existencias actuales (continuación)");productHeader()}
   if(index%2==0)box(left,y-6f,right,y+16f,Color.rgb(241,246,249))
   val color=if(product.status=="SIN STOCK")Color.rgb(168,51,48)
    else if(product.status=="STOCK BAJO")Color.rgb(161,107,22) else navy
   fitted(product.code,px[0]+3f,y+8f,px[1]-px[0]-7f,8f)
   fitted(product.name,px[1]+3f,y+8f,px[2]-px[1]-8f,8f)
   fitted(product.brand,px[2]+3f,y+8f,px[3]-px[2]-8f,8f)
   fitted(product.unit,px[3]+3f,y+8f,px[4]-px[3]-8f,7.7f)
   txt(product.stock.toString(),px[4]+5f,y+8f,8.3f,true,color)
   txt(product.minimum.toString(),px[5]+5f,y+8f,8f)
   txt(product.shortage.toString(),px[6]+5f,y+8f,8f,false,color)
   fitted(product.status,px[7]+3f,y+8f,px[8]-px[7]-7f,7.9f,true,color)
   y+=23f
  }
  if(snapshot.products.isEmpty()){
   txt("No existen productos físicos registrados en este catálogo.",left+8f,y+10f,9f)
   y+=30f
  }
  space(70f)
  y+=15f
  section("Movimientos registrados")
  fitted("Historial disponible en esta instalación; no sustituye un Kárdex valorizado completo.",
   left,y,750f,8.5f,false,muted)
  y+=19f
  fun movementHeader(){
   box(left,y-14f,right,y+10f,teal)
   listOf(Pair("FECHA",40f),Pair("PRODUCTO",148f),Pair("TIPO",416f),
    Pair("CANT.",505f),Pair("REFERENCIA",590f)).forEach{
    txt(it.first,it.second,y+2f,7.5f,true,Color.WHITE)
   }
   y+=19f
  }
  movementHeader()
  snapshot.movements.forEachIndexed{index,m->
   if(y+26f>bottom){start();section("Movimientos (continuación)");movementHeader()}
   if(index%2==0)box(left,y-6f,right,y+16f,Color.rgb(241,246,249))
   fitted(m.date,40f,y+8f,102f,8f)
   fitted(m.product,148f,y+8f,260f,8f)
   fitted(m.type,416f,y+8f,84f,8f,true)
   txt(m.qty.toString(),505f,y+8f,8.2f,true,
    if(m.qty<0)Color.rgb(168,51,48) else teal)
   fitted(m.reason,590f,y+8f,210f,7.8f)
   y+=23f
  }
  if(snapshot.movements.isEmpty())
   txt("Aún no se han registrado movimientos de inventario.",left+8f,y+10f,9f)
  finish()
  pdf.writeTo(out)
  pdf.close()
 }
}