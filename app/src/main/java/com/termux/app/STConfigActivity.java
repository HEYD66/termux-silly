package com.termux.app;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-fidelity editor for every SillyTavern config.yaml field. */
public class STConfigActivity extends AppCompatActivity {
    private EditText editor;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SillyTavern 完整配置");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView hint = new TextView(this);
        hint.setText("这里覆盖 config.yaml 的全部字段。保存前会自动备份；修改后请重启 SillyTavern。\n只编辑配置，不会删除角色卡、聊天或其他用户数据。");
        hint.setTextSize(13);
        root.addView(hint);

        editor = new EditText(this);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(13);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams editorLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        editorLp.setMargins(0, dp(10), 0, dp(10));
        root.addView(editor, editorLp);

        status = new TextView(this);
        status.setTextSize(12);
        root.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button reload = button("重新加载", v -> loadConfig());
        Button restore = button("恢复默认", v -> confirmRestore());
        Button save = button("保存配置", v -> saveConfig());
        actions.addView(reload, rowParams());
        actions.addView(restore, rowParams());
        actions.addView(save, rowParams());
        root.addView(actions);

        setContentView(root);
        loadConfig();
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private File homeDir() {
        return TermuxConstants.TERMUX_HOME_DIR;
    }

    private File configFile() {
        return new File(homeDir(), "SillyTavern/config.yaml");
    }

    private File defaultConfigFile() {
        return new File(homeDir(), STMainActivity.CONTROL_DIR_NAME + "/" + STMainActivity.DEFAULT_CONFIG_FILE_NAME);
    }

    private void loadConfig() {
        try {
            File config = configFile();
            if (config.exists() && config.length() > 0) {
                editor.setText(read(config));
                status.setText("当前文件：" + config.getAbsolutePath());
                return;
            }
            File fallback = defaultConfigFile();
            if (fallback.exists() && fallback.length() > 0) {
                editor.setText(read(fallback));
                status.setText("尚未安装 SillyTavern，当前显示默认模板；保存会创建 config.yaml");
                return;
            }
            try (InputStream input = getAssets().open(STMainActivity.DEFAULT_CONFIG_ASSET_NAME)) {
                byte[] bytes = read(input);
                editor.setText(new String(bytes, StandardCharsets.UTF_8));
                status.setText("当前显示内置默认模板");
            }
        } catch (Exception e) {
            status.setText("读取失败：" + e.getMessage());
        }
    }

    private void saveConfig() {
        String text = editor.getText().toString();
        if (text.trim().isEmpty()) {
            toast("配置不能为空");
            return;
        }
        try {
            File config = configFile();
            File parent = config.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建 SillyTavern 目录");
            if (config.exists()) {
                File backup = new File(parent, "config.yaml.bak." +
                    new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()));
                copy(config, backup);
            }
            File tmp = new File(parent, "config.yaml.tmp");
            write(tmp, text.endsWith("\n") ? text : text + "\n");
            if (config.exists() && !config.delete()) throw new IllegalStateException("无法替换旧配置");
            if (!tmp.renameTo(config)) throw new IllegalStateException("无法写入新配置");
            status.setText("已保存：" + config.getAbsolutePath() + "\n请停止并重新启动 SillyTavern 使配置生效。");
            toast("配置已保存并完成备份");
        } catch (Exception e) {
            status.setText("保存失败：" + e.getMessage());
            toast("保存失败：" + e.getMessage());
        }
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
            .setTitle("恢复默认配置")
            .setMessage("只会覆盖 SillyTavern/config.yaml，不会删除角色卡和聊天数据。继续吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复", (dialog, which) -> restoreDefault())
            .show();
    }

    private void restoreDefault() {
        try {
            File fallback = defaultConfigFile();
            if (!fallback.exists()) {
                try (InputStream input = getAssets().open(STMainActivity.DEFAULT_CONFIG_ASSET_NAME)) {
                    write(fallback, new String(read(input), StandardCharsets.UTF_8));
                }
            }
            editor.setText(read(fallback));
            saveConfig();
        } catch (Exception e) {
            toast("恢复失败：" + e.getMessage());
        }
    }

    private String read(File file) throws Exception {
        return new String(read(new FileInputStream(file)), StandardCharsets.UTF_8);
    }

    private byte[] read(InputStream input) throws Exception {
        try (InputStream stream = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private void write(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void copy(File source, File target) throws Exception {
        try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
