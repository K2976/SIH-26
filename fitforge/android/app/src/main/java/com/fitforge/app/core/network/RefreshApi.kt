package com.fitforge.app.core.network

import javax.inject.Qualifier

/**
 * Marks the bare Retrofit/AuthApi instance used only for token refresh —
 * it must not carry [AuthInterceptor] or [TokenAuthenticator], or
 * refreshing would recurse into itself.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshApi
