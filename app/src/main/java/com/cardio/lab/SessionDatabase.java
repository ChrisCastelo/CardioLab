package com.cardio.lab;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Called only from the activity's serial storage executor. Drafts recover paused. */
final class SessionDatabase extends SQLiteOpenHelper {
    static final class Sample {
        final long t; final double vibration; final int steps,cadence,heart;
        Sample(long t,double vibration,int steps,int cadence,int heart){this.t=t;this.vibration=vibration;this.steps=steps;this.cadence=cadence;this.heart=heart;}
    }
    SessionDatabase(Context c){super(c,"workouts.db",null,1);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,date INTEGER NOT NULL,elapsed INTEGER NOT NULL,steps INTEGER NOT NULL,hr_total INTEGER NOT NULL,hr_count INTEGER NOT NULL,threshold REAL NOT NULL,source TEXT NOT NULL,saved INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,t INTEGER NOT NULL,vibration REAL NOT NULL,steps INTEGER NOT NULL,cadence INTEGER NOT NULL,heart INTEGER)");
        db.execSQL("CREATE INDEX sample_session_time ON samples(session_id,t)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){throw new IllegalStateException("Unsupported database version");}
    void persist(Workout w,List<Sample> samples,boolean saved){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("id",w.id);v.put("date",w.date);v.put("elapsed",w.elapsed);v.put("steps",w.steps);v.put("hr_total",w.hrTotal);v.put("hr_count",w.hrCount);v.put("threshold",w.threshold);v.put("source",w.source);v.put("saved",saved?1:0);
            if(db.update("sessions",v,"id=?",new String[]{w.id})==0)db.insertOrThrow("sessions",null,v);
            for(Sample s:samples){ContentValues p=new ContentValues();p.put("session_id",w.id);p.put("t",s.t);p.put("vibration",s.vibration);p.put("steps",s.steps);p.put("cadence",s.cadence);if(s.heart>0)p.put("heart",s.heart);else p.putNull("heart");db.insertOrThrow("samples",null,p);}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private Workout read(Cursor c){Workout w=new Workout();w.id=c.getString(0);w.date=c.getLong(1);w.elapsed=c.getLong(2);w.steps=c.getInt(3);w.hrTotal=c.getLong(4);w.hrCount=c.getLong(5);w.threshold=c.getDouble(6);w.source=c.getString(7);return w;}
    List<Workout> list(boolean saved){
        ArrayList<Workout> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,date,elapsed,steps,hr_total,hr_count,threshold,source FROM sessions WHERE saved=? ORDER BY date DESC",new String[]{saved?"1":"0"})){while(c.moveToNext())result.add(read(c));}
        return result;
    }
    void discard(String id){getWritableDatabase().delete("sessions","id=?",new String[]{id});}
    List<Sample> samples(Workout w){
        ArrayList<Sample> result=new ArrayList<>();
        // Bound detail graph size while retaining the persisted 10 Hz trace.
        long bucket=Math.max(100,w.elapsed/1200);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(t),AVG(vibration),MAX(steps),AVG(cadence),AVG(heart) FROM samples WHERE session_id=? GROUP BY t / ? ORDER BY MIN(t)",new String[]{w.id,Long.toString(bucket)})){
            while(c.moveToNext())result.add(new Sample(c.getLong(0),c.getDouble(1),c.getInt(2),c.getInt(3),c.isNull(4)?0:c.getInt(4)));
        }return result;
    }
}
