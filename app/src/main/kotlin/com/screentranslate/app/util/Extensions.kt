package com.screentranslate.app.util

import android.app.Application
import com.screentranslate.app.AppContainer
import com.screentranslate.app.ScreenTranslateApp

val Application.appContainer: AppContainer
    get() = (this as ScreenTranslateApp).container
