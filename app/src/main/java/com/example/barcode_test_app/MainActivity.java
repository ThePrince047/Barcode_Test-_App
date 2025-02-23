package com.example.barcode_test_app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.barcode.BarcodeScanner;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private ExecutorService cameraExecutor;
    private boolean isScanning = false; // Prevent duplicate scans

    private RadioGroup themeGroup;
    private RadioButton lightTheme, darkTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        themeGroup = findViewById(R.id.themeGrp);
        lightTheme = findViewById(R.id.lightTheme);
        darkTheme = findViewById(R.id.darkTheme);

        setThemeSelection();

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.lightTheme) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                saveThemePreference("light");
            } else if (checkedId == R.id.darkTheme) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                saveThemePreference("dark");
            }
        });

        findViewById(R.id.scanbtn).setOnClickListener(view -> checkCameraPermission());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("themePrefs", MODE_PRIVATE);
        String theme = prefs.getString("theme", "light");

        if ("dark".equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void saveThemePreference(String theme) {
        SharedPreferences prefs = getSharedPreferences("themePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("theme", theme);
        editor.apply();
    }

    private void setThemeSelection() {
        SharedPreferences prefs = getSharedPreferences("themePrefs", MODE_PRIVATE);
        String theme = prefs.getString("theme", "light");

        if ("dark".equals(theme)) {
            darkTheme.setChecked(true);
        } else {
            lightTheme.setChecked(true);
        }
    }

    public void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraForBarcodeScanning();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraForBarcodeScanning();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCameraForBarcodeScanning() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    @SuppressWarnings("UnsafeOptInUsageError")
                    InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

                    BarcodeScannerOptions options =
                            new BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                    .build();

                    BarcodeScanner scanner = BarcodeScanning.getClient(options);
                    scanner.process(inputImage)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty() && !isScanning) {
                                    isScanning = true;
                                    String scannedData = extractBarcodeData(barcodes);
                                    showScannedDataPopup(scannedData);
                                }
                            })
                            .addOnFailureListener(Throwable::printStackTrace)
                            .addOnCompleteListener(task -> image.close());
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private String extractBarcodeData(List<Barcode> barcodes) {
        StringBuilder result = new StringBuilder();
        for (Barcode barcode : barcodes) {
            result.append(barcode.getRawValue()).append("\n");
        }
        return result.toString();
    }

    private void showScannedDataPopup(String scannedData) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Scanned Data");
            builder.setMessage(scannedData);

            builder.setPositiveButton("OK", (dialog, which) -> {
                dialog.dismiss();
                isScanning = false; // Allow next scan
            });

            builder.show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
