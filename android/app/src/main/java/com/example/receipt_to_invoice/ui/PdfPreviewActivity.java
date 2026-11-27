package com.example.receipt_to_invoice.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.receipt_to_invoice.R;
import com.google.mlkit.vision.common.InputImage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPreviewActivity extends AppCompatActivity {

    private static final String TAG = PdfPreviewActivity.class.getSimpleName();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Button saveButton;

    private File pdfFile;

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

        saveButton = findViewById(R.id.saveButton);

        //Files under the assets folder aren't stored as regular files, they are compressed and bundled inside the .apk
        //FileRenderer, used to render PDFs, needs a real, regular file
        //So, we take the file out of assets and write it to the cache - then we can take the regular file from the cache and PdfRenderer can use it

        setTitle(R.string.invoice_preview);
        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        executor.execute(() -> {

            File file = copyToCache(getString(R.string.sample_invoice_file_name));
            pdfFile = file;
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
                    Toast.makeText(PdfPreviewActivity.this, getString(R.string.failed_to_render_pdf, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }

        });

        saveButton.setOnClickListener(v -> {

            executor.execute(() -> {

                //In modern Android, apps are blocked from freely browsing/modifying the device's shared storage using raw file paths
                //Instead, a system database called MediaStore is used to manage shared files

                //ContentValues is a map that describes the metadata of our file
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, getString(R.string.sample_invoice_file_name));
                values.put(MediaStore.Downloads.MIME_TYPE, getString(R.string.pdf_mime_type));
                //Environment.DIRECTORY_DOWNLOADS set to the relative_path means that the file goes inside the root downloads folder
                //Environment.DIRECTORY_DOWNLOADS + /Invoice would put it in downloads/Invoice
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                //"Under construction" flag, does not let apps open a half-written PDF
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                //Our app is not allowed to touch the downloads folder on its own - ContentResolver is a middleman between the app and the system databases
                ContentResolver contentResolver = getContentResolver();
                //MediaStore.Downloads.EXTERNAL_CONTENT_URI resolves internally to: content://media/external/downloads - this is how the MediaStore system database replaces raw file paths
                //Now, the ContentResolver takes our values map and creates a new row inside the MediaStore database
                //This translates to Android creating an empty file in the specific directory, in our case the downloads folder
                //Because, as we said, we don't have access to raw files/raw file paths, we get a URI
                Uri uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show());
                    return;
                }

                //Now, like when we copied the file to the cache, we basically copy the contents of our pdf inside the empty file we just created above
                try(InputStream is = new FileInputStream(pdfFile);
                    BufferedInputStream bis = new BufferedInputStream(is);
                OutputStream os = contentResolver.openOutputStream(uri)){//We ask the ContentResolver for a stream to the file

                    try (BufferedOutputStream bos = new BufferedOutputStream(os)) {
                        int b;
                        //We read from our pdf
                        while ((b = bis.read()) != -1) {
                            //We write inside the (empty) file inside downloads
                            bos.write(b);
                        }
                        bos.flush(); //Ensure all buffered bytes are written before updating MediaStore
                    }//bos closes here

                    //Now, the file is written, and we can update the is pending flag (at the uri, because that's the "path" for our file)
                    //This also makes the file visible to the apps
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    contentResolver.update(uri, values, null, null);
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.save_complete), Toast.LENGTH_SHORT).show());

                }catch (IOException e) {
                    //In case of an error, we delete the half-written file
                    contentResolver.delete(uri, null, null);
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.save_failed_with_error, e.getMessage()), Toast.LENGTH_SHORT).show());
                }

            });
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
            Log.e(TAG, getString(R.string.failed_to_copy_to_cache, name), e);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(PdfPreviewActivity.this, getString(R.string.error_loading_asset, e.getMessage()), Toast.LENGTH_SHORT).show();
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}