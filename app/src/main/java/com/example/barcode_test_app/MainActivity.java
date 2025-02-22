package com.example.barcode_test_app;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
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

import com.google.mlkit.vision.barcode.common.Barcode;

import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.barcode.BarcodeScanner;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 200;

    private RadioGroup themeGroup;
    private RadioButton lightTheme, darkTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme preference
        applySavedTheme();

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        themeGroup = findViewById(R.id.themeGrp);
        lightTheme = findViewById(R.id.lightTheme);
        darkTheme = findViewById(R.id.darkTheme);

        // Set correct theme selection
        setThemeSelection();

        // Handle theme change
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
        SharedPreferences prefs = getSharedPreferences("permissionPrefs", MODE_PRIVATE);
        int denialCount = prefs.getInt(Manifest.permission.CAMERA, 0);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                if (denialCount < 1) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(Manifest.permission.CAMERA, denialCount + 1);
                    editor.apply();
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                } else {
                    showSettingsDialog();
                }
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap bitmap = (Bitmap) extras.get("data");

            if (bitmap != null) {
                scanBarcodeFromBitmap(bitmap);
            }
        }
    }

    private void scanBarcodeFromBitmap(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        BarcodeScanner scanner = BarcodeScanning.getClient();

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        String scannedData = extractBarcodeData(barcodes);
                        showScannedDataPopup(scannedData);
                    } else {
                        Toast.makeText(MainActivity.this, "No barcode detected", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to scan barcode", Toast.LENGTH_SHORT).show());
    }

    private String extractBarcodeData(List<Barcode> barcodes) {
        StringBuilder result = new StringBuilder();
        for (Barcode barcode : barcodes) {
            result.append(barcode.getRawValue()).append("\n");
        }
        return result.toString();
    }

    private void showScannedDataPopup(String scannedData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Scanned Data");
        builder.setMessage(scannedData);

        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        SharedPreferences prefs = getSharedPreferences("permissionPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                editor.putInt(Manifest.permission.CAMERA, 0);
                editor.apply();
                openCamera();
            } else {
                int denialCount = prefs.getInt(Manifest.permission.CAMERA, 0);
                if (denialCount >= 1) {
                    showSettingsDialog();
                } else {
                    editor.putInt(Manifest.permission.CAMERA, denialCount + 1);
                    editor.apply();
                    Toast.makeText(this, "Camera Permission Denied. Please allow again.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required");
        builder.setMessage("You have denied the permission multiple times. You must enable it manually in Settings.");

        builder.setPositiveButton("Go to Settings", (dialog, which) -> {
            dialog.dismiss();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }
}
