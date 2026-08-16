package com.termux.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.os.Build;
import android.util.Base64;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class SillyHomeOverlay {

    private static final String GITEE_SOURCE = "https://gitee.com/HEYD66/SillyTavern.git";
    private static final String OFFICIAL_SOURCE = "https://github.com/SillyTavern/SillyTavern";
    private static final String AUTO_SOURCE = GITEE_SOURCE + " " + OFFICIAL_SOURCE;
    private static final String DEFAULT_CONFIG_ASSET_NAME = "default-config.yaml";
    private static final String DEFAULT_CONFIG_FILE_NAME = "default-config.yaml";
    private static final String BASIC_AUTH_CREDENTIALS_FILE_NAME = "basic-auth.credentials";
    private static final String SILLY_SESSION_NAME = "SillyTavern";

    private final TermuxActivity activity;
    private final Handler handler = new Handler();
    private View overlayRoot;
    private LinearLayout overlay;
    private ScrollView homeScrollView;
    private FrameLayout pageHost;
    private LinearLayout configPage;
    private LinearLayout configContent;
    private View configUnavailableView;
    private LinearLayout logPage;
    private ScrollView logTextScroll;
    private TextView logView;
    private CharacterGalleryView characterGallery;
    private Button[] mainTabButtons;
    private Button[] logTabButtons;
    private int selectedLog;
    private int selectedTab;
    private LinearLayout dependencyList;
    private TextView statusView;
    private TextView lanAddressView;
    private Button copyLanAddressButton;
    private EditText branchInput;
    private EditText portInput;
    private EditText configPortInput;
    private Spinner sourceSpinner;
    private EditText customSourceInput;
    private CheckBox openBrowserAfterStart;
    private Switch userAccountsEnabled;
    private Switch discreetLoginEnabled;
    private Switch whitelistModeEnabled;
    private Switch listenEnabled;
    private Switch basicAuthEnabled;
    private Switch perUserBasicAuthEnabled;
    private EditText authUsernameInput;
    private EditText authPasswordInput;
    private EditText whitelistInput;
    private Button installButton;
    private Button updateButton;
    private Button startButton;
    private Button stopButton;
    private Button browserButton;
    private Button applyConfigButton;
    private Button restoreButton;
    private TerminalSession controlSession;
    private String pendingConfigCommandArgs;
    private boolean configDirty;
    private boolean configUpdating;
    private boolean portUpdating;
    private boolean configUserAccounts;
    private boolean configDiscreetLogin;
    private boolean configWhitelistMode = true;
    private boolean configListen;
    private boolean configBasicAuth;
    private boolean configPerUserBasicAuth;
    private String configWhitelist = "::1,127.0.0.1";
    private String pendingAction;
    private int pendingServiceRetries;

    public SillyHomeOverlay(TermuxActivity activity) {
        this.activity = activity;
    }

    public View build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        // Keep the tab bar fixed while each page scrolls independently.
        // Keep the terminal tab transparent so the real Termux terminal stays
        // interactive underneath the fixed tab bar.
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setPadding(dp(10), dp(10), dp(10), dp(12));
        overlayRoot = root;

        LinearLayout tabBar = new LinearLayout(activity);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(0, 0, 0, dp(8));
        String[] tabLabels = new String[]{"\u4e3b\u9875", "\u914d\u7f6e", "\u7ec8\u7aef", "\u65e5\u5fd7", "\u89d2\u8272\u5361"};
        mainTabButtons = new Button[tabLabels.length];
        for (int i = 0; i < tabLabels.length; i++) {
            final int tab = i;
            Button button = new Button(activity);
            button.setText(tabLabels[i]);
            button.setOnClickListener(v -> selectMainTab(tab));
            SillyUI.styleButton(activity, button, SillyUI.BTN_GHOST);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) tabLp.setMarginStart(dp(6));
            tabBar.addView(button, tabLp);
            mainTabButtons[i] = button;
        }
        root.addView(tabBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pageHost = new FrameLayout(activity);
        root.addView(pageHost, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        homeScrollView = createPageScrollView();
        overlay = new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setPadding(dp(18), dp(20), dp(18), dp(22));
        overlay.setBackground(SillyUI.roundRect(SillyUI.BG_CARD, dp(22), 0x99DDE5F2, 1f));
        homeScrollView.addView(overlay, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageHost.addView(homeScrollView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        configPage = new LinearLayout(activity);
        configPage.setOrientation(LinearLayout.VERTICAL);
        configPage.setPadding(dp(18), dp(20), dp(18), dp(22));
        configPage.setBackground(SillyUI.roundRect(SillyUI.BG_CARD, dp(22), 0x99DDE5F2, 1f));
        ScrollView configScroll = createPageScrollView();
        configScroll.addView(configPage, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageHost.addView(configScroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        configUnavailableView = buildConfigUnavailableView();
        configPage.addView(configUnavailableView);
        configContent = new LinearLayout(activity);
        configContent.setOrientation(LinearLayout.VERTICAL);
        configPage.addView(configContent, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        logPage = buildLogPage();
        ScrollView logScroll = createPageScrollView();
        logScroll.addView(logPage, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageHost.addView(logScroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        characterGallery = new CharacterGalleryView(activity);
        pageHost.addView(characterGallery.build(), new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("SillyTavern");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 31);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(SillyUI.TEXT_PRIMARY);
        title.setLetterSpacing(0f);
        titleCol.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Termux 控制台 · 一键安装与启动");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(SillyUI.TEXT_SECONDARY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(5), 0, 0);
        titleCol.addView(subtitle, subLp);

        header.addView(titleCol, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView mascot = new ImageView(activity);
        mascot.setImageResource(R.drawable.silly_mascot_cutout);
        mascot.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mascot.setAlpha(0.96f);
        LinearLayout.LayoutParams mascotLp = new LinearLayout.LayoutParams(dp(126), dp(142));
        mascotLp.setMarginStart(dp(8));
        header.addView(mascot, mascotLp);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, 0, 0, dp(10));
        overlay.addView(header, headerLp);

        // Status chip card.
        LinearLayout statusCard = SillyUI.card(activity, null);
        statusView = new TextView(activity);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusView.setTextColor(SillyUI.TEXT_PRIMARY);
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusView.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        statusCard.addView(statusView);
        overlay.addView(statusCard);

        // Show the real address that a computer on the same Wi-Fi can use.
        LinearLayout lanCard = SillyUI.card(activity, "局域网共享");
        TextView lanHint = new TextView(activity);
        lanHint.setText("手机和电脑连接同一个 Wi-Fi 后，用电脑浏览器打开下面的地址。开启“允许局域网访问”并保存重启后生效。");
        lanHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lanHint.setTextColor(SillyUI.TEXT_SECONDARY);
        lanCard.addView(lanHint);
        lanAddressView = new TextView(activity);
        lanAddressView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lanAddressView.setTypeface(Typeface.MONOSPACE);
        lanAddressView.setTextColor(SillyUI.TEXT_PRIMARY);
        lanAddressView.setPadding(dp(12), dp(10), dp(12), dp(10));
        lanAddressView.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        LinearLayout.LayoutParams lanAddressLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lanAddressLp.setMargins(0, dp(8), 0, dp(4));
        lanCard.addView(lanAddressView, lanAddressLp);
        copyLanAddressButton = addButton("复制电脑访问地址", this::copyLanAddress, SillyUI.BTN_SECONDARY);
        addFullButton(lanCard, copyLanAddressButton);
        overlay.addView(lanCard);

        // Environment status card.
        LinearLayout depsCard = SillyUI.card(activity, "环境状态");
        final TextView depsHeader = (TextView) depsCard.getChildAt(0);
        dependencyList = new LinearLayout(activity);
        dependencyList.setOrientation(LinearLayout.VERTICAL);
        dependencyList.setVisibility(View.GONE);
        depsCard.addView(dependencyList);
        if (depsHeader != null) {
            depsHeader.setText("环境状态 · 点此展开");
            depsHeader.setClickable(true);
            depsHeader.setFocusable(true);
            depsHeader.setOnClickListener(v -> {
                boolean shown = dependencyList.getVisibility() == View.VISIBLE;
                dependencyList.setVisibility(shown ? View.GONE : View.VISIBLE);
                depsHeader.setText(shown ? "环境状态 · 点此展开" : "环境状态 · 收起");
            });
        }
        overlay.addView(depsCard);

        // Settings card.
        LinearLayout settingsCard = SillyUI.card(activity, "安装设置");

        branchInput = styledInput();
        branchInput.setText("release");
        branchInput.setHint("release");
        branchInput.setSingleLine(true);
        branchInput.setImeOptions(EditorInfo.IME_ACTION_DONE);

        portInput = styledInput();
        portInput.setText("8001");
        portInput.setHint("端口");
        portInput.setSingleLine(true);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setImeOptions(EditorInfo.IME_ACTION_DONE);

        LinearLayout combinedRow = new LinearLayout(activity);
        combinedRow.setOrientation(LinearLayout.HORIZONTAL);
        combinedRow.addView(verticalField("分支", branchInput),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        LinearLayout.LayoutParams colPortLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        colPortLp.setMarginStart(dp(8));
        combinedRow.addView(verticalField("端口", portInput), colPortLp);
        settingsCard.addView(combinedRow);

        LinearLayout sourceCol = verticalField("源码源", null);
        sourceSpinner = new Spinner(activity);
        String[] sourceOptions = new String[]{
            "Gitee 优先（GitHub 备用）",
            "自定义源"
        };
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, sourceOptions) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) ((TextView) v).setTextColor(SillyUI.TEXT_PRIMARY);
                return v;
            }
            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(SillyUI.TEXT_PRIMARY);
                    v.setBackgroundColor(SillyUI.BG_CARD);
                }
                return v;
            }
        };
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        SillyUI.styleSpinner(activity, sourceSpinner);
        sourceSpinner.setAdapter(sourceAdapter);
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (customSourceInput != null) customSourceInput.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sourceCol.addView(sourceSpinner);
        settingsCard.addView(sourceCol);

        customSourceInput = styledInput();
        customSourceInput.setHint("自定义 git 地址，多个源用空格分隔");
        customSourceInput.setSingleLine(false);
        customSourceInput.setMinLines(2);
        customSourceInput.setVisibility(View.GONE);
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        customLp.setMargins(0, dp(8), 0, 0);
        settingsCard.addView(customSourceInput, customLp);

        openBrowserAfterStart = new CheckBox(activity);
        openBrowserAfterStart.setText("启动后打开浏览器");
        openBrowserAfterStart.setChecked(true);
        SillyUI.styleCheckBox(activity, openBrowserAfterStart);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.setMargins(0, dp(12), 0, dp(2));
        settingsCard.addView(openBrowserAfterStart, cbLp);

        overlay.addView(settingsCard);

        // SillyTavern server/security configuration card.
        LinearLayout securityCard = SillyUI.card(activity, "SillyTavern 服务设置");
        TextView securityHint = new TextView(activity);
        securityHint.setText("对应 config.yaml；保存后会自动停止并重新启动服务。");
        securityHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        securityHint.setTextColor(SillyUI.TEXT_SECONDARY);
        securityCard.addView(securityHint);

        TextView securityNotice = new TextView(activity);
        securityNotice.setText("注意事项\n"
            + "• 开启“允许局域网访问”后，至少保留一项安全保护：IP 白名单、多用户模式或基础认证；全部关闭会导致 SillyTavern 拒绝启动。\n"
            + "• 关闭白名单模式不会删除已保存的 IP 列表，日志仍可能显示 whitelist=...；只有开关开启时才会校验。\n"
            + "• 日志中的 private request filter 是独立的 SSRF 防护警告，不等同于 IP 白名单。\n"
            + "• 基础认证账号密码来自 config.yaml 的 basicAuthUser；开启后可在下方编辑用户名和密码。密码留空会保留现有密码，保存时仅短暂写入应用私有临时文件，执行后立即删除。");
        securityNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        securityNotice.setTextColor(SillyUI.TEXT_PRIMARY);
        securityNotice.setPadding(dp(12), dp(10), dp(12), dp(10));
        securityNotice.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        LinearLayout.LayoutParams noticeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeLp.setMargins(0, dp(8), 0, dp(8));
        securityCard.addView(securityNotice, noticeLp);

        configPortInput = styledInput();
        configPortInput.setText(portInput.getText().toString());
        configPortInput.setHint("例如 8001");
        configPortInput.setSingleLine(true);
        configPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        configPortInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        securityCard.addView(verticalField("启动端口 (port)", configPortInput));
        portInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (portUpdating || configPortInput == null) return;
                String value = s.toString();
                if (!value.equals(configPortInput.getText().toString())) {
                    portUpdating = true;
                    configPortInput.setText(value);
                    portUpdating = false;
                }
                refreshLanSharing();
            }
        });
        configPortInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String value = s.toString();
                if (!portUpdating && portInput != null && !value.equals(portInput.getText().toString())) {
                    portUpdating = true;
                    portInput.setText(value);
                    portUpdating = false;
                }
                if (!configUpdating) configDirty = true;
                refreshLanSharing();
            }
        });

        userAccountsEnabled = configCheckBox("多用户模式 (enableUserAccounts)");
        discreetLoginEnabled = configCheckBox("隐藏登录用户列表 (enableDiscreetLogin)");
        whitelistModeEnabled = configCheckBox("IP 白名单模式 (whitelistMode)");
        listenEnabled = configCheckBox("允许局域网访问 (listen)");
        basicAuthEnabled = configCheckBox("启用基础认证 (basicAuthMode)");
        perUserBasicAuthEnabled = configCheckBox("按用户使用基础认证 (perUserBasicAuth)");
        securityCard.addView(userAccountsEnabled);
        securityCard.addView(discreetLoginEnabled);
        securityCard.addView(listenEnabled);
        securityCard.addView(basicAuthEnabled);

        authUsernameInput = styledInput();
        authUsernameInput.setHint("基础认证用户名");
        authUsernameInput.setSingleLine(true);
        authUsernameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!configUpdating) configDirty = true;
            }
        });
        securityCard.addView(verticalField("基础认证用户名 (basicAuthUser.username)", authUsernameInput));

        authPasswordInput = styledInput();
        authPasswordInput.setHint("留空保持现有密码");
        authPasswordInput.setSingleLine(true);
        authPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        authPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        authPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!configUpdating) configDirty = true;
            }
        });
        securityCard.addView(verticalField("基础认证密码 (basicAuthUser.password)", authPasswordInput));

        securityCard.addView(perUserBasicAuthEnabled);
        // Keep the whitelist field directly under its switch so it expands
        // and collapses with the setting instead of appearing at the bottom.
        securityCard.addView(whitelistModeEnabled);

        whitelistInput = styledInput();
        whitelistInput.setHint("白名单 IP，用逗号分隔，例如 127.0.0.1,192.168.1.0/24");
        whitelistInput.setSingleLine(false);
        whitelistInput.setMinLines(2);
        whitelistInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) configDirty = true;
        });
        whitelistInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!configUpdating) {
                    configWhitelist = s.toString().trim();
                    configDirty = true;
                }
            }
        });
        LinearLayout.LayoutParams whitelistLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        whitelistLp.setMargins(0, dp(6), 0, 0);
        securityCard.addView(whitelistInput, whitelistLp);
        applyConfigButton = addButton("保存并重启服务", this::applyServerConfigAndRestart, SillyUI.BTN_PRIMARY);
        addFullButton(securityCard, applyConfigButton);
        Button fullConfigButton = addButton("编辑完整 config.yaml（全部字段）",
            () -> activity.startActivity(new Intent(activity, STConfigActivity.class)), SillyUI.BTN_SECONDARY);
        addFullButton(securityCard, fullConfigButton);
        configContent.addView(securityCard);

        // Actions card.
        LinearLayout actionsCard = SillyUI.card(activity, "操作");
        installButton = addButton("一键安装 / 修复", () -> runCommand("install"), SillyUI.BTN_PRIMARY);
        addFullButton(actionsCard, installButton);
        updateButton = addButton("更新 SillyTavern", () -> runCommand("update"), SillyUI.BTN_SECONDARY);
        addFullButton(actionsCard, updateButton);

        LinearLayout rowA = buttonRow();
        startButton = addButton("启动", () -> runCommand("start"), SillyUI.BTN_PRIMARY);
        stopButton = addButton("停止", () -> runCommand("stop"), SillyUI.BTN_DANGER);
        rowA.addView(startButton, rowParams(true));
        rowA.addView(stopButton, rowParams(false));
        actionsCard.addView(rowA);

        browserButton = addButton("内置浏览器", this::openBrowser, SillyUI.BTN_SECONDARY);
        addFullButton(actionsCard, browserButton);

        Button refreshBtn = addButton("刷新状态", this::refreshStatus, SillyUI.BTN_GHOST);
        addFullButton(actionsCard, refreshBtn);

        overlay.addView(actionsCard);

        userAccountsEnabled.setChecked(false);
        discreetLoginEnabled.setChecked(false);
        whitelistModeEnabled.setChecked(true);
        listenEnabled.setChecked(false);
        basicAuthEnabled.setChecked(false);
        perUserBasicAuthEnabled.setChecked(false);
        whitelistInput.setText("::1,127.0.0.1");
        configUserAccounts = false;
        configDiscreetLogin = false;
        configWhitelistMode = true;
        configListen = false;
        configBasicAuth = false;
        configPerUserBasicAuth = false;
        configWhitelist = "::1,127.0.0.1";
        configDirty = false;
        updateBasicAuthVisibility();
        updateWhitelistVisibility();
        refreshLanSharing();
        ensureScripts();
        refreshStatusFromFileOnly();
        renderDependencyStatus(readTextFile(new File(scriptsDir(), STMainActivity.DEPS_STATUS_FILE_NAME)));
        handler.postDelayed(this::refreshStatus, 500);
        refreshConfigAvailability();
        ensureStoragePermission();
        selectMainTab(0);
        homeScrollView.post(() -> {
            setTerminalToolbarVisible(false);
            homeScrollView.requestFocus();
            hideKeyboard(homeScrollView);
            handler.postDelayed(() -> hideKeyboard(homeScrollView), 250);
            handler.postDelayed(() -> hideKeyboard(homeScrollView), 700);
        });
        return root;
    }

    public void setRestoreButton(Button restoreButton) {
        this.restoreButton = restoreButton;
        if (restoreButton != null) {
            boolean panelShown = overlayRoot != null && overlayRoot.getVisibility() == View.VISIBLE;
            restoreButton.setVisibility(panelShown ? View.GONE : View.VISIBLE);
            restoreButton.setOnClickListener(v -> {
                selectMainTab(0);
                show();
            });
        }
    }

    public void show() {
        if (overlayRoot != null) overlayRoot.setVisibility(View.VISIBLE);
        setTerminalToolbarVisible(false);
        if (restoreButton != null) restoreButton.setVisibility(View.GONE);
        if (overlay != null) {
            overlay.post(() -> {
                overlay.requestFocus();
                hideKeyboard(overlay);
                handler.postDelayed(() -> hideKeyboard(overlay), 250);
            });
        }
    }

    public void hide() {
        if (overlayRoot != null) overlayRoot.setVisibility(View.GONE);
        setTerminalToolbarVisible(activity.getPreferences().shouldShowTerminalToolbar());
        if (restoreButton != null) restoreButton.setVisibility(View.VISIBLE);
    }

    private View buildConfigUnavailableView() {
        LinearLayout card = SillyUI.card(activity, null);
        TextView title = new TextView(activity);
        title.setText("SillyTavern 未安装");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(SillyUI.TEXT_PRIMARY);
        card.addView(title);

        TextView message = new TextView(activity);
        message.setText("请等待 SillyTavern 安装完毕后再修改配置。\n"
            + "安装过程中配置文件可能尚未生成或仍在写入，暂时无法打开配置项。");
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        message.setTextColor(SillyUI.TEXT_SECONDARY);
        message.setPadding(0, dp(10), 0, dp(6));
        card.addView(message);

        Button homeButton = addButton("返回主页安装", () -> selectMainTab(0), SillyUI.BTN_PRIMARY);
        addFullButton(card, homeButton);
        return card;
    }

    private void refreshConfigAvailability() {
        if (configUnavailableView == null || configContent == null) return;
        boolean ready = isSillyTavernInstallComplete();
        configUnavailableView.setVisibility(ready ? View.GONE : View.VISIBLE);
        configContent.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (!ready) {
            configDirty = false;
            pendingConfigCommandArgs = null;
        }
    }

    private boolean isSillyTavernInstallComplete() {
        File appDir = new File(TermuxConstants.TERMUX_HOME_DIR, "SillyTavern");
        if (!new File(appDir, "package.json").isFile()
            || !new File(appDir, "start.sh").isFile()
            || !new File(appDir, "config.yaml").isFile()) return false;

        String status = readTextFile(new File(scriptsDir(), STMainActivity.STATUS_FILE_NAME)).trim();
        return !status.startsWith("Installing /")
            && !status.startsWith("Install failed:")
            && !status.startsWith("Source install failed");
    }

    private void setTerminalToolbarVisible(boolean visible) {
        View toolbar = activity.getTerminalToolbarViewPager();
        if (toolbar != null) toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void onServiceConnected() {
        ensureScripts();
        if (pendingAction != null) {
            String action = pendingAction;
            pendingAction = null;
            pendingServiceRetries = 0;
            handler.post(() -> runCommand(action));
        } else {
            refreshStatus();
        }
    }

    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        activity.requestPermissions(new String[]{
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, 1001);
    }

    private ScrollView createPageScrollView() {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scrollView.setBackgroundColor(Color.rgb(244, 246, 250));
        return scrollView;
    }

    private LinearLayout buildLogPage() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(20), dp(18), dp(22));
        page.setBackground(SillyUI.roundRect(SillyUI.BG_CARD, dp(22), 0x99DDE5F2, 1f));

        TextView title = new TextView(activity);
        title.setText("\u8fd0\u884c\u65e5\u5fd7");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(SillyUI.TEXT_PRIMARY);
        page.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout logTabs = buttonRow();
        logTabButtons = new Button[2];
        logTabButtons[0] = addButton("\u63a7\u5236\u65e5\u5fd7", () -> {
            selectedLog = 0;
            refreshInlineLog();
        }, SillyUI.BTN_GHOST);
        logTabButtons[1] = addButton("SillyTavern \u65e5\u5fd7", () -> {
            selectedLog = 1;
            refreshInlineLog();
        }, SillyUI.BTN_GHOST);
        logTabs.addView(logTabButtons[0], rowParams(true));
        logTabs.addView(logTabButtons[1], rowParams(false));
        LinearLayout.LayoutParams logTabsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logTabsLp.setMargins(0, dp(10), 0, dp(6));
        page.addView(logTabs, logTabsLp);

        LinearLayout logActions = buttonRow();
        Button refreshLogButton = addButton("\u5237\u65b0", this::refreshInlineLog, SillyUI.BTN_SECONDARY);
        Button clearLogButton = addButton("\u6e05\u7a7a\u5f53\u524d", this::clearInlineLog, SillyUI.BTN_DANGER);
        logActions.addView(refreshLogButton, rowParams(true));
        logActions.addView(clearLogButton, rowParams(false));
        page.addView(logActions);

        logView = new TextView(activity);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setTextColor(SillyUI.TEXT_PRIMARY);
        logView.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        logView.setPadding(dp(14), dp(14), dp(14), dp(14));
        logView.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        logTextScroll = new ScrollView(activity);
        logTextScroll.setFillViewport(true);
        logTextScroll.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        logTextScroll.addView(logView, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(900));
        logLp.setMargins(0, dp(10), 0, 0);
        page.addView(logTextScroll, logLp);
        updateLogTabStyles();
        return page;
    }

    private void selectMainTab(int tab) {
        selectedTab = Math.max(0, Math.min(4, tab));
        if (mainTabButtons != null) updateMainTabStyles();
        if (overlayRoot != null) overlayRoot.setVisibility(View.VISIBLE);
        if (selectedTab == 1) refreshConfigAvailability();
        if (selectedTab == 2) {
            // Leave the real terminal visible below the fixed tab bar. The
            // transparent page host does not replace or recreate its session.
            if (pageHost != null) pageHost.setVisibility(View.INVISIBLE);
            setTerminalToolbarVisible(activity.getPreferences().shouldShowTerminalToolbar());
            if (restoreButton != null) restoreButton.setVisibility(View.GONE);
            return;
        }
        if (pageHost != null) {
            pageHost.setVisibility(View.VISIBLE);
            pageHost.getChildAt(0).setVisibility(selectedTab == 0 ? View.VISIBLE : View.GONE);
            pageHost.getChildAt(1).setVisibility(selectedTab == 1 ? View.VISIBLE : View.GONE);
            pageHost.getChildAt(2).setVisibility(selectedTab == 3 ? View.VISIBLE : View.GONE);
            pageHost.getChildAt(3).setVisibility(selectedTab == 4 ? View.VISIBLE : View.GONE);
        }
        setTerminalToolbarVisible(false);
        if (selectedTab == 3) refreshInlineLog();
        if (selectedTab == 4 && characterGallery != null) characterGallery.refresh();
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return characterGallery != null && characterGallery.onActivityResult(requestCode, resultCode, data);
    }

    private void updateMainTabStyles() {
        for (int i = 0; i < mainTabButtons.length; i++) {
            boolean active = i == selectedTab;
            mainTabButtons[i].setBackground(SillyUI.roundRect(active ? SillyUI.ACCENT : SillyUI.BG_CARD,
                dp(10), active ? 0 : SillyUI.DIVIDER, active ? 0 : 1f));
            mainTabButtons[i].setTextColor(active ? Color.WHITE : SillyUI.TEXT_PRIMARY);
        }
    }

    private void updateLogTabStyles() {
        if (logTabButtons == null) return;
        for (int i = 0; i < logTabButtons.length; i++) {
            boolean active = i == selectedLog;
            logTabButtons[i].setBackground(SillyUI.roundRect(active ? SillyUI.ACCENT : SillyUI.BG_CARD,
                dp(10), active ? 0 : SillyUI.DIVIDER, active ? 0 : 1f));
            logTabButtons[i].setTextColor(active ? Color.WHITE : SillyUI.TEXT_PRIMARY);
        }
    }

    private void refreshInlineLog() {
        if (logView == null) return;
        File file = currentLogFile();
        String text = readTail(file, 512 * 1024);
        if (text.trim().isEmpty()) text = file.getAbsolutePath() + "\n\n\u6682\u65e0\u65e5\u5fd7";
        logView.setText(text);
        if (logTextScroll != null) logTextScroll.post(() -> logTextScroll.scrollTo(0, 0));
        updateLogTabStyles();
    }

    private void clearInlineLog() {
        try {
            File file = currentLogFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream ignored = new FileOutputStream(file, false)) {
            }
            refreshInlineLog();
        } catch (Exception e) {
            toast("\u6e05\u7a7a\u5931\u8d25: " + e.getMessage());
        }
    }

    private File currentLogFile() {
        String name = selectedLog == 0 ? STMainActivity.CONTROL_LOG_FILE_NAME : STMainActivity.SILLY_LOG_FILE_NAME;
        return new File(new File(TermuxConstants.TERMUX_HOME_DIR, STMainActivity.CONTROL_DIR_NAME), name);
    }

    private String readTail(File file, int maxBytes) {
        try {
            if (!file.exists()) return "";
            long length = file.length();
            int readLength = (int) Math.min(length, maxBytes);
            byte[] bytes = new byte[readLength];
            try (InputStream input = new java.io.FileInputStream(file)) {
                long skip = Math.max(0, length - readLength);
                while (skip > 0) {
                    long skipped = input.skip(skip);
                    if (skipped <= 0) break;
                    skip -= skipped;
                }
                int read = input.read(bytes);
                return read <= 0 ? "" : new String(bytes, 0, read);
            }
        } catch (Exception e) {
            return "\u8bfb\u53d6\u5931\u8d25: " + e.getMessage();
        }
    }

    // --- view helpers ---
    private Button addButton(String text, Runnable action, int type) {
        Button button = new Button(activity);
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        SillyUI.styleButton(activity, button, type);
        return button;
    }

    private void addFullButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        parent.addView(b, lp);
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams rowParams(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(first ? 0 : dp(6), 0, first ? dp(6) : 0, 0);
        return lp;
    }

    private EditText styledInput() {
        EditText e = new EditText(activity);
        SillyUI.styleInput(activity, e);
        return e;
    }

    private Switch configCheckBox(String text) {
        Switch sw = new Switch(activity);
        sw.setText(text);
        SillyUI.styleSwitch(activity, sw);
        sw.setOnClickListener(v -> {
            configDirty = true;
            boolean checked = sw.isChecked();
            if (sw == userAccountsEnabled) configUserAccounts = checked;
            else if (sw == discreetLoginEnabled) configDiscreetLogin = checked;
            else if (sw == whitelistModeEnabled) {
                configWhitelistMode = checked;
                updateWhitelistVisibility();
            }
            else if (sw == listenEnabled) {
                configListen = checked;
                refreshLanSharing();
            }
            else if (sw == basicAuthEnabled) {
                configBasicAuth = checked;
                updateBasicAuthVisibility();
            }
            else if (sw == perUserBasicAuthEnabled) configPerUserBasicAuth = checked;
        });
        return sw;
    }

    private void updateWhitelistVisibility() {
        if (whitelistInput != null) {
            whitelistInput.setVisibility(configWhitelistMode ? View.VISIBLE : View.GONE);
        }
    }

    private void updateBasicAuthVisibility() {
        int visibility = configBasicAuth ? View.VISIBLE : View.GONE;
        if (authUsernameInput != null && authUsernameInput.getParent() instanceof View) {
            View parent = (View) authUsernameInput.getParent();
            parent.setVisibility(visibility);
        }
        if (authPasswordInput != null && authPasswordInput.getParent() instanceof View) {
            View parent = (View) authPasswordInput.getParent();
            parent.setVisibility(visibility);
        }
    }

    private TextView labelView(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(SillyUI.TEXT_SECONDARY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout verticalField(String label, View field) {
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(labelView(label));
        if (field != null) col.addView(field, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private void applyServerConfig() {
        if (!validatePortInput()) return;
        if (!prepareBasicAuthCredentials()) return;
        if (whitelistInput.getText().toString().trim().isEmpty()) whitelistInput.setText("127.0.0.1");
        pendingConfigCommandArgs = configCommandArgs();
        configDirty = false;
        runCommand("config");
    }

    private void applyServerConfigAndRestart() {
        if (!validatePortInput()) return;
        if (!prepareBasicAuthCredentials()) return;
        if (whitelistInput.getText().toString().trim().isEmpty()) whitelistInput.setText("127.0.0.1");
        pendingConfigCommandArgs = configCommandArgs();
        configDirty = false;
        runCommand("config-restart");
    }

    private boolean validatePortInput() {
        String value = configPortInput == null ? portInput.getText().toString().trim()
            : configPortInput.getText().toString().trim();
        if (!isValidPort(value)) {
            toast("端口必须是 1-65535 的数字");
            return false;
        }
        setPortFields(value);
        return true;
    }

    private boolean prepareBasicAuthCredentials() {
        File credentialsFile = new File(scriptsDir(), BASIC_AUTH_CREDENTIALS_FILE_NAME);
        if (!configBasicAuth) {
            if (credentialsFile.exists()) credentialsFile.delete();
            return true;
        }
        String username = authUsernameInput == null ? "" : authUsernameInput.getText().toString().trim();
        String password = authPasswordInput == null ? "" : authPasswordInput.getText().toString();
        if (username.isEmpty()) {
            toast("启用基础认证时必须填写用户名");
            return false;
        }
        try {
            File parent = credentialsFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(credentialsFile, false)) {
                output.write((username + "\n" + password + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            credentialsFile.setReadable(false, false);
            credentialsFile.setReadable(true, true);
            return true;
        } catch (Exception e) {
            toast("保存基础认证凭据失败: " + e.getMessage());
            return false;
        }
    }

    private void runCommand(String action) {
        ensureScripts();
        copyDefaultConfigIfNeeded();
        TermuxService service = activity.getTermuxService();
        if (service == null) {
            waitForServiceAndRun(action);
            return;
        }
        boolean configAction = "config".equals(action) || "config-restart".equals(action);
        String requestedConfigArgs = configAction && pendingConfigCommandArgs != null
            ? pendingConfigCommandArgs : (configAction ? configCommandArgs() : null);
        String command = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " " + action + " " +
            quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());
        if (configAction) command += " " + requestedConfigArgs;

        if ("stop".equals(action)) {
            closeManagedSession(service);
            service.createTermuxTask(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
                new String[]{"-lc", command}, null, TermuxConstants.TERMUX_HOME_DIR_PATH);
        } else {
            if ("install".equals(action) || "start".equals(action) || "config-restart".equals(action)) closeManagedSession(service);
            String sessionName = ("install".equals(action) || "start".equals(action))
                ? SILLY_SESSION_NAME : SILLY_SESSION_NAME + " " + action;
            TermuxSession termuxSession = service.createTermuxSession(
                TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
                new String[]{"-lc", command}, null,
                TermuxConstants.TERMUX_HOME_DIR_PATH, false, sessionName);
            if (termuxSession == null || !isUsableSession(termuxSession.getTerminalSession())) {
                toast("无法创建 Termux 会话");
                return;
            }
            controlSession = termuxSession.getTerminalSession();
            service.setCurrentStoredTerminalSession(controlSession);
            activity.getTermuxTerminalSessionClient().setCurrentSession(controlSession);
        }
        if ("install".equals(action) || "update".equals(action)) hide();
        setButtonsEnabled(false);
        if (configAction) pendingConfigCommandArgs = null;
        statusView.setText("命令已发送：" + action);
        if (("start".equals(action) || "install".equals(action) || "config-restart".equals(action))
                && openBrowserAfterStart.isChecked()) {
            handler.postDelayed(() -> waitForBrowserReady(0), 1500);
        }
        handler.postDelayed(() -> {
            setButtonsEnabled(true);
            refreshStatus();
        }, 3000);
    }

    private void waitForServiceAndRun(String action) {
        pendingAction = action;
        pendingServiceRetries = 0;
        setButtonsEnabled(false);
        statusView.setText("Termux 服务正在启动，将自动继续：" + action);
        activity.ensureTermuxServiceReady();
        pollServiceAndRun(action);
    }

    private void pollServiceAndRun(String action) {
        handler.postDelayed(() -> {
            if (!action.equals(pendingAction)) return;
            if (activity.getTermuxService() != null) {
                pendingAction = null;
                pendingServiceRetries = 0;
                runCommand(action);
                return;
            }
            if (++pendingServiceRetries < 12) {
                activity.ensureTermuxServiceReady();
                pollServiceAndRun(action);
                return;
            }
            pendingAction = null;
            pendingServiceRetries = 0;
            setButtonsEnabled(true);
            statusView.setText("Termux 服务启动失败");
            toast("Termux 服务启动失败，请重试");
        }, 250);
    }

    private void closeManagedSession(TermuxService service) {
        TerminalSession session = controlSession;
        if (!isUsableSession(session)) {
            TermuxSession existing = service.getTermuxSessionForShellName(SILLY_SESSION_NAME);
            session = existing == null ? null : existing.getTerminalSession();
        }
        if (isUsableSession(session)) service.removeTermuxSession(session);
        controlSession = null;
    }

    private boolean isUsableSession(TerminalSession session) {
        return session != null && session.isRunning();
    }

    private void refreshStatus() {
        ensureScripts();
        TermuxService service = activity.getTermuxService();
        if (service != null) {
            String command = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " status " +
                quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());
            service.createTermuxTask(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
                new String[]{"-lc", command}, null, TermuxConstants.TERMUX_HOME_DIR_PATH);
            String configCommand = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " config-status " +
                quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());
            service.createTermuxTask(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
                new String[]{"-lc", configCommand}, null, TermuxConstants.TERMUX_HOME_DIR_PATH);
        }
        handler.postDelayed(() -> {
            refreshStatusFromFileOnly();
            renderDependencyStatus(readTextFile(new File(scriptsDir(), STMainActivity.DEPS_STATUS_FILE_NAME)));
            applyConfigStatus(readTextFile(new File(scriptsDir(), STMainActivity.CONFIG_STATUS_FILE_NAME)));
        }, 1200);
    }

    private void refreshStatusFromFileOnly() {
        statusView.setText(toStatusSummary(readTextFile(new File(scriptsDir(), STMainActivity.STATUS_FILE_NAME))));
        refreshConfigAvailability();
        refreshLanSharing();
    }

    private void openBrowser() {
        Intent intent = new Intent(activity, STBrowserActivity.class);
        intent.putExtra(STBrowserActivity.EXTRA_URL, "http://localhost:" + getPort());
        activity.startActivity(intent);
    }

    private void waitForBrowserReady(int attempt) {
        if (readTextFile(new File(scriptsDir(), STMainActivity.STATUS_FILE_NAME)).startsWith("Running at http://")) {
            openBrowser();
            return;
        }
        if (attempt < 90) handler.postDelayed(() -> waitForBrowserReady(attempt + 1), 1000);
    }

    private void setButtonsEnabled(boolean enabled) {
        installButton.setEnabled(enabled);
        updateButton.setEnabled(enabled);
        startButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        browserButton.setEnabled(enabled);
        applyConfigButton.setEnabled(enabled);
    }

    private String configCommandArgs() {
        return (configUserAccounts ? "1" : "0") + " " +
            (configDiscreetLogin ? "1" : "0") + " " +
            (configWhitelistMode ? "1" : "0") + " " +
            (configListen ? "1" : "0") + " " +
            (configBasicAuth ? "1" : "0") + " " +
            (configPerUserBasicAuth ? "1" : "0") + " " +
            quote(effectiveWhitelist());
    }

    /** Add the current Wi-Fi subnet without discarding user-entered entries. */
    private String effectiveWhitelist() {
        String value = configWhitelist == null ? "" : configWhitelist.trim();
        if (value.isEmpty()) value = "127.0.0.1";
        if (configListen && configWhitelistMode) {
            String cidr = getLanCidr();
            if (!cidr.isEmpty() && !containsWhitelistEntry(value, cidr)) value += "," + cidr;
        }
        return value;
    }

    private boolean containsWhitelistEntry(String value, String entry) {
        for (String item : value.split(",")) if (entry.equals(item.trim())) return true;
        return false;
    }

    private void refreshLanSharing() {
        if (lanAddressView == null) return;
        String ip = getLanIpv4();
        if (ip.isEmpty()) {
            lanAddressView.setText("未检测到 Wi-Fi 地址");
            if (copyLanAddressButton != null) copyLanAddressButton.setEnabled(false);
            return;
        }
        String url = "http://" + ip + ":" + getPort() + "/";
        String state = configListen ? "电脑访问地址：\n" : "当前未开启局域网访问，开启后可访问：\n";
        lanAddressView.setText(state + url + "\n当前网段：" + getLanCidr());
        if (copyLanAddressButton != null) copyLanAddressButton.setEnabled(true);
    }

    private void copyLanAddress() {
        String ip = getLanIpv4();
        if (ip.isEmpty()) {
            toast("未检测到 Wi-Fi 地址");
            return;
        }
        String url = "http://" + ip + ":" + getPort() + "/";
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
            activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SillyTavern 局域网地址", url));
            toast("已复制：" + url);
        }
    }

    private InterfaceAddress findLanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            InterfaceAddress fallback = null;
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) continue;
                boolean preferred = "wlan0".equalsIgnoreCase(network.getName())
                    || network.getName().toLowerCase(java.util.Locale.ROOT).contains("wifi");
                for (InterfaceAddress candidate : network.getInterfaceAddresses()) {
                    if (!(candidate.getAddress() instanceof Inet4Address)) continue;
                    Inet4Address address = (Inet4Address) candidate.getAddress();
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                    if (preferred) return candidate;
                    if (fallback == null) fallback = candidate;
                }
            }
            return fallback;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getLanIpv4() {
        InterfaceAddress interfaceAddress = findLanAddress();
        return interfaceAddress == null ? "" : interfaceAddress.getAddress().getHostAddress();
    }

    private String getLanCidr() {
        InterfaceAddress interfaceAddress = findLanAddress();
        if (interfaceAddress == null || interfaceAddress.getNetworkPrefixLength() <= 0
            || interfaceAddress.getNetworkPrefixLength() > 32) return "";
        byte[] bytes = interfaceAddress.getAddress().getAddress();
        int prefix = interfaceAddress.getNetworkPrefixLength();
        int mask = prefix == 0 ? 0 : (int) (0xffffffffL << (32 - prefix));
        int ip = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16)
            | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        int network = ip & mask;
        return ((network >>> 24) & 0xff) + "." + ((network >>> 16) & 0xff) + "."
            + ((network >>> 8) & 0xff) + "." + (network & 0xff) + "/" + prefix;
    }

    private void applyConfigStatus(String rawStatus) {
        if (configDirty) return;
        if (rawStatus == null || rawStatus.trim().isEmpty()) return;
        Map<String, String> values = new HashMap<>();
        for (String line : rawStatus.split("\\r?\\n")) {
            int index = line.indexOf('=');
            if (index > 0) values.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
        }
        configUpdating = true;
        if (values.containsKey("port") && isValidPort(values.get("port"))) {
            setPortFields(values.get("port"));
        }
        if (values.containsKey("enableUserAccounts")) {
            configUserAccounts = "1".equals(values.get("enableUserAccounts"));
            userAccountsEnabled.setChecked(configUserAccounts);
        }
        if (values.containsKey("enableDiscreetLogin")) {
            configDiscreetLogin = "1".equals(values.get("enableDiscreetLogin"));
            discreetLoginEnabled.setChecked(configDiscreetLogin);
        }
        if (values.containsKey("whitelistMode")) {
            configWhitelistMode = "1".equals(values.get("whitelistMode"));
            whitelistModeEnabled.setChecked(configWhitelistMode);
        }
        if (values.containsKey("listen")) {
            configListen = "1".equals(values.get("listen"));
            listenEnabled.setChecked(configListen);
        }
        if (values.containsKey("basicAuthMode")) {
            configBasicAuth = "1".equals(values.get("basicAuthMode"));
            basicAuthEnabled.setChecked(configBasicAuth);
        }
        if (values.containsKey("perUserBasicAuth")) {
            configPerUserBasicAuth = "1".equals(values.get("perUserBasicAuth"));
            perUserBasicAuthEnabled.setChecked(configPerUserBasicAuth);
        }
        if (values.containsKey("whitelist") && !values.get("whitelist").isEmpty()) {
            configWhitelist = values.get("whitelist");
            whitelistInput.setText(configWhitelist);
        }
        if (values.containsKey("basicAuthUsernameB64") && authUsernameInput != null) {
            try {
                String username = new String(Base64.decode(values.get("basicAuthUsernameB64"), Base64.DEFAULT),
                    java.nio.charset.StandardCharsets.UTF_8);
                authUsernameInput.setText(username);
            } catch (Exception ignored) {
            }
        }
        if (authPasswordInput != null) authPasswordInput.setText("");
        updateBasicAuthVisibility();
        updateWhitelistVisibility();
        configUpdating = false;
        refreshLanSharing();
    }

    private boolean isValidPort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            return port >= 1 && port <= 65535;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void setPortFields(String value) {
        if (!isValidPort(value)) return;
        portUpdating = true;
        portInput.setText(value);
        if (configPortInput != null) configPortInput.setText(value);
        portUpdating = false;
    }

    private String getBranch() {
        String branch = branchInput.getText().toString().trim();
        return branch.isEmpty() ? "release" : branch;
    }

    private String getPort() {
        String port = configPortInput != null ? configPortInput.getText().toString().trim()
            : portInput.getText().toString().trim();
        return port.isEmpty() ? "8001" : port;
    }

    private String getSourceSpec() {
        int mode = sourceSpinner == null ? 0 : sourceSpinner.getSelectedItemPosition();
        if (mode == 0) return AUTO_SOURCE;
        if (mode == 1) {
            String custom = customSourceInput.getText().toString().trim();
            return custom.isEmpty() ? AUTO_SOURCE : custom;
        }
        return AUTO_SOURCE;
    }

    private void ensureScripts() {
        try {
            File dir = scriptsDir();
            if (!dir.exists()) dir.mkdirs();
            File script = new File(dir, "sillyctl.sh");
            try (FileWriter writer = new FileWriter(script, false)) {
                writer.write(getControlScript());
            }
            script.setExecutable(true, true);
        } catch (Exception e) {
            toast("cannot write scripts: " + e.getMessage());
        }
    }

    private void copyDefaultConfigIfNeeded() {
        File config = new File(scriptsDir(), DEFAULT_CONFIG_FILE_NAME);
        if (config.exists() && config.length() > 0) return;
        try (InputStream input = activity.getAssets().open(DEFAULT_CONFIG_ASSET_NAME);
             FileOutputStream output = new FileOutputStream(config, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        } catch (Exception e) {
            toast("cannot copy default config: " + e.getMessage());
        }
    }

    private void renderDependencyStatus(String rawStatus) {
        if (dependencyList == null) return;
        dependencyList.removeAllViews();
        Map<String, String> status = new HashMap<>();
        if (rawStatus != null) {
            for (String line : rawStatus.split("\\r?\\n")) {
                int index = line.indexOf('=');
                if (index > 0) status.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
            }
        }
        addDependencyRow("Termux bootstrap", "termux", status);
        addDependencyRow("git", "git", status);
        addDependencyRow("node", "node", status);
        addDependencyRow("npm", "npm", status);
        addDependencyRow("SillyTavern directory", "silly_dir", status);
        addDependencyRow("package.json", "package_json", status);
        addDependencyRow("start.sh", "start_sh", status);
        addDependencyRow("config.yaml", "config_yaml", status);
        addDependencyRow("data directory", "data_dir", status);
    }

    private void addDependencyRow(String label, String key, Map<String, String> status) {
        String value = status.get(key);
        boolean installed = "ok".equals(value);
        boolean known = value != null && !value.isEmpty();
        TextView statusLabel = new TextView(activity);
        statusLabel.setText(label);
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusLabel.setTextColor(!known ? SillyUI.TEXT_SECONDARY : SillyUI.TEXT_PRIMARY);

        TextView badge = new TextView(activity);
        badge.setText(installed ? "OK" : "NO");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setTextColor(installed ? SillyUI.SUCCESS : SillyUI.DANGER);
        badge.setPadding(dp(11), dp(4), dp(11), dp(4));
        badge.setBackground(SillyUI.roundRect(installed ? 0xFFEAFBF2 : 0xFFFFEEF1,
            dp(14), installed ? 0xFFC6F1D8 : 0xFFFFC3CC, 1f));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.addView(statusLabel, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(badge);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(2), 0, dp(2));
        dependencyList.addView(row, rowLp);
    }

    private String readTextFile(File file) {
        try {
            if (!file.exists()) return "";
            int length = (int) Math.min(file.length(), 128 * 1024);
            byte[] bytes = new byte[length];
            try (InputStream input = new java.io.FileInputStream(file)) {
                int read = input.read(bytes);
                return read <= 0 ? "" : new String(bytes, 0, read);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private String toStatusSummary(String status) {
        if (status == null) return "Not installed / not running";
        String cleaned = status.replace('\r', '\n').trim();
        int newline = cleaned.indexOf('\n');
        if (newline >= 0) cleaned = cleaned.substring(0, newline).trim();
        if (cleaned.isEmpty()) return "Not installed / not running";
        if (cleaned.length() > 140) cleaned = cleaned.substring(0, 140) + "...";
        return cleaned;
    }

    private File scriptsDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, STMainActivity.CONTROL_DIR_NAME);
    }

    private String quote(String value) {
        if (value == null) value = "";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void hideKeyboard(View view) {
        activity.getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View current = activity.getCurrentFocus();
        if (current != null && current != view) current.clearFocus();
        if (view != null) view.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View decor = activity.getWindow().getDecorView();
            imm.hideSoftInputFromWindow(decor.getWindowToken(), 0);
        }
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }

    private String getControlScript() {
        return STMainActivity.getControlScript();
    }
}
