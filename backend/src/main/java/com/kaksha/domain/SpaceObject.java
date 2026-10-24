package com.kaksha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A catalogued object in orbit (from SATCAT). The primary key is its NORAD catalog number. */
@Entity
@Table(name = "space_object")
public class SpaceObject {

    /** SATCAT owner code used for India. */
    public static final String OWNER_INDIA = "IND";

    /** SATCAT object type of a payload, as opposed to a rocket body (R/B) or debris (DEB). */
    public static final String TYPE_PAYLOAD = "PAY";

    @Id
    private Long noradId;

    @Column(nullable = false)
    private String name;

    private String intlDesignator;

    /** PAY = payload, R/B = rocket body, DEB = debris, UNK = unknown. */
    @Column(length = 8)
    private String objectType;

    @Column(length = 8)
    private String opsStatus;

    @Column(length = 16)
    private String owner;

    private LocalDate launchDate;

    private Double apogeeKm;

    private Double perigeeKm;

    protected SpaceObject() {
        // required by JPA
    }

    public SpaceObject(Long noradId, String name, String intlDesignator, String objectType,
                       String opsStatus, String owner, LocalDate launchDate,
                       Double apogeeKm, Double perigeeKm) {
        this.noradId = noradId;
        this.name = name;
        this.intlDesignator = intlDesignator;
        this.objectType = objectType;
        this.opsStatus = opsStatus;
        this.owner = owner;
        this.launchDate = launchDate;
        this.apogeeKm = apogeeKm;
        this.perigeeKm = perigeeKm;
    }

    /** An Indian payload: the kind of object that is screened as a "primary". */
    public boolean isIndianPayload() {
        return OWNER_INDIA.equals(owner) && TYPE_PAYLOAD.equals(objectType);
    }

    public Long getNoradId() { return noradId; }
    public String getName() { return name; }
    public String getIntlDesignator() { return intlDesignator; }
    public String getObjectType() { return objectType; }
    public String getOpsStatus() { return opsStatus; }
    public String getOwner() { return owner; }
    public LocalDate getLaunchDate() { return launchDate; }
    public Double getApogeeKm() { return apogeeKm; }
    public Double getPerigeeKm() { return perigeeKm; }
}
