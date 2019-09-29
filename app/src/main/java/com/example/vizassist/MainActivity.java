package com.example.vizassist;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.example.vizassist.imagepipeline.ImageActions;
import com.example.vizassist.utilities.HttpUtilities;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;




public class MainActivity extends AppCompatActivity {

    //private static final String UPLOAD_HTTP_URL = "http://173.255.117.247:8080/vizassist/annotate";
    private static final String UPLOAD_HTTP_URL = "http://34.68.186.175:8080/vizassist/annotate";

    private static final int IMAGE_CAPTURE_CODE = 1;
    private static final int SELECT_IMAGE_CODE = 2;


    private static final int CAMERA_PERMISSION_REQUEST = 1001;

    private MainActivityUIController mainActivityUIController;

    //declared myself
    private Activity activity;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PRDownloader.initialize(getApplicationContext());
        // Enabling database for resume support even after the application is killed.
        PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setDatabaseEnabled(true)
                .build();
        PRDownloader.initialize(getApplicationContext(),config);

        //Setting timeout globally for the download network request.
        PRDownloaderConfig config1 = PRDownloaderConfig.newBuilder()
                .setReadTimeout(30_000)
                .setConnectTimeout(30_000)
                .build();
        int temp = Integer.valueOf("1130");
        setContentView(R.layout.activity_main);
        mainActivityUIController = new MainActivityUIController(this);
//        log.e(tag "MainActivity", msg "We created");
    }

    @Override
    public void onResume() {
        super.onResume();
        mainActivityUIController.resume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_capture:
                mainActivityUIController.updateResultView(getString(R.string.result_placeholder));
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    mainActivityUIController.askForPermission(
                            Manifest.permission.CAMERA, CAMERA_PERMISSION_REQUEST);
                } else {
                    ImageActions.startCameraActivity(this, IMAGE_CAPTURE_CODE);
                }
                break;
            case R.id.action_gallery:
                mainActivityUIController.updateResultView(getString(R.string.result_placeholder));
                ImageActions.startGalleryActivity(this, SELECT_IMAGE_CODE);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case CAMERA_PERMISSION_REQUEST:
                    ImageActions.startCameraActivity(this, IMAGE_CAPTURE_CODE);
                    break;
                default:
                    break;
            }
        } else {
            mainActivityUIController.showErrorDialogWithMessage(R.string.permission_denied_message);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Bitmap bitmap = null;
            if (requestCode == IMAGE_CAPTURE_CODE) {
                bitmap = (Bitmap) data.getExtras().get("data");
                mainActivityUIController.updateImageViewWithBitmap(bitmap);
            } else if (requestCode == SELECT_IMAGE_CODE) {
                Uri selectedImage = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(),
                            selectedImage);
                    mainActivityUIController.updateImageViewWithBitmap(bitmap);
                } catch (IOException e) {
                    mainActivityUIController.showErrorDialogWithMessage(
                            R.string.reading_error_message);
                }
            }

            if (bitmap != null) {
                final Bitmap bitmapToUpload = bitmap;
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        uploadImage(bitmapToUpload);
                    }
                });
                thread.start();//一次性thread 跑完就没了，系统自动清除
                //upload image 需要时间，如果在主线程做，会一直卡在哪儿，主线程需处理完这个再处理别的。
            }
        }
    }

    private void uploadImage(Bitmap bitmap) {
        try {
            HttpURLConnection conn = HttpUtilities.makeHttpPostConnectionToUploadImage(bitmap,
                    UPLOAD_HTTP_URL);
            conn.connect();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) { //return 200 else 404
                mainActivityUIController.updateResultView(HttpUtilities.parseOCRResponse(conn));
            } else {
                mainActivityUIController.showInternetError();
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            mainActivityUIController.showInternetError();
        } catch (IOException e) {
            e.printStackTrace();
            mainActivityUIController.showInternetError();
        } catch (JSONException e) {
            e.printStackTrace();
            mainActivityUIController.showInternetError();
        }
    }

//    public void showInternetError(){
//        private final Handler mainThreadHandler;
//        this.mainThreadHandler = new Handler(Looper.getMainLooper());
//        mainThreadHandler.post(new Runnable(){
//            @Override
//            public void run(){
//                Toast.makeText(activity,R.string.internet_error_message,Toast.LENGTH_SHORT).show();
//            }
//
//        });
//    }

    public void showErrorDialogWithMessage(int messageStringID) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.error_dialog_title);
        builder.setMessage(messageStringID);
        builder.setPositiveButton(R.string.error_dialog_dismiss_button,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });
        builder.show();
    }

/*
* @param bitmap image to be sent to server
* @param urlString URL address of OCR server
* @return {@Link HttpURLConnection} to be used to connect to server
* @throws IOException
* */

  public static HttpURLConnection makeHttpPostConnectionToUploadImage(Bitmap bitmap,String urlString) throws IOException{
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection","keep-Alive");

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90,bos);
      byte[] data = bos.toByteArray();
      ByteArrayEntity byteArrayEntity = new ByteArrayEntity(data, ContentType.IMAGE_JPEG);

      conn.addRequestProperty("Content-length",byteArrayEntity.getContentLength()+"");
      conn.addRequestProperty(byteArrayEntity.getContentType().getName(),
              byteArrayEntity.getContentType().getValue());

      OutputStream os = conn.getOutputStream();
      byteArrayEntity.writeTo(conn.getOutputStream());
      os.close();
      return conn;
  }

    public static String parseOCRResponse(HttpURLConnection httpURLConnection) throws JSONException,
            IOException{
        JSONObject resultObject = new JSONObject(readStream(httpURLConnection.getInputStream()));
        String result = resultObject.getString("text");
        return result;
    }

    private static String readStream(InputStream in){
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try{
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while((line = reader.readLine())!=null){
                builder.append(line);
            }
        }catch(IOException e){
            e.printStackTrace();
        }finally {
            if(reader !=null){
                try{
                    reader.close();
                } catch(IOException e){
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }


}

