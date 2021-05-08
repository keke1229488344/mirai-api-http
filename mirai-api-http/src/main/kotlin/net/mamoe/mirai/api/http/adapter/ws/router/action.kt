package net.mamoe.mirai.api.http.adapter.ws.router

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamoe.mirai.api.http.adapter.common.IllegalAccessException
import net.mamoe.mirai.api.http.adapter.common.StateCode
import net.mamoe.mirai.api.http.adapter.internal.action.*
import net.mamoe.mirai.api.http.adapter.internal.action.onAbout
import net.mamoe.mirai.api.http.adapter.internal.action.onBotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.api.http.adapter.internal.action.onMemberJoinRequestEvent
import net.mamoe.mirai.api.http.adapter.internal.action.onNewFriendRequestEvent
import net.mamoe.mirai.api.http.adapter.internal.consts.Paths
import net.mamoe.mirai.api.http.adapter.internal.dto.AuthedDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.DTO
import net.mamoe.mirai.api.http.adapter.internal.dto.StringMapRestfulResult
import net.mamoe.mirai.api.http.adapter.internal.serializer.jsonElementParseOrNull
import net.mamoe.mirai.api.http.adapter.internal.serializer.jsonParseOrNull
import net.mamoe.mirai.api.http.adapter.internal.serializer.toJson
import net.mamoe.mirai.api.http.adapter.internal.serializer.toJsonElement
import net.mamoe.mirai.api.http.adapter.ws.dto.WsIncoming
import net.mamoe.mirai.api.http.adapter.ws.dto.WsOutgoing
import net.mamoe.mirai.api.http.context.session.AuthedSession

internal suspend fun SendChannel<Frame>.handleWsAction(session: AuthedSession, content: String) {
    val commandWrapper = content.jsonParseOrNull<WsIncoming>()
        ?: run {
            send(Frame.Text(StateCode.InvalidParameter.toJson()))
            return
        }


    val element = commandWrapper.content
    val jsonElement: JsonElement = when (commandWrapper.command) {
        // about
        Paths.about -> StringMapRestfulResult(onAbout()).toJsonElement()


        // event
        Paths.newFriend -> execute(session, element, ::onNewFriendRequestEvent)
        Paths.memberJoin -> execute(session, element, ::onMemberJoinRequestEvent)
        Paths.botInvited -> execute(session, element, ::onBotInvitedJoinGroupRequestEvent)


        // friend
        Paths.deleteFriend -> execute(session, element, ::onDeleteFriend)


        // group
        Paths.muteAll -> execute(session, element, ::onMuteAll)
        Paths.unmuteAll -> execute(session, element, ::onUnmuteAll)
        Paths.mute -> execute(session, element, ::onMute)
        Paths.unmute -> execute(session, element, ::onUnmute)
        Paths.kick -> execute(session, element, ::onKick)
        Paths.quit -> execute(session, element, ::onQuit)
        Paths.essence -> execute(session, element, ::onSetEssence)
        Paths.groupConfig -> {
            when (commandWrapper.subCommand) {
                "get" -> {
                    val target = element?.jsonObject?.get("target")?.jsonPrimitive?.content?.toLong()
                    target?.let {
                        onGetGroupConfig(session, target).toJsonElement()
                    } ?: StateCode.InvalidParameter.toJsonElement()
                }
                "post" -> execute(session, element, ::onUpdateGroupConfig)
                else -> StateCode.NoOperateSupport.toJsonElement()
            }
        }
        Paths.memberInfo -> {
            when (commandWrapper.subCommand) {
                "get" -> {
                    val target = element?.jsonObject?.get("target")?.jsonPrimitive?.content?.toLong()
                    val memberId = element?.jsonObject?.get("memberId")?.jsonPrimitive?.content?.toLong()
                    if (target != null && memberId != null) {
                        onGetMemberInfo(session, target, memberId).toJsonElement()
                    } else {
                        StateCode.InvalidParameter.toJsonElement()
                    }
                }
                "post" -> execute(session, element, ::onUpdateMemberInfo)
                else -> StateCode.NoOperateSupport.toJsonElement()
            }
        }


        // info
        Paths.friendList -> onGetFriendList(session).toJsonElement()
        Paths.groupList -> onGetGroupList(session).toJsonElement()
        Paths.memberList -> {
            val target = element?.jsonObject?.get("target")?.jsonPrimitive?.content?.toLong()
            target?.let {
                onGetMemberList(session, it).toJsonElement()
            } ?: StateCode.InvalidParameter.toJsonElement()
        }
        Paths.botProfile -> onGetBotProfile(session).toJsonElement()
        Paths.friendProfile -> {
            val target = element?.jsonObject?.get("target")?.jsonPrimitive?.content?.toLong()
            target?.let {
                onGetFriendProfile(session, it).toJsonElement()
            } ?: StateCode.InvalidParameter.toJsonElement()
        }
        Paths.memberProfile -> {
            val group = element?.jsonObject?.get("group")?.jsonPrimitive?.content?.toLong()
            val member = element?.jsonObject?.get("member")?.jsonPrimitive?.content?.toLong()
            if (group != null && member != null) {
                onGetMemberProfile(session, group, member).toJsonElement()
            } else {
                StateCode.InvalidParameter.toJsonElement()
            }
        }


        // message
        Paths.messageFromId -> {
            val id = element?.jsonObject?.get("id")?.jsonPrimitive?.content?.toInt()
            id?.let {
                onGetMessageFromId(session, id).toJsonElement()
            } ?: StateCode.InvalidParameter.toJsonElement()
        }
        Paths.sendFriendMessage -> execute(session, element, ::onSendFriendMessage)
        Paths.sendGroupMessage -> execute(session, element, ::onSendGroupMessage)
        Paths.sendTempMessage -> execute(session, element, ::onSendTempMessage)
        Paths.sendImageMessage -> execute(session, element, ::onSendImageMessage)
        Paths.uploadImage -> StateCode.NoOperateSupport.toJsonElement()
        Paths.uploadVoice -> StateCode.NoOperateSupport.toJsonElement()
        Paths.recall -> execute(session, element, ::onRecall)
        Paths.sendNudge -> execute(session, element, ::onNudge)
        else -> StateCode.NoOperateSupport.toJsonElement()
    }

    send(Frame.Text(WsOutgoing(
        syncId = commandWrapper.syncId,
        data = jsonElement
    ).toJson()))
}

private suspend inline fun <reified T : AuthedDTO, reified R : DTO> execute(
    session: AuthedSession,
    content: JsonElement?,
    crossinline action: suspend (T) -> R
): JsonElement {
    val parameter = parseContent<T>(content)
    parameter.session = session
    return action(parameter).toJsonElement()
}

private inline fun <reified T : AuthedDTO> parseContent(content: JsonElement?): T =
    content?.jsonElementParseOrNull() ?: throw IllegalAccessException()