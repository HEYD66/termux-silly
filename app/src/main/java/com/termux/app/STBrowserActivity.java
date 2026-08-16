package com.termux.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.HttpAuthHandler;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.MotionEvent;

import androidx.appcompat.app.AppCompatActivity;

import java.net.HttpURLConnection;
import java.net.URL;

public class STBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    private static final int FILE_CHOOSER_REQUEST = 7101;

    private static WebView retainedWebView;
    /**
     * A retained WebView must use a context that can be switched back to the
     * current Activity. Application context breaks WebView-owned popups such as
     * HTML select menus because it has no window token.
     */
    private static MutableContextWrapper retainedWebViewContext;
    private static String retainedUrl;
    private static boolean retainedTriedLocalhostFallback;
    private static boolean retainedPageLoaded;
    private static boolean useSoftwareRendering;

    private AlertDialog httpAuthDialog;
    private LinearLayout root;
    private FrameLayout webContainer;
    private LinearLayout loadingPanel;
    private TextView loadingText;
    private WebView webView;
    private String currentUrl;
    private ValueCallback<Uri[]> fileChooserCallback;
    private int loadRetryCount;
    private boolean initialLoadPending;
    private View.OnLayoutChangeListener webViewLayoutListener;
    private int lastAppliedWebViewHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(SillyUI.BG_ROOT);
        int tbPad = dp(2);
        toolbar.setPadding(tbPad, dp(2), tbPad, dp(2));
        root.addView(toolbar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button backButton = addButton(toolbar, "\u8fd4\u56de");
        Button refreshButton = addButton(toolbar, "\u5237\u65b0");
        Button closeButton = addButton(toolbar, "\u63a7\u5236\u53f0");

        backButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) webView.goBack();
            else finish();
        });
        refreshButton.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
        closeButton.setOnClickListener(v -> finish());

        webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        createLoadingPanel();

        setContentView(root);

        currentUrl = getIntent().getStringExtra(EXTRA_URL);
        if (currentUrl == null || currentUrl.trim().isEmpty()) currentUrl = "http://localhost:8001";
        currentUrl = preferLocalhost(currentUrl);
        attachOrCreateWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void attachOrCreateWebView() {
        if (retainedWebView != null) {
            if (retainedWebViewContext != null) retainedWebViewContext.setBaseContext(this);
            webView = retainedWebView;
            prepareWebViewForTouch();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webContainer.addView(webView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            applyRenderingMode();
            configureWebViewClient();
            configureWebChromeClient();
            installWebViewLayoutListener();
            webView.onResume();
            if (retainedUrl == null || !retainedUrl.equals(currentUrl)) {
                loadWhenServerReady(0);
            } else if (retainedPageLoaded) {
                hideLoading();
                webView.post(() -> applyMobileLayout(webView));
            } else {
                showLoading("正在恢复 SillyTavern…");
            }
            return;
        }

        retainedWebViewContext = new MutableContextWrapper(this);
        webView = new WebView(retainedWebViewContext);
        retainedWebView = webView;
        prepareWebViewForTouch();
        applyRenderingMode();
        webView.setBackgroundColor(Color.rgb(18, 18, 18));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webContainer.addView(webView, 0, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) settings.setOffscreenPreRaster(false);

        configureWebViewClient();
        configureWebChromeClient();
        installWebViewLayoutListener();
        loadWhenServerReady(0);
    }

    /**
     * Keep the WebView as the touch target even when it is hosted in the
     * activity's toolbar/content hierarchy. Returning false from the listener
     * deliberately leaves the event to WebView's own dispatcher.
     */
    private void prepareWebViewForTouch() {
        if (webView == null) return;
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setClickable(true);
        webView.setLongClickable(true);
        webView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.requestFocus(View.FOCUS_DOWN);
                ViewParent parent = view.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                ViewParent parent = view.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private void createLoadingPanel() {
        loadingPanel = new LinearLayout(this);
        loadingPanel.setOrientation(LinearLayout.VERTICAL);
        loadingPanel.setGravity(android.view.Gravity.CENTER);
        loadingPanel.setBackgroundColor(Color.rgb(18, 18, 18));

        ProgressBar progress = new ProgressBar(this);
        loadingPanel.addView(progress, new LinearLayout.LayoutParams(dp(42), dp(42)));

        loadingText = new TextView(this);
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(14);
        loadingText.setGravity(android.view.Gravity.CENTER);
        loadingText.setPadding(dp(20), dp(14), dp(20), 0);
        loadingPanel.addView(loadingText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webContainer.addView(loadingPanel, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showLoading("正在连接 SillyTavern…");
    }

    private void applyRenderingMode() {
        if (webView == null) return;
        // LAYER_TYPE_NONE keeps normal window hardware acceleration without forcing
        // WebView into a separate full-screen GPU texture. The forced hardware layer
        // exhausted tile memory on Mali devices and caused a long black first frame.
        webView.setLayerType(
            useSoftwareRendering ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE, null);
    }

    private void showLoading(String message) {
        if (loadingText != null) loadingText.setText(message);
        if (loadingPanel != null) {
            loadingPanel.setVisibility(View.VISIBLE);
            loadingPanel.bringToFront();
        }
    }

    private void hideLoading() {
        if (loadingPanel != null) loadingPanel.setVisibility(View.GONE);
    }

    private void configureWebChromeClient() {
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                String[] accepted = params == null ? null : params.getAcceptTypes();
                if (accepted != null && accepted.length > 0) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, accepted);
                }
                if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });
    }

    private void configureWebViewClient() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null
                    || !isExternalHttpUrl(request.getUrl())) {
                    return false;
                }
                openExternalUrl(request.getUrl());
                return true;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (!isExternalHttpUrl(uri)) return false;
                openExternalUrl(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && ("localhost".equalsIgnoreCase(Uri.parse(url).getHost())
                    || "127.0.0.1".equals(Uri.parse(url).getHost()))) {
                    applyMobileLayout(view);
                }
                retainedPageLoaded = true;
                view.postDelayed(STBrowserActivity.this::hideLoading, 150);
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String host, String realm) {
                showHttpAuthDialog(handler, host, realm);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request != null
                    && request.isForMainFrame()) {
                    retainedPageLoaded = false;
                    showLoading("连接失败，正在重试…");
                    if (!retainedTriedLocalhostFallback) {
                        String fallbackUrl = toggleLoopbackHost(currentUrl);
                        if (!fallbackUrl.equals(currentUrl)) {
                            retainedTriedLocalhostFallback = true;
                            currentUrl = fallbackUrl;
                        }
                    }
                    scheduleLoadRetry();
                }
            }

            @TargetApi(Build.VERSION_CODES.O)
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (detail != null && detail.didCrash()) useSoftwareRendering = true;
                retainedPageLoaded = false;
                showLoading("WebView 渲染进程已重启…");
                destroyWebView(true);
                root.postDelayed(() -> {
                    attachOrCreateWebView();
                    if (webView != null) loadCurrentUrl();
                }, 500);
                return true;
            }
        });
    }

    /**
     * Android WebView does not display a Basic Auth prompt on its own. Without
     * handling this callback, SillyTavern's 401 response is rendered as a
     * permanent "Unauthorized" page. Keep credentials in memory only; WebView
     * will reuse them for the retained page during this app session.
     */
    private void showHttpAuthDialog(HttpAuthHandler handler, String host, String realm) {
        if (handler == null || isFinishing() || isDestroyed()) {
            if (handler != null) handler.cancel();
            return;
        }
        if (httpAuthDialog != null && httpAuthDialog.isShowing()) {
            handler.cancel();
            return;
        }

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(24);
        fields.setPadding(horizontalPadding, dp(4), horizontalPadding, 0);

        TextView message = new TextView(this);
        String target = host == null || host.trim().isEmpty() ? "SillyTavern" : host;
        String scope = realm == null || realm.trim().isEmpty() ? "HTTP Basic Auth" : realm;
        message.setText("服务器 " + target + " 要求登录（" + scope + "）");
        message.setTextColor(Color.WHITE);
        fields.addView(message, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setHint("用户名");
        username.setText("user");
        fields.addView(username, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setText("password");
        fields.addView(password, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("SillyTavern 登录")
            .setView(fields)
            .setNegativeButton("取消", (ignored, which) -> handler.cancel())
            .setPositiveButton("登录", null)
            .create();
        httpAuthDialog = dialog;
        dialog.setOnCancelListener(ignored -> handler.cancel());
        dialog.setOnDismissListener(ignored -> {
            if (httpAuthDialog == dialog) httpAuthDialog = null;
        });
        dialog.setOnShowListener(ignored -> {
            Button loginButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            loginButton.setOnClickListener(v -> {
                String user = username.getText().toString().trim();
                String pass = password.getText().toString();
                if (user.isEmpty()) {
                    username.setError("请输入用户名");
                    username.requestFocus();
                    return;
                }
                handler.proceed(user, pass);
                dialog.dismiss();
            });
            password.requestFocus();
        });
        dialog.show();
    }

    private void applyMobileLayout(WebView view) {
        if (view == null || view.getHeight() <= 0) return;
        int viewportHeight = Math.max(1, Math.round(
            view.getHeight() / getResources().getDisplayMetrics().density));
        lastAppliedWebViewHeight = view.getHeight();
        String script = buildMobileLayoutScript(viewportHeight);
        view.evaluateJavascript(script, null);
    }

    static String buildMobileLayoutScript(int viewportHeight) {
        int safeViewportHeight = Math.max(1, viewportHeight);
        return "(function(){"
            + "var viewport=document.querySelector('meta[name=\"viewport\"]');"
            + "if(!viewport){viewport=document.createElement('meta');viewport.name='viewport';document.head.appendChild(viewport);}"
            // Re-assigning the viewport after load is intentional. Chromium 99
            // otherwise keeps its desktop 980px layout viewport in this WebView,
            // so SillyTavern selects the desktop split layout and renders half-width.
            + "viewport.content='width=device-width,viewport-fit=cover,initial-scale=1,maximum-scale=1,user-scalable=no,interactive-widget=resizes-content';"
            + "var id='stavern-mobile-layout';"
            + "var style=document.getElementById(id);"
            + "if(!style){style=document.createElement('style');style.id=id;document.head.appendChild(style);}"
            // Chromium 99 reports a stale CSS viewport height after the viewport meta is
            // normalized. That makes SillyTavern's 100vh/100dvh shell only about two thirds of
            // the real WebView and leaves the send box in the middle. Feed the measured native
            // WebView height back as a CSS pixel variable instead.
            + "style.textContent=':root{--stavernWebViewHeight:" + safeViewportHeight + "px!important;--sheldWidth:100vw!important;--blurStrength:0!important;--SmartThemeBlurStrength:0px!important;}'"
            + "+'#top-bar,#sheld,#character_popup,.drawer-content{width:100vw!important;max-width:100vw!important;}'"
            + "+'html,body{height:var(--stavernWebViewHeight)!important;min-height:var(--stavernWebViewHeight)!important;max-height:var(--stavernWebViewHeight)!important;max-width:100%!important;overflow-x:hidden!important;scroll-behavior:auto!important;}'"
            + "+'#bg1,#bg_custom{height:var(--stavernWebViewHeight)!important;max-height:var(--stavernWebViewHeight)!important;}'"
            + "+'#sheld{left:0!important;margin-left:0!important;margin-right:0!important;height:calc(var(--stavernWebViewHeight) - var(--topBarBlockSize) - 1px)!important;max-height:calc(var(--stavernWebViewHeight) - var(--topBarBlockSize) - 1px)!important;}'"
            + "+'#chat{max-height:calc(var(--stavernWebViewHeight) - var(--topBarBlockSize) - var(--bottomFormBlockSize))!important;}'"
            // Chromium 99 + Mali still allocates a backing surface for blur(0).
            // Removing the filter avoids both tile-memory churn and EGL BAD_ALLOC.
            + "+'*,*::before,*::after{-webkit-backdrop-filter:none!important;backdrop-filter:none!important;}';"
            + "window.dispatchEvent(new Event('resize'));"
            + "})();";
    }

    private void installWebViewLayoutListener() {
        if (webView == null) return;
        if (webViewLayoutListener != null) webView.removeOnLayoutChangeListener(webViewLayoutListener);
        webViewLayoutListener = (v, left, top, right, bottom,
                                 oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (height > 0 && height != lastAppliedWebViewHeight && retainedPageLoaded) {
                v.post(() -> applyMobileLayout((WebView) v));
            }
        };
        webView.addOnLayoutChangeListener(webViewLayoutListener);
    }

    private void loadWhenServerReady(int attempt) {
        if (webView == null || (attempt == 0 && initialLoadPending)) return;
        initialLoadPending = true;
        if (attempt == 0) showLoading("正在等待 SillyTavern 服务…");
        final String url = currentUrl;
        new Thread(() -> {
            boolean ready = probeUrl(url);
            runOnUiThread(() -> {
                if (webView == null || isFinishing()) return;
                if (ready || attempt >= 30) {
                    initialLoadPending = false;
                    showLoading(ready ? "正在加载 SillyTavern…" : "服务响应较慢，正在尝试打开…");
                    loadCurrentUrl();
                } else {
                    webView.postDelayed(() -> loadWhenServerReady(attempt + 1), 500);
                }
            });
        }, "stavern-web-probe").start();
    }

    private boolean probeUrl(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(800);
            connection.setReadTimeout(800);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            return connection.getResponseCode() < 500;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isLocalUrl(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    private boolean isExternalHttpUrl(Uri uri) {
        if (uri == null || isLocalUrl(uri)) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private void openExternalUrl(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
    }

    private void scheduleLoadRetry() {
        if (webView == null || loadRetryCount >= 30) return;
        loadRetryCount++;
        webView.postDelayed(() -> {
            if (webView != null) loadCurrentUrl();
        }, 1000);
    }

    private void loadCurrentUrl() {
        retainedUrl = currentUrl;
        retainedPageLoaded = false;
        if (webView != null) webView.loadUrl(currentUrl);
    }

    private String preferLocalhost(String url) {
        if (url == null) return "http://localhost:8001";
        return url.replace("http://127.0.0.1:", "http://localhost:");
    }

    private String toggleLoopbackHost(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if ("localhost".equalsIgnoreCase(host)) return url.replace("http://localhost:", "http://127.0.0.1:");
        if ("127.0.0.1".equals(host)) return url.replace("http://127.0.0.1:", "http://localhost:");
        return url;
    }

    private Button addButton(LinearLayout parent, String text) {
        Button button = new Button(this);
        button.setText(text);
        SillyUI.styleButton(this, button, SillyUI.BTN_GHOST);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.setMarginEnd(dp(2));
        parent.addView(button, lp);
        return button;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;

        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clipData = data.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                result = new Uri[clipData.getItemCount()];
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    result[i] = clipData.getItemAt(i).getUri();
                    takeReadPermission(result[i]);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                result = new Uri[]{uri};
                takeReadPermission(uri);
            }
        }
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    private void takeReadPermission(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        detachWebView();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            if (retainedPageLoaded) webView.post(() -> applyMobileLayout(webView));
        }
    }

    private void detachWebView() {
        if (webView == null) return;
        if (webViewLayoutListener != null) {
            webView.removeOnLayoutChangeListener(webViewLayoutListener);
            webViewLayoutListener = null;
        }
        lastAppliedWebViewHeight = 0;
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        webView.onPause();
        // A retained WebView must not keep anonymous clients that capture the
        // finished Activity. Replace them before detaching to avoid Activity leaks.
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        if (retainedWebViewContext != null) {
            retainedWebViewContext.setBaseContext(getApplicationContext());
        }
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webView = null;
    }

    private void destroyWebView(boolean clearRetained) {
        if (webView == null && retainedWebView != null) webView = retainedWebView;
        if (webView == null) return;
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webView.destroy();
        if (clearRetained) {
            retainedWebView = null;
            retainedWebViewContext = null;
            retainedUrl = null;
            retainedTriedLocalhostFallback = false;
            retainedPageLoaded = false;
        }
        webView = null;
    }
}
