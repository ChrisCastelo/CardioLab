package com.cardio.lab;

import java.util.*;
import java.util.regex.*;

/**
 * Treadmill state rebuilt from received controller frames (live serial bytes or the stock
 * app's SearialPortManager RCV log lines). Pure Java, no Android types.
 * The only frames CardioLab may send are built here: the A0 heartbeat, verbatim echoes of D0/D3
 * notifications (as the stock app does), and the B0 state, B1 incline and B2 speed commands.
 * A command counts as done only when the controller's own D0/D2/D3 notification confirms it.
 * Layouts and units are from docs/protocol/idle-capture-2026-10-04.md.
 */
public final class TreadmillState {
    public enum Phase {UNKNOWN,STOPPED,COUNTDOWN,RUNNING,PAUSED,SAFETY_KEY_OUT,EMERGENCY_STOP}
    private static final Pattern RCV=Pattern.compile("SearialPortManager[^:]*:\\s*RCV\\s+([0-9A-Fa-f]+)\\s*$");
    private final byte[] buffer=new byte[256];
    private int length;
    public Phase phase=Phase.UNKNOWN;
    public int speedRaw=-1,incline=Integer.MIN_VALUE,elapsed,distanceRaw,calories,heartRate,maxSpeedRaw=-1,minSpeedRaw=-1,maxIncline=-1;
    /** The 2026-10-04 controller reported miles (A1 unit code 1); A1 is only sent when the stock app connects. */
    public boolean miles=true;
    public long frames,badFrames,lastFrameAt;
    /** Command awaiting the controller's confirmation; kind is "state", "speed" or "incline". */
    public String pendingKind;
    public int pendingTarget;
    public long pendingSince;
    public static final long CONFIRM_MS=2000;

