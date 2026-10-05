package com.cardio.lab;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * The treadmill screen has no navigation bar, and TV-style video apps expect a remote's Back key.
 * This service only performs the system Back action when the video bar's Back button asks for it;
 * it reads no window content. Enabled over adb (see docs/TREADMILL-HANDOFF.md).
 */
public final class NavigationService extends AccessibilityService {
    private static NavigationService active;
    static boolean back(){NavigationService s=active;return s!=null&&s.performGlobalAction(GLOBAL_ACTION_BACK);}
    @Override protected void onServiceConnected(){active=this;}
    @Override public boolean onUnbind(android.content.Intent intent){active=null;return super.onUnbind(intent);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
}
