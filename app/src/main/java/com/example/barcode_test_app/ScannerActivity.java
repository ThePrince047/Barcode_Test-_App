package com.example.barcode_test_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean isScanning = false; // Prevent multiple scans

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        Button closeBtn = findViewById(R.id.closeBtn);

        cameraExecutor = Executors.newSingleThreadExecutor();

        closeBtn.setOnClickListener(v -> finish()); // Close ScannerActivity

        // Start Camera
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK) // Use the rear camera
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider()); // ✅ Set PreviewView SurfaceProvider

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (image.getImage() == null || isScanning) {
                        image.close();
                        return;
                    }

                    isScanning = true; // Prevent multiple scans
                    InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

                    BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                            .build();

                    BarcodeScanner scanner = BarcodeScanning.getClient(options);
                    scanner.process(inputImage)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty()) {
                                    String scannedData = extractBarcodeData(barcodes);
                                    sendResultAndClose(scannedData);
                                } else {
                                    isScanning = false; // Allow another scan
                                }
                            })
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                                isScanning = false;
                            })
                            .addOnCompleteListener(task -> image.close());
                });

                // Unbind previous camera use cases before binding a new one
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("CameraX", "Failed to bind camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void sendResultAndClose(String scannedData) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SCANNED_DATA", scannedData);
        setResult(RESULT_OK, resultIntent);
        finish(); // Close activity and return result to MainActivity
    }

    private String extractBarcodeData(List<Barcode> barcodes) {
        StringBuilder result = new StringBuilder();
        for (Barcode barcode : barcodes) {
            result.append(barcode.getRawValue()).append("\n");
        }
        return result.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraExecutor.shutdown();
    }
}