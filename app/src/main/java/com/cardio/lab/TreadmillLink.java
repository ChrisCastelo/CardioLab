package com.cardio.lab;

import android.os.*;
import android.util.Log;
import java.io.*;
import java.util.*;

/**
 * Owns /dev/ttyS3 while CardioLab is in front, replacing the stock app's link.
 * Sends only frames built by TreadmillState: the A0 heartbeat, echoes of D0/D3 notifications and
 * the B0/B1/B2 state, incline and speed commands. If CardioLab stops, the heartbeat stops and the
 * controller ends the workout (verified 2026-10-04 at 0.5 mph).
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
    private static final Set<Integer> SENDABLE=new HashSet<>(Arrays.asList(0xA0,0xA1,0xA3,0xA7,0x72,0x73,0xB0,0xB1,0xB2,0xD0,0xD3));
    /** Returns false when the link is closed; nothing outside the allowed opcodes is ever written. */
    boolean send(byte[] frame){if(out==null||frame==null||frame.length<4||!SENDABLE.contains(frame[1]&255))return false;link.post(()->write(frame));return true;}
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
            FileInputStream input=new FileInputStream(PORT);out=new FileOutputStream(PORT);in=input;
            // Configure while our descriptor holds the tty open: this driver resets termios on last close.
            // Plain 8N1 with every input/output translation off. After a reboot the port came up with iuclc
            // (0xD1 read as 0xF1), icrnl and ixon/ixoff, which would also let a received 0x13 halt our heartbeat.
            // The firmware's busybox stty applies the whole set; toybox silently drops input flags when combined
            // with ignbrk, -crtscts or -echo. The result is read back with toybox and checked either way.
            String settings=stty(true,PORT,"9600","cs8","-cstopb","-parenb","-crtscts","clocal","cread","-ignbrk","-brkint","-icrnl","-inlcr","-igncr","-iuclc",
                "-ixon","-ixoff","-ixany","-imaxbel","-istrip","-inpck","-parmrk","-iutf8","-opost","-isig","-icanon","-iexten","-echo","-echoe","-echok","-echonl","min","1","time","0");
            settings=stty(false,PORT,"-a");
            if(!(settings.contains("-iuclc")&&settings.contains("-ixon")&&settings.contains("-ixoff")&&settings.contains("-ixany")&&settings.contains("-icrnl")&&settings.contains("-icanon")&&settings.contains("-echo ")&&settings.contains("9600"))){
                Log.w("TreadmillLink","port settings not applied: "+settings);release("Treadmill port could not be configured");return;
            }
            reader=new Thread(()->{byte[] b=new byte[256];try{for(int n;(n=input.read(b))>=0;)if(n>0){byte[] copy=Arrays.copyOf(b,n);Log.i("TreadmillLink","RCV "+hex(copy));main.post(()->listener.received(copy,copy.length));}}catch(IOException ignored){}},"treadmill-read");
            reader.setDaemon(true);reader.start();report("Treadmill link open");
            long delay=0;for(byte[] f:TreadmillState.handshake()){delay+=200;link.postDelayed(()->write(f),delay);}
        }catch(IOException e){release("Treadmill port unavailable: "+e.getMessage());}
    }
    private static String stty(boolean busybox,String port,String... args){
        ArrayList<String> command=new ArrayList<>(busybox?Arrays.asList("busybox","stty","-F",port):Arrays.asList("stty","-F",port));command.addAll(Arrays.asList(args));
        try{java.lang.Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder out=new StringBuilder();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));for(String l;(l=r.readLine())!=null;)out.append(l).append(' ');
            p.waitFor();return out.toString();}
        catch(IOException|InterruptedException e){return "";}
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
