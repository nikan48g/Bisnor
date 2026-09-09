package com.hnn.bisnor.data.remote

import android.content.Context
import android.util.Base64
import com.hnn.bisnor.BuildConfig
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaylistsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.*

data class FunnyAvatar(val id:String,val name:String,val drawableRes:Int)
class AuthManager(private val context:Context) {
 private val p=context.getSharedPreferences("bisnor_auth_v3",Context.MODE_PRIVATE)
 companion object { val FUNNY_AVATARS=listOf(FunnyAvatar("avatar_breakingbad","والتر وایت (بریکینگ بد)",R.drawable.avatar_breakingbad),FunnyAvatar("avatar_luffy","لوفی (وان پیس)",R.drawable.avatar_luffy),FunnyAvatar("avatar_wednesday","ونزدی آدامز",R.drawable.avatar_wednesday),FunnyAvatar("avatar_nami","نامی (وان پیس)",R.drawable.avatar_nami),FunnyAvatar("avatar_garfield","گارفیلد",R.drawable.avatar_garfield),FunnyAvatar("avatar_bluey","بلویی",R.drawable.avatar_bluey),FunnyAvatar("avatar_bingo","بینگو",R.drawable.avatar_bingo),FunnyAvatar("avatar_carmen","کارمن سندیگو",R.drawable.avatar_carmen),FunnyAvatar("avatar_film","کلاکت سینما",R.drawable.avatar_film),FunnyAvatar("avatar_theater","ماسک نمایش",R.drawable.avatar_theater),FunnyAvatar("avatar_star","ستاره طلایی",R.drawable.avatar_star)); fun getAvatarDrawable(id:String)=FUNNY_AVATARS.find{it.id==id}?.drawableRes?:R.drawable.avatar_breakingbad }
 var currentUsername:String get()=p.getString("name","")?:""; private set(v){p.edit().putString("name",v).apply()}
 var userAvatarId:String get()=p.getString("avatar","avatar_breakingbad")?:"avatar_breakingbad"; set(v){p.edit().putString("avatar",v).apply()}
 var userAvatarUrl:String get()=p.getString("url","")?:""; set(v){p.edit().putString("url",v).apply()}
 var isLoggedIn:Boolean get()=SupabaseManager.hasSession(context); private set(v){}
 var lastSyncTime:Long get()=p.getLong("sync",0); set(v){p.edit().putLong("sync",v).apply()}
 fun updateUsername(v:String){currentUsername=v}; fun updateAvatarUrl(v:String){userAvatarUrl=v}; fun logout(){SupabaseManager.clear(context);p.edit().clear().apply()}
 private fun internalEmail(username:String) = "${username.trim().lowercase()}@accounts.bisnor.local"
 suspend fun register(name:String,password:String):Pair<Boolean,String> { if(!name.matches(Regex("[a-z0-9_.-]{3,32}")))return false to "نام کاربری نامعتبر است."; if(password.length<8)return false to "رمز عبور باید حداقل ۸ کاراکتر باشد."; val r=SupabaseManager.signup(context,name,internalEmail(name),password,userAvatarId); if(r.first&&SupabaseManager.hasSession(context)){currentUsername=name;syncUp(context)};return r }
 suspend fun login(username:String,password:String):Pair<Boolean,String>{val r=SupabaseManager.login(context,internalEmail(username),password);if(!r.first)return r;val x=SupabaseManager.profile(context)?:return false to "پروفایل امن پیدا نشد.";currentUsername=x.optString("username");userAvatarId=x.optString("avatar_id",userAvatarId);syncDown(context,x);return true to "ورود امن انجام شد."}
 suspend fun updateAvatar(v:String)=run {userAvatarId=v;!isLoggedIn||SupabaseManager.patch(context,JSONObject().put("avatar_id",v))}
 suspend fun syncUp(c:Context):Boolean{if(!isLoggedIn)return false;val x=JSONObject().put("favorites_data",SupabaseManager.compressString(FavoritesManager(c).getFavoritesRawJson())).put("playlists_data",SupabaseManager.compressString(PlaylistsManager(c).getPlaylistsRawJson())).put("avatar_id",userAvatarId);return SupabaseManager.patch(c,x).also{if(it)lastSyncTime=System.currentTimeMillis()}}
 suspend fun syncDown(c:Context,cachedProfile:JSONObject?=null):Boolean{if(!isLoggedIn)return false;val x=cachedProfile?:SupabaseManager.profile(c)?:return false;userAvatarId=x.optString("avatar_id",userAvatarId);runCatching{SupabaseManager.decompressString(x.optString("favorites_data")).takeIf{it.isNotBlank()&&it!="[]"}?.let{FavoritesManager(c).setFavoritesFromRawJson(it)}};runCatching{SupabaseManager.decompressString(x.optString("playlists_data")).takeIf{it.isNotBlank()&&it!="[]"}?.let{PlaylistsManager(c).setPlaylistsFromRawJson(it)}};lastSyncTime=System.currentTimeMillis();return true}
}
object SupabaseManager { private const val P="bisnor_session";private val h=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build();private val u get()=BuildConfig.SUPABASE_URL.trimEnd('/');private val k get()=BuildConfig.SUPABASE_ANON_KEY
 private fun p(c:Context)=c.getSharedPreferences(P,Context.MODE_PRIVATE);fun hasSession(c:Context?=null)=c!=null&&p(c).getString("token","")!!.isNotBlank();fun clear(c:Context){p(c).edit().clear().apply()}
 fun compressString(s:String):String{val b=ByteArrayOutputStream();GZIPOutputStream(b).use{it.write(s.toByteArray())};return Base64.encodeToString(b.toByteArray(),Base64.NO_WRAP)};fun decompressString(s:String):String{if(s.isBlank())return "";return GZIPInputStream(ByteArrayInputStream(Base64.decode(s,Base64.NO_WRAP))).bufferedReader(StandardCharsets.UTF_8).readText()}
 private fun q(c:Context,path:String,method:String="GET",body:JSONObject?=null,auth:Boolean=false):Pair<Int,String>{val b=Request.Builder().url(u+path).addHeader("apikey",k).addHeader("Authorization","Bearer "+if(auth)p(c).getString("token","")else k);val rb=body?.toString()?.toRequestBody("application/json".toMediaType());when(method){"POST"->b.post(rb!!);"PATCH"->b.patch(rb!!);else->b.get()};h.newCall(b.build()).execute().use{return it.code to(it.body?.string()?:"")}}
 private fun save(c:Context,s:String):Boolean{val x=JSONObject(s);val t=x.optString("access_token");val id=x.optJSONObject("user")?.optString("id")?:"";if(t.isBlank()||id.isBlank())return false;p(c).edit().putString("token",t).putString("id",id).apply();return true}
 suspend fun signup(c:Context,n:String,e:String,pw:String,a:String)=withContext(Dispatchers.IO){runCatching{val(r,s)=q(c,"/auth/v1/signup","POST",JSONObject().put("email",e).put("password",pw).put("data",JSONObject().put("username",n).put("avatar_id",a)));if(r !in 200..299) false to "ثبت‌نام انجام نشد." else {save(c,s);true to if(hasSession(c))"حساب امن ساخته شد." else "ایمیل تأیید را باز کنید."}}.getOrElse{false to "خطای ارتباط امن."}}
 suspend fun login(c:Context,e:String,pw:String)=withContext(Dispatchers.IO){runCatching{val(r,s)=q(c,"/auth/v1/token?grant_type=password","POST",JSONObject().put("email",e).put("password",pw));if(r in 200..299&&save(c,s))true to "ok" else false to "ایمیل یا رمز نادرست است."}.getOrElse{false to "خطای ارتباط امن."}}
 suspend fun profile(c:Context):JSONObject?=withContext(Dispatchers.IO){if(!hasSession(c))null else runCatching{val id=p(c).getString("id","");val(r,s)=q(c,"/rest/v1/profiles?user_id=eq.$id&select=*",auth=true);if(r in 200..299)JSONArray(s).optJSONObject(0) else null}.getOrNull()}
 suspend fun patch(c:Context,x:JSONObject)=withContext(Dispatchers.IO){val id=p(c).getString("id","");runCatching{q(c,"/rest/v1/profiles?user_id=eq.$id","PATCH",x,true).first in 200..299}.getOrDefault(false)}
}
