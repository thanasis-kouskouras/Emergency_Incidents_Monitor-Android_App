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

    // Νέος τρόπος για αίτηση αδειών
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                    // Αν ο χρήστης μόλις έδωσε την άδεια και το switch είναι ON, ξεκίνα τον worker
                    if (switchNotifications.isChecked()) {
                        startPeriodicWorker();
                        Toast.makeText(this, "Οι ειδοποιήσεις ενεργοποιήθηκαν.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission denied.");
                    Toast.makeText(this, "Η άδεια για ειδοποιήσεις απορρίφθηκε. Οι ειδοποιήσεις δεν θα λειτουργούν.", Toast.LENGTH_LONG).show();
                    // Αν η άδεια απορρίφθηκε, βεβαιώσου ότι το switch είναι OFF
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

        // Δημιούργησε το κανάλι ειδοποιήσεων όταν ξεκινά η Activity (καλή πρακτική)
        NotificationHelper.createNotificationChannel(this);

        myWebView = findViewById(R.id.webview);
        fabScrollToTop = findViewById(R.id.fab_scroll_to_top);
        fabActiveIncidents = findViewById(R.id.fab_active_incidents);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.loadUrl(CheckWebsiteWorker.WEBSITE_URL); // Χρησιμοποίησε τη σταθερά από τον Worker

        switchNotifications = findViewById(R.id.switchNotifications);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        boolean isEnabled = prefs.getBoolean("notifications_enabled", false);


        // Ζήτα άδεια για ειδοποιήσεις αν χρειάζεται (Android 13+) και ρύθμισε το switch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Αν η άδεια δεν έχει δοθεί ΚΑΙ το switch ήταν αποθηκευμένο ως ON, το κάνουμε OFF
                // γιατί δεν μπορεί να λειτουργήσει χωρίς την άδεια.
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
                // Έλεγχος αν έχει δοθεί η άδεια ΠΡΙΝ ξεκινήσει ο worker (για Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Notification switch ON, but permission POST_NOTIFICATIONS not granted. Requesting permission.");
                    requestNotificationPermission(); // Ζήτα την άδεια. Ο worker θα ξεκινήσει από το callback αν δοθεί.
                    // Μην ξεκινήσεις τον worker εδώ αν η άδεια δεν έχει δοθεί.
                    // Επίσης, γύρνα το switch προσωρινά σε off μέχρι να δούμε την απάντηση για την άδεια.
                    // switchNotifications.setChecked(false); // Ή το αφήνεις on και ο χρήστης θα δει το dialog.
                    return; // Ο χειρισμός θα γίνει στο callback του requestPermissionLauncher
                }
                startPeriodicWorker();
                Toast.makeText(this, "Οι ειδοποιήσεις ενεργοποιήθηκαν.", Toast.LENGTH_SHORT).show();
            } else {
                stopPeriodicWorker();
                Toast.makeText(this, "Οι ειδοποιήσεις απενεργοποιήθηκαν.", Toast.LENGTH_SHORT).show();
            }
        });

        // Αν οι ειδοποιήσεις είναι ήδη ενεργοποιημένες κατά την εκκίνηση (και υπάρχει άδεια),
        // βεβαιώσου ότι ο worker είναι προγραμματισμένος.
        if (isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Μην ξεκινήσεις αν δεν υπάρχει άδεια, παρόλο που το prefs λέει true.
                // Ο παραπάνω χειρισμός στο onCreate θα το έχει ήδη διορθώσει.
                Log.w(TAG, "Prefs say enabled, but no permission. Worker NOT started on init.");
            } else {
                Log.d(TAG, "Notifications were enabled on init. Ensuring worker is scheduled.");
                startPeriodicWorker(); // Χρησιμοποίησε την KEEP για να μην τον ξαναφτιάχνει αν υπάρχει.
            }
        }
    }


    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted.");
                // Άδεια ήδη υπάρχει
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Εμφάνισε ένα UI που εξηγεί γιατί χρειάζεσαι την άδεια (π.χ. ένα AlertDialog)
                // και μετά κάλεσε το requestPermissionLauncher.launch(...)
                Log.i(TAG, "Showing rationale for POST_NOTIFICATIONS permission.");
                Toast.makeText(this, "Η εφαρμογή χρειάζεται άδεια για ειδοποιήσεις ώστε να σας ενημερώνει για νέα συμβάντα.", Toast.LENGTH_LONG).show();
                // Αφού δείξεις το rationale, κάνε την αίτηση
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Απευθείας αίτηση της άδειας
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void startPeriodicWorker() {
        Log.d(TAG, "Attempting to start periodic worker.");
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Απαίτηση για σύνδεση στο δίκτυο
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                CheckWebsiteWorker.class,
                15, TimeUnit.MINUTES) // Η συχνότητα που είχες
                .setConstraints(constraints)
                // .setInitialDelay(1, TimeUnit.MINUTES) // Προαιρετικά, για να μην τρέξει αμέσως την 1η φορά
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Κράτα τον υπάρχοντα worker αν υπάρχει ήδη με αυτό το όνομα
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
        View anchorView = findViewById(R.id.toolbar); // Για τη δόνηση

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

    // Η παλιά μέθοδος onRequestPermissionsResult δεν χρειάζεται πλέον
    // αφού χρησιμοποιούμε το ActivityResultLauncher.
    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { ... }
    */
}