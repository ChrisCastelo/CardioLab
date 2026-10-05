package com.cardio.lab;

import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Controls over video. Framed: the four opaque edge windows around CardioLab's own framed player
 * (VideoActivity), which leave its centre touchable. Compact: one slim bar at the top centre over a
 * full-screen app such as SmartTube (Back, CardioLab, the console's readings, interval switch, start/pause/resume, end);
 * a single small overlay also lets this screen compose video in hardware.
 */
final class OverlayControls {
    private final ConsoleService service;
    private final WindowManager manager;
    private final ConsoleUi ui;
    private final ArrayList<View> windows=new ArrayList<>();
    private int footerHeight;
    private final boolean compact;
    private Button main,switcher,end;
    private final TextView[] stats=new TextView[7];
    OverlayControls(ConsoleService service,boolean compact){this.service=service;this.compact=compact;manager=service.getSystemService(WindowManager.class);ui=new ConsoleUi(service,service,service::openConsole);}
    void show(){if(compact)showCompact();else showEdges();}
    private void showEdges(){
        Point size=new Point();manager.getDefaultDisplay().getRealSize(size);footerHeight=ui.footerHeight();int top=ui.headerHeight(),rail=ui.railWidth();
        add(ui.header(),size.x,top,0,0);add(ui.footer(),size.x,footerHeight,0,size.y-footerHeight);
        add(ui.rail(false),rail,size.y-top-footerHeight,0,top);add(ui.rail(true),rail,size.y-top-footerHeight,size.x-rail,top);ui.refresh();
    }
    private void showCompact(){
        Point size=new Point();manager.getDefaultDisplay().getRealSize(size);
        LinearLayout bar=new LinearLayout(service);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackground(ui.round(ConsoleUi.PANEL));bar.setPadding(ui.dp(3),ui.dp(2),ui.dp(3),ui.dp(2));
        Button back=ui.button("‹ Back",false,()->{if(!NavigationService.back())Toast.makeText(service,"Back needs the CardioLab Back button service (adb)",Toast.LENGTH_LONG).show();});
        Button home=ui.button("CardioLab",false,()->service.openConsole("home"));back.setContentDescription("Back in the video app");home.setContentDescription("Return to the CardioLab console");
        for(Button b:new Button[]{back,home}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(b==back?72:96),ui.dp(32));p.rightMargin=ui.dp(4);bar.addView(b,p);}
        // Same readings as the console's bottom bar; speed and incline change with the treadmill's own keys.
        String[] names={"SPEED","INCLINE","TIME","DISTANCE","STEPS","♥ HR","INTERVAL"};
        for(int i=0;i<stats.length;i++){LinearLayout cell=ui.col();cell.setGravity(Gravity.CENTER);TextView name=ui.text(names[i],9,i==5?ConsoleUi.RED:ConsoleUi.MUTED);stats[i]=ui.text("—",15,ConsoleUi.INK);cell.addView(name);cell.addView(stats[i]);bar.addView(cell,new LinearLayout.LayoutParams(0,-1,i==6?1.4f:1));}
        switcher=ui.button("",true,()->service.action("switch"));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ui.dp(150),ui.dp(32));sp.leftMargin=ui.dp(4);bar.addView(switcher,sp);
        main=ui.button("",true,()->service.action("main"));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(ui.dp(96),ui.dp(32));mp.leftMargin=ui.dp(4);bar.addView(main,mp);
        end=ui.button("End",false,()->service.action("end"));end.setTextColor(ConsoleUi.RED);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ui.dp(64),ui.dp(32));ep.leftMargin=ui.dp(4);bar.addView(end,ep);
        // Narrow enough to leave SmartTube's search/account buttons (left) and clock (right) visible.
        int width=Math.min(size.x-ui.dp(16),ui.dp(900));
        // Top centre: at the bottom it covered SmartTube's player messages and seek bar.
        add(bar,width,ui.dp(40),(size.x-width)/2,ui.dp(2));refreshCompact();
    }
    private void refreshCompact(){
        TreadmillState t=service.treadmill;ConsoleSession session=service.session;boolean live=service.treadmillLive();int bpm=service.heartRate()>0?service.heartRate():t.heartRate;
        boolean intervals=session.intervals&&session.started;
        String[] values={live?String.format(Locale.US,"%.1f mph",t.speedMph()):"—",live&&t.incline!=Integer.MIN_VALUE?String.valueOf(t.incline):"—",live?ConsoleUi.time(t.elapsed):"—",live?String.format(Locale.US,"%d m",Math.round(t.meters())):"—",
            service.freshSensor()?String.valueOf(service.detector.steps()):"—",bpm>0?String.valueOf(bpm):"—",
            intervals?(session.phase==0?"A":"B")+" · "+(session.mode.equals("manual")?"manual":session.mode.equals("time")?(int)Math.ceil((session.phase==0?session.limitA:session.limitB)-session.phaseElapsed)+" s":(int)Math.ceil((session.phase==0?session.limitA:session.limitB)-session.phaseMeters)+" m"):""};
        for(int i=0;i<stats.length;i++)if(!values[i].contentEquals(stats[i].getText()))stats[i].setText(values[i]);
        ((View)stats[6].getParent()).setVisibility(intervals?View.VISIBLE:View.GONE);switcher.setVisibility(intervals?View.VISIBLE:View.GONE);switcher.setEnabled(t.phase==TreadmillState.Phase.RUNNING);
        String sw=String.format(Locale.US,"Switch to %s · %.1f",session.phase==0?"B":"A",session.phase==0?session.speedB:session.speedA);if(!sw.contentEquals(switcher.getText()))switcher.setText(sw);
        String label=t.phase==TreadmillState.Phase.RUNNING?"Pause":t.phase==TreadmillState.Phase.PAUSED?"Resume":t.phase==TreadmillState.Phase.COUNTDOWN?"Starting…":"Start";
        if(!label.contentEquals(main.getText()))main.setText(label);
        end.setVisibility(t.phase==TreadmillState.Phase.RUNNING||t.phase==TreadmillState.Phase.PAUSED?View.VISIBLE:View.GONE);
    }
    private void add(View view,int width,int height,int x,int y){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,Math.max(1,height),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x;p.y=y;p.setTitle("CardioLab edge controls");manager.addView(view,p);windows.add(view);
    }
    void refresh(){if(compact){if(main!=null)refreshCompact();return;}if(footerHeight!=ui.footerHeight()){close();showEdges();}else ui.refresh();}
    void close(){for(View v:windows)try{manager.removeView(v);}catch(IllegalArgumentException ignored){}windows.clear();}
    void dismiss(){close();}
    /** Undoes the display overscan an earlier build (0.12) used to fit the video app; safe to repeat. */
    static void resetOverscan(){
        new Thread(()->{try{new ProcessBuilder("wm","overscan","reset").redirectErrorStream(true).start().waitFor();}catch(java.io.IOException|InterruptedException ignored){}},"overscan-reset").start();
    }
}
