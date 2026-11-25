package com.lsnoglos.tubestrategia;

import android.app.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import android.os.*;
import android.content.Intent;
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
    private String key="";
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
        new AlertDialog.Builder(this).setTitle("Mi cuenta").setMessage(account.getId()+"\n\nLa identidad se mantiene en esta sesión. La sincronización de Drive y el servicio central de YouTube todavía no están conectados.")
            .setPositiveButton("Cerrar sesión",(d,w)->signOut()).setNeutralButton("Diagnóstico API (pruebas)",(d,w)->settings()).setNegativeButton("Volver",null).show();
    }
    void signOut(){
        account=null;key="";lastVideos=new JSONArray();lastChannels=new JSONArray();lastContext="";
        loginScreen("Sesión cerrada.");
        try{credentialManager.clearCredentialStateAsync(new ClearCredentialStateRequest(),null,getMainExecutor(),new CredentialManagerCallback<Void,ClearCredentialException>(){
            @Override public void onResult(Void result){}
            @Override public void onError(ClearCredentialException e){}
        });}catch(Exception ignored){}
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
        status=text(key.isEmpty()?"Fuente: YouTube · servicio central pendiente":"Fuente: YouTube · modo de diagnóstico personal",12,MUTED,false);root.addView(status);
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
    void settings(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Clave de YouTube Data API");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Conexión personal de prueba").setMessage("La clave se conserva únicamente en memoria durante esta sesión. No se envía a nuestro servidor ni se almacena. Restringe su uso a YouTube Data API v3 y revócala si se expone. No utilices claves de producción.").setView(input)
            .setPositiveButton("Configurar",(d,w)->{key=input.getText().toString().trim();lastVideos=new JSONArray();lastChannels=new JSONArray();lastContext="";home();})
            .setNeutralButton("Olvidar clave",(d,w)->{key="";lastVideos=new JSONArray();lastChannels=new JSONArray();lastContext="";home();}).setNegativeButton("Cancelar",null).show();
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
    boolean ready(){if(busy){status.setText("Hay una consulta en curso.");return false;}if(key.isEmpty()){status.setText("Servicio central de YouTube aún no configurado. El modo de diagnóstico es opcional.");return false;}return true;}
    void run(Work work){
        if(!ready())return;
        busy=true;int token=++generation;String sessionKey=key;status.setText("Consultando YouTube…");
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
        String q=query.getText().toString().trim();if(q.isEmpty()){query.setError("Escribe un tema");return;}
        String reg=regions[region.getSelectedItemPosition()];String sort=new String[]{"viewCount","date","relevance"}[order.getSelectedItemPosition()];
        run((token,api)->{
            JSONArray videos=new JSONArray();int used=7;
            for(int days:new int[]{7,30,90}){used=days;videos=api.search(q,reg,days,sort);if(videos.length()>=10)break;}
            final int span=used;final JSONArray found=videos;JSONArray channels=relatedChannels(api,found);
            finish(token,()->results(q+" · "+reg+" · "+span+" días",found,channels,"Resultados de búsqueda; una muestra pequeña no prueba falta de demanda."));
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
        shell("history","Time Machine · historial verificable");
        LinearLayout c=card(body);c.addView(text("Todavía no hay series históricas",20,FG,true));
        note(c,"La API pública devuelve contadores actuales. No entrega el historial diario o mensual de terceros. No se muestran curvas inventadas.");
        note(c,"Pendiente: autorización de métricas y almacenamiento, recolector programado en servidor, importación legítima, gráficas temporales y detección de picos.");
        action(c,"Consultar políticas de métricas",()->open("https://developers.google.com/youtube/terms/derived-metrics-policy"));
    }
    void policies(){
        new AlertDialog.Builder(this).setTitle("Información y privacidad").setMessage("Prototipo: inicia sesión mediante Google Credential Manager. Mantiene la identidad en memoria de sesión; todavía no existe un servidor que valide el token ni almacenamiento en Drive. El modo de diagnóstico utiliza una clave de sesión para consultas HTTPS a Google. No incluye publicidad ni telemetría propia.").setPositiveButton("Políticas de YouTube",(d,w)->open("https://www.youtube.com/t/terms")).setNeutralButton("Privacidad de Google",(d,w)->open("https://policies.google.com/privacy")).setNegativeButton("Cerrar",null).show();
    }
    void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"No hay aplicación disponible para abrir el enlace.",Toast.LENGTH_SHORT).show();}}
}
