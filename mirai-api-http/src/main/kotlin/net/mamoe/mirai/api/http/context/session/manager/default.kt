/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.api.http.context.session.manager

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.api.http.context.session.AuthedSession
import net.mamoe.mirai.api.http.context.session.Session
import net.mamoe.mirai.api.http.context.session.SessionManager
import net.mamoe.mirai.api.http.context.session.TempSession
import kotlin.coroutines.EmptyCoroutineContext

class DefaultSessionManager(val authKey: String) : SessionManager {
    private val sessionMap: MutableMap<String, Session> = mutableMapOf()

    override fun createTempSession(): TempSession =
        TempSession(generateSessionKey(), EmptyCoroutineContext).also { newTempSession ->
            sessionMap[newTempSession.key] = newTempSession
            //设置180000ms后检测并回收
            newTempSession.launch {
                delay(180000)
                sessionMap[newTempSession.key]?.run {
                    if (this is TempSession)
                        closeSession(newTempSession.key)
                }
            }
        }

    override fun authSession(bot: Bot, tempSessionKey: String) =
        AuthedSession(bot, tempSessionKey, EmptyCoroutineContext).also { session ->
            closeSession(tempSessionKey)
            set(tempSessionKey, session)
        }

    override fun authSession(bot: Bot, tempSession: TempSession) = authSession(bot, tempSession.key)

    override operator fun get(key: String) = sessionMap[key]

    override fun set(key: String, session: Session) = sessionMap.set(key, session)

    override fun closeSession(key: String) {
        sessionMap[key]?.close()
    }

    override fun closeSession(session: Session) {
        closeSession(session.key)
    }

    override fun close() = sessionMap.forEach { (_, session) -> session.close() }
}
