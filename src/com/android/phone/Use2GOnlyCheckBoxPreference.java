/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

public class Use2GOnlyCheckBoxPreference extends CheckBoxPreference {
    private static final String LOG_TAG = "Use2GOnlyCheckBoxPreference";

    private Phone mPhone;
    private MyHandler mHandler;
    private Context mContext;

    public Use2GOnlyCheckBoxPreference(Context context) {
        this(context, null);
    }

    public Use2GOnlyCheckBoxPreference(Context context, AttributeSet attrs) {
        this(context, attrs,com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public Use2GOnlyCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mPhone = PhoneGlobals.getPhone();
        mHandler = new MyHandler();
        mPhone.getPreferredNetworkType(
                mHandler.obtainMessage(MyHandler.MESSAGE_GET_PREFERRED_NETWORK_TYPE));
    }

    private int getDefaultNetworkMode() {
        int mode = SystemProperties.getInt("ro.telephony.default_network",
                Phone.PREFERRED_NT_MODE);
        Log.i(LOG_TAG, "getDefaultNetworkMode: mode=" + mode);
        return mode;
    }

    @Override
    protected void  onClick() {
        super.onClick();

        int networkType = isChecked() ? Phone.NT_MODE_GSM_ONLY : getDefaultNetworkMode();
        if (mPhone.getState() == PhoneConstants.State.IDLE) {
            Log.i(LOG_TAG, "set preferred network type=" + networkType);
            android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE, networkType);
            mPhone.setPreferredNetworkType(networkType,
                    mHandler.obtainMessage(MyHandler.MESSAGE_SET_PREFERRED_NETWORK_TYPE));
            // Disable the setting till we get a response.
            setEnabled(false);
        } else {
            Toast message = Toast.makeText(mContext,
                    mContext.getResources().getText(R.string.rat_not_allowed), Toast.LENGTH_SHORT);
            message.show();
            setChecked(networkType != Phone.NT_MODE_GSM_ONLY);
        }
   }

    private class MyHandler extends Handler {

        static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
        static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
                    handleGetPreferredNetworkTypeResponse(msg);
                    break;

                case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                    handleSetPreferredNetworkTypeResponse(msg);
                    break;
            }
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                int type = ((int[])ar.result)[0];
                if (type != Phone.NT_MODE_GSM_ONLY) {
                    // Back to default
                    type = getDefaultNetworkMode();
                }
                Log.i(LOG_TAG, "get preferred network type="+type);
                setChecked(type == Phone.NT_MODE_GSM_ONLY);
                int storedNetworkMode = android.provider.Settings.Global.getInt(
                        mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        Phone.PREFERRED_NT_MODE);

                // check changes in currentNetworkMode and updates storedNetworkMode
                if (type != storedNetworkMode) {
                    storedNetworkMode = type;
                    Log.i(LOG_TAG, "handleGetPreferredNetworkTypeResponse: " +
                                "storedNetworkMode = " + storedNetworkMode);
                    // changes the Settings.System accordingly to currentNetworkMode
                    android.provider.Settings.Global.putInt(
                            mPhone.getContext().getContentResolver(),
                            android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                            storedNetworkMode);
                }
            } else {
                // Weird state, disable the setting
                Log.i(LOG_TAG, "get preferred network type, exception="+ar.exception);
                setEnabled(false);
            }
        }

        private void handleSetPreferredNetworkTypeResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                // Yikes, error, disable the setting
                setEnabled(false);
                // Set UI to current state
                Log.i(LOG_TAG, "set preferred network type, exception=" + ar.exception);
                mPhone.getPreferredNetworkType(obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE));
            } else {
                Log.i(LOG_TAG, "set preferred network type done");
                setEnabled(true);
                android.provider.Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                        android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                        isChecked() ? Phone.NT_MODE_GSM_ONLY : Phone.NT_MODE_WCDMA_PREF);
            }
        }
    }
}
