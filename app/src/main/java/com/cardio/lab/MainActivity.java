package com.cardio.lab;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int BG = Color.rgb(10, 20, 28), CARD = Color.rgb(20, 36, 47),
            INK = Color.rgb(239, 245, 245), MUTED = Color.rgb(152, 176, 188), LIME = Color.rgb(189, 250, 118);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StepDetector detector = new StepDetector();
    private final SessionLog log = new SessionLog();
    private final StringBuilder batch = new StringBuilder();
    private final ArrayList<Double> noise = new ArrayList<>();
    private SensorManager sensors;
    private Sensor accelerometer;
    private TextView countText, cadenceText, timeText, statusText, sensorText, thresholdText, comparisonText;
    private Button startButton, calibrateButton, exportButton, referenceButton, newButton;
    private SeekBar sensitivity;
    private SignalView graph;
    private boolean running, calibrating;
    private long runStart, elapsed, calibrationStart, rateStart, sampleCount, lastSensorNs;
    private double rate;
    private File recording, exportSource;
    private int reference = -1;
    private String status = "Ready for your first test", calibrationStatus = "Not calibrated";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        detector.setThreshold(prefs.getFloat("threshold", 0.12f));
        detector.restoreSteps(prefs.getInt("steps", 0));
        elapsed = prefs.getLong("elapsed", 0);
        reference = prefs.getInt("reference", -1);
        calibrationStatus = prefs.getString("calibration", "Not calibrated");
        String name = prefs.getString("recording", "");
        if (!name.isEmpty()) {
            recording = new File(getFilesDir(), name);
            if (recording.exists()) status = "Previous test restored · paused";
            else recording = null;
        }
        buildUi();
        ui.post(tick);
    }

    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density*n + 0.5f); }
    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(20)); return d;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("tnum"); t.setPadding(0, dp(3), 0, dp(3)); return t;
    }
    private Button button(String title, boolean primary) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? BG : INK); b.setBackground(rounded(primary ? LIME : CARD));
        b.setMinHeight(dp(52)); return b;
    }
    private void gap(LinearLayout target, int h) { target.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h))); }
    private void row(LinearLayout parent, View a, View b) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1); ap.rightMargin = dp(6);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1); bp.leftMargin = dp(6);
        r.addView(a, ap); r.addView(b, bp); parent.addView(r);
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        LinearLayout root = column(); root.setPadding(dp(22), dp(18), dp(22), dp(28)); scroll.addView(root);
        TextView eyebrow = text("CARDIO LAB   /   EXPERIMENT 01", 12, LIME); eyebrow.setLetterSpacing(0.12f); root.addView(eyebrow);
        root.addView(text("Find your rhythm.", 30, INK));
        root.addView(text("Treadmill vibration → estimated steps", 14, MUTED)); gap(root, 18);
        LinearLayout hero = column(); hero.setPadding(dp(22), dp(15), dp(22), dp(18)); hero.setBackground(rounded(CARD));
        hero.addView(text("ESTIMATED STEPS", 12, MUTED));
        countText = text("0", 76, INK); countText.setTypeface(null, Typeface.BOLD); hero.addView(countText);
        LinearLayout cadence = column(), timer = column();
        cadenceText = text("—", 30, LIME); cadence.addView(cadenceText); cadence.addView(text("steps / min", 12, MUTED));
        timeText = text("00:00", 30, INK); timer.addView(timeText); timer.addView(text("active time", 12, MUTED)); row(hero, cadence, timer);
        root.addView(hero); gap(root, 12);
        statusText = text(status, 14, LIME); root.addView(statusText); gap(root, 10);
        startButton = button("Start test", true); startButton.setOnClickListener(v -> { if (running) pauseTest("Test paused"); else startTest(); });
        calibrateButton = button("Calibrate", false); calibrateButton.setOnClickListener(v -> calibrationDialog());
        row(root, startButton, calibrateButton); gap(root, 18);
        root.addView(text("LIVE VIBRATION", 12, MUTED));
        graph = new SignalView(); root.addView(graph, new LinearLayout.LayoutParams(-1, dp(120)));
        sensorText = text("Waiting for accelerometer…", 12, MUTED); root.addView(sensorText);
        thresholdText = text("", 13, INK); root.addView(thresholdText);
        sensitivity = new SeekBar(this); sensitivity.setMax(100);
        sensitivity.setProgress(thresholdToProgress(detector.threshold()));
        sensitivity.setContentDescription("Detection threshold. Left counts weaker vibrations; right rejects more noise.");
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean user) {
                if (user) { detector.setThreshold(0.015*Math.pow(200, progress/100.0)); calibrationStatus = "Manual threshold"; }
                updateThreshold();
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { saveState(); }
        }); root.addView(sensitivity); updateThreshold();
        root.addView(text("← more sensitive                       less sensitive →", 11, MUTED)); gap(root, 14);
        referenceButton = button("Compare count", false); referenceButton.setOnClickListener(v -> referenceDialog());
        exportButton = button("Export CSV", false); exportButton.setOnClickListener(v -> chooseRecording()); row(root, referenceButton, exportButton);
        comparisonText = text("Count your own steps for the same test, then compare.", 13, MUTED); root.addView(comparisonText); updateComparison();
        gap(root, 12); newButton = button("New test", false); root.addView(newButton);
        newButton.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Start a new test?")
            .setMessage("This clears the displayed count. Recordings remain available in Export CSV.")
            .setNegativeButton("Cancel", null).setPositiveButton("New test", (d,w) -> {
                pauseTest("Ready for a new test"); recording = null; detector.reset(); graph.clear(); elapsed = 0; reference = -1;
                updateComparison(); saveState(); refresh();
            }).show());
        gap(root, 16);
        root.addView(text("Secure the phone on a fixed deck edge, clear of the moving belt and your feet. Keep the app open: the screen stays awake during a test. Leaving the app pauses counting.", 13, MUTED));
        root.addView(text("Experimental: motor vibration and deck bounce can look like steps. Recalibrate after changing speed or phone position.", 13, MUTED));
        TextView hardware = text(accelerometer == null ? "No accelerometer found" : accelerometer.getName()+" · 100 Hz requested · local recordings only", 11, MUTED);
        root.addView(hardware); setContentView(scroll); refresh();
    }
    private int thresholdToProgress(double threshold) { return (int)Math.round(100*Math.log(threshold/0.015)/Math.log(200)); }
    private void updateThreshold() {
        thresholdText.setText(String.format(Locale.US, "Threshold %.3f m/s² · %s", detector.threshold(), calibrationStatus));
    }
    private void startTest() {
        if (accelerometer == null) return;
        if (recording == null) {
            recording = new File(getFilesDir(), "cardio-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + ".csv");
            log.append(recording, "# Cardio Lab 0.1; " + StepDetector.VERSION + "; device=" + Build.MODEL + "; sensor=" + accelerometer.getName() + "\n"
                + "# Experimental peak count; requested_rate_hz=100; units=m/s^2\n"
                + "sensor_time_ns,active_time_ms,x,y,z,filtered,threshold,step_event,total_steps,phase\n");
        }
        log.append(recording, "# RESUME active_ms="+elapsed+"\n");
        detector.resetSignal(); running = true; runStart = SystemClock.elapsedRealtime()+3000; status = "Recording · screen stays awake";
        reference = -1; updateComparison();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); saveState(); refresh();
    }
    private void pauseTest(String message) {
        if (running) {
            elapsed += Math.max(0, SystemClock.elapsedRealtime()-runStart); running = false; flush();
            log.append(recording, "# PAUSE active_ms="+elapsed+"; estimated_steps="+detector.steps()+"\n");
        }
        if (calibrating) { calibrating = false; noise.clear(); }
        detector.resetSignal(); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status = message; saveState(); refresh();
    }
    private void calibrationDialog() {
        new AlertDialog.Builder(this).setTitle("Measure the treadmill baseline")
            .setMessage("Secure the phone in its test position. Run the belt at your test speed with nobody stepping on it. After you tap Begin, allow 6 seconds for the measurement. Recalibrate whenever speed or placement changes.")
            .setNegativeButton("Cancel", null).setPositiveButton("Begin", (d,w) -> {
                pauseTest("Measuring baseline…"); detector.resetSignal(); noise.clear(); calibrating = true;
                calibrationStart = SystemClock.elapsedRealtime(); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); refresh();
            }).show();
    }
    private void finishCalibration() {
        calibrating = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (noise.size() < 100) status = "Too few samples · try calibration again";
        else {
            Collections.sort(noise);
            detector.setThreshold(Math.max(0.025, noise.get((int)((noise.size()-1)*0.99))*1.7));
            calibrationStatus = "Baseline calibrated";
            sensitivity.setProgress(Math.max(0, Math.min(100, thresholdToProgress(detector.threshold()))));
            status = "Baseline saved · ready to start";
        }
        detector.resetSignal(); updateThreshold(); saveState(); refresh();
    }
    @Override public void onSensorChanged(SensorEvent e) {
        lastSensorNs = e.timestamp;
        boolean counting = running && SystemClock.elapsedRealtime() >= runStart;
        boolean hit = detector.add(e.timestamp, e.values[0], e.values[1], e.values[2], counting);
        graph.add((float)detector.signal(), hit);
        sampleCount++;
        if (rateStart == 0) rateStart = e.timestamp;
        if (e.timestamp-rateStart >= 1_000_000_000L) { rate = sampleCount*1e9/(e.timestamp-rateStart); rateStart = e.timestamp; sampleCount = 0; }
        if (calibrating && SystemClock.elapsedRealtime()-calibrationStart >= 1000) noise.add(Math.abs(detector.signal()));
        if (running) {
            batch.append(e.timestamp).append(',').append(elapsed+Math.max(0, SystemClock.elapsedRealtime()-runStart)).append(',')
                .append(e.values[0]).append(',').append(e.values[1]).append(',').append(e.values[2]).append(',')
                .append(detector.signal()).append(',').append(detector.threshold()).append(',').append(hit ? 1 : 0).append(',').append(detector.steps()).append(',').append(counting ? "counting" : "settling").append('\n');
            if (batch.length() >= 16000) flush();
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    private void flush() { if (recording != null && batch.length() > 0) { log.append(recording, batch.toString()); batch.setLength(0); } }
    private final Runnable tick = new Runnable() {
        public void run() {
            if (calibrating && SystemClock.elapsedRealtime()-calibrationStart >= 6000) finishCalibration();
            if (running && lastSensorNs != 0 && SystemClock.elapsedRealtimeNanos()-lastSensorNs > 2_000_000_000L) pauseTest("Sensor interrupted · test paused");
            refresh(); ui.postDelayed(this, 200);
        }
    };
    private void refresh() {
        if (countText == null) return;
        countText.setText(String.format(Locale.US, "%,d", detector.steps()));
        int cadence = running ? detector.cadence(SystemClock.elapsedRealtimeNanos()) : 0;
        cadenceText.setText(cadence == 0 ? "—" : Integer.toString(cadence));
        long seconds = (elapsed+(running ? Math.max(0, SystemClock.elapsedRealtime()-runStart) : 0))/1000;
        timeText.setText(String.format(Locale.US, "%02d:%02d", seconds/60, seconds%60));
        statusText.setText(log.error != null ? log.error : calibrating ? "Baseline · "+Math.max(0, 6-(SystemClock.elapsedRealtime()-calibrationStart)/1000)+" seconds remaining" :
            running && SystemClock.elapsedRealtime() < runStart ? "Get ready · "+((runStart-SystemClock.elapsedRealtime()+999)/1000)+" · then begin stepping" : status);
        startButton.setText(running ? "Pause test" : elapsed > 0 ? "Resume test" : "Start test");
        startButton.setEnabled(accelerometer != null && !calibrating);
        calibrateButton.setEnabled(accelerometer != null && !running && !calibrating);
        referenceButton.setEnabled(!running && !calibrating && recording != null);
        exportButton.setEnabled(!running && !calibrating); newButton.setEnabled(!running && !calibrating);
        sensitivity.setEnabled(!running && !calibrating);
        sensorText.setText(String.format(Locale.US, "%s · %.0f Hz measured · dashed line = threshold", running ? "RECORDING" : "PREVIEW", rate));
        graph.invalidate();
    }
    private void referenceDialog() {
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setHint("Manually counted steps");
        if (reference >= 0) input.setText(Integer.toString(reference));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Compare this whole test")
            .setMessage("Enter your manual count for all active time in this test. Use 0 for an empty-belt test.")
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Compare", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { reference = Integer.parseInt(input.getText().toString()); if (reference < 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { input.setError("Enter a whole number, 0 or greater"); return; }
            updateComparison(); saveState();
            log.append(recording, "# REFERENCE manual_steps="+reference+"; estimated_steps="+detector.steps()+"; active_ms="+elapsed+"\n"); dialog.dismiss();
        })); dialog.show();
    }
    private void updateComparison() {
        if (reference < 0) comparisonText.setText("Count your own steps for the same test, then compare.");
        else if (reference == 0) comparisonText.setText(detector.steps()+" false steps with an empty belt.");
        else comparisonText.setText(String.format(Locale.US, "Manual: %d · detected: %d · error: %+.1f%%", reference, detector.steps(), 100.0*(detector.steps()-reference)/reference));
    }
    private void chooseRecording() {
        File[] files = getFilesDir().listFiles((dir,name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) { toast("Run a test first"); return; }
        Arrays.sort(files, (a,b) -> b.getName().compareTo(a.getName()));
        String[] names = new String[files.length]; for (int i=0; i<files.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(this).setTitle("Export a recording").setItems(names, (d,which) -> {
            flush(); exportSource = files[which];
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv").putExtra(Intent.EXTRA_TITLE, exportSource.getName()); startActivityForResult(intent, 1);
        }).setNegativeButton("Cancel", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == 1 && result == RESULT_OK && data != null && data.getData() != null && exportSource != null) {
            try { log.copy(exportSource, getContentResolver().openOutputStream(data.getData()),
                () -> runOnUiThread(() -> toast("Recording exported")), error -> runOnUiThread(() -> toast("Export failed: "+error))); }
            catch (IOException e) { toast("Export failed: "+e.getMessage()); }
        }
    }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    private void saveState() {
        getPreferences(MODE_PRIVATE).edit().putInt("steps",detector.steps()).putLong("elapsed",elapsed)
            .putFloat("threshold",(float)detector.threshold()).putString("calibration",calibrationStatus)
            .putInt("reference",reference).putString("recording",recording == null ? "" : recording.getName()).apply();
    }
    @Override protected void onResume() {
        super.onResume(); detector.resetSignal(); rateStart = sampleCount = lastSensorNs = 0; rate = 0;
        if (accelerometer == null || !sensors.registerListener(this, accelerometer, 10000, 0)) {
            accelerometer = null; status = "Accelerometer unavailable";
        }
        ui.removeCallbacks(tick); ui.post(tick);
    }
    @Override protected void onPause() {
        pauseTest(running || calibrating ? "Paused · app left the screen" : status);
        sensors.unregisterListener(this); ui.removeCallbacks(tick); super.onPause();
    }
    @Override protected void onDestroy() { flush(); log.close(); super.onDestroy(); }

    private final class SignalView extends View {
        private final float[] samples = new float[500];
        private final boolean[] hits = new boolean[500];
        private int cursor, size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final DashPathEffect dash = new DashPathEffect(new float[]{dp(4),dp(4)},0);
        SignalView() { super(MainActivity.this); setBackground(rounded(CARD)); setContentDescription("Live filtered accelerometer trace with step markers and detection threshold"); }
        void add(float value, boolean hit) { samples[cursor] = value; hits[cursor] = hit; cursor = (cursor+1)%samples.length; size = Math.min(size+1,samples.length); }
        void clear() { size = cursor = 0; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float range = (float)Math.max(0.15,detector.threshold()*1.4);
            for (int i=0;i<size;i++) range = Math.max(range, Math.abs(samples[i])*1.1f);
            float mid = getHeight()*0.55f, scale = getHeight()*0.4f/range;
            paint.setColor(Color.rgb(43,64,76)); paint.setStrokeWidth(dp(1)); canvas.drawLine(0,mid,getWidth(),mid,paint);
            paint.setColor(MUTED); paint.setPathEffect(dash);
            float thresholdY = mid-(float)detector.threshold()*scale; canvas.drawLine(0,thresholdY,getWidth(),thresholdY,paint); paint.setPathEffect(null);
            path.reset();
            for (int i=0;i<size;i++) {
                int index = (cursor-size+i+samples.length)%samples.length;
                float x = (float)i/(samples.length-1)*getWidth(), y = mid-samples[index]*scale;
                if (i==0) path.moveTo(x,y); else path.lineTo(x,y);
                if (hits[index]) { paint.setColor(INK); canvas.drawCircle(x,y,dp(3),paint); }
            }
            paint.setColor(LIME); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); canvas.drawPath(path,paint); paint.setStyle(Paint.Style.FILL);
        }
    }
}
