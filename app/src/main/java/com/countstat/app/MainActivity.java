package com.countstat.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREF_NAME = "countstat_pref";
    private static final String KEY_DAV_URL = "dav_url";
    private static final String KEY_DAV_USER = "dav_user";
    private static final String KEY_DAV_PASS = "dav_pass";
    private static final String KEY_DAV_PATH = "dav_path";
    private static final String KEY_AUTO_SYNC = "auto_sync";
    private static final String KEY_SYNC_DELAY_MIN = "sync_delay_min";
    private static final String KEY_SEEDED = "seeded_v2";
    private static final String KEY_MACHINES_CONFIG = "machines_config";

    private final int bg = Color.rgb(8, 13, 24);
    private final int surface = Color.rgb(17, 28, 49);
    private final int surface2 = Color.rgb(23, 35, 58);
    private final int ink = Color.rgb(248, 250, 252);
    private final int muted = Color.rgb(148, 163, 184);
    private final int accent = Color.rgb(59, 130, 246);
    private final int accent2 = Color.rgb(56, 189, 248);
    private final int success = Color.rgb(16, 185, 129);
    private final int danger = Color.rgb(239, 68, 68);

    private SharedPreferences preferences;
    private DbHelper dbHelper;
    private AutoSyncManager syncManager;
    private FrameLayout content;
    private LinearLayout tabbar;
    private String activePage = "home";
    private String statsPeriod = "week";
    private String trendMode = "bar"; // bar | line
    private Record editingRecord;

    // 录入页控件
    private EditText dateInput;
    private final List<MachineRow> machineRows = new ArrayList<>();
    private LinearLayout machineListContainer;
    private TextView previewIncome;
    private TextView previewTotal;
    private TextView previewMachines;

    // 历史页控件
    private EditText filterStart;
    private EditText filterEnd;

    // 设置页控件
    private EditText davUrl, davUser, davPass, davPath;
    private CheckBox autoSyncCheck;
    private EditText syncDelayInput;
    private TextView syncStatus;
    private final List<ConfigRow> configRows = new ArrayList<>();
    private LinearLayout configListContainer;

    private final DecimalFormat moneyFormat = new DecimalFormat("0.000");
    private final DecimalFormat priceFormat = new DecimalFormat("0.000");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    /** 录入页一行机器输入。 */
    private static class MachineRow {
        EditText name;
        EditText quantity;
        EditText price;
        TextView total;
        View root;
    }

    /** 机器配置：名称、单价。 */
    private static class MachineConfig {
        String name;
        double unitPrice;
    }

    /** 机器配置页的一行输入。 */
    private static class ConfigRow {
        EditText name;
        EditText price;
        View root;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        dbHelper = new DbHelper(this);
        syncManager = new AutoSyncManager(this, dbHelper);
        applySyncConfig();
        clearDemoSeedRecordsIfNeeded();

        syncManager.setCallback(msg -> toast(msg));

        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(Color.rgb(10, 16, 30));
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        tabbar = new LinearLayout(this);
        tabbar.setOrientation(LinearLayout.HORIZONTAL);
        tabbar.setGravity(Gravity.CENTER);
        tabbar.setPadding(dp(10), dp(8), dp(10), dp(8 + getNavHeight()));
        tabbar.setBackgroundColor(Color.rgb(10, 16, 30));
        root.addView(tabbar, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        buildTabs();
        showPage("home");

        // 打开时自动拉取
        syncManager.pullOnOpen();
    }

    private int getNavHeight() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) return getResources().getDimensionPixelSize(resId);
        return dp(8);
    }

    private void applySyncConfig() {
        String url = preferences.getString(KEY_DAV_URL, "");
        String user = preferences.getString(KEY_DAV_USER, "");
        String pass = preferences.getString(KEY_DAV_PASS, "");
        String path = preferences.getString(KEY_DAV_PATH, "/计件备份/");
        boolean auto = preferences.getBoolean(KEY_AUTO_SYNC, false);
        long delay = preferences.getLong(KEY_SYNC_DELAY_MIN, 3);
        syncManager.configure(url, user, pass, path, auto, delay);
    }

    private void buildTabs() {
        tabbar.removeAllViews();
        addTab("home", "录入");
        addTab("history", "历史");
        addTab("stats", "统计");
        addTab("settings", "备份");
    }

    private void addTab(String key, String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(key.equals(activePage) ? ink : muted);
        button.setBackground(round(key.equals(activePage) ? Color.rgb(24, 48, 85) : Color.TRANSPARENT, 16, 0, 0));
        button.setOnClickListener(v -> showPage(key));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        tabbar.addView(button, params);
    }

    private void showPage(String page) {
        activePage = page;
        buildTabs();
        content.removeAllViews();
        ScrollView scroll = scroll();
        LinearLayout body = pageBody();
        scroll.addView(body);
        if ("home".equals(page)) {
            buildHome(body);
        } else if ("history".equals(page)) {
            buildHistory(body);
        } else if ("stats".equals(page)) {
            buildStats(body);
        } else if ("settings".equals(page)) {
            buildSettings(body);
        } else if ("detail".equals(page)) {
            buildDetail(body);
        } else {
            buildMachineConfig(body);
        }
        content.addView(scroll);
    }

    // ================= 首页录入 =================

    private void buildHome(LinearLayout body) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(header("CountStat", "首页录入", "配置机器后，只填写数量即可实时计算。"), new LinearLayout.LayoutParams(0, -2, 1));
        Button config = secondaryButton("配置");
        config.setOnClickListener(v -> showPage("machineConfig"));
        LinearLayout.LayoutParams configParams = new LinearLayout.LayoutParams(dp(72), dp(44));
        configParams.setMargins(dp(10), 0, 0, dp(16));
        top.addView(config, configParams);
        body.addView(top);

        LinearLayout hero = card();
        hero.addView(label("今日预览", accent2, 12, true));
        previewIncome = title("¥0.000", 30);
        hero.addView(previewIncome);
        hero.addView(text("根据各机器数量与单价实时计算", muted, 13));

        // 总件数和机器数：带卡片背景，放在今日预览内部
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(12), 0, 0);
        previewTotal = metric(metrics, "总件数", "0");
        previewMachines = metric(metrics, "机器数", "0");
        hero.addView(metrics);
        body.addView(hero);

        LinearLayout form = card();
        dateInput = input(today(), InputType.TYPE_CLASS_DATETIME);
        attachDatePicker(dateInput);
        form.addView(field("记录日期（点击选择）", dateInput));

        // 动态机器行容器
        machineRows.clear();
        machineListContainer = new LinearLayout(this);
        machineListContainer.setOrientation(LinearLayout.VERTICAL);
        form.addView(machineListContainer);
        List<MachineConfig> configs = loadMachineConfigs();
        if (configs.isEmpty()) {
            form.addView(empty("请先点击右上角“配置”添加机器名称和单价。"));
        } else {
            for (MachineConfig machine : configs) {
                addMachineRow(machine.name, "", priceFormat.format(machine.unitPrice));
            }
        }

        LinearLayout actions = row();
        Button clear = secondaryButton("清空");
        clear.setOnClickListener(v -> clearForm());
        Button save = primaryButton("保存今日记录");
        save.setOnClickListener(v -> saveRecord());
        actions.addView(clear, weightParams());
        actions.addView(save, weightParams());
        form.addView(actions);
        body.addView(form);

        body.addView(sectionTitle("最近记录", loadRecords().size() + " 条"));
        body.addView(recordList(loadRecent(3), false));

        updatePreview();
    }

    private MachineRow addMachineRow(String name, String qty, String price) {
        MachineRow row = new MachineRow();

        // 整行容器：竖向卡片
        LinearLayout rowView = new LinearLayout(this);
        rowView.setOrientation(LinearLayout.VERTICAL);
        rowView.setPadding(dp(14), dp(12), dp(14), dp(12));
        rowView.setBackground(round(surface2, 16, Color.rgb(45, 58, 82), 1));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, dp(4), 0, dp(4));
        rowView.setLayoutParams(rowParams);

        // 顶部：机器名（左）+ 总价（右）
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        row.name = input(name == null ? "M" + (machineRows.size() + 1) : name, InputType.TYPE_CLASS_TEXT);
        row.name.setEnabled(false);
        row.name.setTextColor(ink);
        row.name.setBackground(null);
        row.name.setPadding(0, 0, 0, 0);
        topBar.addView(row.name, new LinearLayout.LayoutParams(0, dp(44), 1));

        row.total = label("¥0.000", success, 17, true);
        row.total.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        topBar.addView(row.total, new LinearLayout.LayoutParams(-2, dp(44)));
        rowView.addView(topBar);

        // 数量输入框（独占一行，全宽）
        LinearLayout qtyRow = new LinearLayout(this);
        qtyRow.setOrientation(LinearLayout.HORIZONTAL);
        qtyRow.setGravity(Gravity.CENTER_VERTICAL);
        qtyRow.addView(text("数量", muted, 13), new LinearLayout.LayoutParams(-2, -2));
        row.quantity = input(qty, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        qtyRow.addView(row.quantity, new LinearLayout.LayoutParams(0, dp(44), 1));
        rowView.addView(qtyRow);

        // 单价隐藏存储，不显示
        row.price = input(price, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        row.price.setVisibility(View.GONE);

        row.root = rowView;
        machineRows.add(row);
        machineListContainer.addView(rowView);

        TextWatcher watcher = new SimpleWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                updatePreview();
            }
        };
        row.quantity.addTextChangedListener(watcher);
        row.price.addTextChangedListener(watcher);
        return row;
    }

    private LinearLayout wrapField(String label, EditText input) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.addView(text(label, muted, 11));
        input.setSingleLine(true);
        f.addView(input, new LinearLayout.LayoutParams(-1, dp(44)));
        return f;
    }

    private LinearLayout wrapText(String label, TextView value) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.addView(text(label, muted, 11));
        f.addView(value, new LinearLayout.LayoutParams(-1, dp(44)));
        return f;
    }

    private void fillRecord(Record record) {
        dateInput.setText(record.date);
        machineRows.clear();
        machineListContainer.removeAllViews();
        if (record.machines.isEmpty()) {
            for (MachineConfig machine : loadMachineConfigs()) {
                addMachineRow(machine.name, "", priceFormat.format(machine.unitPrice));
            }
        } else {
            for (Record.Machine m : record.machines) {
                addMachineRow(m.name, String.valueOf(m.quantity), priceFormat.format(m.unitPrice));
            }
        }
        updatePreview();
    }

    private void clearForm() {
        dateInput.setText(today());
        machineRows.clear();
        machineListContainer.removeAllViews();
        for (MachineConfig machine : loadMachineConfigs()) {
            addMachineRow(machine.name, "", priceFormat.format(machine.unitPrice));
        }
        updatePreview();
        toast("已清空录入内容");
    }

    private Record readFormRecord() {
        Record record = new Record();
        record.date = dateInput.getText().toString().trim();
        for (MachineRow row : machineRows) {
            String name = row.name.getText().toString().trim();
            if (name.isEmpty()) name = "M" + (record.machines.size() + 1);
            Record.Machine m = new Record.Machine();
            m.name = name;
            m.quantity = Math.max(0, (int) Math.round(number(row.quantity.getText().toString())));
            m.unitPrice = number(row.price.getText().toString());
            if (m.unitPrice <= 0) m.unitPrice = 0.35;
            record.machines.add(m);
        }
        return record;
    }

    private void updatePreview() {
        if (previewIncome == null) return;
        Record record = readFormRecord();
        previewTotal.setText(String.valueOf(record.total()));
        previewMachines.setText(String.valueOf(record.machines.size()));
        previewIncome.setText(money(record.income()));
        for (int i = 0; i < machineRows.size() && i < record.machines.size(); i++) {
            MachineRow row = machineRows.get(i);
            if (row.total != null) row.total.setText(money(record.machines.get(i).income()));
        }
    }

    private void saveRecord() {
        Record record = readFormRecord();
        if (record.machines.isEmpty()) {
            toast("请先在右上角配置机器");
            return;
        }
        if (record.date.isEmpty()) {
            toast("请填写记录日期");
            return;
        }
        if (record.total() <= 0) {
            toast("请至少录入一台机器的数量");
            return;
        }
        record.updatedAt = System.currentTimeMillis();
        dbHelper.upsert(record);
        toast("记录已保存");
        // 修改后 3 分钟自动上传
        syncManager.scheduleUpload();
        showPage("home");
    }

    // ================= 历史记录 =================

    private void buildHistory(LinearLayout body) {
        List<Record> records = sortedRecords();
        body.addView(header("History", "历史记录", "查看、筛选并回填修改已保存记录。"));

        LinearLayout filters = card();
        filterStart = input(startOfWeek(), InputType.TYPE_CLASS_DATETIME);
        filterEnd = input(today(), InputType.TYPE_CLASS_DATETIME);
        attachDatePicker(filterStart);
        attachDatePicker(filterEnd);
        filters.addView(field("开始日期", filterStart));
        filters.addView(field("结束日期", filterEnd));
        Button apply = primaryButton("筛选");
        LinearLayout listHolder = new LinearLayout(this);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        apply.setOnClickListener(v -> {
            listHolder.removeAllViews();
            listHolder.addView(recordList(filteredRecords(), true));
        });
        filters.addView(apply, fullParams(dp(48)));
        body.addView(filters);

        body.addView(sectionTitle("记录列表", records.size() + " 条"));
        listHolder.addView(recordList(filteredRecords(), true));
        body.addView(listHolder);
    }

    private List<Record> filteredRecords() {
        String start = filterStart == null ? "" : filterStart.getText().toString();
        String end = filterEnd == null ? "" : filterEnd.getText().toString();
        List<Record> result = new ArrayList<>();
        for (Record record : sortedRecords()) {
            if (!start.isEmpty() && record.date.compareTo(start) < 0) continue;
            if (!end.isEmpty() && record.date.compareTo(end) > 0) continue;
            result.add(record);
        }
        return result;
    }

    // ================= 汇总统计 =================

    private void buildStats(LinearLayout body) {
        body.addView(header("Summary", "汇总统计", "按周、月或全部聚合收入、总件数和机器贡献。"));
        LinearLayout segmented = row();
        segmented.addView(periodButton("week", "本周"), weightParams());
        segmented.addView(periodButton("month", "本月"), weightParams());
        segmented.addView(periodButton("all", "全部"), weightParams());
        body.addView(segmented);

        List<Record> records = periodRecords();
        int pieces = 0;
        double income = 0;
        Map<String, int[]> machineTotals = new LinkedHashMap<>();
        for (Record record : records) {
            pieces += record.total();
            income += record.income();
            for (Record.Machine m : record.machines) {
                int[] agg = machineTotals.get(m.name);
                if (agg == null) { agg = new int[]{0}; machineTotals.put(m.name, agg); }
                agg[0] += m.quantity;
            }
        }

        LinearLayout metricsA = row();
        TextView incomeMetric = metric(metricsA, "周期收入", money(income));
        metric(metricsA, "周期件数", String.valueOf(pieces));
        incomeMetric.setTextColor(success);
        body.addView(metricsA);

        LinearLayout metricsB = row();
        metric(metricsB, "记录天数", String.valueOf(records.size()));
        metric(metricsB, "日均件数", records.isEmpty() ? "0" : String.valueOf(Math.round((float) pieces / records.size())));
        body.addView(metricsB);

        LinearLayout machineCard = card();
        machineCard.addView(sectionTitle("机器产量分布", periodName()));
        int max = 1;
        for (int[] v : machineTotals.values()) max = Math.max(max, v[0]);
        if (machineTotals.isEmpty()) {
            machineCard.addView(empty("暂无机器数据"));
        } else {
            for (Map.Entry<String, int[]> e : machineTotals.entrySet()) {
                machineCard.addView(bar(e.getKey(), e.getValue()[0], max));
            }
        }
        body.addView(machineCard);

        LinearLayout trendCard = card();
        LinearLayout trendHeader = row();
        trendHeader.addView(label("最近趋势", ink, 16, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button modeBar = secondaryButton("柱状");
        Button modeLine = secondaryButton("折线");
        if ("bar".equals(trendMode)) {
            modeBar = primaryButton("柱状");
        } else {
            modeLine = primaryButton("折线");
        }
        modeBar.setOnClickListener(v -> {
            trendMode = "bar";
            showPage("stats");
        });
        modeLine.setOnClickListener(v -> {
            trendMode = "line";
            showPage("stats");
        });
        trendHeader.addView(modeBar, new LinearLayout.LayoutParams(dp(64), dp(36)));
        trendHeader.addView(modeLine, new LinearLayout.LayoutParams(dp(64), dp(36)));
        trendCard.addView(trendHeader);

        List<Record> trend = new ArrayList<>(records);
        Collections.reverse(trend);
        int maxTrend = 1;
        for (Record record : trend) maxTrend = Math.max(maxTrend, record.total());
        int start = Math.max(0, trend.size() - 7);
        if (trend.isEmpty()) {
            trendCard.addView(empty("暂无可统计数据"));
        } else if ("line".equals(trendMode)) {
            trendCard.addView(lineChart(trend.subList(start, trend.size()), maxTrend));
        } else {
            for (int i = start; i < trend.size(); i++) {
                Record record = trend.get(i);
                trendCard.addView(bar(record.date.substring(5), record.total(), maxTrend));
            }
        }
        body.addView(trendCard);
    }

    private Button periodButton(String key, String label) {
        Button button = key.equals(statsPeriod) ? primaryButton(label) : secondaryButton(label);
        button.setOnClickListener(v -> {
            statsPeriod = key;
            showPage("stats");
        });
        return button;
    }

    private List<Record> periodRecords() {
        List<Record> result = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        String monthPrefix = new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(now.getTime());
        String weekStart = startOfWeek();
        for (Record record : sortedRecords()) {
            if ("all".equals(statsPeriod)) {
                result.add(record);
            } else if ("month".equals(statsPeriod) && record.date.startsWith(monthPrefix)) {
                result.add(record);
            } else if ("week".equals(statsPeriod) && record.date.compareTo(weekStart) >= 0 && record.date.compareTo(today()) <= 0) {
                result.add(record);
            }
        }
        return result;
    }

    private String periodName() {
        if ("month".equals(statsPeriod)) return "本月";
        if ("all".equals(statsPeriod)) return "全部";
        return "本周";
    }

    // ================= 机器配置 =================

    private void buildMachineConfig(LinearLayout body) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(header("Machines", "机器配置", "在这里维护机器名称和单价，首页只填写数量。"), new LinearLayout.LayoutParams(0, -2, 1));
        Button back = secondaryButton("返回");
        back.setOnClickListener(v -> showPage("home"));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(72), dp(44));
        backParams.setMargins(dp(10), 0, 0, dp(16));
        top.addView(back, backParams);
        body.addView(top);

        LinearLayout form = card();
        configRows.clear();
        configListContainer = new LinearLayout(this);
        configListContainer.setOrientation(LinearLayout.VERTICAL);
        form.addView(configListContainer);

        List<MachineConfig> configs = loadMachineConfigs();
        if (configs.isEmpty()) {
            addConfigRow("M1", "0.35");
        } else {
            for (MachineConfig machine : configs) {
                addConfigRow(machine.name, moneyFormat.format(machine.unitPrice));
            }
        }

        LinearLayout actions = row();
        Button add = secondaryButton("+ 添加机器");
        add.setOnClickListener(v -> addConfigRow("M" + (configRows.size() + 1), "0.35"));
        Button save = primaryButton("保存配置");
        save.setOnClickListener(v -> saveMachineConfig());
        actions.addView(add, weightParams());
        actions.addView(save, weightParams());
        form.addView(actions);
        body.addView(form);
    }

    private ConfigRow addConfigRow(String name, String price) {
        ConfigRow row = new ConfigRow();
        LinearLayout rowView = new LinearLayout(this);
        rowView.setOrientation(LinearLayout.HORIZONTAL);
        rowView.setGravity(Gravity.CENTER_VERTICAL);
        rowView.setPadding(0, dp(4), 0, dp(4));

        row.name = input(name, InputType.TYPE_CLASS_TEXT);
        row.price = input(price, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        rowView.addView(wrapField("机器名称", row.name), new LinearLayout.LayoutParams(0, -2, 2));
        rowView.addView(wrapField("单价", row.price), new LinearLayout.LayoutParams(0, -2, 1));

        Button del = dangerButton("×");
        del.setTextSize(18);
        del.setOnClickListener(v -> {
            if (configRows.size() <= 1) {
                toast("至少保留一台机器");
                return;
            }
            configRows.remove(row);
            configListContainer.removeView(row.root);
        });
        LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        delParams.setMargins(dp(6), dp(22), 0, 0);
        rowView.addView(del, delParams);

        row.root = rowView;
        configRows.add(row);
        configListContainer.addView(rowView);
        return row;
    }

    private void saveMachineConfig() {
        List<MachineConfig> configs = new ArrayList<>();
        for (ConfigRow row : configRows) {
            String name = row.name.getText().toString().trim();
            double price = number(row.price.getText().toString());
            if (name.isEmpty()) {
                toast("机器名称不能为空");
                return;
            }
            if (price <= 0) {
                toast("单价必须大于 0");
                return;
            }
            MachineConfig machine = new MachineConfig();
            machine.name = name;
            machine.unitPrice = price;
            configs.add(machine);
        }
        saveMachineConfigs(configs);
        toast("机器配置已保存");
        showPage("home");
    }

    // ================= WebDAV 备份 =================

    private void buildSettings(LinearLayout body) {
        body.addView(header("Backup", "WebDAV 备份", "本地数据库为主，WebDAV 可自动同步。"));

        LinearLayout form = card();
        davUrl = input(preferences.getString(KEY_DAV_URL, ""), InputType.TYPE_TEXT_VARIATION_URI);
        davUser = input(preferences.getString(KEY_DAV_USER, ""), InputType.TYPE_CLASS_TEXT);
        davPass = input(preferences.getString(KEY_DAV_PASS, ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        davPath = input(preferences.getString(KEY_DAV_PATH, "/计件备份/"), InputType.TYPE_CLASS_TEXT);
        form.addView(field("WebDAV 地址", davUrl));
        form.addView(field("用户名", davUser));
        form.addView(field("密码", davPass));
        form.addView(field("备份目录", davPath));

        // 自动同步开关
        LinearLayout syncRow = new LinearLayout(this);
        syncRow.setOrientation(LinearLayout.HORIZONTAL);
        syncRow.setGravity(Gravity.CENTER_VERTICAL);
        autoSyncCheck = new CheckBox(this);
        autoSyncCheck.setText("打开应用时自动同步");
        autoSyncCheck.setTextColor(ink);
        autoSyncCheck.setTextSize(14);
        autoSyncCheck.setChecked(preferences.getBoolean(KEY_AUTO_SYNC, false));
        syncRow.addView(autoSyncCheck, new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(syncRow);

        LinearLayout delayRow = row();
        delayRow.addView(text("修改后自动上传延迟（分钟）", muted, 13), new LinearLayout.LayoutParams(0, -2, 2));
        syncDelayInput = input(String.valueOf(preferences.getLong(KEY_SYNC_DELAY_MIN, 3)),
                InputType.TYPE_CLASS_NUMBER);
        delayRow.addView(syncDelayInput, new LinearLayout.LayoutParams(0, dp(44), 1));
        form.addView(delayRow);

        LinearLayout actions = row();
        Button test = secondaryButton("测试连接");
        test.setOnClickListener(v -> {
            toast("正在测试连接…");
            new Thread(() -> {
                WebDavClient client = new WebDavClient(davUrl.getText().toString(),
                        davUser.getText().toString(), davPass.getText().toString(),
                        davPath.getText().toString());
                boolean ok = client.testConnection();
                runOnUiThread(() -> toast(ok ? "连接测试通过" : "连接失败，请检查配置"));
            }).start();
        });
        Button save = primaryButton("保存配置");
        save.setOnClickListener(v -> {
            preferences.edit()
                    .putString(KEY_DAV_URL, davUrl.getText().toString())
                    .putString(KEY_DAV_USER, davUser.getText().toString())
                    .putString(KEY_DAV_PASS, davPass.getText().toString())
                    .putString(KEY_DAV_PATH, davPath.getText().toString())
                    .putBoolean(KEY_AUTO_SYNC, autoSyncCheck.isChecked())
                    .putLong(KEY_SYNC_DELAY_MIN, Math.max(1, (long) number(syncDelayInput.getText().toString())))
                    .apply();
            applySyncConfig();
            toast("WebDAV 配置已保存");
        });
        actions.addView(test, weightParams());
        actions.addView(save, weightParams());
        form.addView(actions);
        body.addView(form);

        LinearLayout backup = card();
        backup.addView(sectionTitle("手动备份", ""));
        syncStatus = text("未备份", muted, 13);
        backup.addView(syncStatus);
        Button backupNow = primaryButton("立即备份到 WebDAV");
        backupNow.setOnClickListener(v -> {
            syncStatus.setText("备份中…");
            syncManager.uploadNow();
        });
        backup.addView(backupNow, fullParams(dp(48)));
        body.addView(backup);

        LinearLayout restore = card();
        restore.addView(sectionTitle("可恢复备份", "从 WebDAV 拉取"));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.VERTICAL);
        new Thread(() -> {
            WebDavClient client = new WebDavClient(
                    preferences.getString(KEY_DAV_URL, ""),
                    preferences.getString(KEY_DAV_USER, ""),
                    "",
                    preferences.getString(KEY_DAV_PATH, "/计件备份/"));
            List<String> files = client.listBackups();
            runOnUiThread(() -> {
                if (files.isEmpty()) {
                    restore.addView(text("远程暂无备份文件", muted, 13));
                    return;
                }
                Collections.sort(files, Collections.reverseOrder());
                for (int i = 0; i < Math.min(files.size(), 8); i++) {
                    RadioButton rb = new RadioButton(this);
                    rb.setText(files.get(i));
                    rb.setTextColor(ink);
                    rb.setTextSize(12);
                    rb.setId(View.generateViewId());
                    if (i == 0) rb.setChecked(true);
                    group.addView(rb);
                }
            });
        }).start();
        restore.addView(group);
        Button restoreBtn = dangerButton("恢复选中备份");
        restoreBtn.setOnClickListener(v -> {
            int id = group.getCheckedRadioButtonId();
            if (id == -1) {
                toast("请选择一个备份");
                return;
            }
            RadioButton rb = findViewById(id);
            if (rb == null) return;
            String name = rb.getText().toString();
            toast("正在恢复 " + name);
            new Thread(() -> {
                WebDavClient client = new WebDavClient(
                        preferences.getString(KEY_DAV_URL, ""),
                        preferences.getString(KEY_DAV_USER, ""),
                        "",
                        preferences.getString(KEY_DAV_PATH, "/计件备份/"));
                File tmp = new File(getCacheDir(), "restore_countstat.db");
                if (client.download(name, tmp)) {
                    dbHelper.close();
                    File local = dbHelper.getDbFile();
                    local.getParentFile().mkdirs();
                    try (java.io.FileInputStream in = new java.io.FileInputStream(tmp);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(local)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    } catch (Exception ignored) {
                    }
                    dbHelper.getReadableDatabase();
                    runOnUiThread(() -> {
                        toast("已恢复 " + name);
                        showPage("home");
                    });
                } else {
                    runOnUiThread(() -> toast("恢复失败"));
                }
            }).start();
        });
        restore.addView(restoreBtn, fullParams(dp(48)));
        body.addView(restore);
    }

    // ================= 详情查看与修改 =================

    private void buildDetail(LinearLayout body) {
        if (editingRecord == null) {
            showPage("history");
            return;
        }

        // 返回按钮 + 标题
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = secondaryButton("← 返回");
        back.setOnClickListener(v -> showPage("history"));
        top.addView(back);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(0, -2, 1);
        headerParams.setMargins(dp(12), 0, 0, 0);
        top.addView(header("记录详情", editingRecord.date, "查看并修改各机器数量"), headerParams);
        body.addView(top);

        // 日期（可改）
        EditText dateField = input(editingRecord.date, 0x00000000);
        attachDatePicker(dateField);
        body.addView(field("日期", dateField));

        // 汇总卡片
        LinearLayout hero = card();
        hero.addView(label("总收入", accent2, 12, true));
        TextView detailIncome = title(money(editingRecord.income()), 30);
        hero.addView(detailIncome);
        hero.addView(text(editingRecord.total() + " 件 · " + editingRecord.machines.size() + " 台机器", muted, 13));
        body.addView(hero);

        // 各机器数量编辑
        body.addView(label("各机器数量", ink, 15, true));

        List<DetailRow> detailRows = new ArrayList<>();
        for (Record.Machine m : editingRecord.machines) {
            LinearLayout rowCard = card();
            LinearLayout rowTop = new LinearLayout(this);
            rowTop.setOrientation(LinearLayout.HORIZONTAL);
            rowTop.setGravity(Gravity.CENTER_VERTICAL);
            TextView nameLabel = label(m.name, ink, 15, true);
            rowTop.addView(nameLabel, new LinearLayout.LayoutParams(0, -2, 1));
            TextView rowTotal = label(money(m.quantity * m.unitPrice), success, 17, true);
            rowTop.addView(rowTotal);
            rowCard.addView(rowTop);

            EditText qtyInput = input(String.valueOf(m.quantity), 0x00002002); // TYPE_CLASS_NUMBER | TYPE_NUMBER_FLAG_DECIMAL
            qtyInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    double q = number(s.toString());
                    rowTotal.setText(money(q * m.unitPrice));
                    // 重新计算总收入
                    double totalIncome = 0;
                    for (DetailRow dr : detailRows) {
                        double qq = number(dr.qty.getText().toString());
                        totalIncome += qq * dr.unitPrice;
                    }
                    detailIncome.setText(money(totalIncome));
                }
            });
            rowCard.addView(field("数量", qtyInput));

            DetailRow dr = new DetailRow();
            dr.name = m.name;
            dr.unitPrice = m.unitPrice;
            dr.qty = qtyInput;
            detailRows.add(dr);

            body.addView(rowCard);
        }

        // 保存按钮
        Button saveBtn = primaryButton("保存修改");
        saveBtn.setOnClickListener(v -> {
            String newDate = dateField.getText().toString().trim();
            if (newDate.isEmpty()) {
                toast("请填写日期");
                return;
            }
            List<Record.Machine> machines = new ArrayList<>();
            for (DetailRow dr : detailRows) {
                int q = (int) number(dr.qty.getText().toString());
                if (q > 0) {
                    Record.Machine m = new Record.Machine();
                    m.name = dr.name;
                    m.quantity = q;
                    m.unitPrice = dr.unitPrice;
                    machines.add(m);
                }
            }
            if (machines.isEmpty()) {
                toast("至少输入一台机器的数量");
                return;
            }
            Record updated = new Record();
            updated.date = newDate;
            updated.machines.addAll(machines);
            updated.updatedAt = System.currentTimeMillis();
            // 如果日期变了，先删旧的
            if (!newDate.equals(editingRecord.date)) {
                dbHelper.delete(editingRecord.date);
            }
            dbHelper.upsert(updated);
            syncManager.scheduleUpload();
            toast("已保存修改");
            editingRecord = updated;
            showPage("history");
        });
        LinearLayout.LayoutParams saveParams = fullParams(dp(52));
        saveParams.setMargins(0, dp(16), 0, 0);
        body.addView(saveBtn, saveParams);
    }

    private static class DetailRow {
        String name;
        double unitPrice;
        EditText qty;
    }

    // ================= 记录列表与数据辅助 =================

    private LinearLayout recordList(List<Record> records, boolean editable) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (records.isEmpty()) {
            list.addView(empty("暂无记录，先在首页保存一条数据。"));
            return list;
        }
        for (Record record : records) {
            LinearLayout item = card();
            item.setOnClickListener(v -> {
                editingRecord = record;
                showPage("detail");
            });
            item.addView(label(record.date, ink, 16, true));
            TextView summary = text(record.total() + " 件 · 收入 " + money(record.income()), success, 14);
            summary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            item.addView(summary);

            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            for (Record.Machine m : record.machines) {
                TextView chip = text(m.name + " " + m.quantity + " @" + moneyFormat.format(m.unitPrice), muted, 12);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(12), dp(8), dp(12), dp(8));
                chip.setBackground(round(Color.rgb(9, 15, 28), 12, 0, 0));
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2);
                chipParams.setMargins(0, dp(10), dp(8), dp(8));
                chips.addView(chip, chipParams);
            }
            hScroll.addView(chips);
            item.addView(hScroll);

            if (editable) {
                LinearLayout rowActions = row();
                Button edit = secondaryButton("回填修改");
                edit.setOnClickListener(v -> {
                    showPage("home");
                    fillRecord(record);
                    toast("已回填记录，可修改后保存");
                });
                Button del = dangerButton("删除");
                del.setOnClickListener(v -> {
                    dbHelper.delete(record.date);
                    toast("已删除 " + record.date);
                    syncManager.scheduleUpload();
                    showPage("history");
                });
                rowActions.addView(edit, weightParams());
                rowActions.addView(del, weightParams());
                item.addView(rowActions);
            }
            list.addView(item);
        }
        return list;
    }

    private List<Record> loadRecent(int count) {
        List<Record> records = sortedRecords();
        return records.subList(0, Math.min(count, records.size()));
    }

    private List<Record> sortedRecords() {
        List<Record> records = dbHelper.getAllRecords();
        Collections.sort(records, (a, b) -> b.date.compareTo(a.date));
        return records;
    }

    private List<Record> loadRecords() {
        return dbHelper.getAllRecords();
    }

    private void clearDemoSeedRecordsIfNeeded() {
        if (!preferences.getBoolean(KEY_SEEDED, false)) return;
        for (Record record : dbHelper.getAllRecords()) {
            if (isDemoSeedRecord(record)) dbHelper.delete(record.date);
        }
        preferences.edit().putBoolean(KEY_SEEDED, false).apply();
    }

    private boolean isDemoSeedRecord(Record record) {
        if ("2026-07-30".equals(record.date)) return record.total() == 147 && Math.abs(record.income() - 54.15) < 0.01;
        if ("2026-07-29".equals(record.date)) return record.total() == 94 && Math.abs(record.income() - 35.50) < 0.01;
        if ("2026-07-28".equals(record.date)) return record.total() == 80 && Math.abs(record.income() - 28.00) < 0.01;
        if ("2026-07-27".equals(record.date)) return record.total() == 94 && Math.abs(record.income() - 35.40) < 0.01;
        if ("2026-07-26".equals(record.date)) return record.total() == 35 && Math.abs(record.income() - 12.25) < 0.01;
        return false;
    }

    private List<MachineConfig> loadMachineConfigs() {
        List<MachineConfig> result = new ArrayList<>();
        String json = preferences.getString(KEY_MACHINES_CONFIG, "");
        if (json == null || json.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.optString("name", "").trim();
                double price = obj.optDouble("price", 0);
                if (name.isEmpty() || price <= 0) continue;
                MachineConfig machine = new MachineConfig();
                machine.name = name;
                machine.unitPrice = price;
                result.add(machine);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void saveMachineConfigs(List<MachineConfig> configs) {
        JSONArray array = new JSONArray();
        try {
            for (MachineConfig machine : configs) {
                JSONObject obj = new JSONObject();
                obj.put("name", machine.name);
                obj.put("price", machine.unitPrice);
                array.put(obj);
            }
        } catch (Exception ignored) {
        }
        preferences.edit().putString(KEY_MACHINES_CONFIG, array.toString()).apply();
    }

    private String money(double value) {
        return "¥" + moneyFormat.format(value);
    }

    private String today() {
        return dateFormat.format(new Date());
    }

    private void attachDatePicker(EditText input) {
        input.setFocusable(false);
        input.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance(Locale.CHINA);
            try {
                Date date = dateFormat.parse(input.getText().toString().trim());
                if (date != null) calendar.setTime(date);
            } catch (Exception ignored) {
            }
            new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar selected = Calendar.getInstance(Locale.CHINA);
                        selected.set(year, month, dayOfMonth);
                        input.setText(dateFormat.format(selected.getTime()));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private String startOfWeek() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        int diff = day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day;
        calendar.add(Calendar.DAY_OF_MONTH, diff);
        return dateFormat.format(calendar.getTime());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ================= UI 组件工厂 =================

    private LinearLayout header(String eyebrow, String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(18));
        box.addView(label(eyebrow, accent2, 12, true));
        box.addView(title(title, 26));
        box.addView(text(subtitle, muted, 13));
        return box;
    }

    private LinearLayout sectionTitle(String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(8));
        TextView a = label(left, ink, 16, true);
        TextView b = text(right, muted, 12);
        b.setGravity(Gravity.RIGHT);
        row.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout field(String label, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(0, dp(6), 0, dp(8));
        field.addView(text(label, muted, 13));
        field.addView(input, fullParams(dp(48)));
        return field;
    }

    private EditText input(String value, int type) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(ink);
        input.setHintTextColor(Color.rgb(100, 116, 139));
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setInputType(type);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(round(Color.rgb(7, 12, 23), 14, Color.rgb(45, 58, 82), 1));
        return input;
    }

    private TextView metric(LinearLayout parent, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(round(Color.rgb(12, 20, 37), 14, Color.rgb(35, 48, 72), 1));
        box.addView(text(label, muted, 11));
        TextView number = label(value, ink, 20, true);
        box.addView(number);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(box, params);
        return number;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(surface, 18, Color.rgb(42, 55, 82), 1));
        LinearLayout.LayoutParams params = fullParams(-2);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        // clipToPadding=false 让内容能滚到 padding 区域，避免被 tabbar 遮挡
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(16));
        return scroll;
    }

    private LinearLayout pageBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18) + getStatusBarHeight(), dp(18), dp(24));
        return body;
    }

    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) return getResources().getDimensionPixelSize(resId);
        return dp(24);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        return row;
    }

    private TextView title(String text, int size) {
        return label(text, ink, size, true);
    }

    private TextView label(String text, int color, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView text(String text, int color, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setLineSpacing(dp(2), 1.08f);
        return view;
    }

    private TextView empty(String text) {
        TextView view = text(text, muted, 14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(24), dp(16), dp(24));
        view.setBackground(round(Color.rgb(12, 20, 37), 16, Color.rgb(42, 55, 82), 1));
        return view;
    }

    private Button primaryButton(String text) {
        return button(text, accent, ink);
    }

    private Button secondaryButton(String text) {
        return button(text, surface2, ink);
    }

    private Button dangerButton(String text) {
        return button(text, danger, ink);
    }

    private Button button(String text, int color, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(color, 14, 0, 0));
        return button;
    }

    private LinearLayout bar(String label, int value, int max) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView name = text(label, muted, 12);
        row.addView(name, new LinearLayout.LayoutParams(dp(60), -2));

        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(Color.rgb(31, 41, 59), 999, 0, 0));
        View fill = new View(this);
        fill.setBackground(round(accent, 999, 0, 0));
        int width = Math.max(dp(4), (int) (dp(170) * (value / (float) Math.max(max, 1))));
        track.addView(fill, new FrameLayout.LayoutParams(width, dp(10)));
        row.addView(track, new LinearLayout.LayoutParams(0, dp(10), 1));

        TextView number = text(String.valueOf(value), ink, 13);
        number.setGravity(Gravity.RIGHT);
        number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(number, new LinearLayout.LayoutParams(dp(64), -2));
        return row;
    }

    private View lineChart(List<Record> records, int maxVal) {
        android.graphics.Paint linePaint = new android.graphics.Paint();
        linePaint.setColor(accent);
        linePaint.setStrokeWidth((float) dp(3));
        linePaint.setStyle(android.graphics.Paint.Style.STROKE);
        linePaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        linePaint.setAntiAlias(true);

        android.graphics.Paint dotPaint = new android.graphics.Paint();
        dotPaint.setColor(accent);
        dotPaint.setStyle(android.graphics.Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        android.graphics.Paint gridPaint = new android.graphics.Paint();
        gridPaint.setColor(Color.rgb(31, 41, 59));
        gridPaint.setStrokeWidth(dp(1));

        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setColor(muted);
        textPaint.setTextSize(dp(11));
        textPaint.setAntiAlias(true);

        View chart = new View(this) {
            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                int w = getWidth();
                int h = getHeight();
                int padLeft = dp(8);
                int padRight = dp(8);
                int padTop = dp(16);
                int padBottom = dp(28);
                int chartW = w - padLeft - padRight;
                int chartH = h - padTop - padBottom;
                int n = records.size();
                if (n <= 0) return;

                // 网格线（3条横线）
                for (int i = 0; i <= 3; i++) {
                    float y = padTop + chartH * i / 3f;
                    canvas.drawLine(padLeft, y, w - padRight, y, gridPaint);
                }

                // 计算点坐标
                float[] points = new float[n * 2];
                for (int i = 0; i < n; i++) {
                    float x = n == 1 ? padLeft + chartW / 2f : padLeft + chartW * i / (float) (n - 1);
                    float ratio = maxVal == 0 ? 0 : records.get(i).total() / (float) maxVal;
                    float y = padTop + chartH * (1 - ratio);
                    points[i * 2] = x;
                    points[i * 2 + 1] = y;
                }

                // 折线
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(points[0], points[1]);
                for (int i = 1; i < n; i++) {
                    path.lineTo(points[i * 2], points[i * 2 + 1]);
                }
                canvas.drawPath(path, linePaint);

                // 数据点
                for (int i = 0; i < n; i++) {
                    canvas.drawCircle(points[i * 2], points[i * 2 + 1], (float) dp(4), dotPaint);
                }

                // X轴日期标签
                for (int i = 0; i < n; i++) {
                    String date = records.get(i).date.substring(5);
                    float x = points[i * 2];
                    float y = h - dp(8);
                    String label = n <= 3 ? date : (i % 2 == 0 ? date : "");
                    if (!label.isEmpty()) {
                        float tw = textPaint.measureText(label);
                        canvas.drawText(label, x - tw / 2, y, textPaint);
                    }
                }
            }
        };
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(180));
        params.setMargins(0, dp(8), 0, dp(4));
        chart.setLayoutParams(params);
        return chart;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullParams(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private double number(String value) {
        try {
            String normalized = value == null ? "" : value.trim()
                    .replace("，", ".")
                    .replace(",", ".")
                    .replace("。", ".")
                    .replace(" ", "")
                    .replace("\u00A0", "");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < normalized.length(); i++) {
                char ch = normalized.charAt(i);
                if (ch >= '０' && ch <= '９') {
                    builder.append((char) ('0' + (ch - '０')));
                } else if (ch == '．') {
                    builder.append('.');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    builder.append(ch);
                }
            }
            String cleaned = builder.toString();
            if (cleaned.isEmpty()) return 0;
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
