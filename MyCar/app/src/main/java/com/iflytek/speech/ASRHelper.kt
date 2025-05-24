package com.iflytek.speech

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.HtmlCompat
import com.example.mycar.MyCarApp
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.speech.setting.IatSettings
import com.iflytek.speech.setting.TtsSettings
import com.iflytek.speech.util.FucUtil.showTip
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerListener

class ASRHelper {
    private var mSharedPreferences: SharedPreferences? = null
    private val PRIVACY_KEY: String = "privacy_key"

    private var mIat: SpeechRecognizer? = null
    // 用HashMap存储听写结果
    private val mIatResults: HashMap<String, String> = LinkedHashMap()
    private var mEngineType = "cloud"
    var ret: Int = 0 // 函数调用返回值
    var mContext: Context? = null

    /**
     * 初始化监听器。
     */
    private val mInitListener = InitListener { code ->
        Log.d("benyl",
            "SpeechRecognizer init() code = $code"
        )
        if (code != ErrorCode.SUCCESS) {
            val activity = mContext as Activity
            showTip(activity, "初始化失败，错误码：$code,请点击网址https://www.xfyun.cn/document/error-code查询解决方案")
        }
    }

    fun initASR(context: Context) {
        mContext = context
        mEngineType = SpeechConstant.TYPE_CLOUD
        mSharedPreferences = mContext?.getSharedPreferences(TtsSettings.PREFER_NAME, MODE_PRIVATE)
        mSharedPreferences?.let {
            val privacyConfirm: Boolean = it.getBoolean(PRIVACY_KEY, false)
            if (!privacyConfirm) {
                showPrivacyDialog()
            }
        }
        MyCarApp.initializeMsc(context)
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(context, mInitListener)

        mSharedPreferences = mContext?.getSharedPreferences(IatSettings.PREFER_NAME, MODE_PRIVATE)
    }

    fun isInit(): Boolean {
        return mIat!=null
    }

    /**
     * 参数设置
     *
     * @return
     */
    fun setParam() {
        // 清空参数
        mIat!!.setParameter(SpeechConstant.PARAMS, null)
        val lag = mSharedPreferences!!.getString("iat_language_preference", "mandarin")!!
        // 设置引擎
        mIat!!.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType)
        // 设置返回结果格式
        mIat!!.setParameter(SpeechConstant.RESULT_TYPE, "json")

        // 在线听写支持多种小语种，若想了解请下载在线听写能力，参看其speechDemo
        if (lag == "en_us") {
            // 设置语言
            mIat!!.setParameter(SpeechConstant.LANGUAGE, "en_us")
            mIat!!.setParameter(SpeechConstant.ACCENT, null)

            // 设置语言
            mIat!!.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            // 设置语言区域
            mIat!!.setParameter(SpeechConstant.ACCENT, lag)
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat!!.setParameter(
            SpeechConstant.VAD_BOS,
            mSharedPreferences!!.getString("iat_vadbos_preference", "4000")
        )

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat!!.setParameter(
            SpeechConstant.VAD_EOS,
            mSharedPreferences!!.getString("iat_vadeos_preference", "1000")
        )

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat!!.setParameter(
            SpeechConstant.ASR_PTT,
            mSharedPreferences!!.getString("iat_punc_preference", "1")
        )

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mIat!!.setParameter(SpeechConstant.AUDIO_FORMAT, "wav")
        mIat!!.setParameter(
            SpeechConstant.ASR_AUDIO_PATH,
            mContext?.getExternalFilesDir("msc")!!.absolutePath + "/iat.wav"
        )
    }

    private fun showPrivacyDialog() {
        mContext?.let {
            val textView = AppCompatTextView(it)
            textView.setPadding(100, 50, 100, 50)
            textView.text = HtmlCompat.fromHtml(
                "是否确定同意？",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            val dialog = AlertDialog.Builder(it)
                .setTitle("温馨提示")
                .setView(textView)
                .setPositiveButton("同意") { dialog, which ->
                    mSharedPreferences?.apply {
                        edit().putBoolean(PRIVACY_KEY, true).apply()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(
                    "不同意"
                ) { dialog, which ->
                    mSharedPreferences?.apply {
                        edit().putBoolean(PRIVACY_KEY, false).apply()
                    }
                    System.exit(0)
                }
                .create()
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
        }
    }

    fun startListening(recognizer: RecognizerListener):Int {
        mIatResults.clear()
        // 设置参数
        setParam()
        // 不显示听写对话框
        ret = mIat!!.startListening(recognizer)
        return ret
    }

    fun stopListening() {
        mIat!!.stopListening()
    }

    fun cancel() {
        mIat!!.cancel()
    }
}