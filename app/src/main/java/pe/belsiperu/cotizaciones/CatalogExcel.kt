package pe.belsiperu.cotizaciones

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** XLSX compatible con Excel. No necesita librerías de escritorio. */
object CatalogExcel {
 val keys=listOf("codigo","nombre","tipo","codigo_barras","categoria","marca",
  "unidad","moneda","precio_venta","precio_compra","stock","stock_minimo",
  "venta_incluye_igv","compra_incluye_igv","afectacion_igv","tipo_isc",
  "codigo_sunat","icbper","vender_sin_stock","peso_kg")
 val headings=listOf("Código","Nombre","Tipo","Código de barras","Categoría",
  "Marca","Unidad","Moneda","Precio venta","Precio compra","Stock","Stock mínimo",
  "Venta incluye IGV","Compra incluye IGV","Afectación IGV","Tipo ISC",
  "Código SUNAT","ICBPER","Vender sin stock","Peso kg")
 private val numeric=setOf(8,9,10,11,19)
 private fun esc(s:String):String=buildString {
  for(c in s)append(when(c){
   '&'->"&amp;";'<'->"&lt;";'>'->"&gt;";'"'->"&quot;";'\''->"&apos;"
   else->if(c.code<32&&c!='\t'&&c!='\n') " " else c.toString()
  })
 }
 private fun col(i:Int):String{
  var n=i+1;var s=""
  while(n>0){n--;s=('A'+n%26)+s;n/=26}
  return s
 }
 private fun put(zip:ZipOutputStream,path:String,data:String){
  zip.putNextEntry(ZipEntry(path))
  zip.write(data.toByteArray(Charsets.UTF_8))
  zip.closeEntry()
 }
 private fun sheet(rows:List<List<String>>,header:Boolean):String{
  val x=StringBuilder(1000+rows.size*200)
  x.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
  x.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
  x.append("<sheetViews><sheetView workbookViewId=\"0\">")
  if(header)x.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
  x.append("</sheetView></sheetViews><cols>")
  val count=rows.maxOfOrNull{it.size}?:0
  for(i in 0 until count){
   val width=when(i){1->39;4,5->24;else->18}
   x.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1)
    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>")
  }
  x.append("</cols><sheetData>")
  rows.forEachIndexed{ri,row->
   x.append("<row r=\"").append(ri+1).append("\">")
   row.forEachIndexed{ci,v->
    if(v.isNotEmpty()){
     val addr=col(ci)+(ri+1)
     val n=if(header&&ri>0&&ci in numeric)
      v.toDoubleOrNull()?.takeIf{it.isFinite()} else null
     if(n!=null)x.append("<c r=\"").append(addr).append("\" s=\"2\"><v>")
      .append(n).append("</v></c>")
     else{
      x.append("<c r=\"").append(addr).append("\" t=\"inlineStr\"")
      if(header&&ri==0)x.append(" s=\"1\"")
      x.append("><is><t xml:space=\"preserve\">").append(esc(v))
       .append("</t></is></c>")
     }
    }
   }
   x.append("</row>")
  }
  x.append("</sheetData></worksheet>")
  return x.toString()
 }
 fun write(out:OutputStream,data:List<List<String>>){
  require(data.size<=15000){"El catálogo excede 15 000 productos."}
  val guide=listOf(
   listOf("BELSIPERU - IMPORTACIÓN DE PRODUCTOS"),
   listOf("Mantén los encabezados de la primera fila de la hoja Productos."),
   listOf("Código y Nombre son obligatorios."),
   listOf("Tipo: PRODUCTO o SERVICIO. Moneda: PEN o USD."),
   listOf("Precios, cantidades y peso: números sin símbolos ni unidades."),
   listOf("Para IGV, ICBPER y Vender sin stock: SI o NO."),
   listOf("Afectación IGV: GRAVADO, EXONERADO o INAFECTO."),
   listOf("Los productos existentes se identifican por su código."),
   listOf("Las fotos y las variantes de precio se gestionan en la aplicación."),
   listOf("Haz una exportación de respaldo antes de actualizar muchos productos.")
  )
  ZipOutputStream(out).use{z->
   put(z,"[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
   put(z,"_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
   put(z,"xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Productos" sheetId="1" r:id="rId1"/><sheet name="Guía" sheetId="2" r:id="rId2"/></sheets></workbook>""")
   put(z,"xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
   put(z,"xl/styles.xml","""<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF153866"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>""")
   put(z,"xl/worksheets/sheet1.xml",sheet(listOf(headings)+data,true))
   put(z,"xl/worksheets/sheet2.xml",sheet(guide,false))
  }
 }
 private fun eachXml(bytes:ByteArray,process:(XmlPullParser)->Unit){
  val p=Xml.newPullParser()
  p.setInput(ByteArrayInputStream(bytes),"UTF-8")
  var e=p.eventType
  while(e!=XmlPullParser.END_DOCUMENT){process(p);e=p.next()}
 }
 private fun shared(bytes:ByteArray?):List<String>{
  if(bytes==null)return emptyList()
  val result=mutableListOf<String>()
  var inString=false;var str=StringBuilder()
  eachXml(bytes){p->
   if(p.eventType==XmlPullParser.START_TAG){
    when(p.name){
     "si"->{inString=true;str=StringBuilder()}
     "t"->if(inString)str.append(p.nextText())
    }
   }else if(p.eventType==XmlPullParser.END_TAG&&p.name=="si"){
    result.add(str.toString());inString=false
   }
  }
  return result
 }
 private fun cellColumn(ref:String):Int{
  var n=0
  for(c in ref.uppercase(Locale.ROOT)){
   if(c !in 'A'..'Z')break
   n=n*26+(c-'A'+1)
  }
  return n-1
 }
 private fun sheetRows(bytes:ByteArray,strings:List<String>):List<List<String>>{
  val result=mutableListOf<List<String>>()
  var values=mutableMapOf<Int,String>()
  var col=-1;var type="";var content="";var inCell=false
  eachXml(bytes){p->
   when(p.eventType){
    XmlPullParser.START_TAG->when(p.name){
     "row"->values=mutableMapOf()
     "c"->{
      inCell=true;col=cellColumn(p.getAttributeValue(null,"r")?:"")
      type=p.getAttributeValue(null,"t")?:"";content=""
     }
     "v"->if(inCell)content+=p.nextText()
     "t"->if(inCell&&type=="inlineStr")content+=p.nextText()
    }
    XmlPullParser.END_TAG->when(p.name){
     "c"->{
      if(inCell&&col in 0..64)
       values[col]=if(type=="s")strings.getOrNull(content.toIntOrNull()?:-1)?:"" else content
      inCell=false
     }
     "row"->{
      val end=values.keys.maxOrNull()?:-1
      if(end>=0)result.add((0..end).map{values[it]?:""})
      if(result.size>15001)throw IllegalArgumentException("El archivo supera 15 000 filas.")
     }
    }
   }
  }
  return result
 }
 private fun norm(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFD)
  .replace(Regex("[\u0300-\u036f]"),"").lowercase(Locale.ROOT)
  .replace(Regex("[^a-z0-9]"),"")
 private fun key(label:String):String?=when(norm(label)){
  "codigo","codigoproducto","sku","cod"->"codigo"
  "nombre","descripcion","producto","nombreproducto"->"nombre"
  "tipo"->"tipo"
  "codigobarras","barcode","ean","barras"->"codigo_barras"
  "categoria"->"categoria"
  "marca"->"marca"
  "unidad","unid"->"unidad"
  "moneda"->"moneda"
  "precioventa","precio"->"precio_venta"
  "preciocompra","costo"->"precio_compra"
  "stock","cantidad","existencias"->"stock"
  "stockminimo","minimo"->"stock_minimo"
  "ventaincluyeigv"->"venta_incluye_igv"
  "compraincluyeigv"->"compra_incluye_igv"
  "afectacionigv"->"afectacion_igv"
  "tipoisc","isc"->"tipo_isc"
  "codigosunat"->"codigo_sunat"
  "icbper"->"icbper"
  "vendersinstock"->"vender_sin_stock"
  "pesokg","peso"->"peso_kg"
  else->null
 }
 fun read(input:InputStream):List<Map<String,String>>{
  val parts=mutableMapOf<String,ByteArray>()
  var total=0;var count=0
  ZipInputStream(input).use{zip->
   val buffer=ByteArray(8192)
   var entry=zip.nextEntry
   while(entry!=null){
    count++
    if(count>300)throw IllegalArgumentException("El archivo Excel es demasiado complejo.")
    val name=entry.name
    if(!entry.isDirectory&&name.endsWith(".xml")&&
     (name.startsWith("xl/worksheets/")||name=="xl/sharedStrings.xml")){
     val bytes=ByteArrayOutputStream()
     while(true){
      val n=zip.read(buffer);if(n<0)break
      total+=n
      if(total>24*1024*1024||bytes.size()+n>16*1024*1024)
       throw IllegalArgumentException("El archivo Excel es demasiado grande.")
      bytes.write(buffer,0,n)
     }
     parts[name]=bytes.toByteArray()
    }
    zip.closeEntry();entry=zip.nextEntry
   }
  }
  val src=parts["xl/worksheets/sheet1.xml"]
   ?:parts.filterKeys{it.startsWith("xl/worksheets/sheet")&&it.endsWith(".xml")}
    .toSortedMap().values.firstOrNull()
   ?:throw IllegalArgumentException("Excel sin hoja de productos.")
  val data=sheetRows(src,shared(parts["xl/sharedStrings.xml"]))
  val headerIndex=data.indexOfFirst{row->
   val headings=row.mapNotNull{key(it)}
   "codigo" in headings&&"nombre" in headings
  }
  if(headerIndex !in 0..9)
   throw IllegalArgumentException("Faltan columnas Código y Nombre en las primeras 10 filas.")
  val cols=data[headerIndex].map{key(it)}
  return data.drop(headerIndex+1).mapIndexedNotNull{index,row->
   if(row.all{it.isBlank()})null else mutableMapOf<String,String>().apply{
    row.forEachIndexed{j,value->cols.getOrNull(j)?.let{k->put(k,value.trim())}}
    put("_fila",(index+headerIndex+2).toString())
   }
  }
 }
}