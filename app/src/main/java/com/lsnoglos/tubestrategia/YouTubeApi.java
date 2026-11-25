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
    private final String key;
    YouTubeApi(String key) { this.key = key; }

    private JSONObject get(String endpoint, String... params) throws Exception {
        Uri.Builder uri = Uri.parse("https://www.googleapis.com/youtube/v3/" + endpoint).buildUpon();
        for (int i=0;i<params.length;i+=2) uri.appendQueryParameter(params[i], params[i+1]);
        HttpURLConnection conn=(HttpURLConnection)new URL(uri.build().toString()).openConnection();
        conn.setRequestProperty("X-Goog-Api-Key", key);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(12000); conn.setReadTimeout(18000); conn.setInstanceFollowRedirects(false);
        try {
            int status=conn.getResponseCode();
            InputStream source=status==200?conn.getInputStream():conn.getErrorStream();
            String payload="";
            if(source!=null) try(InputStream in=source; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[4096]; int n;
                while((n=in.read(buf))!=-1) out.write(buf,0,n);
                payload=new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            if(status!=200) {
                JSONObject err=new JSONObject(payload.isEmpty()?"{}":payload).optJSONObject("error");
                JSONArray reasons=err==null?null:err.optJSONArray("errors");
                String reason=reasons==null||reasons.length()==0?"":reasons.optJSONObject(0).optString("reason","");
                throw new IOException("YouTube HTTP "+status+" "+reason+"; verifica la API, restricciones y cuota.");
            }
            return new JSONObject(payload);
        } finally { conn.disconnect(); }
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
    JSONArray search(String q,String region,int days,String order) throws Exception {
        JSONArray found=items(get("search","part","snippet","type","video","q",q,"regionCode",region,"publishedAfter",
            Instant.now().minus(days,ChronoUnit.DAYS).toString(),"order",order,"maxResults","25"));
        return videos(ids(found,true));
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
