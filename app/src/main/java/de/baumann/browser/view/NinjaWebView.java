package de.baumann.browser.view;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.ContentValues.TAG;
import static android.content.Context.NOTIFICATION_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.activity.BrowserActivity;
import de.baumann.browser.browser.AlbumController;
import de.baumann.browser.browser.BrowserController;
import de.baumann.browser.browser.JavaScriptInterface;
import de.baumann.browser.browser.List_protected;
import de.baumann.browser.browser.List_standard;
import de.baumann.browser.browser.List_trusted;
import de.baumann.browser.browser.NinjaDownloadListener;
import de.baumann.browser.browser.NinjaWebChromeClient;
import de.baumann.browser.browser.NinjaWebViewClient;
import de.baumann.browser.database.FaviconHelper;
import de.baumann.browser.database.Record;
import de.baumann.browser.database.RecordAction;
import de.baumann.browser.unit.BrowserUnit;
import de.baumann.browser.unit.HelperUnit;

public class NinjaWebView extends WebView implements AlbumController {

    public boolean fingerPrintProtection;
    public boolean history;
    public boolean adBlock;
    public boolean saveData;
    public boolean camera;
    private OnScrollChangeListener onScrollChangeListener;
    private Context context;
    private boolean desktopMode;
    private boolean stopped;
    private AdapterTabs album;
    private AlbumController predecessor = null;
    private NinjaWebViewClient webViewClient;
    private NinjaWebChromeClient webChromeClient;
    private NinjaDownloadListener downloadListener;
    private static String profile;
    private List_trusted listTrusted;
    private List_standard listStandard;
    private List_protected listProtected;
    private Bitmap favicon;
    private SharedPreferences sp;
    private boolean foreground;
    private static BrowserController browserController = null;

    public NinjaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NinjaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private Activity activity;

    public NinjaWebView(Context context) {
        super(context);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        String profile = sp.getString("profile", "standard");
        this.activity = (Activity) context;
        this.context = context;
        this.foreground = false;
        this.desktopMode = false;
        this.fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        this.history = sp.getBoolean(profile + "_history", true);
        this.adBlock = sp.getBoolean(profile + "_adBlock", false);
        this.saveData = sp.getBoolean(profile + "_saveData", false);
        this.camera = sp.getBoolean(profile + "_camera", false);

        this.stopped = false;
        this.listTrusted = new List_trusted(this.context);
        this.listStandard = new List_standard(this.context);
        this.listProtected = new List_protected(this.context);
        this.album = new AdapterTabs(this.context, this, browserController);
        this.webViewClient = new NinjaWebViewClient(this);
        this.webChromeClient = new NinjaWebChromeClient(this);
        this.downloadListener = new NinjaDownloadListener(this.context, this);

        initWebView();
        initAlbum();
    }

    @Override
    public void onScrollChanged(int l, int t, int old_l, int old_t) {
        super.onScrollChanged(l, t, old_l, old_t);
        if (onScrollChangeListener != null) onScrollChangeListener.onScrollChange(t, old_t);
    }

    public void setOnScrollChangeListener(OnScrollChangeListener onScrollChangeListener) {
        this.onScrollChangeListener = onScrollChangeListener;
    }

    public boolean isForeground() {
        return foreground;
    }

    public static BrowserController getBrowserController() {
        return browserController;
    }

    public void setBrowserController(BrowserController browserController) {
        NinjaWebView.browserController = browserController;
        this.album.setBrowserController(browserController);
    }

