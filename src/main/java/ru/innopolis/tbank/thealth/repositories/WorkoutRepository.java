package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;

public interface WorkoutRepository extends JpaRepository<WorkoutEntity, Long> {

}
