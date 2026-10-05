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
 * connect every 30 s to a paired audio device that is not connected yet.
 * BluetoothA2dp.connect is hidden on Android 9 (greylisted), hence reflection; setPriority is not reachable.
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
        connect(speaker);
    }
    private void connect(BluetoothDevice device){
        try{Method m=BluetoothA2dp.class.getMethod("connect",BluetoothDevice.class);m.invoke(a2dp,device);}
        catch(ReflectiveOperationException e){Log.w("SpeakerKeeper","connect unavailable",e);}
    }
    /** Name of the connected speaker, or null; read live so the console updates as soon as it connects. */
    String connected(){
        try{if(a2dp==null)return null;List<BluetoothDevice> c=a2dp.getConnectedDevices();return c.isEmpty()?null:name(c.get(0));}catch(RuntimeException e){return null;}
    }
    private static String name(BluetoothDevice d){return d.getName()==null?d.getAddress():d.getName();}
}
