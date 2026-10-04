package com.cardio.lab;

import android.content.*;

/** The screen's firmware re-selects the stock Echelon launcher as HOME at every boot, so CardioLab opens itself. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            context.startActivity(new Intent(context,ConsoleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
