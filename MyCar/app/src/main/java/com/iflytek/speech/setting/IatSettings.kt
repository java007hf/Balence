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
 * 听写设置界面
 */
class IatSettings : PreferenceActivity(), OnPreferenceChangeListener {
    private var mVadbosPreference: EditTextPreference? = null
    private var mVadeosPreference: EditTextPreference? = null

    @Suppress("deprecation")
    public override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferencesName = PREFER_NAME
        addPreferencesFromResource(R.xml.iat_setting)

        mVadbosPreference = findPreference("iat_vadbos_preference") as EditTextPreference
        mVadbosPreference!!.editText.addTextChangedListener(
            SettingTextWatcher(
                this@IatSettings,
                mVadbosPreference!!, 0, 10000
            )
        )

        mVadeosPreference = findPreference("iat_vadeos_preference") as EditTextPreference
        mVadeosPreference!!.editText.addTextChangedListener(
            SettingTextWatcher(
                this@IatSettings,
                mVadeosPreference!!, 0, 10000
            )
        )
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return true
    }

    companion object {
        const val PREFER_NAME: String = "com.iflytek.setting"
    }
}