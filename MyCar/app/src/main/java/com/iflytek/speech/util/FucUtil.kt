package com.iflytek.speech.util

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import org.json.JSONObject
import java.io.IOException

/**
 * 功能性函数扩展类
 */
object FucUtil {
    private var mToast: Toast? = null

    /**
     * 读取asset目录下文件。
     *
     * @return content
     */
    fun readFile(mContext: Context, file: String, code: String): String {
        var len = 0
        var buf: ByteArray? = null
        var result = ""
        try {
            val `in` = mContext.assets.open(file)
            len = `in`.available()
            buf = ByteArray(len)
            `in`.read(buf, 0, len)

            result = String(buf, charset(code))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * 将字节缓冲区按照固定大小进行分割成数组
     *
     * @param buffer 缓冲区
     * @param length 缓冲区大小
     * @param spsize 切割块大小
     * @return
     */
    fun splitBuffer(buffer: ByteArray?, length: Int, spsize: Int): ArrayList<ByteArray> {
        val array = ArrayList<ByteArray>()
        if (spsize <= 0 || length <= 0 || buffer == null || buffer.size < length) return array
        var size = 0
        while (size < length) {
            val left = length - size
            if (spsize < left) {
                val sdata = ByteArray(spsize)
                System.arraycopy(buffer, size, sdata, 0, spsize)
                array.add(sdata)
                size += spsize
            } else {
                val sdata = ByteArray(left)
                System.arraycopy(buffer, size, sdata, 0, left)
                array.add(sdata)
                size += left
            }
        }
        return array
    }

    /**
     * 获取语记是否包含离线听写资源，如未包含跳转至资源下载页面
     * 1.PLUS_LOCAL_ALL: 本地所有资源
     * 2.PLUS_LOCAL_ASR: 本地识别资源
     * 3.PLUS_LOCAL_TTS: 本地合成资源
     */
    fun checkLocalResource(): String {
        val resource = SpeechUtility.getUtility().getParameter(SpeechConstant.PLUS_LOCAL_ASR)
        try {
            val result = JSONObject(resource)
            val ret = result.getInt(SpeechUtility.TAG_RESOURCE_RET)
            when (ret) {
                ErrorCode.SUCCESS -> {
                    val asrArray = result.getJSONObject("result").optJSONArray("asr")
                    if (asrArray != null) {
                        var i = 0
                        // 查询否包含离线听写资源
                        while (i < asrArray.length()) {
                            if ("iat" == asrArray.getJSONObject(i)[SpeechConstant.DOMAIN]) {
                                //asrArray中包含语言、方言字段，后续会增加支持方言的本地听写。
                                //如："accent": "mandarin","language": "zh_cn"
                                break
                            }
                            i++
                        }
                        if (i >= asrArray.length()) {
                            SpeechUtility.getUtility().openEngineSettings(SpeechConstant.ENG_ASR)
                            return "没有听写资源，跳转至资源下载页面"
                        }
                    } else {
                        SpeechUtility.getUtility().openEngineSettings(SpeechConstant.ENG_ASR)
                        return "没有听写资源，跳转至资源下载页面"
                    }
                }

                ErrorCode.ERROR_VERSION_LOWER -> return "语记版本过低，请更新后使用本地功能"
                ErrorCode.ERROR_INVALID_RESULT -> {
                    SpeechUtility.getUtility().openEngineSettings(SpeechConstant.ENG_ASR)
                    return "获取结果出错，跳转至资源下载页面"
                }

                ErrorCode.ERROR_SYSTEM_PREINSTALL -> {}
                else -> {}
            }
        } catch (e: Exception) {
            SpeechUtility.getUtility().openEngineSettings(SpeechConstant.ENG_ASR)
            return "获取结果出错，跳转至资源下载页面"
        }
        return ""
    }

    /**
     * 读取asset目录下音频文件。
     *
     * @return 二进制文件数据
     */
    fun readAudioFile(context: Context, filename: String): ByteArray? {
        try {
            val ins = context.assets.open(filename)
            val data = ByteArray(ins.available())

            ins.read(data)
            ins.close()

            return data
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        return null
    }

    fun showTip(activity: Activity, str: String) {
        activity.runOnUiThread {
            if (mToast != null) {
                mToast!!.cancel()
            }
            mToast = Toast.makeText(activity.applicationContext, str, Toast.LENGTH_SHORT)
            mToast?.show()
        }
    }
}