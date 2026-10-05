package com.cardio.lab;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Keeps the paired Bluetooth speaker connected. Android 9 only reconnects audio when Bluetooth starts,
 * so a speaker switched on later stays disconnected. While CardioLab runs, this asks the A2DP profile to
 * connect every 30 s to a paired audio device that is not connected yet, and marks it auto-connect.
 * BluetoothA2dp.connect/setPriority are hidden on Android 9 (greylisted), hence reflection.
 */
@SuppressLint("MissingPermission")
final class SpeakerKeeper {
    private static final long PERIOD_MS=30000;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothA2dp a2dp;
    private boolean running;
    String status="No Bluetooth speaker paired";
    SpeakerKeeper(Context context){this.context=context;}
    void start(){
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null||running)return;running=true;
        adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
            public void onServiceConnected(int profile,BluetoothProfile proxy){a2dp=(BluetoothA2dp)proxy;main.post(tick);}
            public void onServiceDisconnected(int profile){a2dp=null;}
        },BluetoothProfile.A2DP);
    }
    void stop(){running=false;main.removeCallbacksAndMessages(null);BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter!=null&&a2dp!=null)adapter.closeProfileProxy(BluetoothProfile.A2DP,a2dp);a2dp=null;}
    private final Runnable tick=new Runnable(){public void run(){if(!running)return;try{keep();}catch(RuntimeException e){Log.w("SpeakerKeeper","reconnect failed",e);}main.postDelayed(this,PERIOD_MS);}};
    private void keep(){
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null||!adapter.isEnabled()||a2dp==null)return;
        List<BluetoothDevice> connected=a2dp.getConnectedDevices();
        if(!connected.isEmpty()){status="Speaker connected: "+name(connected.get(0));return;}
        BluetoothDevice speaker=null;
        for(BluetoothDevice d:adapter.getBondedDevices()){BluetoothClass c=d.getBluetoothClass();if(c!=null&&c.getMajorDeviceClass()==BluetoothClass.Device.Major.AUDIO_VIDEO){speaker=d;break;}}
        if(speaker==null){status="No Bluetooth speaker paired";return;}
        if(a2dp.getConnectionState(speaker)!=BluetoothProfile.STATE_DISCONNECTED)return;
        status="Looking for "+name(speaker)+"…";
        call("setPriority",speaker,1000); // BluetoothProfile.PRIORITY_AUTO_CONNECT
        call("connect",speaker,null);
    }
    private void call(String method,BluetoothDevice device,Integer value){
        try{Method m=value==null?BluetoothA2dp.class.getMethod(method,BluetoothDevice.class):BluetoothA2dp.class.getMethod(method,BluetoothDevice.class,int.class);
            if(value==null)m.invoke(a2dp,device);else m.invoke(a2dp,device,value);}
        catch(ReflectiveOperationException e){Log.w("SpeakerKeeper",method+" unavailable",e);}
    }
    private static String name(BluetoothDevice d){return d.getName()==null?d.getAddress():d.getName();}
}
