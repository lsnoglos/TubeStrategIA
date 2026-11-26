package com.lsnoglos.tubestrategia;

import android.net.Uri;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

final class YouTubeApi {
    private final String token;
    YouTubeApi(String token) { this.token = token; }

    private JSONObject get(String endpoint, String... params) throws Exception {
        JSONObject request=new JSONObject();
        request.put("idToken",token);request.put("endpoint",endpoint);
        JSONObject args=new JSONObject();
        for(int i=0;i<params.length;i+=2)args.put(params[i],params[i+1]);
        request.put("params",args);
        HttpURLConnection conn=(HttpURLConnection)new URL(BuildConfig.BACKEND_URL).openConnection();
        conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
        conn.setConnectTimeout(15000);conn.setReadTimeout(35000);
        try{
            try(OutputStream out=conn.getOutputStream()){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
            int status=conn.getResponseCode();
            if(status!=200)throw new IOException("YouTube HTTP "+status+" · servicio no disponible.");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                JSONObject response=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                if(!response.optBoolean("ok"))throw new IOException("YouTube HTTP · "+response.optString("error","Consulta rechazada"));
                return response.getJSONObject("data");
            }
        }finally{conn.disconnect();}
    }
    private static JSONArray items(JSONObject obj) { JSONArray a=obj.optJSONArray("items"); return a==null?new JSONArray():a; }
    static String ids(JSONArray a, boolean search) {
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(int i=0;i<a.length();i++){
            JSONObject j=a.optJSONObject(i); if(j==null)continue;
            String id=search?(j.optJSONObject("id")==null?"":j.optJSONObject("id").optString("videoId")):j.optString("id");
            if(!id.isEmpty())result.add(id);
        }
        return String.join(",",result);
    }
    JSONArray popular(String region,String category) throws Exception {
        return items(get("videos","part","snippet,statistics,contentDetails","chart","mostPopular","regionCode",region,"videoCategoryId",category,"maxResults","25"));
    }
    void requireFilters() throws Exception {
        JSONObject capability;
        try{capability=get("capabilities");}
        catch(Exception e){throw new IOException("YouTube HTTP · Actualiza la implementación del servicio para habilitar los filtros avanzados.",e);}
        if(capability.optInt("filterVersion")<2)throw new IOException("YouTube HTTP · El servicio requiere una actualización de filtros.");
    }
    JSONArray search(String q,String region,int days,String order,String category,String event) throws Exception {
        ArrayList<String> params=new ArrayList<>(Arrays.asList("part","snippet","type","video","regionCode",region,"publishedAfter",
            Instant.now().minus(days,ChronoUnit.DAYS).toString(),"order",order,"maxResults","25"));
        if(!q.isEmpty())Collections.addAll(params,"q",q);
        if(!category.isEmpty())Collections.addAll(params,"videoCategoryId",category);
        if(!event.isEmpty())Collections.addAll(params,"eventType",event);
        JSONArray found=items(get("search",params.toArray(new String[0])));
        JSONArray details=videos(ids(found,true));
        // Keep the order from search.list even if videos.list returns a different order.
        Map<String,JSONObject> byId=new HashMap<>();
        for(int i=0;i<details.length();i++){JSONObject j=details.optJSONObject(i);if(j!=null)byId.put(j.optString("id"),j);}
        JSONArray result=new JSONArray();
        for(String id:ids(found,true).split(",")){JSONObject j=byId.get(id);if(j!=null)result.put(j);}
        return result;
    }
    JSONArray videos(String ids) throws Exception {
        if(ids.isEmpty())return new JSONArray();
        return items(get("videos","part","snippet,statistics,contentDetails","id",ids));
    }
    JSONArray channels(String ids) throws Exception {
        if(ids.isEmpty())return new JSONArray();
        return items(get("channels","part","snippet,statistics,contentDetails","id",ids));
    }
    JSONArray uploads(String playlist) throws Exception {
        if(playlist.isEmpty())return new JSONArray();
        JSONArray found=items(get("playlistItems","part","contentDetails","playlistId",playlist,"maxResults","25"));
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(int i=0;i<found.length();i++){
            JSONObject j=found.optJSONObject(i); JSONObject d=j==null?null:j.optJSONObject("contentDetails");
            if(d!=null&&!d.optString("videoId").isEmpty())ids.add(d.optString("videoId"));
        }
        return videos(String.join(",",ids));
    }
}
