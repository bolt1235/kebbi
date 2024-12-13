package com.nuwarobotics.example.voice;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.nuwarobotics.example.R;
import com.nuwarobotics.service.IClientId;
import com.nuwarobotics.service.agent.NuwaRobotAPI;
import com.nuwarobotics.service.agent.RobotEventListener;
import com.nuwarobotics.service.agent.SimpleGrammarData;
import com.nuwarobotics.service.agent.VoiceEventListener;
import com.nuwarobotics.service.agent.VoiceResultJsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.api.gax.core.FixedCredentialsProvider;

import android.os.AsyncTask;

import java.io.InputStream;
import java.util.UUID;

public class CloudASRActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();
    NuwaRobotAPI mRobotAPI;
    IClientId mClientId;
    boolean mSDKinit = false;
    EditText mResult;
    Button mStartBtn;
    Button mStopBtn;

    // 添加 Dialogflow 相关变量
    private static final String PROJECT_ID = "kebbi-rwel";
    private SessionsClient sessionsClient;
    private SessionName session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voice_layout);

        // 现有的初始化代码...
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(TAG);

        mResult = (EditText) findViewById(R.id.text_result);
        mResult.setText("");
        mStartBtn = (Button) findViewById(R.id.btn_start);
        mStartBtn.setEnabled(false);
        mStopBtn = (Button) findViewById(R.id.btn_stop);
        mStopBtn.setEnabled(false);

        // 添加 Dialogflow 初始化
        initializeDialogflow();

        //Step 1 : Initial Nuwa API Object
        mClientId = new IClientId(this.getPackageName());
        mRobotAPI = new NuwaRobotAPI(this, mClientId);

        //Step 2 : Register receive Robot Event
        Log.d(TAG, "register EventListener ");
        mRobotAPI.registerRobotEventListener(robotEventListener);
    }

    // 添加 Dialogflow 初始化方法
    private void initializeDialogflow() {
        try {
            Log.d(TAG, "Starting Dialogflow initialization");

            // 1. 嘗試打開憑證文件
            Log.d(TAG, "Attempting to open credential file");
            InputStream stream = getResources().getAssets().open("kebbi-rwel-ae0023423c57.json");
            Log.d(TAG, "Credential file opened successfully");

            // 2. 載入憑證
            Log.d(TAG, "Loading credentials");
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
            Log.d(TAG, "Credentials loaded successfully: " + credentials.toString());

            // 3. 建立設定
            Log.d(TAG, "Building session settings");
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            Log.d(TAG, "Session settings built successfully");

            // 4. 創建客戶端和會話
            Log.d(TAG, "Creating sessions client");
            sessionsClient = SessionsClient.create(sessionsSettings);
            Log.d(TAG, "Sessions client created successfully");

            session = SessionName.of(PROJECT_ID, UUID.randomUUID().toString());
            Log.d(TAG, "Session created successfully: " + session.toString());

            Log.d(TAG, "Dialogflow initialization completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing Dialogflow: " + e.getMessage());
            Log.e(TAG, "Stack trace: ", e);

            // 檢查特定錯誤類型
            if (e instanceof java.io.FileNotFoundException) {
                Log.e(TAG, "Credential file not found in assets folder");
            } else if (e instanceof java.io.IOException) {
                Log.e(TAG, "Error reading credential file");
            } else if (e instanceof IllegalArgumentException) {
                Log.e(TAG, "Invalid credential format");
            }
        } finally {
            // 確認初始化狀態
            Log.d(TAG, "Dialogflow client status: " + (sessionsClient != null ? "initialized" : "failed"));
            Log.d(TAG, "Session status: " + (session != null ? "created" : "failed"));
        }
    }

    // 添加发送请求到 Dialogflow 的方法
    private void sendToDialogflow(final String text) {
        // 先检查 session 是否正确初始化
        if (session == null || sessionsClient == null) {
            Log.e(TAG, "Dialogflow not properly initialized - attempting to reinitialize");
            initializeDialogflow();

            // 如果重新初始化后仍然失败，则返回
            if (session == null || sessionsClient == null) {
                Log.e(TAG, "Failed to initialize Dialogflow - cannot send text");
                return;
            }
        }

        try {
            TextInput textInput = TextInput.newBuilder()
                    .setText(text)
                    .setLanguageCode("zh-TW")
                    .build();

            QueryInput queryInput = QueryInput.newBuilder()
                    .setText(textInput)
                    .build();

            // 使用 session.toString() 之前进行null检查
            if (session != null) {
                DetectIntentRequest request = DetectIntentRequest.newBuilder()
                        .setSession(session.toString())
                        .setQueryInput(queryInput)
                        .build();

                new AsyncTask<DetectIntentRequest, Void, DetectIntentResponse>() {
                    @Override
                    protected DetectIntentResponse doInBackground(DetectIntentRequest... requests) {
                        try {
                            return sessionsClient.detectIntent(requests[0]);
                        } catch (Exception e) {
                            Log.e(TAG, "Dialogflow request failed: " + e.getMessage(), e);
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(DetectIntentResponse response) {
                        if (response != null) {
                            String fulfillmentText = response.getQueryResult().getFulfillmentText();
                            setText(getCurrentTime() + "Dialogflow response: " + fulfillmentText, false);

                            if (!fulfillmentText.isEmpty()) {
                                mRobotAPI.startTTS(fulfillmentText);
                            }
                        } else {
                            setText(getCurrentTime() + "Failed to get response from Dialogflow", false);
                        }
                    }
                }.execute(request);
            } else {
                Log.e(TAG, "Session is null");
                setText(getCurrentTime() + "Dialogflow session is not initialized", false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sending text to Dialogflow: " + e.getMessage(), e);
            setText(getCurrentTime() + "Error: " + e.getMessage(), false);
        }
    }

    // 其他现有的代码保持不变...}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionsClient != null) {
            sessionsClient.close();
        }
        // release Nuwa Robot SDK resource
        mRobotAPI.release();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void BtnStart(View view) {
        if (!mSDKinit) {
            setText("need to do SDK init first !!!", false);
            return;
        }

        setText(getCurrentTime() + "Start Cloud ASR", false);

        // 設置噪音過濾閾值
        mRobotAPI.setListenParameter(VoiceEventListener.ListenType.RECOGNIZE, "noise_gate", "50");

        // 設置 VAD (Voice Activity Detection) 靈敏度
        mRobotAPI.setListenParameter(VoiceEventListener.ListenType.RECOGNIZE, "vad_sensitivity", "100");
        // 設置語音增益
        mRobotAPI.setListenParameter(VoiceEventListener.ListenType.RECOGNIZE, "vad_volume_gain", "2.0");


        //設置語言
        mRobotAPI.setListenParameter(VoiceEventListener.ListenType.RECOGNIZE, "language", "zh-TW");

        //設置收音超時時間(毫秒)
        mRobotAPI.setListenParameter(VoiceEventListener.ListenType.RECOGNIZE, "timeout", "10000"); // 10秒

        //設置音量靜默超時時間(毫秒)
        mRobotAPI.setListenParameter(VoiceEventListener.ListenType.RECOGNIZE, "silence_timeout", "2000"); // 2秒

        //開始收音
        mRobotAPI.startSpeech2Text(false);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartBtn.setEnabled(false);
                mStopBtn.setEnabled(true);
            }
        });
    }

    public void BtnStop(View view) {
        setText(getCurrentTime() + "Stop Localcmd", false);
        mRobotAPI.stopListen();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartBtn.setEnabled(true);
                mStopBtn.setEnabled(false);
            }
        });
    }

    private void setText(final String text, final boolean append) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResult.append(text);
                if (!append)
                    mResult.append("\n");
                mResult.setMovementMethod(ScrollingMovementMethod.getInstance());
                mResult.setSelection(mResult.getText().length(), mResult.getText().length());
            }
        });
    }

    public static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss ");
        String currentDateAndTime = sdf.format(new Date());
        return currentDateAndTime;
    }

    RobotEventListener robotEventListener = new RobotEventListener() {
        @Override
        public void onWikiServiceStart() {
            // Nuwa Robot SDK is ready now, you call call Nuwa SDK API now.
            Log.d(TAG, "onWikiServiceStart, robot ready to be control");
            //Step 3 : Start Control Robot after Service ready.
            //Register Voice Callback event
            mRobotAPI.registerVoiceEventListener(voiceEventListener);//listen callback of robot voice related event
            //Allow user start demo after service ready
            //TODO
            setText(getCurrentTime() + "onWikiServiceStart, robot ready to be control", false);
            mSDKinit = true;
            mStartBtn.setEnabled(true);
        }

        @Override
        public void onWikiServiceStop() {

        }

        @Override
        public void onWikiServiceCrash() {

        }

        @Override
        public void onWikiServiceRecovery() {

        }

        @Override
        public void onStartOfMotionPlay(String s) {

        }

        @Override
        public void onPauseOfMotionPlay(String s) {

        }

        @Override
        public void onStopOfMotionPlay(String s) {

        }

        @Override
        public void onCompleteOfMotionPlay(String s) {

        }

        @Override
        public void onPlayBackOfMotionPlay(String s) {

        }

        @Override
        public void onErrorOfMotionPlay(int i) {

        }

        @Override
        public void onPrepareMotion(boolean b, String s, float v) {

        }

        @Override
        public void onCameraOfMotionPlay(String s) {

        }

        @Override
        public void onGetCameraPose(float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7, float v8, float v9, float v10, float v11) {

        }

        @Override
        public void onTouchEvent(int i, int i1) {

        }

        @Override
        public void onPIREvent(int i) {

        }

        @Override
        public void onTap(int i) {

        }

        @Override
        public void onLongPress(int i) {

        }

        @Override
        public void onWindowSurfaceReady() {

        }

        @Override
        public void onWindowSurfaceDestroy() {

        }

        @Override
        public void onTouchEyes(int i, int i1) {

        }

        @Override
        public void onRawTouch(int i, int i1, int i2) {

        }

        @Override
        public void onFaceSpeaker(float v) {

        }

        @Override
        public void onActionEvent(int i, int i1) {

        }

        @Override
        public void onDropSensorEvent(int i) {

        }

        @Override
        public void onMotorErrorEvent(int i, int i1) {

        }
    };
    VoiceEventListener voiceEventListener = new VoiceEventListener() {
        @Override
        public void onWakeup(boolean isError, String score, float direction) {

        }

        @Override
        public void onTTSComplete(boolean isError) {
            Log.d(TAG, "TTS completed: " + !isError);
            if (!isError) {
                setText(getCurrentTime() + "TTS completed successfully", false);
            }

        }

        @Override
        public void onSpeechRecognizeComplete(boolean isError, ResultType iFlyResult, String json) {

        }

        @Override
        public void onSpeech2TextComplete(boolean isError, String json) {
            Log.d(TAG, "onSpeech2TextComplete:" + !isError + ", json:" + json);

            String result_string = VoiceResultJsonParser.parseVoiceResult(json);
            setText(getCurrentTime() + "onSpeech2TextComplete:" + !isError + ", result:" + result_string, false);

            // 如果語音辨識成功且有結果，發送到 Dialogflow 處理
            if (!isError && result_string != null && !result_string.isEmpty()) {
                Log.d(TAG, "Sending to Dialogflow: " + result_string);
                sendToDialogflow(result_string);
            } else {
                Log.e(TAG, "Speech recognition error or empty result");
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStartBtn.setEnabled(true);
                    mStopBtn.setEnabled(false);
                }
            });
        }

        @Override
        public void onMixUnderstandComplete(boolean isError, ResultType resultType, String s) {

        }

        @Override
        public void onSpeechState(ListenType listenType, SpeechState speechState) {

        }

        @Override
        public void onSpeakState(SpeakType speakType, SpeakState speakState) {

        }

        @Override
        public void onGrammarState(boolean isError, String s) {

        }

        @Override
        public void onListenVolumeChanged(ListenType listenType, int i) {

        }

        @Override
        public void onHotwordChange(HotwordState hotwordState, HotwordType hotwordType, String s) {

        }
    };
}
