package ai.arena.prowser;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(8,9,13);
    private final int SURFACE = Color.rgb(20,22,30);
    private final int SURFACE2 = Color.rgb(28,30,40);
    private final int TEXT = Color.rgb(246,247,251);
    private final int MUTED = Color.rgb(145,151,168);
    private final int ACCENT = Color.rgb(155,135,255);
    private final int OK = Color.rgb(116,224,184);

    private LinearLayout root, topBar, bottomBar;
    private FrameLayout content;
    private EditText address;
    private TextView shield, tabCount;
    private ProgressBar progress;
    private WebView web;
    private View home;
    private View customView;
    private FrameLayout customContainer;
    private WebChromeClient.CustomViewCallback customCallback;
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int activeTab = -1;
    private SharedPreferences prefs;
    private String privacy = "protected";
    private boolean clearOnExit = true;
    private long lastBack = 0;

    static class Tab {
        String url = "";
        String title = "New tab";
        Tab(String u){ url=u; }
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("prowser",MODE_PRIVATE);
        privacy=prefs.getString("privacy","protected");
        clearOnExit=prefs.getBoolean("clearOnExit",true);
        buildUi();
        newTab(false);
        if(!prefs.getBoolean("ageConfirmed",false)) showAgeGate();
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private TextView text(String s,int sp,int color){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp((int)radius)); return g;
    }
    private GradientDrawable strokeBg(int color,float radius,int stroke){
        GradientDrawable g=bg(color,radius); g.setStroke(dp(1),stroke); return g;
    }
    private Button iconButton(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(20); b.setTextColor(TEXT); b.setAllCaps(false);
        b.setBackground(bg(Color.TRANSPARENT,14)); b.setPadding(0,0,0,0);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        return b;
    }

    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);

        topBar=new LinearLayout(this); topBar.setOrientation(LinearLayout.VERTICAL); topBar.setPadding(dp(14),dp(10),dp(14),dp(8));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("Prowser",16,TEXT); brand.setTypeface(null,1);
        shield=text("  ●  Protected",11,OK); shield.setGravity(Gravity.CENTER);
        shield.setPadding(dp(9),0,dp(9),0); shield.setBackground(strokeBg(SURFACE,12,Color.rgb(45,48,61)));
        shield.setOnClickListener(v->showPrivacyMenu());
        titleRow.addView(brand,new LinearLayout.LayoutParams(0,dp(38),1));
        titleRow.addView(shield,new LinearLayout.LayoutParams(-2,dp(34)));
        topBar.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(40)));

        LinearLayout addressRow=new LinearLayout(this); addressRow.setGravity(Gravity.CENTER_VERTICAL);
        address=new EditText(this);
        address.setSingleLine(true); address.setTextColor(TEXT); address.setHintTextColor(MUTED); address.setHint("Search privately or enter address");
        address.setTextSize(14); address.setPadding(dp(14),0,dp(14),0); address.setBackground(strokeBg(SURFACE,16,Color.rgb(47,50,64)));
        address.setImeOptions(EditorInfo.IME_ACTION_GO); address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setOnEditorActionListener((v,id,event)->{ if(id==EditorInfo.IME_ACTION_GO || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){ navigateInput(address.getText().toString()); return true;} return false;});
        Button go=iconButton("↻"); go.setTextSize(18); go.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE) web.reload(); else address.requestFocus();});
        addressRow.addView(address,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(44),dp(44)); gp.leftMargin=dp(6); addressRow.addView(go,gp);
        topBar.addView(addressRow,new LinearLayout.LayoutParams(-1,dp(54)));
        root.addView(topBar,new LinearLayout.LayoutParams(-1,dp(104)));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgress(0);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));

        content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        buildHome();
        buildWeb();

        bottomBar=new LinearLayout(this); bottomBar.setGravity(Gravity.CENTER); bottomBar.setPadding(dp(8),dp(4),dp(8),dp(6)); bottomBar.setBackgroundColor(BG);
        Button back=iconButton("‹"); Button fwd=iconButton("›"); Button homeBtn=iconButton("⌂"); Button tabsBtn=iconButton("□"); Button menu=iconButton("⋯");
        back.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE && web.canGoBack()) web.goBack(); else showHome(); });
        fwd.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE && web.canGoForward()) web.goForward(); });
        homeBtn.setOnClickListener(v->showHome());
        tabsBtn.setOnClickListener(v->showTabsDialog());
        menu.setOnClickListener(v->showMainMenu(menu));
        for(Button x:new Button[]{back,fwd,homeBtn,tabsBtn,menu}) bottomBar.addView(x,new LinearLayout.LayoutParams(0,dp(54),1));
        tabCount=tabsBtn;
        root.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(64)));
        setContentView(root);
        updateShield();
    }

    private void buildHome(){
        LinearLayout h=new LinearLayout(this); h.setOrientation(LinearLayout.VERTICAL); h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(dp(24),dp(44),dp(24),dp(24)); h.setBackgroundColor(BG);
        TextView mark=text("◐",44,ACCENT); mark.setGravity(Gravity.CENTER);
        TextView title=text("Browse without the noise.",34,TEXT); title.setGravity(Gravity.CENTER); title.setTypeface(null,1);
        TextView sub=text("Private by default. Built for video.",14,MUTED); sub.setGravity(Gravity.CENTER);
        Button start=new Button(this); start.setText("Search or enter address"); start.setTextSize(15); start.setTextColor(TEXT); start.setAllCaps(false);
        start.setBackground(strokeBg(SURFACE,18,Color.rgb(52,55,70))); start.setOnClickListener(v->{ address.requestFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
        TextView note=text("No account  •  Third-party cookies restricted",11,MUTED); note.setGravity(Gravity.CENTER);
        h.addView(mark,new LinearLayout.LayoutParams(-1,dp(82)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(10); h.addView(title,tp);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(12); h.addView(sub,sp);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(58)); bp.topMargin=dp(36); h.addView(start,bp);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(24); h.addView(note,np);
        home=h; content.addView(home,new FrameLayout.LayoutParams(-1,-1));
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void buildWeb(){
        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setSupportZoom(true); s.setUseWideViewPort(true); s.setLoadWithOverviewMode(false); s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setGeolocationEnabled(false); s.setSaveFormData(false);
        if(Build.VERSION.SDK_INT>=26) s.setSafeBrowsingEnabled(true);
        CookieManager cm=CookieManager.getInstance(); cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(web,false);
        web.setBackgroundColor(Color.WHITE); web.setVisibility(View.GONE);
        web.setWebViewClient(new ProwserClient());
        web.setWebChromeClient(new ProwserChrome());
        web.setDownloadListener((url,ua,cd,mime,len)->confirmDownload(url,ua,cd,mime));
        content.addView(web,new FrameLayout.LayoutParams(-1,-1));
    }

    private void showAgeGate(){
        new AlertDialog.Builder(this)
            .setTitle("Prowser is for adults")
            .setMessage("Confirm that you are 18 or older to continue. Prowser does not create an account or store your age.")
            .setCancelable(false)
            .setNegativeButton("Exit",(d,w)->finish())
            .setPositiveButton("I am 18+",(d,w)->{ prefs.edit().putBoolean("ageConfirmed",true).apply(); showPrivacyIntro(); })
            .show();
    }

    private void showPrivacyIntro(){
        String[] levels={"Private — balanced compatibility","Protected — stronger isolation","Maximum — session-first privacy"};
        int checked=privacy.equals("private")?0:privacy.equals("maximum")?2:1;
        new AlertDialog.Builder(this).setTitle("Default privacy")
            .setSingleChoiceItems(levels,checked,(d,which)->{
                privacy=which==0?"private":which==2?"maximum":"protected";
                prefs.edit().putString("privacy",privacy).apply(); applyPrivacy(); updateShield(); d.dismiss();
            }).show();
    }

    private void updateShield(){
        String label=privacy.equals("maximum")?"Maximum":privacy.equals("private")?"Private":"Protected";
        shield.setText("  ●  "+label);
        shield.setTextColor(privacy.equals("private")?MUTED:OK);
    }

    private void applyPrivacy(){
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.getSettings().setCacheMode(privacy.equals("maximum")?WebSettings.LOAD_NO_CACHE:WebSettings.LOAD_DEFAULT);
        if(privacy.equals("maximum")) web.clearCache(true);
    }

    private void navigateInput(String raw){
        String v=raw.trim(); if(v.isEmpty()) return;
        String url;
        if(v.matches("(?i)^https?://.*")) url=v;
        else if(v.matches("^[^\\s]+\\.[a-zA-Z]{2,}(/.*)?$")) url="https://"+v;
        else url="https://duckduckgo.com/?q="+Uri.encode(v);
        loadUrl(cleanTracking(url));
    }

    private String cleanTracking(String raw){
        try{
            Uri u=Uri.parse(raw); Uri.Builder b=u.buildUpon().clearQuery();
            Set<String> bad=new HashSet<>(Arrays.asList("utm_source","utm_medium","utm_campaign","utm_term","utm_content","gclid","fbclid","mc_cid","mc_eid"));
            for(String k:u.getQueryParameterNames()) if(!bad.contains(k.toLowerCase())) for(String val:u.getQueryParameters(k)) b.appendQueryParameter(k,val);
            return b.build().toString();
        }catch(Exception e){ return raw; }
    }

    private void loadUrl(String url){
        if(!url.startsWith("https://")){ Toast.makeText(this,"Prowser requires secure HTTPS pages",Toast.LENGTH_SHORT).show(); return; }
        if(activeTab<0) newTab(false);
        Tab t=tabs.get(activeTab); t.url=url; t.title=host(url);
        applyPrivacy(); home.setVisibility(View.GONE); web.setVisibility(View.VISIBLE); address.setText(url); web.loadUrl(url); updateTabsLabel();
    }

    private String host(String u){ try{return Uri.parse(u).getHost().replace("www.","");}catch(Exception e){return "Page";} }

    private void showHome(){
        if(web!=null){ web.stopLoading(); web.setVisibility(View.GONE); }
        home.setVisibility(View.VISIBLE); progress.setProgress(0); address.setText(""); address.setHint("Search privately or enter address");
    }

    private void newTab(boolean showDialog){
        tabs.add(new Tab("")); activeTab=tabs.size()-1; showHome(); updateTabsLabel();
        if(showDialog) Toast.makeText(this,"New private tab",Toast.LENGTH_SHORT).show();
    }

    private void updateTabsLabel(){ if(tabCount!=null) tabCount.setText(tabs.size()>9?"□":"□\n"+tabs.size()); }

    private void showTabsDialog(){
        String[] names=new String[tabs.size()+1];
        for(int i=0;i<tabs.size();i++){ Tab t=tabs.get(i); names[i]=(i==activeTab?"• ":"")+ (t.url.isEmpty()?"New private tab":t.title); }
        names[tabs.size()]="+ New tab";
        new AlertDialog.Builder(this).setTitle("Tabs").setItems(names,(d,which)->{
            if(which==tabs.size()){ newTab(true); return; }
            activeTab=which; Tab t=tabs.get(activeTab); if(t.url.isEmpty()) showHome(); else loadUrl(t.url);
        }).setNegativeButton("Close current",(d,w)->closeCurrentTab()).show();
    }

    private void closeCurrentTab(){
        if(tabs.isEmpty()) return;
        tabs.remove(activeTab); if(tabs.isEmpty()) tabs.add(new Tab(""));
        activeTab=Math.max(0,Math.min(activeTab,tabs.size()-1));
        Tab t=tabs.get(activeTab); if(t.url.isEmpty()) showHome(); else loadUrl(t.url); updateTabsLabel();
    }

    private void showPrivacyMenu(){
        String[] items={"Private","Protected","Maximum Privacy","Clear current session"};
        int checked=privacy.equals("private")?0:privacy.equals("maximum")?2:1;
        new AlertDialog.Builder(this).setTitle("Privacy").setSingleChoiceItems(items,checked,(d,which)->{
            if(which==3){ clearSession(); d.dismiss(); return; }
            privacy=which==0?"private":which==2?"maximum":"protected";
            prefs.edit().putString("privacy",privacy).apply(); applyPrivacy(); updateShield(); d.dismiss();
        }).setNeutralButton(clearOnExit?"Clear on exit: ON":"Clear on exit: OFF",(d,w)->{
            clearOnExit=!clearOnExit; prefs.edit().putBoolean("clearOnExit",clearOnExit).apply(); Toast.makeText(this,clearOnExit?"Clear on exit enabled":"Clear on exit disabled",Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void showMainMenu(View anchor){
        PopupMenu m=new PopupMenu(this,anchor);
        m.getMenu().add("New tab"); m.getMenu().add("Privacy"); m.getMenu().add("Clear session"); m.getMenu().add("Open in another browser");
        if(customView!=null && Build.VERSION.SDK_INT>=26) m.getMenu().add("Picture in picture");
        m.setOnMenuItemClickListener(item->{
            String x=item.getTitle().toString();
            if(x.equals("New tab")) newTab(true);
            else if(x.equals("Privacy")) showPrivacyMenu();
            else if(x.equals("Clear session")) clearSession();
            else if(x.equals("Open in another browser")) openExternal();
            else if(x.equals("Picture in picture")) enterPip();
            return true;
        }); m.show();
    }

    private void clearSession(){
        web.stopLoading(); web.clearHistory(); web.clearCache(true); web.clearFormData(); WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush();
        tabs.clear(); tabs.add(new Tab("")); activeTab=0; showHome(); updateTabsLabel();
        Toast.makeText(this,"Private session cleared",Toast.LENGTH_SHORT).show();
    }

    private void openExternal(){
        if(activeTab<0 || tabs.get(activeTab).url.isEmpty()) return;
        try{ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(tabs.get(activeTab).url))); }catch(Exception ignored){}
    }

    private void confirmDownload(String url,String ua,String cd,String mime){
        new AlertDialog.Builder(this).setTitle("Download file?")
            .setMessage("Prowser will hand this download to Android's secure download manager.")
            .setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->{
                try{
                    DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url)); r.setMimeType(mime);
                    r.addRequestHeader("User-Agent",ua); r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url,cd,mime));
                    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                }catch(Exception e){ Toast.makeText(this,"Download unavailable",Toast.LENGTH_SHORT).show(); }
            }).show();
    }

    private void enterPip(){
        if(Build.VERSION.SDK_INT>=26){
            PictureInPictureParams p=new PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(16,9)).build();
            enterPictureInPictureMode(p);
        }
    }

    private class ProwserClient extends WebViewClient {
        @Override public void onPageStarted(WebView v,String url,Bitmap icon){
            progress.setProgress(8); address.setText(url);
            if(activeTab>=0){ tabs.get(activeTab).url=url; tabs.get(activeTab).title=host(url); }
        }
        @Override public void onPageFinished(WebView v,String url){
            progress.setProgress(0); String t=v.getTitle();
            if(activeTab>=0){ tabs.get(activeTab).url=url; tabs.get(activeTab).title=(t==null||t.isEmpty())?host(url):t; }
            address.setText(url);
        }
        @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
            String u=r.getUrl().toString();
            if(u.startsWith("https://")){
                String clean=cleanTracking(u); if(!clean.equals(u)){ v.loadUrl(clean); return true; } return false;
            }
            if(u.startsWith("http://")){ Toast.makeText(MainActivity.this,"Blocked insecure HTTP page",Toast.LENGTH_SHORT).show(); return true; }
            Toast.makeText(MainActivity.this,"External app link blocked",Toast.LENGTH_SHORT).show(); return true;
        }
        @Override public void onSafeBrowsingHit(WebView view,WebResourceRequest req,int threatType,SafeBrowsingResponse callback){
            callback.backToSafety(true); Toast.makeText(MainActivity.this,"Unsafe page blocked",Toast.LENGTH_LONG).show();
        }
    }

    private class ProwserChrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView v,int p){ progress.setProgress(p>=100?0:p); }
        @Override public void onPermissionRequest(PermissionRequest req){
            runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("Website permission")
                .setMessage(host(web.getUrl())+" is requesting camera or microphone access.")
                .setNegativeButton("Block",(d,w)->req.deny())
                .setPositiveButton("Allow once",(d,w)->req.grant(req.getResources())).show());
        }
        @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){ cb.invoke(origin,false,false); }
        @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message resultMsg){
            Toast.makeText(MainActivity.this,"Pop-up blocked",Toast.LENGTH_SHORT).show(); return false;
        }
        @Override public void onShowCustomView(View view,CustomViewCallback cb){
            if(customView!=null){ cb.onCustomViewHidden(); return; }
            customView=view; customCallback=cb; customContainer=new FrameLayout(MainActivity.this); customContainer.setBackgroundColor(Color.BLACK);
            customContainer.addView(view,new FrameLayout.LayoutParams(-1,-1)); addContentView(customContainer,new ViewGroup.LayoutParams(-1,-1));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            installVideoGestures(customContainer);
        }
        @Override public void onHideCustomView(){ hideCustomVideo(); }
    }

    private void installVideoGestures(View v){
        final float[] startY={0}; final int[] startVol={0}; final float[] startBright={0};
        v.setOnTouchListener((view,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                startY[0]=e.getY();
                android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                startVol[0]=am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                startBright[0]=getWindow().getAttributes().screenBrightness<0?0.5f:getWindow().getAttributes().screenBrightness;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dy=(startY[0]-e.getY())/Math.max(1f,v.getHeight());
                if(e.getX()<v.getWidth()/2f){
                    WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.screenBrightness=Math.max(.05f,Math.min(1f,startBright[0]+dy)); getWindow().setAttributes(lp);
                }else{
                    android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                    int max=am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,startVol[0]+Math.round(dy*max))),0);
                }
                return true;
            }
            return e.getAction()==MotionEvent.ACTION_UP;
        });
    }

    private void hideCustomVideo(){
        if(customView==null) return;
        ((ViewGroup)customView.getParent()).removeView(customView);
        if(customContainer!=null && customContainer.getParent()!=null) ((ViewGroup)customContainer.getParent()).removeView(customContainer);
        if(customCallback!=null) customCallback.onCustomViewHidden();
        customView=null; customContainer=null; customCallback=null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override public void onBackPressed(){
        if(customView!=null){ hideCustomVideo(); return; }
        if(web.getVisibility()==View.VISIBLE && web.canGoBack()){ web.goBack(); return; }
        if(web.getVisibility()==View.VISIBLE){ showHome(); return; }
        long n=System.currentTimeMillis(); if(n-lastBack<1500){ super.onBackPressed(); } else { lastBack=n; Toast.makeText(this,"Back again to exit",Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onDestroy(){
        if(clearOnExit) clearSession();
        if(web!=null){ web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
