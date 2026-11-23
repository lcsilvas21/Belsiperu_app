package pe.belsiperu.cotizaciones

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Protección local de PIN, recuperación y biometría. El PIN y el código de
 * emergencia nunca se guardan en texto plano después de la migración.
 * No existe un PIN maestro ni un mecanismo para ignorar la pantalla de acceso.
 */
class AppSecurity(context:Context){
 private val prefs:SharedPreferences=
  context.getSharedPreferences("security",Context.MODE_PRIVATE)
 private val random=SecureRandom()
 private val alias="belsiperu_fingerprint_gate_v1"
 private val alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
 private val pbkdfRounds=180_000

 private fun bytes(count:Int)=ByteArray(count).also{random.nextBytes(it)}
 private fun encoded(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.NO_WRAP)
 private fun decoded(value:String)=Base64.decode(value,Base64.DEFAULT)
 private fun derive(text:String,salt:ByteArray):ByteArray{
  val spec=PBEKeySpec(text.toCharArray(),salt,pbkdfRounds,256)
  return try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
   .generateSecret(spec).encoded}finally{spec.clearPassword()}
 }
 private fun hash(value:String,prefix:String,editor:SharedPreferences.Editor){
  val salt=bytes(16)
  editor.putString(prefix+"_salt",encoded(salt))
  editor.putString(prefix+"_hash",encoded(derive(value,salt)))
 }
 private fun matches(value:String,prefix:String):Boolean{
  val salt=prefs.getString(prefix+"_salt",null)?:return false
  val stored=prefs.getString(prefix+"_hash",null)?:return false
  return try{
   MessageDigest.isEqual(derive(value,decoded(salt)),decoded(stored))
  }catch(_:Exception){false}
 }
 fun hasPin()=prefs.contains("pin_hash")||prefs.contains("pin")
 private fun validate(pin:String):Boolean=pin.matches(Regex("[0-9]{4,6}"))
 fun lockedSeconds():Long{
  val until=prefs.getLong("lock_until",0L)
  return ((until-System.currentTimeMillis()+999L)/1000L).coerceAtLeast(0L)
 }
 private fun failed(){
  val attempts=(prefs.getInt("failed",0)+1).coerceAtMost(100)
  val penalty=when{
   attempts<5->0L
   attempts<10->30_000L
   attempts<15->120_000L
   else->600_000L
  }
  prefs.edit().putInt("failed",attempts)
   .putLong("lock_until",System.currentTimeMillis()+penalty).apply()
 }
 private fun succeeded(){
  prefs.edit().putInt("failed",0).putLong("lock_until",0L).apply()
 }
 fun verifyPin(pin:String):Boolean{
  if(lockedSeconds()>0)return false
  val ok=if(prefs.contains("pin_hash"))matches(pin,"pin") else
   pin==prefs.getString("pin",null)&&validate(pin)
  if(!ok){failed();return false}
  if(!prefs.contains("pin_hash")){
   val edit=prefs.edit()
   hash(pin,"pin",edit)
   edit.remove("pin").commit()
  }
  succeeded()
  return true
 }
 fun initialize(pin:String):String{
  require(!hasPin()){"Ya existe una clave de acceso."}
  require(validate(pin)){"El PIN debe contener entre 4 y 6 dígitos."}
  val editor=prefs.edit()
  hash(pin,"pin",editor)
  editor.remove("pin").putInt("failed",0).putLong("lock_until",0L)
  require(editor.commit()){"No se pudo guardar el PIN."}
  return rotateRecoveryCode()
 }
 fun changePin(current:String,newPin:String):Boolean{
  if(!validate(newPin))return false
  if(!verifyPin(current))return false
  val editor=prefs.edit()
  hash(newPin,"pin",editor)
  return editor.remove("pin").commit()
 }
 fun hasRecoveryCode()=prefs.contains("recovery_hash")
 fun rotateRecoveryCode():String{
  val raw=buildString{
   repeat(20){append(alphabet[random.nextInt(alphabet.length)])}
  }
  val result=raw.chunked(5).joinToString("-")
  val edit=prefs.edit()
  hash(result.replace("-",""),"recovery",edit)
  require(edit.commit()){"No se pudo guardar el código de emergencia."}
  return result
 }
 fun recoverPin(code:String,newPin:String):String?{
  if(!validate(newPin)||lockedSeconds()>0)return null
  val clean=code.trim().uppercase().replace("-","").replace(" ","")
  if(!matches(clean,"recovery")){
   failed();return null
  }
  val edit=prefs.edit()
  hash(newPin,"pin",edit)
  edit.remove("pin")
  if(!edit.commit())return null
  succeeded()
  // El código utilizado queda invalidado y se sustituye por otro.
  disableBiometric()
  return rotateRecoveryCode()
 }
 fun isBiometricEnabled():Boolean=
  prefs.getBoolean("biometric_enabled",false)&&prefs.contains("biometric_iv")
 private fun keyStore():KeyStore=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 private fun secretKey():SecretKey?=
  keyStore().getKey(alias,null) as? SecretKey
 fun createEnrollCipher():Cipher{
  check(Build.VERSION.SDK_INT>=28){"La huella requiere Android 9 o posterior."}
  disableBiometric()
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  val builder=KeyGenParameterSpec.Builder(alias,
   KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setUserAuthenticationRequired(true)
   .setInvalidatedByBiometricEnrollment(true)
  if(Build.VERSION.SDK_INT>=30){
   builder.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG)
  }else{
   @Suppress("DEPRECATION")
   builder.setUserAuthenticationValidityDurationSeconds(-1)
  }
  generator.init(builder.build())
  val key=generator.generateKey()
  return Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key)}
 }
 fun finishEnrollment(cipher:Cipher):Boolean{
  val proof=bytes(32)
  val encrypted=cipher.doFinal(proof)
  val editor=prefs.edit()
  editor.putString("biometric_iv",encoded(cipher.iv))
  editor.putString("biometric_cipher",encoded(encrypted))
  editor.putString("biometric_proof",encoded(MessageDigest.getInstance("SHA-256").digest(proof)))
  editor.putBoolean("biometric_enabled",true)
  return editor.commit()
 }
 fun createUnlockCipher():Cipher{
  require(isBiometricEnabled()){"No se activó la huella."}
  val iv=decoded(prefs.getString("biometric_iv",null)!!)
  val key=secretKey()?:throw IllegalStateException("La clave biométrica no está disponible.")
  return Cipher.getInstance("AES/GCM/NoPadding").apply{
   init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,iv))
  }
 }
 fun finishUnlock(cipher:Cipher):Boolean{
  return try{
   val data=decoded(prefs.getString("biometric_cipher",null)!!)
   val expected=decoded(prefs.getString("biometric_proof",null)!!)
   val proof=cipher.doFinal(data)
   MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(proof),expected)
  }catch(_:Exception){false}
 }
 fun disableBiometric(){
  prefs.edit().remove("biometric_iv").remove("biometric_cipher")
   .remove("biometric_proof").putBoolean("biometric_enabled",false).commit()
  try{keyStore().deleteEntry(alias)}catch(_:Exception){}
 }
}
