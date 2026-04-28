/*
 *  Copyright (C) 2017 OrionStar Technology Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.ainirobot.robotos.fragment;

import android.content.Context;
import android.os.RemoteException;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.graphics.Color;

import androidx.fragment.app.Fragment;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.actionbean.Pose;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.speech.SkillApi;
import com.ainirobot.robotos.LogTools;
import com.ainirobot.robotos.R;
import com.ainirobot.robotos.application.ModuleCallback;
import com.ainirobot.robotos.application.RobotOSApplication;
import com.ainirobot.robotos.application.SpeechCallback;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.List;

public class NavigationFragment extends BaseFragment {

    private Button mTurn_direction;
    private Button mStop_navigation;
    private Button mStart_navigation;
    private EditText mNavigation_point;
    private Gson mGson;

    // 语音/NLU 测试 + 语音导航
    private EditText mEtNavNluInput;
    private Button mBtnNavNluQuery;
    private Button mBtnNavNluClear;
    private Button mBtnNavVoiceInput;
    private TextView mTvNavVoiceStatus;
    private TextView mTvNavDomain;
    private TextView mTvNavIntent;
    private TextView mTvNavDestination;
    private TextView mTvNavRawResult;
    private SkillApi mSkillApi;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private String mLastVoicePlaceName;
    private boolean mVoiceInputEnabled = false;

    @Override
    public View onCreateView(Context context) {
        View root = mInflater.inflate(R.layout.fragment_navigation_layout, null, false);
        mSkillApi = RobotOSApplication.getInstance().getSkillApi();
        initViews(root);
        initVoiceNluViews(root);
        return root;
    }

    private void initViews(View root) {
        mTurn_direction = (Button) root.findViewById(R.id.turn_direction);
        mStop_navigation = (Button) root.findViewById(R.id.stop_navigation);
        mStart_navigation = (Button) root.findViewById(R.id.start_navigation);
        mNavigation_point = (EditText) root.findViewById(R.id.et_navigation_point);

        mStart_navigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNavigation("");
            }
        });

        mStop_navigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopNavigation();
            }
        });

        mTurn_direction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeSpecialPlaceTheta();
            }
        });

    }

    private void initVoiceNluViews(View root) {
        mEtNavNluInput = root.findViewById(R.id.et_nav_nlu_input);
        mBtnNavVoiceInput = root.findViewById(R.id.btn_nav_voice_input);
        mBtnNavNluQuery = root.findViewById(R.id.btn_nav_nlu_query);
        mBtnNavNluClear = root.findViewById(R.id.btn_nav_nlu_clear);
        mTvNavVoiceStatus = root.findViewById(R.id.tv_nav_voice_status);
        mTvNavDomain = root.findViewById(R.id.tv_nav_domain);
        mTvNavIntent = root.findViewById(R.id.tv_nav_intent);
        mTvNavDestination = root.findViewById(R.id.tv_nav_destination);
        mTvNavRawResult = root.findViewById(R.id.tv_nav_raw_result);

        if (mBtnNavVoiceInput != null) {
            updateVoiceInputUi();
            mBtnNavVoiceInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mVoiceInputEnabled = !mVoiceInputEnabled;
                    updateVoiceInputUi();
                }
            });
        }

        mBtnNavNluQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mEtNavNluInput.getText().toString().trim();
                if (TextUtils.isEmpty(text)) {
                    return;
                }
                queryByText(text);
            }
        });

        mBtnNavNluClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearVoiceResults();
            }
        });

        registerVoiceListeners();
    }

    private void updateVoiceInputUi() {
        if (mBtnNavVoiceInput == null) {
            return;
        }
        if (mVoiceInputEnabled) {
            mBtnNavVoiceInput.setText(R.string.nlu_voice_input_on);
            mBtnNavVoiceInput.setBackgroundColor(Color.parseColor("#2196F3"));
        } else {
            mBtnNavVoiceInput.setText(R.string.nlu_voice_input_off);
            mBtnNavVoiceInput.setBackgroundColor(Color.parseColor("#9E9E9E"));
        }
        mBtnNavVoiceInput.setTextColor(Color.WHITE);
    }

    private void registerVoiceListeners() {
        SpeechCallback.setNluResultListener(new SpeechCallback.NluResultListener() {
            @Override
            public void onNluResult(final String rawResult) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!mVoiceInputEnabled) {
                            return;
                        }
                        parseAndDisplayVoiceResult(rawResult);
                    }
                });
            }

            @Override
            public void onAsrResult(final String asrResult) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvNavVoiceStatus != null) {
                            mTvNavVoiceStatus.setText("ASR: " + asrResult);
                        }
                    }
                });
            }
        });

        ModuleCallback.setSpeechRequestListener(new ModuleCallback.SpeechRequestListener() {
            @Override
            public void onSpeechRequest(int reqId, final String reqType, final String reqText, final String reqParam) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvNavVoiceStatus != null) {
                            mTvNavVoiceStatus.setText("语音识别: " + reqText);
                        }
                        if (!mVoiceInputEnabled) {
                            return;
                        }
                        if (!TextUtils.isEmpty(reqParam)) {
                            parseAndDisplayVoiceResult(reqParam);
                        }
                    }
                });
            }
        });
    }

    private void queryByText(String text) {
        if (mSkillApi == null) {
            mSkillApi = RobotOSApplication.getInstance().getSkillApi();
        }
        if (mSkillApi != null) {
            if (mTvNavVoiceStatus != null) {
                mTvNavVoiceStatus.setText("查询中: " + text);
            }
            LogTools.info("NavigationFragment queryByText: " + text);
            mSkillApi.queryByText(text);
        } else {
            if (mTvNavVoiceStatus != null) {
                mTvNavVoiceStatus.setText("SkillApi 未连接");
            }
            LogTools.info("NavigationFragment: SkillApi is null");
        }
    }

    private void parseAndDisplayVoiceResult(String rawResult) {
        if (TextUtils.isEmpty(rawResult)) {
            return;
        }

        if (mTvNavRawResult != null) {
            mTvNavRawResult.setText(rawResult);
        }

        String domain = "--";
        String intent = "--";
        String dest = null;
        try {
            JSONObject json = new JSONObject(rawResult);
            domain = json.optString("englishDomain", "--");
            intent = json.optString("englishIntent", "--");
            dest = extractDestination(json);
        } catch (Exception e) {
            LogTools.info("NavigationFragment parse error: " + e.getMessage());
        }

        if (mTvNavDomain != null) {
            mTvNavDomain.setText(domain);
        }
        if (mTvNavIntent != null) {
            mTvNavIntent.setText(intent);
        }

        if (!TextUtils.isEmpty(dest)) {
            mLastVoicePlaceName = dest;
            if (mTvNavDestination != null) {
                mTvNavDestination.setText(dest);
            }
            if (mNavigation_point != null) {
                mNavigation_point.setText(dest);
                mNavigation_point.setSelection(dest.length());
            }
        } else {
            if (mTvNavDestination != null) {
                mTvNavDestination.setText("--");
            }
        }
    }

    private String extractDestination(JSONObject json) {
        if (json == null) {
            return null;
        }

        // 0) 实际使用的解析逻辑
        try {
            // 先尝试作为 JSONObject 获取
            JSONObject nlpData = json.optJSONObject("nlpData");

            // 如果获取不到，尝试作为字符串解析
            if (nlpData == null) {
                String nlpDataStr = json.optString("nlpData", "");
                if (!nlpDataStr.isEmpty()) {
                    nlpData = new JSONObject(nlpDataStr);
                }
            }

            if (nlpData != null) {
                JSONArray nlpDetail = nlpData.optJSONArray("detail");
                if (nlpDetail != null && nlpDetail.length() > 0) {
                    JSONObject firstDetail = nlpDetail.optJSONObject(0);
                    if (firstDetail != null) {
                        JSONObject slots = firstDetail.optJSONObject("slots");
                        if (slots != null) {
                            JSONArray destination = slots.optJSONArray("destination");
                            if (destination == null) {
                                destination = slots.optJSONArray("location");
                            }
                            if (destination != null && destination.length() > 0) {
                                JSONObject destObj = destination.optJSONObject(0);
                                if (destObj != null) {
                                    // 优先取value，如果没有则取text
                                    String destinationValue = destObj.optString("value", "");
                                    if (destinationValue.isEmpty()) {
                                        destinationValue = destObj.optString("text", "");
                                    }
                                    return destinationValue;
                                }
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            // 处理解析异常
            e.printStackTrace();
        }

        // 1) 直接字段（不同链路可能会带）
        String direct = firstNonEmpty(
                json.optString("destination", null),
                json.optString("targetPlace", null),
                json.optString("placeName", null),
                json.optString("poi", null),
                json.optString("goal", null),
                json.optString("target", null)
        );
        if (!TextUtils.isEmpty(direct)) {
            return direct;
        }

        // 2) slots: [{name, value/text/normValue/...}]
        String fromSlots = readSlotsValue(json.optJSONArray("slots"));
        if (!TextUtils.isEmpty(fromSlots)) {
            return fromSlots;
        }

        // 3) semantic.slots
        JSONObject semantic = json.optJSONObject("semantic");
        if (semantic != null) {
            String semSlots = readSlotsValue(semantic.optJSONArray("slots"));
            if (!TextUtils.isEmpty(semSlots)) {
                return semSlots;
            }
        }

        return null;
    }

    private String readSlotsValue(JSONArray slots) {
        if (slots == null) {
            return null;
        }
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.optJSONObject(i);
            if (slot == null) {
                continue;
            }
            String name = firstNonEmpty(
                    slot.optString("name", ""),
                    slot.optString("slotName", ""),
                    slot.optString("dict_name", "")
            );
            if (!isDestinationSlotName(name)) {
                continue;
            }

            String value = firstNonEmpty(
                    slot.optString("value", null),
                    slot.optString("normValue", null),
                    slot.optString("text", null),
                    slot.optString("rawValue", null)
            );
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isDestinationSlotName(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        String n = name.toLowerCase();
        return n.contains("dest")
                || n.contains("destination")
                || n.contains("place")
                || n.contains("poi")
                || n.contains("goal")
                || n.contains("target")
                || n.contains("location")
                || n.contains("目的地")
                || n.contains("地点");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) {
                return v;
            }
        }
        return null;
    }

    private void clearVoiceResults() {
        mLastVoicePlaceName = null;
        if (mTvNavDomain != null) {
            mTvNavDomain.setText("--");
        }
        if (mTvNavIntent != null) {
            mTvNavIntent.setText("--");
        }
        if (mTvNavDestination != null) {
            mTvNavDestination.setText("--");
        }
        if (mTvNavRawResult != null) {
            mTvNavRawResult.setText("--");
        }
        if (mTvNavVoiceStatus != null) {
            mTvNavVoiceStatus.setText(getString(R.string.nlu_voice_waiting));
        }
        if (mEtNavNluInput != null) {
            mEtNavNluInput.setText("");
        }
    }

    private String getNavigationPoint(){
        String leadPoint = mNavigation_point.getText().toString();
        if(TextUtils.isEmpty(leadPoint)){
            leadPoint = mNavigation_point.getHint().toString();
        }
        return leadPoint;
    }

    private void getPoint() {
        //此为闸机线的两个点位在机器里存的数据，可以方便做校验，存的格式为"x_y"
        String startGate = Settings.Global.getString(getContext().getContentResolver(),
                "gate_edge_pixel_enter_node");
        String endGate = Settings.Global.getString(getContext().getContentResolver(),
                "gate_edge_pixel_outer_node");
        LogTools.info("原本闸机入出口: " + startGate + "," + endGate);
    }

    /**
     * startNavigation
     * 导航到指定位置
     */
    private void startNavigation(String name) {
        RobotApi.getInstance().startNavigation(0, name.length() > 0 ? name : getNavigationPoint(), 1.5, 10 * 1000, mNavigationListener);
        //若为Pose则直接导航到对应Pose
        //startNavigation(int reqId, Pose pose, double coordinateDeviation, long time, ActionListener listener)
    }


    /**
     * stopNavigation
     * 停止导航到指定位置
     */
    private void stopNavigation() {
        RobotApi.getInstance().stopNavigation(0);
    }

    /**
     * turn to target point direction
     * 转向目标点方向
     * Notice: this function only make robot target the point, but do not move
     * 方法说明：该接口只会左右转动到目标点方位，不会实际运动到目标点。
     */
    private void resumeSpecialPlaceTheta() {
        String navigationPoint = getNavigationPoint();
        if(TextUtils.isEmpty(navigationPoint)){
            LogTools.info("Point not exist: " + navigationPoint);
            LogTools.info("转向点不存在: " + navigationPoint);
            return;
        }else{
            LogTools.info("Target point: " + navigationPoint);
            LogTools.info("转向点: " + navigationPoint);
        }
        RobotApi.getInstance().resumeSpecialPlaceTheta(0,navigationPoint, new CommandListener() {
            @Override
            public void onResult(int result, String message, String extraData) {
                super.onResult(result, message, extraData);
                LogTools.info("resumeSpecialPlaceTheta result: " + result + " message: "+  message);
            }

            @Override
            public void onStatusUpdate(int status, String data, String extraData) {
                super.onStatusUpdate(status, data, extraData);
                LogTools.info("onStatusUpdate result: " + status + " message: "+  data);
            }

            @Override
            public void onError(int errorCode, String errorString, String extraData) throws RemoteException {
                super.onError(errorCode, errorString, extraData);
                LogTools.info("onError result: " + errorCode + " message: "+  errorString);
            }
        });
    }

    private ActionListener mNavigationListener = new ActionListener() {

        @Override
        public void onResult(int status, String response) throws RemoteException {

            switch (status) {
                case Definition.RESULT_OK:
                    if ("true".equals(response)) {
                        LogTools.info("startNavigation result: " + status + "(Navigation success)" + " message: " + response);
                        LogTools.info("startNavigation result: " + status + "(导航成功)" + " message: " + response);

                    } else {
                        LogTools.info("startNavigation result: " + status +"(Navigation failed)"+ " message: "+  response);
                        LogTools.info("startNavigation result: " + status +"(导航失败)"+ " message: "+  response);
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onError(int errorCode, String errorString) throws RemoteException {
            switch (errorCode) {
                case Definition.ERROR_NOT_ESTIMATE:
                    LogTools.info("onError result: " + errorCode +"(not estimate)"+ " message: "+  errorString);
                    LogTools.info("onError result: " + errorCode +"(当前未定位)"+ " message: "+  errorString);
                    break;
                case Definition.ERROR_IN_DESTINATION:
                    LogTools.info("onError result: " + errorCode +"(in destination, no action)"+ " message: "+  errorString);
                    LogTools.info("onError result: " + errorCode +"(当前机器人已经在目的地范围内)"+ " message: "+  errorString);
                    break;
                case Definition.ERROR_DESTINATION_NOT_EXIST:
                    LogTools.info("onError result: " + errorCode +"(destination not exist)"+ " message: "+  errorString);
                    LogTools.info("onError result: " + errorCode +"(导航目的地不存在)"+ " message: "+  errorString);
                    break;
                case Definition.ERROR_DESTINATION_CAN_NOT_ARRAIVE:
                    LogTools.info("onError result: " + errorCode +"(avoid timeout, can not arrive)"+ " message: "+  errorString);
                    LogTools.info("onError result: " + errorCode +"(避障超时，目的地不能到达，超时时间通过参数设置)"+ " message: "+  errorString);
                    break;
                case Definition.ACTION_RESPONSE_ALREADY_RUN:
                    LogTools.info("onError result: " + errorCode +"(already started, please stop first)"+ " message: "+  errorString);
                    LogTools.info("onError result: " + errorCode +"(当前接口已经调用，请先停止，才能再次调用)"+ " message: "+  errorString);
                    break;
                case Definition.ACTION_RESPONSE_REQUEST_RES_ERROR:
                    LogTools.info("onError result: " + errorCode +"(wheels are busy for other actions, please stop first)"+ " message: "+  errorString);
                    LogTools.info("onError result: " + errorCode +"(已经有需要控制底盘的接口调用，请先停止，才能继续调用)"+ " message: "+  errorString);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStatusUpdate(int status, String data) throws RemoteException {
            switch (status) {
                case Definition.STATUS_NAVI_AVOID:
                    LogTools.info("onStatusUpdate result: " + status +"(can not avoid obstacles)"+ " message: "+  data);
                    LogTools.info("onStatusUpdate result: " + status +"(当前路线已经被障碍物堵死)"+ " message: "+  data);
                    break;
                case Definition.STATUS_NAVI_AVOID_END:
                    LogTools.info("onStatusUpdate result: " + status +"(Obstacle removed)"+ " message: "+  data);
                    LogTools.info("onStatusUpdate result: " + status +"(障碍物已移除)"+ " message: "+  data);
                    break;
                default:
                    break;
            }
        }
    };

    public static Fragment newInstance() {
        return new NavigationFragment();
    }

    @Override
    public void onStop() {
        super.onStop();
        SpeechCallback.setNluResultListener(null);
        ModuleCallback.setSpeechRequestListener(null);
    }
}