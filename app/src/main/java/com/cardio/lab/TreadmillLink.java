package com.cardio.lab;

import android.os.*;
import android.util.Log;
import java.io.*;
import java.util.*;

/**
 * Owns /dev/ttyS3 while CardioLab is in front, replacing the stock app's link.
 * Sends only the A0 heartbeat and echoes of D0/D3 notifications (see TreadmillState); the belt is
 * still started, stopped and adjusted with the treadmill's own buttons.
 * The stock app closes the port when another app comes to the front. Its SearialPortManager log is
 * followed so the port is opened only after its transmissions stop, and released if they resume.
 */
final class TreadmillLink {
    interface Listener {void received(byte[] data,int count);void changed(String status);}
    private static final String PORT="/dev/ttyS3";
    private static final long STOCK_QUIET_MS=3000;
    private final Listener listener;
    private final Handler main;
    private final HandlerThread thread=new HandlerThread("treadmill-link");
    private final Handler link;
    private volatile boolean wanted,closing;
    private volatile long stockSentAt;
    private long wantedAt;
    private FileOutputStream out;
    private FileInputStream in;
    private Thread reader,stockLog;
    private java.lang.Process logcat;
    private int counter;
    private volatile long heartbeatPausedUntil;
    String status="Treadmill link idle";

    TreadmillLink(Listener listener,Handler main){
        this.listener=listener;this.main=main;thread.start();link=new Handler(thread.getLooper());
        stockSentAt=SystemClock.elapsedRealtime();followStockLog();link.post(tick);
    }
    /** CardioLab is on screen (activity or video overlay) and may own the link. */
    void want(boolean value){link.post(()->{if(value&&!wanted)wantedAt=SystemClock.elapsedRealtime();wanted=value;});}
    boolean open(){return out!=null;}
    /** Debug builds only: withhold heartbeats to observe the controller's link-loss behaviour. */
    void pauseHeartbeat(long ms){heartbeatPausedUntil=SystemClock.elapsedRealtime()+ms;Log.w("TreadmillLink","heartbeat paused for "+ms+" ms");}
    void echo(List<byte[]> frames){if(!frames.isEmpty())link.post(()->{for(byte[] f:frames)write(f);});}
    void close(){closing=true;link.post(()->{release("Treadmill link closed");thread.quitSafely();});if(logcat!=null)logcat.destroy();}

    private final Runnable tick=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();boolean stockQuiet=now-stockSentAt>STOCK_QUIET_MS;
        if(out!=null&&!stockQuiet)release("Stock app took the treadmill link back");
        else if(out!=null&&!wanted)release("Treadmill link released");
        else if(out==null&&wanted&&!stockQuiet)report("Waiting for the stock app to release the treadmill");
        else if(out==null&&wanted&&now-wantedAt>=1000)acquire();
        if(out!=null&&now>=heartbeatPausedUntil){counter=(counter+1)&255;write(TreadmillState.heartbeat(counter));}
        if(!closing)link.postDelayed(this,1000);
    }};
    private void acquire(){
        try{
            new ProcessBuilder("stty","-F",PORT,"9600","cs8","-cstopb","-parenb","raw","-echo").redirectErrorStream(true).start().waitFor();
        }catch(IOException|InterruptedException ignored){}
        try{
            FileInputStream input=new FileInputStream(PORT);out=new FileOutputStream(PORT);in=input;
            reader=new Thread(()->{byte[] b=new byte[256];try{for(int n;(n=input.read(b))>=0;)if(n>0){byte[] copy=Arrays.copyOf(b,n);Log.i("TreadmillLink","RCV "+hex(copy));main.post(()->listener.received(copy,copy.length));}}catch(IOException ignored){}},"treadmill-read");
            reader.setDaemon(true);reader.start();report("Treadmill link open");
        }catch(IOException e){release("Treadmill port unavailable: "+e.getMessage());}
    }
    private void write(byte[] frame){if(out==null)return;try{out.write(frame);out.flush();Log.i("TreadmillLink","SNT "+hex(frame));}catch(IOException e){release("Treadmill write failed: "+e.getMessage());}}
    private void release(String why){
        try{if(out!=null)out.close();}catch(IOException ignored){}
        try{if(in!=null)in.close();}catch(IOException ignored){}
        out=null;in=null;reader=null;report(why);
    }
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02X",x&255));return s.toString();}
    private void report(String value){if(!value.equals(status))Log.i("TreadmillLink",value);if(value.equals(status))return;status=value;main.post(()->listener.changed(value));}
    /** Any stock transmission (SNT) means the stock app owns the port; never write at the same time. */
    private void followStockLog(){
        stockLog=new Thread(()->{
            while(!closing){
                try{
                    logcat=new ProcessBuilder("logcat","-v","threadtime","-T","1","-s","SearialPortManager:I").redirectErrorStream(true).start();
                    BufferedReader lines=new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                    for(String line;(line=lines.readLine())!=null&&!closing;)if(line.contains(": SNT "))stockSentAt=SystemClock.elapsedRealtime();
                }catch(IOException ignored){}
                finally{if(logcat!=null)logcat.destroy();}
                SystemClock.sleep(2000);
            }
        },"stock-serial-log");
        stockLog.setDaemon(true);stockLog.start();
    }
}
