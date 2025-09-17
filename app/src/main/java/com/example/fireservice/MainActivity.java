package com.example.fireservice;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Switch;
import android.widget.Toast;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {
    WebView myWebView;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switchNotifications;
    SharedPreferences prefs;

    FloatingActionButton fabScrollToTop;
    FloatingActionButton fabActiveIncidents;

    private static final String TAG = "MainActivity";
    public static final String UNIQUE_WORK_NAME = "CheckWebsitePeriodicWork";

    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                    if (switchNotifications.isChecked()) {
                        startPeriodicWorker();
                        Toast.makeText(this, "Οι ειδοποιήσεις ενεργοποιήθηκαν.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission denied.");
                    Toast.makeText(this, "Η άδεια για ειδοποιήσεις απορρίφθηκε. Οι ειδοποιήσεις δεν θα λειτουργούν.", Toast.LENGTH_LONG).show();
                    if (switchNotifications.isChecked()) {
                        switchNotifications.setChecked(false);
                        prefs.edit().putBoolean("notifications_enabled", false).apply();
                    }
                }
            });


    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NotificationHelper.createNotificationChannel(this);

        myWebView = findViewById(R.id.webview);
        fabScrollToTop = findViewById(R.id.fab_scroll_to_top);
        fabActiveIncidents = findViewById(R.id.fab_active_incidents);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.loadUrl(CheckWebsiteWorker.WEBSITE_URL); 

        switchNotifications = findViewById(R.id.switchNotifications);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        boolean isEnabled = prefs.getBoolean("notifications_enabled", false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (isEnabled) {
                    Log.w(TAG, "Notifications were enabled in prefs, but permission is missing. Disabling notifications.");
                    isEnabled = false;
                    prefs.edit().putBoolean("notifications_enabled", false).apply();
                }
            }
        }
        switchNotifications.setChecked(isEnabled);


        fabScrollToTop.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            myWebView.scrollTo(0, 0);
        });

        fabActiveIncidents.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            Intent intent = new Intent(MainActivity.this, ActiveIncidentsActivity.class);
            startActivity(intent);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            myWebView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY > 1000) {
                    if (fabScrollToTop.getVisibility() != View.VISIBLE) {
                        fabScrollToTop.show();
                    }
                } else {
                    if (fabScrollToTop.getVisibility() == View.VISIBLE) {
                        fabScrollToTop.hide();
                    }
                }
            });
        } else {
            fabScrollToTop.setVisibility(View.VISIBLE);
        }

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply();

            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Notification switch ON, but permission POST_NOTIFICATIONS not granted. Requesting permission.");
                    requestNotificationPermission(); 
                    return; 
                }
                startPeriodicWorker();
                Toast.makeText(this, "Οι ειδοποιήσεις ενεργοποιήθηκαν.", Toast.LENGTH_SHORT).show();
            } else {
                stopPeriodicWorker();
                Toast.makeText(this, "Οι ειδοποιήσεις απενεργοποιήθηκαν.", Toast.LENGTH_SHORT).show();
            }
        });

        
        if (isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Prefs say enabled, but no permission. Worker NOT started on init.");
            } else {
                Log.d(TAG, "Notifications were enabled on init. Ensuring worker is scheduled.");
                startPeriodicWorker(); 
            }
        }
    }


    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted.");
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.i(TAG, "Showing rationale for POST_NOTIFICATIONS permission.");
                Toast.makeText(this, "Η εφαρμογή χρειάζεται άδεια για ειδοποιήσεις ώστε να σας ενημερώνει για νέα συμβάντα.", Toast.LENGTH_LONG).show();
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void startPeriodicWorker() {
        Log.d(TAG, "Attempting to start periodic worker.");
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) 
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                CheckWebsiteWorker.class,
                15, TimeUnit.MINUTES) 
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, 
                workRequest);
        Log.i(TAG, "Periodic worker enqueued with name: " + UNIQUE_WORK_NAME);
    }

    private void stopPeriodicWorker() {
        WorkManager.getInstance(this).cancelUniqueWork(UNIQUE_WORK_NAME);
        Log.i(TAG, "Periodic worker cancelled with name: " + UNIQUE_WORK_NAME);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        View anchorView = findViewById(R.id.toolbar); 

        if (id == R.id.action_refresh) {
            if (anchorView != null) {
                anchorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            if (myWebView != null) {
                myWebView.reload();
            }
            return true;
        } else if (id == R.id.action_info) {
            if (anchorView != null) {
                anchorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}