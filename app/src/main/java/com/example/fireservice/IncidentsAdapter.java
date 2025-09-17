package com.example.fireservice;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import android.text.SpannableString;
import android.text.Spannable;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.util.Log;
public class IncidentsAdapter extends RecyclerView.Adapter<IncidentsAdapter.IncidentViewHolder> {

    private List<Incident> incidentList = new ArrayList<>();

    @NonNull
    @Override
    public IncidentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_incident, parent, false);
        return new IncidentViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull IncidentViewHolder holder, int position) {
        Incident currentIncident = incidentList.get(position);
        String originalDescription = currentIncident.getDescription();

        if (originalDescription == null) {
            originalDescription = "";
        }

        String modifiedDescription = originalDescription; // Αρχικά, είναι το ίδιο

        // Βρες την τελευταία αλλαγή γραμμής για να εντοπίσεις την αρχή της τελευταίας γραμμής
        int lastNewlineIndexOriginal = originalDescription.lastIndexOf('\n');

        if (lastNewlineIndexOriginal != -1 && lastNewlineIndexOriginal < originalDescription.length() - 1) {
            // Υπάρχει τουλάχιστον μία αλλαγή γραμμής και δεν είναι ο τελευταίος χαρακτήρας
            // (δηλαδή, υπάρχει τουλάχιστον δύο γραμμές κειμένου)

            // Χώρισε το string στο σημείο της τελευταίας αλλαγής γραμμής
            String partBeforeLastLine = originalDescription.substring(0, lastNewlineIndexOriginal); // Μέχρι (αλλά όχι συμπεριλαμβανομένου) του τελευταίου '\n'
            String lastLine = originalDescription.substring(lastNewlineIndexOriginal + 1);    // Το κείμενο μετά το τελευταίο '\n'

            // Ανακατασκεύασε το string με μια επιπλέον κενή γραμμή
            modifiedDescription = partBeforeLastLine + "\n\n" + lastLine; // Πρόσθεσε δύο '\n'
        }
        // Αν δεν υπάρχει '\n' (μία μόνο γραμμή) ή αν το '\n' είναι ο τελευταίος χαρακτήρας,
        // δεν κάνουμε καμία τροποποίηση για προσθήκη κενής γραμμής, καθώς δεν υπάρχει "προτελευταία" γραμμή με την ίδια έννοια.

        SpannableString spannableDescription = new SpannableString(modifiedDescription);

        // Η λογική για το bold παραμένει ίδια, αλλά τώρα λειτουργεί πάνω στο modifiedDescription
        int lastNewlineIndexModified = modifiedDescription.lastIndexOf('\n');
        int startIndexForBold = -1;

        if (lastNewlineIndexModified != -1 && lastNewlineIndexModified < modifiedDescription.length() - 1) {
            startIndexForBold = lastNewlineIndexModified + 1;
        } else if (lastNewlineIndexModified == -1 && !modifiedDescription.isEmpty()) {
            startIndexForBold = 0;
        }

        if (startIndexForBold != -1) {
            spannableDescription.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    startIndexForBold,
                    modifiedDescription.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        holder.textViewDescription.setText(spannableDescription);
        holder.textViewLastUpdate.setText(currentIncident.getLastUpdateTime());
        // ...
    }

    @Override
    public int getItemCount() {
        return incidentList.size();
    }

    public void setIncidents(List<Incident> incidents) {
        this.incidentList = incidents;
        notifyDataSetChanged(); // Απλή ανανέωση, για μεγαλύτερες λίστες χρησιμοποίησε DiffUtil
    }

    static class IncidentViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewDescription;
        private final TextView textViewLastUpdate;
        // private final TextView textViewIdDebug; // Αν το έχεις στο layout

        public IncidentViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewDescription = itemView.findViewById(R.id.textView_incident_description);
            textViewLastUpdate = itemView.findViewById(R.id.textView_incident_last_update);
            // textViewIdDebug = itemView.findViewById(R.id.textView_incident_id_debug);
        }
    }
}