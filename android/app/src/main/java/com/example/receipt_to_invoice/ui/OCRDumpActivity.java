package com.example.receipt_to_invoice.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.receipt_to_invoice.R;
import com.example.receipt_to_invoice.ocr.OCR;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ro.receipt.parser.ReceiptParser;

public class OCRDumpActivity extends AppCompatActivity {

    OCR ocr = new OCR();

    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();

    //Register PhotoPicker launcher at class level
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri o) {
                    if(o != null){ //User picked an image, pass this URI to ML Kit to process it
                        ocr.runOcr(o, OCRDumpActivity.this, new OCR.Callback() {
                            @Override
                            public void onSuccess(String alignedText) {
                                saveText(alignedText);
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(OCRDumpActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }else{
                        //User closed the picker, do nothing
                        return;
                    }
                }
            });

    private Button btnPick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ocrdump);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnPick = findViewById(R.id.btnPick);
        btnPick.setOnClickListener(v -> {
            pickPhotoLauncher.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });
    }


    private void saveText(String alignedText){

        String fileName = "ocr_" + System.currentTimeMillis() + ".txt";

        fileExecutor.execute(() -> {

            //Get the exact text returned by the OCR
            Map<String, String> out = ReceiptParser.parse(alignedText, "RO11997009");

            //getExternalFilesDir(null) => an app-specific, writable folder, no permissions needed
            File outFile = new File(getExternalFilesDir(null), fileName);
            try(FileOutputStream fos = new FileOutputStream(outFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                OutputStreamWriter osw = new OutputStreamWriter(bos,StandardCharsets.UTF_8)){

                for(String field: out.keySet()){
                    osw.write(field + ": "+ out.get(field) + "\n");
                }
                runOnUiThread(() -> Toast.makeText(this, "Saved " + fileName, Toast.LENGTH_LONG).show());
            }catch (IOException e){
                runOnUiThread(() -> Toast.makeText(this, "Cannot save file", Toast.LENGTH_LONG).show());
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fileExecutor.shutdown();
        ocr.close();
    }
}