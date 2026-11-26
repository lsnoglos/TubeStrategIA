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
    private static final int BG=0xffeff5fb, PANEL=0xffffffff, FG=0xff18324c, MUTED=0xff4c647b, RED=0xffbd2854;
    private static final int PINK=0xffbd176f, PURPLE=0xff7545c7, BLUE=0xff176bb5, CYAN=0xff086e89, GREEN=0xff287443, YELLOW=0xff86570e;
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
    private static final class VideoResult {
        final String context,explanation;final JSONArray videos;final Instant observed;
        VideoResult(String c,String e,JSONArray v){context=c;explanation=e;videos=v;observed=Instant.now();}
    }
    private VideoResult homeResult,catalogResult,nicheResult;
    private int homeRegion=0,catalogRegion=0,catalogCategory=0,nicheRegion=0,nicheOrder=0;
    private String nicheQuery="";
    private boolean catalogEdit=true,nicheEdit=true,homeAutoAttempted=false;
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
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        credentialManager=CredentialManager.create(this);
        store=new HistoryStore(this);
        loginScreen("");
    }
    @Override protected void onDestroy(){generation++;hideLoading();uiHandler.removeCallbacksAndMessages(null);if(signInCancellation!=null)signInCancellation.cancel();pool.shutdownNow();super.onDestroy();}
    void loginScreen(String message){
        generation++;busy=false;current="login";
        root=column();root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(24),dp(24),dp(24));
        ScrollView loginScroll=new ScrollView(this);loginScroll.setFillViewport(true);loginScroll.setBackground(gradient(BG,0xffe5f3f9,0));loginScroll.addView(root);setContentView(loginScroll);
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
        account=null;driveToken=null;remoteBaseline=null;lastSync=null;driveEnabled=false;homeResult=null;catalogResult=null;nicheResult=null;homeAutoAttempted=false;homeRegion=0;catalogRegion=0;catalogCategory=0;nicheRegion=0;nicheOrder=0;nicheQuery="";catalogEdit=true;nicheEdit=true;
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
        LinearLayout panel=column();panel.setGravity(Gravity.CENTER);panel.setPadding(dp(24),dp(24),dp(24),dp(20));panel.setBackground(bg(0xfff5faff,24));
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
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(FG);b.setTextSize(13);
        b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setMinimumWidth(0);b.setMinWidth(0);
        b.setPadding(dp(7),dp(5),dp(7),dp(5));b.setBackgroundTintList(null);
        boolean primary=label.startsWith("Buscar")||label.startsWith("Consultar")||label.startsWith("Actualizar")||label.startsWith("Sincronizar");
        GradientDrawable shape=primary?gradient(0xffbc056e,0xff4451b9,11):bg(0xffe9f1fa,11);
        if(primary)b.setTextColor(Color.WHITE);else shape.setStroke(dp(1),0xffcbd9e9);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x24000000),shape,null));b.setOnClickListener(v->action.run());return b;
    }
    void action(LinearLayout l,String title,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(5);l.addView(button(title,action),p);}
    void rowAction(LinearLayout row,String title,Runnable action){
        Button b=button(title,action);b.setTextSize(12);b.setMinHeight(dp(40));b.setMinimumHeight(dp(40));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(3),dp(2),0);row.addView(b,p);
    }
    LinearLayout card(LinearLayout target){
        LinearLayout l=column();GradientDrawable shape=bg(PANEL,14);shape.setStroke(dp(1),0xffd6e1ed);l.setBackground(shape);l.setPadding(dp(11),dp(9),dp(11),dp(9));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);target.addView(l,p);return l;
    }
    void rememberFilters(){
        if("catalog".equals(current)&&catalogEdit&&region!=null&&category!=null){catalogRegion=region.getSelectedItemPosition();catalogCategory=category.getSelectedItemPosition();}
        if("niche".equals(current)&&nicheEdit&&region!=null&&order!=null&&query!=null){nicheRegion=region.getSelectedItemPosition();nicheOrder=order.getSelectedItemPosition();nicheQuery=query.getText().toString();}
        if("home".equals(current)&&region!=null)homeRegion=region.getSelectedItemPosition();
    }
    void shell(String name,String subtitle){
        generation++;busy=false;current=name;region=null;category=null;order=null;query=null;
        root=column();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG,0xffe8f4f8}));root.setPadding(dp(10),dp(7),dp(10),dp(6));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(gradient(0xffbc056e,0xff176bb5,14));header.setPadding(dp(10),dp(4),dp(5),dp(4));
        TextView title=text("TubeStrategIA",20,Color.WHITE,true);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button about=button("ⓘ",this::about);about.setContentDescription("Acerca de TubeStrategIA");about.setTextColor(Color.WHITE);about.setBackground(bg(0x30ffffff,10));header.addView(about,new LinearLayout.LayoutParams(dp(40),dp(44)));
        Button accountButton=button("Mi cuenta",this::accountMenu);accountButton.setTextColor(Color.WHITE);accountButton.setBackground(bg(0x30ffffff,10));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(44));ap.leftMargin=dp(3);header.addView(accountButton,ap);root.addView(header);
        note(root,subtitle);
        status=text("",12,MUTED,false);status.setVisibility(View.GONE);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);
        String[] names={"Inicio","Catálogo","Nichos","Historial"};
        Runnable[] acts={this::home,this::catalog,this::explore,this::history};
        String[] keys={"home","catalog","niche","history"};
        for(int i=0;i<names.length;i++){final Runnable go=acts[i];Button b=button(names[i],()->{rememberFilters();go.run();});b.setTextSize(12);
            boolean selected=name.equals(keys[i]);b.setTextColor(selected?Color.WHITE:FG);b.setBackground(selected?gradient(0xffbc056e,0xff4451b9,11):bg(0xffdce8f4,11));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(4),dp(2),0);nav.addView(b,p);}
        root.addView(nav);
    }
    void setStatus(String value){if(status!=null){status.setText(value);status.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}}
    void about(){
        LinearLayout c=column();c.setPadding(dp(16),dp(8),dp(16),dp(12));
        c.addView(text("TubeStrategIA · Inteligencia de mercado",18,FG,true));
        note(c,"Inicio resume la muestra popular regional de YouTube. Catálogo explora listas oficiales y temas predefinidos; Nichos permite investigar cualquier palabra clave.");
        note(c,"Popularidad acumulada no equivale a demanda de búsqueda, crecimiento reciente ni interés de las últimas 24 horas.");
        note(c,"No tenemos series históricas de terceros ni autorización para métricas avanzadas. No inventamos curvas ni puntuaciones. Time Machine se habilitará cuando existan mediciones y permisos.");
        note(c,"Fuente: YouTube Data API. Cuota compartida; el historial personal se guarda en SQLite y, si lo autorizas, en Drive.");
        action(c,"Privacidad y condiciones",this::policies);
        popup("Acerca de",c);
    }
    Spinner spinner(LinearLayout l,String[] options){
        Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);l.addView(s);return s;
    }
    void home(){
        shell("home","Panorama de YouTube");
        LinearLayout controls=card(body);controls.addView(text("Top 3 · panorama regional",19,FG,true));
        note(controls,"Región");region=spinner(controls,countryNames);region.setSelection(Math.max(0,homeRegion));
        action(controls,"Actualizar panorama",this::refreshHome);
        if(homeResult!=null){
            note(body,homeResult.context+" · "+localDate(homeResult.observed.toString()));
            note(body,"Contadores acumulados al consultar. Top 3 dentro de "+homeResult.videos.length()+" videos del catálogo popular, no de todo YouTube ni de las últimas 24 horas.");
            renderTop("Más vistos","viewCount",CYAN,homeResult);
            renderTop("Más me gusta","likeCount",PINK,homeResult);
            renderTop("Más comentados","commentCount",YELLOW,homeResult);
        }else note(body,"Actualiza para conocer los videos destacados de la región.");
        if(!homeAutoAttempted&&homeResult==null&&account!=null){final int screen=generation;uiHandler.postDelayed(()->autoHome(screen),350);}
    }
    void autoHome(int screen){
        if(isDestroyed()||isFinishing()||account==null||screen!=generation||!"home".equals(current)||homeAutoAttempted)return;
        if(syncBusy){uiHandler.postDelayed(()->autoHome(screen),500);return;}
        if(busy)return;homeAutoAttempted=true;refreshHome();
    }
    void refreshHome(){
        if(region==null||region.getSelectedItemPosition()<0)return;
        if(!ready())return;homeAutoAttempted=true;homeRegion=region.getSelectedItemPosition();String reg=regions[homeRegion];
        run((token,api)->{
            stage(token,"Actualizando panorama","Consultando el catálogo popular de "+reg+".");
            JSONArray v=api.popular(reg,"0");
            finish(token,()->{homeResult=new VideoResult("Región "+reg,"",v);home();});
        });
    }
    void renderTop(String title,String key,int accent,VideoResult result){
        LinearLayout group=card(body);group.addView(text(title,17,accent,true));
        ArrayList<JSONObject> ranked=new ArrayList<>();
        for(int i=0;i<result.videos.length();i++){JSONObject j=result.videos.optJSONObject(i);if(j!=null&&obj(j,"statistics").optLong(key,-1)>=0)ranked.add(j);}
        ranked.sort((a,b)->Long.compare(obj(b,"statistics").optLong(key),obj(a,"statistics").optLong(key)));
        if(ranked.isEmpty())note(group,"No hay contadores disponibles.");
        for(int i=0;i<Math.min(3,ranked.size());i++){
            JSONObject j=ranked.get(i),sn=obj(j,"snippet");
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
            TextView rank=text(String.valueOf(i+1),17,accent,true);line.addView(rank,new LinearLayout.LayoutParams(dp(22),-2));
            FrameLayout thumb=videoThumbnail(sn,j.optString("id"));line.addView(thumb,new LinearLayout.LayoutParams(dp(66),dp(38)));
            LinearLayout copy=column();copy.setPadding(dp(8),0,0,0);TextView t=text(sn.optString("title","Sin título"),13,FG,true);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(t);
            copy.addView(text(metric(obj(j,"statistics"),key),15,accent,true));line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            Button more=button("Datos",()->showStatistics(j,result.observed));more.setTextSize(11);line.addView(more,new LinearLayout.LayoutParams(dp(54),dp(44)));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(5);group.addView(line,p);
        }
    }
    void catalog(){
        shell("catalog","Catálogo · explora categorías y temas");
        LinearLayout controls=card(body);
        if(!catalogEdit&&catalogResult!=null){
            controls.addView(text(catalogResult.context,16,FG,true));action(controls,"Cambiar filtros",()->{catalogEdit=true;catalog();});
        }else{
            note(controls,"Región del catálogo");region=spinner(controls,countryNames);region.setSelection(Math.max(0,catalogRegion));
            note(controls,"Categoría o tema");category=spinner(controls,catalogOptions());category.setSelection(Math.max(0,catalogCategory));
            TextView hint=text("",12,MUTED,false);controls.addView(hint);Button submit=button("Consultar catálogo",this::popular);controls.addView(submit);
            category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){boolean official=position<categories.length;submit.setText(official?"Consultar catálogo":"Buscar videos del tema");hint.setText(official?"Lista oficial disponible para esta región.":"Búsqueda temática · 1–3 llamadas según resultados.");}
                @Override public void onNothingSelected(AdapterView<?> p){}
            });
        }
        if(catalogResult!=null)renderResult(catalogResult);
    }
    void explore(){
        shell("niche","Nichos · investiga cualquier palabra clave");
        LinearLayout l=card(body);
        if(!nicheEdit&&nicheResult!=null){
            l.addView(text(nicheResult.context,16,FG,true));action(l,"Cambiar filtros",()->{nicheEdit=true;explore();});
        }else{
            note(l,"Palabra clave");query=new EditText(this);query.setSingleLine(true);query.setHint("Gatos, IA, frutiseries...");query.setTextColor(FG);query.setHintTextColor(MUTED);query.setText(nicheQuery);l.addView(query);
            note(l,"Región de disponibilidad");region=spinner(l,countryNames);region.setSelection(Math.max(0,nicheRegion));
            note(l,"Orden de resultados");order=spinner(l,new String[]{"Más visualizaciones","Más recientes","Relevancia"});order.setSelection(Math.max(0,nicheOrder));
            action(l,"Buscar videos del nicho",this::search);
            note(l,"Búsqueda adaptativa: 7, 30 y 90 días si faltan resultados.");
        }
        if(nicheResult!=null)renderResult(nicheResult);
    }
    boolean ready(){if(busy){setStatus("Hay una consulta en curso.");return false;}if(account==null){loginScreen("");return false;}if(syncBusy){setStatus("Finaliza la sincronización primero.");return false;}if(BuildConfig.BACKEND_URL.isEmpty()){setStatus("El administrador aún no ha desplegado el servicio central de YouTube.");return false;}return true;}
    void run(Work work){
        if(!ready())return;
        busy=true;int token=++generation;String sessionKey=account.getIdToken();setStatus("Consultando YouTube…");showLoading("Consultando YouTube","Conectando con el servicio central…");
        pool.execute(()->{try{work.run(token,new YouTubeApi(sessionKey));}catch(Exception e){
            runOnUiThread(()->{if(token==generation&&!isDestroyed()){busy=false;hideLoading();String message="No se pudo consultar. "+(e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("YouTube HTTP")?e.getMessage():"Comprueba Internet y configuración.");setStatus(message);new AlertDialog.Builder(this).setTitle("Consulta no completada").setMessage(message).setPositiveButton("Entendido",null).show();}});
        }});
    }
    void finish(int token,Runnable r){runOnUiThread(()->{if(token!=generation||isFinishing()||isDestroyed())return;busy=false;hideLoading();setStatus("");r.run();});}
    void popular(){
        if(region==null||category==null)return;
        rememberFilters();int ri=catalogRegion,selected=catalogCategory;if(ri<0||selected<0)return;
        String reg=regions[ri];
        if(selected>=categories.length){
            String[] theme=THEMES[selected-categories.length];String q="@country".equals(theme[1])?countryNames[ri]:theme[1];
            String explanation="Muestra temática por palabras clave, no categoría oficial.";
            if("@country".equals(theme[1]))explanation+=" No verifica el origen del creador.";
            if(selected==5)explanation+=" No garantiza transmisiones activas.";
            if(selected==8)explanation+=" No confirma generación con IA.";
            searchTopic(q,reg,"viewCount",theme[0],explanation,true);return;
        }
        String cat=categories[selected],label=catalogOptions()[selected];
        run((token,api)->{
            stage(token,"Consultando catálogo","Obteniendo videos de la región seleccionada.");JSONArray v=api.popular(reg,cat);
            finish(token,()->{catalogResult=new VideoResult(label+" · "+reg,"Lista oficial de YouTube, no volumen de búsquedas.",v);catalogEdit=false;catalog();});
        });
    }
    void search(){
        if(query==null)return;rememberFilters();String q=nicheQuery.trim();
        if(q.isEmpty()||q.length()>500){query.setError("Escribe un tema de hasta 500 caracteres");return;}
        if(nicheRegion<0||nicheOrder<0)return;
        searchTopic(q,regions[nicheRegion],new String[]{"viewCount","date","relevance"}[nicheOrder],q,"Muestra de búsqueda; pocos resultados no prueban baja demanda.",false);
    }
    void searchTopic(String q,String reg,String sort,String label,String explanation,boolean fromCatalog){
        run((token,api)->{
            JSONArray videos=new JSONArray();int used=7;
            for(int days:new int[]{7,30,90}){used=days;stage(token,"Buscando videos","Explorando publicaciones de los últimos "+days+" días.");videos=api.search(q,reg,days,sort);if(videos.length()>=10)break;}
            final int span=used;final JSONArray found=videos;
            finish(token,()->{if(account==null)return;
                store.add(account.getId().toLowerCase(Locale.ROOT),q,reg,span,sort);
                VideoResult result=new VideoResult(label+" · "+reg+" · "+span+" días",explanation,found);
                if(fromCatalog){catalogResult=result;catalogEdit=false;catalog();}else{nicheResult=result;nicheEdit=false;explore();}
                if(driveEnabled)inspectBackup();
            });
        });
    }
    void renderResult(VideoResult result){
        note(body,"YouTube · "+localDate(result.observed.toString())+" · "+Math.min(10,result.videos.length())+" de "+result.videos.length()+" videos");
        note(body,result.explanation);
        if(result.videos.length()==0)note(body,"Evidencia insuficiente. Prueba sinónimos u otra región.");
        renderVideos(body,result.videos,10,result.observed);
    }
    static JSONObject obj(JSONObject j,String k){JSONObject v=j.optJSONObject(k);return v==null?new JSONObject():v;}
    String metric(JSONObject s,String k){String n=s.optString(k,"");try{return NumberFormat.getIntegerInstance(new Locale("es","NI")).format(Long.parseLong(n));}catch(Exception e){return "No disponible";}}
    void renderVideos(LinearLayout target,JSONArray videos,int limit,Instant observed){
        for(int i=0;i<Math.min(videos.length(),limit);i++){
            JSONObject j=videos.optJSONObject(i);if(j==null)continue;
            JSONObject sn=obj(j,"snippet"),st=obj(j,"statistics"),cd=obj(j,"contentDetails");
            LinearLayout c=card(target),top=new LinearLayout(this);top.setGravity(Gravity.TOP);
            LinearLayout copy=column();TextView title=text(sn.optString("title","Sin título"),16,FG,true);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title);
            TextView meta=text(sn.optString("channelTitle")+" · "+localDate(sn.optString("publishedAt"))+" · "+duration(cd.optString("duration")),11,MUTED,false);meta.setMaxLines(2);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(meta);
            top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            int width=getResources().getConfiguration().screenWidthDp<360?84:98;FrameLayout thumb=videoThumbnail(sn,j.optString("id"));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(width),dp(width*9/16));tp.leftMargin=dp(7);top.addView(thumb,tp);c.addView(top);
            LinearLayout stats=new LinearLayout(this);addMetric(stats,"VISTAS",metric(st,"viewCount"),CYAN,0xffe3f3fa);addMetric(stats,"ME GUSTA",metric(st,"likeCount"),PINK,0xfffae9f3);addMetric(stats,"COMENTARIOS",metric(st,"commentCount"),YELLOW,0xfffff2dc);c.addView(stats);
            LinearLayout buttons=new LinearLayout(this);
            rowAction(buttons,"YouTube",()->open("https://www.youtube.com/watch?v="+Uri.encode(j.optString("id"))));
            rowAction(buttons,"Canal",()->loadChannel(sn.optString("channelId")));
            rowAction(buttons,"Estadísticas",()->showStatistics(j,observed));c.addView(buttons);
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
    void addMetric(LinearLayout row,String label,String value,int accent,int fill){
        LinearLayout tile=column();tile.setPadding(dp(6),dp(4),dp(6),dp(4));tile.setBackground(bg(fill,9));
        TextView name=text(label,10,accent,true);name.setPadding(0,0,0,0);name.setSingleLine(true);name.setAutoSizeTextTypeUniformWithConfiguration(8,10,1,android.util.TypedValue.COMPLEX_UNIT_SP);tile.addView(name);
        TextView number=text(value,15,FG,true);number.setPadding(0,dp(2),0,0);number.setSingleLine(true);number.setAutoSizeTextTypeUniformWithConfiguration(9,15,1,android.util.TypedValue.COMPLEX_UNIT_SP);tile.addView(number);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(4),dp(2),dp(2));row.addView(tile,p);
    }
    AlertDialog popup(String title,LinearLayout content){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Cerrar",null).create();dialog.show();
        Window w=dialog.getWindow();if(w!=null)w.setLayout(Math.min(dp(520),getResources().getDisplayMetrics().widthPixels-dp(20)),-2);
        return dialog;
    }
    void channelCard(LinearLayout target,JSONObject j){
        JSONObject s=obj(j,"snippet"),t=obj(j,"statistics");LinearLayout c=card(target);
        c.addView(text(s.optString("title"),19,FG,true));note(c,"Creación: "+localDate(s.optString("publishedAt")));
        LinearLayout stats=new LinearLayout(this);addMetric(stats,"SUSCRIPTORES",t.optBoolean("hiddenSubscriberCount")?"Ocultos":metric(t,"subscriberCount"),GREEN,0xffe6f5eb);addMetric(stats,"VISTAS",metric(t,"viewCount"),CYAN,0xffe3f3fa);addMetric(stats,"VIDEOS",metric(t,"videoCount"),YELLOW,0xfffff2dc);c.addView(stats);
        note(c,"Contadores públicos actuales · YouTube");
        action(c,"Abrir canal en YouTube",()->open("https://www.youtube.com/channel/"+Uri.encode(j.optString("id"))));
    }
    void loadChannel(String id){
        if(id.isEmpty())return;
        run((token,api)->{
            stage(token,"Consultando canal","Obteniendo estadísticas públicas.");JSONArray a=api.channels(id);JSONObject ch=a.optJSONObject(0);
            if(ch==null){finish(token,()->toast("Canal no disponible."));return;}
            String playlist=obj(obj(ch,"contentDetails"),"relatedPlaylists").optString("uploads");
            stage(token,"Cargando publicaciones","Obteniendo videos recientes del canal.");JSONArray videos=api.uploads(playlist);
            finish(token,()->{
                LinearLayout content=column();content.setPadding(dp(10),dp(5),dp(10),dp(8));channelCard(content,ch);
                note(content,"YouTube · "+localDate(Instant.now().toString()));
                content.addView(text("Publicaciones recientes",16,FG,true));note(content,"Hasta 25 publicaciones públicas identificadas.");
                renderVideos(content,videos,25,Instant.now());popup("Ficha del canal",content);
            });
        });
    }
    void showStatistics(JSONObject j,Instant observed){
        LinearLayout content=column();content.setPadding(dp(12),dp(5),dp(12),dp(10));
        JSONObject sn=obj(j,"snippet");content.addView(text(sn.optString("title","Video"),17,FG,true));note(content,sn.optString("channelTitle"));
        note(content,"Publicado: "+localDate(sn.optString("publishedAt")));
        LinearLayout readings=column();content.addView(readings);statReading(readings,j,observed,"Consulta original");
        LinearLayout latest=column();content.addView(latest);
        LinearLayout explanation=card(content);explanation.addView(text("Evolución histórica",16,FG,true));
        note(explanation,"La API pública entrega contadores acumulados actuales, no una serie histórica. Una sola medición no permite determinar si el video crece o pierde interés.");
        note(explanation,"Puedes volver a consultar para comparar dos lecturas reales durante esta ventana. El historial de largo plazo y las métricas de crecimiento aún requieren datos y permisos.");
        String id=j.optString("id");
        final AlertDialog[] dialog=new AlertDialog[1];
        action(content,"Actualizar contadores",()->{
            if(id.isEmpty())return;
            run((token,api)->{
                stage(token,"Actualizando estadísticas","Consultando contadores públicos del video.");JSONArray v=api.videos(id);
                finish(token,()->{if(dialog[0]==null||!dialog[0].isShowing())return;JSONObject fresh=v.optJSONObject(0);
                    if(fresh==null){toast("Video no disponible.");return;}
                    latest.removeAllViews();statReading(latest,fresh,Instant.now(),"Última consulta");});
            });
        });
        dialog[0]=popup("Estadísticas del video",content);
    }
    void statReading(LinearLayout target,JSONObject j,Instant observed,String label){
        LinearLayout c=card(target);c.addView(text(label,14,FG,true));note(c,localDate(observed.toString())+" · YouTube Data API");
        JSONObject st=obj(j,"statistics");LinearLayout row=new LinearLayout(this);addMetric(row,"VISTAS",metric(st,"viewCount"),CYAN,0xffe3f3fa);addMetric(row,"ME GUSTA",metric(st,"likeCount"),PINK,0xfffae9f3);addMetric(row,"COMENTARIOS",metric(st,"commentCount"),YELLOW,0xfffff2dc);c.addView(row);
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
    }
    void policies(){
        new AlertDialog.Builder(this).setTitle("Información y privacidad").setMessage("Inicia sesión con Google. Guarda tus términos de búsqueda y filtros en SQLite y, con tu autorización, en el almacenamiento privado de Drive. Las consultas públicas se realizan mediante el servicio central cuando esté configurado. No se guardan estadísticas externas históricas ni contraseñas. No incluye publicidad ni telemetría propia.").setPositiveButton("Políticas de YouTube",(d,w)->open("https://www.youtube.com/t/terms")).setNeutralButton("Privacidad de Google",(d,w)->open("https://policies.google.com/privacy")).setNegativeButton("Cerrar",null).show();
    }
    void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"No hay aplicación disponible para abrir el enlace.",Toast.LENGTH_SHORT).show();}}
}
