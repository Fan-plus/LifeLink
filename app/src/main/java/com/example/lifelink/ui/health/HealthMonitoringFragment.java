package com.example.lifelink.ui.health;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;
import com.example.lifelink.data.health.HealthData;
import com.example.lifelink.data.health.HealthRepository;
// MPAndroidChart optional - avoid direct references to prevent runtime ClassNotFound
import android.widget.Toast;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.File;
import java.io.FileOutputStream;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class HealthMonitoringFragment extends Fragment {

    private HealthRepository repository;
    private View hrChartContainer;
    private TextView hrPlaceholder;
    private TextView aiReportText;
    private LinearLayout aiReportCard;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health_monitoring, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new HealthRepository(requireContext());

        Spinner spinner = view.findViewById(R.id.trend_selector);
        String[] items = new String[]{"心电图", "心率趋势", "血压趋势"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final View ecgCard = view.findViewById(R.id.ecg_card);
        final View hrCard = view.findViewById(R.id.hr_card);
        final View bpCard = view.findViewById(R.id.bp_card);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                ecgCard.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                hrCard.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                bpCard.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinner.setSelection(1);

        // chart placeholder (MPAndroidChart may be unavailable at runtime)
        hrChartContainer = view.findViewById(R.id.hr_preview);
        hrPlaceholder = view.findViewById(R.id.hr_placeholder);

        // fab sos
        View fab = view.findViewById(R.id.fab_sos);
        if (fab != null) {
            fab.setOnClickListener(v -> Toast.makeText(requireContext(), "一键求救触发（示例）", Toast.LENGTH_SHORT).show());
        }

        // AI report views
        Button btnAi = view.findViewById(R.id.btn_ai_report);
        aiReportCard = view.findViewById(R.id.ai_report_card);
        aiReportText = view.findViewById(R.id.ai_report_text);
        Button btnDownload = view.findViewById(R.id.btn_download_pdf);

        btnAi.setOnClickListener(v -> {
            // simulate analysis
            aiReportText.setText(generateAiReportText());
            aiReportCard.setVisibility(View.VISIBLE);
        });

        btnDownload.setOnClickListener(v -> {
            exportReportToPdf(aiReportText.getText().toString());
        });

        // seed simulated data, show summary and attempt to render chart
        seedSimulatedData();
        showHrSummary();
        try {
            setupHrChart();
        } catch (Throwable t) {
            // leave placeholder visible on any failure
            t.printStackTrace();
        }
    }

    private void setupHrChart() {
        List<HealthData> samples = repository.getLatest(100);
        if (samples == null || samples.isEmpty()) return;

        // remove placeholder and create LineChart
        hrPlaceholder.setVisibility(View.GONE);
        LineChart chart = new LineChart(requireContext());
        chart.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((ViewGroup) hrChartContainer).addView(chart);

        List<Entry> entries = new ArrayList<>();
        long baseTs = samples.get(0).timestamp;
        for (int i = 0; i < samples.size(); i++) {
            HealthData s = samples.get(i);
            float x = (s.timestamp - baseTs) / 60000f; // minutes offset
            entries.add(new Entry(x, s.heartRate));
        }

        LineDataSet ds = new LineDataSet(entries, "心率");
        ds.setColor(ContextCompat.getColor(requireContext(), R.color.purple_500));
        ds.setLineWidth(2f);
        ds.setCircleRadius(3f);
        ds.setDrawValues(false);

        LineData ld = new LineData(ds);
        chart.setData(ld);

        // X axis as minutes offset
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int minutes = Math.round(value);
                if (minutes % 5 == 0) return minutes + "m";
                return "";
            }
        });

        YAxis left = chart.getAxisLeft();
        YAxis right = chart.getAxisRight();
        right.setEnabled(false);
        left.setAxisMinimum(30f);
        left.setAxisMaximum(180f);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);

        chart.invalidate();
    }
    private void seedSimulatedData() {
        List<HealthData> existing = repository.getLatest(100);
        int existingCount = existing == null ? 0 : existing.size();
        int target = 30;
        if (existingCount >= target) return; // already sufficient

        long now = System.currentTimeMillis();
        Random rnd = new Random();
        // insert missing points so total becomes `target`
        for (int i = 0; i < (target - existingCount); i++) {
            // place older points first: earlier timestamps
            long minutesAgo = (target - 1) - i;
            long ts = now - minutesAgo * 60_000L - existingCount * 60_000L;
            int hr = 60 + rnd.nextInt(60); // 60-119
            int bps = 110 + rnd.nextInt(30);
            int bpd = 70 + rnd.nextInt(20);
            int spo2 = 95 + rnd.nextInt(5);
            repository.insertSample(ts, hr, bps, bpd, spo2);
        }
    }

    private void showHrSummary() {
        List<HealthData> samples = repository.getLatest(100);
        if (samples == null || samples.isEmpty()) {
            hrPlaceholder.setText("暂无心率数据");
            return;
        }
        int sum = 0; int cnt = 0;
        for (HealthData s : samples) {
            sum += s.heartRate; cnt++;
        }
        int avg = cnt == 0 ? 0 : (sum / cnt);
        hrPlaceholder.setText("心率（模拟）平均: " + avg + " bpm\n（图表不可用 — 依赖缺失时显示）");
    }

    private String generateAiReportText() {
        // simple simulated analysis
        return "AI分析（模拟）:\n\n\n\n\n\n\n\n\n\n\n\n\n\n最近心率平均值：约 78 bpm\n血压：在正常范围示意\n血氧：无明显异常\n\n说明：本报告为模拟数据生成的示例，仅供参考。";
    }

    private void exportReportToPdf(String text) {
        if (text == null || text.trim().isEmpty()) {
            // If no AI report yet, generate one automatically
            text = generateAiReportText();
            aiReportText.setText(text);
            aiReportCard.setVisibility(View.VISIBLE);
        }

        PdfDocument doc = null;
        try {
            doc = new PdfDocument();
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = doc.startPage(info);
            Canvas c = page.getCanvas();
            Paint p = new Paint();
            p.setTextSize(12f);
            int x = 40;
            int y = 60;
            for (String line : text.split("\\n")) {
                c.drawText(line, x, y, p);
                y += 18;
            }
            doc.finishPage(page);

            File base = new File(requireContext().getExternalFilesDir(null), "data/health");
            if (!base.exists() && !base.mkdirs()) {
                Toast.makeText(requireContext(), "无法创建目录: " + base.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            String name = "ai_report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";
            File out = new File(base, name);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.writeTo(fos);
            }

            Toast.makeText(requireContext(), "已导出 PDF: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.i("HealthFragment", "Exported PDF to " + out.getAbsolutePath());
        } catch (Exception e) {
            Log.e("HealthFragment", "Failed to export PDF", e);
            Toast.makeText(requireContext(), "导出 PDF 失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (doc != null) doc.close();
        }
    }
}
