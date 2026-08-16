package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Native character-card gallery. Cards are kept separately from SillyTavern until imported. */
public final class CharacterGalleryView {

    private static final int PICK_REQUEST_CODE = 7351;
    private static final String META_SUFFIX = ".cardmeta";
    private static final String DEFAULT_CATEGORY = "未分类";

    private final TermuxActivity activity;
    private LinearLayout cardList;
    private Spinner filterSpinner;
    private TextView summaryView;
    private boolean updatingFilter;

    public CharacterGalleryView(TermuxActivity activity) {
        this.activity = activity;
    }

    public View build() {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(20), dp(18), dp(22));
        page.setBackground(SillyUI.roundRect(SillyUI.BG_CARD, dp(22), 0x99DDE5F2, 1f));
        scrollView.addView(page, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = SillyUI.card(activity, "角色卡画廊");
        TextView hint = text("角色卡先保存在应用画廊中。可导入 PNG、JSON 或 CHARX，设置分类标签，再一键复制到 SillyTavern。", 13,
            SillyUI.TEXT_SECONDARY);
        header.addView(hint);
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button importButton = button("导入角色卡", this::pickCards, SillyUI.BTN_PRIMARY);
        Button refreshButton = button("刷新画廊", this::refresh);
        actions.addView(importButton, rowParams(true));
        actions.addView(refreshButton, rowParams(false));
        header.addView(actions, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(header);

        LinearLayout filterCard = SillyUI.card(activity, "分类筛选");
        filterSpinner = new Spinner(activity);
        SillyUI.styleSpinner(activity, filterSpinner);
        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (!updatingFilter) renderCards();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        filterCard.addView(filterSpinner, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        summaryView = text("", 12, SillyUI.TEXT_SECONDARY);
        summaryView.setPadding(0, dp(8), 0, 0);
        filterCard.addView(summaryView);
        page.addView(filterCard);

        cardList = new LinearLayout(activity);
        cardList.setOrientation(LinearLayout.VERTICAL);
        page.addView(cardList, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
        return scrollView;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_REQUEST_CODE) return false;
        if (resultCode == Activity.RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            int imported = 0;
            for (Uri uri : uris) {
                if (copyIntoGallery(uri)) imported++;
            }
            refresh();
            if (imported > 0) toast("已添加 " + imported + " 张角色卡");
            else if (!uris.isEmpty()) toast("没有可导入的角色卡文件");
        }
        return true;
    }

    public void refresh() {
        if (cardList == null || filterSpinner == null) return;
        ensureGalleryDir();
        String previous = currentFilter();
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.add("全部");
        categories.add(DEFAULT_CATEGORY);
        for (CardEntry entry : listCards()) categories.addAll(entry.tags);

        updatingFilter = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
            android.R.layout.simple_spinner_item, new ArrayList<>(categories)) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) ((TextView) view).setTextColor(SillyUI.TEXT_PRIMARY);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(SillyUI.TEXT_PRIMARY);
                    view.setBackgroundColor(SillyUI.BG_CARD);
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).equals(previous)) {
                selected = i;
                break;
            }
        }
        filterSpinner.setSelection(selected, false);
        updatingFilter = false;
        renderCards();
    }

    private void pickCards() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "image/png", "application/json", "application/zip", "application/octet-stream"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivityForResult(intent, PICK_REQUEST_CODE);
        } catch (Exception e) {
            toast("无法打开文件选择器: " + e.getMessage());
        }
    }

    private boolean copyIntoGallery(Uri uri) {
        String name = displayName(uri);
        if (name == null || name.trim().isEmpty()) name = "character-card.json";
        name = safeName(name);
        if (!isSupportedCard(name)) {
            toast("已跳过不支持的文件: " + name);
            return false;
        }
        File destination = uniqueFile(new File(galleryDir(), name));
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(destination, false)) {
            if (input == null) throw new IllegalStateException("文件内容不可读");
            copy(input, output);
            saveTags(destination, DEFAULT_CATEGORY);
            return true;
        } catch (Exception e) {
            if (destination.exists()) destination.delete();
            toast("导入失败: " + e.getMessage());
            return false;
        }
    }

    private void renderCards() {
        if (cardList == null) return;
        cardList.removeAllViews();
        String filter = currentFilter();
        List<CardEntry> cards = listCards();
        int shown = 0;
        for (CardEntry entry : cards) {
            if (!"全部".equals(filter) && !entry.tags.contains(filter)) continue;
            cardList.addView(buildCard(entry));
            shown++;
        }
        summaryView.setText(shown + " / " + cards.size() + " 张角色卡");
        if (shown == 0) {
            TextView empty = text(cards.isEmpty()
                ? "画廊还是空的，点击“导入角色卡”开始添加。"
                : "当前分类没有角色卡。", 14, SillyUI.TEXT_SECONDARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            cardList.addView(empty);
        }
    }

    private LinearLayout buildCard(CardEntry entry) {
        LinearLayout card = SillyUI.card(activity, null);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        preview.setPadding(dp(4), dp(4), dp(4), dp(4));
        Bitmap bitmap = loadPreview(entry.file);
        if (bitmap != null) preview.setImageBitmap(bitmap);
        else preview.setImageResource(R.drawable.silly_mascot_cutout);
        row.addView(preview, new LinearLayout.LayoutParams(dp(78), dp(78)));

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, 0, 0);
        TextView title = text(stripExtension(entry.file.getName()), 16, SillyUI.TEXT_PRIMARY);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        details.addView(title);
        TextView fileInfo = text(entry.file.getName() + "\n" + formatSize(entry.file.length()), 12,
            SillyUI.TEXT_SECONDARY);
        details.addView(fileInfo);
        TextView tags = text("标签: " + joinTags(entry.tags), 12, SillyUI.ACCENT_TEXT);
        tags.setPadding(0, dp(6), 0, 0);
        details.addView(tags);
        row.addView(details, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button importButton = button("导入到酒馆", () -> importIntoSillyTavern(entry.file), SillyUI.BTN_PRIMARY);
        Button tagsButton = button("分类标签", () -> editTags(entry.file), SillyUI.BTN_SECONDARY);
        Button deleteButton = button("删除", () -> confirmDelete(entry.file), SillyUI.BTN_DANGER);
        buttons.addView(importButton, rowParams(true));
        buttons.addView(tagsButton, rowParams(false));
        buttons.addView(deleteButton, rowParams(false));
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.setMargins(0, dp(10), 0, 0);
        card.addView(buttons, buttonsLp);
        return card;
    }

    private void editTags(File card) {
        EditText input = new EditText(activity);
        SillyUI.styleInput(activity, input);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("例如：科幻, 恋爱, 原创");
        input.setText(joinTags(readTags(card)));
        LinearLayout container = new LinearLayout(activity);
        container.setPadding(dp(22), dp(4), dp(22), 0);
        container.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity)
            .setTitle("设置分类标签")
            .setMessage("多个标签用逗号分隔")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", (dialog, which) -> {
                saveTags(card, input.getText().toString());
                refresh();
            }).show();
    }

    private void confirmDelete(File card) {
        new AlertDialog.Builder(activity)
            .setTitle("删除画廊角色卡？")
            .setMessage("只删除画廊副本，不会删除已经导入 SillyTavern 的文件。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", (dialog, which) -> {
                boolean deleted = !card.exists() || card.delete();
                File meta = metaFile(card);
                if (meta.exists()) meta.delete();
                toast(deleted ? "已从画廊删除" : "删除失败");
                refresh();
            }).show();
    }

    private void importIntoSillyTavern(File card) {
        File appDir = new File(TermuxConstants.TERMUX_HOME_DIR, "SillyTavern");
        if (!new File(appDir, "package.json").exists()) {
            toast("SillyTavern 尚未安装完成");
            return;
        }
        File charactersDir = new File(appDir, "public/characters");
        File target = new File(charactersDir, card.getName());
        if (target.exists()) {
            new AlertDialog.Builder(activity)
                .setTitle("角色卡已存在")
                .setMessage("是否覆盖 SillyTavern 中的同名角色卡？")
                .setNegativeButton("取消", null)
                .setPositiveButton("覆盖", (dialog, which) -> copyToSillyTavern(card, target))
                .show();
        } else {
            copyToSillyTavern(card, target);
        }
    }

    private void copyToSillyTavern(File source, File target) {
        new Thread(() -> {
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("无法创建 characters 目录");
                }
                try (InputStream input = new FileInputStream(source);
                     OutputStream output = new FileOutputStream(target, false)) {
                    copy(input, output);
                }
                activity.runOnUiThread(() -> toast("已导入 SillyTavern: " + target.getName()));
            } catch (Exception e) {
                activity.runOnUiThread(() -> toast("导入 SillyTavern 失败: " + e.getMessage()));
            }
        }, "character-card-import").start();
    }

    private List<CardEntry> listCards() {
        ensureGalleryDir();
        File[] files = galleryDir().listFiles(file -> file.isFile()
            && !file.getName().endsWith(META_SUFFIX) && isSupportedCard(file.getName()));
        List<CardEntry> result = new ArrayList<>();
        if (files != null) {
            for (File file : files) result.add(new CardEntry(file, readTags(file)));
        }
        Collections.sort(result, (a, b) -> {
            int byTime = Long.compare(b.file.lastModified(), a.file.lastModified());
            return byTime != 0 ? byTime : a.file.getName().compareToIgnoreCase(b.file.getName());
        });
        return result;
    }

    private Set<String> readTags(File card) {
        Properties properties = new Properties();
        File meta = metaFile(card);
        try (InputStream input = new FileInputStream(meta)) {
            properties.load(input);
        } catch (Exception ignored) {
        }
        String raw = properties.getProperty("tags", DEFAULT_CATEGORY);
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String tag : raw.split("[,，]")) {
            String value = tag.trim();
            if (!value.isEmpty()) tags.add(value);
        }
        if (tags.isEmpty()) tags.add(DEFAULT_CATEGORY);
        return tags;
    }

    private void saveTags(File card, String raw) {
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (raw != null) {
            for (String tag : raw.split("[,，]")) {
                String value = tag.trim();
                if (!value.isEmpty() && value.length() <= 32) tags.add(value);
            }
        }
        if (tags.isEmpty()) tags.add(DEFAULT_CATEGORY);
        Properties properties = new Properties();
        properties.setProperty("tags", joinTags(tags));
        try (OutputStream output = new FileOutputStream(metaFile(card), false)) {
            properties.store(output, "SillyTavern character card metadata");
        } catch (Exception e) {
            toast("保存分类失败: " + e.getMessage());
        }
    }

    private File galleryDir() {
        return new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".termux-silly"), "character-gallery");
    }

    private void ensureGalleryDir() {
        File dir = galleryDir();
        if (!dir.exists()) dir.mkdirs();
    }

    private File metaFile(File card) {
        return new File(card.getParentFile(), card.getName() + META_SUFFIX);
    }

    private String currentFilter() {
        Object selected = filterSpinner == null ? null : filterSpinner.getSelectedItem();
        return selected == null ? "全部" : String.valueOf(selected);
    }

    private String displayName(Uri uri) {
        ContentResolver resolver = activity.getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                                            null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        }
        String path = uri == null ? null : uri.getLastPathSegment();
        return path == null ? null : path.substring(path.lastIndexOf('/') + 1);
    }

    private File uniqueFile(File requested) {
        if (!requested.exists()) return requested;
        String name = requested.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        File candidate;
        do {
            candidate = new File(requested.getParentFile(), base + " (" + index++ + ")" + ext);
        } while (candidate.exists());
        return candidate;
    }

    private String safeName(String name) {
        String value = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (value.length() > 120) value = value.substring(0, 120);
        return value.isEmpty() ? "character-card.json" : value;
    }

    private boolean isSupportedCard(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".json") || lower.endsWith(".charx");
    }

    private Bitmap loadPreview(File file) {
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > dp(156) || bounds.outHeight / sample > dp(156)) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String joinTags(Set<String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(tag);
        }
        return builder.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024d);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, Runnable action) {
        return button(label, action, SillyUI.BTN_SECONDARY);
    }

    private Button button(String label, Runnable action, int style) {
        Button button = new Button(activity);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        SillyUI.styleButton(activity, button, style);
        return button;
    }

    private LinearLayout.LayoutParams rowParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(first ? 0 : dp(6), 0, first ? dp(6) : 0, 0);
        return params;
    }

    private int dp(int value) {
        return SillyUI.dp(activity, value);
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private static final class CardEntry {
        final File file;
        final Set<String> tags;

        CardEntry(File file, Set<String> tags) {
            this.file = file;
            this.tags = tags;
        }
    }
}
