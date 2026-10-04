package com.cardio.lab;

import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.*;
import java.util.*;

/** Separate opaque edge windows leave the video center genuinely touchable. */
final class OverlayControls {
    private final ConsoleService service;
    private final WindowManager manager;
    private final ConsoleUi ui;
    private final ArrayList<View> windows=new ArrayList<>();
    private int footerHeight;
    OverlayControls(ConsoleService service){this.service=service;manager=service.getSystemService(WindowManager.class);ui=new ConsoleUi(service,service,service::openConsole);}
    void show(){
        Point size=new Point();manager.getDefaultDisplay().getRealSize(size);footerHeight=ui.footerHeight();int top=ui.headerHeight(),rail=ui.railWidth();
        add(ui.header(),size.x,top,0,0);add(ui.footer(),size.x,footerHeight,0,size.y-footerHeight);
        add(ui.rail(false),rail,size.y-top-footerHeight,0,top);add(ui.rail(true),rail,size.y-top-footerHeight,size.x-rail,top);ui.refresh();
    }
    private void add(View view,int width,int height,int x,int y){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,Math.max(1,height),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x;p.y=y;p.setTitle("CardioLab edge controls");manager.addView(view,p);windows.add(view);
    }
    void refresh(){if(footerHeight!=ui.footerHeight()){close();show();}else ui.refresh();}
    void close(){for(View v:windows)try{manager.removeView(v);}catch(IllegalArgumentException ignored){}windows.clear();}
}
