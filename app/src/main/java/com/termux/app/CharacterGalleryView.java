package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Native character-card bookshelf.
 *
 * Cards live in the app-private gallery until explicitly imported into
 * SillyTavern. Export archives contain both cards and their category metadata,
 * so they can be moved to another device and imported again.
 */
public final class CharacterGalleryView {

    private static final int PICK_REQUEST_CODE = 7351;
    private static final int EXPORT_REQUEST_CODE = 7352;
    private static final String META_SUFFIX = ".cardmeta";
    private static final String ARCHIVE_SUFFIX = ".stcards.zip";
    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String ALL = "全部";
    private static final String STATUS_ALL = "全部状态";
    private static final String STATUS_IMPORTED = "已导入";
    private static final String STATUS_NOT_IMPORTED = "未导入";

    private final TermuxActivity activity;
    private final Set<String> selectedNames = new HashSet<>();
    private LinearLayout cardList;
    private Spinner statusSpinner;
    private Spinner categorySpinner;
    private TextView summaryView;
    private TextView selectedView;
    private boolean updatingFilters;
    private List<String> pendingExportNames = new ArrayList<>();

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

        LinearLayout header = SillyUI.card(activity, "画廊");
        TextView hint = text("以书架方式管理角色卡。点击卡片查看详情，勾选后可批量导入或打包导出到其他设备。",
            13, SillyUI.TEXT_SECONDARY);
        header.addView(hint);
        LinearLayout importRow = new LinearLayout(activity);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        importRow.addView(button("导入角色卡", this::pickCards, SillyUI.BTN_PRIMARY), rowParams(true));
        importRow.addView(button("刷新画廊", this::refresh), rowParams(false));
        header.addView(importRow, wrapParams(dp(10)));

        LinearLayout selectRow = new LinearLayout(activity);
        selectRow.setOrientation(LinearLayout.HORIZONTAL);
        selectRow.addView(button("全选当前", this::selectAllVisible), rowParams(true));
        selectRow.addView(button("清除选择", this::clearSelection), rowParams(false));
        header.addView(selectRow, wrapParams(dp(6)));

        LinearLayout batchRow = new LinearLayout(activity);
        batchRow.setOrientation(LinearLayout.HORIZONTAL);
        batchRow.addView(button("批量导入", this::batchImport, SillyUI.BTN_PRIMARY), rowParams(true));
        batchRow.addView(button("批量导出", this::createExportDocument), rowParams(false));
        header.addView(batchRow, wrapParams(dp(6)));
        selectedView = text("未选择角色卡", 12, SillyUI.TEXT_SECONDARY);
        selectedView.setPadding(0, dp(8), 0, 0);
        header.addView(selectedView);
        page.addView(header);

        LinearLayout filterCard = SillyUI.card(activity, "筛选");
        filterCard.addView(SillyUI.sectionHeader(activity, "导入状态"), wrapParams(0));
        statusSpinner = new Spinner(activity);
        SillyUI.styleSpinner(activity, statusSpinner);
        filterCard.addView(statusSpinner, wrapParams(dp(6)));
        filterCard.addView(SillyUI.sectionHeader(activity, "分类标签"), wrapParams(dp(10)));
        categorySpinner = new Spinner(activity);
        SillyUI.styleSpinner(activity, categorySpinner);
        filterCard.addView(categorySpinner, wrapParams(dp(6)));
        configureFilterListeners();
        summaryView = text("", 12, SillyUI.TEXT_SECONDARY);
        summaryView.setPadding(0, dp(8), 0, 0);
        filterCard.addView(summaryView);
        page.addView(filterCard);

