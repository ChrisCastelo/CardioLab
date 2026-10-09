package com.cardio.lab;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A 400 m oval; progress is simulated until a verified telemetry source is integrated. */
final class TrackView extends View {
    private final Paint paint=new Paint(3);
    private final Path path=new Path(),segment=new Path();
    private final PathMeasure measure=new PathMeasure();
    private final RectF oval=new RectF(),outer=new RectF(),bend=new RectF();
    private final float[] point=new float[2];
    private static final String[] GUIDES={"START / FINISH","100 m","200 m","300 m"};
    private double meters;
    private long countdownAt,goAt;
    private String label,detail;
    TrackView(Context c){super(c);setContentDescription("400 meter track, counterclockwise from the finish line at the end of the home straight, with 100, 200 and 300 meter guides. Tap to expand or collapse.");}
    void meters(double value){if(meters!=value){meters=value;invalidate();}}
    /** Text in the middle of the oval (the compact bar's lap count); the countdown replaces it while it runs. */
    void label(String value,String below){if(!java.util.Objects.equals(label,value)||!java.util.Objects.equals(detail,below)){label=value;detail=below;invalidate();}}
    /** Controller countdown start and belt start times (elapsedRealtime, 0 for none); the label animates itself. */
    void countdown(long startedAt,long started){if(countdownAt!=startedAt||goAt!=started){countdownAt=startedAt;goAt=started;invalidate();}}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight(),stroke=Math.max(2,Math.min(w,h)*.035f),inset=stroke*(w<300*getResources().getDisplayMetrics().density?2:3);
        // In this schematic, each straight has the same length as a semicircle.
        // Thus every 100 m guide lands at a tangent, with matching left/right Xs.
        float r=Math.max(1,Math.min((w-2*inset)/(float)(Math.PI+2),(h-2*inset)/2));
        float halfWidth=(float)(Math.PI+2)*r/2;
        oval.set(w/2-halfWidth,h/2-r,w/2+halfWidth,h/2+r);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(ConsoleUi.SOFT);
        // The home straight runs left-to-right along the bottom. Zero is its finish
        // line, immediately before the right bend, not Path.addRoundRect's origin.
        path.reset();path.moveTo(oval.right-r,oval.bottom);
        bend.set(oval.right-2*r,oval.top,oval.right,oval.bottom);path.arcTo(bend,90,-180);
        path.lineTo(oval.left+r,oval.top);
        bend.set(oval.left,oval.top,oval.left+2*r,oval.bottom);path.arcTo(bend,270,-180);
        path.close();canvas.drawPath(path,paint);
        paint.setStrokeWidth(Math.max(1,stroke*.15f));paint.setColor(ConsoleUi.LINE);
        outer.set(oval);outer.inset(-stroke,-stroke);canvas.drawRoundRect(outer,r+stroke,r+stroke,paint);
        outer.inset(-stroke*.6f,-stroke*.6f);canvas.drawRoundRect(outer,r+stroke*1.6f,r+stroke*1.6f,paint);
        measure.setPath(path,false);float distance=(float)((meters%400)/400*measure.getLength());segment.reset();measure.getSegment(0,distance,segment,true);
        paint.setColor(ConsoleUi.LIME);paint.setStrokeWidth(stroke);canvas.drawPath(segment,paint);
        boolean labels=w>=300*getResources().getDisplayMetrics().density&&h>=140*getResources().getDisplayMetrics().density;
        float textSize=20*getResources().getDisplayMetrics().scaledDensity;
        for(int split=0;split<4;split++){
            point[0]=split<2?oval.right-r:oval.left+r;
            boolean bottom=split==0||split==3;
            point[1]=bottom?oval.bottom:oval.top;
            float nx=0,ny=bottom?-1:1,extent=stroke*2.1f;
            paint.setStyle(Paint.Style.STROKE);paint.setColor(split==0?ConsoleUi.INK:ConsoleUi.MUTED);paint.setStrokeWidth(Math.max(1,stroke*(split==0?.2f:.1f)));
            canvas.drawLine(point[0]-nx*extent,point[1]-ny*extent,point[0]+nx*extent,point[1]+ny*extent,paint);
            if(labels){
                paint.setStyle(Paint.Style.FILL);paint.setTextSize(textSize);paint.setTextAlign(Paint.Align.CENTER);
                float offset=extent+textSize*1.4f;
                canvas.drawText(GUIDES[split],point[0]+nx*offset,point[1]+ny*offset-(paint.ascent()+paint.descent())/2,paint);
            }
        }
        paint.setColor(ConsoleUi.LIME);
        measure.getPosTan(distance,point,null);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(point[0],point[1],stroke*.8f,paint);
        long now=android.os.SystemClock.elapsedRealtime();
        String count=countdownAt>0?String.valueOf(Math.max(1,3-(now-countdownAt)/1000)):goAt>0&&now-goAt<1200?"GO":null;
        if(count!=null){
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(r*1.1f);paint.setFakeBoldText(true);paint.setColor(count.equals("GO")?ConsoleUi.LIME:ConsoleUi.INK);
            canvas.drawText(count,w/2,h/2-(paint.ascent()+paint.descent())/2,paint);paint.setFakeBoldText(false);
            postInvalidateDelayed(100);
        }else if(label!=null){
            // Lap count with the smaller lap pace beneath it.
            // Readable minimums for the compact bar's 36 dp track.
            float density=getResources().getDisplayMetrics().density,lapText=Math.max(r*.72f,12*density),paceText=Math.max(r*.5f,9*density);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(lapText);paint.setFakeBoldText(true);paint.setColor(ConsoleUi.INK);
            float lapSize=-paint.ascent(),gap=r*.08f;paint.setTextSize(paceText);float paceSize=detail==null?0:-paint.ascent();
            float top=h/2-(lapSize+(detail==null?0:gap+paceSize))/2;
            paint.setTextSize(lapText);canvas.drawText(label,w/2,top+lapSize,paint);paint.setFakeBoldText(false);
            if(detail!=null){paint.setTextSize(paceText);paint.setColor(ConsoleUi.MUTED);canvas.drawText(detail,w/2,top+lapSize+gap+paceSize,paint);}
        }
    }
}
