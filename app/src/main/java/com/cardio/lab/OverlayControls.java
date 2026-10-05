package com.cardio.lab;

import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.*;
import java.util.*;

/**
 * Separate opaque edge windows leave the video center genuinely touchable. While they show, the display's
 * overscan is set to their size so the video app lays itself out in the center instead of underneath them
 * (needs WRITE_SECURE_SETTINGS, granted over adb; without it the app simply stays full screen).
 */
final class OverlayControls {
    private final ConsoleService service;
    private final WindowManager manager;
    private final ConsoleUi ui;
    private final ArrayList<View> windows=new ArrayList<>();
    private int footerHeight,offsetX,offsetY;
    OverlayControls(ConsoleService service){this.service=service;manager=service.getSystemService(WindowManager.class);ui=new ConsoleUi(service,service,service::openConsole);}
    void show(){
        Point size=new Point();manager.getDefaultDisplay().getRealSize(size);footerHeight=ui.footerHeight();int top=ui.headerHeight(),rail=ui.railWidth();
        // Window coordinates start inside the overscan, so the edge windows are placed at negative offsets.
        boolean fit=service.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")==android.content.pm.PackageManager.PERMISSION_GRANTED&&overscanNow(rail+","+top+","+rail+","+footerHeight);
        offsetX=fit?-rail:0;offsetY=fit?-top:0;
        add(ui.header(),size.x,top,0,0);add(ui.footer(),size.x,footerHeight,0,size.y-footerHeight);
        add(ui.rail(false),rail,size.y-top-footerHeight,0,top);add(ui.rail(true),rail,size.y-top-footerHeight,size.x-rail,top);ui.refresh();
    }
    private void add(View view,int width,int height,int x,int y){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,Math.max(1,height),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x+offsetX;p.y=y+offsetY;p.setTitle("CardioLab edge controls");manager.addView(view,p);windows.add(view);
    }
    private boolean minimized;
    /** Leaves one small button so the video app's own menus and sign-in are reachable; the treadmill link stays held. */
    void minimize(){
        close();overscanNow("reset");offsetX=offsetY=0;minimized=true;
        android.widget.Button restore=ui.button("Show controls",true,()->{close();minimized=false;show();});
        add(restore,ui.dp(150),ui.dp(44),ui.dp(8),ui.dp(8));
    }
    void refresh(){if(minimized)return;if(footerHeight!=ui.footerHeight()){close();show();}else ui.refresh();}
    void close(){for(View v:windows)try{manager.removeView(v);}catch(IllegalArgumentException ignored){}windows.clear();}
    /** Removes the controls and gives the whole display back. */
    void dismiss(){close();overscan("reset");}
    private static final java.util.concurrent.ExecutorService OVERSCAN=java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Serialized so a reset and the next set cannot apply out of order. */
    private static boolean overscanNow(String value){
        try{return OVERSCAN.submit(()->new ProcessBuilder("wm","overscan",value).redirectErrorStream(true).start().waitFor()==0).get();}catch(Exception e){return false;}
    }
    static void overscan(String value){
        OVERSCAN.execute(()->{try{new ProcessBuilder("wm","overscan",value).redirectErrorStream(true).start().waitFor();}catch(java.io.IOException|InterruptedException ignored){}});
    }
}
