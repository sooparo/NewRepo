package com.sooparo.schedulecatcher

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        BriefingScheduler.schedule(this)
    }
}
