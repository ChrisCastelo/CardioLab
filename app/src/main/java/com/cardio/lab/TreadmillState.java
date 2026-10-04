package com.cardio.lab;

import java.util.*;
import java.util.regex.*;

/**
 * Treadmill state rebuilt from received controller frames (live serial bytes or the stock
 * app's SearialPortManager RCV log lines). Pure Java, no Android types.
 * The only frames CardioLab may send are built here: the A0 heartbeat and verbatim echoes of
 * D0/D3 notifications, exactly as the stock app does. No start, speed or incline command exists.
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
    /** The stock heartbeat, F0 A0 01 counter checksum, sent once per second. */
    public static byte[] heartbeat(int counter){byte[] f={(byte)0xF0,(byte)0xA0,1,(byte)counter,0};f[4]=(byte)((0xF0+0xA0+1+(counter&255))&255);return f;}
    private void drop(int n){System.arraycopy(buffer,n,buffer,0,length-n);length-=n;}
    private static int u16(byte[] p,int i){return (p[i]&255)<<8|(p[i+1]&255);}
    private void apply(int op,byte[] p){
        if(op==0xD0&&p.length>=1)phase=phase(p[0]&255);
        else if(op==0xD1&&p.length>=9){elapsed=u16(p,0);distanceRaw=(u16(p,2)<<16)|u16(p,4);calories=u16(p,6);heartRate=p[8]&255;}
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
