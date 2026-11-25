package com.lsnoglos.tubestrategia;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import java.time.Instant;
final class HistoryStore extends SQLiteOpenHelper {
    HistoryStore(Context c){super(c,"history.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE queries(owner TEXT NOT NULL,id TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(owner,id))");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int version){}
    synchronized void add(String owner,String query,String region,int days,String sort){
        try{JSONObject r=new JSONObject().put("id",UUID.randomUUID().toString()).put("query",query).put("region",region).put("days",days).put("sort",sort).put("at",Instant.now().toString());mergeInto(owner,new JSONArray().put(r));}catch(JSONException e){throw new IllegalStateException(e);}
    }
    synchronized JSONArray read(String owner){
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().query("queries",new String[]{"payload"},"owner=?",new String[]{owner},null,null,"id ASC")){
            while(c.moveToNext())a.put(new JSONObject(c.getString(0)));
        }catch(JSONException e){throw new IllegalStateException(e);}
        return a;
    }
    static JSONObject validated(JSONObject r)throws JSONException{
        String id=r.getString("id"),q=r.getString("query"),region=r.getString("region"),sort=r.getString("sort"),at=r.getString("at");int days=r.getInt("days");
        if(!id.matches("[a-fA-F0-9-]{36}")||q.length()>500||!region.matches("[A-Z]{2}")||!Arrays.asList("viewCount","date","relevance").contains(sort)||!(days==7||days==30||days==90))throw new JSONException("Registro inválido");
        Instant.parse(at);
        return new JSONObject().put("id",id).put("query",q).put("region",region).put("days",days).put("sort",sort).put("at",at);
    }
    static JSONArray merge(JSONArray a,JSONArray b)throws JSONException{
        TreeMap<String,JSONObject> m=new TreeMap<>();
        for(JSONArray x:new JSONArray[]{a,b})for(int i=0;i<x.length();i++){
            JSONObject r=validated(x.getJSONObject(i));String id=r.getString("id");
            if(m.containsKey(id)&&!m.get(id).toString().equals(r.toString()))throw new JSONException("Conflicto de registro");
            m.put(id,r);
        }
        JSONArray result=new JSONArray();for(JSONObject r:m.values())result.put(r);return result;
    }
    synchronized void mergeInto(String owner,JSONArray entries)throws JSONException{
        JSONArray valid=merge(read(owner),entries);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{for(int i=0;i<valid.length();i++){
            JSONObject r=valid.getJSONObject(i);ContentValues v=new ContentValues();v.put("owner",owner);v.put("id",r.getString("id"));v.put("payload",r.toString());db.insertWithOnConflict("queries",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
}
