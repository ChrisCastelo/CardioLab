package com.cardio.lab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native console. HOME capable, but changing the default remains a separate rollout step. */
public final class ConsoleActivity extends Activity implements ConsoleService.Listener {
    private ConsoleService service;
    private ConsoleUi ui;
    private boolean bound,visible,track;
    private int footerHeight;
    private LinearLayout root;
    private View footer;
    private TextView status;
    private CalibrationWizard calibration;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){service=((ConsoleService.LocalBinder)binder).service();service.hideOverlay();service.consoleVisible(true);build();service.listen(ConsoleActivity.this);handleIntent();}
        public void onServiceDisconnected(ComponentName name){service=null;}
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(ConsoleUi.BG);getWindow().setNavigationBarColor(ConsoleUi.BG);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(state!=null)track=state.getBoolean("track");}
    @Override protected void onStart(){super.onStart();visible=true;Intent intent=new Intent(this,ConsoleService.class);startForegroundService(intent);bound=bindService(intent,connection,BIND_AUTO_CREATE);}
    @Override protected void onResume(){super.onResume();getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);if(service!=null)service.hideOverlay();}
    @Override protected void onPause(){if(calibration!=null)calibration.cancel();super.onPause();}
    @Override protected void onStop(){visible=false;if(service!=null){service.unlisten(this);service.consoleVisible(false);}if(bound){unbindService(connection);bound=false;}service=null;super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("track",track);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(service!=null)handleIntent();}
    private void handleIntent(){String action=getIntent().getStringExtra("action");getIntent().removeExtra("action");if(action!=null)open(action);}
    private void build(){
        ui=new ConsoleUi(this,service,this::open);root=ui.col();root.setBackgroundColor(ConsoleUi.BG);root.addView(ui.header(),new LinearLayout.LayoutParams(-1,ui.headerHeight()));
        LinearLayout stage=new LinearLayout(this);stage.addView(ui.rail(false),new LinearLayout.LayoutParams(ui.railWidth(),-1));
        LinearLayout center=ui.col();center.setGravity(Gravity.CENTER);center.setPadding(ui.dp(28),ui.dp(16),ui.dp(28),ui.dp(16));
        if(track){ui.large=new TrackView(this);center.addView(ui.large,new LinearLayout.LayoutParams(-1,0,1));TextView description=ui.text("400 m track · simulated distance in preview",16,ConsoleUi.MUTED);description.setGravity(Gravity.CENTER);center.addView(description);center.addView(ui.button("Back to YouTube",false,()->{track=false;build();}),new LinearLayout.LayoutParams(ui.dp(220),ui.dp(52)));}
        else{
            TextView wordmark=ui.text("YouTube",40,ConsoleUi.INK);wordmark.setGravity(Gravity.CENTER);center.addView(wordmark);TextView description=ui.text("Your video. Your workout.",20,ConsoleUi.MUTED);LinearLayout.LayoutParams desc=new LinearLayout.LayoutParams(-2,-2);desc.topMargin=ui.dp(10);desc.bottomMargin=ui.dp(30);center.addView(description,desc);
            center.addView(ui.button("Open YouTube",true,this::youtube),new LinearLayout.LayoutParams(ui.dp(250),ui.dp(60)));
            TextView sub=ui.text("Workout controls stay around the video",13,ConsoleUi.MUTED);sub.setGravity(Gravity.CENTER);sub.setPadding(0,ui.dp(18),0,0);center.addView(sub);
        }
        status=ui.text(service.message,12,ConsoleUi.MUTED);status.setGravity(Gravity.CENTER);status.setPadding(0,ui.dp(20),0,0);center.addView(status);
        stage.addView(center,new LinearLayout.LayoutParams(0,-1,1));stage.addView(ui.rail(true),new LinearLayout.LayoutParams(ui.railWidth(),-1));root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));footer=ui.footer();footerHeight=ui.footerHeight();root.addView(footer,new LinearLayout.LayoutParams(-1,footerHeight));setContentView(root);ui.refresh();
    }
    @Override public void changed(){if(!visible||ui==null||service==null)return;ui.refresh();String note=service.treadmillLive()&&service.message.startsWith("Controller not verified")?"Treadmill link active · physical Stop and safety key always work":service.message;if(!note.contentEquals(status.getText()))status.setText(note);if(footerHeight!=ui.footerHeight()){footerHeight=ui.footerHeight();footer.setLayoutParams(new LinearLayout.LayoutParams(-1,footerHeight));}}
    private void open(String action){if(service==null)return;switch(action){
        case "home":break;
        case "pauseHeartbeat":service.pauseHeartbeat(15000);break;
        case "track":track=!track;build();break;
        case "expandTrack":track=true;build();break;
        case "sensors":settings();break;
        case "audio":startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));break;
        case "workout":workout();break;
        case "connection":new AlertDialog.Builder(this).setTitle("Treadmill integration pending").setMessage("We still need the controller identity, speed/incline limits, safety-key states, and physical button events from the original harness. This build sends no treadmill commands.\n\nPreview mode lets you try the interface with simulated speed and distance. Steps and heart rate remain real sensor readings.").setPositiveButton("Enable preview",(d,w)->service.enablePreview(true)).setNegativeButton("Close",null).show();break;
    }}
    private void settings(){
        ConsoleService current=service;
        String calibrationState=getSharedPreferences("MainActivity",MODE_PRIVATE).getString("calibration","Not calibrated on this screen");
        String info=String.format(Locale.US,"Accelerometer: %s · %.0f Hz\nThreshold: %.3f m/s² · %s\nHeart rate: %s\n%s",current.freshSensor()?"Live":"No recent samples",current.sensorRate,current.detector.threshold(),calibrationState,current.deviceName.isEmpty()?"None selected":current.deviceName,current.heartStatus);
        String[] choices={"Select Garmin / heart-rate sensor","Calibrate vibrations · 2 stages","Bluetooth audio settings","Android settings",current.session.preview?"Turn preview mode off":"Enable preview mode","Sensor lab and saved sessions","Close CardioLab"};
        new AlertDialog.Builder(this).setTitle("Sensors & console").setItems(choices,(d,which)->{
            switch(which){case 0:chooseHeart();break;case 1:calibrate();break;case 2:open("audio");break;case 3:startActivity(new Intent(Settings.ACTION_SETTINGS));break;case 4:current.enablePreview(!current.session.preview);break;case 5:current.action("end");stopService(new Intent(this,ConsoleService.class));startActivity(new Intent(this,MainActivity.class));break;case 6:closeConsole();break;}
        }).setNeutralButton("Live diagnostics",(d,w)->new AlertDialog.Builder(this).setTitle("Live sensor snapshot").setMessage(info+"\n\nGarmin broadcasts directly over standard Bluetooth HR; CardioLab does not use Garmin Connect APIs.\nAudio pairing and reconnection are managed by Android.").setPositiveButton("Close",null).show()).setNegativeButton("Close",null).show();
    }
    private void calibrate(){
        if(!service.freshSensor()){toast("No accelerometer readings");return;}service.session.pause();service.detector.resetSignal();
        ConsoleService current=service;
        calibration=new CalibrationWizard(this,current.detector.threshold(),new CalibrationWizard.Listener(){
            public void applied(StepCalibration.Result result){current.detector.setThreshold(result.threshold);current.detector.resetSignal();getSharedPreferences("MainActivity",MODE_PRIVATE).edit().putFloat("threshold",(float)result.threshold).putString("calibration","Two-stage calibrated").putLong("calibration_date",System.currentTimeMillis()).putInt("calibration_reference",result.reference).putInt("calibration_after",result.after).apply();toast("Calibration applied. Check a separate counted walk.");}
            public void closed(){current.calibrationSamples=null;calibration=null;}
        });
        current.calibrationSamples=e->{if(calibration!=null)calibration.sample(e.timestamp,e.values[0],e.values[1],e.values[2]);};calibration.show();
    }
    private boolean bluetoothPermission(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    @SuppressLint("MissingPermission") private void chooseHeart(){
        if(!bluetoothPermission()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},42);return;}
        BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();if(adapter==null){toast("Bluetooth unavailable");return;}if(!adapter.isEnabled()){startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));return;}
        ArrayList<BluetoothDevice> devices=new ArrayList<>();for(BluetoothDevice d:adapter.getBondedDevices())if(d.getType()!=BluetoothDevice.DEVICE_TYPE_CLASSIC)devices.add(d);
        devices.sort((a,b)->String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
        ArrayList<String> names=new ArrayList<>();for(BluetoothDevice d:devices)names.add(d.getName()==null?d.getAddress():d.getName());names.add("Pair a device in Android settings");names.add("Disconnect heart-rate sensor");
        new AlertDialog.Builder(this).setTitle("Paired heart-rate devices").setItems(names.toArray(new String[0]),(d,i)->{if(i==devices.size())startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));else if(i>devices.size())service.selectHeart("","");else{BluetoothDevice device=devices.get(i);service.selectHeart(device.getAddress(),device.getName()==null?"Heart-rate sensor":device.getName());}}).setNegativeButton("Close",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==42&&bluetoothPermission()&&service!=null)chooseHeart();}
    private EditText number(LinearLayout parent,String name,double value){parent.addView(ui.text(name,14,ConsoleUi.MUTED));EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);field.setText(String.valueOf(value));parent.addView(field);return field;}
    private void workout(){
        if(!service.session.preview){open("connection");return;}if(service.session.started){toast("End the current session before changing the workout");return;}
        ConsoleSession s=service.session;LinearLayout content=ui.col();content.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(12));Spinner mode=new Spinner(this);String[] modes={"Timer · seconds","Distance · meters","Manual switching"};mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));mode.setSelection(s.mode.equals("time")?0:s.mode.equals("distance")?1:2);content.addView(mode);
        LinearLayout row=new LinearLayout(this),a=ui.col(),b=ui.col();a.setPadding(0,0,ui.dp(12),0);row.addView(a,new LinearLayout.LayoutParams(0,-2,1));row.addView(b,new LinearLayout.LayoutParams(0,-2,1));content.addView(row);
        EditText speedA=number(a,"Speed A · mph",s.speedA),speedB=number(b,"Speed B · mph",s.speedB),limitA=number(a,"A duration / distance",s.limitA),limitB=number(b,"B duration / distance",s.limitB);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){limitA.setEnabled(position!=2);limitB.setEnabled(position!=2);}});
        ScrollView scroll=new ScrollView(this);scroll.addView(content);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Two-speed intervals · PREVIEW").setView(scroll).setPositiveButton("Start intervals",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{s.configure(new String[]{"time","distance","manual"}[mode.getSelectedItemPosition()],Double.parseDouble(speedA.getText().toString()),Double.parseDouble(speedB.getText().toString()),Double.parseDouble(limitA.getText().toString()),Double.parseDouble(limitB.getText().toString()));service.action("main");dialog.dismiss();}catch(IllegalArgumentException|IllegalStateException error){toast(error.getMessage());}}));dialog.show();
    }
    private void youtube(){
        if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("Allow workout controls over video").setMessage("Enable ‘Allow display over other apps’ for Cardio Lab, then return and tap Open YouTube.").setPositiveButton("Open permission settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Cancel",null).show();return;}
        if(!service.showOverlay()){toast(service.message);return;}
        Intent video=new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.youtube.com/"));
        try{startActivity(video);}catch(ActivityNotFoundException e){startActivity(new Intent(this,VideoActivity.class).putExtra("top",ui.headerHeight()).putExtra("bottom",ui.footerHeight()).putExtra("rail",ui.railWidth()));}
    }
    private void closeConsole(){if(service!=null){service.action("end");service.hideOverlay();}stopService(new Intent(this,ConsoleService.class));finish();}
    @Override public void onBackPressed(){new AlertDialog.Builder(this).setTitle("Close CardioLab?").setMessage("End the preview session and stop the sensor service?").setPositiveButton("Close",(d,w)->closeConsole()).setNegativeButton("Keep open",null).show();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
