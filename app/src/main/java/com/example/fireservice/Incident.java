package com.example.fireservice;

import java.util.Objects;

public class Incident {
    String id; // Μοναδικό ID που θα φτιάξουμε (MD5 hash)
    String description; // Πλήρης περιγραφή από το πρώτο <td> (με newlines)
    String lastUpdateTime; // Η συμβολοσειρά "Τελευταία Ενημέρωση..." (για πληροφορία, όχι για ID)

    // Default constructor για Gson (αν και δεν είναι πάντα απαραίτητος για serialization, καλό είναι να υπάρχει)
    public Incident() {}

    public Incident(String id, String description, String lastUpdateTime) {
        this.id = id;
        this.description = description;
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id); // Σύγκριση μόνο βάσει ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Hashcode μόνο βάσει ID
    }

    @Override
    public String toString() {
        // Κάνε την περιγραφή πιο σύντομη για το logging
        String shortDescription = description != null && description.length() > 60
                ? description.substring(0, 60) + "..."
                : description;
        shortDescription = shortDescription != null ? shortDescription.replace("\n", " ") : "N/A";

        return "Incident{" +
                "id='" + id + '\'' +
                ", desc='" + shortDescription + '\'' +
                ", lastUpdate='" + lastUpdateTime + '\'' +
                '}';
    }
}
