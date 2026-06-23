package com.tavern.lite.di

import javax.inject.Qualifier

/**
 * 标记注入应用生命周期 CoroutineScope 的限定符
 * 用于 Singleton 中需要 fire-and-forget 后台任务的场景
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
