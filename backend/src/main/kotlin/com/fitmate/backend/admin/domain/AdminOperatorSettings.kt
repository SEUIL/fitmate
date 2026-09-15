package com.fitmate.backend.admin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** One lock shared by all role mutations, including mutual revocation attempts. */
@Entity
@Table(name = "ops_operator_settings")
class AdminOperatorSettings(
    @Id var id: Int = 1,
    @Column(nullable = false) var version: Long = 0,
)
