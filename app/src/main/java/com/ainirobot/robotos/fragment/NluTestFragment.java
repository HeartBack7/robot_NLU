package com.ainirobot.robotos.fragment;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.ainirobot.coreservice.client.speech.SkillApi;
import com.ainirobot.robotos.LogTools;
import com.ainirobot.robotos.R;
import com.ainirobot.robotos.application.ModuleCallback;
import com.ainirobot.robotos.application.RobotOSApplication;
import com.ainirobot.robotos.application.SpeechCallback;

import org.json.JSONObject;

public class NluTestFragment extends BaseFragment {

    private EditText mEtInput;
    private Button mBtnQuery;
    private Button mBtnClear;
    private TextView mTvDomain;
    private TextView mTvIntent;
    private TextView mTvAnswer;
    private TextView mTvRawResult;
    private TextView mTvVoiceStatus;
    private SkillApi mSkillApi;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(Context context) {
        mSkillApi = RobotOSApplication.getInstance().getSkillApi();
        View root = mInflater.inflate(R.layout.fragment_nlu_test_layout, null, false);
        bindViews(root);
        registerListeners();
        return root;
    }

    private void bindViews(View root) {
        mEtInput = root.findViewById(R.id.et_nlu_input);
        mBtnQuery = root.findViewById(R.id.btn_nlu_query);
        mBtnClear = root.findViewById(R.id.btn_nlu_clear);
        mTvDomain = root.findViewById(R.id.tv_domain);
        mTvIntent = root.findViewById(R.id.tv_intent);
        mTvAnswer = root.findViewById(R.id.tv_answer);
        mTvRawResult = root.findViewById(R.id.tv_raw_result);
        mTvVoiceStatus = root.findViewById(R.id.tv_voice_status);

        mBtnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mEtInput.getText().toString().trim();
                if (TextUtils.isEmpty(text)) {
                    return;
                }
                hideKeyboard();
                mEtInput.clearFocus();
                queryByText(text);
            }
        });

        mBtnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearResults();
            }
        });

        mEtInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyboard();
                }
            }
        });
    }

    private void registerListeners() {
        SpeechCallback.setNluResultListener(new SpeechCallback.NluResultListener() {
            @Override
            public void onNluResult(final String rawResult) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        parseAndDisplayResult(rawResult);
                    }
                });
            }

            @Override
            public void onAsrResult(final String asrResult) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvVoiceStatus != null) {
                            mTvVoiceStatus.setText("ASR: " + asrResult);
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
                        if (mTvVoiceStatus != null) {
                            mTvVoiceStatus.setText("语音识别: " + reqText);
                        }
                        if (!TextUtils.isEmpty(reqParam)) {
                            parseAndDisplayResult(reqParam);
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
            mTvVoiceStatus.setText("查询中: " + text);
            LogTools.info("NluTest queryByText: " + text);
            mSkillApi.queryByText(text);
        } else {
            mTvVoiceStatus.setText("SkillApi 未连接");
            LogTools.info("NluTest: SkillApi is null");
        }
    }

    private void parseAndDisplayResult(String rawResult) {
        if (TextUtils.isEmpty(rawResult)) {
            return;
        }

        mTvRawResult.setText(rawResult);

        try {
            JSONObject json = new JSONObject(rawResult);

            String domain = json.optString("englishDomain", "--");
            String intent = json.optString("englishIntent", "--");
            String answer = json.optString("answerText", "--");

            mTvDomain.setText(domain);
            mTvIntent.setText(intent);
            mTvAnswer.setText(answer);
        } catch (Exception e) {
            mTvDomain.setText("--");
            mTvIntent.setText("--");
            mTvAnswer.setText(rawResult);
            LogTools.info("NluTest parse error: " + e.getMessage());
        }
    }

    private void clearResults() {
        mTvDomain.setText("--");
        mTvIntent.setText("--");
        mTvAnswer.setText("--");
        mTvRawResult.setText("--");
        mTvVoiceStatus.setText(getString(R.string.nlu_voice_waiting));
        mEtInput.setText("");
    }

    private void hideKeyboard() {
        if (getActivity() != null && mEtInput != null) {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mEtInput.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        SpeechCallback.setNluResultListener(null);
        ModuleCallback.setSpeechRequestListener(null);
    }

    public static Fragment newInstance() {
        return new NluTestFragment();
    }
}
