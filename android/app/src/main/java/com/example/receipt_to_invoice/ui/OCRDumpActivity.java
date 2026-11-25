package com.example.receipt_to_invoice.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.receipt_to_invoice.R;
import com.example.receipt_to_invoice.ocr.OCR;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
                                fileExecutor.execute(() -> {

                                    //Get the exact text returned by the OCR
                                    Map<String, String> out = ReceiptParser.parse(alignedText, "RO11997009");
                                    runOnUiThread(() -> {
                                        Intent intent = new Intent(OCRDumpActivity.this, PreviewReceiptDataActivity.class);
                                        intent.putExtra(PreviewReceiptDataActivity.DATA_EXTRA, new HashMap<>(out)); //create a HashMap from the Map, because HashMap is serializable
                                        startActivity(intent);
                                    });
                                });
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

    private Uri cameraPhotoUri;
    private final ActivityResultLauncher<Uri> takePhotoLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean o) {
            if(Boolean.TRUE.equals(o) && cameraPhotoUri != null){
                ocr.runOcr(cameraPhotoUri, OCRDumpActivity.this, new OCR.Callback() {
                    @Override
                    public void onSuccess(String alignedText) {
                        fileExecutor.execute(() -> {
                            //Get the exact text returned by the OCR
                            Map<String, String> out = ReceiptParser.parse(alignedText, "RO11997009");
                            runOnUiThread(() -> {
                                Intent intent = new Intent(OCRDumpActivity.this, PreviewReceiptDataActivity.class);
                                intent.putExtra(PreviewReceiptDataActivity.DATA_EXTRA, new HashMap<>(out)); //create a HashMap from the Map, because HashMap is serializable
                                startActivity(intent);
                            });
                        });
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(OCRDumpActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
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

        setTitle("Creeaza bon");

        btnPick = findViewById(R.id.btnPick);
        btnPick.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, btnPick);
            popupMenu.getMenuInflater().inflate(R.menu.popup_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int id = item.getItemId();
                    if(id == R.id.popup_camera){

                        try {
                            //Create an empty file in the cache
                            File imageFile = File.createTempFile("receipt_" ,".jpg", getCacheDir());
                            //Get the Uri of the file through a FileProvider - this is where the photo taken by the camera will be saved
                            cameraPhotoUri = FileProvider.getUriForFile(OCRDumpActivity.this, getPackageName() + ".fileprovider", imageFile);
                            //Launch the camera - the callback, will run the ocr on the saved photo
                            takePhotoLauncher.launch(cameraPhotoUri);
                        } catch (IOException e) {
                            Toast.makeText(OCRDumpActivity.this, "Cannot open camera. Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                        return true;
                    }else if(id == R.id.popup_gallery){
                        pickPhotoLauncher.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
                        return true;
                    }
                    return true;
                }
            });

            popupMenu.show();
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        fileExecutor.shutdown();
        ocr.close();
    }
}