    private synchronized void initWebView() {
        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public synchronized void initPreferences(String url) {

        sp = PreferenceManager.getDefaultSharedPreferences(context);
        profile = sp.getString("profile", "profileStandard");
        String profileOriginal = profile;
        WebSettings webSettings = getSettings();
        addJavascriptInterface(new JavaScriptInterface(context, this), "NinjaWebViewJS");

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) || sp.getString("sp_theme", "1").equals("3")) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                boolean allowed = sp.getBoolean("setAlgorithmicDarkeningAllowed", true);
                if (!allowed) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false);
                    sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", false).apply();
                } else {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true);
                    sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", true).apply();
                }
            }
        }

        String userAgent = getUserAgent(desktopMode);
        webSettings.setUserAgentString(userAgent);
        webSettings.setSafeBrowsingEnabled(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setTextZoom(Integer.parseInt(Objects.requireNonNull(sp.getString("sp_fontSize", "100"))));

        if (sp.getBoolean("sp_autofill", true)) {
            this.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        } else {
            this.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        if (listTrusted.isWhite(url)) profile = "profileTrusted";
        else if (listStandard.isWhite(url)) profile = "profileStandard";
        else if (listProtected.isWhite(url)) profile = "profileProtected";

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setMediaPlaybackRequiresUserGesture(sp.getBoolean(profile + "_saveData", true));
        webSettings.setBlockNetworkImage(!sp.getBoolean(profile + "_images", true));
        webSettings.setGeolocationEnabled(sp.getBoolean(profile + "_location", false));
        webSettings.setJavaScriptEnabled(sp.getBoolean(profile + "_javascript", true));
        webSettings.setJavaScriptCanOpenWindowsAutomatically(sp.getBoolean(profile + "_javascriptPopUp", false));
        webSettings.setDomStorageEnabled(sp.getBoolean(profile + "_dom", false));

        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        history = sp.getBoolean(profile + "_saveHistory", true);
        adBlock = sp.getBoolean(profile + "_adBlock", true);
        saveData = sp.getBoolean(profile + "_saveData", true);
        camera = sp.getBoolean(profile + "_camera", true);

        try {
            CookieManager manager = CookieManager.getInstance();
            if (sp.getBoolean(profile + "_cookies", false)) {
                manager.setAcceptCookie(true);
                manager.getCookie(url);
            } else manager.setAcceptCookie(false);
            if (sp.getBoolean(profile + "_cookiesThirdParty", false)) {
                manager.setAcceptThirdPartyCookies(this, true);
                manager.getCookie(url);
            } else manager.setAcceptThirdPartyCookies(this, true);
        } catch (Exception e) {
            Log.i(TAG, "Error loading cookies:" + e);
        }
        profile = profileOriginal;
    }

    public void setProfileIcon(FloatingActionButton omniBox_tab, String url) {
        String profile = sp.getString("profile", "profileStandard");
        assert url != null;
        switch (profile) {
            case "profileTrusted":
                omniBox_tab.setImageResource(R.drawable.icon_profile_trusted);
                break;
            case "profileStandard":
                omniBox_tab.setImageResource(R.drawable.icon_profile_standard);
                break;
            case "profileProtected":
                omniBox_tab.setImageResource(R.drawable.icon_profile_protected);
                break;
            default:
                omniBox_tab.setImageResource(R.drawable.icon_profile_changed);
                break;
        }

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorError, typedValue, true);
        int color = typedValue.data;

        if (listTrusted.isWhite(url)) {
            omniBox_tab.setImageResource(R.drawable.icon_profile_trusted);
            omniBox_tab.getDrawable().mutate().setTint(color);
        } else if (listStandard.isWhite(url)) {
            omniBox_tab.setImageResource(R.drawable.icon_profile_standard);
            omniBox_tab.getDrawable().mutate().setTint(color);
        } else if (listProtected.isWhite(url)) {
            omniBox_tab.setImageResource(R.drawable.icon_profile_protected);
            omniBox_tab.getDrawable().mutate().setTint(color);
        }
    }

    public void setProfileDefaultValues() {

        RecordAction action = new RecordAction(context);
        action.open(true);
        action.addBookmark(new Record("FOSS Browser - WIKI", "https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki", 0, 0, 2, false, false, 0));
        action.close();

        sp.edit()
                .putBoolean("profileTrusted_saveData", true)
                .putBoolean("profileTrusted_images", true)
                .putBoolean("profileTrusted_adBlock", true)
                .putBoolean("profileTrusted_trackingULS", false)
                .putBoolean("profileTrusted_location", false)
                .putBoolean("profileTrusted_fingerPrintProtection", false)
                .putBoolean("profileTrusted_cookies", true)
                .putBoolean("profileTrusted_cookiesThirdParty", true)
                .putBoolean("profileTrusted_deny_cookie_banners", false)
                .putBoolean("profileTrusted_javascript", true)
                .putBoolean("profileTrusted_javascriptPopUp", true)
                .putBoolean("profileTrusted_saveHistory", true)
                .putBoolean("profileTrusted_camera", false)
                .putBoolean("profileTrusted_microphone", false)
                .putBoolean("profileTrusted_dom", true)

                .putBoolean("profileStandard_saveData", true)
                .putBoolean("profileStandard_images", true)
                .putBoolean("profileStandard_adBlock", true)
                .putBoolean("profileStandard_trackingULS", false)
                .putBoolean("profileStandard_location", false)
                .putBoolean("profileStandard_fingerPrintProtection", true)
                .putBoolean("profileStandard_cookies", false)
                .putBoolean("profileStandard_cookiesThirdParty", false)
                .putBoolean("profileStandard_deny_cookie_banners", false)
                .putBoolean("profileStandard_javascript", true)
                .putBoolean("profileStandard_javascriptPopUp", false)
                .putBoolean("profileStandard_saveHistory", true)
                .putBoolean("profileStandard_camera", false)
                .putBoolean("profileStandard_microphone", false)
                .putBoolean("profileStandard_dom", false)

                .putBoolean("profileProtected_saveData", true)
                .putBoolean("profileProtected_images", true)
                .putBoolean("profileProtected_adBlock", true)
                .putBoolean("profileProtected_trackingULS", true)
                .putBoolean("profileProtected_location", false)
                .putBoolean("profileProtected_fingerPrintProtection", true)
                .putBoolean("profileProtected_cookies", false)
                .putBoolean("profileProtected_cookiesThirdParty", false)
                .putBoolean("profileProtected_deny_cookie_banners", false)
                .putBoolean("profileProtected_javascript", false)
                .putBoolean("profileProtected_javascriptPopUp", false)
                .putBoolean("profileProtected_saveHistory", true)
                .putBoolean("profileProtected_camera", false)
                .putBoolean("profileProtected_microphone", false)
                .putBoolean("profileProtected_dom", false).apply();
    }

    public void setProfileChanged() {
        sp.edit().putBoolean("profileChanged_saveData", sp.getBoolean(profile + "_saveData", true))
                .putBoolean("profileChanged_images", sp.getBoolean(profile + "_images", true))
                .putBoolean("profileChanged_adBlock", sp.getBoolean(profile + "_adBlock", true))
                .putBoolean("profileChanged_trackingULS", sp.getBoolean(profile + "_trackingULS", true))
                .putBoolean("profileChanged_location", sp.getBoolean(profile + "_location", false))
                .putBoolean("profileChanged_fingerPrintProtection", sp.getBoolean(profile + "_fingerPrintProtection", true))
                .putBoolean("profileChanged_cookies", sp.getBoolean(profile + "_cookies", false))
                .putBoolean("profileChanged_cookiesThirdParty", sp.getBoolean(profile + "_cookiesThirdParty", false))
                .putBoolean("profileChanged_deny_cookie_banners", sp.getBoolean(profile + "_deny_cookie_banners", false))
                .putBoolean("profileChanged_javascript", sp.getBoolean(profile + "_javascript", true))
                .putBoolean("profileChanged_javascriptPopUp", sp.getBoolean(profile + "_javascriptPopUp", false))
                .putBoolean("profileChanged_saveHistory", sp.getBoolean(profile + "_saveHistory", true))
                .putBoolean("profileChanged_camera", sp.getBoolean(profile + "_camera", false))
                .putBoolean("profileChanged_microphone", sp.getBoolean(profile + "_microphone", false))
                .putBoolean("profileChanged_dom", sp.getBoolean(profile + "_dom", false))
                .putString("profile", "profileChanged").apply();
    }

    public void putProfileBoolean(String string, Chip chip_profile_trusted,
                                  Chip chip_profile_standard, Chip chip_profile_protected, Chip chip_profile_changed) {
        switch (string) {
            case "_images":
                sp.edit().putBoolean("profileChanged_images", !sp.getBoolean("profileChanged_images", true)).apply();
                break;
            case "_javascript":
                sp.edit().putBoolean("profileChanged_javascript", !sp.getBoolean("profileChanged_javascript", true)).apply();
                break;
            case "_javascriptPopUp":
                sp.edit().putBoolean("profileChanged_javascriptPopUp", !sp.getBoolean("profileChanged_javascriptPopUp", false)).apply();
                break;
            case "_cookies":
                sp.edit().putBoolean("profileChanged_cookies", !sp.getBoolean("profileChanged_cookies", false)).apply();
                break;
            case "_cookiesThirdParty":
                sp.edit().putBoolean("profileChanged_cookiesThirdParty", !sp.getBoolean("profileChanged_cookiesThirdParty", false)).apply();
                break;
            case "_deny_cookie_banners":
                sp.edit().putBoolean("profileChanged_deny_cookie_banners", !sp.getBoolean("profileChanged_deny_cookie_banners", false)).apply();
                break;
            case "_fingerPrintProtection":
                sp.edit().putBoolean("profileChanged_fingerPrintProtection", !sp.getBoolean("profileChanged_fingerPrintProtection", true)).apply();
                break;
            case "_adBlock":
                sp.edit().putBoolean("profileChanged_adBlock", !sp.getBoolean("profileChanged_adBlock", true)).apply();
                break;
            case "_trackingULS":
                sp.edit().putBoolean("profileChanged_trackingULS", !sp.getBoolean("profileChanged_trackingULS", true)).apply();
                break;
            case "_saveData":
                sp.edit().putBoolean("profileChanged_saveData", !sp.getBoolean("profileChanged_saveData", true)).apply();
                break;
            case "_saveHistory":
                sp.edit().putBoolean("profileChanged_saveHistory", !sp.getBoolean("profileChanged_saveHistory", true)).apply();
                break;
            case "_location":
                sp.edit().putBoolean("profileChanged_location", !sp.getBoolean("profileChanged_location", false)).apply();
                break;
            case "_camera":
                sp.edit().putBoolean("profileChanged_camera", !sp.getBoolean("profileChanged_camera", false)).apply();
                break;
            case "_microphone":
                sp.edit().putBoolean("profileChanged_microphone", !sp.getBoolean("profileChanged_microphone", false)).apply();
                break;
            case "_dom":
                sp.edit().putBoolean("profileChanged_dom", !sp.getBoolean("profileChanged_dom", false)).apply();
                break;
        }
        this.initPreferences("");

        switch (Objects.requireNonNull(profile)) {
            case "profileTrusted":
                chip_profile_trusted.setChecked(true);
                chip_profile_standard.setChecked(false);
                chip_profile_protected.setChecked(false);
                chip_profile_changed.setChecked(false);
                break;
            case "profileStandard":
                chip_profile_trusted.setChecked(false);
                chip_profile_standard.setChecked(true);
                chip_profile_protected.setChecked(false);
                chip_profile_changed.setChecked(false);
                break;
            case "profileProtected":
                chip_profile_trusted.setChecked(false);
                chip_profile_standard.setChecked(false);
                chip_profile_protected.setChecked(true);
                chip_profile_changed.setChecked(false);
                break;
            default:
                chip_profile_trusted.setChecked(false);
                chip_profile_standard.setChecked(false);
                chip_profile_protected.setChecked(false);
                chip_profile_changed.setChecked(true);
                break;
        }
    }

    public boolean getBoolean(String string) {
        switch (string) {
            case "_images":
                return sp.getBoolean(profile + "_images", true);
            case "_javascript":
                return sp.getBoolean(profile + "_javascript", true);
            case "_javascriptPopUp":
                return sp.getBoolean(profile + "_javascriptPopUp", false);
            case "_cookies":
                return sp.getBoolean(profile + "_cookies", false);
            case "_cookiesThirdParty":
                return sp.getBoolean(profile + "_cookiesThirdParty", false);
            case "_deny_cookie_banners":
                return sp.getBoolean(profile + "_deny_cookie_banners", false);
            case "_fingerPrintProtection":
                return sp.getBoolean(profile + "_fingerPrintProtection", true);
            case "_adBlock":
                return sp.getBoolean(profile + "_adBlock", true);
            case "_trackingULS":
                return sp.getBoolean(profile + "_trackingULS", true);
            case "_saveData":
                return sp.getBoolean(profile + "_saveData", true);
            case "_saveHistory":
                return sp.getBoolean(profile + "_saveHistory", true);
            case "_location":
                return sp.getBoolean(profile + "_location", false);
            case "_camera":
                return sp.getBoolean(profile + "_camera", false);
            case "_microphone":
                return sp.getBoolean(profile + "_microphone", false);
            case "_dom":
                return sp.getBoolean(profile + "_dom", false);
            default:
                return false;
        }
    }

    private synchronized void initAlbum() {
        album.setBrowserController(browserController);
    }

    public synchronized HashMap<String, String> getRequestHeaders() {
        HashMap<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("DNT", "1");
        //  Server-side detection for GlobalPrivacyControl
        requestHeaders.put("Sec-GPC", "1");
        requestHeaders.put("X-Requested-With", "com.duckduckgo.mobile.android");
        requestHeaders.put("Referer", this.getUrl());
        profile = sp.getString("profile", "profileStandard");
        if (sp.getBoolean(profile + "_saveData", false)) requestHeaders.put("Save-Data", "on");
        return requestHeaders;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {

        if (sp.getBoolean("sp_audioBackground", false)) {

            NotificationManager mNotifyMgr = (NotificationManager) this.context.getSystemService(NOTIFICATION_SERVICE);
            if (visibility == View.GONE) {

                Intent intentP = new Intent(this.context, BrowserActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(this.context, 0, intentP, FLAG_IMMUTABLE);

                String name = "Audio background";
                String description = "Play audio on background -> click to open";
                int importance = NotificationManager.IMPORTANCE_LOW; //Important for heads-up notification
                NotificationChannel channel = new NotificationChannel("2", name, importance);
                channel.setDescription(description);
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager notificationManager = this.context.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this.context, "2")
                        .setSmallIcon(R.drawable.icon_web)
                        .setAutoCancel(true)
                        .setContentTitle(HelperUnit.domain(this.getUrl()))
                        .setContentText(this.context.getString(R.string.setting_title_audioBackground))
                        .setContentIntent(pendingIntent); //Set the intent that will fire when the user taps the notification
                Notification buildNotification = mBuilder.build();
                mNotifyMgr.notify(2, buildNotification);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    int permissionState = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS);

                    // If the permission is not granted, request it.
                    if (permissionState == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(this.activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                    } else {
                        // Build the notification and add the action.
                        mNotifyMgr.notify(2, buildNotification);
                    }
                } else {
                    // Build the notification and add the action.
                    mNotifyMgr.notify(2, buildNotification);
                }
            } else mNotifyMgr.cancel(2);
            super.onWindowVisibilityChanged(View.VISIBLE);
        } else
            super.onWindowVisibilityChanged(visibility);
    }

    @Override
    public synchronized void stopLoading() {
        stopped = true;
        super.stopLoading();
    }

    public synchronized void reloadWithoutInit() {  //needed for camera usage without deactivating "save_data"
        stopped = false;
        super.reload();
    }

    public synchronized void goBack() {
        WebBackForwardList mWebBackForwardList = this.copyBackForwardList();
        if (mWebBackForwardList.getCurrentIndex() > 0) {
            String historyUrl;
            historyUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getCurrentIndex()-1).getUrl();
            initPreferences(historyUrl);
        }
        super.goBack();
    }

    @Override
    public synchronized void reload() {
        stopped = false;
        this.initPreferences(this.getUrl());
        super.reload();
    }

    @Override
    public synchronized void loadUrl(@NonNull String url) {

        InputMethodManager imm = (InputMethodManager) this.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getWindowToken(), 0);

        String urlToLoad = BrowserUnit.redirectURL( this, sp, url);

        favicon = null;
        stopped = false;

        listTrusted = new List_trusted(context);
        listStandard = new List_standard(context);
        listProtected = new List_protected(context);

        profile = sp.getString("profile", "profileStandard");
        if (listTrusted.isWhite(url)) profile = "profileTrusted";
        else if (listStandard.isWhite(url)) profile = "profileStandard";
        else if (listProtected.isWhite(url)) profile = "profileProtected";

        boolean removeTracking = sp.getBoolean(profile + "_trackingULS", true);
        if (removeTracking && urlToLoad.contains("?") && urlToLoad.contains("/")) {
            stopLoading();
            String lastIndex = urlToLoad.substring(urlToLoad.lastIndexOf("/"));
            String tracking = urlToLoad.substring(urlToLoad.lastIndexOf("?"));
            String urlClean = urlToLoad.replace(tracking, "");
            if (lastIndex.contains(tracking)) {

                String m = context.getString(R.string.dialog_tracking) + " \"" + tracking + "\"" + "?";

                if (m.length() > 150) {
                    m = m.substring(0, 150) + " [...]?\"";
                }

                GridItem item_01 = new GridItem(context.getString(R.string.app_ok), R.drawable.icon_check);
                GridItem item_02 = new GridItem( context.getString(R.string.app_no), R.drawable.icon_close);
                GridItem item_03 = new GridItem( context.getString(R.string.menu_edit), R.drawable.icon_edit);

                View dialogView = View.inflate(context, R.layout.dialog_menu, null);
                MaterialAlertDialogBuilder builderTrack = new MaterialAlertDialogBuilder(context);

                CardView albumCardView = dialogView.findViewById(R.id.albumCardView);
                albumCardView.setVisibility(GONE);
                builderTrack.setTitle(urlToLoad);
                builderTrack.setIcon(R.drawable.icon_tracking);
                builderTrack.setMessage(m);
                builderTrack.setPositiveButton(R.string.app_cancel, (dialog2, whichButton) -> dialog2.cancel());
                builderTrack.setView(dialogView);
                AlertDialog dialogTrack = builderTrack.create();
                dialogTrack.show();
                HelperUnit.setupDialog(context, dialogTrack);

                GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
                final List<GridItem> gridList = new LinkedList<>();
                gridList.add(gridList.size(), item_01);
                gridList.add(gridList.size(), item_02);
                gridList.add(gridList.size(), item_03);
                GridAdapter gridAdapter = new GridAdapter(context, gridList);
                menu_grid.setAdapter(gridAdapter);
                gridAdapter.notifyDataSetChanged();
                menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                    switch (position) {
                        case 0:
                            dialogTrack.cancel();
                            initPreferences(BrowserUnit.queryWrapper(context, urlClean));
                            super.loadUrl(BrowserUnit.queryWrapper(context, urlClean), getRequestHeaders());
                            break;
                        case 1:
                            dialogTrack.cancel();
                            initPreferences(BrowserUnit.queryWrapper(context, urlClean));
                            super.loadUrl(BrowserUnit.queryWrapper(context, urlToLoad), getRequestHeaders());
                            break;
                        case 2:
                            dialogTrack.cancel();
                            View dialogEdit = View.inflate(getContext(), R.layout.dialog_edit_text, null);
                            TextInputEditText input = dialogEdit.findViewById(R.id.textInput);
                            input.setText(urlToLoad);
                            HelperUnit.showSoftKeyboard(input);

                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                            builder.setTitle(context.getString(R.string.menu_edit));
                            builder.setIcon(R.drawable.icon_tracking);
                            builder.setNegativeButton(R.string.app_cancel, null);
                            builder.setPositiveButton(R.string.app_ok, (dialog, i) -> {
                                dialog.dismiss();
                                String newValue = Objects.requireNonNull(input.getText()).toString();
                                initPreferences(BrowserUnit.queryWrapper(context, newValue));
                                super.loadUrl(BrowserUnit.queryWrapper(context, newValue), getRequestHeaders());
                            });
                            builder.setView(dialogEdit);
                            Dialog dialog = builder.create();
                            dialog.show();
                            HelperUnit.setupDialog(context, dialog);
                            break;
                    }
                });
            }

        } else if (url.startsWith("http://") && sp.getString("dialog_neverAsk", "no").equals("no")) {

            stopLoading();
            GridItem item_01 = new GridItem("https://", R.drawable.icon_secure);
            GridItem item_02 = new GridItem( "http://", R.drawable.icon_unsecure);
            GridItem item_03 = new GridItem( context.getString(R.string.app_never), R.drawable.icon_close);

            View dialogView = View.inflate(context, R.layout.dialog_menu, null);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

            CardView albumCardView = dialogView.findViewById(R.id.albumCardView);
            albumCardView.setVisibility(GONE);

            String secure = url.replace("http://", "https://");

            builder.setTitle(HelperUnit.domain(url));
            builder.setIcon(R.drawable.icon_unsecure);
            builder.setMessage(R.string.toast_unsecured);
            builder.setPositiveButton(R.string.app_cancel, (dialog2, whichButton) -> dialog2.cancel());
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(context, dialog);

            GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
            final List<GridItem> gridList = new LinkedList<>();
            gridList.add(gridList.size(), item_01);
            gridList.add(gridList.size(), item_02);
            gridList.add(gridList.size(), item_03);
            GridAdapter gridAdapter = new GridAdapter(context, gridList);
            menu_grid.setAdapter(gridAdapter);
            gridAdapter.notifyDataSetChanged();
            menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                switch (position) {
                    case 0:
                        dialog.cancel();
                        initPreferences(BrowserUnit.queryWrapper(context, secure));
                        super.loadUrl(BrowserUnit.queryWrapper(context, secure), getRequestHeaders());
                        break;
                    case 1:
                        dialog.cancel();
                        initPreferences(BrowserUnit.queryWrapper(context, url));
                        super.loadUrl(BrowserUnit.queryWrapper(context, url), getRequestHeaders());
                        break;
                    case 2:
                        sp.edit().putString("dialog_neverAsk", "yes").apply();
                        initPreferences(BrowserUnit.queryWrapper(context, url));
                        super.loadUrl(BrowserUnit.queryWrapper(context, url), getRequestHeaders());
                        break;
                }
            });
        } else {
            initPreferences(BrowserUnit.queryWrapper(context, urlToLoad));
            super.loadUrl(BrowserUnit.queryWrapper(context, urlToLoad), getRequestHeaders());
        }
    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    public void setAlbumTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate();
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate();
    }

    public synchronized void updateTitle(int progress) {
        if (foreground && !stopped) browserController.updateProgress(progress);
        else if (foreground) browserController.updateProgress(BrowserUnit.LOADING_STOPPED);
    }

    public synchronized void updateTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    public synchronized void updateFavicon(String url) {
        FaviconHelper.setFavicon(context, album.getAlbumView(), url, R.id.faviconView, R.drawable.icon_image_broken);
    }

    @Override
    public synchronized void destroy() {
        stopLoading();
        onPause();
        clearHistory();
        setVisibility(GONE);
        removeAllViews();
        super.destroy();
    }

    public boolean isDesktopMode() {
        return desktopMode;
    }

    public boolean isFingerPrintProtection() {
        return fingerPrintProtection;
    }

    public boolean isHistory() {
        return history;
    }

    public boolean isAdBlock() {
        return adBlock;
    }

    public boolean isSaveData() {
        return saveData;
    }

    public boolean isCamera() {
        return camera;
    }

    public String getUserAgent(boolean desktopMode) {
        String mobilePrefix = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")";
        String desktopPrefix = "Mozilla/5.0 (X11; Linux " + System.getProperty("os.arch") + ")";

        String newUserAgent = WebSettings.getDefaultUserAgent(context);
        String prefix = newUserAgent.substring(0, newUserAgent.indexOf(")") + 1);

        if (desktopMode) {
            try {
                newUserAgent = newUserAgent.replace(prefix, desktopPrefix);
            } catch (Exception e) {
                Log.v(TAG, "Failed: UserAgent");
            }
        } else {
            try {
                newUserAgent = newUserAgent.replace(prefix, mobilePrefix);
            } catch (Exception e) {
                Log.v(TAG, "Failed: UserAgent");
            }
        }

        //Override UserAgent if own UserAgent is defined
        if (!sp.contains("userAgentSwitch")) {  //if new switch_text_preference has never been used initialize the switch
            if (Objects.requireNonNull(sp.getString("sp_userAgent", "")).isEmpty()) {
                sp.edit().putBoolean("userAgentSwitch", false).apply();
            } else sp.edit().putBoolean("userAgentSwitch", true).apply();
        }

        String ownUserAgent = sp.getString("sp_userAgent", "");
        if (!ownUserAgent.isEmpty() && (sp.getBoolean("userAgentSwitch", false)))
            newUserAgent = ownUserAgent;
        return newUserAgent;
    }

    public void toggleDesktopMode(boolean reload) {
        desktopMode = !desktopMode;
        String newUserAgent = getUserAgent(desktopMode);
        getSettings().setUserAgentString(newUserAgent);
        getSettings().setUseWideViewPort(desktopMode);
        getSettings().setLoadWithOverviewMode(desktopMode);
        if (reload) reload();
    }

    public void toggleNightMode() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettings s = this.getSettings();
            boolean allowed = sp.getBoolean("setAlgorithmicDarkeningAllowed", true);
            if (allowed) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
                sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", false).apply();
            } else {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, true);
                sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", true).apply();
            }
        }
    }

    public void resetFavicon() {
        this.favicon = null;
    }

    @Nullable
    @Override
    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
        //Save faviconView for existing bookmarks or start site entries
        FaviconHelper faviconHelper = new FaviconHelper(context);
        RecordAction action = new RecordAction(context);
        action.open(false);
        List<Record> list;
        list = action.listEntries((Activity) context);
        action.close();
        for (Record listItem : list) {
            if (listItem.getURL().equals(getUrl()) && faviconHelper.getFavicon(listItem.getURL()) == null)
                faviconHelper.addFavicon(this.context, getUrl(), getFavicon());
        }
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public static String getProfile() {
        return profile;
    }

    public AlbumController getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(AlbumController predecessor) {
        this.predecessor = predecessor;
    }

    public interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         *
         * @param scrollY    Current vertical scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        void onScrollChange(int scrollY, int oldScrollY);
    }
}