package com.ainirobot.robotos.fragment;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ainirobot.robotos.BuildConfig;
import com.ainirobot.robotos.R;
import com.ainirobot.robotos.application.ModuleCallback;
import com.ainirobot.robotos.application.SpeechCallback;
import com.ainirobot.robotos.bailian.DashScopeStreamChat;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class VoiceQaFragment extends BaseFragment {

    private static final int REQ_RECORD_AUDIO = 0x7001;
    /** 识别结果流式更新停止后，再等待该时长（无新结果）才将整句提交给大模型，避免中间态多次计费。 */
    private static final long ASR_COMMIT_DELAY_MS = 2000L;

    private TextView tvVoiceQa;
    private ScrollView svVoiceQa;
    private Button btnVoiceQa;
    private TextToSpeech tts;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final DashScopeStreamChat dashClient = new DashScopeStreamChat();

    private volatile boolean awaitingAnswer;
    private boolean voiceInputEnabled;
    private String pendingAsrText = "";
    private Runnable commitAsrRunnable;

    @Override
    public View onCreateView(Context context) {
        View root = mInflater.inflate(R.layout.fragment_voice_qa_layout, null, false);
        bindViews(root);
        showBackView();
        hideResultView();
        return root;
    }

    @Override
    public void onDestroyView() {
        cancelPendingAsrCommit();
        dashClient.cancel();
        ioPool.shutdownNow();
        unregisterSpeechListeners();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceQuestionFlow();
            } else {
                Toast.makeText(mActivity, R.string.voice_qa_mic_denied, Toast.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void bindViews(View root) {
        svVoiceQa = root.findViewById(R.id.sv_voice_qa);
        tvVoiceQa = root.findViewById(R.id.tv_voice_qa);
        btnVoiceQa = root.findViewById(R.id.btn_voice_qa);
        btnVoiceQa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onVoiceQaClicked();
            }
        });
        updateVoiceQaButton();
    }

    private void onVoiceQaClicked() {
        if (BuildConfig.DASHSCOPE_API_KEY == null || BuildConfig.DASHSCOPE_API_KEY.trim().isEmpty()) {
            Toast.makeText(mActivity, R.string.voice_qa_no_key, Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
                return;
            }
        }
        voiceInputEnabled = !voiceInputEnabled;
        if (voiceInputEnabled) {
            registerSpeechListeners();
            appendLine(getString(R.string.voice_qa_listening));
        } else {
            cancelPendingAsrCommit();
            stripInterimLineFromDisplay();
            unregisterSpeechListeners();
            appendLine(getString(R.string.voice_qa_stopped));
        }
        updateVoiceQaButton();
    }

    private void startVoiceQuestionFlow() {
        if (!voiceInputEnabled) {
            voiceInputEnabled = true;
            registerSpeechListeners();
            appendLine(getString(R.string.voice_qa_listening));
            updateVoiceQaButton();
        }
        ensureTts();
    }

    private void ensureTts() {
        if (tts != null) {
            return;
        }
        tts = new TextToSpeech(mActivity, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(Locale.CHINESE);
                }
            }
        });
    }

    private void registerSpeechListeners() {
        SpeechCallback.setNluResultListener(new SpeechCallback.NluResultListener() {
            @Override
            public void onNluResult(final String rawResult) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!voiceInputEnabled || TextUtils.isEmpty(rawResult)) {
                            return;
                        }
                        handleRecognizedPayload(rawResult);
                    }
                });
            }

            @Override
            public void onAsrResult(final String asrResult) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!voiceInputEnabled || TextUtils.isEmpty(asrResult)) {
                            return;
                        }
                        handleRecognizedPayload(asrResult);
                    }
                });
            }
        });

        ModuleCallback.setSpeechRequestListener(new ModuleCallback.SpeechRequestListener() {
            @Override
            public void onSpeechRequest(int reqId, String reqType, String reqText, final String reqParam) {
                final String payload = !TextUtils.isEmpty(reqParam) ? reqParam : reqText;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!voiceInputEnabled || TextUtils.isEmpty(payload)) {
                            return;
                        }
                        handleRecognizedPayload(payload);
                    }
                });
            }
        });
    }

    private void unregisterSpeechListeners() {
        SpeechCallback.setNluResultListener(null);
        ModuleCallback.setSpeechRequestListener(null);
    }

    private void handleRecognizedPayload(String payload) {
        String recognizedText = extractUserText(payload);
        if (TextUtils.isEmpty(recognizedText)) {
            return;
        }
        if (awaitingAnswer) {
            appendLine(getString(R.string.voice_qa_wait_answer));
            return;
        }
        pendingAsrText = recognizedText;
        updateInterimRecognitionDisplay(recognizedText);
        if (commitAsrRunnable != null) {
            mainHandler.removeCallbacks(commitAsrRunnable);
        }
        commitAsrRunnable = new Runnable() {
            @Override
            public void run() {
                commitAsrRunnable = null;
                if (!voiceInputEnabled || awaitingAnswer) {
                    return;
                }
                String toSubmit = pendingAsrText;
                if (TextUtils.isEmpty(toSubmit)) {
                    return;
                }
                stripInterimLineFromDisplay();
                appendLine("您：" + toSubmit);
                requestDashScopeAnswer(toSubmit);
            }
        };
        mainHandler.postDelayed(commitAsrRunnable, ASR_COMMIT_DELAY_MS);
    }

    private void cancelPendingAsrCommit() {
        if (commitAsrRunnable != null) {
            mainHandler.removeCallbacks(commitAsrRunnable);
            commitAsrRunnable = null;
        }
    }

    private Pattern interimLinePattern() {
        String prefix = getString(R.string.voice_qa_interim_prefix);
        return Pattern.compile("(?:^|\\n)" + Pattern.quote(prefix) + ".*$", Pattern.DOTALL);
    }

    private String displayBaseWithoutInterimLine() {
        if (tvVoiceQa == null) {
            return "";
        }
        CharSequence cur = tvVoiceQa.getText();
        if (cur == null || cur.length() == 0) {
            return "";
        }
        String s = cur.toString();
        if (getString(R.string.voice_qa_hint).contentEquals(s)) {
            return "";
        }
        Matcher m = interimLinePattern().matcher(s);
        if (m.find()) {
            return s.substring(0, m.start());
        }
        return s;
    }

    private void updateInterimRecognitionDisplay(String text) {
        if (tvVoiceQa == null) {
            return;
        }
        CharSequence cur = tvVoiceQa.getText();
        if (cur != null && getString(R.string.voice_qa_hint).contentEquals(cur)) {
            tvVoiceQa.setText(getString(R.string.voice_qa_interim_prefix) + text);
            scrollVoiceQaToBottom();
            return;
        }
        String base = displayBaseWithoutInterimLine();
        String prefix = getString(R.string.voice_qa_interim_prefix);
        String addition = base.isEmpty() ? prefix + text : "\n" + prefix + text;
        tvVoiceQa.setText(base + addition);
        scrollVoiceQaToBottom();
    }

    private void stripInterimLineFromDisplay() {
        if (tvVoiceQa == null) {
            return;
        }
        String base = displayBaseWithoutInterimLine();
        tvVoiceQa.setText(base);
        scrollVoiceQaToBottom();
    }

    private String extractUserText(String payload) {
        if (TextUtils.isEmpty(payload)) {
            return "";
        }
        String text = payload.trim();
        if (!text.startsWith("{")) {
            return text;
        }
        try {
            JSONObject json = new JSONObject(text);
            String userText = json.optString("userText", "").trim();
            if (!TextUtils.isEmpty(userText)) {
                return userText;
            }
            String asrText = json.optString("asrResult", "").trim();
            if (!TextUtils.isEmpty(asrText)) {
                return asrText;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void updateVoiceQaButton() {
        if (btnVoiceQa != null) {
            btnVoiceQa.setText(voiceInputEnabled
                    ? R.string.voice_qa_stop
                    : R.string.voice_qa_start);
        }
    }

    private void requestDashScopeAnswer(final String userText) {
        awaitingAnswer = true;
        appendLine("百炼：");
        ioPool.execute(new Runnable() {
            @Override
            public void run() {
                dashClient.streamChat(
                        BuildConfig.DASHSCOPE_API_KEY,
                        BuildConfig.DASHSCOPE_MODEL,
                        userText,
                        new DashScopeStreamChat.StreamListener() {
                            @Override
                            public void onDelta(final String text) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        appendToLastAssistantLine(text);
                                    }
                                });
                            }

                            @Override
                            public void onComplete(final String fullText) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        awaitingAnswer = false;
                                        if (tts != null && fullText != null && fullText.length() > 0) {
                                            speakAnswer(fullText);
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onError(final String message) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        awaitingAnswer = false;
                                        appendLine(getString(R.string.voice_qa_network_error) + ": " + message);
                                    }
                                });
                            }
                        });
            }
        });
    }

    private void appendLine(String line) {
        if (tvVoiceQa == null) {
            return;
        }
        CharSequence cur = tvVoiceQa.getText();
        if (cur == null || cur.length() == 0
                || getString(R.string.voice_qa_hint).contentEquals(cur)) {
            tvVoiceQa.setText(line);
        } else {
            tvVoiceQa.append("\n");
            tvVoiceQa.append(line);
        }
        scrollVoiceQaToBottom();
    }

    private void appendToLastAssistantLine(String delta) {
        if (tvVoiceQa != null) {
            tvVoiceQa.append(delta);
            scrollVoiceQaToBottom();
        }
    }

    private void scrollVoiceQaToBottom() {
        if (svVoiceQa == null) {
            return;
        }
        svVoiceQa.post(new Runnable() {
            @Override
            public void run() {
                svVoiceQa.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void speakAnswer(String fullText) {
        if (tts == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "bailian");
        } else {
            tts.speak(fullText, TextToSpeech.QUEUE_FLUSH, new HashMap<String, String>());
        }
    }

    public static Fragment newInstance() {
        return new VoiceQaFragment();
    }
}
