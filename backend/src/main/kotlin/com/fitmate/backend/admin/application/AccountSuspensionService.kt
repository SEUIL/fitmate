package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.domain.ModerationAction
import com.fitmate.backend.admin.repository.AdminSanctionRepository
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.domain.User
import com.fitmate.backend.auth.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AccountSuspensionService(
    private val sanctions: AdminSanctionRepository,
    private val users: UserRepository,
    private val clock: Clock,
) {
    @Transactional
    fun restoreIfExpired(user: User): User {
        if (user.accountStatus != AccountStatus.SUSPENDED) return user
        val latest = sanctions.findFirstByUserIdAndActionOrderByCreatedAtDesc(requireNotNull(user.id), ModerationAction.SUSPEND)
        if (latest?.endsAt?.isAfter(clock.instant()) == false) {
            user.accountStatus = AccountStatus.ACTIVE
            return users.save(user)
        }
        return user
    }
}
