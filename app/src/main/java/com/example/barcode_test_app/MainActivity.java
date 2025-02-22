package com.example.barcode_test_app;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
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

public class MainActivity extends AppCompatActivity {
    Button scanButton;
    RadioGroup themeGroup;
    RadioButton lightTheme, darkTheme;
    final int CAMERA_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadTheme();
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        scanButton = findViewById(R.id.scanbtn);
        themeGroup = findViewById(R.id.themeGrp);
        lightTheme = findViewById(R.id.lightTheme);
        darkTheme = findViewById(R.id.darkTheme);
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            darkTheme.setChecked(true);
        } else {
            lightTheme.setChecked(true);
        }

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.darkTheme) {
                setThemeMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                setThemeMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
        scanButton.setOnClickListener(view -> checkCameraPermission());
    }
    private void loadTheme() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("darkMode", false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
    private void setThemeMode(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode);

        SharedPreferences.Editor editor = getSharedPreferences("settings", MODE_PRIVATE).edit();
        editor.putBoolean("darkMode", mode == AppCompatDelegate.MODE_NIGHT_YES);
        editor.apply();
    }

    public void checkCameraPermission() {
        SharedPreferences prefs = getSharedPreferences("permissionPrefs", MODE_PRIVATE);
        int denialCount = prefs.getInt("camera_denial_count", 0);  // Using correct key

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                if (denialCount < 1) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("camera_denial_count", denialCount + 1);
                    editor.apply();
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                } else {
                    showSettingsDialog();
                }
            } else {
                if (denialCount >= 1) {
                    showSettingsDialog();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                }
            }
        } else {
            Toast.makeText(this, "Camera Permission already granted", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        SharedPreferences prefs = getSharedPreferences("permissionPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Reset denial count when granted
                editor.putInt("camera_denial_count", 0);
                editor.apply();
                Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                int denialCount = prefs.getInt("camera_denial_count", 0);
                if (denialCount >= 1) {
                    showSettingsDialog();
                } else {
                    editor.putInt("camera_denial_count", denialCount + 1);
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

        builder.setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }
}
