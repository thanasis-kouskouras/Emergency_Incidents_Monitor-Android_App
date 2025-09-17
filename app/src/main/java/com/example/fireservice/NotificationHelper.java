package com.example.fireservice;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "fire_alert_channel_v4"; // Άλλαξε το ID για να είσαι σίγουρος ότι παίρνει τις νέες ρυθμίσεις
    private static final String CHANNEL_NAME = "Fire Alerts GR";
    private static final String CHANNEL_DESCRIPTION = "Ειδοποιήσεις για νέα συμβάντα πυρκαγιών";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setShowBadge(true); // Εμφάνιση badge

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d("NotificationHelper", "Notification channel created/updated: " + CHANNEL_ID);
            } else {
                Log.e("NotificationHelper", "NotificationManager is null, cannot create channel.");
            }
        }
    }

    public static void showNotification(Context context, String title, String contentText, Class<?> targetActivityClass) {
        // Βεβαιώσου ότι το κανάλι έχει δημιουργηθεί (σημαντικό αν η εφαρμογή ξεκινάει από τον worker)
        createNotificationChannel(context);

        Intent intent = new Intent(context, ActiveIncidentsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0 /* Request code */,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT // Προσθήκη FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_fire) // **ΠΡΟΣΟΧΗ:** Βάλε ένα δικό σου εικονίδιο!
                .setContentTitle(title)
                .setContentText(contentText)
                .setColor(Color.RED)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Η ειδοποίηση κλείνει όταν πατηθεί
                .setCategory(NotificationCompat.CATEGORY_ALARM) // Για να τραβήξει την προσοχή
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Ορατή και στο lock screen
                .setNumber(1); // Αυτό μπορεί να βοηθήσει με το badge, αλλά εξαρτάται από τον launcher

        // Χρησιμοποίησε ένα σταθερό ID για την ειδοποίηση αν θέλεις να την ενημερώνεις
        // ή ένα μοναδικό αν θέλεις πολλές ειδοποιήσεις (π.χ. System.currentTimeMillis())
        // Για την περίπτωσή μας, ένα νέο συμβάν είναι ένα νέο notification.
        int notificationId = (int) System.currentTimeMillis();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d("NotificationHelper", "Notification sent with ID: " + notificationId + ", Title: " + title);
        } catch (SecurityException e) {
            Log.e("NotificationHelper", "SecurityException on notify: " + e.getMessage());
            // Αυτό δεν θα έπρεπε να συμβεί αν έχεις ζητήσει την άδεια στη MainActivity.
        }
    }
}