        cardList = new LinearLayout(activity);
        cardList.setOrientation(LinearLayout.VERTICAL);
        page.addView(cardList, wrapParams(dp(4)));
        refresh();
        return scrollView;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                List<Uri> uris = new ArrayList<>();
                if (data.getClipData() != null) {
                    ClipData clip = data.getClipData();
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        uris.add(clip.getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
                importUris(uris);
            }
            return true;
        }
        if (requestCode == EXPORT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                exportTo(data.getData(), new ArrayList<>(pendingExportNames));
            }
            pendingExportNames.clear();
            return true;
        }
        return false;
    }

    public void refresh() {
        if (cardList == null || statusSpinner == null || categorySpinner == null) return;
        ensureGalleryDir();
        List<CardEntry> cards = listCards();
        String oldStatus = selectedItem(statusSpinner, STATUS_ALL);
        String oldCategory = selectedItem(categorySpinner, ALL);
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.add(ALL);
        categories.add(DEFAULT_CATEGORY);
        for (CardEntry card : cards) categories.addAll(card.tags);

        updatingFilters = true;
        setSpinner(statusSpinner, STATUS_ALL, STATUS_IMPORTED, STATUS_NOT_IMPORTED);
        setSpinner(categorySpinner, new ArrayList<>(categories));
        selectSpinner(statusSpinner, oldStatus, STATUS_ALL);
        selectSpinner(categorySpinner, oldCategory, ALL);
        updatingFilters = false;
        Set<String> existing = new HashSet<>();
        for (CardEntry card : cards) existing.add(card.file.getName());
        selectedNames.retainAll(existing);
        renderCards();
    }

    private void configureFilterListeners() {
        if (statusSpinner == null) return;
        statusSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (!updatingFilters) renderCards();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                if (!updatingFilters) renderCards();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setSpinner(Spinner spinner, String... values) {
        ArrayList<String> list = new ArrayList<>();
        Collections.addAll(list, values);
        setSpinner(spinner, list);
    }

    private void setSpinner(Spinner spinner, List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
            android.R.layout.simple_spinner_item, values) {
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
        spinner.setAdapter(adapter);
    }

    private void selectSpinner(Spinner spinner, String value, String fallback) {
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) spinner.getAdapter();
        int selected = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (String.valueOf(adapter.getItem(i)).equals(value)) {
                selected = i;
                break;
            }
            if (String.valueOf(adapter.getItem(i)).equals(fallback)) selected = i;
        }
        spinner.setSelection(selected, false);
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

    private void importUris(List<Uri> uris) {
        if (uris.isEmpty()) return;
        new Thread(() -> {
            int imported = 0;
            for (Uri uri : uris) imported += importUri(uri);
            final int count = imported;
            activity.runOnUiThread(() -> {
                refresh();
                toast(count > 0 ? "已添加 " + count + " 张角色卡" : "没有可导入的角色卡文件");
            });
        }, "character-gallery-import").start();
    }

    private int importUri(Uri uri) {
        String name = safeName(displayName(uri));
        if (isArchive(name)) return importArchive(uri);
        if (!isSupportedCard(name)) {
            toast("已跳过不支持的文件: " + name);
            return 0;
        }
        File destination = uniqueFile(new File(galleryDir(), name));
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(destination, false)) {
            if (input == null) throw new IllegalStateException("文件内容不可读");
            copy(input, output);
            saveTags(destination, DEFAULT_CATEGORY);
            return 1;
        } catch (Exception e) {
            if (destination.exists()) destination.delete();
            toast("导入失败: " + e.getMessage());
            return 0;
        }
    }

    private int importArchive(Uri uri) {
        int count = 0;
        Map<String, File> cards = new HashMap<>();
        Map<String, byte[]> metadata = new HashMap<>();
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(input)) {
            if (input == null) throw new IllegalStateException("压缩包不可读");
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null && entries++ < 256) {
                if (entry.isDirectory()) continue;
                String path = entry.getName().replace('\\', '/');
                String name = safeName(new File(path).getName());
                if (path.startsWith("metadata/") && name.endsWith(META_SUFFIX)) {
                    metadata.put(name.substring(0, name.length() - META_SUFFIX.length()),
                        readBytes(zip, 1024 * 1024));
                } else if ((path.startsWith("cards/") || path.indexOf('/') < 0)
                    && isSupportedCard(name)) {
                    File destination = uniqueFile(new File(galleryDir(), name));
                    try (OutputStream output = new FileOutputStream(destination, false)) {
                        copy(zip, output);
                    }
                    cards.put(name, destination);
                    count++;
                }
                zip.closeEntry();
            }
            for (Map.Entry<String, byte[]> item : metadata.entrySet()) {
                File card = cards.get(item.getKey());
                if (card != null) {
                    try (OutputStream output = new FileOutputStream(metaFile(card), false)) {
                        output.write(item.getValue());
                    }
                }
            }
        } catch (Exception e) {
            toast("导入画廊压缩包失败: " + e.getMessage());
        }
        return count;
    }

    private void renderCards() {
        if (cardList == null) return;
        cardList.removeAllViews();
        List<CardEntry> cards = listCards();
        String status = selectedItem(statusSpinner, STATUS_ALL);
        String category = selectedItem(categorySpinner, ALL);
        List<CardEntry> visible = new ArrayList<>();
        for (CardEntry card : cards) {
            boolean statusMatch = STATUS_ALL.equals(status)
                || STATUS_IMPORTED.equals(status) == isImported(card.file);
            boolean categoryMatch = ALL.equals(category) || card.tags.contains(category);
            if (statusMatch && categoryMatch) visible.add(card);
        }
        GridLayout shelf = new GridLayout(activity);
        shelf.setColumnCount(2);
        int index = 0;
        for (CardEntry card : visible) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(index / 2, 1, 1f), GridLayout.spec(index % 2, 1, 1f));
            params.width = 0;
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            shelf.addView(buildShelfCard(card), params);
            index++;
        }
        if (visible.isEmpty()) {
            TextView empty = text(cards.isEmpty()
                ? "书架还是空的，点击“导入角色卡”开始添加。"
                : "当前筛选条件下没有角色卡。", 14, SillyUI.TEXT_SECONDARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            cardList.addView(empty, wrapParams(dp(12)));
        } else {
            cardList.addView(shelf, wrapParams(dp(4)));
            View shelfLine = new View(activity);
            shelfLine.setBackgroundColor(0xFFB98559);
            cardList.addView(shelfLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        }
        summaryView.setText(visible.size() + " / " + cards.size() + " 张角色卡");
        updateSelectionSummary();
    }

    private LinearLayout buildShelfCard(CardEntry entry) {
        boolean imported = isImported(entry.file);
        LinearLayout card = SillyUI.card(activity, null);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setMinimumHeight(dp(214));
        card.setOnClickListener(v -> showDetails(entry));

        CheckBox selector = new CheckBox(activity);
        selector.setText(imported ? STATUS_IMPORTED : STATUS_NOT_IMPORTED);
        selector.setTextColor(imported ? SillyUI.SUCCESS : SillyUI.TEXT_SECONDARY);
        selector.setTypeface(Typeface.DEFAULT_BOLD);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        SillyUI.styleCheckBox(activity, selector);
        selector.setChecked(selectedNames.contains(entry.file.getName()));
        selector.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selectedNames.add(entry.file.getName());
            else selectedNames.remove(entry.file.getName());
            updateSelectionSummary();
        });
        card.addView(selector, wrapParams(0));

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(SillyUI.roundRect(SillyUI.BG_INPUT, dp(10), SillyUI.DIVIDER, 1f));
        preview.setPadding(dp(5), dp(5), dp(5), dp(5));
        Bitmap bitmap = loadPreview(entry.file);
        if (bitmap != null) preview.setImageBitmap(bitmap);
        else preview.setImageResource(R.drawable.silly_mascot_cutout);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(106));
        imageParams.setMargins(0, dp(2), 0, dp(8));
        card.addView(preview, imageParams);

        TextView title = text(stripExtension(entry.file.getName()), 15, SillyUI.TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        card.addView(title, wrapParams(0));
        TextView tags = text("标签: " + joinTags(entry.tags), 11, SillyUI.ACCENT_TEXT);
        tags.setMaxLines(2);
        card.addView(tags, wrapParams(dp(5)));
        TextView tip = text("点击查看详情", 10, SillyUI.TEXT_HINT);
        card.addView(tip, wrapParams(dp(4)));
        return card;
    }

    private void showDetails(CardEntry entry) {
        boolean imported = isImported(entry.file);
        ScrollView scroll = new ScrollView(activity);
        TextView details = text(buildDetails(entry), 14, SillyUI.TEXT_PRIMARY);
        details.setPadding(dp(22), dp(4), dp(22), dp(4));
        scroll.addView(details);
        new AlertDialog.Builder(activity)
            .setTitle(stripExtension(entry.file.getName()))
            .setView(scroll)
            .setNegativeButton("删除", (dialog, which) -> confirmDelete(entry.file))
            .setNeutralButton("分类标签", (dialog, which) -> editTags(entry.file))
            .setPositiveButton(imported ? "重新导入" : "导入到酒馆",
                (dialog, which) -> importIntoSillyTavern(entry.file))
            .show();
    }

    private String buildDetails(CardEntry entry) {
        StringBuilder info = new StringBuilder();
        info.append("文件：").append(entry.file.getName()).append("\n");
        info.append("大小：").append(formatSize(entry.file.length())).append("\n");
        info.append("状态：").append(isImported(entry.file) ? STATUS_IMPORTED : STATUS_NOT_IMPORTED).append("\n");
        info.append("标签：").append(joinTags(entry.tags)).append("\n");
        String name = readCardField(entry.file, "name");
        String description = readCardField(entry.file, "description");
        String firstMessage = readCardField(entry.file, "first_mes");
        if (name != null && !name.equals(stripExtension(entry.file.getName()))) {
            info.append("\n角色名：").append(name);
        }
        if (description != null) info.append("\n\n简介：").append(trimForDisplay(description));
        if (firstMessage != null) info.append("\n\n开场白：").append(trimForDisplay(firstMessage));
        return info.toString();
    }

    private void editTags(File card) {
        android.widget.EditText input = new android.widget.EditText(activity);
        SillyUI.styleInput(activity, input);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("例如：科幻, 恋爱, 原创");
        input.setText(joinTags(readTags(card)));
        LinearLayout container = new LinearLayout(activity);
        container.setPadding(dp(22), dp(4), dp(22), 0);
        container.addView(input, wrapParams(0));
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
                selectedNames.remove(card.getName());
                boolean deleted = !card.exists() || card.delete();
                File meta = metaFile(card);
                if (meta.exists()) meta.delete();
                toast(deleted ? "已从画廊删除" : "删除失败");
                refresh();
            }).show();
    }

    private void selectAllVisible() {
        List<CardEntry> cards = visibleCards();
        for (CardEntry card : cards) selectedNames.add(card.file.getName());
        renderCards();
    }

    private void clearSelection() {
        selectedNames.clear();
        renderCards();
    }

    private List<CardEntry> visibleCards() {
        List<CardEntry> result = new ArrayList<>();
        String status = selectedItem(statusSpinner, STATUS_ALL);
        String category = selectedItem(categorySpinner, ALL);
        for (CardEntry card : listCards()) {
            boolean statusMatch = STATUS_ALL.equals(status)
                || STATUS_IMPORTED.equals(status) == isImported(card.file);
            boolean categoryMatch = ALL.equals(category) || card.tags.contains(category);
            if (statusMatch && categoryMatch) result.add(card);
        }
        return result;
    }

    private List<CardEntry> selectedCards() {
        List<CardEntry> result = new ArrayList<>();
        for (CardEntry card : listCards()) {
            if (selectedNames.contains(card.file.getName())) result.add(card);
        }
        return result;
    }

    private void batchImport() {
        List<CardEntry> cards = selectedCards();
        if (cards.isEmpty()) {
            toast("请先勾选角色卡");
            return;
        }
        File appDir = new File(TermuxConstants.TERMUX_HOME_DIR, "SillyTavern");
        if (!new File(appDir, "package.json").exists()) {
            toast("SillyTavern 尚未安装完成");
            return;
        }
        int conflicts = 0;
        for (CardEntry card : cards) {
            if (targetFile(card.file).exists()) conflicts++;
        }
        if (conflicts > 0) {
            new AlertDialog.Builder(activity)
                .setTitle("发现同名角色卡")
                .setMessage("有 " + conflicts + " 张卡已存在，批量导入将覆盖同名文件。")
                .setNegativeButton("取消", null)
                .setPositiveButton("覆盖并导入", (dialog, which) -> performBatchImport(cards))
                .show();
        } else {
            performBatchImport(cards);
        }
    }

    private void performBatchImport(List<CardEntry> cards) {
        new Thread(() -> {
            int imported = 0;
            for (CardEntry card : cards) {
                if (copyToSillyTavern(card.file, targetFile(card.file))) imported++;
            }
            int count = imported;
            activity.runOnUiThread(() -> {
                refresh();
                toast("批量导入完成：" + count + " / " + cards.size());
            });
        }, "character-gallery-batch-import").start();
    }

    private void createExportDocument() {
        List<CardEntry> cards = selectedCards();
        if (cards.isEmpty()) {
            toast("请先勾选要导出的角色卡");
            return;
        }
        pendingExportNames.clear();
        for (CardEntry card : cards) pendingExportNames.add(card.file.getName());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,
            "sillytavern-gallery-" + System.currentTimeMillis() + ARCHIVE_SUFFIX);
        try {
            activity.startActivityForResult(intent, EXPORT_REQUEST_CODE);
        } catch (Exception e) {
            pendingExportNames.clear();
            toast("无法打开保存位置: " + e.getMessage());
        }
    }

    private void exportTo(Uri uri, List<String> names) {
        new Thread(() -> {
            int count = 0;
            String failure = null;
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri);
                 ZipOutputStream zip = new ZipOutputStream(output)) {
                if (output == null) throw new IllegalStateException("无法创建导出文件");
                ZipEntry manifest = new ZipEntry("manifest.properties");
                zip.putNextEntry(manifest);
                zip.write("format=termux-silly-gallery-v1\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                for (String name : names) {
                    File card = new File(galleryDir(), name);
                    if (!card.exists()) continue;
                    zip.putNextEntry(new ZipEntry("cards/" + card.getName()));
                    try (InputStream input = new FileInputStream(card)) {
                        copy(input, zip);
                    }
                    zip.closeEntry();
                    File meta = metaFile(card);
                    if (meta.exists()) {
                        zip.putNextEntry(new ZipEntry("metadata/" + meta.getName()));
                        try (InputStream input = new FileInputStream(meta)) {
                            copy(input, zip);
                        }
                        zip.closeEntry();
                    }
                    count++;
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }
            int exported = count;
            String exportFailure = failure;
            activity.runOnUiThread(() -> toast(exportFailure == null
                ? "已导出 " + exported + " 张角色卡，可在其他设备导入"
                : "导出失败: " + exportFailure));
        }, "character-gallery-export").start();
    }

    private void importIntoSillyTavern(File card) {
        File appDir = new File(TermuxConstants.TERMUX_HOME_DIR, "SillyTavern");
        if (!new File(appDir, "package.json").exists()) {
            toast("SillyTavern 尚未安装完成");
            return;
        }
        File target = targetFile(card);
        if (target.exists()) {
            new AlertDialog.Builder(activity)
                .setTitle("角色卡已存在")
                .setMessage("是否覆盖 SillyTavern 中的同名角色卡？")
                .setNegativeButton("取消", null)
                .setPositiveButton("覆盖", (dialog, which) ->
                    performSingleImport(card, target))
                .show();
        } else {
            performSingleImport(card, target);
        }
    }

    private void performSingleImport(File card, File target) {
        new Thread(() -> {
            boolean ok = copyToSillyTavern(card, target);
            activity.runOnUiThread(() -> {
                refresh();
                toast(ok ? "已导入 SillyTavern: " + target.getName() : "导入 SillyTavern 失败");
            });
        }, "character-gallery-single-import").start();
    }

    private boolean copyToSillyTavern(File source, File target) {
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("无法创建 characters 目录");
            }
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = new FileOutputStream(target, false)) {
                copy(input, output);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
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
        try (InputStream input = new FileInputStream(metaFile(card))) {
            properties.load(input);
        } catch (Exception ignored) {}
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String raw = properties.getProperty("tags", DEFAULT_CATEGORY);
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
        return new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".termux-silly"),
            "character-gallery");
    }

    private File targetFile(File card) {
        return new File(new File(new File(TermuxConstants.TERMUX_HOME_DIR, "SillyTavern"),
            "public/characters"), card.getName());
    }

    private boolean isImported(File card) {
        return targetFile(card).isFile();
    }

    private void ensureGalleryDir() {
        File dir = galleryDir();
        if (!dir.exists()) dir.mkdirs();
    }

    private File metaFile(File card) {
        return new File(card.getParentFile(), card.getName() + META_SUFFIX);
    }

    private String selectedItem(Spinner spinner, String fallback) {
        Object item = spinner == null ? null : spinner.getSelectedItem();
        return item == null ? fallback : String.valueOf(item);
    }

    private void updateSelectionSummary() {
        if (selectedView != null) {
            selectedView.setText(selectedNames.isEmpty()
                ? "未选择角色卡" : "已选择 " + selectedNames.size() + " 张角色卡");
        }
    }

    private String displayName(Uri uri) {
        ContentResolver resolver = activity.getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                                            null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (Exception ignored) {}
        String path = uri == null ? null : uri.getLastPathSegment();
        return path == null ? "character-card.json" : path.substring(path.lastIndexOf('/') + 1);
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
        if (name == null) name = "character-card.json";
        String value = name.replaceAll("[\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (value.length() > 120) value = value.substring(0, 120);
        return value.isEmpty() ? "character-card.json" : value;
    }

    private boolean isArchive(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
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

    private String readCardField(File card, String key) {
        if (!card.getName().toLowerCase(Locale.ROOT).endsWith(".json")) return null;
        try {
            String content = new String(readFileBytes(card, 256 * 1024), StandardCharsets.UTF_8);
            String regex = "\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"";
            Matcher matcher = Pattern.compile(regex).matcher(content);
            if (!matcher.find()) return null;
            return unescape(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimForDisplay(String value) {
        if (value == null) return null;
        value = value.trim();
        return value.length() > 800 ? value.substring(0, 800) + "…" : value;
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r");
    }

    private byte[] readFileBytes(File file, int maxBytes) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return readBytes(input, maxBytes);
        }
    }

    private byte[] readBytes(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int allowed = Math.min(read, maxBytes - total);
            if (allowed > 0) output.write(buffer, 0, allowed);
            total += read;
            if (total >= maxBytes) break;
        }
        return output.toByteArray();
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

    private LinearLayout.LayoutParams wrapParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (topMargin > 0) params.setMargins(0, topMargin, 0, 0);
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
        activity.runOnUiThread(() ->
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
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
