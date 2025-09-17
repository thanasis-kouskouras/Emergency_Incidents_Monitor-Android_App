package com.example.fireservice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build; // *** ΠΡΟΣΤΕΘΗΚΕ ***
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Color;
// Τα Collections, Comparator, Map δεν χρησιμοποιούνται πλέον απευθείας εδώ μετά την αλλαγή του loadActiveIncidents,
// αλλά μπορεί να τα χρειαστείς αν επαναφέρεις ταξινόμηση ή άλλη λογική.
// import java.util.Collections;
// import java.util.Comparator;
// import java.util.Map;

public class ActiveIncidentsActivity extends AppCompatActivity {

    private static final String TAG = "ActiveIncidentsActivity";
    private RecyclerView recyclerView;
    private IncidentsAdapter incidentsAdapter; // *** ΑΛΛΑΓΗ ΟΝΟΜΑΤΟΣ ΣΕ incidentsAdapter (αντί για adapter σκέτο για συνέπεια) ***
    private TextView textViewNoIncidents;
    private Vibrator vibrator; // *** ΠΡΟΣΤΕΘΗΚΕ ***

    // Τα SharedPreferences keys που χρησιμοποιείς για τη φόρτωση
    // Θα πρέπει να ταιριάζουν με αυτά που χρησιμοποιεί ο Worker σου για την αποθήκευση.
    private static final String ACTIVE_INCIDENTS_PREFS_NAME = "ActiveIncidentsPrefs"; // Το όνομα του αρχείου SharedPreferences
    private static final String ACTIVE_KM_INCIDENTS_KEY = "active_km_incidents_list"; // Το κλειδί για τη λίστα συμβάντων

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_incidents);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); // Σημαντικό
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Για σκούρα status bar -> ανοιχτά εικονίδια
            decorView.setSystemUiVisibility(flags);
        }

        Toolbar toolbar = findViewById(R.id.toolbar_active_incidents);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Εμφάνιση κουμπιού "πίσω"
            getSupportActionBar().setTitle("Ενεργά Συμβάντα (Κ.Μακεδονία)"); // *** ΑΛΛΑΓΗ ΤΙΤΛΟΥ (ή άφησέ το "Ενεργά Συμβάντα (Κ. Μακεδονία)" αν προτιμάς) ***
        }

        recyclerView = findViewById(R.id.recyclerView_active_incidents);
        textViewNoIncidents = findViewById(R.id.textView_no_incidents);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); // *** ΠΡΟΣΤΕΘΗΚΕ: Αρχικοποίηση Vibrator ***

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // *** ΑΡΧΙΚΟΠΟΙΗΣΗ ADAPTER: Βεβαιώσου ότι ο IncidentsAdapter ΔΕΧΕΤΑΙ List<Incident> στον constructor του
        // ή έχει μια μέθοδο για να ορίσεις τα δεδομένα (όπως η setIncidents που χρησιμοποιούσες).
        // Αν ο constructor σου είναι κενός (new IncidentsAdapter()), είναι εντάξει.
        // Αν ο constructor σου απαιτεί List<Incident>, αρχικοποίησέ τον με μια κενή λίστα αρχικά:
        // incidentsAdapter = new IncidentsAdapter(new ArrayList<>(), this); // Αν ο constructor παίρνει και context
        incidentsAdapter = new IncidentsAdapter(); // Υποθέτοντας ότι έχεις τη μέθοδο setIncidents
        recyclerView.setAdapter(incidentsAdapter);

        loadActiveIncidents();
    }

    private void loadActiveIncidents() {
        // Χρησιμοποίησε τα SharedPreferences που ορίζεις στην κορυφή της κλάσης
        SharedPreferences prefs = getSharedPreferences(ACTIVE_INCIDENTS_PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString(ACTIVE_KM_INCIDENTS_KEY, null);
        List<Incident> activeIncidents = new ArrayList<>();

        if (json != null && !json.equals("null")) { // Έλεγχos και για το string "null"
            try {
                Type type = new TypeToken<ArrayList<Incident>>() {}.getType();
                activeIncidents = gson.fromJson(json, type);
                if (activeIncidents == null) { // Επιπλέον έλεγχος μετά το fromJson
                    activeIncidents = new ArrayList<>();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing active incidents JSON", e);
                activeIncidents = new ArrayList<>(); // Σε περίπτωση σφάλματος, άδεια λίστα
            }
        } else {
            Log.d(TAG, "No active KM incidents JSON found or JSON was 'null'.");
        }


        // Προαιρετικά: Ταξινόμηση συμβάντων αν το Incident class έχει κατάλληλη μέθοδο
        // (π.χ., getTimestampForSorting() ή παρόμοιο)
        /*
        if (!activeIncidents.isEmpty() && activeIncidents.get(0) instanceof Comparable) { // Ένας τρόπος για έλεγχο
            Collections.sort(activeIncidents); // Αν το Incident υλοποιεί Comparable
        } else if (!activeIncidents.isEmpty() && activeIncidents.get(0).getTimestampForSorting() != null) { // Αν έχεις getter για timestamp
            Collections.sort(activeIncidents, Comparator.comparing(Incident::getTimestampForSorting).reversed());
        }
        */

        if (activeIncidents.isEmpty()) {
            textViewNoIncidents.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            Log.d(TAG, "No active KM incidents to display.");
        } else {
            textViewNoIncidents.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            incidentsAdapter.setIncidents(activeIncidents); // Χρήση της μεθόδου setIncidents του adapter
            Log.d(TAG, "Loaded and displayed " + activeIncidents.size() + " active KM incidents.");
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Χειρισμός του κουμπιού "πίσω" στο toolbar
        if (item.getItemId() == android.R.id.home) {
            // Δόνηση
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Για API 26 (Android Oreo) και νεότερες
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    // Για παλαιότερες εκδόσεις (deprecated σε API 26)
                    vibrator.vibrate(50);
                }
            }
            finish(); // Κλείνει την τρέχουσα activity και επιστρέφει στην προηγούμενη
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}