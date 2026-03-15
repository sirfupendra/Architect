package com.parser.Architect.Entites;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemaDefinition {

    @Id
    @GeneratedValue
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String originalSchema;

    @Column(columnDefinition = "TEXT")
    private String optimizedSchema;

    /** RELAY: transformation spec or code to map optimized output back to original schema format. */
    @Column(columnDefinition = "TEXT")
    private String relayTransformSpec;

    private String modelName;

}
