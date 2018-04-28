package com.example.priyankadesai.surrounding_scanner;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CameraActivity extends Activity {

    public static final int MEDIA_TYPE_IMAGE = 1;

    private static final String TAG = CameraActivity.class.getName();
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private final static String API_KEY = "XXXX";
    private OkHttpClient mOkHttpClient;
    private Camera mCamera;
    private Camera.PictureCallback mPictureCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Create an instance of Camera
        mCamera = getCameraInstance();
        mOkHttpClient = new OkHttpClient();

        // Create our Preview view and set it as the content of our activity.
        CameraPreview cameraPreview = new CameraPreview(this, mCamera);
        FrameLayout frameLayoutPreview = findViewById(R.id.camera_preview);
        frameLayoutPreview.addView(cameraPreview);

        mPictureCallback = new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
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

        findViewById(R.id.button_capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get an image from the camera
                mCamera.takePicture(null, null, mPictureCallback);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release the camera immediately on pause event
        releaseCamera();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    public Camera getCameraInstance() {
        Camera camera = null;
        try {
            // attempt to get a Camera instance
            camera = Camera.open();
        } catch (Exception e) {
            Log.e(TAG, "Camera.open() failed", e);
        }
        // returns null if camera is unavailable
        return camera;
    }

    /**
     * Create a File for saving an image
     */
    private File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                getString(R.string.app_name));
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        if (type == MEDIA_TYPE_IMAGE) {
            return new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        }
        return null;
    }

    private void sendImageToCloud(String fileUri) {
        //createServiceAccount();
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
            jsonObjectFeature.put("maxResults", "1");
            jsonArrayFeatures.put(jsonObjectFeature);

            jsonObjectRequest.put("image", jsonObjectImage);
            jsonObjectRequest.put("features", jsonArrayFeatures);

            jsonArrayRequests.put(jsonObjectRequest);

            jsonObjectBody.put("requests", jsonArrayRequests);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Request body: " + jsonObjectBody.toString());
        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + API_KEY;
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, jsonObjectBody.toString());
        final Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "API call failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d(TAG, "Response code: " + response.code());
                Log.d(TAG, "Response: " + response.body().string());
            }
        });
    }
}
