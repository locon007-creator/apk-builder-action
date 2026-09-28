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
    private final int SURFACE2 = Color.rgb(29,31,42);
    private final int TEXT = Color.rgb(246,247,251);
    private final int MUTED = Color.rgb(145,151,168);
    private final int ACCENT = Color.rgb(150,126,255);
    private final int OK = Color.rgb(102,221,176);
    private final int DANGER = Color.rgb(255,105,120);

    private LinearLayout root, topBar, bottomBar;
    private FrameLayout content;
    private EditText address;
    private TextView shield, engineChip, tabCount;
    private ProgressBar progress;
    private WebView web;
    private View home;
    private LinearLayout tabsPage;
    private ScrollView tabsScroll;
    private LinearLayout tabsList;
    private LinearLayout bookmarksPage;
    private LinearLayout bookmarksList;
    private View customView;
    private FrameLayout customContainer;
    private LinearLayout fullscreenControls;
    private WebChromeClient.CustomViewCallback customCallback;
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int activeTab = -1;
    private SharedPreferences prefs;
    private String privacy = "protected";
    private String searchEngine = "DuckDuckGo";
    private boolean clearOnExit = true;
    private long lastBack = 0;
    private int bottomInset = 0;
    private boolean videoMode = false;
    private boolean videoDetected = false;
    private final Handler videoHandler = new Handler(Looper.getMainLooper());

    static class Tab {
        String url = "";
        String title = "New tab";
        long lastUsed = System.currentTimeMillis();
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
        startVideoDetection();
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
        TextView v=text(symbol+"\n"+label,12,TEXT);
        v.setGravity(Gravity.CENTER);
        v.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        v.setLineSpacing(dp(1),1.0f);
        v.setPadding(dp(6),dp(6),dp(6),dp(5));
        v.setBackground(strokeBg(SURFACE,18,Color.rgb(50,53,68)));
        v.setClickable(true);
        v.setFocusable(true);
        v.setMinHeight(dp(62));
        v.setElevation(dp(2));
        v.setOnTouchListener((view,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                view.setAlpha(.72f);
                view.setScaleX(.96f);
                view.setScaleY(.96f);
            }else if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL){
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });
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
            }else{
                bottomInset=insets.getSystemWindowInsetBottom();
                v.setPadding(0,insets.getSystemWindowInsetTop(),0,0);
            }
            updateBottomBarInsets();
            return insets;
        });

        buildTopBar();

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));

        content=new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        buildHome();
        buildWeb();
        buildTabsPage();
        buildBookmarksPage();

        buildBrowserBottomBar();
        setContentView(root);
        root.requestApplyInsets();
        updateShield();
        updateEngineChip();
    }

    private void buildTopBar(){
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
        start.setOnClickListener(v->showQuickSearch());

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

    private void buildTabsPage(){
        tabsPage=new LinearLayout(this);
        tabsPage.setOrientation(LinearLayout.VERTICAL);
        tabsPage.setBackgroundColor(BG);
        tabsPage.setPadding(dp(14),dp(8),dp(14),dp(12));
        tabsPage.setVisibility(View.GONE);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back=text("‹",32,TEXT);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setOnClickListener(v->hideTabsPage());

        TextView title=text("Tabs",24,TEXT);
        title.setTypeface(null,1);

        TextView newTab=text("+ New",14,ACCENT);
        newTab.setGravity(Gravity.CENTER);
        newTab.setPadding(dp(10),0,dp(10),0);
        newTab.setClickable(true);
        newTab.setOnClickListener(v->{ newTab(true); hideTabsPage(); });

        TextView closeAll=text("Close all",13,DANGER);
        closeAll.setGravity(Gravity.CENTER);
        closeAll.setPadding(dp(10),0,0,0);
        closeAll.setClickable(true);
        closeAll.setOnClickListener(v->closeAllTabs());

        header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(52)));
        header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        header.addView(newTab,new LinearLayout.LayoutParams(-2,dp(44)));
        header.addView(closeAll,new LinearLayout.LayoutParams(-2,dp(44)));
        tabsPage.addView(header,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView sub=text("Tap a tab to switch. Close anything you no longer need.",12,MUTED);
        tabsPage.addView(sub,new LinearLayout.LayoutParams(-1,dp(38)));

        tabsList=new LinearLayout(this);
        tabsList.setOrientation(LinearLayout.VERTICAL);

        tabsScroll=new ScrollView(this);
        tabsScroll.addView(tabsList,new ScrollView.LayoutParams(-1,-2));
        tabsPage.addView(tabsScroll,new LinearLayout.LayoutParams(-1,0,1));

        content.addView(tabsPage,new FrameLayout.LayoutParams(-1,-1));
    }

    private void refreshTabsPage(){
        tabsList.removeAllViews();
        for(int i=0;i<tabs.size();i++){
            final int index=i;
            Tab t=tabs.get(i);

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16),dp(14),dp(14),dp(12));
            card.setBackground(strokeBg(index==activeTab?SURFACE2:SURFACE,18,index==activeTab?ACCENT:Color.rgb(45,48,61)));
            card.setClickable(true);
            card.setOnClickListener(v->{ switchToTab(index); hideTabsPage(); });

            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView title=text((index==activeTab?"●  ":"")+((t.title==null||t.title.isEmpty())?"New private tab":t.title),15,TEXT);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);

            TextView close=text("×",28,MUTED);
            close.setGravity(Gravity.CENTER);
            close.setClickable(true);
            close.setOnClickListener(v->{ closeTab(index); refreshTabsPage(); });

            row.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
            row.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
            card.addView(row,new LinearLayout.LayoutParams(-1,dp(44)));

            String line=t.url.isEmpty()?"Ready for a new search":host(t.url)+"\n"+t.url;
            TextView url=text(line,11,MUTED);
            url.setMaxLines(2);
            url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(url,new LinearLayout.LayoutParams(-1,dp(42)));

            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(98));
            cp.bottomMargin=dp(10);
            tabsList.addView(card,cp);
        }
    }

    private void showTabsPage(){
        if(videoMode) exitVideoMode();
        refreshTabsPage();
        home.setVisibility(View.GONE);
        web.setVisibility(View.GONE);
        tabsPage.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    private void hideTabsPage(){
        tabsPage.setVisibility(View.GONE);
        topBar.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        if(activeTab>=0 && !tabs.get(activeTab).url.isEmpty()){
            web.setVisibility(View.VISIBLE);
            home.setVisibility(View.GONE);
        }else{
            showHome();
        }
        root.requestApplyInsets();
    }

    private void buildBookmarksPage(){
        bookmarksPage=new LinearLayout(this);
        bookmarksPage.setOrientation(LinearLayout.VERTICAL);
        bookmarksPage.setBackgroundColor(BG);
        bookmarksPage.setPadding(dp(14),dp(8),dp(14),dp(12));
        bookmarksPage.setVisibility(View.GONE);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back=text("‹",32,TEXT);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setOnClickListener(v->hideBookmarksPage());

        TextView title=text("Bookmarks",24,TEXT);
        title.setTypeface(null,1);

        TextView add=text("+ Save",14,ACCENT);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(10),0,dp(10),0);
        add.setClickable(true);
        add.setOnClickListener(v->saveCurrentBookmark());

        header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(52)));
        header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        header.addView(add,new LinearLayout.LayoutParams(-2,dp(44)));
        bookmarksPage.addView(header,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView sub=text("Saved websites stay here for quick access.",12,MUTED);
        bookmarksPage.addView(sub,new LinearLayout.LayoutParams(-1,dp(38)));

        bookmarksList=new LinearLayout(this);
        bookmarksList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(bookmarksList,new ScrollView.LayoutParams(-1,-2));
        bookmarksPage.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        content.addView(bookmarksPage,new FrameLayout.LayoutParams(-1,-1));
    }

    private Set<String> readBookmarks(){
        return new LinkedHashSet<>(prefs.getStringSet("bookmarks",new LinkedHashSet<>()));
    }

    private void writeBookmarks(Set<String> items){
        prefs.edit().putStringSet("bookmarks",new LinkedHashSet<>(items)).apply();
    }

    private void saveCurrentBookmark(){
        if(activeTab<0 || activeTab>=tabs.size() || tabs.get(activeTab).url.isEmpty()){
            Toast.makeText(this,"Open a website first",Toast.LENGTH_SHORT).show();
            return;
        }
        Tab t=tabs.get(activeTab);
        Set<String> items=readBookmarks();
        items.add((t.title==null||t.title.isEmpty()?host(t.url):t.title)+"\t"+t.url);
        writeBookmarks(items);
        Toast.makeText(this,"Bookmark saved",Toast.LENGTH_SHORT).show();
        if(bookmarksPage.getVisibility()==View.VISIBLE) refreshBookmarksPage();
    }

    private void refreshBookmarksPage(){
        bookmarksList.removeAllViews();
        Set<String> items=readBookmarks();
        if(items.isEmpty()){
            TextView empty=text("No bookmarks yet.",14,MUTED);
            empty.setGravity(Gravity.CENTER);
            bookmarksList.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
            return;
        }
        for(String item:items){
            String[] parts=item.split("\\t",2);
            String title=parts.length>0?parts[0]:"Saved page";
            String url=parts.length>1?parts[1]:"";

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16),dp(12),dp(12),dp(10));
            card.setBackground(strokeBg(SURFACE,18,Color.rgb(45,48,61)));

            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name=text(title,15,TEXT);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setClickable(true);
            name.setOnClickListener(v->{ loadUrl(url); hideBookmarksPage(); });

            TextView close=text("×",28,MUTED);
            close.setGravity(Gravity.CENTER);
            close.setClickable(true);
            close.setOnClickListener(v->{
                Set<String> set=readBookmarks();
                set.remove(item);
                writeBookmarks(set);
                refreshBookmarksPage();
            });

            row.addView(name,new LinearLayout.LayoutParams(0,dp(42),1));
            row.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
            card.addView(row,new LinearLayout.LayoutParams(-1,dp(44)));

            TextView domain=text(host(url),11,MUTED);
            card.addView(domain,new LinearLayout.LayoutParams(-1,dp(30)));

            card.setClickable(true);
            card.setOnClickListener(v->{ loadUrl(url); hideBookmarksPage(); });

            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(86));
            cp.bottomMargin=dp(10);
            bookmarksList.addView(card,cp);
        }
    }

    private void showBookmarksPage(){
        if(videoMode) exitVideoMode();
        refreshBookmarksPage();
        home.setVisibility(View.GONE);
        web.setVisibility(View.GONE);
        tabsPage.setVisibility(View.GONE);
        bookmarksPage.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    private void hideBookmarksPage(){
        bookmarksPage.setVisibility(View.GONE);
        topBar.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        if(activeTab>=0 && !tabs.get(activeTab).url.isEmpty()){
            web.setVisibility(View.VISIBLE);
            home.setVisibility(View.GONE);
        }else{
            showHome();
        }
        root.requestApplyInsets();
    }

    private void requestProwserFullscreen(){
        if(web==null) return;
        web.evaluateJavascript(
            "(function(){try{const v=document.querySelector('video');if(!v)return 'none';" +
            "if(v.requestFullscreen){v.requestFullscreen();return 'requested';}" +
            "if(v.webkitRequestFullscreen){v.webkitRequestFullscreen();return 'requested';}" +
            "if(v.webkitEnterFullscreen){v.webkitEnterFullscreen();return 'requested';}" +
            "return 'native';}catch(e){return 'native';}})();",
            result->{
                String r=result==null?"":result.replace("\"","");
                videoHandler.postDelayed(()->{
                    if(customView==null && !isInPictureInPictureMode()) enterNativeVideoFullscreen();
                },350);
            }
        );
    }

    private void enterNativeVideoFullscreen(){
        if(web==null || customView!=null) return;

        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        WebView videoWeb=new WebView(this);
        WebSettings s=videoWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(videoWeb,false);

        String current=web.getUrl();
        if(current!=null) videoWeb.loadUrl(current);

        videoWeb.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){
                v.evaluateJavascript(
                    "(function(){const v=document.querySelector('video');if(v){v.play().catch(()=>{});if(v.requestFullscreen)v.requestFullscreen();}})();",
                    null
                );
            }
        });
        videoWeb.setWebChromeClient(new ProwserChrome());

        frame.addView(videoWeb,new FrameLayout.LayoutParams(-1,-1));
        customView=frame;
        customContainer=new FrameLayout(this);
        customContainer.setBackgroundColor(Color.BLACK);
        customContainer.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        addFullscreenControls(customContainer);
        addContentView(customContainer,new ViewGroup.LayoutParams(-1,-1));

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void buildBrowserBottomBar(){
        if(bottomBar!=null) root.removeView(bottomBar);
        bottomBar=new LinearLayout(this);
        bottomBar.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        bottomBar.setPadding(dp(8),dp(7),dp(8),dp(8)+bottomInset);
        bottomBar.setBackgroundColor(BG);

        TextView back=navButton("←","Back");
        TextView fwd=navButton("→","Forward");
        TextView search=navButton("⌕","Search");
        TextView tabsBtn=navButton("▣","Tabs");
        TextView menu=navButton("•••","Menu");

        back.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE && web.canGoBack()) web.goBack(); else showHome(); });
        fwd.setOnClickListener(v->{ if(web.getVisibility()==View.VISIBLE && web.canGoForward()) web.goForward(); });
        search.setBackground(strokeBg(SURFACE2,18,ACCENT));
        search.setTextColor(Color.WHITE);
        search.setOnClickListener(v->showQuickSearch());
        tabsBtn.setOnClickListener(v->showTabsPage());
        menu.setOnClickListener(v->showMainMenu(menu));

        for(TextView x:new TextView[]{back,fwd,search,tabsBtn,menu}){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(64),1);
            p.leftMargin=dp(3);
            p.rightMargin=dp(3);
            bottomBar.addView(x,p);
        }
        tabCount=tabsBtn;
        root.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(80)+bottomInset));
        updateBottomBarInsets();
        updateTabsLabel();
    }

    private void buildVideoBottomBar(){
        if(bottomBar!=null) root.removeView(bottomBar);
        bottomBar=new LinearLayout(this);
        bottomBar.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        bottomBar.setBackgroundColor(BG);

        TextView rewind=navButton("−15","Back");
        TextView play=navButton("▶","Play/Pause");
        TextView forward=navButton("+15","Forward");
        TextView fullscreen=navButton("⛶","Full");
        TextView more=navButton("⋯","Browser");

        rewind.setOnClickListener(v->videoCommand("const v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime-15);"));
        play.setOnClickListener(v->videoCommand("const v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}"));
        forward.setOnClickListener(v->videoCommand("const v=document.querySelector('video');if(v)v.currentTime=Math.min(v.duration||1e9,v.currentTime+15);"));
        fullscreen.setOnClickListener(v->requestProwserFullscreen());
        more.setOnClickListener(v->{
            PopupMenu m=new PopupMenu(this,more);
            m.getMenu().add("Browser controls");
            m.getMenu().add("Playback 0.75×");
            m.getMenu().add("Playback 1×");
            m.getMenu().add("Playback 1.25×");
            m.getMenu().add("Playback 1.5×");
            m.getMenu().add("Playback 2×");
            if(Build.VERSION.SDK_INT>=26) m.getMenu().add("Picture in picture");
            m.setOnMenuItemClickListener(item->{
                String x=item.getTitle().toString();
                if(x.equals("Browser controls")) exitVideoMode();
                else if(x.startsWith("Playback ")){
                    String n=x.replace("Playback ","").replace("×","");
                    videoCommand("const v=document.querySelector('video');if(v)v.playbackRate="+n+";");
                }else if(x.equals("Picture in picture")) enterPip();
                return true;
            });
            m.show();
        });

        for(TextView x:new TextView[]{rewind,play,forward,fullscreen,more}){
            bottomBar.addView(x,new LinearLayout.LayoutParams(0,dp(60),1));
        }
        root.addView(bottomBar,new LinearLayout.LayoutParams(-1,dp(80)+bottomInset));
        updateBottomBarInsets();
    }

    private void updateBottomBarInsets(){
        if(bottomBar==null) return;
        bottomBar.setPadding(dp(8),dp(4),dp(8),dp(8)+bottomInset);
        ViewGroup.LayoutParams p=bottomBar.getLayoutParams();
        if(p!=null){
            p.height=dp(80)+bottomInset;
            bottomBar.setLayoutParams(p);
        }
    }

    private void showQuickSearch(){
        final Dialog d=new Dialog(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(14),dp(18),dp(18));
        box.setBackground(bg(SURFACE,24));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Quick Search",20,TEXT); title.setTypeface(null,1);
        TextView engine=text(searchEngine,12,ACCENT); engine.setGravity(Gravity.CENTER);
        engine.setPadding(dp(10),0,dp(10),0); engine.setBackground(strokeBg(SURFACE2,12,Color.rgb(55,58,73)));
        engine.setClickable(true);
        engine.setOnClickListener(v->{ d.dismiss(); showSearchEnginePicker(); });
        header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        header.addView(engine,new LinearLayout.LayoutParams(-2,dp(34)));
        box.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));

        EditText q=new EditText(this);
        q.setSingleLine(true); q.setTextColor(TEXT); q.setHintTextColor(MUTED);
        q.setHint("What do you want to search?");
        q.setTextSize(16); q.setPadding(dp(14),0,dp(14),0);
        q.setBackground(strokeBg(SURFACE2,16,Color.rgb(58,61,77)));
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_SEARCH || id==EditorInfo.IME_ACTION_GO || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                String query=q.getText().toString().trim();
                if(!query.isEmpty()){ d.dismiss(); loadUrl(searchUrl(query)); }
                return true;
            }
            return false;
        });
        box.addView(q,new LinearLayout.LayoutParams(-1,dp(56)));

        TextView hint=text("Searches open in your current tab. No new tab is created.",11,MUTED);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(42)); hp.topMargin=dp(4);
        box.addView(hint,hp);

        d.setContentView(box);
        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(-1,-2);
            w.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.width=WindowManager.LayoutParams.MATCH_PARENT;
            lp.dimAmount=.45f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if(Build.VERSION.SDK_INT>=30){
                w.setDecorFitsSystemWindows(true);
            }
        }
        d.setOnShowListener(x->{
            Window ww=d.getWindow();
            if(ww!=null) ww.setLayout(-1,-2);
            q.requestFocus();
            q.postDelayed(()->((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(q,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT),150);
        });
        d.show();
    }

    private void startVideoDetection(){
        videoHandler.postDelayed(new Runnable(){
            @Override public void run(){
                detectVideo();
                videoHandler.postDelayed(this,1500);
            }
        },1500);
    }

    private void detectVideo(){
        if(web==null || web.getVisibility()!=View.VISIBLE || tabsPage.getVisibility()==View.VISIBLE) return;
        web.evaluateJavascript(
            "(function(){try{const vs=[...document.querySelectorAll('video')];const v=vs.find(x=>!x.paused&&x.readyState>=2)||vs.find(x=>x.readyState>=2);return v?((!v.paused)?'playing':'ready'):'none';}catch(e){return 'none';}})();",
            value->{
                String state=value==null?"":value.replace("\"","");
                videoDetected=state.equals("playing")||state.equals("ready");
                if(state.equals("playing") && !videoMode) enterVideoMode();
                if(state.equals("none") && videoMode) exitVideoMode();
            }
        );
    }

    private void enterVideoMode(){
        if(videoMode || !videoDetected) return;
        videoMode=true;
        buildVideoBottomBar();
    }

    private void exitVideoMode(){
        if(!videoMode) return;
        videoMode=false;
        buildBrowserBottomBar();
    }

    private void videoCommand(String js){
        if(web!=null) web.evaluateJavascript("(function(){try{"+js+"return true;}catch(e){return false;}})();",null);
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

    private void updateShield(){
        String label=privacy.equals("maximum")?"Maximum":privacy.equals("private")?"Private":"Protected";
        shield.setText("●  "+label);
        shield.setTextColor(privacy.equals("private")?MUTED:OK);
    }

    private boolean isGoogleUrl(String url){
        try{
            String h=Uri.parse(url).getHost();
            if(h==null) return false;
            h=h.toLowerCase(Locale.US);
            return h.equals("google.com") || h.endsWith(".google.com");
        }catch(Exception e){
            return false;
        }
    }

    private void applySiteCompatibility(String url){
        boolean google=isGoogleUrl(url);
        CookieManager cm=CookieManager.getInstance();

        // Google compatibility: preserve a stable first-party session and allow
        // the cookie flow its search/verification pages expect while on Google.
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web,google);

        // Avoid making every Google navigation look like a fresh browser session.
        if(google){
            web.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        }else{
            web.getSettings().setCacheMode(
                privacy.equals("maximum") ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT
            );
        }
    }

    private void applyPrivacy(){
        String current=web!=null?web.getUrl():null;
        applySiteCompatibility(current);
        if(privacy.equals("maximum") && !isGoogleUrl(current)) web.clearCache(true);
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
        t.url=url; t.title=host(url); t.lastUsed=System.currentTimeMillis();
        applySiteCompatibility(url);
        tabsPage.setVisibility(View.GONE);
        if(bookmarksPage!=null) bookmarksPage.setVisibility(View.GONE);
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
        if(videoMode) exitVideoMode();
        if(web!=null){
            web.stopLoading();
            web.setVisibility(View.GONE);
        }
        tabsPage.setVisibility(View.GONE);
        if(bookmarksPage!=null) bookmarksPage.setVisibility(View.GONE);
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

    private void switchToTab(int index){
        if(index<0 || index>=tabs.size()) return;
        activeTab=index;
        Tab t=tabs.get(activeTab);
        t.lastUsed=System.currentTimeMillis();
        if(t.url.isEmpty()) showHome();
        else loadUrl(t.url);
        updateTabsLabel();
    }

    private void closeTab(int index){
        if(index<0 || index>=tabs.size()) return;
        tabs.remove(index);
        if(tabs.isEmpty()) tabs.add(new Tab(""));
        if(activeTab>index) activeTab--;
        else if(activeTab==index) activeTab=Math.min(index,tabs.size()-1);
        updateTabsLabel();
    }

    private void closeAllTabs(){
        tabs.clear();
        tabs.add(new Tab(""));
        activeTab=0;
        hideTabsPage();
        showHome();
        updateTabsLabel();
        Toast.makeText(this,"All tabs closed",Toast.LENGTH_SHORT).show();
    }

    private void updateTabsLabel(){
        if(tabCount!=null) tabCount.setText("▢\nTabs "+tabs.size());
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
        m.getMenu().add("Home");
        m.getMenu().add("Bookmarks");
        m.getMenu().add("Save bookmark");
        m.getMenu().add("Tabs");
        m.getMenu().add("New tab");
        m.getMenu().add("Search engine");
        if(videoDetected) m.getMenu().add(videoMode?"Browser controls":"Video controls");
        m.getMenu().add("Privacy");
        m.getMenu().add("Clear session");
        m.getMenu().add("Open in another browser");
        m.setOnMenuItemClickListener(item->{
            String x=item.getTitle().toString();
            if(x.equals("Home")) showHome();
            else if(x.equals("Bookmarks")) showBookmarksPage();
            else if(x.equals("Save bookmark")) saveCurrentBookmark();
            else if(x.equals("Tabs")) showTabsPage();
            else if(x.equals("New tab")) newTab(true);
            else if(x.equals("Search engine")) showSearchEnginePicker();
            else if(x.equals("Video controls")) enterVideoMode();
            else if(x.equals("Browser controls")) exitVideoMode();
            else if(x.equals("Privacy")) showPrivacyMenu();
            else if(x.equals("Clear session")) clearSession();
            else if(x.equals("Open in another browser")) openExternal();
            return true;
        });
        m.show();
    }

    private void clearSession(){
        if(videoMode) exitVideoMode();
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
            applySiteCompatibility(url);
            progress.setProgress(8);
            address.setText(url);
            videoDetected=false;
            if(videoMode) exitVideoMode();
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
                tabs.get(activeTab).lastUsed=System.currentTimeMillis();
            }
            address.setText(url);
            if(isGoogleUrl(url)) CookieManager.getInstance().flush();
            updateTabsLabel();
            detectVideo();
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
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));
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
            if(customView!=null){ cb.onCustomViewHidden(); return; }
            customView=view;
            customCallback=cb;
            customContainer=new FrameLayout(MainActivity.this);
            customContainer.setBackgroundColor(Color.BLACK);
            customContainer.addView(view,new FrameLayout.LayoutParams(-1,-1));
            addFullscreenControls(customContainer);
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

    private TextView fullscreenControlButton(String label,int size){
        TextView b=text(label,size,Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(strokeBg(Color.argb(190,20,22,30),16,Color.argb(120,255,255,255)));
        b.setClickable(true);
        b.setFocusable(true);
        b.setPadding(dp(8),dp(6),dp(8),dp(6));
        b.setElevation(dp(5));
        return b;
    }

    private void addFullscreenControls(FrameLayout parent){
        if(fullscreenControls!=null && fullscreenControls.getParent()!=null){
            ((ViewGroup)fullscreenControls.getParent()).removeView(fullscreenControls);
        }

        fullscreenControls=new LinearLayout(this);
        fullscreenControls.setOrientation(LinearLayout.VERTICAL);
        fullscreenControls.setGravity(Gravity.CENTER_HORIZONTAL);
        fullscreenControls.setPadding(dp(8),dp(8),dp(8),dp(8));
        fullscreenControls.setBackground(bg(Color.argb(105,8,9,13),20));
        fullscreenControls.setElevation(dp(12));

        LinearLayout seekRow=new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER);

        TextView back15=fullscreenControlButton("−15",14);
        TextView forward15=fullscreenControlButton("+15",14);
        back15.setOnClickListener(v->videoCommand("const v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime-15);"));
        forward15.setOnClickListener(v->videoCommand("const v=document.querySelector('video');if(v)v.currentTime=Math.min(v.duration||1e9,v.currentTime+15);"));

        LinearLayout.LayoutParams seekP=new LinearLayout.LayoutParams(dp(54),dp(42));
        seekP.leftMargin=dp(3);
        seekP.rightMargin=dp(3);
        seekRow.addView(back15,seekP);
        seekRow.addView(forward15,seekP);
        fullscreenControls.addView(seekRow,new LinearLayout.LayoutParams(-1,dp(44)));

        TextView play=fullscreenControlButton("▶  ❚❚",15);
        play.setOnClickListener(v->videoCommand("const v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}"));
        LinearLayout.LayoutParams playP=new LinearLayout.LayoutParams(dp(112),dp(46));
        playP.topMargin=dp(6);
        fullscreenControls.addView(play,playP);

        TextView exit=fullscreenControlButton("Exit Fullscreen",12);
        exit.setOnClickListener(v->hideCustomVideo());
        LinearLayout.LayoutParams exitP=new LinearLayout.LayoutParams(dp(112),dp(42));
        exitP.topMargin=dp(6);
        fullscreenControls.addView(exit,exitP);

        FrameLayout.LayoutParams overlayP=new FrameLayout.LayoutParams(dp(132),dp(156));
        overlayP.gravity=Gravity.TOP|Gravity.END;
        overlayP.topMargin=dp(18);
        overlayP.rightMargin=dp(14);
        parent.addView(fullscreenControls,overlayP);
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
        if(customView instanceof ViewGroup){
            ViewGroup vg=(ViewGroup)customView;
            for(int i=0;i<vg.getChildCount();i++){
                View child=vg.getChildAt(i);
                if(child instanceof WebView){ ((WebView)child).stopLoading(); ((WebView)child).destroy(); }
            }
        }
        if(customCallback!=null) customCallback.onCustomViewHidden();
        customView=null;
        customContainer=null;
        fullscreenControls=null;
        customCallback=null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        root.requestApplyInsets();
    }

    @Override public void onBackPressed(){
        if(customView!=null){
            hideCustomVideo();
            return;
        }
        if(bookmarksPage!=null && bookmarksPage.getVisibility()==View.VISIBLE){
            hideBookmarksPage();
            return;
        }
        if(tabsPage!=null && tabsPage.getVisibility()==View.VISIBLE){
            hideTabsPage();
            return;
        }
        if(videoMode){
            exitVideoMode();
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
        videoHandler.removeCallbacksAndMessages(null);
        if(clearOnExit) clearSession();
        if(web!=null){
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
