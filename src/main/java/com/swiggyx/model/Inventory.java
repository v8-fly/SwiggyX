package com.swiggyx.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @EmbeddedId
    private InventoryId id;

    @Column(nullable = false)
    private Integer count;

    @Version
    private Long version;

}O