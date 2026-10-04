package com.cardio.lab;

import java.util.*;

/** Replays both recordings through the production detector. Never changes a live detector. */
public final class StepCalibration {
    public static final long NOISE_MS=6000, WALK_MS=30000;
    public static final class Sample {
        public final long ns;
        public final double x,y,z;
        public final boolean count;
        public Sample(long ns,double x,double y,double z,boolean count){this.ns=ns;this.x=x;this.y=y;this.z=z;this.count=count;}
    }
    public static final class Result {
        public final boolean accepted;
        public final String explanation;
        public final double threshold,low,high,noiseFloor;
        public final int reference,before,after,noiseSteps;
        private Result(boolean ok,String explanation,double threshold,double low,double high,double floor,int reference,int before,int after,int noiseSteps){
            this.accepted=ok;this.explanation=explanation;this.threshold=threshold;this.low=low;this.high=high;this.noiseFloor=floor;this.reference=reference;this.before=before;this.after=after;this.noiseSteps=noiseSteps;
        }
    }
    public static int replay(List<Sample> data,double threshold){
        StepDetector detector=new StepDetector();detector.setThreshold(threshold);
        for(Sample s:data)detector.add(s.ns,s.x,s.y,s.z,s.count);
        return detector.steps();
    }
    private static String validate(List<Sample> data,long expectedMs){
        if(data.size()<100)return "Too few sensor samples. Repeat calibration.";
        long previous=0,first=0,last=0;int measured=0;
        for(Sample s:data){
            if(!Double.isFinite(s.x)||!Double.isFinite(s.y)||!Double.isFinite(s.z))return "Invalid sensor readings. Repeat calibration.";
            if(previous!=0&&(s.ns<=previous||s.ns-previous>150_000_000L))return "Sensor recording was interrupted. Repeat calibration.";
            previous=s.ns;
            if(s.count){if(first==0)first=s.ns;last=s.ns;measured++;}
        }
        if(measured<100||last-first<(expectedMs-300)*1_000_000L)return "Recording was too short. Keep the app open for both stages.";
        if(first-data.get(0).ns<1_000_000_000L)return "Sensor warmup was incomplete. Repeat calibration.";
        return null;
    }
    private static Result failure(String text,int reference,int before){return new Result(false,text,0,0,0,0,reference,before,0,0);}
    public static Result fit(List<Sample> noise,List<Sample> walking,int reference,double oldThreshold){
        if(reference<20||reference>120)return failure("Enter 20–120 individual foot contacts counted during the 30-second window.",reference,0);
        String error=validate(noise,NOISE_MS);if(error==null)error=validate(walking,WALK_MS);
        if(error!=null)return failure(error,reference,0);
        int before=replay(walking,oldThreshold);
        ArrayList<Double> baseline=new ArrayList<>();StepDetector filter=new StepDetector();
        for(Sample s:noise){filter.add(s.ns,s.x,s.y,s.z,false);if(s.count)baseline.add(Math.abs(filter.signal()));}
        Collections.sort(baseline);double floor=Math.max(.025,1.7*baseline.get((int)((baseline.size()-1)*.99)));
        filter=new StepDetector();double peak=0;
        for(Sample s:walking){filter.add(s.ns,s.x,s.y,s.z,false);if(s.count)peak=Math.max(peak,Math.abs(filter.signal()));}
        if(peak<=floor)return failure("Walking vibrations do not clearly exceed empty-belt noise. Check phone placement and repeat both stages.",reference,before);
        final int size=321;double[] thresholds=new double[size];int[] counts=new int[size],noiseCounts=new int[size];
        int best=Integer.MAX_VALUE;
        for(int i=0;i<size;i++){
            if(Thread.currentThread().isInterrupted())return failure("Calibration cancelled.",reference,before);
            thresholds[i]=floor*Math.pow(peak*1.05/floor,i/(double)(size-1));
            noiseCounts[i]=replay(noise,thresholds[i]);counts[i]=replay(walking,thresholds[i]);
            if(noiseCounts[i]==0)best=Math.min(best,Math.abs(counts[i]-reference));
        }
        if(best>Math.max(1,Math.floor(reference*.05)))return failure("No threshold could match your count within 5% while rejecting empty-belt steps. Keep the current setting; repeat at a steady pace or change phone placement.",reference,before);
        // Select the center of the widest equally good band, avoiding a fragile edge value.
        int start=-1,lo=-1,hi=-1;double width=-1;
        for(int i=0;i<=size;i++){
            boolean matches=i<size&&noiseCounts[i]==0&&Math.abs(counts[i]-reference)==best;
            if(matches&&start<0)start=i;
            if(!matches&&start>=0){double w=Math.log(thresholds[i-1]/thresholds[start]);if(w>width){width=w;lo=start;hi=i-1;}start=-1;}
        }
        if(hi-lo<2)return failure("The matching threshold range is too narrow to trust. Repeat at a steady pace or change phone placement.",reference,before);
        int chosen=(lo+hi)/2;
        return new Result(true,"This fits the calibration walk only. Check a separate 100-step walk before relying on it.",thresholds[chosen],thresholds[lo],thresholds[hi],floor,reference,before,counts[chosen],noiseCounts[chosen]);
    }
}
