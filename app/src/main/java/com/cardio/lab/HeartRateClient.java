package com.cardio.lab;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import java.util.UUID;

/** Foreground BLE connection, serialized callbacks, bounded retries, no vendor commands. */
@SuppressLint("MissingPermission")
final class HeartRateClient {
    interface Listener { void status(String text); void measurement(int bpm); default void moved(String address){} }
    private static UUID uuid(String s) { return UUID.fromString("0000"+s+"-0000-1000-8000-00805f9b34fb"); }
    private static final UUID SERVICE=uuid("180d"), MEASUREMENT=uuid("2a37"), CCC=uuid("2902");
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt active;
    private String address,name;
    private android.bluetooth.le.ScanCallback scan;
    private boolean enabled;
    private long lastPacket;
    private int attempt;
    HeartRateClient(Context context, Listener listener) { this.context=context; this.listener=listener; }
    void start(String target,String targetName) { stop(); address=target; name=targetName; enabled=true; attempt=0; connect(); }
    void stop() { enabled=false; main.removeCallbacksAndMessages(null); stopScan(); close(); }
    private void close() {
        BluetoothGatt old=active; active=null;
        if(old!=null) { try { old.disconnect(); old.close(); } catch(SecurityException ignored) {} }
    }
    private void retry(String reason) {
        if(!enabled)return;
        main.removeCallbacksAndMessages(null); stopScan(); close();
        int delay=Math.min(30, 3 << Math.min(attempt++,3));
        listener.status(reason+" · retry in "+delay+"s");
        main.postDelayed(this::connect, delay*1000L);
    }
    /**
     * Garmin watches advertise heart rate from a different address once a watch workout starts ("Broadcast
     * During Activity"), so each attempt first scans for the standard Heart Rate service and accepts the saved
     * address or the same device name; the saved address is then updated. Falls back to a direct connect.
     */
    private void connect() {
        if(!enabled)return;
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){retry("Bluetooth off");return;}
        android.bluetooth.le.BluetoothLeScanner scanner=adapter.getBluetoothLeScanner();
        if(scanner==null){connectTo(adapter,address);return;}
        listener.status("Searching for "+(name==null||name.isEmpty()?"heart-rate broadcast":name)+"…");
        scan=new android.bluetooth.le.ScanCallback(){@Override public void onScanResult(int type,android.bluetooth.le.ScanResult r){main.post(()->{
            if(scan!=this||!enabled)return;
            BluetoothDevice d=r.getDevice();String n=r.getScanRecord()!=null&&r.getScanRecord().getDeviceName()!=null?r.getScanRecord().getDeviceName():d.getName();
            boolean same=d.getAddress().equals(address)||(name!=null&&!name.isEmpty()&&n!=null&&n.trim().equalsIgnoreCase(name.trim()));
            if(!same)return;
            stopScan();if(!d.getAddress().equals(address)){address=d.getAddress();listener.moved(address);}
            connectTo(adapter,address);
        });}};
        try{
            scanner.startScan(java.util.Collections.singletonList(new android.bluetooth.le.ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(SERVICE)).build()),
                new android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan);
        }catch(SecurityException|IllegalStateException e){scan=null;connectTo(adapter,address);return;}
        android.bluetooth.le.ScanCallback expected=scan;
        main.postDelayed(()->{if(scan==expected){stopScan();connectTo(adapter,address);}},8000);
    }
    private void stopScan(){
        android.bluetooth.le.ScanCallback s=scan;scan=null;if(s==null)return;
        try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null&&a.getBluetoothLeScanner()!=null)a.getBluetoothLeScanner().stopScan(s);}catch(SecurityException|IllegalStateException ignored){}
    }
    private void connectTo(BluetoothAdapter adapter,String target) {
        if(!enabled)return;
        listener.status("Connecting…");
        try {
            active=adapter.getRemoteDevice(target).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
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
