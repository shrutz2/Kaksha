package com.kaksha.repository;

import com.kaksha.domain.SpaceObject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceObjectRepository extends JpaRepository<SpaceObject, Long> {

    /** Objects of one owner, for example "IND" for the Indian satellites we watch. */
    List<SpaceObject> findByOwnerOrderByNameAsc(String owner);
}
