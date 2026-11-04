package com.series76.assistant

import android.app.Application
import com.series76.assistant.di.AppContainer

class Series76Application : Application() {
    val container by lazy { AppContainer(this) }
}
