package com.lsnoglos.tubestrategia;

import android.app.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import android.os.*;
import android.content.Intent;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.common.api.Scope;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.NumberFormat;
import java.time.Instant;

public class MainActivity extends Activity {
    private static final int BG=0xff101827, PANEL=0xff1e293b, FG=0xfff1f5f9, MUTED=0xffa8b6c9, RED=0xfff04444;
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private LinearLayout root, body;
    private TextView status;
    private EditText query;
    private Spinner region,order,category;
    private HistoryStore store;
    private String driveToken,remoteBaseline;
    private boolean syncBusy=false,driveEnabled=false;
    private static final int DRIVE_REQUEST=4421;
    private static final String DRIVE_SCOPE="https://www.googleapis.com/auth/drive.appdata";
    private GoogleIdTokenCredential account;
    private boolean signingIn=false;
    private CancellationSignal signInCancellation;
    private CredentialManager credentialManager;
    private static final String GOOGLE_WEB_CLIENT="462342787368-pi9u7cfa8na7df34llfed6hc9so6ocf5.apps.googleusercontent.com";
    private int generation=0;
    private JSONArray lastVideos=new JSONArray(), lastChannels=new JSONArray();
    private String lastContext="";
    private boolean busy=false;
    private String current="home";
    private final String[] regions={"NI","MX","US","ES","CO","AR","BR"};
    private final String[] categories={"0","10","20","1"};
    interface Work { void run(int token,YouTubeApi api) throws Exception; }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        credentialManager=CredentialManager.create(this);
        store=new HistoryStore(this);
        loginScreen("");
    }
    @Override protected void onDestroy(){generation++;if(signInCancellation!=null)signInCancellation.cancel();pool.shutdownNow();super.onDestroy();}
    void loginScreen(String message){
        generation++;busy=false;current="login";
        root=column();root.setBackgroundColor(BG);root.setGravity(Gravity.CENTER);root.setPadding(dp(26),dp(24),dp(26),dp(24));setContentView(root);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);root.addView(logo,new LinearLayout.LayoutParams(dp(88),dp(88)));
        TextView title=text("TubeStrategIA",30,FG,true);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView sub=text("Inteligencia de mercado para YouTube",15,MUTED,false);sub.setGravity(Gravity.CENTER);root.addView(sub);
        TextView intro=text("Explora tendencias y encuentra tu próxima oportunidad.",18,FG,true);intro.setGravity(Gravity.CENTER);intro.setPadding(0,dp(28),0,dp(20));root.addView(intro);
        Button google=button("Continuar con Google",this::signIn);google.setTextColor(0xff202124);google.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xfff8fafc));root.addView(google,new LinearLayout.LayoutParams(-1,dp(52)));
        status=text(message.isEmpty()?"Accede con tu propia cuenta de Google.":message,13,MUTED,false);status.setGravity(Gravity.CENTER);root.addView(status);
        action(root,"Privacidad y condiciones",this::policies);
    }
    void signIn(){
        if(signingIn)return;
        signingIn=true;status.setText("Abriendo selector de Google…");
        signInCancellation=new CancellationSignal();
        try{
            GetSignInWithGoogleOption option=new GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT).build();
            GetCredentialRequest request=new GetCredentialRequest.Builder().addCredentialOption(option).build();
            credentialManager.getCredentialAsync(this,request,signInCancellation,getMainExecutor(),new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
                @Override public void onResult(GetCredentialResponse response){
                    signingIn=false;if(isFinishing()||isDestroyed())return;
                    try{
                        Credential c=response.getCredential();
                        if(!(c instanceof CustomCredential)||!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType()))throw new IllegalArgumentException();
                        account=GoogleIdTokenCredential.createFrom(c.getData());
                        home();
                        authorizeDrive();
                    }catch(Exception e){loginScreen("No se pudo leer la credencial de Google. Intenta nuevamente.");}
                }
                @Override public void onError(GetCredentialException e){
                    signingIn=false;if(isFinishing()||isDestroyed())return;
                    loginScreen("No se completó el acceso. Verifica que tu cuenta sea usuaria de prueba y que Google Play Services esté actualizado. ("+e.getClass().getSimpleName()+")");
                }
            });
        }catch(Exception e){signingIn=false;loginScreen("No se pudo abrir el acceso con Google.");}
    }
    void accountMenu(){
        if(account==null){loginScreen("");return;}
        new AlertDialog.Builder(this).setTitle("Mi cuenta").setMessage(account.getId()+"\n\nYouTube: "+(BuildConfig.BACKEND_URL.isEmpty()?"servicio central pendiente":"servicio central configurado")+"\nDrive: "+(driveEnabled?"sincronización activa":"sincronización pendiente o pausada"))
            .setPositiveButton("Cerrar sesión",(d,w)->signOut()).setNeutralButton("Sincronizar Drive",(d,w)->authorizeDrive()).setNegativeButton("Volver",null).show();
    }
    void signOut(){
        if(syncBusy){toast("Finaliza o cancela la sincronización primero.");return;}
        account=null;driveToken=null;remoteBaseline=null;driveEnabled=false;lastVideos=new JSONArray();lastChannels=new JSONArray();lastContext="";
        loginScreen("Sesión cerrada.");
        try{credentialManager.clearCredentialStateAsync(new ClearCredentialStateRequest(),null,getMainExecutor(),new CredentialManagerCallback<Void,ClearCredentialException>(){
            @Override public void onResult(Void result){}
            @Override public void onError(ClearCredentialException e){}
        });}catch(Exception ignored){}
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void authorizeDrive(){
        if(account==null||syncBusy)return;
        final GoogleIdTokenCredential session=account;syncBusy=true;
        AuthorizationRequest request=AuthorizationRequest.builder().setRequestedScopes(Arrays.asList(
            new Scope(DRIVE_SCOPE),new Scope("openid"),new Scope("https://www.googleapis.com/auth/userinfo.email"))).build();
        Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener(result->{
            if(account!=session||isDestroyed())return;
            if(result.hasResolution()){
                try{startIntentSenderForResult(result.getPendingIntent().getIntentSender(),DRIVE_REQUEST,null,0,0,0);}
                catch(Exception e){syncBusy=false;toast("No se pudo solicitar permiso de Drive.");}
            }else acceptDrive(result);
        }).addOnFailureListener(e->{if(account==session){syncBusy=false;toast("Drive no autorizado. Puedes seguir usando la aplicación.");}});
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=DRIVE_REQUEST)return;
        if(resultCode!=RESULT_OK||data==null){syncBusy=false;toast("Sincronización cancelada.");return;}
        try{acceptDrive(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));}
        catch(Exception e){syncBusy=false;toast("No se pudo obtener permiso de Drive.");}
    }
    void acceptDrive(AuthorizationResult result){
        syncBusy=false;
        if(account==null||result.getAccessToken()==null){toast("Permiso de Drive no disponible.");return;}
        driveToken=result.getAccessToken();inspectBackup();
    }
    void inspectBackup(){
        if(account==null||driveToken==null||syncBusy)return;
        final GoogleIdTokenCredential session=account;final String owner=session.getId().toLowerCase(Locale.ROOT);
        final String access=driveToken,baseline=remoteBaseline;final boolean auto=driveEnabled;syncBusy=true;
        pool.execute(()->{
            try{
                DriveBackup drive=new DriveBackup(access,owner);DriveBackup.Snapshot remote=drive.read();
                JSONArray local=store.read(owner);
                runOnUiThread(()->{
                    if(account!=session||isDestroyed())return;
                    if(local.toString().equals(remote.entries.toString())){
                        remoteBaseline=remote.signature();driveEnabled=true;syncBusy=false;toast("Respaldo y dispositivo coinciden.");return;
                    }
                    if(remote.id==null||(auto&&Objects.equals(baseline,remote.signature()))){
                        applyBackup(session,drive,remote,false);return;
                    }
                    new AlertDialog.Builder(this).setTitle("¿Desea sincronizar con su respaldo de Google Drive?")
                    .setMessage("Dispositivo: "+local.length()+" consultas. Drive: "+remote.entries.length()+" consultas. Sincronizar conserva ambos historiales sin duplicar registros.")
                    .setPositiveButton("Sincronizar",(d,w)->applyBackup(session,drive,remote,true))
                    .setNeutralButton("Ahora no",(d,w)->{syncBusy=false;driveEnabled=false;toast("Respaldo intacto.");})
                    .setNegativeButton("Reemplazar respaldo",(d,w)->new AlertDialog.Builder(this).setTitle("¿Reemplazar respaldo?")
                        .setMessage("Se sustituirán las consultas de Drive por las de este dispositivo. Si está vacío, el respaldo quedará vacío.")
                        .setPositiveButton("Sí, reemplazar",(x,y)->applyBackup(session,drive,remote,false))
                        .setNegativeButton("Cancelar",(x,y)->{syncBusy=false;driveEnabled=false;})
                        .setOnCancelListener(x->{syncBusy=false;driveEnabled=false;}).show())
                    .setOnCancelListener(d->{syncBusy=false;driveEnabled=false;}).show();
                });
            }catch(Exception e){syncError(session);}
        });
    }
    void applyBackup(GoogleIdTokenCredential session,DriveBackup drive,DriveBackup.Snapshot remote,boolean merge){
        if(account!=session)return;
        final String owner=session.getId().toLowerCase(Locale.ROOT);
        pool.execute(()->{
            try{
                JSONArray local=store.read(owner);
                JSONArray target=merge?HistoryStore.merge(local,remote.entries):local;
                DriveBackup.Snapshot latest=drive.read();
                if(!remote.signature().equals(latest.signature()))throw new java.io.IOException("Conflicto");
                if(merge)store.mergeInto(owner,target);
                drive.write(latest,target);
                DriveBackup.Snapshot saved=drive.read();
                if(!saved.entries.toString().equals(target.toString()))throw new java.io.IOException("Verificación");
                runOnUiThread(()->{if(account==session&&!isDestroyed()){remoteBaseline=saved.signature();driveEnabled=true;syncBusy=false;toast("Historial sincronizado con Drive.");if("history".equals(current))history();}});
            }catch(Exception e){syncError(session);}
        });
    }
    void syncError(GoogleIdTokenCredential session){
        runOnUiThread(()->{if(account==session&&!isDestroyed()){syncBusy=false;driveEnabled=false;driveToken=null;toast("No se sincronizó. Tus datos locales se conservan. Reintenta desde Mi cuenta.");}});
    }
    GradientDrawable bg(int c,int radius){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(radius));return d;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(4),0,dp(4));return t;
    }
    void note(LinearLayout l,String s){l.addView(text(s,13,MUTED,false));}
    Button button(String label,Runnable action){
        Button b=new Button(this); b.setAllCaps(false);b.setText(label);b.setTextColor(FG);b.setTextSize(13);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));b.setOnClickListener(v->action.run());return b;
    }
    void action(LinearLayout l,String title,Runnable action){l.addView(button(title,action));}
    LinearLayout card(LinearLayout target){
        LinearLayout l=column();l.setBackground(bg(PANEL,14));l.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);target.addView(l,p);return l;
    }
    void shell(String name,String subtitle){
        generation++;busy=false;current=name;
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(8),dp(16),dp(8));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("TubeStrategIA",23,FG,true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("Mi cuenta",this::accountMenu));root.addView(header);
        note(root,subtitle);
        status=text(BuildConfig.BACKEND_URL.isEmpty()?"YouTube · servicio central pendiente":"YouTube · cuota compartida de la aplicación",12,MUTED,false);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);
        String[] names={"Inicio","Buscar","Canales","Historial"};
        Runnable[] acts={this::home,this::explore,this::showChannels,this::history};
        for(int i=0;i<names.length;i++){Button b=button(names[i],acts[i]);nav.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(nav);
    }
    void home(){
        shell("home","Panorama · información pública y actual");
        LinearLayout intro=card(body);
        intro.addView(text("Observa el mercado",23,FG,true));
        note(intro,"Explora publicaciones de YouTube por región o investiga un tema específico. Las listas son muestras de la API, no un censo del mercado.");
        LinearLayout controls=card(body);
        note(controls,"Región del catálogo");
        region=spinner(controls,new String[]{"Nicaragua","México","Estados Unidos","España","Colombia","Argentina","Brasil"});
        note(controls,"Categoría del catálogo");
        category=spinner(controls,new String[]{"General","Música","Videojuegos","Cine y animación"});
        action(controls,"Consultar catálogo popular",()->popular());
        action(controls,"Explorar un nicho",this::explore);
        LinearLayout info=card(body);
        info.addView(text("Demanda, crecimiento y sostenibilidad",17,FG,true));
        note(info,"Aún no hay historial ni fuente de interés de búsqueda conectada. El catálogo popular no mide volumen de búsquedas. Los índices y el radar requieren autorización y mediciones suficientes.");
        action(info,"Consultar Google Trends (externo)",()->open("https://trends.google.com/trends/"));
        action(info,"Políticas y privacidad",this::policies);
    }
    Spinner spinner(LinearLayout l,String[] options){
        Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);l.addView(s);return s;
    }
    void explore(){
        shell("search","Explorador · búsqueda adaptativa por antigüedad");
        LinearLayout l=card(body);
        query=new EditText(this);query.setSingleLine(true);query.setHint("Gatos, IA, frutiseries...");query.setTextColor(FG);query.setHintTextColor(MUTED);l.addView(query);
        note(l,"Mercado de disponibilidad de videos (no ubicación de audiencia)");
        region=spinner(l,new String[]{"Nicaragua","México","Estados Unidos","España","Colombia","Argentina","Brasil"});
        note(l,"Orden de YouTube");
        order=spinner(l,new String[]{"Más visualizaciones","Más recientes","Relevancia"});
        action(l,"Buscar hasta 10 publicaciones",()->search());
        note(l,"Se amplía de 7 a 30 y 90 días si hay menos de diez resultados. Frecuencia de publicación y análisis de demanda: pendientes.");
        if(lastVideos.length()>0){note(body,"Última consulta: "+lastContext);renderVideos(body,lastVideos,10);}
    }
    boolean ready(){if(busy){status.setText("Hay una consulta en curso.");return false;}if(account==null){loginScreen("");return false;}if(syncBusy){status.setText("Finaliza la sincronización primero.");return false;}if(BuildConfig.BACKEND_URL.isEmpty()){status.setText("El administrador aún no ha desplegado el servicio central de YouTube.");return false;}return true;}
    void run(Work work){
        if(!ready())return;
        busy=true;int token=++generation;String sessionKey=account.getIdToken();status.setText("Consultando YouTube…");
        pool.execute(()->{try{work.run(token,new YouTubeApi(sessionKey));}catch(Exception e){
            runOnUiThread(()->{if(token==generation){busy=false;status.setText("No se pudo consultar. "+(e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("YouTube HTTP")?e.getMessage():"Comprueba Internet y configuración."));}});
        }});
    }
    void finish(int token,Runnable r){runOnUiThread(()->{if(token!=generation||isFinishing())return;busy=false;r.run();});}
    JSONArray relatedChannels(YouTubeApi api,JSONArray videos)throws Exception{
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(int i=0;i<videos.length();i++){JSONObject j=videos.optJSONObject(i);if(j!=null){String id=obj(j,"snippet").optString("channelId");if(!id.isEmpty())ids.add(id);}}
        return api.channels(String.join(",",ids));
    }
    void popular(){
        String reg=regions[region.getSelectedItemPosition()], cat=categories[category.getSelectedItemPosition()];
        run((token,api)->{
            JSONArray videos=api.popular(reg,cat);JSONArray channels=relatedChannels(api,videos);
            finish(token,()->results("Catálogo popular · "+reg,videos,channels,"Orden del catálogo oficial; no equivale a las búsquedas más populares."));
        });
    }
    void search(){
        String q=query.getText().toString().trim();if(q.isEmpty()||q.length()>500){query.setError("Escribe un tema de hasta 500 caracteres");return;}
        String reg=regions[region.getSelectedItemPosition()];String sort=new String[]{"viewCount","date","relevance"}[order.getSelectedItemPosition()];
        run((token,api)->{
            JSONArray videos=new JSONArray();int used=7;
            for(int days:new int[]{7,30,90}){used=days;videos=api.search(q,reg,days,sort);if(videos.length()>=10)break;}
            final int span=used;final JSONArray found=videos;JSONArray channels=relatedChannels(api,found);
            finish(token,()->{if(account==null)return;
                store.add(account.getId().toLowerCase(Locale.ROOT),q,reg,span,sort);
                results(q+" · "+reg+" · "+span+" días",found,channels,"Resultados de búsqueda; una muestra pequeña no prueba falta de demanda.");
                if(driveEnabled)inspectBackup();
            });
        });
    }
    void results(String context,JSONArray videos,JSONArray channels,String explanation){
        lastVideos=videos;lastChannels=channels;lastContext=context;
        shell("results",context);
        status.setText("Consultado: "+Instant.now().toString()+" · YouTube Data API");
        note(body,explanation);note(body,"Videos disponibles en esta consulta: "+videos.length()+".");
        if(videos.length()==0)note(body,"Evidencia insuficiente. Prueba sinónimos u otro mercado.");
        renderVideos(body,videos,10);
        action(body,"Ver canales de esta muestra",this::showChannels);
    }
    static JSONObject obj(JSONObject j,String k){JSONObject v=j.optJSONObject(k);return v==null?new JSONObject():v;}
    String metric(JSONObject s,String k){String n=s.optString(k,"");try{return NumberFormat.getIntegerInstance(new Locale("es","NI")).format(Long.parseLong(n));}catch(Exception e){return "No disponible";}}
    void renderVideos(LinearLayout target,JSONArray videos,int limit){
        for(int i=0;i<Math.min(videos.length(),limit);i++){
            JSONObject j=videos.optJSONObject(i);if(j==null)continue;
            JSONObject sn=obj(j,"snippet"),st=obj(j,"statistics"),cd=obj(j,"contentDetails");
            LinearLayout c=card(target);c.addView(text(sn.optString("title","Sin título"),17,FG,true));
            note(c,sn.optString("channelTitle")+" · "+sn.optString("publishedAt")+" · "+cd.optString("duration"));
            c.addView(text("Vistas  "+metric(st,"viewCount"),20,FG,true));
            note(c,"Likes: "+metric(st,"likeCount")+"   |   Comentarios: "+metric(st,"commentCount"));
            action(c,"Abrir en YouTube",()->open("https://www.youtube.com/watch?v="+Uri.encode(j.optString("id"))));
            action(c,"Ver canal",()->loadChannel(sn.optString("channelId")));
        }
    }
    void showChannels(){
        shell("channels","Canales · muestra de la última consulta");
        note(body,lastContext.isEmpty()?"Primero consulta el catálogo o busca un nicho.":"Canales relacionados con "+lastContext+". No es un ranking global.");
        for(int i=0;i<lastChannels.length();i++){
            JSONObject j=lastChannels.optJSONObject(i);if(j==null)continue;channelCard(body,j);
            action(body,"Ver publicaciones recientes",()->loadChannel(j.optString("id")));
        }
    }
    void channelCard(LinearLayout target,JSONObject j){
        JSONObject s=obj(j,"snippet"),t=obj(j,"statistics");LinearLayout c=card(target);
        c.addView(text(s.optString("title"),19,FG,true));
        note(c,"Creación: "+s.optString("publishedAt","No disponible"));
        note(c,"Suscriptores: "+(t.optBoolean("hiddenSubscriberCount")?"Ocultos":metric(t,"subscriberCount"))+" (contador público)");
        note(c,"Vistas totales: "+metric(t,"viewCount")+" · Videos públicos: "+metric(t,"videoCount"));
        action(c,"Abrir canal en YouTube",()->open("https://www.youtube.com/channel/"+Uri.encode(j.optString("id"))));
    }
    void loadChannel(String id){
        if(id.isEmpty())return;
        run((token,api)->{
            JSONArray a=api.channels(id);JSONObject ch=a.optJSONObject(0);
            if(ch==null){finish(token,()->status.setText("Canal no disponible."));return;}
            String playlist=obj(obj(ch,"contentDetails"),"relatedPlaylists").optString("uploads");
            JSONArray videos=api.uploads(playlist);
            finish(token,()->{shell("detail","Ficha de canal · estadísticas públicas actuales");status.setText("Consultado: "+Instant.now().toString());channelCard(body,ch);note(body,"Últimas publicaciones públicas identificadas (hasta 25)");renderVideos(body,videos,25);});
        });
    }
    void history(){
        shell("history","Historial personal · consultas guardadas");
        if(account!=null){
            action(body,"Sincronizar con Google Drive",this::authorizeDrive);
            JSONArray rows=store.read(account.getId().toLowerCase(Locale.ROOT));
            note(body,rows.length()+" consultas guardadas. Se guardan términos y filtros, no contadores de YouTube.");
            ArrayList<JSONObject> ordered=new ArrayList<>();for(int i=0;i<rows.length();i++)ordered.add(rows.optJSONObject(i));
            ordered.sort((x,y)->y.optString("at").compareTo(x.optString("at")));
            for(JSONObject r:ordered){
                LinearLayout item=card(body);item.addView(text(r.optString("query"),17,FG,true));
                note(item,r.optString("at")+" · "+r.optString("region")+" · "+r.optInt("days")+" días");
            }
        }
        LinearLayout c=card(body);c.addView(text("Todavía no hay series históricas",20,FG,true));
        note(c,"La API pública devuelve contadores actuales. No entrega el historial diario o mensual de terceros. No se muestran curvas inventadas.");
        note(c,"Pendiente: autorización de métricas y almacenamiento, recolector programado en servidor, importación legítima, gráficas temporales y detección de picos.");
        action(c,"Consultar políticas de métricas",()->open("https://developers.google.com/youtube/terms/derived-metrics-policy"));
    }
    void policies(){
        new AlertDialog.Builder(this).setTitle("Información y privacidad").setMessage("Inicia sesión con Google. Guarda tus términos de búsqueda y filtros en SQLite y, con tu autorización, en el almacenamiento privado de Drive. Las consultas públicas se realizan mediante el servicio central cuando esté configurado. No se guardan estadísticas externas históricas ni contraseñas. No incluye publicidad ni telemetría propia.").setPositiveButton("Políticas de YouTube",(d,w)->open("https://www.youtube.com/t/terms")).setNeutralButton("Privacidad de Google",(d,w)->open("https://policies.google.com/privacy")).setNegativeButton("Cerrar",null).show();
    }
    void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"No hay aplicación disponible para abrir el enlace.",Toast.LENGTH_SHORT).show();}}
}
