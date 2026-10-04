package com.cardio.diagnostic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.graphics.Color;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A separate, bounded GATT probe. No proprietary commands, firmware updates or pairing changes. */
@SuppressLint("MissingPermission")
public final class DiagnosticActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt active;
    private EditText address;
    private TextView output;
    private Button connect;
    private final StringBuilder report = new StringBuilder();
    private final ArrayDeque<BluetoothGattCharacteristic> reads = new ArrayDeque<>();
    private BluetoothGattCharacteristic heart;
    private int measurements;
    private long started;
    private int generation;
    private static final UUID HR_SERVICE = uuid("180d"), HR_MEASUREMENT = uuid("2a37"), CCC = uuid("2902");
    private static UUID uuid(String shortId) { return UUID.fromString("0000"+shortId+"-0000-1000-8000-00805f9b34fb"); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36,36,36,36); root.setBackgroundColor(Color.rgb(10,20,28));
        TextView title = new TextView(this); title.setText("Cardio · Bluetooth diagnosis"); title.setTextSize(24); title.setTextColor(Color.WHITE); root.addView(title);
        TextView description = new TextView(this); description.setText("Inspect the selected device. Read device information and listen for standard heart-rate notifications for 20 seconds."); description.setTextColor(Color.LTGRAY); root.addView(description);
        address = new EditText(this); address.setSingleLine(); address.setHint("Bluetooth address"); address.setTextColor(Color.WHITE);
        String target = getIntent().getStringExtra("target"); if (target != null) address.setText(target); root.addView(address);
        connect = new Button(this); connect.setText("Connect and inspect"); root.addView(connect); connect.setOnClickListener(v -> beginWithPermission());
        Button disconnect = new Button(this); disconnect.setText("Disconnect"); root.addView(disconnect); disconnect.setOnClickListener(v -> finishProbe("Stopped by user"));
        ScrollView scroll = new ScrollView(this); output = new TextView(this); output.setTextSize(12); output.setTextColor(Color.rgb(189,250,118)); output.setTextIsSelectable(true); scroll.addView(output); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        if (getIntent().getBooleanExtra("connect",false)) main.post(this::beginWithPermission);
    }

    private void beginWithPermission() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1); return;
        }
        begin();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==1 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) begin();
        else log("Bluetooth permission was not granted.");
    }
    private void log(String line) {
        String entry = String.format(Locale.US,"%6.1fs %s",started==0?0:(SystemClock.elapsedRealtime()-started)/1000.0,line);
        report.append(entry).append('\n'); output.setText(report);
        android.util.Log.i("CardioBLE",entry);
        // Small diagnostic report only; no raw notification payloads or device addresses.
        try (Writer out = new OutputStreamWriter(openFileOutput("last-report.txt", MODE_PRIVATE),StandardCharsets.UTF_8)) { out.write(report.toString()); }
        catch (IOException e) { android.util.Log.e("CardioBLE","Report could not be saved",e); }
    }
    private void begin() {
        finishConnection(); report.setLength(0); started=SystemClock.elapsedRealtime(); measurements=0; heart=null; reads.clear();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter=manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){log("Bluetooth is unavailable or turned off.");return;}
        String target=address.getText().toString().trim();
        if(!BluetoothAdapter.checkBluetoothAddress(target)){log("Enter a valid Bluetooth address.");return;}
        connect.setEnabled(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        log("Android "+Build.VERSION.RELEASE+"; direct BLE connection; existing pairing preserved.");
        final int token=generation;
        try { active=adapter.getRemoteDevice(target).connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE); }
        catch(SecurityException|IllegalArgumentException e){finishProbe("Connection could not start: "+e.getClass().getSimpleName());return;}
        main.postDelayed(()->{if(token==generation&&active!=null)finishProbe("TIMEOUT: diagnostic limit reached before completion.");},45000);
    }
    private String properties(int flags) {
        List<String> names=new ArrayList<>();
        if((flags&BluetoothGattCharacteristic.PROPERTY_READ)!=0)names.add("READ");
        if((flags&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)names.add("WRITE");
        if((flags&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)names.add("WRITE_NO_RESPONSE");
        if((flags&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0)names.add("NOTIFY");
        if((flags&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0)names.add("INDICATE");
        return String.join("|",names);
    }
    private void discovered(BluetoothGatt g,int status) {
        if(g!=active)return;
        log("Service discovery status="+status+"; services="+g.getServices().size());
        if(status!=BluetoothGatt.GATT_SUCCESS){finishProbe("Service discovery failed.");return;}
        for(BluetoothGattService s:g.getServices()) {
            log("SERVICE "+s.getUuid());
            for(BluetoothGattCharacteristic c:s.getCharacteristics()) {
                log("  CHAR "+c.getUuid()+" "+properties(c.getProperties()));
                for(BluetoothGattDescriptor d:c.getDescriptors()) log("    DESC "+d.getUuid());
                if(s.getUuid().equals(uuid("180a")) && (c.getUuid().equals(uuid("2a29"))||c.getUuid().equals(uuid("2a24"))||c.getUuid().equals(uuid("2a26"))) && (c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)reads.add(c);
            }
        }
        BluetoothGattService service=g.getService(HR_SERVICE);
        heart=service==null?null:service.getCharacteristic(HR_MEASUREMENT);
        log(heart==null?"RESULT: Standard Heart Rate Measurement (180D/2A37) is absent.":"RESULT: Standard Heart Rate Measurement (180D/2A37) is present.");
        readNext();
    }
    private void readNext() {
        if(active==null)return;
        if(!reads.isEmpty()){
            BluetoothGattCharacteristic c=reads.removeFirst();
            if(!active.readCharacteristic(c)){log("Read request rejected: "+c.getUuid());readNext();}
            return;
        }
        if(heart==null){finishProbe("Complete. Direct standard BPM streaming is unavailable in this service discovery.");return;}
        if((heart.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0){finishProbe("Heart-rate characteristic does not support notifications.");return;}
        BluetoothGattDescriptor config=heart.getDescriptor(CCC);
        if(config==null||!active.setCharacteristicNotification(heart,true)){finishProbe("Unable to enable notifications.");return;}
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if(!active.writeDescriptor(config))finishProbe("Notification configuration request rejected.");
    }
    private void measurement(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value) {
        if(g!=active||!c.getUuid().equals(HR_MEASUREMENT))return;
        if(value==null||value.length<2){log("Malformed heart-rate packet.");return;}
        boolean wide=(value[0]&1)!=0;
        if(wide&&value.length<3){log("Truncated 16-bit heart-rate packet.");return;}
        int bpm=(value[1]&255)+(wide?((value[2]&255)<<8):0); measurements++;
        log("HEART_RATE bpm="+bpm+"; notification="+measurements);
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){main.post(()->{
            if(g!=active)return;
            log("Connection status="+status+"; state="+state);
            if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){log("Connected. Discovering GATT services.");if(!g.discoverServices())finishProbe("Discovery request rejected.");}
            else if(state==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS)finishProbe("Disconnected before probe completion.");
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){main.post(()->discovered(g,status));}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){byte[] v=c.getValue();main.post(()->readResult(g,c,v,status));}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){main.post(()->readResult(g,c,value,status));}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){main.post(()->{
            if(g!=active)return;
            log("Notification subscription status="+status);
            if(status!=BluetoothGatt.GATT_SUCCESS){finishProbe("Subscription failed.");return;}
            int token=generation;
            log("Listening for 20 seconds.");
            main.postDelayed(()->{if(token==generation&&active==g)finishProbe("Complete. Heart-rate notifications received="+measurements);},20000);
        });}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] v=c.getValue();main.post(()->measurement(g,c,v));}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){main.post(()->measurement(g,c,v));}
    };
    private void readResult(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){
        if(g!=active)return;
        log("DEVICE_INFO "+c.getUuid()+" status="+status+" value="+(status==BluetoothGatt.GATT_SUCCESS&&value!=null?new String(value,StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]",""):"unavailable"));
        readNext();
    }
    private void finishProbe(String message){log(message);finishConnection();}
    private void finishConnection(){
        generation++; main.removeCallbacksAndMessages(null);
        BluetoothGatt g=active;active=null;
        if(g!=null){try{g.disconnect();g.close();}catch(SecurityException ignored){}}
        if(connect!=null)connect.setEnabled(true);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override protected void onStop(){if(active!=null)finishProbe("Probe stopped because diagnostic left the foreground.");super.onStop();}
    @Override protected void onDestroy(){finishConnection();super.onDestroy();}
}
