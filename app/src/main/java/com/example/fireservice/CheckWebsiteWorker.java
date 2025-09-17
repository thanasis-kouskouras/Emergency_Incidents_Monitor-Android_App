package com.example.fireservice;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class CheckWebsiteWorker extends Worker {

    private static final String TAG = "CheckWebsiteWorker";
    public static final String WEBSITE_URL = "https://museum.fireservice.gr/symvanta/";
    public static final String TARGET_REGION = "ΠΕΡΙΦΕΡΕΙΑ ΚΕΝΤΡΙΚΗΣ ΜΑΚΕΔΟΝΙΑΣ";

    // SharedPreferences Names and Keys
    private static final String NOTIFICATION_PREFS_NAME = "NotificationStatePrefs";
    private static final String NOTIFIED_INCIDENTS_KEY = "notified_incidents_list"; // Άλλαξα από _ids σε _list για σαφήνεια

    private static final String ACTIVE_INCIDENTS_PREFS_NAME = "ActiveIncidentsPrefs"; // Για την ActiveIncidentsActivity (αν την φτιάξεις)
    private static final String ACTIVE_KM_INCIDENTS_KEY = "active_km_incidents_list";


    public CheckWebsiteWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        // Δημιούργησε το κανάλι ειδοποιήσεων όταν αρχικοποιείται ο worker, για σιγουριά.
        NotificationHelper.createNotificationChannel(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "CheckWebsiteWorker: Task started. Checking for notifications setting.");

        SharedPreferences settingsPrefs = getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean notificationsEnabled = settingsPrefs.getBoolean("notifications_enabled", false);

        if (!notificationsEnabled) {
            Log.i(TAG, "Notifications are disabled by the user. Worker will not proceed with parsing or notifying.");
            // Αν οι ειδοποιήσεις είναι απενεργοποιημένες, ίσως να μην θέλεις καν να τρέχει ο worker.
            // Θα μπορούσες να ακυρώσεις τον worker από την MainActivity όταν το switch είναι off.
            // Προς το παρόν, απλά δεν κάνει τίποτα.
            return Result.success();
        }

        Log.d(TAG, "Notifications enabled. Proceeding with website check.");

        List<Incident> previouslyNotifiedIncidents = loadPreviouslyNotifiedIncidents();
        List<Incident> currentActiveKmIncidents = new ArrayList<>();
        List<Incident> newIncidentsForNotification = new ArrayList<>();

        try {
            Document doc = Jsoup.connect(WEBSITE_URL)
                    .timeout(30000) // 30 seconds timeout
                    .get();

            Elements activeIncidentPanels = doc.select("div.panel.panel-red");
            Log.d(TAG, "Found " + activeIncidentPanels.size() + " active (red) incidents in total.");

            for (Element panel : activeIncidentPanels) {
                Element panelHeading = panel.selectFirst("div.panel-heading");
                if (panelHeading == null) continue;

                Element descriptionTd = panelHeading.selectFirst("table td:first-child");
                if (descriptionTd == null) continue;

                // html() για να κρατήσει τα <br> tags, μετά αντικατάσταση με newline, και μετά Jsoup.parse για καθαρισμό.
                String rawHtmlDescription = descriptionTd.html();
                String descriptionWithNewlines = rawHtmlDescription.replace("<br>", "BR_TAG_TEMP").replace("<BR>", "BR_TAG_TEMP");
                String plainTextDescription = Jsoup.parse(descriptionWithNewlines).text().replace("BR_TAG_TEMP", "\n").trim();

                String startTimeClean = "UNKNOWN_START_TIME";
                Elements tds = panelHeading.select("table td");
                if (tds.size() > 1) {
                    String startTimeFullText = tds.get(1).text();
                    if (startTimeFullText.toUpperCase().contains("ΕΝΑΡΞΗ")) {
                        // Προσπάθεια να πάρουμε μόνο την ημερομηνία/ώρα
                        String[] parts = startTimeFullText.split("ΕΝΑΡΞΗ");
                        if (parts.length > 1) {
                            String datePart = parts[1].trim().split(" ")[0]; // Παίρνει το πρώτο στοιχείο μετά το "ΕΝΑΡΞΗ "
                            if (datePart.matches("\\d{2}/\\d{2}/\\d{4}")) {
                                startTimeClean = datePart;
                            } else {
                                // Fallback αν δεν είναι ακριβώς έτσι, μπορεί να χρειάζεται πιο πολύπλοκο parsing
                                startTimeClean = parts[1].trim(); // Κράτα ό,τι βρήκες μετά το "ΕΝΑΡΞΗ"
                            }
                        }
                    }
                }
                Log.v(TAG, "Extracted Start Time for ID generation: " + startTimeClean);

                if (plainTextDescription.toUpperCase().contains(TARGET_REGION.toUpperCase())) {
                    String incidentId = generateStableIncidentId(plainTextDescription, startTimeClean);

                    String lastUpdateTimeText = "";
                    List<org.jsoup.nodes.TextNode> textNodes = panelHeading.textNodes();
                    if (!textNodes.isEmpty()) {
                        for (int i = textNodes.size() - 1; i >= 0; i--) {
                            String S = textNodes.get(i).getWholeText().trim();
                            if (!S.isEmpty() && S.toLowerCase().contains("τελευταία ενημέρωση")) { // Έλεγχος αν περιέχει τη φράση
                                lastUpdateTimeText = S;
                                break;
                            }
                        }
                        if (lastUpdateTimeText.isEmpty() && !textNodes.isEmpty()){ // Fallback αν δεν βρήκε τη φράση
                            lastUpdateTimeText = textNodes.get(textNodes.size()-1).getWholeText().trim();
                        }
                    }
                    Log.v(TAG, "Incident in " + TARGET_REGION + " - ID: " + incidentId + ", Desc: " + plainTextDescription.split("\n")[0] + ", Start: " + startTimeClean + ", LastUpdate: " + lastUpdateTimeText);

                    Incident currentIncident = new Incident(incidentId, plainTextDescription, lastUpdateTimeText);
                    currentActiveKmIncidents.add(currentIncident);

                    if (isNewAndNeedsNotification(currentIncident, previouslyNotifiedIncidents)) {
                        newIncidentsForNotification.add(currentIncident);
                        Log.i(TAG, "NEW incident for notification: " + currentIncident.toString());
                    }
                }
            }

            // Αποθήκευση της τρέχουσας λίστας ενεργών συμβάντων της Κ.Μ. (αν τη χρειάζεσαι για UI)
            saveActiveKmIncidentsForUI(currentActiveKmIncidents);

            if (!newIncidentsForNotification.isEmpty()) {
                String notificationTitle;
                String notificationText;

                if (newIncidentsForNotification.size() == 1) {
                    Incident singleNewIncident = newIncidentsForNotification.get(0);
                    notificationTitle = "Νέο Συμβάν στην Κ. Μακεδονία";
                    // Πάρε τις πρώτες 2 γραμμές της περιγραφής για το κείμενο
                    String[] descLines = singleNewIncident.getDescription().split("\n");
                    notificationText = descLines[0];
                    if (descLines.length > 1) {
                        notificationText += " - " + descLines[1];
                    }
                } else {
                    notificationTitle = newIncidentsForNotification.size() + " Νέα Συμβάντα στην Κ. Μακεδονία";
                    notificationText = "Εντοπίστηκαν νέα ενεργά συμβάντα. Πατήστε για λεπτομέρειες.";
                }

                NotificationHelper.showNotification(getApplicationContext(), notificationTitle, notificationText, ActiveIncidentsActivity.class);
                Log.i(TAG, "Notification sent for " + newIncidentsForNotification.size() + " new incident(s).");

                // Ενημέρωσε τη λίστα των συμβάντων για τα οποία έχει σταλεί ειδοποίηση
                updatePreviouslyNotifiedIncidentsList(newIncidentsForNotification, previouslyNotifiedIncidents);
            } else {
                Log.i(TAG, "No new incidents matching criteria for notification.");
            }

            // Καθάρισμα παλιών συμβάντων από τη λίστα "previouslyNotified" (προαιρετικό αλλά καλό)
            // Αν ένα συμβάν δεν είναι πλέον ενεργό (δεν βρέθηκε στο currentActiveKmIncidents),
            // αλλά υπάρχει στο previouslyNotifiedIncidents, μπορείς να το αφαιρέσεις.
            // Αυτό κρατάει τη λίστα πιο καθαρή.
            cleanupOldNotifiedIncidents(currentActiveKmIncidents, previouslyNotifiedIncidents);


            Log.d(TAG, "CheckWebsiteWorker: Task finished successfully.");
            return Result.success();

        } catch (IOException e) {
            Log.e(TAG, "IOException in CheckWebsiteWorker (Network issue?): ", e);
            return Result.retry(); // Δοκίμασε ξανά αν είναι δικτυακό πρόβλημα
        } catch (Exception e) {
            Log.e(TAG, "Generic Exception in CheckWebsiteWorker: ", e);
            return Result.failure(); // Μη δοκιμάσεις ξανά αν είναι άλλο σφάλμα
        }
    }

    private String generateStableIncidentId(String description, String startTime) {
        String normalizedDescriptionFirstLine = "NO_DESC";
        if (description != null && !description.isEmpty()) {
            normalizedDescriptionFirstLine = description.split("\n")[0].toLowerCase() // Πάρε την πρώτη γραμμή για το ID
                    .replaceAll("\\s+", " ")
                    .replaceAll("[^a-zA-Z0-9α-ωΑ-ΩάέήίόύώΆΈΉΊΌΎΏ\\s]", "")
                    .trim();
        }
        String safeStartTime = (startTime != null) ? startTime.trim() : "NO_START_TIME";
        String combinedKey = normalizedDescriptionFirstLine + "::" + safeStartTime;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(combinedKey.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(String.format("%02x", b));
            }
            // Log.v(TAG, "Generated ID: " + sb.toString() + " from Key: " + combinedKey);
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            Log.e(TAG, "Error generating MD5 for ID, falling back to combinedKey hashcode: " + combinedKey, e);
            return "ID_ERR_" + combinedKey.hashCode();
        }
    }

    private boolean isNewAndNeedsNotification(Incident currentIncident, List<Incident> previouslyNotifiedIncidents) {
        for (Incident notifiedIncident : previouslyNotifiedIncidents) {
            if (notifiedIncident.getId().equals(currentIncident.getId())) {
                return false; // Βρέθηκε, άρα δεν είναι νέο
            }
        }
        return true; // Δεν βρέθηκε, άρα είναι νέο
    }

    private void updatePreviouslyNotifiedIncidentsList(List<Incident> newIncidents, List<Incident> previouslyNotifiedIncidents) {
        // Πρόσθεσε τα νέα συμβάντα στη λίστα των "previously notified"
        // Χρησιμοποίησε ένα αντίγραφο για να μην τροποποιήσεις τη λίστα που χρησιμοποιείται για έλεγχο μέσα στον βρόχο.
        List<Incident> updatedList = new ArrayList<>(previouslyNotifiedIncidents);
        updatedList.addAll(newIncidents);

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(NOTIFICATION_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(updatedList);
        editor.putString(NOTIFIED_INCIDENTS_KEY, json);
        editor.apply();
        Log.d(TAG, "Updated previously notified incidents list. Total notified: " + updatedList.size());
    }

    private List<Incident> loadPreviouslyNotifiedIncidents() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(NOTIFICATION_PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString(NOTIFIED_INCIDENTS_KEY, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<Incident>>() {}.getType();
            List<Incident> loadedIncidents = gson.fromJson(json, type);
            if (loadedIncidents != null) {
                Log.d(TAG, "Loaded " + loadedIncidents.size() + " previously notified incidents.");
                return loadedIncidents;
            }
        }
        Log.d(TAG, "No previously notified incidents found or error loading.");
        return new ArrayList<>();
    }

    // Μέθοδος για καθαρισμό παλιών συμβάντων από τη λίστα previouslyNotifiedIncidents
    private void cleanupOldNotifiedIncidents(List<Incident> currentActiveIncidents, List<Incident> previouslyNotifiedIncidents) {
        List<Incident> incidentsToKeep = new ArrayList<>();
        boolean changed = false;

        // Κράτα μόνο αυτά που είναι ακόμα ενεργά
        for (Incident notified : previouslyNotifiedIncidents) {
            boolean isActive = false;
            for (Incident active : currentActiveIncidents) {
                if (notified.getId().equals(active.getId())) {
                    isActive = true;
                    break;
                }
            }
            if (isActive) {
                incidentsToKeep.add(notified);
            } else {
                changed = true; // Βρέθηκε ένα που δεν είναι πλέον ενεργό
                Log.d(TAG, "Removing inactive incident from notified list: " + notified.getId());
            }
        }

        if (changed) {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(NOTIFICATION_PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            Gson gson = new Gson();
            String json = gson.toJson(incidentsToKeep);
            editor.putString(NOTIFIED_INCIDENTS_KEY, json);
            editor.apply();
            Log.d(TAG, "Cleaned up notified incidents list. Kept: " + incidentsToKeep.size());
        }
    }


    // Μέθοδος για αποθήκευση όλων των ενεργών συμβάνων της Κ.Μ. (για ένα μελλοντικό UI)
    // Δεν καλείται ακόμα, αλλά είναι έτοιμη.
    private void saveActiveKmIncidentsForUI(List<Incident> incidents) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(ACTIVE_INCIDENTS_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String jsonIncidents = gson.toJson(incidents);
        editor.putString(ACTIVE_KM_INCIDENTS_KEY, jsonIncidents);
        editor.apply();
        Log.d(TAG, "Saved " + incidents.size() + " active KM incidents to SharedPreferences for UI (" + ACTIVE_KM_INCIDENTS_KEY + ").");
    }

}
