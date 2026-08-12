package com.termux.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import java.util.HashMap;
import java.util.Map;

public class SillyHomeOverlay {

    private static final String GITEE_SOURCE = "https://gitee.com/HEYD66/SillyTavern.git";
    private static final String OFFICIAL_SOURCE = "https://github.com/SillyTavern/SillyTavern";
    private static final String AUTO_SOURCE = GITEE_SOURCE + " " + OFFICIAL_SOURCE;
    private static final String DEFAULT_CONFIG_ASSET_NAME = "default-config.yaml";
    private static final String DEFAULT_CONFIG_FILE_NAME = "default-config.yaml";
    private static final String SILLY_SESSION_NAME = "SillyTavern";

    private final TermuxActivity activity;
    private final Handler handler = new Handler();
    private LinearLayout overlay;
    private ScrollView overlayScrollView;
    private LinearLayout dependencyList;
    private TextView statusView;
    private EditText branchInput;
    private EditText portInput;
    private Spinner sourceSpinner;
    private EditText customSourceInput;
    private CheckBox openBrowserAfterStart;
    private Button installButton;
    private Button updateButton;
    private Button startButton;
    private Button stopButton;
    private Button browserButton;
    private Button logButton;
    private Button restoreButton;
    private TerminalSession controlSession;
    private String pendingAction;
    private int pendingServiceRetries;

    public SillyHomeOverlay(TermuxActivity activity) {
        this.activity = activity;
    }

    public View build() {
        ScrollView scrollView = new ScrollView(activity);
        overlayScrollView = scrollView;
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        // The control panel must be visually independent from the terminal.
        // A transparent root let terminal text and the extra-keys bar bleed
        // through the cards, which also made the UI look incorrectly scaled.
        scrollView.setBackgroundColor(Color.rgb(244, 246, 250));
        scrollView.setPadding(dp(10), dp(10), dp(10), dp(16));
        overlay = new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setPadding(dp(18), dp(20), dp(18), dp(22));
        overlay.setBackground(SillyUI.roundRect(SillyUI.BG_CARD, dp(22), 0x99DDE5F2, 1f));
        scrollView.addView(overlay, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        portInput.setText("8000");
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

        LinearLayout rowB = buttonRow();
        browserButton = addButton("内置浏览器", this::openBrowser, SillyUI.BTN_SECONDARY);
        Button showTerminalBtn = addButton("显示终端", this::hide, SillyUI.BTN_SECONDARY);
        rowB.addView(browserButton, rowParams(true));
        rowB.addView(showTerminalBtn, rowParams(false));
        actionsCard.addView(rowB);

        LinearLayout rowC = buttonRow();
        logButton = addButton("查看日志", () -> activity.startActivity(new Intent(activity, STLogActivity.class)), SillyUI.BTN_GHOST);
        Button refreshBtn = addButton("刷新状态", this::refreshStatus, SillyUI.BTN_GHOST);
        rowC.addView(logButton, rowParams(true));
        rowC.addView(refreshBtn, rowParams(false));
        actionsCard.addView(rowC);

        overlay.addView(actionsCard);

        ensureScripts();
        refreshStatusFromFileOnly();
        renderDependencyStatus(readTextFile(new File(scriptsDir(), STMainActivity.DEPS_STATUS_FILE_NAME)));
        handler.postDelayed(this::refreshStatus, 500);
        ensureStoragePermission();
        scrollView.post(() -> {
            setTerminalToolbarVisible(false);
            scrollView.requestFocus();
            hideKeyboard(scrollView);
            handler.postDelayed(() -> hideKeyboard(scrollView), 250);
            handler.postDelayed(() -> hideKeyboard(scrollView), 700);
        });
        return scrollView;
    }

    public void setRestoreButton(Button restoreButton) {
        this.restoreButton = restoreButton;
        if (restoreButton != null) {
            boolean panelShown = overlayScrollView != null && overlayScrollView.getVisibility() == View.VISIBLE;
            restoreButton.setVisibility(panelShown ? View.GONE : View.VISIBLE);
            restoreButton.setOnClickListener(v -> show());
        }
    }

    public void show() {
        if (overlayScrollView != null) overlayScrollView.setVisibility(View.VISIBLE);
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
        if (overlayScrollView != null) overlayScrollView.setVisibility(View.GONE);
        setTerminalToolbarVisible(activity.getPreferences().shouldShowTerminalToolbar());
        if (restoreButton != null) restoreButton.setVisibility(View.VISIBLE);
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

    private void runCommand(String action) {
        ensureScripts();
        copyDefaultConfigIfNeeded();
        TermuxService service = activity.getTermuxService();
        if (service == null) {
            waitForServiceAndRun(action);
            return;
        }
        String command = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " " + action + " " +
            quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());

        if ("stop".equals(action)) {
            closeManagedSession(service);
            service.createTermuxTask(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
                new String[]{"-lc", command}, null, TermuxConstants.TERMUX_HOME_DIR_PATH);
        } else {
            if ("install".equals(action) || "start".equals(action)) closeManagedSession(service);
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
        statusView.setText("命令已发送：" + action);
        if (("start".equals(action) || "install".equals(action))
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
        }
        handler.postDelayed(() -> {
            refreshStatusFromFileOnly();
            renderDependencyStatus(readTextFile(new File(scriptsDir(), STMainActivity.DEPS_STATUS_FILE_NAME)));
        }, 1200);
    }

    private void refreshStatusFromFileOnly() {
        statusView.setText(toStatusSummary(readTextFile(new File(scriptsDir(), STMainActivity.STATUS_FILE_NAME))));
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
        logButton.setEnabled(enabled);
    }

    private String getBranch() {
        String branch = branchInput.getText().toString().trim();
        return branch.isEmpty() ? "release" : branch;
    }

    private String getPort() {
        String port = portInput.getText().toString().trim();
        return port.isEmpty() ? "8000" : port;
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
