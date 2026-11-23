package pe.belsiperu.cotizaciones

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Respaldo portátil cifrado AES-256-GCM, contraseña independiente del PIN.
 * Incluye base SQLite, preferencias no sensibles, logo y fotografías accesibles.
 * Excluye el PIN, el código de emergencia y las claves biométricas.
 */
object SecureBackup {
 private val magic="BELSBAK1".toByteArray(StandardCharsets.US_ASCII)
 private const val maxInput=80*1024*1024
 private const val maxDb=35*1024*1024
 private const val maxPhoto=6*1024*1024
 private const val maxPhotos=1000
 private const val iterations=220_000
 private val prefsNames=listOf("belsiperu_company_pdf","quote_sequence",
  "belsiperu_home_ui","ruc_service")
 data class Exported(val photoCount:Int,val inaccessiblePhotos:Int,val bytes:Long)
 data class Restored(val photoCount:Int,val databaseBytes:Long)
 private fun salt(size:Int)=ByteArray(size).also{SecureRandom().nextBytes(it)}
 private fun key(password:CharArray,salt:ByteArray):javax.crypto.SecretKey{
  val spec=PBEKeySpec(password,salt,iterations,256)
  return try{
   val bytes=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    .generateSecret(spec).encoded
   javax.crypto.spec.SecretKeySpec(bytes,"AES").also{bytes.fill(0)}
  }finally{spec.clearPassword()}
 }
 private fun prefValue(value:Any):JSONObject{
  val result=JSONObject()
  when(value){
   is String->result.put("type","str").put("value",value)
   is Boolean->result.put("type","bool").put("value",value)
   is Int->result.put("type","int").put("value",value)
   is Long->result.put("type","long").put("value",value)
   is Float->result.put("type","float").put("value",value.toDouble())
   is Set<*>->result.put("type","set").put("value",
    JSONArray(value.filterIsInstance<String>()))
  }
  return result
 }
 private fun preferenceExport(context:Context):JSONObject{
  val root=JSONObject()
  for(name in prefsNames){
   val all=context.getSharedPreferences(name,Context.MODE_PRIVATE).all
   val obj=JSONObject()
   for((key,value) in all)if(value!=null)obj.put(key,prefValue(value))
   root.put(name,obj)
  }
  return root
 }
 private fun restorePrefs(context:Context,root:JSONObject){
  for(name in prefsNames){
   val record=root.optJSONObject(name)?:continue
   val edit=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
   val keys=record.keys()
   while(keys.hasNext()){
    val k=keys.next()
    val entry=record.optJSONObject(k)?:continue
    when(entry.optString("type")){
     "str"->edit.putString(k,entry.optString("value",""))
     "bool"->edit.putBoolean(k,entry.optBoolean("value",false))
     "int"->edit.putInt(k,entry.optInt("value",0))
     "long"->edit.putLong(k,entry.optLong("value",0))
     "float"->edit.putFloat(k,entry.optDouble("value",0.0).toFloat())
     "set"->{
      val array=entry.optJSONArray("value")
      val values=mutableSetOf<String>()
      if(array!=null)for(i in 0 until array.length())values.add(array.optString(i))
      edit.putStringSet(k,values)
     }
    }
   }
   check(edit.commit()){"No se pudo restaurar $name."}
  }
 }
 private fun photos(context:Context,dbFile:File):List<String>{
  val db=SQLiteDatabase.openDatabase(dbFile.absolutePath,null,
   SQLiteDatabase.OPEN_READONLY)
  val values=linkedSetOf<String>()
  try{
   for(sql in listOf("SELECT photo_uri FROM products",
    "SELECT photo_uri FROM quote_items","SELECT product_photo FROM quotes")){
    val cursor=db.rawQuery(sql,null)
    try{while(cursor.moveToNext()){
     val uri=cursor.getString(0)
     if(!uri.isNullOrBlank()&&uri.startsWith("content://"))values.add(uri)
    }}finally{cursor.close()}
   }
  }finally{db.close()}
  return values.take(maxPhotos)
 }
 private fun entry(z:ZipOutputStream,name:String,source:InputStream,limit:Long):Long{
  z.putNextEntry(ZipEntry(name))
  var copied=0L
  val buffer=ByteArray(16*1024)
  while(true){
   val n=source.read(buffer);if(n<0)break
   copied+=n
   require(copied<=limit){"Archivo demasiado grande: $name"}
   z.write(buffer,0,n)
  }
  z.closeEntry()
  return copied
 }
 private fun bytesToZip(z:ZipOutputStream,name:String,data:ByteArray){
  z.putNextEntry(ZipEntry(name));z.write(data);z.closeEntry()
 }
 fun export(context:Context,store:Store,out:OutputStream,
  password:CharArray):Exported{
  require(password.size>=10){"La contraseña del respaldo debe tener al menos 10 caracteres."}
  // Cerramos la conexión antes de copiar para obtener un estado consistente.
  store.close()
  val dbFile=context.getDatabasePath("belsiperu.db")
  require(dbFile.exists()&&dbFile.length()<=maxDb){"Base de datos ausente o demasiado grande."}
  val photos=photos(context,dbFile)
  var writtenPhotos=0;var skipped=0;var bytes=0L
  val iv=salt(12);val salt=salt(16)
  out.write(magic);out.write(salt);out.write(iv)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{
   init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv))
  }
  CipherOutputStream(out,cipher).use{cipherStream->
   ZipOutputStream(cipherStream).use{zip->
    dbFile.inputStream().use{bytes+=entry(zip,"database.sqlite",it,maxDb.toLong())}
    bytesToZip(zip,"preferences.json",
     preferenceExport(context).toString().toByteArray(Charsets.UTF_8))
    val logo=File(context.filesDir,"company_pdf_logo.png")
    if(logo.isFile&&logo.length()<=maxPhoto)
     logo.inputStream().use{entry(zip,"company_logo.png",it,maxPhoto.toLong())}
    val mapping=JSONObject()
    for((i,originalUri) in photos.withIndex()){
     try{
      val src=context.contentResolver.openInputStream(Uri.parse(originalUri))
       ?:throw IllegalStateException("La foto ya no está disponible.")
      // Leer y validar primero: no dejar entradas ZIP incompletas al omitir.
      val data=src.use{readLimited(it,maxPhoto)}
      if(data.isEmpty())throw IllegalStateException("Fotografía vacía.")
      val name="photos/photo_$i.bin"
      bytesToZip(zip,name,data)
      bytes+=data.size
      mapping.put(originalUri,name)
      writtenPhotos++
     }catch(_:Exception){skipped++}
    }
    bytesToZip(zip,"photo_map.json",mapping.toString().toByteArray(Charsets.UTF_8))
   }
  }
  return Exported(writtenPhotos,skipped,bytes)
 }
 private fun readLimited(input:InputStream,limit:Int):ByteArray{
  val out=ByteArrayOutputStream()
  val buffer=ByteArray(16*1024)
  while(true){
   val n=input.read(buffer);if(n<0)break
   require(out.size()+n<=limit){"Respaldo demasiado grande."}
   out.write(buffer,0,n)
  }
  return out.toByteArray()
 }
 private fun unpack(data:ByteArray,dir:File){
  var size=0L
  var entries=0
  ZipInputStream(ByteArrayInputStream(data)).use{zip->
   var item=zip.nextEntry
   val buffer=ByteArray(16*1024)
   while(item!=null){
    entries++
    require(entries<=maxPhotos+10){"Respaldo con demasiados archivos."}
    val name=item.name
    require(name=="database.sqlite"||name=="preferences.json"||
     name=="company_logo.png"||name=="photo_map.json"||
     name.matches(Regex("photo[s]/photo_[0-9]{1,4}[.]bin"))){"Archivo no permitido."}
    val dest=File(dir,name)
    require(dest.canonicalPath.startsWith(dir.canonicalPath+File.separator)){
     "Ruta de archivo no permitida."
    }
    dest.parentFile?.mkdirs()
    dest.outputStream().use{out->
     while(true){
      val n=zip.read(buffer);if(n<0)break
      size+=n
      require(size<=maxInput){"Respaldo descomprimido demasiado grande."}
      out.write(buffer,0,n)
     }
    }
    zip.closeEntry()
    item=zip.nextEntry
   }
  }
 }
 private fun validateDatabase(file:File){
  require(file.isFile&&file.length()>0&&file.length()<=maxDb){"Base SQLite no válida."}
  val db=SQLiteDatabase.openDatabase(file.absolutePath,null,SQLiteDatabase.OPEN_READONLY)
  try{
   val check=db.rawQuery("PRAGMA integrity_check",null)
   try{require(check.moveToFirst()&&check.getString(0)=="ok"){
    "La base de datos del respaldo no pasó la prueba de integridad."
   }}finally{check.close()}
   for(table in listOf("products","quotes","quote_items","clients","movements")){
    val cursor=db.rawQuery(
     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
     arrayOf(table))
    try{require(cursor.moveToFirst()&&cursor.getInt(0)==1){
     "El respaldo no incluye la tabla $table."
    }}finally{cursor.close()}
   }
   // No importar una base más nueva que la app conoce.
   require(db.version in 7..8){"La versión de la base de datos no es compatible."}
  }finally{db.close()}
 }
 private fun rewritePhotoUris(context:Context,dbFile:File,mapping:JSONObject,
  extracted:File):Int{
  val db=SQLiteDatabase.openDatabase(dbFile.absolutePath,null,
   SQLiteDatabase.OPEN_READWRITE)
  var total=0
  val target=File(context.filesDir,"restored_photos")
  target.mkdirs()
  try{
   db.beginTransaction()
   val originals=mapping.keys()
   while(originals.hasNext()){
    val sourceUri=originals.next()
    val relative=mapping.optString(sourceUri)
    require(relative.matches(Regex("photos/photo_[0-9]{1,4}[.]bin")))
    val source=File(extracted,relative)
    if(!source.isFile)continue
    val targetFile=File(target,"restore_"+System.nanoTime()+"_"+total+".bin")
    source.copyTo(targetFile,overwrite=false)
    val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",
     targetFile).toString()
    for((table,col) in listOf("products" to "photo_uri",
     "quote_items" to "photo_uri","quotes" to "product_photo")){
     db.execSQL("UPDATE $table SET $col=? WHERE $col=?",
      arrayOf(uri,sourceUri))
    }
    total++
   }
   db.setTransactionSuccessful()
  }finally{
   if(db.inTransaction())db.endTransaction()
   db.close()
  }
  return total
 }
 /**
  * Solo invocar tras confirmación explícita del usuario. La importación
  * conserva el PIN/recovery actuales, para que no se bloquee el acceso.
  */
 fun restore(context:Context,store:Store,input:InputStream,
  password:CharArray):Restored{
  require(password.size>=10){"Contraseña de respaldo incorrecta o demasiado corta."}
  val header=ByteArray(magic.size)
  val data=java.io.DataInputStream(input)
  try{data.readFully(header)}catch(_:Exception){throw IllegalArgumentException("Archivo incompleto.")}
  require(header.contentEquals(magic)){
   "El archivo no tiene el formato de respaldo BELSIPERU."
  }
  val salt=ByteArray(16);val iv=ByteArray(12)
  try{data.readFully(salt);data.readFully(iv)}
   catch(_:Exception){throw IllegalArgumentException("Respaldo incompleto.")}
  val enc=readLimited(data,maxInput)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{
   init(Cipher.DECRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv))
  }
  // doFinal verifica el tag GCM ANTES de procesar o reemplazar datos.
  val decrypted=try{cipher.doFinal(enc)}
   catch(_:Exception){throw IllegalArgumentException("Contraseña incorrecta o respaldo alterado.")}
  val staging=File(context.cacheDir,"restore_"+System.nanoTime())
  check(staging.mkdirs()){"No se pudo preparar restauración."}
  try{
   unpack(decrypted,staging)
   val stagedDb=File(staging,"database.sqlite")
   validateDatabase(stagedDb)
   val prefsFile=File(staging,"preferences.json")
   require(prefsFile.isFile&&prefsFile.length()<1024*1024){"Faltan las preferencias."}
   val settings=JSONObject(prefsFile.readText())
   val photoMap=File(staging,"photo_map.json").let{
    if(it.isFile)JSONObject(it.readText()) else JSONObject()
   }
   val backed=File(staging,"original.sqlite")
   val current=context.getDatabasePath("belsiperu.db")
   store.close()
   val originalExists=current.isFile
   if(originalExists)current.copyTo(backed,overwrite=true)
   // Validación y preparación completas antes de sustituir la base actual.
   try{
    stagedDb.copyTo(current,overwrite=true)
    val count=rewritePhotoUris(context,current,photoMap,staging)
    restorePrefs(context,settings)
    val logo=File(staging,"company_logo.png")
    if(logo.isFile)logo.copyTo(File(context.filesDir,"company_pdf_logo.png"),
     overwrite=true)
    return Restored(count,current.length())
   }catch(e:Exception){
    if(originalExists)backed.copyTo(current,overwrite=true)
    else current.delete()
    throw e
   }
  }finally{staging.deleteRecursively()}
 }
}
