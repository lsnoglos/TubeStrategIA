package com.lsnoglos.tubestrategia;

import android.app.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import android.os.*;
import android.content.Intent;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
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
    private static final int BG=0xff12345a, PANEL=0xff163e62, FG=0xfff8fbff, MUTED=0xffd2e6f5, RED=0xffef476f;
    private static final int PINK=0xffec087b, PURPLE=0xff7545c7, BLUE=0xff176bb5, CYAN=0xff58e4f1, GREEN=0xffb5f36a, YELLOW=0xffffd66a;
    private Dialog loadingDialog;
    private TextView loadingTitle,loadingDetail;
    private Instant lastSync;
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
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
    private final String[] countryNames={"Nicaragua","México","Estados Unidos","España","Colombia","Argentina","Brasil"};
    private final String[] categories={"0","10","20","1"};
    private static final String[][] THEMES={
        {"Creadores de contenido y vlogs","creadores de contenido | vlog | youtuber"},
        {"Streaming y directos (tema)","streaming | streamer | directo | livestream"},
        {"Nacional y cultura local","@country"},
        {"Comida, recetas y gastronomía","recetas | comida | cocina | gastronomía"},
        {"Videos creados con IA","video generado con IA | AI generated video | historias con IA"},
        {"Inteligencia artificial y herramientas","inteligencia artificial | herramientas IA | AI tools"},
        {"Historias animadas y frutiseries","historias animadas | frutiseries | fruit animation"},
        {"Humor, memes y entretenimiento","humor | memes | comedia | funny"},
        {"Retos y experimentos","retos | experimentos | challenge"},
        {"Historias, misterio y terror","historias de terror | misterio | relatos"},
        {"Animales y mascotas","gatos | perros | animales | mascotas"},
        {"Tecnología y gadgets","tecnología | gadgets | smartphones"},
        {"Tutoriales y educación","tutorial | cómo hacer | aprendizaje"},
        {"Programación y desarrollo","programación | desarrollo software | coding"},
        {"Negocios y emprendimiento","emprendimiento | negocios | small business"},
        {"Finanzas y economía","finanzas personales | economía | inversión"},
        {"Deportes","deportes | fútbol | highlights"},
        {"Salud, ejercicio y bienestar","ejercicio | fitness | bienestar"},
        {"Viajes, turismo y aventura","viajes | turismo | adventure"},
        {"Belleza, moda y estilo","maquillaje | moda | belleza"},
        {"Arte, diseño y manualidades","arte | diseño | manualidades | DIY"},
        {"Ciencia y curiosidades","ciencia | curiosidades | science"},
        {"Documentales e historia","documental | historia | documentary"},
        {"Noticias y actualidad","noticias | actualidad | news"},
        {"Podcasts y entrevistas","podcast | entrevista"},
        {"Música relajante y ASMR","música relajante | ASMR | sleep music"},
        {"Motivación y desarrollo personal","motivación | desarrollo personal"},
        {"Automóviles y motocicletas","autos | motocicletas | car reviews"},
        {"Hogar y jardinería","hogar | jardinería | decoración"},
        {"Reseñas y comparaciones","reseña | review | comparativa"},
        {"Cultura digital y redes sociales","redes sociales | cultura digital | tendencias internet"}
    };
    String[] catalogOptions(){
        String[] labels=new String[4+THEMES.length];
        labels[0]="General · catálogo oficial";labels[1]="Música · catálogo oficial";
        labels[2]="Videojuegos · catálogo oficial";labels[3]="Cine y animación · catálogo oficial";
        for(int i=0;i<THEMES.length;i++)labels[i+4]=THEMES[i][0]+" · tema";
        return labels;
    }
    interface Work { void run(int token,YouTubeApi api) throws Exception; }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        credentialManager=CredentialManager.create(this);
        store=new HistoryStore(this);
        loginScreen("");
    }
    @Override protected void onDestroy(){generation++;hideLoading();uiHandler.removeCallbacksAndMessages(null);if(signInCancellation!=null)signInCancellation.cancel();pool.shutdownNow();super.onDestroy();}
    void loginScreen(String message){
        generation++;busy=false;current="login";
        root=column();root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(24),dp(24),dp(24));
        ScrollView loginScroll=new ScrollView(this);loginScroll.setFillViewport(true);loginScroll.setBackground(gradient(BG,0xff147b98,0));loginScroll.addView(root);setContentView(loginScroll);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);root.addView(logo,new LinearLayout.LayoutParams(dp(88),dp(88)));
        TextView title=text("TubeStrategIA",30,FG,true);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView sub=text("Inteligencia de mercado para YouTube",15,MUTED,false);sub.setGravity(Gravity.CENTER);root.addView(sub);
        TextView intro=text("Explora tendencias y encuentra tu próxima oportunidad.",18,FG,true);intro.setGravity(Gravity.CENTER);intro.setPadding(0,dp(28),0,dp(20));root.addView(intro);
        Button google=button("Continuar con Google",this::signIn);google.setTextColor(0xff202124);google.setBackground(bg(0xfff8fafc,14));root.addView(google,new LinearLayout.LayoutParams(-1,dp(52)));
        status=text(message.isEmpty()?"Accede con tu propia cuenta de Google.":message,13,MUTED,false);status.setGravity(Gravity.CENTER);root.addView(status);
        LinearLayout trust=card(root);
        trust.addView(text("Tus datos, bajo tu control",17,CYAN,true));
        note(trust,"Inicia con Google. Guardamos tus consultas en el dispositivo y, si lo autorizas, en el espacio privado de TubeStrategIA en Drive. No solicitamos acceso general a tus documentos.");
        note(trust,"Versión de prueba: Google puede mostrar una advertencia de aplicación no verificada. Continúa solo si reconoces este proyecto y sus permisos.");
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
        LinearLayout content=column();content.setPadding(dp(20),dp(6),dp(20),dp(8));
        String name=account.getDisplayName();
        content.addView(text(name==null||name.isEmpty()?"Cuenta Google":name,20,FG,true));
        note(content,account.getId());
        content.addView(text("Seguridad y respaldo",17,CYAN,true));
        content.addView(text(driveEnabled?"Drive · sincronización activa":syncBusy?"Drive · verificando respaldo":"Drive · pendiente o pausado",15,driveEnabled?GREEN:YELLOW,true));
        note(content,"Última verificación en esta sesión: "+(lastSync==null?"Sin confirmar":localDate(lastSync.toString())));
        note(content,"YouTube: "+(BuildConfig.BACKEND_URL.isEmpty()?"Servicio pendiente":"Servicio central configurado · cuota compartida"));
        note(content,"Permisos: identidad Google y, si lo autorizas, espacio privado de la app en Drive. No acceso general a tus archivos. La clave de YouTube no está en la APK.");
        note(content,"Aplicación en pruebas; verificación oficial pendiente.");
        new AlertDialog.Builder(this).setTitle("Mi cuenta").setView(content)
            .setPositiveButton("Cerrar sesión",(d,w)->signOut()).setNeutralButton("Sincronizar Drive",(d,w)->authorizeDrive()).setNegativeButton("Volver",null).show();
    }
    void signOut(){
        if(syncBusy){toast("Finaliza o cancela la sincronización primero.");return;}
        account=null;driveToken=null;remoteBaseline=null;lastSync=null;driveEnabled=false;lastVideos=new JSONArray();lastChannels=new JSONArray();lastContext="";
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
        showLoading("Verificando Google Drive","Comparando tus consultas locales con el respaldo.");
        pool.execute(()->{
            try{
                DriveBackup drive=new DriveBackup(access,owner);DriveBackup.Snapshot remote=drive.read();
                JSONArray local=store.read(owner);
                runOnUiThread(()->{
                    if(account!=session||isDestroyed())return;
                    if(local.toString().equals(remote.entries.toString())){
                        remoteBaseline=remote.signature();lastSync=Instant.now();driveEnabled=true;syncBusy=false;hideLoading();toast("Respaldo y dispositivo coinciden.");return;
                    }
                    if(remote.id==null||(auto&&Objects.equals(baseline,remote.signature()))){
                        applyBackup(session,drive,remote,false);return;
                    }
                    hideLoading();
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
        showLoading("Sincronizando historial","Guardando y verificando el respaldo privado.");
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
                runOnUiThread(()->{if(account==session&&!isDestroyed()){remoteBaseline=saved.signature();lastSync=Instant.now();driveEnabled=true;syncBusy=false;hideLoading();toast("Historial sincronizado con Drive.");if("history".equals(current))history();}});
            }catch(Exception e){syncError(session);}
        });
    }
    void syncError(GoogleIdTokenCredential session){
        runOnUiThread(()->{if(account==session&&!isDestroyed()){syncBusy=false;driveEnabled=false;driveToken=null;hideLoading();toast("No se sincronizó. Tus datos locales se conservan. Reintenta desde Mi cuenta.");}});
    }
    GradientDrawable bg(int c,int radius){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(radius));return d;}
    GradientDrawable gradient(int a,int b,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{a,b});d.setCornerRadius(dp(radius));return d;
    }
    String localDate(String date){
        try{return java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm",new Locale("es","NI")).withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(date));}
        catch(Exception e){return date.isEmpty()?"No disponible":date;}
    }
    String duration(String value){
        try{long s=java.time.Duration.parse(value).getSeconds();return s>=3600?String.format(Locale.ROOT,"%d h %02d min",s/3600,(s%3600)/60):String.format(Locale.ROOT,"%d min %02d s",s/60,s%60);}
        catch(Exception e){return "Duración no disponible";}
    }
    void showLoading(String title,String detail){
        hideLoading();if(isFinishing()||isDestroyed())return;
        Dialog d=new Dialog(this);loadingDialog=d;d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setCancelable(false);
        LinearLayout panel=column();panel.setGravity(Gravity.CENTER);panel.setPadding(dp(24),dp(24),dp(24),dp(20));panel.setBackground(gradient(0xff15345c,0xff21698a,24));
        WaveLoader wave=new WaveLoader(this);panel.addView(wave,new LinearLayout.LayoutParams(-1,dp(96)));
        loadingTitle=text(title,21,FG,true);loadingTitle.setGravity(Gravity.CENTER);panel.addView(loadingTitle);
        loadingDetail=text(detail,14,MUTED,false);loadingDetail.setGravity(Gravity.CENTER);panel.addView(loadingDetail);
        TextView info=text("Conexión en curso",12,CYAN,true);info.setGravity(Gravity.CENTER);panel.addView(info);
        d.setContentView(panel);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.65f);w.setLayout(Math.min(dp(340),getResources().getDisplayMetrics().widthPixels-dp(32)),-2);}
    }
    void hideLoading(){if(loadingDialog!=null){loadingDialog.dismiss();loadingDialog=null;}loadingTitle=null;loadingDetail=null;}
    void stage(int token,String title,String detail){runOnUiThread(()->{if(token==generation&&loadingDialog!=null&&loadingDialog.isShowing()){loadingTitle.setText(title);loadingDetail.setText(detail);}});}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(4),0,dp(4));return t;
    }
    void note(LinearLayout l,String s){l.addView(text(s,13,MUTED,false));}
    Button button(String label,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(FG);b.setTextSize(13);b.setMinHeight(dp(48));b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setBackgroundTintList(null);
        boolean primary=label.startsWith("Buscar")||label.startsWith("Consultar catálogo")||label.startsWith("Explorar")||label.startsWith("Sincronizar");
        GradientDrawable shape=primary?gradient(0xffbc056e,0xff4451b9,12):bg(0xff24577c,12);
        if(!primary)shape.setStroke(dp(1),0xff387399);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40ffffff),shape,null));b.setOnClickListener(v->action.run());return b;
    }
    void action(LinearLayout l,String title,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(7);l.addView(button(title,action),p);}
    LinearLayout card(LinearLayout target){
        LinearLayout l=column();GradientDrawable shape=bg(PANEL,18);shape.setStroke(dp(1),0xff387399);l.setBackground(shape);l.setPadding(dp(15),dp(13),dp(15),dp(13));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);target.addView(l,p);return l;
    }
    void shell(String name,String subtitle){
        generation++;busy=false;current=name;
        root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG,0xff146b86}));root.setPadding(dp(12),dp(8),dp(12),dp(8));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(gradient(0xffbc056e,0xff176bb5,16));header.setPadding(dp(12),dp(5),dp(8),dp(5));
        TextView title=text("TubeStrategIA",23,FG,true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("Mi cuenta",this::accountMenu));root.addView(header);
        note(root,subtitle);
        status=text(BuildConfig.BACKEND_URL.isEmpty()?"YouTube · servicio central pendiente":"YouTube · cuota compartida de la aplicación",12,MUTED,false);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);
        String[] names={"Inicio","Buscar","Canales","Historial"};
        Runnable[] acts={this::home,this::explore,this::showChannels,this::history};
        String[] keys={"home","search","channels","history"};
        for(int i=0;i<names.length;i++){Button b=button(names[i],acts[i]);b.setTextSize(12);
            boolean selected=name.equals(keys[i])||(i==1&&name.equals("results"))||(i==2&&name.equals("detail"));
            b.setBackground(selected?gradient(0xffbc056e,0xff4451b9,12):bg(0xff153b60,12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(5),dp(2),0);nav.addView(b,p);}
        root.addView(nav);
    }
    void home(){
        shell("home","Panorama · información pública y actual");
        LinearLayout intro=card(body);
        intro.setBackground(gradient(0xff7d176e,0xff225d9e,18));intro.addView(text("Observa el mercado",23,FG,true));
        note(intro,"Explora publicaciones de YouTube por región o investiga un tema específico. Las listas son muestras de la API, no un censo del mercado.");
        LinearLayout controls=card(body);
        note(controls,"Región del catálogo");
        region=spinner(controls,new String[]{"Nicaragua","México","Estados Unidos","España","Colombia","Argentina","Brasil"});
        note(controls,"Categoría del catálogo");
        category=spinner(controls,catalogOptions());
        TextView catalogHint=text("",12,MUTED,false);controls.addView(catalogHint);
        Button catalogButton=button("Consultar catálogo popular",this::popular);
        LinearLayout.LayoutParams catalogParams=new LinearLayout.LayoutParams(-1,-2);catalogParams.topMargin=dp(7);controls.addView(catalogButton,catalogParams);
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                boolean official=position<categories.length;
                catalogButton.setText(official?"Consultar catálogo popular":"Buscar videos del tema");
                catalogHint.setText(official?"Lista oficial de YouTube disponible para esta región.":"Búsqueda temática por palabras clave, no clasificación oficial. Consume 1–3 llamadas de búsqueda según resultados.");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        action(controls,"Explorar un nicho",this::explore);
        LinearLayout info=card(body);
        info.addView(text("Demanda, crecimiento y sostenibilidad",17,CYAN,true));
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
        busy=true;int token=++generation;String sessionKey=account.getIdToken();status.setText("Consultando YouTube…");showLoading("Consultando YouTube","Conectando con el servicio central…");
        pool.execute(()->{try{work.run(token,new YouTubeApi(sessionKey));}catch(Exception e){
            runOnUiThread(()->{if(token==generation&&!isDestroyed()){busy=false;hideLoading();String message="No se pudo consultar. "+(e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("YouTube HTTP")?e.getMessage():"Comprueba Internet y configuración.");status.setText(message);new AlertDialog.Builder(this).setTitle("Consulta no completada").setMessage(message).setPositiveButton("Entendido",null).show();}});
        }});
    }
    void finish(int token,Runnable r){runOnUiThread(()->{if(token!=generation||isFinishing()||isDestroyed())return;busy=false;hideLoading();r.run();});}
    JSONArray relatedChannels(YouTubeApi api,JSONArray videos)throws Exception{
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(int i=0;i<videos.length();i++){JSONObject j=videos.optJSONObject(i);if(j!=null){String id=obj(j,"snippet").optString("channelId");if(!id.isEmpty())ids.add(id);}}
        return api.channels(String.join(",",ids));
    }
    void popular(){
        int regionIndex=region.getSelectedItemPosition(), selected=category.getSelectedItemPosition();
        if(regionIndex<0||selected<0)return;
        String reg=regions[regionIndex];
        if(selected>=categories.length){
            String[] theme=THEMES[selected-categories.length];
            String q="@country".equals(theme[1])?countryNames[regionIndex]:theme[1];
            String explanation="Muestra temática por palabras clave; no es una categoría oficial ni un ranking de búsquedas.";
            if("@country".equals(theme[1]))explanation+=" El país se utiliza como término de búsqueda y no verifica el origen del creador.";
            if(selected==5)explanation+=" Incluye contenido relacionado con streaming, no necesariamente transmisiones activas.";
            if(selected==8)explanation+=" La búsqueda no confirma que cada video haya sido generado con IA.";
            searchTopic(q,reg,"viewCount",theme[0],explanation);
            return;
        }
        String cat=categories[selected];
        run((token,api)->{
            stage(token,"Consultando catálogo","Obteniendo videos de la región seleccionada.");JSONArray videos=api.popular(reg,cat);stage(token,"Cargando canales","Recuperando datos públicos de los creadores.");JSONArray channels=relatedChannels(api,videos);
            finish(token,()->results("Catálogo popular · "+reg,videos,channels,"Orden del catálogo oficial; no equivale a las búsquedas más populares."));
        });
    }
    void search(){
        String q=query.getText().toString().trim();if(q.isEmpty()||q.length()>500){query.setError("Escribe un tema de hasta 500 caracteres");return;}
        String reg=regions[region.getSelectedItemPosition()];String sort=new String[]{"viewCount","date","relevance"}[order.getSelectedItemPosition()];
        searchTopic(q,reg,sort,q,"Resultados de búsqueda; una muestra pequeña no prueba falta de demanda.");
    }
    void searchTopic(String q,String reg,String sort,String label,String explanation){
        run((token,api)->{
            JSONArray videos=new JSONArray();int used=7;
            for(int days:new int[]{7,30,90}){used=days;stage(token,"Buscando videos","Explorando publicaciones de los últimos "+days+" días.");videos=api.search(q,reg,days,sort);if(videos.length()>=10)break;}
            final int span=used;final JSONArray found=videos;stage(token,"Cargando canales","Recuperando información pública de los creadores.");JSONArray channels=relatedChannels(api,found);
            finish(token,()->{if(account==null)return;
                store.add(account.getId().toLowerCase(Locale.ROOT),q,reg,span,sort);
                results(label+" · "+reg+" · "+span+" días",found,channels,explanation);
                if(driveEnabled)inspectBackup();
            });
        });
    }
    void results(String context,JSONArray videos,JSONArray channels,String explanation){
        lastVideos=videos;lastChannels=channels;lastContext=context;
        shell("results",context);
        status.setText("Consultado: "+localDate(Instant.now().toString())+" · YouTube Data API");
        note(body,explanation);note(body,"Mostrando "+Math.min(10,videos.length())+" de "+videos.length()+" resultados obtenidos.");
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
            LinearLayout c=card(target);
            c.addView(text(String.format(Locale.ROOT,"%02d  ·  VIDEO",i+1),11,CYAN,true));
            LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.TOP);
            LinearLayout copy=column();copy.addView(text(sn.optString("title","Sin título"),19,FG,true));
            note(copy,sn.optString("channelTitle")+" · "+localDate(sn.optString("publishedAt"))+" · "+duration(cd.optString("duration")));
            top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            FrameLayout thumb=videoThumbnail(sn,j.optString("id"));
            int widthDp=getResources().getConfiguration().screenWidthDp;
            int thumbWidth=widthDp<360?92:112;
            LinearLayout.LayoutParams thumbParams=new LinearLayout.LayoutParams(dp(thumbWidth),dp(thumbWidth*9/16));thumbParams.leftMargin=dp(9);
            top.addView(thumb,thumbParams);c.addView(top);
            metricTile(c,"VISUALIZACIONES",metric(st,"viewCount"),CYAN,0xff194b71);
            LinearLayout stats=new LinearLayout(this);addMetric(stats,"ME GUSTA",metric(st,"likeCount"),0xffff9ed0,0xff60345f);addMetric(stats,"COMENTARIOS",metric(st,"commentCount"),YELLOW,0xff534a47);c.addView(stats);
            action(c,"Abrir en YouTube",()->open("https://www.youtube.com/watch?v="+Uri.encode(j.optString("id"))));
            action(c,"Ver canal",()->loadChannel(sn.optString("channelId")));
        }
    }
    FrameLayout videoThumbnail(JSONObject snippet,String videoId){
        FrameLayout frame=new FrameLayout(this);frame.setBackground(bg(0xff24577c,10));frame.setClipToOutline(true);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageResource(R.drawable.video_placeholder);
        frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        JSONObject thumbnails=obj(snippet,"thumbnails");String url="";
        for(String size:new String[]{"medium","high","default"}){
            String candidate=obj(thumbnails,size).optString("url","");
            Uri uri=Uri.parse(candidate);String host=uri.getHost();
            if("https".equalsIgnoreCase(uri.getScheme())&&host!=null&&(host.equals("ytimg.com")||host.endsWith(".ytimg.com"))){url=candidate;break;}
        }
        if(!url.isEmpty()){
            Glide.with(this).load(url).centerCrop().placeholder(R.drawable.video_placeholder).error(R.drawable.video_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE).into(image);
        }
        frame.setContentDescription("Abrir video en YouTube: "+snippet.optString("title",""));
        if(!videoId.isEmpty())frame.setOnClickListener(v->open("https://www.youtube.com/watch?v="+Uri.encode(videoId)));
        return frame;
    }
    void metricTile(LinearLayout parent,String label,String value,int accent,int fill){
        LinearLayout tile=column();tile.setPadding(dp(10),dp(8),dp(10),dp(8));tile.setBackground(bg(fill,12));
        tile.addView(text(label,11,accent,true));tile.addView(text(value,23,FG,true));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(3));parent.addView(tile,p);
    }
    void addMetric(LinearLayout row,String label,String value,int accent,int fill){
        LinearLayout tile=column();tile.setPadding(dp(9),dp(7),dp(9),dp(7));tile.setBackground(bg(fill,12));tile.addView(text(label,10,accent,true));tile.addView(text(value,17,FG,true));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(4),dp(2),dp(3));row.addView(tile,p);
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
        note(c,"Creación: "+localDate(s.optString("publishedAt")));
        metricTile(c,"SUSCRIPTORES · CONTADOR PÚBLICO",t.optBoolean("hiddenSubscriberCount")?"Ocultos":metric(t,"subscriberCount"),GREEN,0xff294e59);
        LinearLayout stats=new LinearLayout(this);addMetric(stats,"VISTAS TOTALES",metric(t,"viewCount"),CYAN,0xff194b71);addMetric(stats,"VIDEOS PÚBLICOS",metric(t,"videoCount"),YELLOW,0xff534a47);c.addView(stats);
        action(c,"Abrir canal en YouTube",()->open("https://www.youtube.com/channel/"+Uri.encode(j.optString("id"))));
    }
    void loadChannel(String id){
        if(id.isEmpty())return;
        run((token,api)->{
            stage(token,"Consultando canal","Obteniendo estadísticas públicas.");JSONArray a=api.channels(id);JSONObject ch=a.optJSONObject(0);
            if(ch==null){finish(token,()->status.setText("Canal no disponible."));return;}
            String playlist=obj(obj(ch,"contentDetails"),"relatedPlaylists").optString("uploads");
            stage(token,"Cargando publicaciones","Obteniendo videos recientes del canal.");JSONArray videos=api.uploads(playlist);
            finish(token,()->{shell("detail","Ficha de canal · estadísticas públicas actuales");status.setText("Consultado: "+localDate(Instant.now().toString()));channelCard(body,ch);note(body,"Últimas publicaciones públicas identificadas (hasta 25)");renderVideos(body,videos,25);});
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
                note(item,localDate(r.optString("at"))+" · "+r.optString("region")+" · "+r.optInt("days")+" días");
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
