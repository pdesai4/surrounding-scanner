package com.example.priyankadesai.surrounding_scanner;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CameraActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int DELAY_TILL_CAPTURE_MILLIS = 1000;
    private final static String TAG = CameraActivity.class.getName();
    private final static MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private final static String API_KEY = BuildConfig.VISION_API_KEY;
    private final static String VISION_API_URL = "https://vision.googleapis.com/v1/images:annotate?key=" + API_KEY;
    private final static double SCORE_THRESHOLD = 0.7;

    private OkHttpClient OkHttpClient;
    private Camera camera;
    private Camera.PictureCallback mPictureCallback;
    private TextToSpeech textToSpeech;
    private boolean textToSpeechInitialised;
    private TextView textViewCaption;
    private File cacheDir;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textViewCaption = findViewById(R.id.textViewCaption);

        textToSpeech = new TextToSpeech(this, this);
        textToSpeechInitialised = false;

        // Create an instance of Camera
        camera = getCameraInstance();
        OkHttpClient = new OkHttpClient();

        cacheDir = getCacheDir();

        // Create our Preview view and set it as the content of our activity.
        CameraPreview cameraPreview = new CameraPreview(this, camera);
        FrameLayout frameLayoutPreview = findViewById(R.id.camera_preview);
        frameLayoutPreview.addView(cameraPreview);

        mPictureCallback = new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                File pictureFile = getOutputMediaFile();
                if (pictureFile == null) {
                    Log.e(TAG, "Error creating media file, check storage permissions");
                    return;
                }
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(pictureFile);
                    fileOutputStream.write(data);
                    fileOutputStream.close();
                    sendImageToCloud(pictureFile.getAbsolutePath());
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "File not found", e);
                } catch (IOException e) {
                    Log.e(TAG, "Error accessing file", e);
                }
            }
        };

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && camera != null) {
                    camera.takePicture(null, null, mPictureCallback);
                }
            }
        }, DELAY_TILL_CAPTURE_MILLIS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release the camera immediately on pause event
        releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            // release the camera for other applications
            camera.release();
            camera = null;
        }
    }

    public Camera getCameraInstance() {
        Camera camera = null;
        try {
            // attempt to get a Camera instance
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();
            if (parameters.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                camera.setParameters(parameters);
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera.open() failed", e);
        }
        // returns null if camera is unavailable
        return camera;
    }

    /**
     * Create a temp File for saving an image
     */
    private File getOutputMediaFile() {
        try {
            File tempFile = File.createTempFile(UUID.randomUUID().toString(), ".jpg", cacheDir);
            tempFile.deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void sendImageToCloud(String fileUri) {
        Bitmap bitmap = BitmapFactory.decodeFile(fileUri);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArrayImage = byteArrayOutputStream.toByteArray();
        String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);

        JSONObject jsonObjectBody = new JSONObject();
        try {
            JSONArray jsonArrayRequests = new JSONArray();
            JSONObject jsonObjectRequest = new JSONObject();

            JSONObject jsonObjectImage = new JSONObject();
            jsonObjectImage.put("content", encodedImage);

            JSONArray jsonArrayFeatures = new JSONArray();
            JSONObject jsonObjectFeature = new JSONObject();
            jsonObjectFeature.put("type", "LABEL_DETECTION");
            jsonObjectFeature.put("maxResults", "10");
            jsonArrayFeatures.put(jsonObjectFeature);

            jsonObjectRequest.put("image", jsonObjectImage);
            jsonObjectRequest.put("features", jsonArrayFeatures);

            jsonArrayRequests.put(jsonObjectRequest);

            jsonObjectBody.put("requests", jsonArrayRequests);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Request body: " + jsonObjectBody.toString());
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, jsonObjectBody.toString());
        final Request request = new Request.Builder()
                .url(VISION_API_URL)
                .post(body)
                .build();
        OkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "API call failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d(TAG, "Response code: " + response.code());
                StringBuilder objectsToRead = new StringBuilder();
                if (response.body() != null) {
                    try {
                        @SuppressWarnings("ConstantConditions")
                        JSONObject jsonObjectResponse = new JSONObject(response.body().string());
                        Log.d(TAG, "Response body: " + jsonObjectResponse.toString());
                        if (jsonObjectResponse.has("responses")) {
                            JSONArray responses = jsonObjectResponse.getJSONArray("responses");
                            for (int i = 0; i < responses.length(); i++) {
                                JSONObject jsonObject = responses.getJSONObject(i);
                                if (jsonObject.has("labelAnnotations")) {
                                    JSONArray labelAnnotations = jsonObject.getJSONArray("labelAnnotations");
                                    for (int j = 0; j < labelAnnotations.length(); j++) {
                                        JSONObject labelAnnotation = labelAnnotations.getJSONObject(j);
                                        if (labelAnnotation.has("description") && labelAnnotation.has("score")) {
                                            double score = labelAnnotation.getDouble("score");
                                            if (score >= SCORE_THRESHOLD) {
                                                objectsToRead
                                                        .append(labelAnnotation.getString("description"))
                                                        .append(", ");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error parsing response json", e);
                    }
                }
                readOutLoud(objectsToRead.toString());
            }
        });
    }

    private void readOutLoud(final String text) {
        Log.d(TAG, text);
        if (textToSpeechInitialised) {
            final HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textViewCaption.setText(text);
                    textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params);
                }
            });
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language
            int result = textToSpeech.setLanguage(Locale.US);

            // Set pitch level
            // textToSpeech.setPitch(5);

            // Set speech speed rate
            // textToSpeech.setSpeechRate(2);

            // Check if initialised successfully
            textToSpeechInitialised = (result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED);

            // Listener for the event when done speaking given word(s)
            textToSpeech.setOnUtteranceProgressListener(new MyUtteranceProgressListener());
        }
    }

    class MyUtteranceProgressListener extends android.speech.tts.UtteranceProgressListener {

        @Override
        public void onStart(String utteranceId) {
        }

        @Override
        public void onDone(final String utteranceId) {
            if (camera != null) {
                camera.startPreview();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && camera != null) {
                            camera.takePicture(null, null, mPictureCallback);
                        }
                    }
                }, DELAY_TILL_CAPTURE_MILLIS);
            }
        }

        @Override
        public void onError(String utteranceId) {
        }
    }
}
