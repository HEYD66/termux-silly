package com.termux.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class STMainActivity extends AppCompatActivity {

    static final String CONTROL_DIR_NAME = ".termux-silly";
    static final String CONTROL_LOG_FILE_NAME = "control.log";
    static final String SILLY_LOG_FILE_NAME = "silly.log";
    static final String STATUS_FILE_NAME = "status.txt";
    static final String DEPS_STATUS_FILE_NAME = "deps.status";

    private static final String PREFS_NAME = "sillytavern_launcher";
    private static final String KEY_BRANCH = "branch";
    private static final String KEY_PORT = "port";
    private static final String KEY_SOURCE_MODE = "source_mode";
    private static final String KEY_CUSTOM_SOURCE = "custom_source";
    private static final String KEY_OPEN_BROWSER = "open_browser_after_start";

    private static final String GITEE_SOURCE = "https://gitee.com/HEYD66/SillyTavern.git";
    private static final String OFFICIAL_SOURCE = "https://github.com/SillyTavern/SillyTavern";
    private static final String AUTO_SOURCE = GITEE_SOURCE + " " + OFFICIAL_SOURCE;
    private static final String DEFAULT_CONFIG_ASSET_NAME = "default-config.yaml";
    private static final String DEFAULT_CONFIG_FILE_NAME = "default-config.yaml";

    private EditText branchInput;
    private EditText portInput;
    private Spinner sourceSpinner;
    private EditText customSourceInput;
    private CheckBox openBrowserAfterStart;
    private TextView statusView;
    private LinearLayout dependencyList;
    private Button installButton;
    private Button updateButton;
    private Button startButton;
    private Button stopButton;
    private Button browserButton;
    private Button terminalButton;
    private Button logButton;

    private SharedPreferences preferences;
    private final Handler handler = new Handler();
    private TermuxService termuxService;
    private boolean serviceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            termuxService = ((TermuxService.LocalBinder) service).service;
            serviceBound = true;
            refreshStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            termuxService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        buildLayout();
        loadSettings();
        ensureScripts();
        startAndBindTermuxService();
        refreshStatusFromFileOnly();
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }

    private void buildLayout() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("SillyTavern");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(8), 0, dp(12));
        root.addView(statusView);

        TextView depsTitle = new TextView(this);
        depsTitle.setText("Environment status");
        depsTitle.setTextSize(16);
        depsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        depsTitle.setPadding(0, dp(6), 0, dp(4));
        root.addView(depsTitle);

        dependencyList = new LinearLayout(this);
        dependencyList.setOrientation(LinearLayout.VERTICAL);
        dependencyList.setPadding(0, 0, 0, dp(10));
        root.addView(dependencyList);
        renderDependencyStatus(readTextFile(new File(scriptsDir(), DEPS_STATUS_FILE_NAME)));

        LinearLayout settingsRow = new LinearLayout(this);
        settingsRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(settingsRow);

        branchInput = new EditText(this);
        branchInput.setHint("branch");
        branchInput.setSingleLine(true);
        branchInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        settingsRow.addView(branchInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        portInput = new EditText(this);
        portInput.setHint("port");
        portInput.setSingleLine(true);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        settingsRow.addView(portInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView sourceLabel = new TextView(this);
        sourceLabel.setText("源码源");
        sourceLabel.setTextSize(14);
        sourceLabel.setPadding(0, dp(12), 0, 0);
        root.addView(sourceLabel);

        sourceSpinner = new Spinner(this);
        String[] sourceOptions = new String[]{
            "Gitee 优先（GitHub 备用）",
            "自定义源"
        };
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, sourceOptions);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(sourceAdapter);
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (customSourceInput != null) customSourceInput.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(sourceSpinner);

        customSourceInput = new EditText(this);
        customSourceInput.setHint("自定义 git 地址，多个源用空格分隔");
        customSourceInput.setSingleLine(false);
        customSourceInput.setMinLines(2);
        customSourceInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(customSourceInput);

        openBrowserAfterStart = new CheckBox(this);
        openBrowserAfterStart.setText("启动后打开浏览器");
        root.addView(openBrowserAfterStart);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        root.addView(actions);

        installButton = addButton(actions, "一键安装 / 修复", v -> runManagedCommand("install"));
        updateButton = addButton(actions, "更新 SillyTavern", v -> runManagedCommand("update"));
        startButton = addButton(actions, "启动", v -> runManagedCommand("start"));
        stopButton = addButton(actions, "停止", v -> runManagedCommand("stop"));
        browserButton = addButton(actions, "打开内置浏览器", v -> openBrowser());
        terminalButton = addButton(actions, "打开 Termux 终端", v -> startActivity(new Intent(this, TermuxActivity.class)));
        logButton = addButton(actions, "查看日志", v -> startActivity(new Intent(this, STLogActivity.class)));
        addButton(actions, "刷新状态", v -> refreshStatus());

        setContentView(scrollView);
    }

    private Button addButton(LinearLayout parent, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void loadSettings() {
        branchInput.setText(preferences.getString(KEY_BRANCH, "release"));
        portInput.setText(preferences.getString(KEY_PORT, "8000"));
        int defaultSourceMode = 0;
        int sourceMode = preferences.getInt(KEY_SOURCE_MODE, defaultSourceMode);
        sourceSpinner.setSelection(sourceMode >= 0 && sourceMode <= 1 ? sourceMode : defaultSourceMode);
        customSourceInput.setText(preferences.getString(KEY_CUSTOM_SOURCE, AUTO_SOURCE));
        openBrowserAfterStart.setChecked(preferences.getBoolean(KEY_OPEN_BROWSER, true));
    }

    private void saveSettings() {
        String branch = branchInput.getText().toString().trim();
        String port = portInput.getText().toString().trim();
        String customSource = customSourceInput.getText().toString().trim();
        if (branch.isEmpty()) branch = "release";
        if (port.isEmpty()) port = "8000";
        if (customSource.isEmpty()) customSource = AUTO_SOURCE;
        preferences.edit()
            .putString(KEY_BRANCH, branch)
            .putString(KEY_PORT, port)
            .putInt(KEY_SOURCE_MODE, sourceSpinner.getSelectedItemPosition())
            .putString(KEY_CUSTOM_SOURCE, customSource)
            .putBoolean(KEY_OPEN_BROWSER, openBrowserAfterStart.isChecked())
            .apply();
    }

    private void runManagedCommand(String action) {
        saveSettings();
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            ensureScripts();
            setButtonsEnabled(false);
            new Thread(() -> {
                copyDefaultConfigIfNeeded();
                runOnUiThread(() -> dispatchTermuxSessionCommand(action));
            }).start();
        });
    }

    private void refreshStatus() {
        ensureScripts();
        refreshStatusFromFileOnly();
        refreshDependencyStatusFromFileOnly();
        dispatchStatusCheckTask();
    }

    private void refreshStatusFromFileOnly() {
        String status = readTextFile(new File(scriptsDir(), STATUS_FILE_NAME));
        statusView.setText(toStatusSummary(status));
    }

    private void refreshDependencyStatusFromFileOnly() {
        renderDependencyStatus(readTextFile(new File(scriptsDir(), DEPS_STATUS_FILE_NAME)));
    }

    private void dispatchStatusCheckTask() {
        if (termuxService == null) return;
        String command = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " status " +
            quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());
        termuxService.createTermuxTask(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
            new String[]{"-lc", command},
            null,
            TermuxConstants.TERMUX_HOME_DIR_PATH);
        handler.postDelayed(() -> {
            refreshStatusFromFileOnly();
            refreshDependencyStatusFromFileOnly();
        }, 1200);
    }

    private void openBrowser() {
        saveSettings();
        Intent intent = new Intent(this, STBrowserActivity.class);
        intent.putExtra(STBrowserActivity.EXTRA_URL, "http://localhost:" + getPort());
        startActivity(intent);
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

    private void setButtonsEnabled(boolean enabled) {
        installButton.setEnabled(enabled);
        updateButton.setEnabled(enabled);
        startButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        browserButton.setEnabled(enabled);
        terminalButton.setEnabled(enabled);
        logButton.setEnabled(enabled);
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
        try (InputStream input = getAssets().open(DEFAULT_CONFIG_ASSET_NAME);
             FileOutputStream output = new FileOutputStream(config, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        } catch (Exception e) {
            runOnUiThread(() -> toast("cannot copy default config: " + e.getMessage()));
        }
    }

    private File scriptsDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, CONTROL_DIR_NAME);
    }

    private void startAndBindTermuxService() {
        Intent serviceIntent = new Intent(this, TermuxService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void dispatchTermuxSessionCommand(String action) {
        if (termuxService == null) {
            toast("Termux service is not ready");
            startAndBindTermuxService();
            setButtonsEnabled(true);
            return;
        }
        String command = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " " + action + " " +
            quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());
        TermuxSession session = termuxService.createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
            new String[]{"-lc", command},
            null,
            TermuxConstants.TERMUX_HOME_DIR_PATH,
            false,
            "SillyTavern " + action);
        if (session != null) {
            termuxService.setCurrentStoredTerminalSession(session.getTerminalSession());
            statusView.setText("Sent to Termux session: " + action + "\nOpen Termux terminal to watch the process.");
        } else {
            statusView.setText("Failed to create Termux session: " + action);
            toast("Failed to create Termux session");
        }
        handler.postDelayed(() -> {
            setButtonsEnabled(true);
            refreshStatusFromFileOnly();
            if (("start".equals(action) || "install".equals(action))
                    && openBrowserAfterStart.isChecked()) waitForBrowserReady(0);
        }, 1500);
    }

    private void dispatchTermuxTask(String action) {
        if (termuxService == null) {
            toast("Termux 服务还没准备好");
            startAndBindTermuxService();
            setButtonsEnabled(true);
            return;
        }
        String command = quote(scriptsDir().getAbsolutePath() + "/sillyctl.sh") + " " + action + " " +
            quote(getBranch()) + " " + quote(getSourceSpec()) + " " + quote(getPort());
        termuxService.createTermuxTask(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
            new String[]{"-lc", command},
            null,
            TermuxConstants.TERMUX_HOME_DIR_PATH);
        statusView.setText("Task queued: " + action + "\n查看日志可跟踪进度");
        handler.postDelayed(() -> {
            setButtonsEnabled(true);
            refreshStatusFromFileOnly();
            if (("start".equals(action) || "install".equals(action))
                    && openBrowserAfterStart.isChecked()) waitForBrowserReady(0);
        }, 1500);
    }

    private void waitForBrowserReady(int attempt) {
        String status = readTextFile(new File(scriptsDir(), STATUS_FILE_NAME));
        if (status.startsWith("Running at http://")) {
            openBrowser();
            return;
        }
        if (attempt < 90) handler.postDelayed(() -> waitForBrowserReady(attempt + 1), 1000);
    }

    private String readTextFile(File file) {
        try {
            if (!file.exists()) return "";
            int length = (int) Math.min(file.length(), 128 * 1024);
            byte[] bytes = new byte[length];
            try (InputStream input = new FileInputStream(file)) {
                int read = input.read(bytes);
                return read <= 0 ? "" : new String(bytes, 0, read);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private void renderDependencyStatus(String rawStatus) {
        if (dependencyList == null) return;
        dependencyList.removeAllViews();

        Map<String, String> status = new HashMap<>();
        if (rawStatus != null) {
            String[] lines = rawStatus.split("\\r?\\n");
            for (String line : lines) {
                int index = line.indexOf('=');
                if (index <= 0) continue;
                status.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
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

        TextView row = new TextView(this);
        row.setText((installed ? "OK  " : "NO  ") + label);
        row.setTextSize(14);
        row.setTypeface(Typeface.MONOSPACE);
        row.setTextColor(!known ? Color.rgb(120, 120, 120) : installed ? Color.rgb(22, 128, 64) : Color.rgb(190, 32, 32));
        row.setPadding(0, dp(1), 0, dp(1));
        dependencyList.addView(row);
    }

    private String toStatusSummary(String status) {
        if (status == null) return "Not installed / not running";
        String cleaned = status.replace('\r', '\n')
            .replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
            .replaceAll("\\u001B\\][^\\u0007\\\\]*(?:\\u0007|\\\\)?", "");
        String[] logMarkers = new String[] {
            "SillyTavern WebServer",
            "SillyTavern is listening",
            "Go to:",
            "Content file ",
            "===="
        };
        for (String marker : logMarkers) {
            int index = cleaned.indexOf(marker);
            if (index >= 0) cleaned = cleaned.substring(0, index);
        }
        cleaned = cleaned.trim();
        int newline = cleaned.indexOf('\n');
        if (newline >= 0) cleaned = cleaned.substring(0, newline).trim();
        if (cleaned.isEmpty()) return "Not installed / not running";
        if (cleaned.length() > 140) cleaned = cleaned.substring(0, 140) + "...";
        return cleaned;
    }

    private String quote(String value) {
        if (value == null) value = "";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    public static String getControlScript() {
        return "#!/data/data/io.stavern/files/usr/bin/bash\n" +
            "set -u\n" +
            "ACTION=\"${1:-status}\"\n" +
            "BRANCH=\"${2:-release}\"\n" +
            "SOURCE_URLS=\"${3:-" + AUTO_SOURCE + "}\"\n" +
            "PORT=\"${4:-8000}\"\n" +
            "NPM_REGISTRY=\"https://registry.npmmirror.com\"\n" +
            "export HOME=\"" + TermuxConstants.TERMUX_HOME_DIR_PATH + "\"\n" +
            "export PREFIX=\"" + TermuxConstants.TERMUX_PREFIX_DIR_PATH + "\"\n" +
            "export TMPDIR=\"$PREFIX/tmp\"\n" +
            "export PATH=\"" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":$PREFIX/bin:/system/bin\"\n" +
            "export LD_LIBRARY_PATH=\"$PREFIX/lib\"\n" +
            "export GIT_TERMINAL_PROMPT=0\n" +
            "APP_DIR=\"$HOME/SillyTavern\"\n" +
            "PID_FILE=\"$HOME/.termux-silly/silly.pid\"\n" +
            "LOG_FILE=\"$HOME/.termux-silly/" + SILLY_LOG_FILE_NAME + "\"\n" +
            "CONTROL_LOG=\"$HOME/.termux-silly/" + CONTROL_LOG_FILE_NAME + "\"\n" +
            "STATUS_FILE=\"$HOME/.termux-silly/" + STATUS_FILE_NAME + "\"\n" +
            "DEPS_FILE=\"$HOME/.termux-silly/" + DEPS_STATUS_FILE_NAME + "\"\n" +
            "SOURCE_FILE=\"$HOME/.termux-silly/source.url\"\n" +
            "RUNTIME_MARKER=\"$HOME/.termux-silly/runtime-packages-v1\"\n" +
            "DEFAULT_CONFIG_FILE=\"$HOME/.termux-silly/" + DEFAULT_CONFIG_FILE_NAME + "\"\n" +
            "mkdir -p \"$HOME/.termux-silly\"\n" +
            "if [ -t 1 ] && [ \"${SILLYCTL_CAPTURED:-0}\" != 1 ] && command -v script >/dev/null 2>&1; then\n" +
            "  export SILLYCTL_CAPTURED=1\n" +
            "  printf -v replay_command '%q ' \"$0\" \"$ACTION\" \"$BRANCH\" \"$SOURCE_URLS\" \"$PORT\"\n" +
            "  exec script -q -e -f -a -c \"$replay_command\" \"$CONTROL_LOG\"\n" +
            "fi\n" +
            "[ -t 1 ] || exec >> \"$CONTROL_LOG\" 2>&1\n" +
            "echo \"\"\n" +
            "echo \"[$(date '+%Y-%m-%d %H:%M:%S')] sillyctl $ACTION\"\n" +
            "write_status() { echo \"$1\" > \"$STATUS_FILE\"; }\n" +
            "step() { if [ -t 1 ]; then printf '\\033[1;34m[%s] %s\\033[0m\\n' \"$1\" \"$2\"; else printf '[%s] %s\\n' \"$1\" \"$2\"; fi; }\n" +
            "dep_status() { if eval \"$2\" >/dev/null 2>&1; then echo \"$1=ok\"; else echo \"$1=missing\"; fi; }\n" +
            "check_deps() {\n" +
            "  {\n" +
            "    dep_status termux \"[ -d '$PREFIX' ] && [ -x '$PREFIX/bin/bash' ]\"\n" +
            "    dep_status git \"command -v git\"\n" +
            "    dep_status node \"command -v node\"\n" +
            "    dep_status npm \"command -v npm\"\n" +
            "    dep_status silly_dir \"[ -d '$APP_DIR' ]\"\n" +
            "    dep_status package_json \"[ -f '$APP_DIR/package.json' ]\"\n" +
            "    dep_status start_sh \"[ -f '$APP_DIR/start.sh' ]\"\n" +
            "    dep_status config_yaml \"[ -f '$APP_DIR/config.yaml' ]\"\n" +
            "    dep_status data_dir \"[ -d '$APP_DIR/data' ]\"\n" +
            "  } > \"$DEPS_FILE\"\n" +
            "}\n" +
            "server_pids() { pgrep -f 'node server\\.js' 2>/dev/null || true; }\n" +
            "launcher_pid() { [ -f \"$PID_FILE\" ] && cat \"$PID_FILE\" 2>/dev/null || true; }\n" +
            "launcher_running() { pid=\"$(launcher_pid)\"; [ -n \"$pid\" ] && kill -0 \"$pid\" 2>/dev/null; }\n" +
            "is_running() { [ -n \"$(server_pids)\" ] || launcher_running; }\n" +
            "first_server_pid() { pid=\"$(server_pids | head -n 1)\"; [ -n \"$pid\" ] && echo \"$pid\" || launcher_pid; }\n" +
            "is_installed() { [ -f \"$APP_DIR/package.json\" ] && [ -f \"$APP_DIR/start.sh\" ]; }\n" +
            "is_http_ready() {\n" +
            "  node -e \"const http=require('http');const port=process.argv[1];const req=http.get({host:'127.0.0.1',port,path:'/'},res=>process.exit(res.statusCode<500?0:1));req.on('error',()=>process.exit(1));req.setTimeout(1500,()=>{req.destroy();process.exit(1);});\" \"$PORT\"\n" +
            "}\n" +
            "wait_ready() {\n" +
            "  i=0\n" +
            "  while [ \"$i\" -lt 90 ]; do\n" +
            "    is_http_ready && return 0\n" +
            "    is_running || return 1\n" +
            "    sleep 1\n" +
            "    i=$((i + 1))\n" +
            "  done\n" +
            "  return 1\n" +
            "}\n" +
            "primary_source() { for source in $SOURCE_URLS; do echo \"$source\"; return 0; done; }\n" +
            "source_is_selected() {\n" +
            "  needle=\"$1\"\n" +
            "  for source in $SOURCE_URLS; do [ \"$source\" = \"$needle\" ] && return 0; done\n" +
            "  return 1\n" +
            "}\n" +
            "preferred_source() {\n" +
            "  if [ -f \"$SOURCE_FILE\" ]; then\n" +
            "    saved=\"$(cat \"$SOURCE_FILE\")\"\n" +
            "    if [ -n \"$saved\" ] && source_is_selected \"$saved\"; then echo \"$saved\"; return 0; fi\n" +
            "  fi\n" +
            "  primary_source\n" +
            "}\n" +
            "status_source() { preferred_source; }\n" +
            "clone_from_sources() {\n" +
            "  for source in $SOURCE_URLS; do\n" +
            "    echo \"Trying source: $source\"\n" +
            "    rm -rf \"$APP_DIR\"\n" +
            "    if git clone --progress --depth 1 --single-branch -b \"$BRANCH\" \"$source\" \"$APP_DIR\"; then\n" +
            "      echo \"Source used: $source\"\n" +
            "      echo \"$source\" > \"$SOURCE_FILE\"\n" +
            "      return 0\n" +
            "    fi\n" +
            "  done\n" +
            "  echo 'All sources failed.'\n" +
            "  return 1\n" +
            "}\n" +
            "backup_app_dir() {\n" +
            "  [ -e \"$APP_DIR\" ] || return 0\n" +
            "  backup=\"$HOME/SillyTavern.broken.$(date '+%Y%m%d-%H%M%S')\"\n" +
            "  echo \"Backing up broken install to $backup\"\n" +
            "  mv \"$APP_DIR\" \"$backup\" || rm -rf \"$APP_DIR\"\n" +
            "}\n" +
            "prepare_git_source() {\n" +
            "  source=\"$(preferred_source)\"\n" +
            "  [ -d \"$APP_DIR/.git\" ] || return 0\n" +
            "  if git remote get-url origin >/dev/null 2>&1; then\n" +
            "    [ -n \"$source\" ] && git remote set-url origin \"$source\" || true\n" +
            "  else\n" +
            "    [ -n \"$source\" ] && git remote add origin \"$source\" || true\n" +
            "  fi\n" +
            "}\n" +
            "install_node_deps() {\n" +
            "  npm config set registry \"$NPM_REGISTRY\" || true\n" +
            "  npm config set progress true || true\n" +
            "  npm config set color always || true\n" +
            "  npm install --omit=dev --registry=\"$NPM_REGISTRY\" --no-audit --no-fund --progress=true --loglevel=info\n" +
            "}\n" +
            "ensure_runtime() {\n" +
            "  command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1 && command -v git >/dev/null 2>&1 && return 0\n" +
            "  echo 'Bundled Node.js/Git runtime is incomplete.'\n" +
            "  return 1\n" +
            "}\n" +
            "install_runtime_packages() {\n" +
            "  if [ -f \"$RUNTIME_MARKER\" ] && ensure_runtime; then\n" +
            "    step '1/6' '正在检查 Termux 运行环境...'\n" +
            "    echo \"Runtime ready: node $(node --version), npm $(npm --version), git $(git --version | awk '{print $3}')\"\n" +
            "    return 0\n" +
            "  fi\n" +
            "  export DEBIAN_FRONTEND=noninteractive\n" +
            "  step '1/6' '正在更新 Termux 软件包列表...'\n" +
            "  if ! apt-get update; then\n" +
            "    echo 'Package index update failed; checking the bundled runtime...'\n" +
            "    ensure_runtime && { echo 'Bundled runtime is usable; continuing installation.'; return 0; }\n" +
            "    return 1\n" +
            "  fi\n" +
            "  step '2/6' '正在升级已安装软件...'\n" +
            "  if ! apt-get -y -o Dpkg::Options::=--force-confold upgrade; then\n" +
            "    echo 'Package upgrade was interrupted; repairing package configuration...'\n" +
            "    dpkg --configure -a || true\n" +
            "    ensure_runtime || return 1\n" +
            "  fi\n" +
            "  step '3/6' '正在安装 SillyTavern 运行依赖...'\n" +
            "  if ! apt-get -y -o Dpkg::Options::=--force-confold install ca-certificates git nodejs-lts; then\n" +
            "    ensure_runtime || return 1\n" +
            "    echo 'Bundled dependencies are already usable; continuing installation.'\n" +
            "    return 0\n" +
            "  fi\n" +
            "  hash -r\n" +
            "  ensure_runtime || return 1\n" +
            "  touch \"$RUNTIME_MARKER\"\n" +
            "  echo \"Runtime ready: node $(node --version), npm $(npm --version), git $(git --version | awk '{print $3}')\"\n" +
            "}\n" +
            "ensure_runtime_dirs() {\n" +
            "  [ -d \"$APP_DIR\" ] || return 0\n" +
            "  mkdir -p \"$APP_DIR/data/_storage\" \"$APP_DIR/data/default-user\"\n" +
            "}\n" +
            "ensure_config() {\n" +
            "  [ -d \"$APP_DIR\" ] || return 0\n" +
            "  cd \"$APP_DIR\"\n" +
            "  if [ ! -f config.yaml ] && [ ! -f default/config.yaml ] && [ -f \"$DEFAULT_CONFIG_FILE\" ]; then mkdir -p default && cp \"$DEFAULT_CONFIG_FILE\" default/config.yaml; fi\n" +
            "  [ -f config.yaml ] || { [ -f default/config.yaml ] && cp default/config.yaml config.yaml; }\n" +
            "  [ -f default/config.yaml ] || { [ -f config.yaml ] && mkdir -p default && cp config.yaml default/config.yaml; }\n" +
            "  if [ -f config.yaml ]; then\n" +
            "    node - \"$PORT\" <<'NODE' || true\n" +
            "const fs = require('fs');\n" +
            "const path = 'config.yaml';\n" +
            "const port = process.argv[2];\n" +
            "let text = fs.readFileSync(path, 'utf8');\n" +
            "if (/^port\\s*:/m.test(text)) text = text.replace(/^port\\s*:.*$/m, `port: ${port}`);\n" +
            "else text += `\\nport: ${port}\\n`;\n" +
            "const lines = text.split(/\\r?\\n/);\n" +
            "const out = [];\n" +
            "let inBrowserLaunch = false;\n" +
            "let browserEnabledWritten = false;\n" +
            "let browserSectionFound = false;\n" +
            "for (const line of lines) {\n" +
            "  if (/^\\S/.test(line)) {\n" +
            "    if (inBrowserLaunch && !browserEnabledWritten) out.push('  enabled: false');\n" +
            "    inBrowserLaunch = false;\n" +
            "  }\n" +
            "  if (line.trim() === 'browserLaunch:') {\n" +
            "    browserSectionFound = true;\n" +
            "    inBrowserLaunch = true;\n" +
            "    browserEnabledWritten = false;\n" +
            "    out.push(line);\n" +
            "    continue;\n" +
            "  }\n" +
            "  if (inBrowserLaunch && /^\\s+enabled\\s*:/.test(line)) {\n" +
            "    out.push('  enabled: false');\n" +
            "    browserEnabledWritten = true;\n" +
            "    continue;\n" +
            "  }\n" +
            "  out.push(line);\n" +
            "}\n" +
            "if (inBrowserLaunch && !browserEnabledWritten) out.push('  enabled: false');\n" +
            "if (!browserSectionFound) out.push('', 'browserLaunch:', '  enabled: false');\n" +
            "fs.writeFileSync(path, out.join('\\n').replace(/\\n*$/, '\\n'));\n" +
            "NODE\n" +
            "  fi\n" +
            "}\n" +
            "install_app() {\n" +
            "  write_status \"Installing / branch=$BRANCH / port=$PORT\"\n" +
            "  echo 'SillyTavern installation started.'\n" +
            "  install_runtime_packages || { check_deps; write_status 'Install failed: runtime packages unavailable'; exit 1; }\n" +
            "  step '4/6' '正在下载或检查 SillyTavern 源码...'\n" +
            "  if [ -e \"$APP_DIR\" ] && { [ ! -d \"$APP_DIR/.git\" ] || [ ! -f \"$APP_DIR/package.json\" ] || [ ! -f \"$APP_DIR/start.sh\" ]; }; then backup_app_dir; fi\n" +
            "  if [ ! -d \"$APP_DIR/.git\" ]; then clone_from_sources || { echo 'Source install failed.'; check_deps; write_status 'Install failed: source unavailable'; exit 1; }; fi\n" +
            "  cd \"$APP_DIR\" || { echo \"Cannot enter $APP_DIR\"; check_deps; write_status 'Install failed: app directory missing'; exit 1; }\n" +
            "  prepare_git_source\n" +
            "  git checkout \"$BRANCH\" || git checkout -b \"$BRANCH\" || true\n" +
            "  step '5/6' '正在安装 Node.js 依赖...'\n" +
            "  install_node_deps\n" +
            "  step '6/6' '正在生成配置并完成安装...'\n" +
            "  ensure_config\n" +
            "  ensure_runtime_dirs\n" +
            "  check_deps\n" +
            "  echo 'Install complete.'\n" +
            "  write_status \"Installed / not running / source=$(status_source) / port=$PORT\"\n" +
            "}\n" +
            "update_app() {\n" +
            "  is_installed || { echo 'SillyTavern is not installed.'; exit 1; }\n" +
            "  ensure_runtime || { check_deps; write_status 'Update failed: bundled runtime incomplete'; exit 1; }\n" +
            "  cd \"$APP_DIR\"\n" +
            "  updated=0\n" +
            "  for source in $SOURCE_URLS; do\n" +
            "    echo \"Trying update source: $source\"\n" +
            "    git remote set-url origin \"$source\"\n" +
            "    if git fetch origin --prune && { git checkout \"$BRANCH\" || git checkout -B \"$BRANCH\" \"origin/$BRANCH\"; } && git pull --ff-only origin \"$BRANCH\"; then\n" +
            "      echo \"$source\" > \"$SOURCE_FILE\"\n" +
            "      updated=1\n" +
            "      break\n" +
            "    fi\n" +
            "  done\n" +
            "  [ \"$updated\" = 1 ] || { echo 'Update failed on all sources. Check the network or local source changes.'; write_status 'Update failed: all sources unavailable or source has local changes'; exit 1; }\n" +
            "  install_node_deps\n" +
            "  ensure_config\n" +
            "  ensure_runtime_dirs\n" +
            "  check_deps\n" +
            "  echo 'Update complete.'\n" +
            "  write_status \"Installed / not running / source=$(status_source) / port=$PORT\"\n" +
            "}\n" +
            "start_app() {\n" +
            "  [ -d \"$APP_DIR\" ] || { echo 'SillyTavern is not installed.'; exit 1; }\n" +
            "  is_installed || { echo 'SillyTavern install is incomplete. Run install/repair first.'; exit 1; }\n" +
            "  ensure_runtime || { check_deps; write_status 'Start failed: bundled runtime incomplete'; exit 1; }\n" +
            "  if is_running; then wait_ready && { echo \"Already running at http://127.0.0.1:$PORT\"; write_status \"Running at http://127.0.0.1:$PORT / source=$(status_source)\"; } || { echo 'Process is running but HTTP is not ready.'; write_status 'Process running but HTTP not ready'; tail -n 80 \"$LOG_FILE\"; exit 1; }; exit 0; fi\n" +
            "  ensure_config\n" +
            "  ensure_runtime_dirs\n" +
            "  cd \"$APP_DIR\"\n" +
            "  echo 'Starting SillyTavern...'\n" +
            "  write_status \"Starting / port=$PORT / source=$(status_source)\"\n" +
            "  : > \"$LOG_FILE\"\n" +
            "  ( cd \"$APP_DIR\" && exec setsid bash ./start.sh ) > \"$LOG_FILE\" 2>&1 &\n" +
            "  echo $! > \"$PID_FILE\"\n" +
            "  if wait_ready; then check_deps; echo \"Running at http://127.0.0.1:$PORT\"; write_status \"Running at http://127.0.0.1:$PORT / source=$(status_source)\"; else check_deps; echo 'Start failed or timed out.'; write_status 'Start failed or timed out'; tail -n 80 \"$LOG_FILE\"; exit 1; fi\n" +
            "}\n" +
            "stop_app() {\n" +
            "  if is_running; then\n" +
            "    pid=\"$(launcher_pid)\"\n" +
            "    if [ -n \"$pid\" ]; then kill -TERM -- \"-$pid\" 2>/dev/null || kill -TERM \"$pid\" 2>/dev/null || true; fi\n" +
            "    pkill -TERM -f 'node server\\.js' 2>/dev/null || true\n" +
            "    i=0\n" +
            "    while is_running && [ \"$i\" -lt 10 ]; do sleep 1; i=$((i+1)); done\n" +
            "    if is_running; then pkill -KILL -f 'node server\\.js' 2>/dev/null || true; sleep 1; fi\n" +
            "  fi\n" +
            "  rm -f \"$PID_FILE\"\n" +
            "  check_deps\n" +
            "  if is_running; then echo 'Stop failed.'; write_status 'Stop failed: process still running'; exit 1; fi\n" +
            "  echo 'Stopped.'\n" +
            "  write_status \"Installed / not running / source=$(status_source) / port=$PORT\"\n" +
            "}\n" +
            "status_app() {\n" +
            "  if is_installed; then installed='Installed'; else installed='Not installed'; fi\n" +
            "  if is_running; then if is_http_ready; then running=\"Running pid=$(first_server_pid)\"; else running=\"Starting pid=$(first_server_pid) / HTTP not ready\"; fi; else running='Not running'; fi\n" +
            "  summary=\"$installed / $running / branch=$BRANCH / port=$PORT / source=$(status_source)\"\n" +
            "  echo \"$summary\"\n" +
            "  check_deps\n" +
            "  write_status \"$summary\"\n" +
            "}\n" +
            "case \"$ACTION\" in\n" +
            "  install) install_app && start_app ;;\n" +
            "  update) update_app ;;\n" +
            "  start) start_app ;;\n" +
            "  stop) stop_app ;;\n" +
            "  status) status_app ;;\n" +
            "  *) echo \"Unknown action: $ACTION\"; exit 2 ;;\n" +
            "esac\n";
    }
}
