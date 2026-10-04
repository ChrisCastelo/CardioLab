package com.cardio.lab;

import android.app.*;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded two-stage capture. Closing or leaving the app cancels without applying a threshold. */
final class CalibrationWizard {
    interface Listener { void applied(StepCalibration.Result result); void closed(); }
    private enum Stage { INTRO, NOISE, WALK_READY, WALK, REFERENCE, ANALYZING, RESULT }
    private final Activity activity;
    private final Listener listener;
    private final double previous;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService compute=Executors.newSingleThreadExecutor();
    private final ArrayList<StepCalibration.Sample> noise=new ArrayList<>(),walk=new ArrayList<>();
    private AlertDialog dialog;
    private TextView heading,instruction,clock;
    private EditText reference;
    private Stage stage=Stage.INTRO;
    private long captureStart,measureStart,end;
    private int lastSecond=-1;
    private boolean active=true;
    private ToneGenerator tone;
    private StepCalibration.Result result;
    CalibrationWizard(Activity activity,double previous,Listener listener){this.activity=activity;this.previous=previous;this.listener=listener;}
    void show(){
        try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,75);}catch(RuntimeException ignored){}
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,pad/2,pad,pad/2);
        heading=text(18);instruction=text(14);clock=text(32);clock.setGravity(Gravity.CENTER);reference=new EditText(activity);reference.setSingleLine(true);reference.setInputType(InputType.TYPE_CLASS_NUMBER);reference.setHint("Steps you counted, both feet");reference.setVisibility(View.GONE);
        body.addView(heading);body.addView(instruction);body.addView(clock);body.addView(reference);ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle("Step calibration · 2 stages").setView(scroll).setPositiveButton("Begin empty-belt stage",null).setNegativeButton("Cancel",(d,w)->cancel()).create();
        dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->cancel());dialog.setOnDismissListener(d->cancel());dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->next());
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);render();
    }
    private TextView text(int size){TextView t=new TextView(activity);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(0,4,0,8);return t;}
    private void next(){
        if(stage==Stage.INTRO)begin(false);
        else if(stage==Stage.WALK_READY)begin(true);
        else if(stage==Stage.REFERENCE){
            int count;try{count=Integer.parseInt(reference.getText().toString());}catch(NumberFormatException e){reference.setError("Enter your counted steps");return;}
            if(count<20||count>120){reference.setError("Enter 20–120 foot contacts; repeat if you lost count");return;}
            stage=Stage.ANALYZING;render();final int total=count;
            compute.execute(()->{StepCalibration.Result fitted=StepCalibration.fit(noise,walk,total,previous);main.post(()->{if(!active)return;result=fitted;stage=Stage.RESULT;render();});});
        } else if(stage==Stage.RESULT){if(result.accepted){listener.applied(result);cancel();}else{noise.clear();walk.clear();reference.setText("");stage=Stage.INTRO;render();}}
    }
    private void begin(boolean walking){
        stage=walking?Stage.WALK:Stage.NOISE;captureStart=SystemClock.elapsedRealtimeNanos();measureStart=captureStart+(walking?5000:3000)*1_000_000L;end=measureStart+(walking?StepCalibration.WALK_MS:StepCalibration.NOISE_MS)*1_000_000L;lastSecond=-1;
        (walking?walk:noise).clear();render();main.post(tick);
    }
    void sample(long ns,float x,float y,float z){
        if(!active||(stage!=Stage.NOISE&&stage!=Stage.WALK)||ns<captureStart||ns>=end)return;
        ArrayList<StepCalibration.Sample> target=stage==Stage.NOISE?noise:walk;
        if(target.size()>=20000){cancel();Toast.makeText(activity,"Calibration sample limit reached. Please retry.",Toast.LENGTH_LONG).show();return;}
        target.add(new StepCalibration.Sample(ns,x,y,z,ns>=measureStart));
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!active||(stage!=Stage.NOISE&&stage!=Stage.WALK))return;
        long now=SystemClock.elapsedRealtimeNanos();
        if(now>=end){beep(ToneGenerator.TONE_PROP_ACK,450);stage=stage==Stage.NOISE?Stage.WALK_READY:Stage.REFERENCE;render();return;}
        int second=(int)((Math.max(0,(now<measureStart?measureStart:end)-now)+999_999_999L)/1_000_000_000L);
        if(second!=lastSecond){lastSecond=second;if(now<measureStart)beep(ToneGenerator.TONE_PROP_BEEP,100);else if(second==(stage==Stage.NOISE?6:30))beep(ToneGenerator.TONE_PROP_BEEP2,350);}
        clock.setText(now<measureStart?"Starts in "+second:second+" seconds left");
        instruction.setText(now<measureStart?(stage==Stage.NOISE?"Keep the belt running with nobody stepping. Do not touch the phone.":"Walk at a steady pace. Start counting BOTH feet when the countdown ends."):(stage==Stage.NOISE?"Measuring empty-belt noise…":"COUNT NOW · count every foot contact until the end signal."));
        main.postDelayed(this,100);
    }};
    private void beep(int id,int duration){if(tone!=null)tone.startTone(id,duration);}
    private void render(){
        boolean recording=stage==Stage.NOISE||stage==Stage.WALK;
        reference.setVisibility(stage==Stage.REFERENCE?View.VISIBLE:View.GONE);clock.setVisibility(recording?View.VISIBLE:View.GONE);
        Button next=dialog.getButton(AlertDialog.BUTTON_POSITIVE);next.setEnabled(!recording&&stage!=Stage.ANALYZING);
        switch(stage){
            case INTRO:heading.setText("1 / 2 · Empty belt");instruction.setText("Secure the phone on a fixed deck edge, clear of the moving belt and feet. Run the belt at your test speed with nobody stepping.\n\nAfter a 3-second countdown, record 6 seconds of noise. The current threshold stays unchanged until you review both stages.");next.setText("Begin empty-belt stage");break;
            case NOISE:heading.setText("1 / 2 · Empty belt");next.setText("Recording…");break;
            case WALK_READY:heading.setText("2 / 2 · Walking reference");instruction.setText("Keep the same speed and phone position. Walk at a steady pace.\n\nTap Begin, then use the 5-second countdown to settle. Count every foot contact (left + right) during the following 30 seconds, then enter that total. Sound cues use media volume; the countdown is also visible. A helper makes counting easier.");next.setText("Begin walking stage");break;
            case WALK:heading.setText("2 / 2 · Walking reference");next.setText("Recording…");break;
            case REFERENCE:heading.setText("How many steps did you count?");instruction.setText("Enter all individual foot contacts during COUNT NOW only. Do not include the countdown. If you lost count, cancel and repeat. The threshold has not changed.");next.setText("Compare thresholds");break;
            case ANALYZING:heading.setText("Comparing both recordings…");instruction.setText("Checking walking counts and rejecting thresholds that count empty-belt noise.");next.setText("Analyzing…");break;
            case RESULT:
                heading.setText(result.accepted?"Review suggested threshold":"Calibration could not find a reliable setting");
                instruction.setText(result.accepted?String.format(Locale.US,"Your count: %d steps\nCurrent setting: %d steps → suggested: %d steps\nEmpty-belt false steps at suggestion: %d\n\nThreshold: %.3f → %.3f m/s²\nMatching range: %.3f–%.3f m/s²\n\n%s",result.reference,result.before,result.after,result.noiseSteps,previous,result.threshold,result.low,result.high,result.explanation):result.explanation+"\n\nYour previous threshold is unchanged.");
                next.setText(result.accepted?"Apply calibration":"Repeat both stages");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Keep current setting");break;
        }
    }
    void cancel(){if(!active)return;active=false;main.removeCallbacksAndMessages(null);compute.shutdownNow();if(tone!=null){tone.release();tone=null;}activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(dialog!=null)dialog.dismiss();listener.closed();}
}
