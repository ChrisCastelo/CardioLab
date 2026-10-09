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
    /** Bluetooth SIG company identifier of Garmin International. */
    private static final int GARMIN=0x0087;
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
    void start(String target,String targetName) {
        stop(); address=target; name=targetName; enabled=true; attempt=0;
        context.registerReceiver(power,new android.content.IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)); listening=true; connect();
    }
    void stop() { enabled=false; if(listening){listening=false;try{context.unregisterReceiver(power);}catch(IllegalArgumentException ignored){}} main.removeCallbacksAndMessages(null); stopScan(); close(); }
    /** Turning Bluetooth off ends a scan without any callback; start again as soon as it is back on. */
    private boolean listening;
    private final android.content.BroadcastReceiver power=new android.content.BroadcastReceiver(){@Override public void onReceive(Context c,android.content.Intent i){
        int state=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
        if(!enabled||(state!=BluetoothAdapter.STATE_ON&&state!=BluetoothAdapter.STATE_TURNING_OFF))return;
        main.removeCallbacksAndMessages(null);scan=null;close();
        if(state==BluetoothAdapter.STATE_ON){attempt=0;connect();}else listener.status("Bluetooth off");
    }};
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
     * During Activity"), so each attempt scans every advertiser (one that leaves the Heart Rate service out of its
     * advertisement is still found by address or name). It accepts the saved address or the same device name,
     * a lone Garmin advertiser listing the Heart Rate service, or after 15 s the only one listing it; the saved
     * address is then updated and the service is confirmed after connecting. Only a device seen advertising is
     * connected: a blind connect to a stale address hangs, and cancelling it leaks one of the 32 GATT client
     * slots on the console's Android 9 stack until Bluetooth restarts. The scan runs until the watch appears
     * and is renewed every 10 minutes, before Android downgrades a long scan without hardware filters.
     */
    private void connect() {
        if(!enabled)return;
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){retry("Bluetooth off");return;}
        android.bluetooth.le.BluetoothLeScanner scanner=adapter.getBluetoothLeScanner();
        if(scanner==null){connectTo(adapter,address);return;}
        listener.status("Searching for "+(name==null||name.isEmpty()?"heart-rate broadcast":name)+"…");
        java.util.HashSet<String> seen=new java.util.HashSet<>(),heartRate=new java.util.HashSet<>();
        scan=new android.bluetooth.le.ScanCallback(){
            @Override public void onScanResult(int type,android.bluetooth.le.ScanResult r){main.post(()->{
                if(scan!=this||!enabled)return;
                BluetoothDevice d=r.getDevice();android.bluetooth.le.ScanRecord record=r.getScanRecord();String n=record!=null&&record.getDeviceName()!=null?record.getDeviceName():d.getName();
                boolean garmin=record!=null&&record.getManufacturerSpecificData(GARMIN)!=null;
                boolean hr=record!=null&&record.getServiceUuids()!=null&&record.getServiceUuids().contains(new android.os.ParcelUuid(SERVICE));
                boolean same=d.getAddress().equals(address)||(name!=null&&!name.isEmpty()&&n!=null&&n.trim().equalsIgnoreCase(name.trim()));
                if(hr)heartRate.add(d.getAddress());
                if(seen.add(d.getAddress())&&(hr||garmin||same))android.util.Log.i("CardioHR","Advertiser name="+n+" garmin="+garmin+" heartRate="+hr+" matches="+same);
                if(!same&&hr&&garmin&&heartRate.size()==1)same=true;
                if(same)use(adapter,d);
            });}
            @Override public void onScanFailed(int code){main.post(()->{
                if(scan!=this)return;
                scan=null;retry(code==SCAN_FAILED_APPLICATION_REGISTRATION_FAILED?"Bluetooth has no free connections · turn Bluetooth off and on":"Scan failed ("+code+")");
            });}
        };
        try{
            scanner.startScan(null,new android.bluetooth.le.ScanSettings.Builder().setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan);
        }catch(SecurityException|IllegalStateException e){scan=null;connectTo(adapter,address);return;}
        android.bluetooth.le.ScanCallback expected=scan;
        // A lone heart-rate broadcaster is taken as the watch: its activity broadcast may carry no name and a new address.
        main.postDelayed(()->{if(scan==expected&&heartRate.size()==1){android.util.Log.i("CardioHR","Using the only HR broadcaster");use(adapter,adapter.getRemoteDevice(heartRate.iterator().next()));}},15000);
        main.postDelayed(()->{if(scan==expected){stopScan();connect();}},600_000);
    }
    private void use(BluetoothAdapter adapter,BluetoothDevice d){
        stopScan();main.removeCallbacksAndMessages(null);if(!d.getAddress().equals(address)){address=d.getAddress();listener.moved(address);}
        connectTo(adapter,address);
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
            main.postDelayed(()->{if(active==expected)retry("Connection timed out");},45000);
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
                // The watch is in range but not broadcasting heart rate yet; look again in a minute.
                main.removeCallbacksAndMessages(null); close(); listener.status("No heart-rate broadcast · turn on Broadcast Heart Rate"); main.postDelayed(HeartRateClient.this::connect,60000); return;
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
