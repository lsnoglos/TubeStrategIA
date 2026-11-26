package com.lsnoglos.tubestrategia;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Indeterminate bar wave; does not represent actual data or percentage. */
final class WaveLoader extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] palette={0xfff41e92,0xffc873f7,0xff8e98ff,0xff59d9ff,0xff59e7dc,0xffb5f36a,0xffffd66a};
    private long start;
    WaveLoader(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();start=SystemClock.uptimeMillis();invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),spacing=w/11f,bar=spacing*.66f,base=h*.84f;
        double t=(SystemClock.uptimeMillis()-start)/1000.0;
        for(int i=0;i<palette.length;i++){
            float x=w/2f+(i-3)*spacing,amplitude=(float)(.5+.5*Math.sin(t*5.3-i*.7));
            float height=h*(.22f+.58f*amplitude);
            paint.setColor(palette[i]);c.drawRoundRect(x-bar/2,base-height,x+bar/2,base,bar/2,bar/2,paint);
        }
        if(isShown())postInvalidateOnAnimation();
    }
}
