package com.example.mycar

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.HtmlCompat
import com.example.mycar.Command.sendMsg
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechEvent
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.ui.RecognizerDialog
import com.iflytek.cloud.ui.RecognizerDialogListener
import com.iflytek.cloud.util.ResourceUtil
import com.iflytek.speech.setting.IatSettings
import com.iflytek.speech.setting.TtsSettings
import com.iflytek.speech.util.JsonParser.parseIatResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var hasConnected = false
    private val agentClient = AgentClient("http://192.168.3.102:8000")
    private val scope = CoroutineScope(Dispatchers.Main)
    private var mSharedPreferences: SharedPreferences? = null
    private val PRIVACY_KEY: String = "privacy_key"

    private var mIat: SpeechRecognizer? = null

    // 语音听写UI
    private var mIatDialog: RecognizerDialog? = null

    // 听写结果内容
    private var mResultText: EditText? = null

    // 用HashMap存储听写结果
    private val mIatResults: HashMap<String, String> = LinkedHashMap()
    private var mToast: Toast? = null
    private var mEngineType = "cloud"
    var ret: Int = 0 // 函数调用返回值

    private val mRecognizerDialogListener: RecognizerDialogListener = object : RecognizerDialogListener {
        override fun onResult(results: RecognizerResult, isLast: Boolean) {
            Log.d("benyl",
                "recognizer result：" + results.resultString
            )

            val text = parseIatResult(results.resultString)
            mResultText!!.append(text)
            mResultText!!.setSelection(mResultText!!.length())
        }

        /**
         * 识别回调错误.
         */
        override fun onError(error: SpeechError) {
            showTip(error.getPlainDescription(true))
        }
    }

    /**
     * 听写监听器。
     */
    private val mRecognizerListener: RecognizerListener = object : RecognizerListener {
        override fun onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话")
        }

        override fun onError(error: SpeechError) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            showTip(error.getPlainDescription(true))
        }

        override fun onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话")
        }

        override fun onResult(results: RecognizerResult, isLast: Boolean) {
            val text = parseIatResult(results.resultString)
            mResultText!!.append(text)
            mResultText!!.setSelection(mResultText!!.length())
            if (isLast) {
                //TODO 最后的结果
            }
        }

        override fun onVolumeChanged(volume: Int, data: ByteArray) {
            showTip("当前正在说话，音量大小：$volume")
            Log.d("benyl", "返回音频数据：" + data.size)
        }

        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                val sid = obj.getString(SpeechEvent.KEY_EVENT_AUDIO_URL)
                Log.d("benyl", "session id =$sid")
            }
        }
    }

    /**
     * 初始化监听器。
     */
    private val mInitListener = InitListener { code ->
        Log.d("benyl",
            "SpeechRecognizer init() code = $code"
        )
        if (code != ErrorCode.SUCCESS) {
            showTip("初始化失败，错误码：$code,请点击网址https://www.xfyun.cn/document/error-code查询解决方案")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initASR()
        initView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        findViewById<Button>(R.id.opensetting).setOnClickListener(this)
        findViewById<Button>(R.id.checklinked).setOnClickListener(this)
        findViewById<View>(R.id.iat_recognize).setOnClickListener(this)
        findViewById<View>(R.id.iat_stop).setOnClickListener(this)
        findViewById<View>(R.id.iat_cancel).setOnClickListener(this)
        findViewById<View>(R.id.image_iat_set).setOnClickListener(this)

        //选择云端or本地
        val group = findViewById<View>(R.id.iat_radioGroup) as RadioGroup
        group.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.iat_radioCloud) {
                mEngineType = SpeechConstant.TYPE_CLOUD
            } else if (checkedId == R.id.iat_radioLocal) {
                //离线听写不支持联系人/热词上传
                mEngineType = SpeechConstant.TYPE_LOCAL
            }
        }

