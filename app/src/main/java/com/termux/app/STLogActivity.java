package com.termux.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.HorizontalScrollView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class STLogActivity extends AppCompatActivity {

    private TextView logView;
    private int selectedLog;
    private Button[] tabButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildLayout();
        refreshLog();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(16));
        SillyUI.darkRoot(root);

        TextView title = new TextView(this);
        title.setText("运行日志");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(SillyUI.ACCENT);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, dp(12));
        root.addView(title, titleLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabButtons = new Button[2];
        tabButtons[0] = addTab(tabs, "控制日志", () -> { selectedLog = 0; refreshLog(); });
        tabButtons[1] = addTab(tabs, "Silly 日志", () -> { selectedLog = 1; refreshLog(); });
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.setMargins(0, 0, 0, dp(10));
        root.addView(tabs, tabsLp);
        updateTabStyles();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addButton(actions, "刷新", this::refreshLog, SillyUI.BTN_SECONDARY);
        addButton(actions, "清空当前", this::clearCurrentLog, SillyUI.BTN_DANGER);
        addButton(actions, "返回控制台", this::finish, SillyUI.BTN_GHOST);
        root.addView(actions, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        scrollView.addView(logView);
        logView.setPadding(dp(14), dp(14), dp(14), dp(14));
        logView.setTextColor(SillyUI.TEXT_PRIMARY);
        scrollView.setBackground(SillyUI.card(STLogActivity.this));
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        svLp.setMargins(0, dp(10), 0, 0);
        root.addView(scrollView, svLp);

        setContentView(root);
    }

    private Button addButton(LinearLayout parent, String text, Runnable action, int type) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        SillyUI.styleButton(this, button, type);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(dp(6));
        parent.addView(button, lp);
        return button;
    }

    private Button addTab(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> { action.run(); updateTabStyles(); });
        SillyUI.styleButton(this, button, SillyUI.BTN_GHOST);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(dp(6));
        parent.addView(button, lp);
        return button;
    }

    private void updateTabStyles() {
        if (tabButtons == null) return;
        for (int i = 0; i < tabButtons.length; i++) {
            if (tabButtons[i] == null) continue;
            boolean active = i == selectedLog;
            tabButtons[i].setBackground(SillyUI.roundRect(active ? SillyUI.ACCENT : SillyUI.BG_CARD,
                dp(10), active ? 0 : SillyUI.DIVIDER, active ? 0 : 1f));
            tabButtons[i].setTextColor(active ? 0xFF15121E : SillyUI.TEXT_PRIMARY);
        }
    }

    private void refreshLog() {
        File file = currentLogFile();
        String text = readTail(file, 512 * 1024);
        if (text.trim().isEmpty()) text = file.getAbsolutePath() + "\n\n暂无日志";
        logView.setText(text);
        logView.post(() -> {
            ViewGroup parent = (ViewGroup) logView.getParent();
            if (parent instanceof ScrollView) ((ScrollView) parent).fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

    private void clearCurrentLog() {
        try {
            File file = currentLogFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream ignored = new FileOutputStream(file, false)) {
            }
            refreshLog();
        } catch (Exception e) {
            Toast.makeText(this, "清空失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            try (InputStream input = new FileInputStream(file)) {
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
            return "读取失败: " + e.getMessage();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
