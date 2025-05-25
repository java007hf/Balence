package com.example.mycar

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mycar.Command.sendMsg
import com.iflytek.cloud.ErrorCode

import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechEvent
import com.iflytek.speech.ASRHelper
import com.iflytek.speech.setting.IatSettings
import com.iflytek.speech.util.FucUtil.showTip
import com.iflytek.speech.util.JsonParser.parseIatResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var hasConnected = false
    private val agentClient = AgentClient("http://192.168.3.102:8000")
    private val scope = CoroutineScope(Dispatchers.Main)
    private var asrHelper: ASRHelper? = null

    // 听写结果内容
    private var mResultText: TextView? = null

    /**
     * 听写监听器。
     */
    private val mRecognizerListener: RecognizerListener = object : RecognizerListener {
        override fun onVolumeChanged(volume: Int, data: ByteArray?) {
//            showTip("当前正在说话，音量大小：$volume")
            data?.let {
                Log.d("benyl", "返回音频数据：" + it.size)
            }
        }

        override fun onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip(this@MainActivity, "开始说话")
        }

        override fun onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip(this@MainActivity, "结束说话")
        }

        override fun onResult(p0: RecognizerResult?, isLast: Boolean) {
            p0?.let {
                val text = parseIatResult(it.resultString)
                mResultText!!.append(text)
//                mResultText!!.setSelection(mResultText!!.length())
                if (isLast) {
                    //TODO 最后的结果
                }
            }
        }

        override fun onError(p0: SpeechError?) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            p0?.let {
                showTip(this@MainActivity, it.getPlainDescription(true))
            }

        }

        override fun onEvent(eventType: Int, p1: Int, p2: Int, obj: Bundle?) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                obj?.let {
                    val sid = it.getString(SpeechEvent.KEY_EVENT_AUDIO_URL)
                    Log.d("benyl", "session id =$sid")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        initASR()
        initView()

        // 订阅 AgentClient 的事件流
        scope.launch(Dispatchers.IO) {
            agentClient.getEventFlow().collect { event ->
                withContext(Dispatchers.Main) {  // 切回主线程更新 UI
                    when (event.type) {
                        "model_delta" -> {
                            val modelResponse = event.data as String
                            mResultText?.append(modelResponse)
                        }
                        "tool_call" -> {
                            val toolCall = event.data as AgentClient.ToolCall
                            mResultText?.append("工具调用：${toolCall.name}，参数：${toolCall.arguments}\n")
                        }
                        "error" -> {
                            val errorMsg = event.data as String
                            showTip(this@MainActivity, "错误：$errorMsg")
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        mResultText = (findViewById<View>(R.id.iat_text) as TextView)
        findViewById<Button>(R.id.opensetting).setOnClickListener(this)
        findViewById<Button>(R.id.checklinked).setOnClickListener(this)
        findViewById<View>(R.id.iat_recognize).setOnClickListener(this)
        findViewById<View>(R.id.iat_stop).setOnClickListener(this)
        findViewById<View>(R.id.iat_cancel).setOnClickListener(this)
        findViewById<View>(R.id.image_iat_set).setOnClickListener(this)
    }

    private fun initASR() {
        asrHelper = ASRHelper()
        asrHelper?.initASR(this)
    }

    override fun onClick(view: View) {
        asrHelper?.let {
            if (!it.isInit()) {
                // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
                showTip(this@MainActivity, "创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化")
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
                    val ret = asrHelper?.startListening(mRecognizerListener)

                    if (ret != ErrorCode.SUCCESS) {
                        showTip(this@MainActivity, "听写失败,错误码：$ret,请点击网址https://www.xfyun.cn/document/error-code查询解决方案")
                    } else {
                        showTip(this@MainActivity, getString(R.string.text_begin))
                    }
                }
                R.id.iat_stop -> {
                    asrHelper?.stopListening()
                    showTip(this@MainActivity, "停止听写")
                }
                R.id.iat_cancel -> {
                    asrHelper?.cancel()
                    showTip(this@MainActivity, "取消听写")
                }
                R.id.opensetting -> {
                    val intent = Intent()
                    intent.setClass(this, SettingsActivity::class.java)
                    startActivity(intent)
                }
                R.id.checklinked -> {
//                    sendMsg(Command.TEST.toString())
                    scope.launch {
                        agentClient.query("前进")
                    }

                }
                else -> {}
            }
        }
    }

    private fun onRecv(msg: String) {
        Log.d("benyl", "recvMsg===== $msg")
        if (msg.equals("100 test ok")) {
            hasConnected = true
            Toast.makeText(this, "连接正常", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()  // 取消协程作用域，停止事件监听
    }
}

