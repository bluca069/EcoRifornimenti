package net.ecorifornimenti.app.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun engineHttpPredefinito(): HttpClientEngine = OkHttp.create()
