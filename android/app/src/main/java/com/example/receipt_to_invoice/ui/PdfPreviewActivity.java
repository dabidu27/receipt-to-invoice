package com.example.receipt_to_invoice.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.receipt_to_invoice.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPreviewActivity extends AppCompatActivity {

    private static final String TAG = "PdfPreviewActivity";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pdf_preview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //Files under the assets folder aren't stored as regular files, they are compressed and bundled inside the .apk
        //FileRenderer, used to render PDFs, needs a real, regular file
        //So, we take the file out of assets and write it to the cache - then we can take the regular file from the cache and PdfRenderer can use it

        executor.execute(() -> {

            File file = copyToCache("sample_invoice.pdf");
            try(ParcelFileDescriptor parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer pdf = new PdfRenderer(parcelFileDescriptor);
                PdfRenderer.Page firstPage = pdf.openPage(0)){

                //ImageView uses a Bitmap, so we convert PdfRenderer.page to a Bitmap, with the render method, into an empty bitmap

                //Empty bitmap
                int width = 1200;                                     //fixed width keeps memory bounded
                int height = width * firstPage.getHeight() / firstPage.getWidth();  //keep the page proportions
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE); //PDFs render onto transparent
                //Render the first page onto this bitmap
                firstPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                //Now, the bitmap has the content we want, so we display it inside the image view
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ImageView pageView = findViewById(R.id.pageView);
                    pageView.setImageBitmap(bitmap);
                });

            }catch (IOException e){
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(PdfPreviewActivity.this, "Failed to render PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

        });

    }

    private File copyToCache(String name){

        File out = new File(getCacheDir(), name); //create an empty file in the cache
        try(InputStream is = new BufferedInputStream(getAssets().open(name)); //get the file from assets
            OutputStream os = new BufferedOutputStream(new FileOutputStream(out))){

            int b;
            while((b = is.read()) != -1){ //Read out of the input stream (the file from assets), write it to the output stream - on cache
                os.write(b);
            }
        }catch (IOException e){
            Log.e(TAG, "Failed to copy " + name + " to cache", e);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(PdfPreviewActivity.this, "Error loading asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }

        //now, the out File variable has the contents of our file in it, so we return it for use
        return out;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}