package com.example.fireservice;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.MenuItem;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar_about);
        setSupportActionBar(toolbar);

        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); 
            getSupportActionBar().setDisplayShowHomeEnabled(true);  
            getSupportActionBar().setTitle("Σχετικά με FireService"); 
        }

        TextView textView = findViewById(R.id.text_about);

        String fullText = "Κατασκευή: Κούσκουρας Αθανάσιος\n" +
                "Έκδοση: 1.3\n" +
                "Τόπος: Θέρμη | Ημερομηνία: 05/08/2025\n\n" +
                "Περιγραφή: Η παρούσα εφαρμογή κατασκευάστηκε για τη διευκόλυνση εθελοντών Πολιτικής Προστασίας στην παρακολούθηση συμβάντων μέσω του ανοιχτού ιστότοπου που παρέχει η Πυροσβεστική Υπηρεσία για τα ενεργά συμβάντα σε όλη την Ελλάδα.\n\n" +
                "Η εφαρμογή παρακολουθεί τον ανοιχτό σύνδεσμο της Πυροσβεστικής Υπηρεσίας και όταν εντοπίσει αλλαγή σε αυτή στέλνει ειδοποίηση. Ο έλεγχος γίνεται κάθε 15 λεπτά και η επιλογή των ειδοποιήσεων πρέπει να έχει επιλεχθεί. Η ειδοποίηση δεν έρχεται άμεσα λόγω του παραπάνω χρονικού παραθύρου.\n\n" +
                "Τέλος, η εφαρμογή έχει ρυθμιστεί ώστε να στέλνει ειδοποιήσεις μόνο για ενεργά συμβάντα στην περιοχή της Περιφέρειας Κεντρικής Μακεδονίας.\n\n" +
                "Επικοινωνία: tnskousko@gmail.com\n\n" +
                "© 2025 Κούσκουρας Αθανάσιος. Με επιφύλαξη κάθε δικαιώματος. Η εφαρμογή αυτή δεν αποτελεί εμπορικό προϊόν και η χρήση της προορίζεται για εθελοντικούς σκοπούς.";

        SpannableString spannableString = new SpannableString(fullText);

        String email = "tnskousko@gmail.com";
        int start = fullText.indexOf(email);
        int end = start + email.length();

        int darkBlue = Color.parseColor("#283593");

        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:" + email));
                widget.getContext().startActivity(intent);
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(darkBlue);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String copyrightText = "© 2025 Κούσκουρας Αθανάσιος. Με επιφύλαξη κάθε δικαιώματος. Η εφαρμογή αυτή δεν αποτελεί εμπορικό προϊόν και η χρήση της προορίζεται για εθελοντικούς σκοπούς.";
        int copyrightStart = fullText.indexOf(copyrightText);
        int copyrightEnd = copyrightStart + copyrightText.length();

        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), copyrightStart, copyrightEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new RelativeSizeSpan(0.8f), copyrightStart, copyrightEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        textView.setText(spannableString);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Δόνηση όταν πατηθεί το κουμπί "πίσω"
            View viewForHapticFeedback = findViewById(R.id.toolbar_about);
            if (viewForHapticFeedback != null) {
                viewForHapticFeedback.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}