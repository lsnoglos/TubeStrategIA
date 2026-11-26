// TubeStrategIA: piloto personal. No usar tokeninfo para producción.
const CLIENT_ID='462342787368-pi9u7cfa8na7df34llfed6hc9so6ocf5.apps.googleusercontent.com';
function output_(obj){return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);}
function fail_(s){return output_({ok:false,error:s});}
function doPost(e){
  try{
    const props=PropertiesService.getScriptProperties();
    const key=props.getProperty('YOUTUBE_API_KEY');
    const allow=(props.getProperty('TEST_EMAILS')||'').split(',').map(x=>x.trim().toLowerCase()).filter(Boolean);
    if(!key||!allow.length)return fail_('Servicio no configurado.');
    if(!e||!e.postData||e.postData.contents.length>16000)return fail_('Solicitud inválida.');
    const req=JSON.parse(e.postData.contents);
    if(typeof req.idToken!=='string'||req.idToken.length>10000)return fail_('Inicia sesión.');
    const check=UrlFetchApp.fetch('https://oauth2.googleapis.com/tokeninfo?id_token='+encodeURIComponent(req.idToken),{muteHttpExceptions:true});
    if(check.getResponseCode()!==200)return fail_('Sesión caducada. Cierra sesión y vuelve a ingresar.');
    const user=JSON.parse(check.getContentText());
    if(user.aud!==CLIENT_ID||!['accounts.google.com','https://accounts.google.com'].includes(user.iss)||Number(user.exp)<=Date.now()/1000||String(user.email_verified)!=='true'||!allow.includes(String(user.email).toLowerCase()))return fail_('Cuenta no autorizada.');
    const p=req.params||{},ep=req.endpoint;let args={};
    const region=String(p.regionCode||'NI');
    if(!['NI','MX','US','ES','CO','AR','BR'].includes(region))return fail_('Región inválida.');
    if(ep==='capabilities')return output_({ok:true,data:{filterVersion:2}});
    if(ep==='search'){
      const q=String(p.q||'').trim(),t=Date.parse(p.publishedAfter),order=String(p.order);
      const category=String(p.videoCategoryId||''),event=String(p.eventType||'');
      if(category&&!['2','15','17','19','25','27','28'].includes(category))return fail_('Categoría de búsqueda inválida.');
      if(event&&event!=='live')return fail_('Tipo de transmisión inválido.');
      if((!q&&!event&&!category)||q.length>500||!Number.isFinite(t)||t<Date.now()-91*86400000||t>Date.now()||!['viewCount','date','relevance'].includes(order))return fail_('Búsqueda inválida.');
      args={part:'snippet',type:'video',regionCode:region,publishedAfter:new Date(t).toISOString(),order,maxResults:25};
      if(q)args.q=q;
      if(category)args.videoCategoryId=category;
      if(event)args.eventType=event;
    }else if(ep==='videos'){
      args={part:'snippet,statistics,contentDetails'};
      if(p.chart==='mostPopular'){
        if(!['0','10','20','1'].includes(String(p.videoCategoryId)))return fail_('Categoría inválida.');
        Object.assign(args,{chart:'mostPopular',regionCode:region,videoCategoryId:String(p.videoCategoryId),maxResults:25});
      }else{if(!validIds_(p.id))return fail_('Identificadores inválidos.');args.id=String(p.id);}
    }else if(ep==='channels'){
      if(!validIds_(p.id))return fail_('Identificadores inválidos.');
      args={part:'snippet,statistics,contentDetails',id:String(p.id)};
    }else if(ep==='playlistItems'){
      if(!/^[A-Za-z0-9_-]{1,128}$/.test(String(p.playlistId)))return fail_('Lista inválida.');
      args={part:'contentDetails',playlistId:String(p.playlistId),maxResults:25};
    }else return fail_('Operación no permitida.');
    if(ep==='search'&&!reserveSearch_(props))return fail_('Cuota diaria de búsqueda agotada para todos. Se restablece a medianoche del Pacífico.');
    const url='https://www.googleapis.com/youtube/v3/'+ep+'?'+Object.keys(args).map(k=>encodeURIComponent(k)+'='+encodeURIComponent(args[k])).join('&');
    const res=UrlFetchApp.fetch(url,{headers:{'X-Goog-Api-Key':key},muteHttpExceptions:true,followRedirects:false});
    if(res.getResponseCode()!==200){
      const err=JSON.parse(res.getContentText()).error||{},reason=((err.errors||[])[0]||{}).reason||'';
      return fail_(reason==='quotaExceeded'||reason==='dailyLimitExceeded'?'Cuota de YouTube agotada. Intenta tras el reinicio diario.':'YouTube rechazó la consulta ('+res.getResponseCode()+').');
    }
    return output_({ok:true,data:JSON.parse(res.getContentText())});
  }catch(e){return fail_('Servicio temporalmente no disponible.');}
}
function validIds_(ids){return typeof ids==='string'&&ids.split(',').length<=50&&ids.split(',').every(id=>/^[A-Za-z0-9_-]{1,128}$/.test(id));}
function reserveSearch_(props){
  const lock=LockService.getScriptLock();lock.waitLock(10000);
  try{
    const day=Utilities.formatDate(new Date(),'America/Los_Angeles','yyyy-MM-dd');
    const old=JSON.parse(props.getProperty('SEARCH_QUOTA')||'{}');
    const used=old.day===day?Number(old.used||0):0;
    if(used>=100)return false;
    props.setProperty('SEARCH_QUOTA',JSON.stringify({day,used:used+1}));return true;
  }finally{lock.releaseLock();}
}
