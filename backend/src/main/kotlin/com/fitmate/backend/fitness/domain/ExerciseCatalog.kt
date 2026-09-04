package com.fitmate.backend.fitness.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "fitness_exercise_catalog")
class ExerciseCatalog(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    var sport: Sport? = null,
    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",
    @Column(name = "normalized_name", nullable = false, length = 100)
    var normalizedName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "body_part", nullable = false, length = 32)
    var bodyPart: BodyPart = BodyPart.CHEST,
    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_type", nullable = false, length = 32)
    var equipmentType: EquipmentType = EquipmentType.MACHINE,
    @Column(name = "reference_url", length = 512)
    var referenceUrl: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseTimeEntity()