    /** Feeds one logcat line; returns true when it changed the state. */
    public boolean line(String text,long now){
        Matcher m=RCV.matcher(text);if(!m.find()||m.group(1).length()%2!=0)return false;
        String hex=m.group(1);byte[] data=new byte[hex.length()/2];
        for(int i=0;i<data.length;i++)data[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        long before=frames;bytes(data,data.length,now);return frames!=before;
    }
    /** Feeds received serial bytes; returns the complete frames the stock app acknowledges by echoing. */
    public List<byte[]> bytes(byte[] data,int count,long now){
        ArrayList<byte[]> echoes=new ArrayList<>();
        for(int i=0;i<count;i++){if(length==buffer.length)length=0;buffer[length++]=data[i];}
        while(true){
            int start=0;while(start<length&&(buffer[start]&255)!=0xF0)start++;
            drop(start);if(length<4)break;
            int size=(buffer[2]&255)+4;if(size>buffer.length){badFrames++;drop(1);continue;}if(length<size)break;
            int sum=0;for(int i=0;i<size-1;i++)sum+=buffer[i]&255;
            if((sum&255)!=(buffer[size-1]&255)){badFrames++;drop(1);continue;}
            byte[] p=new byte[size-4];System.arraycopy(buffer,3,p,0,p.length);int op=buffer[1]&255;
            if(op==0xD0||op==0xD3)echoes.add(Arrays.copyOf(buffer,size));
            drop(size);frames++;lastFrameAt=now;apply(op,p);
        }
        return echoes;
    }
    static byte[] frame(int op,int... payload){
        byte[] f=new byte[payload.length+4];f[0]=(byte)0xF0;f[1]=(byte)op;f[2]=(byte)payload.length;
        int sum=0xF0+op+payload.length;for(int i=0;i<payload.length;i++){f[3+i]=(byte)payload[i];sum+=payload[i]&255;}
        f[f.length-1]=(byte)(sum&255);return f;
    }
    /** The stock heartbeat, F0 A0 01 counter checksum, sent once per second. */
    public static byte[] heartbeat(int counter){return frame(0xA0,counter&255);}
    public boolean confirmPending(long now){return pendingKind!=null&&now-pendingSince<=CONFIRM_MS;}
    public boolean confirmFailed(long now){return pendingKind!=null&&now-pendingSince>CONFIRM_MS;}
    private byte[] pending(String kind,int target,long now,byte[] command){pendingKind=kind;pendingTarget=target;pendingSince=now;return command;}
    /** B0: 00 stop, 01 start or resume, 02 pause. Returns null when the request does not fit the current state. */
    public byte[] setState(int state,long now){
        boolean idle=phase==Phase.STOPPED||phase==Phase.UNKNOWN;
        boolean ok=state==1?idle||phase==Phase.PAUSED:state==2?phase==Phase.RUNNING:state==0&&(phase==Phase.RUNNING||phase==Phase.PAUSED);
        return ok?pending("state",state,now,frame(0xB0,state)):null;
    }
    /** B2 in mph, only while running, within the controller's reported limits (0.5–12 mph seen on 2026-10-04). */
    public byte[] setSpeed(double mph,long now){
        int min=minSpeedRaw>0?minSpeedRaw:500,max=maxSpeedRaw>0?maxSpeedRaw:12000;
        double units=miles?mph:mph*1.609344;
        if(phase!=Phase.RUNNING||!Double.isFinite(units)||Math.round(units*1000)<min||Math.round(units*1000)>max)return null;
        int raw=(int)Math.round(units*1000);
        int wire=raw+5; // the stock encoder adds 5 to the scaled value
        return pending("speed",raw,now,frame(0xB2,wire>>8&255,wire&255));
    }
    /** B1 incline level, only while running, within 0 and the reported maximum (12 seen on 2026-10-04). */
    public byte[] setIncline(int level,long now){
        int max=maxIncline>0?maxIncline:12;
        return phase==Phase.RUNNING&&level>=0&&level<=max?pending("incline",level,now,frame(0xB1,level)):null;
    }
    private void drop(int n){System.arraycopy(buffer,n,buffer,0,length-n);length-=n;}
    private static int u16(byte[] p,int i){return (p[i]&255)<<8|(p[i+1]&255);}
    private void apply(int op,byte[] p){
        if(op==0xD0&&p.length>=1){phase=phase(p[0]&255);if("state".equals(pendingKind)&&(p[0]&255)==pendingTarget||"state".equals(pendingKind)&&pendingTarget==1&&phase==Phase.COUNTDOWN)pendingKind=null;}
        if(op==0xD2&&p.length>=1&&"incline".equals(pendingKind)&&p[0]==pendingTarget)pendingKind=null;
        if(op==0xD3&&p.length>=2&&"speed".equals(pendingKind)&&Math.abs(u16(p,0)-pendingTarget)<=50)pendingKind=null;
        if(op==0xD1&&p.length>=9){elapsed=u16(p,0);distanceRaw=(u16(p,2)<<16)|u16(p,4);calories=u16(p,6);heartRate=p[8]&255;}
        else if(op==0xD2&&p.length>=1)incline=p[0];
        else if(op==0xD3&&p.length>=2)speedRaw=u16(p,0);
        else if(op==0xA1&&p.length>=7&&(p[6]==0||p[6]==1))miles=p[6]==1;
        else if(op==0xA3&&p.length>=6){maxIncline=p[0];maxSpeedRaw=u16(p,2);minSpeedRaw=u16(p,4);}
    }
    static Phase phase(int code){
        switch(code){case 0:return Phase.STOPPED;case 1:return Phase.RUNNING;case 2:return Phase.PAUSED;case 0x11:return Phase.COUNTDOWN;case 0xAA:return Phase.SAFETY_KEY_OUT;case 0xEA:return Phase.EMERGENCY_STOP;default:return Phase.UNKNOWN;}
    }
    /** Frames arrive every second (heartbeat echo and D1), so a 3 s gap means the feed is gone. */
    public boolean live(long now){return lastFrameAt>0&&now-lastFrameAt<=3000;}
    public boolean moving(){return phase==Phase.RUNNING||phase==Phase.COUNTDOWN;}
    private double toMiles(double v){return miles?v:v/1.609344;}
    /** Last reported belt setpoint in mph; D3 arrives only on change. Zero unless running. */
    public double speedMph(){return phase==Phase.RUNNING&&speedRaw>=0?toMiles(speedRaw/1000.0):0;}
    public double distanceMiles(){return toMiles(distanceRaw/1000.0);}
    public double meters(){return distanceMiles()*1609.344;}
    public String label(){
        switch(phase){case STOPPED:return "Stopped";case COUNTDOWN:return "Starting";case RUNNING:return "Running";case PAUSED:return "Paused";case SAFETY_KEY_OUT:return "Safety key out";case EMERGENCY_STOP:return "Emergency stop";default:return "Connected";}
    }
}
