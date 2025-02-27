package com.example.barcode_test_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;

import com.google.android.material.button.MaterialButton;
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
    private Camera camera;
    private boolean isScanning = false;
    private boolean isFlashOn = false;
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;

    private MaterialButton switchCameraButton, flashToggleButton;
    private SeekBar zoomSeekBar; // For manual zoom

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        RelativeLayout closeBtn = findViewById(R.id.closeBtn);
        switchCameraButton = findViewById(R.id.changeCamera);
        flashToggleButton = findViewById(R.id.flashtoggle);
        zoomSeekBar = findViewById(R.id.zoomSeekBar); // Initialize SeekBar

        cameraExecutor = Executors.newSingleThreadExecutor();

        closeBtn.setOnClickListener(v -> finish());
        switchCameraButton.setOnClickListener(v -> switchCamera());
        flashToggleButton.setOnClickListener(v -> toggleFlash());

        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (camera != null) {
                    float zoomRatio = 1.0f + (progress / 10.0f); // Convert SeekBar progress to zoom range
                    camera.getCameraControl().setZoomRatio(zoomRatio);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("CameraX", "Failed to bind camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            if (image.getImage() == null || isScanning) {
                image.close();
                return;
            }

            isScanning = true;
            InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

            BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();

            BarcodeScanner scanner = BarcodeScanning.getClient(options);
            scanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            adjustZoomForBarcode(); // Automatic Zoom when QR detected
                            String scannedData = extractBarcodeData(barcodes);
                            sendResultAndClose(scannedData);
                        } else {
                            isScanning = false;
                        }
                    })
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                        isScanning = false;
                    })
                    .addOnCompleteListener(task -> image.close());
        });

        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void switchCamera() {
        cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        bindCameraUseCases();
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);
        }
    }

    private void adjustZoomForBarcode() {
        if (camera != null) {
            camera.getCameraControl().setZoomRatio(2.5f); // Automatically zoom when QR detected
        }
    }

    private void sendResultAndClose(String scannedData) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SCANNED_DATA", scannedData);
        setResult(RESULT_OK, resultIntent);
        finish();
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
