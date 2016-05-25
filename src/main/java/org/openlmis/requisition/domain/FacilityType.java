package org.openlmis.requisition.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "facility_types")
@NoArgsConstructor
public class FacilityType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    @Setter
    private Integer id;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    @Getter
    @Setter
    private String code;

    @Column(columnDefinition = "text")
    @Getter
    @Setter
    private String name;

    @Column(columnDefinition = "text")
    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private Integer displayOrder;

    @Getter
    @Setter
    private Boolean active;
}