//        val frontBtn = findViewById<Button>(R.id.front_btn)
//        frontBtn.setOnClickListener {
//            scope.launch {
//                try {
//                    withContext(Dispatchers.IO) {
//                        agentClient.query("前进").collect { event ->
//                            withContext(Dispatchers.Main) {
//                                when (event.type) {
//                                    "model_response" -> {
//                                        val response = event.data as AgentClient.ModelResponse
//                                        Toast.makeText(this@MainActivity, "收到响应: ${response.content}", Toast.LENGTH_SHORT).show()
//                                    }
//                                    "tool_call" -> {
//                                        val toolCall = event.data as AgentClient.ToolCall
//                                        Toast.makeText(this@MainActivity, "工具调用: ${toolCall.name}", Toast.LENGTH_SHORT).show()
//                                    }
//                                    "error" -> {
//                                        val error = event.data as String
//                                        Toast.makeText(this@MainActivity, "错误: $error", Toast.LENGTH_SHORT).show()
//                                    }
//                                }
//                            }
//                        }
//                    }
//                } catch (e: Exception) {
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(this@MainActivity, "发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//        }
    }

    private fun initASR() {
        mSharedPreferences = getSharedPreferences(TtsSettings.PREFER_NAME, MODE_PRIVATE)
        mSharedPreferences?.let {
            val privacyConfirm: Boolean = it.getBoolean(PRIVACY_KEY, false)
            if (!privacyConfirm) {
                showPrivacyDialog()
            }
        }
        MyCarApp.initializeMsc(this@MainActivity)
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener)

        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = RecognizerDialog(this, mInitListener)
        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, MODE_PRIVATE)
        mResultText = (findViewById<View>(R.id.iat_text) as EditText)
    }

    override fun onClick(view: View) {
        if (null == mIat) {
            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            this.showTip("创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化")
            return
        }

        when (view.id) {
            R.id.image_iat_set -> {
                val intents = Intent(
                    this@MainActivity,
                    IatSettings::class.java
                )
                startActivity(intents)
            }
            R.id.iat_recognize -> {
                mResultText?.setText(null) // 清空显示内容
                mIatResults.clear()
                // 设置参数
                setParam()
                val isShowDialog =
                    mSharedPreferences!!.getBoolean(getString(R.string.pref_key_iat_show), true)
                if (isShowDialog) {
                    // 显示听写对话框
                    mIatDialog!!.setListener(mRecognizerDialogListener)
                    mIatDialog!!.show()
                    showTip(getString(R.string.text_begin))
                } else {
                    // 不显示听写对话框
                    ret = mIat!!.startListening(mRecognizerListener)
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("听写失败,错误码：$ret,请点击网址https://www.xfyun.cn/document/error-code查询解决方案")
                    } else {
                        showTip(getString(R.string.text_begin))
                    }
                }
            }
            R.id.iat_stop -> {
                mIat!!.stopListening()
                showTip("停止听写")
            }
            R.id.iat_cancel -> {
                mIat!!.cancel()
                showTip("取消听写")
            }
            R.id.opensetting -> {
                val intent = Intent()
                intent.setClass(this, SettingsActivity::class.java)
                startActivity(intent)
            }
            R.id.checklinked -> {
                sendMsg(Command.TEST.toString())
            }
            else -> {}
        }
    }

    private fun onRecv(msg: String) {
        Log.d("benyl", "recvMsg===== $msg")
        if (msg.equals("100 test ok")) {
            hasConnected = true
            Toast.makeText(this, "连接正常", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyDialog() {
        val textView = AppCompatTextView(this)
        textView.setPadding(100, 50, 100, 50)
        textView.text = HtmlCompat.fromHtml(
            "我们非常重视对您个人信息的保护，承诺严格按照讯飞开放平台<font color='#3B5FF5'>《隐私政策》</font>保护及处理您的信息，是否确定同意？",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        textView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse("https://www.xfyun.cn/doc/policy/sdk_privacy.html"))
            startActivity(intent)
        }
        val dialog = AlertDialog.Builder(this)
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
                finish()
                System.exit(0)
            }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun showTip(str: String) {
        runOnUiThread {
            if (mToast != null) {
                mToast!!.cancel()
            }
            mToast = Toast.makeText(applicationContext, str, Toast.LENGTH_SHORT)
            mToast?.show()
        }
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

        //mIat.setParameter(MscKeys.REQUEST_AUDIO_URL,"true");

        //	this.mTranslateEnable = mSharedPreferences.getBoolean( this.getString(R.string.pref_key_translate), false );
        if (mEngineType == SpeechConstant.TYPE_LOCAL) {
            // 设置本地识别资源
            mIat!!.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath())
        }
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
            getExternalFilesDir("msc")!!.absolutePath + "/iat.wav"
        )
    }

    private fun getResourcePath(): String {
        val tempBuffer = StringBuffer()
        //识别通用资源
        tempBuffer.append(
            ResourceUtil.generateResourcePath(
                this,
                ResourceUtil.RESOURCE_TYPE.assets,
                "iat/common.jet"
            )
        )
        tempBuffer.append(";")
        tempBuffer.append(
            ResourceUtil.generateResourcePath(
                this,
                ResourceUtil.RESOURCE_TYPE.assets,
                "iat/sms_16k.jet"
            )
        )
        //识别8k资源-使用8k的时候请解开注释
        return tempBuffer.toString()
    }
}

