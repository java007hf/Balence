package com.example.mycar

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility

class MyCarApp : Application() {
    override fun onCreate() {
        init()
        super.onCreate()
    }

    private fun init() {
        // 打印日志
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                Log.d("Activity Resumed ----- ", activity.javaClass.name)
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })
    }

    companion object {
        const val PRIVACY_KEY: String = "privacy_key"
        private var mscInitialize = false

        fun initializeMsc(context: Context) {
            if (mscInitialize) return
            // 应用程序入口处调用,避免手机内存过小,杀死后台进程后通过历史intent进入Activity造成SpeechUtility对象为null
            // 注意：此接口在非主进程调用会返回null对象，如需在非主进程使用语音功能，请增加参数：SpeechConstant.FORCE_LOGIN+"=true"
            // 参数间使用“,”分隔。
            // 设置你申请的应用appid
            // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
            val param = StringBuffer()
            param.append("appid=" + context.getString(R.string.app_id))
            param.append(",")
            // 设置使用v5+
            param.append(SpeechConstant.ENGINE_MODE + "=" + SpeechConstant.MODE_MSC)
            SpeechUtility.createUtility(context, param.toString())
            mscInitialize = true
        }
    }
}