package com.cardio.lab;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

final class TraceView extends View {
    private static final class Point { final long t;final float y; Point(long t,float y){this.t=t;this.y=y;} }
    private final ArrayDeque<Point> points=new ArrayDeque<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final int color;
    private final long window;
    private final boolean vibration;
    private float threshold=0.12f;
    private long end;
    TraceView(Context c,int color,long window,boolean vibration){super(c);this.color=color;this.window=window;this.vibration=vibration;setMinimumHeight(35);}
    void threshold(double value){threshold=(float)value;}
    void clear(){points.clear();end=0;invalidate();}
    void add(long t,float value){points.addLast(new Point(t,value));end=t;while(points.size()>1600||(!points.isEmpty()&&window>0&&points.peekFirst().t<t-window))points.removeFirst();}
    void time(long t){end=t;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        float min=vibration?-Math.max(.15f,threshold*1.4f):40,max=vibration?Math.max(.15f,threshold*1.4f):100;
        long left=window>0?Math.max(0,end-window):0,right=Math.max(left+1,end);
        for(Point p:points)if(p.t>=left&&Float.isFinite(p.y)){min=Math.min(min,p.y);max=Math.max(max,p.y);}
        if(vibration){max=Math.max(Math.abs(min),max)*1.05f;min=-max;}else{min=Math.max(0,min-5);max+=5;}
        float top=12*density,bottom=getHeight()-8*density,h=Math.max(1,bottom-top),width=getWidth();
        paint.setStrokeWidth(density);paint.setColor(Color.rgb(46,66,77));paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<3;i++){float y=top+h*i/2;canvas.drawLine(0,y,width,y,paint);}
        if(vibration){paint.setColor(0xff8a9da7);paint.setPathEffect(new DashPathEffect(new float[]{5*density,4*density},0));float y=bottom-(threshold-min)/(max-min)*h;canvas.drawLine(0,y,width,y,paint);paint.setPathEffect(null);}
        path.reset();boolean connected=false;long last=-1;
        for(Point p:points){
            if(p.t<left||!Float.isFinite(p.y)){connected=false;continue;}
            float x=(float)(p.t-left)/(right-left)*width,y=bottom-(p.y-min)/(max-min)*h;
            if(!connected||(!vibration&&last>=0&&p.t-last>5000))path.moveTo(x,y);else path.lineTo(x,y);
            connected=true;last=p.t;
        }
        paint.setColor(color);paint.setStrokeWidth(1.8f*density);canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextSize(9*density);paint.setColor(0xff9fb4bf);
        canvas.drawText(vibration?String.format(Locale.US,"±%.2f",max):String.format(Locale.US,"%.0f",max),2*density,10*density,paint);
        if(points.isEmpty()){paint.setTextSize(11*density);canvas.drawText("Waiting for data",8*density,top+h*.65f,paint);}
    }
}
