package com.example.receipt_to_invoice.ocr;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class OCR implements AutoCloseable {

    private static final String TAG = OCR.class.getSimpleName();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    public interface Callback{
        void onSuccess(String alignedText);
        void onError(String message);
    }

    public void runOcr(Uri uri, Context context, Callback callback){
        Context appContext = context.getApplicationContext();
        //Run the OCR on a background thread
        executor.execute(() -> {
            try{
                //Create InputImage object from the Uri
                InputImage img = InputImage.fromFilePath(appContext, uri);
                //Process image
                recognizer.process(img)
                        //Putting executor as the first argument makes the call back also run on a background thread
                        //Even though we are writing this code inside an execute(), by default callbacks like .addSuccessListener(callback) are executed on the main thread
                        .addOnSuccessListener(executor, new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text text) {
                                String alignedText = reconstructRows(text);
                                Log.d(TAG, alignedText);
                                mainHandler.post(() -> callback.onSuccess(alignedText));
                            }
                        })

                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mainHandler.post(() -> callback.onError(e.getMessage()));
                            }
                        });
            } catch (IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private static String reconstructRows(Text result) {
        //1. Flatten all Text.Line objects that have a valid bounding box
        List<Text.Line> allLines = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                if (line.getBoundingBox() != null) {
                    allLines.add(line);
                }
            }
        }
        if (allLines.isEmpty()) {
            return result.getText();
        }

        // 2. Sort top-to-bottom by vertical center
        allLines.sort(Comparator.comparingInt(l -> l.getBoundingBox().centerY()));

        // 3. Group lines that share the same visual row
        List<List<Text.Line>> rows = new ArrayList<>();
        for (Text.Line line : allLines) {
            Rect box = line.getBoundingBox();
            int centerY = box.centerY();
            // Tolerance = 60% of the line's pixel height (handles slight paper tilt)
            int tolerance = Math.max(10, (int) (box.height() * 0.6));

            List<Text.Line> matchedRow = null;
            for (List<Text.Line> row : rows) {
                int rowCenterY = row.get(0).getBoundingBox().centerY();
                if (Math.abs(centerY - rowCenterY) <= tolerance) {
                    matchedRow = row;
                    break;
                }
            }

            if (matchedRow != null) {
                matchedRow.add(line);
            } else {
                List<Text.Line> newRow = new ArrayList<>();
                newRow.add(line);
                rows.add(newRow);
            }
        }

        // 4. Sort each row left-to-right and join with spaces
        StringBuilder out = new StringBuilder();
        for (List<Text.Line> row : rows) {
            row.sort(Comparator.comparingInt(l -> l.getBoundingBox().left));
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(" ");
                out.append(row.get(i).getText());
            }
            out.append('\n');
        }
        return out.toString();
    }

    @Override
    public void close() {
        executor.shutdown();
        recognizer.close();
    }
}
