package ru.innopolis.tbank.thealth.entities;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name="workouts")
public class WorkoutEntity {

    public WorkoutEntity() {

    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
