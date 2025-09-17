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

        String modifiedDescription = originalDescription; 

        
        int lastNewlineIndexOriginal = originalDescription.lastIndexOf('\n');

        if (lastNewlineIndexOriginal != -1 && lastNewlineIndexOriginal < originalDescription.length() - 1) {
            String partBeforeLastLine = originalDescription.substring(0, lastNewlineIndexOriginal);  
            String lastLine = originalDescription.substring(lastNewlineIndexOriginal + 1);    

            modifiedDescription = partBeforeLastLine + "\n\n" + lastLine; 
        }

        SpannableString spannableDescription = new SpannableString(modifiedDescription);

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
    }

    @Override
    public int getItemCount() {
        return incidentList.size();
    }

    public void setIncidents(List<Incident> incidents) {
        this.incidentList = incidents;
        notifyDataSetChanged(); 
    }

    static class IncidentViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewDescription;
        private final TextView textViewLastUpdate;

        public IncidentViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewDescription = itemView.findViewById(R.id.textView_incident_description);
            textViewLastUpdate = itemView.findViewById(R.id.textView_incident_last_update);
        }
    }
}