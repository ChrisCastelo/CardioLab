package com.cardio.lab;

import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Controls over video. Framed: the four opaque edge windows around CardioLab's own framed player
 * (VideoActivity), which leave its centre touchable. Compact: one slim bar at the top centre over a
 * full-screen app such as SmartTube (live readings, Back, CardioLab, speed down/up, pause/resume);
 * a single small overlay also lets this screen compose video in hardware.
 */
final class OverlayControls {
    private final ConsoleService service;
    private final WindowManager manager;
    private final ConsoleUi ui;
    private final ArrayList<View> windows=new ArrayList<>();
    private int footerHeight;
    private final boolean compact;
    private TextView readings;
    private Button main;
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
        readings=ui.text("",14,ConsoleUi.INK);readings.setPadding(ui.dp(6),0,0,0);bar.addView(readings,new LinearLayout.LayoutParams(0,-2,1));
        Button slower=ui.button("−",false,()->nudge(-.5)),faster=ui.button("+",false,()->nudge(.5));slower.setContentDescription("Slower by 0.5 mph");faster.setContentDescription("Faster by 0.5 mph");
        main=ui.button("",true,()->service.action("main"));
        for(Button b:new Button[]{slower,main,faster}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(b==main?ui.dp(96):ui.dp(48),ui.dp(32));p.leftMargin=ui.dp(4);bar.addView(b,p);}
        int width=Math.min(size.x-ui.dp(16),ui.dp(740));
        // Top centre: at the bottom it covered SmartTube's player messages and seek bar.
        add(bar,width,ui.dp(36),(size.x-width)/2,ui.dp(2));refreshCompact();
    }
    private void nudge(double delta){double mph=service.treadmill.speedMph();if(mph>0)service.speed(Math.round((mph+delta)*2)/2.0);}
    private void refreshCompact(){
        TreadmillState t=service.treadmill;boolean live=service.treadmillLive();int bpm=service.heartRate()>0?service.heartRate():t.heartRate;
        String text=live?String.format(Locale.US,"%.1f mph · %s · %.2f mi%s",t.speedMph(),ConsoleUi.time(t.elapsed),t.distanceMiles(),bpm>0?" · ♥ "+bpm:""):"Treadmill not connected";
        if(!text.contentEquals(readings.getText()))readings.setText(text);
        String label=t.phase==TreadmillState.Phase.RUNNING?"Pause":t.phase==TreadmillState.Phase.PAUSED?"Resume":t.phase==TreadmillState.Phase.COUNTDOWN?"Starting…":"Start";
        if(!label.contentEquals(main.getText()))main.setText(label);
    }
    private void add(View view,int width,int height,int x,int y){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,Math.max(1,height),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x;p.y=y;p.setTitle("CardioLab edge controls");manager.addView(view,p);windows.add(view);
    }
    void refresh(){if(compact){if(readings!=null)refreshCompact();return;}if(footerHeight!=ui.footerHeight()){close();showEdges();}else ui.refresh();}
    void close(){for(View v:windows)try{manager.removeView(v);}catch(IllegalArgumentException ignored){}windows.clear();}
    void dismiss(){close();}
    /** Undoes the display overscan an earlier build (0.12) used to fit the video app; safe to repeat. */
    static void resetOverscan(){
        new Thread(()->{try{new ProcessBuilder("wm","overscan","reset").redirectErrorStream(true).start().waitFor();}catch(java.io.IOException|InterruptedException ignored){}},"overscan-reset").start();
    }
}
