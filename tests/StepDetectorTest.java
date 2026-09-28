import com.cardio.lab.StepDetector;
import java.util.Random;

/** No Android runtime needed. These tests validate mechanics, not treadmill accuracy. */
public class StepDetectorTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static StepDetector wave(double hz, double amplitude, boolean sideways) {
        StepDetector d = new StepDetector(); d.setThreshold(0.12);
        Random rng = new Random(3); long ns = 1_000_000_000L;
        while (ns < 31_000_000_000L) {
            double t = (ns-1_000_000_000L)/1e9;
            double value = 9.81 + amplitude*Math.sin(2*Math.PI*hz*t) + 0.002*rng.nextGaussian();
            d.add(ns, sideways ? value : 0, 0, sideways ? 0 : value, true);
            ns += 8_000_000L + rng.nextInt(4_000_001);
        }
        return d;
    }
    public static void main(String[] args) {
        check(wave(0, 0, false).steps() == 0, "Stationary noise must not count");
        check(wave(2, 0.03, false).steps() == 0, "Below-threshold belt vibration must not count");
        for (double hz : new double[]{1.2,2,3,3.5}) {
            StepDetector d = wave(hz, 0.5, false);
            int expected = (int)Math.round(29*hz);
            check(Math.abs(d.steps()-expected)<=2, "Cadence " + hz + ": " + d.steps()+" vs "+expected);
            check(Math.abs(d.cadence(31_000_000_000L)-hz*60)<3, "Cadence estimate");
            check(d.cadence(34_000_000_000L)==0, "Stale cadence clears");
        }
        check(wave(2,0.5,true).steps()==wave(2,0.5,false).steps(), "Orientation-independent magnitude");
        StepDetector paused = new StepDetector();
        for (int i=0;i<500;i++) paused.add(1_000_000_000L+i*10_000_000L,0,0,9.81+Math.sin(i*0.2),false);
        check(paused.steps()==0, "Preview/calibration never count");
        StepDetector d = wave(2,0.5,false); int old = d.steps(); d.resetSignal();
        d.add(90_000_000_000L,0,0,15,true);
        check(d.steps()==old, "Resume initialization cannot create a step");
        d.add(90_000_000_000L,0,0,15,true);
        d.add(89_000_000_000L,0,0,15,true);
        d.add(91_000_000_000L,Double.NaN,0,0,true);
        check(d.steps()==old, "Bad timestamps / invalid samples ignored");
        StepDetector ringing = wave(12,0.8,false);
        check(ringing.steps()<=120, "Refractory period caps double-counting");
        d.reset(); check(d.steps()==0 && d.cadence(100_000_000_000L)==0, "New test resets state");
        System.out.println("PASS: rest, baseline, walking/running cadence, jitter, orientation, preview, resume, invalid data, refractory, reset");
    }
}
