package com.cardio.lab;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** Owns sensing while the console or an external video app is visible. */
public final class ConsoleService extends Service implements SensorEventListener {
    public final ConsoleSession session=new ConsoleSession();
    public final StepDetector detector=new StepDetector();
    public interface Listener {void changed();}
    public interface Samples {void sample(SensorEvent event);}
    public Samples calibrationSamples;
    private final Set<Listener> listeners=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final IBinder binder=new LocalBinder();
    private SensorManager sensors;
    private HeartRateClient heart;
    private OverlayControls overlay;
    private long lastTick,lastSave,hrAt,rateStart,sampleCount,lastSample;
    private int bpm;
    public double sensorRate;
    public boolean sensorAvailable;
    public String heartStatus="Select a paired heart-rate sensor",deviceName="",message="Controller not verified · controls are preview only";
    public final class LocalBinder extends Binder {public ConsoleService service(){return ConsoleService.this;}}
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("console","CardioLab console",NotificationManager.IMPORTANCE_LOW));
        Intent home=new Intent(this,ConsoleActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,0,home,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent close=new Intent(this,ConsoleService.class).setAction("close");
        PendingIntent stop=PendingIntent.getService(this,1,close,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"console").setSmallIcon(R.drawable.ic_launcher).setContentTitle("CardioLab sensors active")
            .setContentText("Console preview · no treadmill commands").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Close console",stop).build()).build();
        startForeground(41,n);
        restore();
        sensors=getSystemService(SensorManager.class);Sensor accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        detector.setThreshold(getSharedPreferences("MainActivity",MODE_PRIVATE).getFloat("threshold",.12f));
        sensorAvailable=accelerometer!=null&&sensors.registerListener(this,accelerometer,10000,0);
        heart=new HeartRateClient(this,new HeartRateClient.Listener(){
            public void status(String value){heartStatus=value;if(!value.startsWith("Live")){bpm=0;hrAt=0;}notifyUi();}
            public void measurement(int value){bpm=value;hrAt=SystemClock.elapsedRealtime();notifyUi();}
        });
        reconnectHeart();lastTick=SystemClock.elapsedRealtime();main.post(tick);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null&&"close".equals(intent.getAction())){session.pause();save();stopSelf();}return START_NOT_STICKY;}
    public void listen(Listener l){listeners.add(l);l.changed();}
    public void unlisten(Listener l){listeners.remove(l);}
    public void notifyUi(){for(Listener l:new ArrayList<>(listeners))l.changed();if(overlay!=null)overlay.refresh();}
    public int heartRate(){return bpm>0&&SystemClock.elapsedRealtime()-hrAt<=5000?bpm:0;}
    public boolean freshSensor(){return sensorAvailable&&lastSample>0&&SystemClock.elapsedRealtimeNanos()-lastSample<2_000_000_000L;}
    public void reconnectHeart(){
        SharedPreferences p=getSharedPreferences("MainActivity",MODE_PRIVATE);deviceName=p.getString("hr_name","");String address=p.getString("hr_address","");
        heart.stop();bpm=0;hrAt=0;heartStatus=address.isEmpty()?"Select a paired heart-rate sensor":"Connecting…";
        if(!address.isEmpty())heart.start(address);
    }
    public void selectHeart(String address,String name){getSharedPreferences("MainActivity",MODE_PRIVATE).edit().putString("hr_address",address).putString("hr_name",name).apply();reconnectHeart();notifyUi();}
    public void enablePreview(boolean enabled){session.end();session.preview=enabled;session.elapsed=session.meters=0;detector.reset();message=enabled?"PREVIEW · simulated speed/distance · real steps and HR":"Controller not verified · controls are preview only";save();notifyUi();}
    public void action(String action){
        advance();
        if(action.equals("end")){session.end();session.intervals=false;save();notifyUi();return;}
        if(!session.preview){message="Connect and verify the treadmill before enabling real controls";notifyUi();return;}
        switch(action){
            case "main": if(session.running)session.pause();else{if(!session.started)detector.reset();session.start();detector.resetSignal();}break;
            case "switch":if(session.running&&session.intervals)session.switchPhase();break;
            default:return;
        }
        save();notifyUi();
    }
    public void speed(double value){advance();if(session.preview)session.setSpeed(value);save();notifyUi();}
    public void incline(int value){if(session.preview)session.setIncline(value);save();notifyUi();}
    private void advance(){long now=SystemClock.elapsedRealtime();session.advance((now-lastTick)/1000.0);lastTick=now;}
    private final Runnable tick=new Runnable(){public void run(){advance();if(SystemClock.elapsedRealtime()-lastSave>1000)save();notifyUi();main.postDelayed(this,200);}};
    private void save(){
        lastSave=SystemClock.elapsedRealtime();ConsoleSession s=session;
        getSharedPreferences("console",MODE_PRIVATE).edit().putBoolean("preview",s.preview).putBoolean("started",s.started).putBoolean("intervals",s.intervals)
            .putFloat("speed",(float)s.speed).putInt("incline",s.incline).putFloat("elapsed",(float)s.elapsed).putFloat("meters",(float)s.meters).putInt("steps",detector.steps())
            .putString("mode",s.mode).putFloat("a",(float)s.speedA).putFloat("b",(float)s.speedB).putFloat("la",(float)s.limitA).putFloat("lb",(float)s.limitB)
            .putInt("phase",s.phase).putFloat("pe",(float)s.phaseElapsed).putFloat("pm",(float)s.phaseMeters).apply();
    }
    private void restore(){
        SharedPreferences p=getSharedPreferences("console",MODE_PRIVATE);ConsoleSession s=session;
        s.preview=p.getBoolean("preview",false);s.started=p.getBoolean("started",false)&&s.preview;s.intervals=p.getBoolean("intervals",false);
        s.speed=p.getFloat("speed",2);s.incline=p.getInt("incline",0);s.elapsed=p.getFloat("elapsed",0);s.meters=p.getFloat("meters",0);detector.restoreSteps(p.getInt("steps",0));
        s.mode=p.getString("mode","time");s.speedA=p.getFloat("a",2);s.speedB=p.getFloat("b",6);s.limitA=p.getFloat("la",60);s.limitB=p.getFloat("lb",60);
        s.phase=p.getInt("phase",0);s.phaseElapsed=p.getFloat("pe",0);s.phaseMeters=p.getFloat("pm",0);
        if(s.preview)message="PREVIEW · simulated speed/distance · real steps and HR";
    }
    @Override public void onSensorChanged(SensorEvent e){lastSample=e.timestamp;detector.add(e.timestamp,e.values[0],e.values[1],e.values[2],session.running);if(calibrationSamples!=null)calibrationSamples.sample(e);sampleCount++;if(rateStart==0)rateStart=e.timestamp;if(e.timestamp-rateStart>=1_000_000_000L){sensorRate=sampleCount*1e9/(e.timestamp-rateStart);sampleCount=0;rateStart=e.timestamp;}}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    public boolean showOverlay(){if(!Settings.canDrawOverlays(this))return false;hideOverlay();try{overlay=new OverlayControls(this);overlay.show();return true;}catch(RuntimeException e){hideOverlay();message="Overlay unavailable: "+e.getClass().getSimpleName();return false;}}
    public void hideOverlay(){if(overlay!=null){overlay.close();overlay=null;}}
    public void openConsole(String action){hideOverlay();startActivity(new Intent(this,ConsoleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("action",action.equals("track")?"expandTrack":action));}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(overlay!=null)showOverlay();}
    @Override public void onDestroy(){advance();session.pause();save();hideOverlay();main.removeCallbacksAndMessages(null);sensors.unregisterListener(this);heart.stop();listeners.clear();super.onDestroy();}
}
