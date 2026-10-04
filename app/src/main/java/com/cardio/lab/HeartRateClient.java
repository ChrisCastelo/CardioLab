package com.cardio.lab;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.util.UUID;

/** Foreground BLE connection, serialized callbacks, bounded retries, no vendor commands. */
@SuppressLint("MissingPermission")
final class HeartRateClient {
    interface Listener { void status(String text); void measurement(int bpm); }
    private static UUID uuid(String s) { return UUID.fromString("0000"+s+"-0000-1000-8000-00805f9b34fb"); }
    private static final UUID SERVICE=uuid("180d"), MEASUREMENT=uuid("2a37"), CCC=uuid("2902");
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt active;
    private String address;
    private boolean enabled;
    private long lastPacket;
    private int attempt;
    HeartRateClient(Context context, Listener listener) { this.context=context; this.listener=listener; }
    void start(String target) { stop(); address=target; enabled=true; attempt=0; connect(); }
    void stop() { enabled=false; main.removeCallbacksAndMessages(null); close(); }
    private void close() {
        BluetoothGatt old=active; active=null;
        if(old!=null) { try { old.disconnect(); old.close(); } catch(SecurityException ignored) {} }
    }
    private void retry(String reason) {
        if(!enabled)return;
        main.removeCallbacksAndMessages(null); close();
        int delay=Math.min(30, 3 << Math.min(attempt++,3));
        listener.status(reason+" · retry in "+delay+"s");
        main.postDelayed(this::connect, delay*1000L);
    }
    private void connect() {
        if(!enabled)return;
        listener.status("Connecting…");
        try {
            BluetoothManager manager=context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){retry("Bluetooth off");return;}
            active=adapter.getRemoteDevice(address).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
            BluetoothGatt expected=active;
            main.postDelayed(()->{if(active==expected)retry("Connection timed out");},25000);
        } catch(SecurityException e) { enabled=false; close(); listener.status("Nearby Devices permission needed"); }
          catch(IllegalArgumentException e) { enabled=false; close(); listener.status("Select a heart-rate device"); }
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) { main.post(()->{
            if(g!=active)return;
            if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED) {
                listener.status("Finding heart-rate feed…");
                if(!g.discoverServices())retry("Discovery failed");
            } else if(state==BluetoothProfile.STATE_DISCONNECTED||status!=0)retry("Disconnected");
        }); }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) { main.post(()->{
            if(g!=active)return;
            if(status!=0){retry("Discovery failed");return;}
            BluetoothGattService service=g.getService(SERVICE);
            BluetoothGattCharacteristic heart=service==null?null:service.getCharacteristic(MEASUREMENT);
            BluetoothGattDescriptor ccc=heart==null?null:heart.getDescriptor(CCC);
            if(heart==null||ccc==null||(heart.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0) {
                stop(); listener.status("No standard HR feed · enable broadcast and reconnect"); return;
            }
            if(!g.setCharacteristicNotification(heart,true)){retry("Subscription failed");return;}
            ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if(!g.writeDescriptor(ccc))retry("Subscription failed");
        }); }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status) { main.post(()->{
            if(g!=active)return;
            if(status!=0){retry("Subscription failed");return;}
            main.removeCallbacksAndMessages(null); attempt=0; lastPacket=SystemClock.elapsedRealtime();
            listener.status("Connected · waiting for BPM"); main.postDelayed(watchdog,5000);
        }); }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value) { final byte[] copy=value.clone(); main.post(()->received(g,c,copy)); }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c) { byte[] v=c.getValue(); if(v!=null) { final byte[] copy=v.clone(); main.post(()->received(g,c,copy)); } }
    };
    private void received(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value) {
        if(g!=active||!c.getUuid().equals(MEASUREMENT))return;
        lastPacket=SystemClock.elapsedRealtime(); int bpm=HeartRatePacket.bpm(value);
        listener.status(bpm>0?"Live · Bluetooth":"No valid HR · check watch contact"); listener.measurement(bpm);
    }
    private final Runnable watchdog=new Runnable(){public void run(){
        if(!enabled||active==null)return;
        if(SystemClock.elapsedRealtime()-lastPacket>20000){retry("No broadcast received");return;}
        main.postDelayed(this,5000);
    }};
}
