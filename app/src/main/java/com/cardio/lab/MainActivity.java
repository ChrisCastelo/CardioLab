package com.cardio.lab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int BG=0xff0b171f,CARD=0xff162832,INK=0xfff0f5f4,MUTED=0xffa0b6c0,LIME=0xffbdfa76,PINK=0xfff3a1b3;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final StepDetector detector=new StepDetector();
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final ArrayList<SessionDatabase.Sample> pending=new ArrayList<>();
    private CalibrationWizard calibration;
    private SessionDatabase database;
    private Workout workout;
    private SensorManager sensors;
    private Sensor accelerometer;
    private HeartRateClient heartClient;
    private TextView timer,phase,steps,heart,stepMeta,heartMeta,sensorMeta,heartState,note;
    private Button play,stop,setup,history,source;
    private TraceView vibration,heartGraph;
    private String deviceAddress="",deviceName="",connection="Select heart-rate sensor",message="Ready";
    private boolean loading=true,busy,calibrating,foreground,storageFailed;
    private int bpm=-1;
    private long hrAt,lastSensorNs,lastSampleAt,lastCheckpoint,rateAt,rateSamples;
    private double rate;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        database=new SessionDatabase(this);
        sensors=getSystemService(SensorManager.class);accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        detector.setThreshold(getPreferences(MODE_PRIVATE).getFloat("threshold",.12f));
        deviceAddress=getPreferences(MODE_PRIVATE).getString("hr_address","");deviceName=getPreferences(MODE_PRIVATE).getString("hr_name","");
        heartClient=new HeartRateClient(this,new HeartRateClient.Listener(){
            public void status(String value){connection=value;if(!value.startsWith("Live")){bpm=-1;hrAt=0;}refresh();}
            public void measurement(int value){bpm=value;hrAt=SystemClock.elapsedRealtime();heartGraph.add(hrAt,value>0?value:Float.NaN);if(workout!=null)workout.heart(value);refresh();}
        });
        buildUi();
        storage.execute(()->{
            try{List<Workout> drafts=database.list(false);ui.post(()->{if(isDestroyed())return;if(!drafts.isEmpty()){workout=drafts.get(0);detector.restoreSteps(workout.steps);detector.setThreshold(workout.threshold);message="Recovered · paused";}loading=false;refresh();});}
            catch(Exception e){ui.post(()->{loading=false;storageError(e);});}
        });
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);return t;}
    private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?BG:INK);b.setBackground(rounded(primary?LIME:CARD));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),0,dp(10),0);return b;}
    private void buildUi(){
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(6),dp(18),dp(6));
        FrameLayout header=new FrameLayout(this);root.addView(header,new LinearLayout.LayoutParams(-1,dp(70)));
        LinearLayout clock=column();clock.setGravity(Gravity.CENTER);timer=label("00:00",48,INK);timer.setGravity(Gravity.CENTER);timer.setContentDescription("Session active time");clock.addView(timer);phase=label("Ready",11,MUTED);phase.setGravity(Gravity.CENTER);clock.addView(phase);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(260),-1,Gravity.CENTER);header.addView(clock,cp);
        TextView local=label("",11,MUTED);local.setText("Landscape · screen stays on");header.addView(local,new FrameLayout.LayoutParams(dp(200),-2,Gravity.START|Gravity.CENTER_VERTICAL));
        history=button("Sessions",false);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(110),dp(44),Gravity.END|Gravity.CENTER_VERTICAL);header.addView(history,hp);history.setOnClickListener(v->showHistory());
        LinearLayout panels=new LinearLayout(this);root.addView(panels,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=panel(),right=panel();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.rightMargin=dp(6);panels.addView(left,lp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=dp(6);panels.addView(right,rp);
        LinearLayout stepHead=new LinearLayout(this);stepHead.setGravity(Gravity.CENTER_VERTICAL);stepHead.addView(label("Steps",15,INK),new LinearLayout.LayoutParams(0,dp(28),1));stepHead.addView(label("Estimated",11,MUTED));left.addView(stepHead);
        steps=label("0",56,INK);left.addView(steps);stepMeta=label("— steps/min",12,MUTED);left.addView(stepMeta);
        sensorMeta=label("Live vibration · m/s²",11,MUTED);left.addView(sensorMeta);
        vibration=new TraceView(this,LIME,5000,true);vibration.setContentDescription("Live vibration over the last 5 seconds");left.addView(vibration,new LinearLayout.LayoutParams(-1,0,1));left.addView(chartFoot("−5 seconds"));
        LinearLayout heartHead=new LinearLayout(this);heartHead.setGravity(Gravity.CENTER_VERTICAL);heartHead.addView(label("Heart rate",15,INK),new LinearLayout.LayoutParams(0,dp(28),1));source=button("Connect sensor",false);source.setTextColor(PINK);source.setTextSize(11);heartHead.addView(source,new LinearLayout.LayoutParams(dp(175),dp(32)));source.setOnClickListener(v->chooseHeart());right.addView(heartHead);
        heart=label("—",56,PINK);right.addView(heart);heartMeta=label("Average — bpm",12,MUTED);right.addView(heartMeta);heartState=label("Heart rate trend · bpm",11,MUTED);right.addView(heartState);
        heartGraph=new TraceView(this,PINK,60000,false);heartGraph.setContentDescription("Live heart rate over the last minute; gaps indicate missing readings");right.addView(heartGraph,new LinearLayout.LayoutParams(-1,0,1));right.addView(chartFoot("−1 minute"));
        FrameLayout footer=new FrameLayout(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(66));fp.topMargin=dp(8);root.addView(footer,fp);
        setup=button("Sensor setup",false);footer.addView(setup,new FrameLayout.LayoutParams(dp(135),dp(48),Gravity.START|Gravity.CENTER_VERTICAL));setup.setOnClickListener(v->showSetup());
        play=button("▶  Start session",true);play.setTextSize(21);footer.addView(play,new FrameLayout.LayoutParams(dp(230),dp(62),Gravity.CENTER));play.setOnClickListener(v->{if(workout!=null&&workout.running())pause("Paused");else start();});
        stop=button("■  Stop",false);stop.setTextSize(17);footer.addView(stop,new FrameLayout.LayoutParams(dp(110),dp(52),Gravity.END|Gravity.CENTER_VERTICAL));stop.setOnClickListener(v->stopDialog());
        note=label("",10,MUTED);root.addView(note,new LinearLayout.LayoutParams(-1,dp(17)));setContentView(root);refresh();
    }
    private LinearLayout panel(){LinearLayout l=column();l.setPadding(dp(16),dp(9),dp(16),dp(8));l.setBackground(rounded(CARD));return l;}
    private LinearLayout chartFoot(String start){LinearLayout row=new LinearLayout(this);row.addView(label(start,10,MUTED),new LinearLayout.LayoutParams(0,-2,1));row.addView(label("Now",10,MUTED));return row;}
    private long now(){return SystemClock.elapsedRealtime();}
    private int freshHeart(){return bpm>0&&now()-hrAt<=5000?bpm:0;}
    private static String duration(long ms){long s=ms/1000;return String.format(Locale.US,"%02d:%02d",s/60,s%60);}
    private void refresh(){
        if(timer==null)return;boolean running=workout!=null&&workout.running();int hr=freshHeart();
        timer.setText(duration(workout==null?0:workout.duration(now())));phase.setText(loading?"Loading sessions…":busy?"Saving…":message);
        steps.setText(String.format(Locale.US,"%,d",detector.steps()));heart.setText(hr==0?"—":Integer.toString(hr));
        int cadence=running?detector.cadence(SystemClock.elapsedRealtimeNanos()):0;stepMeta.setText((cadence==0?"—":Integer.toString(cadence))+" steps/min");
        heartMeta.setText("Average "+(workout==null||workout.average()==0?"—":workout.average())+" bpm");
        source.setText(deviceName.isEmpty()?"Connect sensor":deviceName+"  ›");
        heartState.setText(hr>0?"● Live · Bluetooth · bpm":bpm>0?"No signal · last reading over 5s ago":connection);
        sensorMeta.setText(String.format(Locale.US,"Live vibration · m/s² · %.0f Hz",rate));
        play.setText(running?"Ⅱ  Pause":workout==null?"▶  Start session":"▶  Resume");
        play.setEnabled(!loading&&!busy&&!calibrating&&!storageFailed&&accelerometer!=null);stop.setEnabled(workout!=null&&!busy&&!loading&&!calibrating);
        play.setAlpha(play.isEnabled()?1:.45f);stop.setAlpha(stop.isEnabled()?1:.35f);
        setup.setEnabled(!running&&!busy&&!calibrating&&!loading&&accelerometer!=null);history.setEnabled(!running&&!busy&&!loading&&!calibrating);source.setEnabled(!busy&&!calibrating);
        note.setText(calibrating?"Two-stage calibration · workout recording paused":storageFailed?"Storage error · session paused":String.format(Locale.US,"Threshold %.3f m/s² · %s",detector.threshold(),getPreferences(MODE_PRIVATE).getString("calibration","Not calibrated")));
        vibration.threshold(detector.threshold());vibration.time(now());heartGraph.time(now());
    }
    private void start(){
        if(workout==null){workout=new Workout();workout.date=System.currentTimeMillis();workout.threshold=detector.threshold();workout.source=deviceName;detector.reset();pending.clear();}
        workout.resume(now());detector.resetSignal();lastSampleAt=0;message="Recording";getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);checkpoint(false,null);refresh();
    }
    private void pause(String reason){
        if(workout!=null&&workout.running()){workout.pause(now());workout.steps=detector.steps();checkpoint(false,null);}
        if(calibration!=null)calibration.cancel();
        detector.resetSignal();message=reason;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);refresh();
    }
    private void stopDialog(){
        pause("Paused");
        new AlertDialog.Builder(this).setTitle("Save this session?").setMessage(duration(workout.elapsed)+"  ·  "+workout.steps+" steps  ·  Average "+(workout.average()==0?"—":workout.average())+" bpm")
            .setPositiveButton("Save session",(d,w)->{busy=true;refresh();checkpoint(true,()->{clearWorkout();toast("Session saved");});})
            .setNegativeButton("Discard",(d,w)->{busy=true;refresh();String id=workout.id;storage.execute(()->{try{database.discard(id);ui.post(()->{clearWorkout();toast("Session discarded");});}catch(Exception e){ui.post(()->storageError(e));}});})
            .setNeutralButton("Continue session",(d,w)->{if(!storageFailed)start();}).show();
    }
    private void clearWorkout(){workout=null;pending.clear();detector.reset();busy=false;storageFailed=false;message="Ready";refresh();}
    private void checkpoint(boolean saved,Runnable success){
        if(workout==null)return;workout.steps=detector.steps();Workout snapshot=workout.snapshot(now());ArrayList<SessionDatabase.Sample> batch=new ArrayList<>(pending);pending.clear();lastCheckpoint=now();
        storage.execute(()->{try{database.persist(snapshot,batch,saved);if(success!=null)ui.post(success);}catch(Exception e){ui.post(()->{pending.addAll(0,batch);storageError(e);});}});
    }
    private void storageError(Exception e){
        storageFailed=true;busy=false;if(workout!=null)workout.pause(now());getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);message="Storage error · paused";refresh();toast("Could not save session. Keep the app open and free storage before retrying.");android.util.Log.e("CardioLab","Session storage failed",e);
    }
    private void showSetup(){
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));TextView value=label("",15,INK);content.addView(value);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress((int)Math.round(100*Math.log(detector.threshold()/.015)/Math.log(200)));content.addView(slider);
        value.setText(String.format(Locale.US,"Threshold %.3f m/s²",detector.threshold()));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){saveThreshold("Manual threshold");}public void onProgressChanged(SeekBar s,int p,boolean user){if(user){detector.setThreshold(.015*Math.pow(200,p/100.0));if(workout!=null)workout.threshold=detector.threshold();value.setText(String.format(Locale.US,"Threshold %.3f m/s²",detector.threshold()));refresh();}}});
        content.addView(label("Lower thresholds count weaker vibrations and can add false steps.\nGuided calibration measures empty-belt noise, then compares a\n30-second walk with your manually counted foot contacts.",13,MUTED));
        new AlertDialog.Builder(this).setTitle("Sensor setup").setView(content).setNegativeButton("Close",null)
            .setNeutralButton("Restore 0.120",(d,w)->{detector.setThreshold(.12);saveThreshold("Default threshold");if(workout!=null)checkpoint(false,null);})
            .setPositiveButton("Calibrate · 2 stages",(d,w)->beginCalibration()).show();
    }
    private void saveThreshold(String name){getPreferences(MODE_PRIVATE).edit().putFloat("threshold",(float)detector.threshold()).putString("calibration",name).apply();if(workout!=null)workout.threshold=detector.threshold();refresh();}
    private void beginCalibration(){
        if(calibrating||accelerometer==null)return;
        calibrating=true;message="Calibrating";
        calibration=new CalibrationWizard(this,detector.threshold(),new CalibrationWizard.Listener(){
            public void applied(StepCalibration.Result result){
                detector.setThreshold(result.threshold);detector.resetSignal();saveThreshold("Two-stage calibrated");
                getPreferences(MODE_PRIVATE).edit().putLong("calibration_date",System.currentTimeMillis()).putInt("calibration_reference",result.reference)
                    .putInt("calibration_before",result.before).putInt("calibration_after",result.after).putInt("calibration_noise_steps",result.noiseSteps)
                    .putFloat("calibration_low",(float)result.low).putFloat("calibration_high",(float)result.high).apply();
                if(workout!=null)checkpoint(false,null);toast("Calibration applied · check a separate 100-step walk");
            }
            public void closed(){calibration=null;calibrating=false;message=workout==null?"Ready":"Paused";refresh();}
        });calibration.show();refresh();
    }
    private boolean bluetoothPermission(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    @SuppressLint("MissingPermission") private void chooseHeart(){
        if(!bluetoothPermission()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},42);return;}
        BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null){toast("Bluetooth is unavailable");return;}
        if(!adapter.isEnabled()){startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));return;}
        ArrayList<BluetoothDevice> devices=new ArrayList<>();for(BluetoothDevice d:adapter.getBondedDevices())if(d.getType()!=BluetoothDevice.DEVICE_TYPE_CLASSIC)devices.add(d);
        devices.sort((a,b)->{int rank=Boolean.compare(isGarmin(b),isGarmin(a));return rank!=0?rank:String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));});
        ArrayList<String> labels=new ArrayList<>();for(BluetoothDevice d:devices)labels.add(d.getName()==null?d.getAddress():d.getName());labels.add("Continue without heart rate");labels.add("Pair another device in Bluetooth settings");
        new AlertDialog.Builder(this).setTitle("Heart-rate sensor · paired devices").setItems(labels.toArray(new String[0]),(dialog,index)->{
            if(index==devices.size()+1){startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));return;}
            heartClient.stop();bpm=-1;hrAt=0;heartGraph.clear();
            if(index==devices.size()){deviceName="";deviceAddress="";connection="No heart-rate sensor";}
            else{BluetoothDevice selected=devices.get(index);deviceAddress=selected.getAddress();deviceName=selected.getName()==null?"Heart-rate sensor":selected.getName();heartClient.start(deviceAddress);}
            getPreferences(MODE_PRIVATE).edit().putString("hr_address",deviceAddress).putString("hr_name",deviceName).apply();if(workout!=null)workout.source=deviceName;refresh();
        }).setNegativeButton("Cancel",null).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==42){if(bluetoothPermission())chooseHeart();else{connection="Nearby Devices permission denied";refresh();}}}
    @SuppressLint("MissingPermission") private boolean isGarmin(BluetoothDevice device){String name=String.valueOf(device.getName()).toLowerCase(Locale.US);return name.contains("garmin")||name.contains("instinct");}
    private void showHistory(){
        storage.execute(()->{try{List<Workout> list=database.list(true);ui.post(()->{
            if(isFinishing()||isDestroyed())return;if(list.isEmpty()){toast("No saved sessions yet");return;}
            String[] entries=new String[list.size()];for(int i=0;i<list.size();i++){Workout w=list.get(i);entries[i]=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(w.date))+"\n"+duration(w.elapsed)+" · "+w.steps+" steps · avg "+(w.average()==0?"—":w.average())+" bpm";}
            new AlertDialog.Builder(this).setTitle("Saved sessions").setItems(entries,(d,index)->showDetail(list.get(index))).setNegativeButton("Close",null).show();
        });}catch(Exception e){ui.post(()->toast("Could not load saved sessions"));}});
    }
    private void showDetail(Workout w){
        storage.execute(()->{try{List<SessionDatabase.Sample> samples=database.samples(w);ui.post(()->{
            if(isFinishing()||isDestroyed())return;ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),dp(16));scroll.addView(content);
            content.addView(label(duration(w.elapsed)+"  ·  "+w.steps+" steps  ·  Average "+(w.average()==0?"—":w.average())+" bpm",20,INK));
            content.addView(label(w.source.isEmpty()?"No heart-rate sensor":w.source+" · "+w.hrCount+" valid HR readings",12,MUTED));
            String[] titles={"Cadence · steps/min","Heart rate · bpm","Vibration · m/s²"};
            for(int kind=0;kind<3;kind++){content.addView(label(titles[kind],13,MUTED));TraceView chart=new TraceView(this,kind==1?PINK:LIME,0,kind==2);chart.threshold(w.threshold);for(SessionDatabase.Sample s:samples)chart.add(s.t,kind==0?s.cadence:kind==1?(s.heart>0?s.heart:Float.NaN):(float)s.vibration);chart.time(w.elapsed);content.addView(chart,new LinearLayout.LayoutParams(-1,dp(100)));}
            content.addView(label("Graphs use active time; pauses are excluded. Missing HR is shown as gaps.\nAverage uses valid HR notifications received during active time.",11,MUTED));
            new AlertDialog.Builder(this).setTitle(DateFormat.getDateTimeInstance().format(new Date(w.date))).setView(scroll).setPositiveButton("Close",null).show();
        });}catch(Exception e){ui.post(()->toast("Could not load session graphs"));}});
    }
    @Override public void onSensorChanged(SensorEvent e){
        long t=now();lastSensorNs=e.timestamp;boolean running=workout!=null&&workout.running();detector.add(e.timestamp,e.values[0],e.values[1],e.values[2],running);vibration.add(t,(float)detector.signal());
        rateSamples++;if(rateAt==0)rateAt=e.timestamp;if(e.timestamp-rateAt>=1_000_000_000L){rate=rateSamples*1e9/(e.timestamp-rateAt);rateSamples=0;rateAt=e.timestamp;}
        if(calibration!=null)calibration.sample(e.timestamp,e.values[0],e.values[1],e.values[2]);
        if(running&&t-lastSampleAt>=100){workout.steps=detector.steps();pending.add(new SessionDatabase.Sample(workout.duration(t),detector.signal(),detector.steps(),detector.cadence(e.timestamp),freshHeart()));lastSampleAt=t;}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    private final Runnable tick=new Runnable(){public void run(){
        long t=now();
        if(workout!=null&&workout.running()){
            if(lastSensorNs!=0&&SystemClock.elapsedRealtimeNanos()-lastSensorNs>2_000_000_000L)pause("Sensor interrupted · paused");
            else if(t-lastCheckpoint>=1000)checkpoint(false,null);
        }
        refresh();if(foreground)ui.postDelayed(this,200);
    }};
    @Override protected void onResume(){super.onResume();foreground=true;detector.resetSignal();lastSensorNs=rateAt=rateSamples=0;if(accelerometer==null||!sensors.registerListener(this,accelerometer,10000,0)){accelerometer=null;message="Accelerometer unavailable";}if(!deviceAddress.isEmpty()&&bluetoothPermission())heartClient.start(deviceAddress);ui.removeCallbacks(tick);ui.post(tick);}
    @Override protected void onPause(){foreground=false;if(workout!=null&&workout.running()||calibrating)pause("Paused · app left the screen");sensors.unregisterListener(this);ui.removeCallbacks(tick);heartClient.stop();bpm=-1;hrAt=0;super.onPause();}
    @Override protected void onDestroy(){heartClient.stop();storage.execute(database::close);storage.shutdown();super.onDestroy();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
