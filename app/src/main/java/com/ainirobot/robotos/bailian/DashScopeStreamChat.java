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

package com.ainirobot.robotos.bailian;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * 阿里云百炼 DashScope OpenAI 兼容模式流式对话（HTTPS）。
 * 文档：https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-by-calling-api
 */
public final class DashScopeStreamChat {

    private static final String ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    public interface StreamListener {
        void onDelta(String text);

        void onComplete(String fullText);

        void onError(String message);
    }

    private volatile HttpURLConnection connection;

    public void cancel() {
        HttpURLConnection c = connection;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 同步调用：请在后台线程执行。
     */
    public void streamChat(String apiKey, String model, String userMessage, StreamListener listener) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            listener.onError("empty_api_key");
            return;
        }
        HttpURLConnection conn = null;
        InputStream input = null;
        BufferedReader reader = null;
        StringBuilder full = new StringBuilder();
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);
            JSONArray messages = new JSONArray();
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userMessage);
            messages.put(user);
            body.put("messages", messages);

            Charset utf8 = Charset.forName("UTF-8");
            byte[] payload = body.toString().getBytes(utf8);

            URL url = new URL(ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            connection = conn;
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            input = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (input == null) {
                listener.onError("http_" + code);
                return;
            }
            reader = new BufferedReader(new InputStreamReader(input, utf8));
            if (code >= 400) {
                StringBuilder err = new StringBuilder();
                String errLine;
                while ((errLine = reader.readLine()) != null) {
                    err.append(errLine).append('\n');
                }
                String errBody = err.toString().trim();
                if (errBody.length() > 0) {
                    try {
                        JSONObject o = new JSONObject(errBody);
                        JSONObject errObj = o.optJSONObject("error");
                        if (errObj != null) {
                            listener.onError(errObj.optString("message", errBody));
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    listener.onError(errBody.length() > 240 ? errBody.substring(0, 240) + "..." : errBody);
                    return;
                }
                listener.onError("http_" + code);
                return;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                try {
                    JSONObject obj = new JSONObject(data);
                    JSONArray choices = obj.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        continue;
                    }
                    JSONObject choice0 = choices.getJSONObject(0);
                    JSONObject delta = choice0.optJSONObject("delta");
                    if (delta != null && delta.has("content")) {
                        String piece = delta.optString("content", "");
                        if (piece.length() > 0) {
                            full.append(piece);
                            listener.onDelta(piece);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            listener.onComplete(full.toString());
        } catch (Exception e) {
            listener.onError(e.getMessage() != null ? e.getMessage() : "unknown_error");
        } finally {
            connection = null;
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception ignored) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
