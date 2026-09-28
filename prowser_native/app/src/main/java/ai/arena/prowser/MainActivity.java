package ai.arena.prowser;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
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
    private final int ACCENT = Color.rgb(150,126,255);
    private final int OK = Color.rgb(102,221,176);

    private LinearLayout root, topBar, bottomBar;
    private FrameLayout content;
    private EditText address;
    private TextView shield, engineChip, tabCount;
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
    private String searchEngine = "DuckDuckGo";
    private boolean clearOnExit = true;
    private long lastBack = 0;
    private int bottomInset = 0;

    static class Tab {
        String url = "";
        String title = "New tab";
        Tab(String u){ url=u; }
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setTheme(R.style.AppTheme);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("prowser",MODE_PRIVATE);
        privacy=prefs.getString("privacy","protected");
        searchEngine=prefs.getString("searchEngine","DuckDuckGo");
        clearOnExit=prefs.getBoolean("clearOnExit",true);
        buildUi();
        newTab(false);
        if(!prefs.getBoolean("ageConfirmed",false)) showAgeGate();
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private TextView text(String s,int sp,int color){
        TextView v=new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp((int)radius));
        return g;
    }

    private GradientDrawable strokeBg(int color,float radius,int stroke){
        GradientDrawable g=bg(color,radius);
        g.setStroke(dp(1),stroke);
        return g;
    }

    private TextView navButton(String symbol,String label){
        TextView v=text(symbol+"\n"+label,11,TEXT);
        v.setGravity(Gravity.CENTER);
        v.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        v.setLineSpacing(0f,.92f);
        v.setPadding(dp(4),dp(3),dp(4),dp(2));
        v.setBackground(bg(Color.TRANSPARENT,14));
        v.setClickable(true); v.setFocusable(true);
        return v;
    }

    private void buildUi(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                bottomInset=bars.bottom;
                v.setPadding(0,bars.top,0,0);
                if(bottomBar!=null){
                    bottomBar.setPadding(dp(8),dp(5),dp(8),dp(8)+bottomInset);
                    ViewGroup.LayoutParams p=bottomBar.getLayoutParams();
                    if(p!=null){ p.height=dp(70)+bottomInset; bottomBar.setLayoutParams(p); }
                }
            }else{
                bottomInset=insets.getSystemWindowInsetBottom();
                v.setPadding(0,insets.getSystemWindowInsetTop(),0,0);
                if(bottomBar!=null){
                    bottomBar.setPadding(dp(8),dp(5),dp(8),dp(8)+bottomInset);
                    ViewGroup.LayoutParams p=bottomBar.getLayoutParams();
                    if(p!=null){ p.height=dp(70)+bottomInset; bottomBar.setLayoutParams(p); }
                }
            }
            return insets;
        });

        topBar=new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setPadding(dp(12),dp(7),dp(12),dp(7));

        LinearLayout utilityRow=new LinearLayout(this);
        utilityRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand=text("Prowser",16,TEXT);
        brand.setTypeface(null,1);

        engineChip=text(searchEngine,11,MUTED);
        engineChip.setGravity(Gravity.CENTER);
        engineChip.setPadding(dp(10),0,dp(10),0);
        engineChip.setBackground(strokeBg(SURFACE,12,Color.rgb(45,48,61)));
        engineChip.setOnClickListener(v->showSearchEnginePicker());

        shield=text("●  Protected",11,OK);
        shield.setGravity(Gravity.CENTER);
        shield.setPadding(dp(10),0,dp(10),0);
        shield.setBackground(strokeBg(SURFACE,12,Color.rgb(45,48,61)));
        shield.setOnClickListener(v->showPrivacyMenu());

        utilityRow.addView(brand,new LinearLayout.LayoutParams(0,dp(38),1));
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,dp(34));
        ep.rightMargin=dp(7); utilityRow.addView(engineChip,ep);
        utilityRow.addView(shield,new LinearLayout.LayoutParams(-2,dp(34)));
        topBar.addView(utilityRow,new LinearLayout.LayoutParams(-1,dp(40)));

        LinearLayout addressRow=new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);

        address=new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(TEXT);
        address.setHintTextColor(MUTED);
        address.setHint("Search or enter address");
        address.setTextSize(14);
        address.setSelectAllOnFocus(false);
        address.setPadding(dp(14),0,dp(14),0);
        address.setBackground(strokeBg(SURFACE,17,Color.rgb(49,52,67)));
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setOnFocusChangeListener((v,focused)->{ if(focused && web.getVisibility()==View.VISIBLE) address.selectAll(); });
        address.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_GO || id==EditorInfo.IME_ACTION_SEARCH || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                navigateInput(address.getText().toString());
                return true;
            }
            return false;
        });

        TextView reload=navButton("↻","");
        reload.setTextSize(21);
        reload.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE) web.reload(); else address.requestFocus(); });

        addressRow.addView(address,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(44),dp(44));
        rp.leftMargin=dp(6); addressRow.addView(reload,rp);
        topBar.addView(addressRow,new LinearLayout.LayoutParams(-1,dp(54)));
        root.addView(topBar,new LinearLayout.LayoutParams(-1,dp(101)));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));

        content=new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        buildHome();
        buildWeb();

        bottomBar=new LinearLayout(this);
        bottomBar.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        bottomBar.setPadding(dp(8),dp(5),dp(8),dp(8));
        bottomBar.setBackgroundColor(BG);

        TextView back=navButton("‹","Back");
        TextView fwd=navButton("›","Forward");
        TextView homeBtn=navButton("⌂","Home");
        TextView tabsBtn=navButton("▢","Tabs");
        TextView menu=navButton("⋯","Menu");

        back.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE && web.canGoBack()) web.goBack(); else showHome(); });
        fwd.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE && web.canGoForward()) web.goForward(); });
        homeBtn.setOnClickListener(v->showHome());
        tabsBtn.setOnClickListener(v->showTabsDialog());
        menu.setOnClickListener(v->showMainMenu(menu));

        for(TextView x:new TextView[]{back,fwd,homeBtn,tabsBtn,menu}){
            bottomBar.addView(x,new LinearLayout.LayoutParams(0,dp(58),1));
        }
        tabCount=tabsBtn;
        root.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(70)));

        setContentView(root);
        root.requestApplyInsets();
        updateShield();
        updateEngineChip();
    }

    private void buildHome(){
        LinearLayout h=new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(dp(26),dp(44),dp(26),dp(24));
        h.setBackgroundColor(BG);

        TextView mark=text("◒",46,ACCENT);
        mark.setGravity(Gravity.CENTER);

        TextView title=text("Private browsing, without the clutter.",31,TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setTypeface(null,1);

        TextView sub=text("Fast. Discreet. Built for video.",14,MUTED);
        sub.setGravity(Gravity.CENTER);

        Button start=new Button(this);
        start.setText("Search with "+searchEngine);
        start.setTextSize(15); start.setTextColor(TEXT); start.setAllCaps(false);
        start.setBackground(strokeBg(SURFACE,18,Color.rgb(52,55,70)));
        start.setOnClickListener(v->{
            address.requestFocus();
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(address,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });

        TextView changeEngine=text("Change search engine",12,ACCENT);
        changeEngine.setGravity(Gravity.CENTER);
        changeEngine.setPadding(0,dp(12),0,dp(12));
        changeEngine.setClickable(true);
        changeEngine.setOnClickListener(v->showSearchEnginePicker());

        TextView note=text("No account  •  Third-party cookies restricted",11,MUTED);
        note.setGravity(Gravity.CENTER);

        h.addView(mark,new LinearLayout.LayoutParams(-1,dp(78)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(8); h.addView(title,tp);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(12); h.addView(sub,sp);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(58)); bp.topMargin=dp(34); h.addView(start,bp);
        h.addView(changeEngine,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(12); h.addView(note,np);

        home=h;
        content.addView(home,new FrameLayout.LayoutParams(-1,-1));
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void buildWeb(){
        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        if(Build.VERSION.SDK_INT>=26) s.setSafeBrowsingEnabled(true);

        CookieManager cm=CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web,false);

        web.setBackgroundColor(Color.WHITE);
        web.setVisibility(View.GONE);
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
            .setPositiveButton("I am 18+",(d,w)->{
                prefs.edit().putBoolean("ageConfirmed",true).apply();
                showSearchEnginePicker();
            }).show();
    }

    private void showSearchEnginePicker(){
        final String[] engines={"DuckDuckGo","Google","Bing","Brave Search"};
        int checked=0;
        for(int i=0;i<engines.length;i++) if(engines[i].equals(searchEngine)) checked=i;
        new AlertDialog.Builder(this)
            .setTitle("Search engine")
            .setSingleChoiceItems(engines,checked,(d,which)->{
                searchEngine=engines[which];
                prefs.edit().putString("searchEngine",searchEngine).apply();
                updateEngineChip();
                rebuildHomeLabel();
                d.dismiss();
            })
            .setNegativeButton("Cancel",null)
            .show();
    }

    private void rebuildHomeLabel(){
        if(home instanceof ViewGroup){
            ViewGroup vg=(ViewGroup)home;
            for(int i=0;i<vg.getChildCount();i++){
                View v=vg.getChildAt(i);
                if(v instanceof Button && ((Button)v).getText().toString().startsWith("Search with ")){
                    ((Button)v).setText("Search with "+searchEngine);
                }
            }
        }
    }

    private void updateEngineChip(){
        if(engineChip!=null) engineChip.setText(searchEngine);
    }

    private void showPrivacyIntro(){
        String[] levels={"Private — balanced compatibility","Protected — stronger isolation","Maximum — session-first privacy"};
        int checked=privacy.equals("private")?0:privacy.equals("maximum")?2:1;
        new AlertDialog.Builder(this).setTitle("Default privacy")
            .setSingleChoiceItems(levels,checked,(d,which)->{
                privacy=which==0?"private":which==2?"maximum":"protected";
                prefs.edit().putString("privacy",privacy).apply();
                applyPrivacy(); updateShield(); d.dismiss();
            }).show();
    }

    private void updateShield(){
        String label=privacy.equals("maximum")?"Maximum":privacy.equals("private")?"Private":"Protected";
        shield.setText("●  "+label);
        shield.setTextColor(privacy.equals("private")?MUTED:OK);
    }

    private void applyPrivacy(){
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.getSettings().setCacheMode(privacy.equals("maximum")?WebSettings.LOAD_NO_CACHE:WebSettings.LOAD_DEFAULT);
        if(privacy.equals("maximum")) web.clearCache(true);
    }

    private String searchUrl(String q){
        String e=Uri.encode(q);
        if("Google".equals(searchEngine)) return "https://www.google.com/search?q="+e;
        if("Bing".equals(searchEngine)) return "https://www.bing.com/search?q="+e;
        if("Brave Search".equals(searchEngine)) return "https://search.brave.com/search?q="+e;
        return "https://duckduckgo.com/?q="+e;
    }

    private void navigateInput(String raw){
        String v=raw.trim();
        if(v.isEmpty()) return;
        String url;
        if(v.matches("(?i)^https?://.*")) url=v;
        else if(v.matches("^[^\\s]+\\.[a-zA-Z]{2,}(/.*)?$")) url="https://"+v;
        else url=searchUrl(v);
        loadUrl(cleanTracking(url));
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(address.getWindowToken(),0);
        address.clearFocus();
    }

    private String cleanTracking(String raw){
        try{
            Uri u=Uri.parse(raw);
            Uri.Builder b=u.buildUpon().clearQuery();
            Set<String> bad=new HashSet<>(Arrays.asList(
                "utm_source","utm_medium","utm_campaign","utm_term","utm_content","gclid","fbclid","mc_cid","mc_eid"
            ));
            for(String k:u.getQueryParameterNames()){
                if(!bad.contains(k.toLowerCase())){
                    for(String val:u.getQueryParameters(k)) b.appendQueryParameter(k,val);
                }
            }
            return b.build().toString();
        }catch(Exception e){ return raw; }
    }

    private void loadUrl(String url){
        if(!url.startsWith("https://")){
            Toast.makeText(this,"Prowser requires secure HTTPS pages",Toast.LENGTH_SHORT).show();
            return;
        }
        if(activeTab<0) newTab(false);
        Tab t=tabs.get(activeTab);
        t.url=url; t.title=host(url);
        applyPrivacy();
        home.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        address.setText(url);
        web.loadUrl(url);
        updateTabsLabel();
    }

    private String host(String u){
        try{
            String h=Uri.parse(u).getHost();
            return h==null?"Page":h.replace("www.","");
        }catch(Exception e){ return "Page"; }
    }

    private void showHome(){
        if(web!=null){
            web.stopLoading();
            web.setVisibility(View.GONE);
        }
        home.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        address.setText("");
        address.setHint("Search or enter address");
    }

    private void newTab(boolean notify){
        tabs.add(new Tab(""));
        activeTab=tabs.size()-1;
        showHome();
        updateTabsLabel();
        if(notify) Toast.makeText(this,"New private tab",Toast.LENGTH_SHORT).show();
    }

    private void updateTabsLabel(){
        if(tabCount!=null) tabCount.setText("▢\nTabs "+tabs.size());
    }

    private void showTabsDialog(){
        String[] names=new String[tabs.size()+1];
        for(int i=0;i<tabs.size();i++){
            Tab t=tabs.get(i);
            String label=t.url.isEmpty()?"New private tab":t.title;
            names[i]=(i==activeTab?"✓  ":"     ")+label;
        }
        names[tabs.size()]="+  New tab";

        new AlertDialog.Builder(this)
            .setTitle("Open tabs")
            .setItems(names,(d,which)->{
                if(which==tabs.size()){
                    newTab(true);
                    return;
                }
                activeTab=which;
                Tab t=tabs.get(activeTab);
                if(t.url.isEmpty()) showHome();
                else loadUrl(t.url);
                updateTabsLabel();
            })
            .setNeutralButton("New tab",(d,w)->newTab(true))
            .setNegativeButton("Close current",(d,w)->closeCurrentTab())
            .show();
    }

    private void closeCurrentTab(){
        if(tabs.isEmpty()) return;
        tabs.remove(activeTab);
        if(tabs.isEmpty()) tabs.add(new Tab(""));
        activeTab=Math.max(0,Math.min(activeTab,tabs.size()-1));
        Tab t=tabs.get(activeTab);
        if(t.url.isEmpty()) showHome(); else loadUrl(t.url);
        updateTabsLabel();
    }

    private void showPrivacyMenu(){
        String[] items={"Private","Protected","Maximum Privacy","Clear current session"};
        int checked=privacy.equals("private")?0:privacy.equals("maximum")?2:1;
        new AlertDialog.Builder(this)
            .setTitle("Privacy")
            .setSingleChoiceItems(items,checked,(d,which)->{
                if(which==3){ clearSession(); d.dismiss(); return; }
                privacy=which==0?"private":which==2?"maximum":"protected";
                prefs.edit().putString("privacy",privacy).apply();
                applyPrivacy(); updateShield(); d.dismiss();
            })
            .setNeutralButton(clearOnExit?"Clear on exit: ON":"Clear on exit: OFF",(d,w)->{
                clearOnExit=!clearOnExit;
                prefs.edit().putBoolean("clearOnExit",clearOnExit).apply();
                Toast.makeText(this,clearOnExit?"Clear on exit enabled":"Clear on exit disabled",Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showMainMenu(View anchor){
        PopupMenu m=new PopupMenu(this,anchor);
        m.getMenu().add("New tab");
        m.getMenu().add("Search engine");
        m.getMenu().add("Privacy");
        m.getMenu().add("Clear session");
        m.getMenu().add("Open in another browser");
        if(customView!=null && Build.VERSION.SDK_INT>=26) m.getMenu().add("Picture in picture");
        m.setOnMenuItemClickListener(item->{
            String x=item.getTitle().toString();
            if(x.equals("New tab")) newTab(true);
            else if(x.equals("Search engine")) showSearchEnginePicker();
            else if(x.equals("Privacy")) showPrivacyMenu();
            else if(x.equals("Clear session")) clearSession();
            else if(x.equals("Open in another browser")) openExternal();
            else if(x.equals("Picture in picture")) enterPip();
            return true;
        });
        m.show();
    }

    private void clearSession(){
        web.stopLoading();
        web.clearHistory();
        web.clearCache(true);
        web.clearFormData();
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        tabs.clear();
        tabs.add(new Tab(""));
        activeTab=0;
        showHome();
        updateTabsLabel();
        Toast.makeText(this,"Private session cleared",Toast.LENGTH_SHORT).show();
    }

    private void openExternal(){
        if(activeTab<0 || tabs.get(activeTab).url.isEmpty()) return;
        try{
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(tabs.get(activeTab).url)));
        }catch(Exception ignored){}
    }

    private void confirmDownload(String url,String ua,String cd,String mime){
        new AlertDialog.Builder(this)
            .setTitle("Download file?")
            .setMessage("Prowser will use Android's download manager.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Download",(d,w)->{
                try{
                    DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mime);
                    r.addRequestHeader("User-Agent",ua);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url,cd,mime)
                    );
                    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                }catch(Exception e){
                    Toast.makeText(this,"Download unavailable",Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    private void enterPip(){
        if(Build.VERSION.SDK_INT>=26){
            PictureInPictureParams p=new PictureInPictureParams.Builder()
                .setAspectRatio(new android.util.Rational(16,9))
                .build();
            enterPictureInPictureMode(p);
        }
    }

    private class ProwserClient extends WebViewClient {
        @Override public void onPageStarted(WebView v,String url,Bitmap icon){
            progress.setProgress(8);
            address.setText(url);
            if(activeTab>=0){
                tabs.get(activeTab).url=url;
                tabs.get(activeTab).title=host(url);
            }
        }

        @Override public void onPageFinished(WebView v,String url){
            progress.setProgress(0);
            String t=v.getTitle();
            if(activeTab>=0){
                tabs.get(activeTab).url=url;
                tabs.get(activeTab).title=(t==null||t.isEmpty())?host(url):t;
            }
            address.setText(url);
            updateTabsLabel();
        }

        @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
            String u=r.getUrl().toString();
            if(u.startsWith("https://")){
                String clean=cleanTracking(u);
                if(!clean.equals(u)){
                    v.loadUrl(clean);
                    return true;
                }
                return false;
            }
            if(u.startsWith("http://")){
                Toast.makeText(MainActivity.this,"Blocked insecure HTTP page",Toast.LENGTH_SHORT).show();
                return true;
            }
            try{
                Intent external=new Intent(Intent.ACTION_VIEW,Uri.parse(u));
                startActivity(external);
            }catch(Exception e){
                Toast.makeText(MainActivity.this,"External link blocked",Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override public void onSafeBrowsingHit(WebView view,WebResourceRequest req,int threatType,SafeBrowsingResponse callback){
            callback.backToSafety(true);
            Toast.makeText(MainActivity.this,"Unsafe page blocked",Toast.LENGTH_LONG).show();
        }
    }

    private class ProwserChrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView v,int p){
            progress.setProgress(p>=100?0:p);
        }

        @Override public void onPermissionRequest(PermissionRequest req){
            runOnUiThread(()->new AlertDialog.Builder(MainActivity.this)
                .setTitle("Website permission")
                .setMessage(host(web.getUrl())+" is requesting camera or microphone access.")
                .setNegativeButton("Block",(d,w)->req.deny())
                .setPositiveButton("Allow once",(d,w)->req.grant(req.getResources()))
                .show());
        }

        @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){
            cb.invoke(origin,false,false);
        }

        @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message resultMsg){
            Toast.makeText(MainActivity.this,"Pop-up blocked",Toast.LENGTH_SHORT).show();
            return false;
        }

        @Override public void onShowCustomView(View view,CustomViewCallback cb){
            if(customView!=null){
                cb.onCustomViewHidden();
                return;
            }
            customView=view;
            customCallback=cb;
            customContainer=new FrameLayout(MainActivity.this);
            customContainer.setBackgroundColor(Color.BLACK);
            customContainer.addView(view,new FrameLayout.LayoutParams(-1,-1));
            addContentView(customContainer,new ViewGroup.LayoutParams(-1,-1));
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            installVideoGestures(customContainer);
        }

        @Override public void onHideCustomView(){
            hideCustomVideo();
        }
    }

    private void installVideoGestures(View v){
        final float[] startY={0};
        final int[] startVol={0};
        final float[] startBright={0};

        v.setOnTouchListener((view,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                startY[0]=e.getY();
                android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                startVol[0]=am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                startBright[0]=getWindow().getAttributes().screenBrightness<0
                    ?0.5f:getWindow().getAttributes().screenBrightness;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dy=(startY[0]-e.getY())/Math.max(1f,v.getHeight());
                if(e.getX()<v.getWidth()/2f){
                    WindowManager.LayoutParams lp=getWindow().getAttributes();
                    lp.screenBrightness=Math.max(.05f,Math.min(1f,startBright[0]+dy));
                    getWindow().setAttributes(lp);
                }else{
                    android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                    int max=am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        Math.max(0,Math.min(max,startVol[0]+Math.round(dy*max))),0
                    );
                }
                return true;
            }
            return e.getAction()==MotionEvent.ACTION_UP;
        });
    }

    private void hideCustomVideo(){
        if(customView==null) return;
        ((ViewGroup)customView.getParent()).removeView(customView);
        if(customContainer!=null && customContainer.getParent()!=null){
            ((ViewGroup)customContainer.getParent()).removeView(customContainer);
        }
        if(customCallback!=null) customCallback.onCustomViewHidden();
        customView=null;
        customContainer=null;
        customCallback=null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        root.requestApplyInsets();
    }

    @Override public void onBackPressed(){
        if(customView!=null){
            hideCustomVideo();
            return;
        }
        if(web.getVisibility()==View.VISIBLE && web.canGoBack()){
            web.goBack();
            return;
        }
        if(web.getVisibility()==View.VISIBLE){
            showHome();
            return;
        }
        long n=System.currentTimeMillis();
        if(n-lastBack<1500){
            super.onBackPressed();
        }else{
            lastBack=n;
            Toast.makeText(this,"Back again to exit",Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy(){
        if(clearOnExit) clearSession();
        if(web!=null){
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
