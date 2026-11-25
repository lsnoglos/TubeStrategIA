package com.lsnoglos.tubestrategia;
import android.net.Uri;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class DriveBackup {
    static final String NAME="tubestrategia-history-v1.json";
    final String token,owner;
    DriveBackup(String t,String o){token=t;owner=o;}
    static class Snapshot {
        final String id,version;final JSONArray entries;
        Snapshot(String i,String v,JSONArray e){id=i;version=v;entries=e;}
        String signature(){return id+"|"+version+"|"+entries.toString();}
    }
    JSONObject request(String url,String method,String type,String payload)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);
        c.setConnectTimeout(15000);c.setReadTimeout(25000);
        try{
            if(payload!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",type);try(OutputStream o=c.getOutputStream()){o.write(payload.getBytes(StandardCharsets.UTF_8));}}
            if(c.getResponseCode()/100!=2)throw new IOException("Drive HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>8000000)throw new IOException("Respaldo demasiado grande");out.write(buf,0,n);}
                return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{c.disconnect();}
    }
    Snapshot read()throws Exception{
        JSONObject identity=request("https://www.googleapis.com/oauth2/v3/userinfo","GET",null,null);
        if(!identity.optBoolean("email_verified")||!owner.equalsIgnoreCase(identity.optString("email")))throw new IOException("Cuenta Drive diferente");
        String list="https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=nextPageToken,files(id,version)&q="+Uri.encode("name = '"+NAME+"' and trashed = false");
        JSONObject response=request(list,"GET",null,null);JSONArray files=response.getJSONArray("files");
        if(files.length()>1||response.has("nextPageToken"))throw new IOException("Respaldos duplicados");
        if(files.length()==0)return new Snapshot(null,"",new JSONArray());
        JSONObject f=files.getJSONObject(0);String id=f.getString("id");
        JSONObject content=request("https://www.googleapis.com/drive/v3/files/"+Uri.encode(id)+"?alt=media","GET",null,null);
        if(content.optInt("schema")!=1||!owner.equals(content.optString("owner")))throw new IOException("Respaldo inválido");
        return new Snapshot(id,f.optString("version"),HistoryStore.merge(new JSONArray(),content.getJSONArray("entries")));
    }
    void write(Snapshot remote,JSONArray entries)throws Exception{
        String content=new JSONObject().put("schema",1).put("owner",owner).put("entries",entries).toString();
        if(remote.id!=null){
            request("https://www.googleapis.com/upload/drive/v3/files/"+Uri.encode(remote.id)+"?uploadType=media","PATCH","application/json; charset=UTF-8",content);
        }else{
            String boundary="ts_"+UUID.randomUUID().toString();
            String metadata=new JSONObject().put("name",NAME).put("parents",new JSONArray().put("appDataFolder")).put("mimeType","application/json").toString();
            String body="--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+metadata+"\r\n--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+content+"\r\n--"+boundary+"--\r\n";
            request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart","POST","multipart/related; boundary="+boundary,body);
        }
    }
}
