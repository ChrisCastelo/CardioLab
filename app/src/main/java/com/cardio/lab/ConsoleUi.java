package com.cardio.lab;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared native controls for the activity and four separate overlay windows. */
final class ConsoleUi {
    static final int BG=0xff101510,PANEL=0xff1d241e,SOFT=0xff2a332b,INK=0xfff1f5ed,MUTED=0xffa8b5a9,LIME=0xffd5f59b,RED=0xffff9d8f,LINE=0xff39463b;
    interface Actions {void open(String action);}
    final Context context;
    final ConsoleService service;
    final Actions actions;
    final float scale;
    TextView title,detail,lap,lapDetail,inclineValue,speedValue,interval;
    final TextView[] values=new TextView[6];
    final ArrayList<Button> speedButtons=new ArrayList<>(),inclineButtons=new ArrayList<>();
    Button main,end,workout,switcher;
    TrackView mini,large;
    ConsoleUi(Context c,ConsoleService s,Actions a){context=c;service=s;actions=a;float width=c.getResources().getDisplayMetrics().widthPixels/c.getResources().getDisplayMetrics().density;scale=width>=1200?1.35f:1;}
    int dp(float v){return Math.round(v*scale*context.getResources().getDisplayMetrics().density);}
    GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    LinearLayout col(){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView text(String value,int size,int color){TextView t=new TextView(context);t.setText(value);t.setTextSize(size*scale);t.setTextColor(color);t.setIncludeFontPadding(false);t.setFontFeatureSettings("tnum");return t;}
    Button button(String label,boolean primary,Runnable action){Button b=new Button(context);b.setText(label);b.setAllCaps(false);b.setTextSize(14*scale);b.setTextColor(primary?BG:INK);b.setBackground(round(primary?LIME:SOFT));b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setOnClickListener(v->action.run());return b;}
    int headerHeight(){return dp(84);}
    int footerHeight(){return dp(96);}
    int railWidth(){return dp(96);}
    View header(){
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(PANEL);root.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout left=new LinearLayout(context);left.setGravity(Gravity.CENTER_VERTICAL);
        Button sensors=button("⚙",false,()->actions.open("sensors"));sensors.setContentDescription("Sensors and console settings");left.addView(sensors,new LinearLayout.LayoutParams(dp(44),dp(48)));
        Button audio=button("♫",false,()->actions.open("audio"));audio.setContentDescription("Bluetooth audio settings");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(44),dp(48));ap.leftMargin=dp(4);left.addView(audio,ap);
        LinearLayout copy=col();copy.setPadding(dp(12),0,0,0);title=text("",15,INK);detail=text("",11,MUTED);interval=text("",12,LIME);copy.addView(title);copy.addView(detail);copy.addView(interval);left.addView(copy,new LinearLayout.LayoutParams(0,-2,1));copy.setOnClickListener(v->actions.open("home"));
        int width=context.getResources().getDisplayMetrics().widthPixels;int trackWidth=Math.min(dp(310),width/3);
        root.addView(left,new FrameLayout.LayoutParams((width-trackWidth)/2-dp(20),-1,Gravity.START));
        LinearLayout track=new LinearLayout(context);track.setGravity(Gravity.CENTER_VERTICAL);track.setPadding(dp(8),dp(4),dp(8),dp(4));track.setBackground(round(SOFT));
        mini=new TrackView(context);track.addView(mini,new LinearLayout.LayoutParams(dp(82),dp(42)));LinearLayout caption=col();caption.setPadding(dp(8),0,dp(4),0);lap=text("",16,INK);lapDetail=text("",10,MUTED);caption.addView(lap);caption.addView(lapDetail);track.addView(caption,new LinearLayout.LayoutParams(0,-2,1));Button expand=button("⛶",false,()->actions.open("track"));expand.setContentDescription("Expand or collapse track");track.addView(expand,new LinearLayout.LayoutParams(dp(40),dp(44)));root.addView(track,new FrameLayout.LayoutParams(trackWidth,-1,Gravity.CENTER));track.setOnClickListener(v->actions.open("track"));
        LinearLayout right=new LinearLayout(context);right.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);workout=button("Workout",false,()->actions.open("workout"));main=button("Quick start",true,()->{if(service.session.preview||service.treadmillLive())service.action("main");else actions.open("connection");});end=button("End",false,()->service.action("end"));end.setTextColor(RED);
        switcher=button("Switch speed",true,()->service.action("switch"));
        boolean compact=width<dp(1100);int mainWidth=dp(compact?88:108),endWidth=dp(compact?64:82);
        int switchWidth=Math.min(dp(200),(width-trackWidth)/2-dp(20)-mainWidth-endWidth-dp(18));
        for(Button b:new Button[]{workout,switcher,main,end}){int buttonWidth=b==switcher?Math.max(dp(120),switchWidth):b==main?mainWidth:b==end?endWidth:dp(82);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(buttonWidth,dp(48));p.leftMargin=dp(6);right.addView(b,p);}
        if(compact)switcher.setTextSize(11*scale);
        root.addView(right,new FrameLayout.LayoutParams(-2,-1,Gravity.END));return root;
    }
    View rail(boolean speed){
        LinearLayout root=col();root.setBackgroundColor(PANEL);root.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(text(speed?"Speed · mph":"Incline · level",12,INK));TextView value=text("—",28,INK);root.addView(value,new LinearLayout.LayoutParams(-1,dp(40)));if(speed)speedValue=value;else inclineValue=value;
        for(int i=speed?2:0;i<=12;i++){final int n=i;Button b=button(String.valueOf(i),false,()->{if(!service.session.preview&&!service.treadmillLive()){actions.open("connection");return;}if(speed)service.speed(n);else service.incline(n);});b.setContentDescription(speed?"Speed "+i+" mph":"Incline level "+i);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=dp(3);root.addView(b,p);(speed?speedButtons:inclineButtons).add(b);}
        return root;
    }
    View footer(){
        LinearLayout root=col();root.setBackgroundColor(BG);root.setPadding(dp(12),dp(6),dp(12),dp(6));LinearLayout metrics=new LinearLayout(context);metrics.setBackground(round(PANEL));metrics.setPadding(dp(10),dp(10),dp(10),dp(10));
        String[] names={"INCLINE","SPEED","TIME","DISTANCE","STEPS","♥ HEART RATE"};float[] weights={1.5f,1.5f,1.4f,1,1,1};
        for(int i=0;i<6;i++){LinearLayout item=col();item.setPadding(dp(12),0,dp(4),0);TextView name=text(names[i],10,i==5?RED:MUTED);values[i]=text("—",28,INK);if(i==2){item.setPadding(0,0,0,0);item.setGravity(Gravity.CENTER);name.setGravity(Gravity.CENTER);values[i].setGravity(Gravity.CENTER);}item.addView(name);item.addView(values[i]);metrics.addView(item,new LinearLayout.LayoutParams(0,-1,weights[i]));}
        root.addView(metrics,new LinearLayout.LayoutParams(-1,dp(68)));return root;
    }
    private static void set(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    void refresh(){
        if(service.treadmillLive()){live();return;}
        ConsoleSession s=service.session;
        if(title!=null){set(title,(s.preview?"Preview · ":"")+(s.started?(s.intervals?"Intervals":"Quick start")+(s.running?" · Running":" · Paused"):"Ready when you are"));set(detail,s.preview?"Simulated belt · real sensors":service.treadmillStatus());set(main,s.running?"Pause":s.started?"Resume":"Quick start");end.setVisibility(s.started?View.VISIBLE:View.GONE);workout.setVisibility(s.started?View.GONE:View.VISIBLE);set(lap,s.preview?"Lap "+(s.laps()+1)+" · "+(int)(s.meters%400)+" m":"400 m track");set(lapDetail,s.preview?s.laps()+" laps · preview":"Awaiting treadmill data");mini.meters(s.meters);}
        if(large!=null)large.meters(s.meters);
        if(speedValue!=null)set(speedValue,s.preview?String.format(Locale.US,"%.1f",s.speed):"—");if(inclineValue!=null)set(inclineValue,s.preview?String.valueOf(s.incline):"—");
        for(Button b:speedButtons)select(b,s.preview&&Integer.parseInt(b.getText().toString())==s.speed);for(Button b:inclineButtons)select(b,s.preview&&Integer.parseInt(b.getText().toString())==s.incline);
        if(values[0]!=null){String[] readings={s.preview?s.incline+" lvl":"—",s.preview?String.format(Locale.US,"%.1f mph",s.running?s.speed:0):"—",time(s.elapsed),s.preview?String.format(Locale.US,"%.2f mi",s.meters/1609.344):"—",service.freshSensor()?String.valueOf(service.detector.steps()):"—",service.heartRate()>0?service.heartRate()+" bpm":"—"};for(int i=0;i<6;i++)set(values[i],readings[i]);}
        intervals(s);
    }
    private void intervals(ConsoleSession s){
        if(interval!=null){boolean show=s.intervals&&s.started;interval.setVisibility(show?View.VISIBLE:View.GONE);switcher.setVisibility(show?View.VISIBLE:View.GONE);switcher.setEnabled(s.running);set(switcher,"Switch to "+(s.phase==0?"B":"A")+" · "+(s.phase==0?s.speedB:s.speedA)+" mph");set(interval,(s.phase==0?"A · Recover":"B · Push")+" · "+(s.mode.equals("manual")?"Manual":s.mode.equals("time")?(int)Math.ceil((s.phase==0?s.limitA:s.limitB)-s.phaseElapsed)+" sec left":(int)Math.ceil((s.phase==0?s.limitA:s.limitB)-s.phaseMeters)+" m left"));}
    }
    /** Real treadmill readings, as reported by the controller. */
    private void live(){
        TreadmillState t=service.treadmill;boolean known=t.incline!=Integer.MIN_VALUE;double meters=t.meters();
        if(title!=null){set(title,(service.session.intervals&&service.session.started?"Intervals":"Treadmill")+" · "+t.label());set(detail,"Live · physical Stop and safety key always work");set(main,t.phase==TreadmillState.Phase.RUNNING?"Pause":t.phase==TreadmillState.Phase.PAUSED?"Resume":t.phase==TreadmillState.Phase.COUNTDOWN?"Starting…":t.phase==TreadmillState.Phase.STOPPED||t.phase==TreadmillState.Phase.UNKNOWN?"Quick start":t.label());boolean idle=t.phase==TreadmillState.Phase.STOPPED||t.phase==TreadmillState.Phase.UNKNOWN;end.setVisibility(t.phase==TreadmillState.Phase.RUNNING||t.phase==TreadmillState.Phase.PAUSED?View.VISIBLE:View.GONE);workout.setVisibility(idle?View.VISIBLE:View.GONE);set(lap,"Lap "+((int)(meters/400)+1)+" · "+(int)(meters%400)+" m");set(lapDetail,(int)(meters/400)+" laps · treadmill distance");mini.meters(meters);}
        intervals(service.session);
        if(large!=null)large.meters(meters);
        if(speedValue!=null)set(speedValue,String.format(Locale.US,"%.1f",t.speedMph()));if(inclineValue!=null)set(inclineValue,known?String.valueOf(t.incline):"—");
        for(Button b:speedButtons)select(b,t.phase==TreadmillState.Phase.RUNNING&&Math.abs(Integer.parseInt(b.getText().toString())-t.speedMph())<.05);for(Button b:inclineButtons)select(b,known&&Integer.parseInt(b.getText().toString())==t.incline);
        int bpm=service.heartRate()>0?service.heartRate():t.heartRate;
        if(values[0]!=null){String[] readings={known?t.incline+" lvl":"—",String.format(Locale.US,"%.1f mph",t.speedMph()),time(t.elapsed),String.format(Locale.US,"%.2f mi",t.distanceMiles()),service.freshSensor()?String.valueOf(service.detector.steps()):"—",bpm>0?bpm+" bpm":"—"};for(int i=0;i<6;i++)set(values[i],readings[i]);}
    }
    private void select(Button b,boolean selected){if(b.isSelected()!=selected){b.setSelected(selected);b.setBackground(round(selected?LIME:SOFT));b.setTextColor(selected?BG:INK);}}
    static String time(double seconds){long s=(long)seconds;return String.format(Locale.US,"%02d:%02d",s/60,s%60);}
}
