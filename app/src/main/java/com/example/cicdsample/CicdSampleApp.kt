package com.example.cicdsample

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt 의존성 그래프가 시작되는 지점. */
@HiltAndroidApp
class CicdSampleApp : Application()
