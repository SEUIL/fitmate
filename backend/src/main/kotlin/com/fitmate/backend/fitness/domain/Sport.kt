package com.fitmate.backend.fitness.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "fitness_sports")
class Sport(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 32)
    var code: SportCode = SportCode.HEALTH,
    @Column(name = "name", nullable = false, length = 50)
    var name: String = "",
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false,
) : BaseTimeEntity()
