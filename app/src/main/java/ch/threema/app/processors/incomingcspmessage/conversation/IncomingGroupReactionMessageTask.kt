/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.GroupModel

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupReactionMessageTask")

class IncomingGroupReactionMessageTask(
    message: GroupReactionMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupReactionMessage>(message, triggerSource, serviceManager) {

    private val messageService by lazy { serviceManager.messageService }
    private val groupService by lazy { serviceManager.groupService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.debug("IncomingGroupReactionMessageTask from remote id: {}", message.data.messageId)

        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
            ?: return ReceiveStepsResult.DISCARD

        return applyGroupReaction(groupModel)
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.debug("IncomingGroupReactionMessageTask from sync id: {}", message.data.messageId)

        val groupModel = groupService.getByGroupMessage(message)

        if (groupModel == null) {
            logger.error("Received a reflected group reaction message in an unknown group")
            return ReceiveStepsResult.DISCARD
        }

        return applyGroupReaction(groupModel)
    }

    private fun applyGroupReaction(groupModel: GroupModel): ReceiveStepsResult {
        val receiver = groupService.createReceiver(groupModel)
        val targetMessage = runCommonReactionMessageReceiveSteps(message, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD
        val emojiSequence = runCommonReactionMessageReceiveEmojiSequenceConversion(message.data.emojiSequenceBytes)
            ?: return ReceiveStepsResult.DISCARD

        messageService.saveEmojiReactionMessage(targetMessage, message.fromIdentity, message.data.actionCase, emojiSequence)

        return ReceiveStepsResult.SUCCESS
    }
}
