package com.fitmate.backend.notification.application

import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.member.repository.NotificationSettingRepository
import com.fitmate.backend.notification.domain.Notification
import com.fitmate.backend.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service class NotificationWriter(private val settings:NotificationSettingRepository,private val notifications:NotificationRepository){
 fun mate(userId:UUID,actorId:UUID?,type:String,eventKey:String,payload:String){if(userId==actorId)return;val setting=settings.findById(userId).orElse(null)?:return;if(!setting.inAppEnabled||!setting.mateEnabled||notifications.existsByUserIdAndEventKey(userId,eventKey))return;notifications.save(Notification(UuidBinaryConverter.toMysqlOrderedUuid(UUID.randomUUID()),userId,actorId,type,eventKey,payload))}
}
