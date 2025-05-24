package com.iflytek.speech.setting

import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceActivity
import android.view.Window
import com.example.mycar.R
import com.iflytek.speech.util.SettingTextWatcher

/**
 * 合成设置界面
 */
class TtsSettings : PreferenceActivity(), OnPreferenceChangeListener {
    private var mSpeedPreference: EditTextPreference? = null
    private var mPitchPreference: EditTextPreference? = null
    private var mVolumePreference: EditTextPreference? = null

    @Suppress("deprecation")
    public override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        // 指定保存文件名字
        preferenceManager.sharedPreferencesName = PREFER_NAME
        addPreferencesFromResource(R.xml.tts_setting)
        mSpeedPreference = findPreference("speed_preference") as EditTextPreference
        mSpeedPreference?.apply {
            editText.addTextChangedListener(
                SettingTextWatcher(
                    this@TtsSettings,
                    this,
                    0,
                    200
                )
            )
        }

        mPitchPreference = findPreference("pitch_preference") as EditTextPreference
        mPitchPreference?.apply {
            editText.addTextChangedListener(
                SettingTextWatcher(
                    this@TtsSettings,
                    this,
                    0,
                    100
                )
            )
        }

        mVolumePreference = findPreference("volume_preference") as EditTextPreference
        mVolumePreference?.apply {
            editText.addTextChangedListener(
                SettingTextWatcher(
                    this@TtsSettings,
                    this,
                    0,
                    100
                )
            )
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return true
    }


    companion object {
        const val PREFER_NAME: String = "com.iflytek.setting"
    }
}