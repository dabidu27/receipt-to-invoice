package com.example.receipt_to_invoice.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.receipt_to_invoice.R;

import java.util.Calendar;
import java.util.HashMap;

public class PreviewReceiptDataActivity extends AppCompatActivity {

    public final static String DATA_EXTRA = "data";

    private EditText etClientCui;
    private EditText etTotal;
    private EditText etTVA;
    private DatePicker dtData;

    private Button btnToInvoice;

    public static class ParsedDate {
        public final int year;
        public final int month; // 1 - 12 (calendar month)
        public final int day;   // 1 - 31

        public ParsedDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        //Returns 0-based month (0 - 11) for Android's DatePicker or java.util.Calendar
        public int getDatePickerMonth() {
            return Math.max(0, month - 1);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_preview_receipt_data);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setTitle(R.string.genereaza_factura);
        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupControls();

        Intent intent = getIntent();
        HashMap<String, String> receiptData = (HashMap<String, String>) intent.getSerializableExtra(DATA_EXTRA);
        String rawDate = null;
        if (receiptData != null) {
            etClientCui.setText(receiptData.get("clientCui") != null ? receiptData.get("clientCui") : getString(R.string.nu_s_a_gasit_cui_client));
            etTotal.setText(receiptData.get("total") != null ? receiptData.get("total") : getString(R.string.default_zero));
            etTVA.setText(receiptData.get("vat") != null ? receiptData.get("vat") : getString(R.string.default_zero));
            rawDate = receiptData.get("date");
        }

        ParsedDate date = parseDate(rawDate);
        dtData.init(date.year, date.getDatePickerMonth(), date.day, null);
        btnToInvoice.setOnClickListener(v -> {

            //Validation
            EditText invalidField = null;
            if(etClientCui.getText().toString().trim().isEmpty()){
                etClientCui.setError(getString(R.string.camp_gol));
                etClientCui.requestFocus();
                invalidField = etClientCui;
            } else if(etTotal.getText().toString().trim().isEmpty()){
                etTotal.setError(getString(R.string.camp_gol));
                etTotal.requestFocus();
                invalidField = etTotal;
            } else if(etTVA.getText().toString().trim().isEmpty()){
                etTVA.setError(getString(R.string.camp_gol));
                etTVA.requestFocus();
                invalidField = etTVA;
            }

            if(invalidField != null)
                return;

            //Go the invoice preview
            Intent pdfPreview = new Intent(this, PdfPreviewActivity.class);
            startActivity(pdfPreview);

        });
    }

    public static ParsedDate parseDate(String dateStr) {
        Calendar calendar = Calendar.getInstance();
        int defaultYear = calendar.get(Calendar.YEAR);
        int defaultMonth = calendar.get(Calendar.MONTH) + 1; // 1-12
        int defaultDay = calendar.get(Calendar.DAY_OF_MONTH);

        if (dateStr == null || dateStr.trim().isEmpty()) {
            return new ParsedDate(defaultYear, defaultMonth, defaultDay);
        }

        String[] parts = dateStr.trim().split("[-./]");
        if (parts.length == 3) {
            try {
                int p0 = Integer.parseInt(parts[0].trim());
                int p1 = Integer.parseInt(parts[1].trim());
                int p2 = Integer.parseInt(parts[2].trim());

                if (p0 > 1000) {
                    // Format: yyyy-mm-dd
                    return new ParsedDate(p0, p1, p2);
                } else if (p2 > 1000) {
                    // Format: dd-mm-yyyy
                    return new ParsedDate(p2, p1, p0);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return new ParsedDate(defaultYear, defaultMonth, defaultDay);
    }

    private void setupControls(){
        etTotal = findViewById(R.id.etTotal);
        etTVA = findViewById(R.id.etTVA);
        etClientCui = findViewById(R.id.etClientCui);
        dtData = findViewById(R.id.dpData);
        btnToInvoice = findViewById(R.id.btnToInvoice);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}