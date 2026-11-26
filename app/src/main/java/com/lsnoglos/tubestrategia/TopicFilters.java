package com.lsnoglos.tubestrategia;
import org.json.*;
import java.util.*;
import java.text.Normalizer;

/** Filters factual public metadata; no AI detector and no comment inspection. */
final class TopicFilters {
    static JSONObject obj(JSONObject j,String key){JSONObject o=j.optJSONObject(key);return o==null?new JSONObject():o;}
    // Only topics with a reasonably direct official category correspondence.
    static String category(int topic){
        switch(topic){
            case 10:return "15"; // pets and animals
            case 11:return "28"; // science and technology
            case 12:return "27"; // education
            case 16:return "17"; // sports
            case 18:return "19"; // travel and events
            case 23:return "25"; // news and politics
            case 27:return "2";  // autos and vehicles
            default:return "";
        }
    }
    static String hint(int topic){
        if(topic==1)return "Filtro real de transmisiones activas.";
        if(topic==2)return "Comprueba el país público declarado del canal.";
        if(topic==4)return "Indicios en la descripción; no prueba de generación por IA.";
        if(!category(topic).isEmpty())return "Categoría oficial de YouTube + palabras clave.";
        return "Búsqueda temática por palabras clave.";
    }
    static String explanation(int topic){
        if(topic==1)return "Transmisiones marcadas en vivo por YouTube al consultar.";
        if(topic==2)return "Canales cuyo país público declarado coincide con la región. No verifica nacionalidad ni residencia.";
        if(topic==4)return "Candidatos con declaración textual explícita en la descripción. No equivale a la etiqueta oficial de IA.";
        if(!category(topic).isEmpty())return "Categoría oficial y coincidencias temáticas.";
        return "Muestra temática por palabras clave.";
    }
    static String normalize(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    static boolean aiDescription(String description){
        String d=normalize(description);
        // Require an explicit statement about the video or its images/animation, not a generic AI mention.
        String[] phrases={"this video was generated with ai","this video was generated using ai","this video was created with ai","this video was created using ai","this video is ai generated","this video is ai-generated","this video was made with ai","this video was made using ai","all visuals are ai-generated","all visuals are ai generated","all visuals were generated using ai","este video fue generado con ia","este video fue creado con ia","este video fue hecho con ia","este video esta generado con ia","este video esta hecho con ia","este video se creo con ia","las imagenes de este video fueron generadas con ia","este video fue generado con inteligencia artificial","este video fue creado con inteligencia artificial"};
        for(String p:phrases)if(d.contains(p))return true;
        return false;
    }
    static JSONArray filter(JSONArray raw,int topic,String region,YouTubeApi api)throws Exception{
        if(topic<0)return raw;
        Set<String> national=new HashSet<>();
        if(topic==2){
            LinkedHashSet<String> ids=new LinkedHashSet<>();
            for(int i=0;i<raw.length();i++){JSONObject j=raw.optJSONObject(i);if(j!=null){String id=obj(j,"snippet").optString("channelId");if(!id.isEmpty())ids.add(id);}}
            JSONArray channels=api.channels(String.join(",",ids));
            for(int i=0;i<channels.length();i++){JSONObject c=channels.optJSONObject(i);if(c!=null&&region.equalsIgnoreCase(obj(c,"snippet").optString("country")))national.add(c.optString("id"));}
        }
        JSONArray out=new JSONArray();String cat=category(topic);
        for(int i=0;i<raw.length();i++){
            JSONObject j=raw.optJSONObject(i);if(j==null)continue;
            JSONObject sn=obj(j,"snippet");
            if(!cat.isEmpty()&&!cat.equals(sn.optString("categoryId")))continue;
            if(topic==1&&!"live".equals(sn.optString("liveBroadcastContent")))continue;
            if(topic==2&&!national.contains(sn.optString("channelId")))continue;
            if(topic==4&&!aiDescription(sn.optString("description")))continue;
            out.put(j);
        }
        return out;
    }
}
