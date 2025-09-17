package com.example.fireservice;

import java.util.Objects;

public class Incident {
    String id; 
    String description; 
    String lastUpdateTime; 

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
        return Objects.equals(id, incident.id); 
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); 
    }

    @Override
    public String toString() {
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
