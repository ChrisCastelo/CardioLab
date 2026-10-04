package com.cardio.lab;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Isolated YouTube viewer fallback. No JS bridge, file access, or credential interception. */
public final class VideoActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private View fullScreen;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    @SuppressLint("SetJavaScriptEnabled") @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
        root=new FrameLayout(this);root.setBackgroundColor(ConsoleUi.BG);root.setPadding(getIntent().getIntExtra("rail",0),getIntent().getIntExtra("top",0),getIntent().getIntExtra("rail",0),getIntent().getIntExtra("bottom",0));web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return false;Uri uri=request.getUrl();String host=uri.getHost();if("https".equals(uri.getScheme())&&("m.youtube.com".equals(host)||"www.youtube.com".equals(host)||"youtube.com".equals(host)||"youtu.be".equals(host)))return false;if(!"https".equals(uri.getScheme()))return true;
                try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){Toast.makeText(VideoActivity.this,"Sign-in needs an installed browser or YouTube app. This viewer is for watching without signing in.",Toast.LENGTH_LONG).show();}return true;}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())Toast.makeText(VideoActivity.this,"YouTube could not load. Check Wi-Fi and use an up-to-date browser.",Toast.LENGTH_LONG).show();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullScreen!=null){callback.onCustomViewHidden();return;}fullScreen=view;fullScreenCallback=callback;root.addView(view,new FrameLayout.LayoutParams(-1,-1));web.setVisibility(View.GONE);}
            @Override public void onHideCustomView(){hideVideo();}
        });
        if(state==null)web.loadUrl("https://m.youtube.com/");else web.restoreState(state);
    }
    private void hideVideo(){if(fullScreen!=null){root.removeView(fullScreen);fullScreen=null;web.setVisibility(View.VISIBLE);fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}}
    @Override public void onBackPressed(){if(fullScreen!=null)hideVideo();else if(web.canGoBack())web.goBack();else{startActivity(new Intent(this,ConsoleActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}}
    @Override protected void onSaveInstanceState(Bundle state){web.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onPause(){web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();web.onResume();}
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
