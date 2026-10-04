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
    /** Rebuilt from controller frames received over our own link; see TreadmillLink for what is sent. */
    public final TreadmillState treadmill=new TreadmillState();
    public interface Listener {void changed();}
    public interface Samples {void sample(SensorEvent event);}
    public Samples calibrationSamples;
    private final Set<Listener> listeners=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final IBinder binder=new LocalBinder();
    private SensorManager sensors;
    private HeartRateClient heart;
    private OverlayControls overlay;
    private TreadmillLink link;
    private boolean consoleVisible;
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
            .setContentText("Treadmill link active · physical Stop and safety key always work").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Close console",stop).build()).build();
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
        link=new TreadmillLink(new TreadmillLink.Listener(){
            public void received(byte[] data,int count){treadmillBytes(data,count);}
            public void changed(String status){notifyUi();}
        },main);
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
        if(treadmillLive()){treadmillAction(action);return;}
        if(action.equals("end")){session.end();session.intervals=false;save();notifyUi();return;}
        if(!session.preview){message="Connect and verify the treadmill before enabling real controls";notifyUi();return;}
        switch(action){
            case "intervals":
            case "main": if(session.running)session.pause();else{if(!session.started)detector.reset();session.start();detector.resetSignal();}break;
            case "switch":if(session.running&&session.intervals)session.switchPhase();break;
            default:return;
        }
        save();notifyUi();
    }
    public void speed(double value){advance();if(treadmillLive()){command(now->treadmill.setSpeed(value,now),String.format(Locale.US,"%.1f mph",value));return;}if(session.preview)session.setSpeed(value);save();notifyUi();}
    public void incline(int value){if(treadmillLive()){command(now->treadmill.setIncline(value,now),"incline "+value);return;}if(session.preview)session.setIncline(value);save();notifyUi();}
    /** Quick start runs at 2 mph; resuming restores the speed held before the pause (the controller restarts at 0.5). */
    private double speedAfterStart,queuedSpeed,lastElapsed,lastMeters;
    private String commandLabel="";
    private void treadmillAction(String action){
        TreadmillState.Phase p=treadmill.phase;
        if(action.equals("main")){
            if(p==TreadmillState.Phase.RUNNING){speedAfterStart=treadmill.speedMph();command(now->treadmill.setState(2,now),"pause");}
            else if(p==TreadmillState.Phase.PAUSED){if(speedAfterStart<2)speedAfterStart=2;command(now->treadmill.setState(1,now),"resume");}
            else if(p==TreadmillState.Phase.STOPPED||p==TreadmillState.Phase.UNKNOWN){session.intervals=false;speedAfterStart=2;command(now->treadmill.setState(1,now),"start");}
            else{message="Treadmill is "+treadmill.label().toLowerCase(Locale.US)+" · wait or use its own buttons";notifyUi();}
        }else if(action.equals("intervals")&&(p==TreadmillState.Phase.STOPPED||p==TreadmillState.Phase.UNKNOWN)&&session.intervals){speedAfterStart=session.speedA;command(now->treadmill.setState(1,now),"start intervals");}
        else if(action.equals("switch")&&session.intervals&&p==TreadmillState.Phase.RUNNING){session.switchPhase();queuedSpeed=session.speed;}
        else if(action.equals("end"))command(now->treadmill.setState(0,now),"end");
    }
    private interface Command {byte[] build(long now);}
    /** Builds the frame only once no earlier command is awaiting confirmation (building marks it pending). */
    private void command(Command build,String what){
        long now=SystemClock.elapsedRealtime();
        if(treadmill.confirmPending(now)){message="Waiting for the treadmill to confirm "+commandLabel;notifyUi();return;}
        byte[] frame=build.build(now);
        if(frame==null){message="The treadmill can't "+what+" right now ("+treadmill.label().toLowerCase(Locale.US)+")";notifyUi();return;}
        if(!link.send(frame)){treadmill.pendingKind=null;message="Treadmill link is not open";notifyUi();return;}
        commandLabel=what;message="Sent "+what+" · waiting for the treadmill";notifyUi();
    }
    public boolean treadmillLive(){return treadmill.live(SystemClock.elapsedRealtime());}
    public boolean canReadLogs(){return checkSelfPermission("android.permission.READ_LOGS")==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    /** The console or its video overlay is on screen, so CardioLab may own the treadmill link. */
    public void consoleVisible(boolean value){consoleVisible=value;claimLink();}
    /** Debug builds only, for the supervised link-loss test. */
    public void pauseHeartbeat(long ms){if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)link.pauseHeartbeat(ms);}
    private void claimLink(){link.want(canReadLogs()&&(consoleVisible||overlay!=null));}
    private void treadmillBytes(byte[] data,int count){
        TreadmillState.Phase before=treadmill.phase;
        link.echo(treadmill.bytes(data,count,SystemClock.elapsedRealtime()));
        boolean started=treadmill.phase==TreadmillState.Phase.COUNTDOWN&&before!=TreadmillState.Phase.COUNTDOWN&&before!=TreadmillState.Phase.PAUSED;
        boolean ended=treadmill.phase==TreadmillState.Phase.STOPPED&&(before==TreadmillState.Phase.RUNNING||before==TreadmillState.Phase.PAUSED||before==TreadmillState.Phase.COUNTDOWN);
        if(started){detector.reset();session.begin();lastElapsed=0;lastMeters=0;}
        // Time and distance return to zero when the controller ends a workout; steps and intervals follow.
        if(ended){detector.reset();session.end();session.intervals=false;queuedSpeed=0;}
        session.running=treadmill.phase==TreadmillState.Phase.RUNNING;
        if(session.running&&(treadmill.elapsed!=lastElapsed||treadmill.meters()!=lastMeters)){
            if(session.measured(Math.max(0,treadmill.elapsed-lastElapsed),Math.max(0,treadmill.meters()-lastMeters)))queuedSpeed=session.speed;
        }
        lastElapsed=treadmill.elapsed;lastMeters=treadmill.meters();
        if(treadmill.phase==TreadmillState.Phase.RUNNING&&before!=TreadmillState.Phase.RUNNING&&speedAfterStart>0){double target=speedAfterStart;speedAfterStart=0;command(now->treadmill.setSpeed(target,now),String.format(Locale.US,"%.1f mph",target));}
        // The controller answers B0 with its current state before the new one, so a stop seen while a command is pending is not a cancellation.
        else if(treadmill.pendingKind==null&&treadmill.phase==TreadmillState.Phase.STOPPED||treadmill.phase==TreadmillState.Phase.SAFETY_KEY_OUT||treadmill.phase==TreadmillState.Phase.EMERGENCY_STOP)speedAfterStart=0;
        if(treadmill.phase!=before)notifyUi();
    }
    public String treadmillStatus(){return treadmillLive()?"Live from treadmill · "+treadmill.label():!canReadLogs()?"Grant READ_LOGS over adb so CardioLab can hand off from the stock app":link.status;}
    private void advance(){long now=SystemClock.elapsedRealtime();if(!treadmillLive())session.advance((now-lastTick)/1000.0);lastTick=now;}
    private final Runnable tick=new Runnable(){public void run(){advance();if(treadmill.confirmFailed(SystemClock.elapsedRealtime())){treadmill.pendingKind=null;speedAfterStart=0;message="The treadmill did not confirm "+commandLabel+" · the screen shows what the treadmill reports";}else if(treadmillLive()&&treadmill.pendingKind==null&&message.startsWith("Sent "))message="Treadmill confirmed "+commandLabel;
            if(queuedSpeed>0&&treadmill.phase==TreadmillState.Phase.RUNNING&&!treadmill.confirmPending(SystemClock.elapsedRealtime())){double target=queuedSpeed;queuedSpeed=0;command(now->treadmill.setSpeed(target,now),String.format(Locale.US,"interval %.1f mph",target));}if(SystemClock.elapsedRealtime()-lastSave>1000)save();notifyUi();main.postDelayed(this,200);}};
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
        s.speed=p.getFloat("speed",2);s.incline=p.getInt("incline",0);s.elapsed=p.getFloat("elapsed",0);s.meters=p.getFloat("meters",0);detector.restoreSteps(s.started?p.getInt("steps",0):0);
        s.mode=p.getString("mode","time");s.speedA=p.getFloat("a",2);s.speedB=p.getFloat("b",6);s.limitA=p.getFloat("la",60);s.limitB=p.getFloat("lb",60);
        s.phase=p.getInt("phase",0);s.phaseElapsed=p.getFloat("pe",0);s.phaseMeters=p.getFloat("pm",0);
        if(s.preview)message="PREVIEW · simulated speed/distance · real steps and HR";
    }
    @Override public void onSensorChanged(SensorEvent e){lastSample=e.timestamp;detector.add(e.timestamp,e.values[0],e.values[1],e.values[2],session.running||treadmillLive()&&treadmill.phase==TreadmillState.Phase.RUNNING);if(calibrationSamples!=null)calibrationSamples.sample(e);sampleCount++;if(rateStart==0)rateStart=e.timestamp;if(e.timestamp-rateStart>=1_000_000_000L){sensorRate=sampleCount*1e9/(e.timestamp-rateStart);sampleCount=0;rateStart=e.timestamp;}}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    public boolean showOverlay(){if(!Settings.canDrawOverlays(this))return false;hideOverlay();try{overlay=new OverlayControls(this);overlay.show();claimLink();return true;}catch(RuntimeException e){hideOverlay();message="Overlay unavailable: "+e.getClass().getSimpleName();return false;}}
    public void hideOverlay(){if(overlay!=null){overlay.close();overlay=null;}if(link!=null)claimLink();}
    public void openConsole(String action){hideOverlay();startActivity(new Intent(this,ConsoleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("action",action.equals("track")?"expandTrack":action));}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(overlay!=null)showOverlay();}
    @Override public void onDestroy(){link.close();advance();session.pause();save();hideOverlay();main.removeCallbacksAndMessages(null);sensors.unregisterListener(this);heart.stop();listeners.clear();super.onDestroy();}
}
