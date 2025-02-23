package com.example.barcode_test_app;

import android.Manifest;
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int SCAN_REQUEST_CODE = 101; // Request code for ScannerActivity

    private RadioGroup themeGroup;
    private RadioButton lightTheme, darkTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            openScannerActivity(); // ✅ Open ScannerActivity directly
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openScannerActivity(); // ✅ Open ScannerActivity if permission is granted
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openScannerActivity() {
        Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
        startActivityForResult(intent, SCAN_REQUEST_CODE); // ✅ Start ScannerActivity
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String scannedData = data.getStringExtra("SCANNED_DATA");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Scanned Data");
            builder.setMessage(scannedData);
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            builder.show();
        }
    }
}
