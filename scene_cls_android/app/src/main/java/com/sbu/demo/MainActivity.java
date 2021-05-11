package com.sbu.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentUris;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.sbu.scenecls.R;
import com.scls.library.SceneRecognition;


public class MainActivity extends AppCompatActivity {

    private String cachePath;
    private String modelPath;
    private String paramPath;
    private String selectPicturePath = "";
    private List<String> resultLabel = new ArrayList<>();

    private ImageView iv_pic;

    private ImageView iv_video;

    private String videoPath;

    private LoadingDialog loadingDialog;

    private DealAsyncTask dealAsyncTask;

    private String OUTPUT_DIR = "/storage/emulated/0/T2/";

    private TextView result_text;

    SceneRecognition sceneRecognition = null;

    private boolean is_img = true;

//
//    static {
//        System.loadLibrary("native-lib");
//    }
//
//
//    public native String stringFromJNI();
//    public native void sceneClsInit(String modelPath);
//    public native void sceneClsDel();
//    public native int sceneClsInference(String img_path);
//    public native int sceneClsInference1(Bitmap bitmap);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_1);

        result_text = (TextView) findViewById(R.id.result_text);
        result_text.setMovementMethod(ScrollingMovementMethod.getInstance());

        cachePath = FileStorageHelper.getDiskCacheDir(this);
        modelPath = cachePath + "/model";
        paramPath = cachePath + "/params";
        Log.d("AAAAAAAAAAAAAAAAAAA", "modelPath" + modelPath);

//        FileStorageHelper.copyFilesFromRaw(this, R.raw.mobile2_bin, "mobile2.bin", modelPath);
//        FileStorageHelper.copyFilesFromRaw(this, R.raw.mobile2_param, "mobile2.param", modelPath);


        iv_pic = findViewById(R.id.iv_pic);
        iv_video = findViewById(R.id.iv_video);

        loadingDialog = new LoadingDialog(this);

//        sceneClsInit(modelPath);
        readCacheLabelFromLocalFile();

        sceneRecognition = new SceneRecognition(this);
    }

    public void onPicSelect(View view) {
        is_img = true;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent,3);
    }

    public void onVideoSelect(View view) {
        is_img = false;
        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent,4);
    }


    public void onPicDeal(View view) {
        dealAsyncTask = new DealAsyncTask();
        dealAsyncTask.execute();
    }

    private void readCacheLabelFromLocalFile() {
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("label.txt")));
            String readLine = null;
            while ((readLine = reader.readLine()) != null) {
                resultLabel.add(readLine);
            }
            reader.close();
        } catch (Exception e) {
            Log.e("labelCache", "error " + e);
        }
    }

    MediaExtractor extractor;
    private MediaCodec decoder;
    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private static final long DEFAULT_TIMEOUT_US = 10000;
    private int outputImageFileType = 3;
    public static final int FILE_TypeI420 = 1;
    public static final int FILE_TypeNV21 = 2;
    public static final int FILE_TypeJPEG = 3;
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;
    private int frameRate = 0;
    private class DealAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            if(selectPicturePath == "" || selectPicturePath == null) return true;

            if(is_img) {
                Vector vecText = sceneRecognition.inference_img(selectPicturePath);
                int length = vecText.size();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(length < 2) {
                            result_text.setText((String)vecText.get(0));
                        } else {
                            String text = "  推理时间:" + (String)vecText.get(0) + "ms" + " 场景类别是:" + (String)vecText.get(1) +" score:"+((int)vecText.get(2)/100.0f);
                            result_text.setText(text);
                        }
                    }
                });
            } else {
                Vector vecText = sceneRecognition.inference_video(selectPicturePath,0,10000, false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(vecText.size() < 2) {
                            result_text.setText((String)vecText.get(0));
                        } else {
                            String show = "总推理时间:" + (String)vecText.get(0) + "ms \n平均每帧的推理时间:" + (String)vecText.get(1) + "ms \n总处理帧数:"+ vecText.get(2)+" 场景类别分别是:\n";
                            for(int i = 3; i < vecText.size(); ++i) {
                                //show += String.valueOf(i-3) + " : " +(String)vecText.get(i) + "\n";
                                show += (String)vecText.get(i) + "\n";
                            }
                            result_text.setText(show);
                        }
                    }
                });
            }




//            String show_text = sceneRecognition.inference_img(selectPicturePath);
//            String show_text = resultLabel.get(index);
//            String show_text = Integer.toString(index);
//            result_text.setText(show_text);
//            try {
//                FileInputStream fis = new FileInputStream(selectPicturePath);
//                Bitmap bitmap  = BitmapFactory.decodeStream(fis);
//                int index = sceneClsInference1(bitmap);
//                String show_text = resultLabel.get(index);
////            String show_text = Integer.toString(index);
//                result_text.setText(show_text);
//            } catch(IOException io) {
//
//            }






            return true;

        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            loadingDialog.dismiss();
//            if (aBoolean) {
//                Toast.makeText(MainActivity.this, "生成成功,关键图片保存到了: "+ OUTPUT_DIR, Toast.LENGTH_SHORT).show();
//                //launchModelRendererActivity(Uri.parse("file://" + objPath));
//            } else {
//                Toast.makeText(MainActivity.this, "生成失败", Toast.LENGTH_SHORT).show();
//            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
             loadingDialog.show("分类中。。。");
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

    }




    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        // 选取图片的返回值
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == 3) {
//            //
//            if (resultCode == RESULT_OK) {
//                if (data != null) {
//                    selectPicturePath = getRealPathFromUri(this, data.getData());
//                } else {
//                    Toast.makeText(this, "图片损坏，请重新选择", Toast.LENGTH_SHORT).show();
//                }
//            }
//
//        }
//    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 选取图片的返回值
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 4) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] { split[1] };

                Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

                Cursor cursor = null;
                final String column = "_data";
                final String[] projection = {column};

                cursor = this.getContentResolver().query(contentUri, projection, selection , selectionArgs,
                        null);
                cursor.moveToFirst();

                final int column_index = cursor.getColumnIndexOrThrow(column);
                selectPicturePath = cursor.getString(column_index);
                cursor.close();
            }
        } else if(requestCode == 3) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    selectPicturePath = getRealPathFromUri(this, data.getData());
                } else {
                    Toast.makeText(this, "图片损坏，请重新选择", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getRealPathFromUri(MainActivity mAct, Uri uri) {
        Log.d("tgw", "getRealPathFromUri:uri.getScheme() " + uri.getScheme());
        if (Build.VERSION.SDK_INT >= 19) { // api >= 19  如果Uri对应的图片存在, 那么返回该图片的绝对路径, 否则返回null
            String filePath = null;
            //判断该Uri是否是document封装过的DocumentsContract.isDocumentUri(mAct, uri)
            if (DocumentsContract.isDocumentUri(mAct, uri)) {
                // 如果是document类型的 uri, 则通过document id来进行处理
                String documentId = DocumentsContract.getDocumentId(uri);
                if ("com.android.providers.media.documents".equals(uri.getAuthority())) { // MediaProvider
                    // 使用':'分割用split('.')方法将字符串以"."开割形成一个字符串数组，
                    // 然后再通过索引[1]取出所得数组中的第二个元素的值。
                    String id = documentId.split(":")[1];
                    Log.d("tgw1", "getRealPathFromUri: " + documentId + "--" + id);
                    String selection = MediaStore.Images.Media._ID + "=?";
                    String[] selectionArgs = {id};
                    //如果要查询图片，地址：MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    filePath = getDataColumn(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs);
                    Log.d("tgwdocuments", "getRealPathFromUri: " + filePath + "==" + selectionArgs);
                } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) { // DownloadsProvider

                    //这个方法负责把id和contentUri连接成一个新的Uri=: content://downloads/public_downloads/documentId
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(documentId));
                    filePath = getDataColumn(contentUri, null, null);
                    Log.d("tgwcontentUri", "getRealPathFromUri: " + filePath);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {

                // 如果是 content 类型的 Uri
                // 返回文件头 uri.getScheme() 如：content  file 等等
                filePath = getDataColumn(uri, null, null);
                Log.d("tgwcontent", "getRealPathFromUri: " + filePath);

            } else if ("file".equals(uri.getScheme())) {

                // 如果是 file 类型的 Uri,直接获取图片对应的路径
                filePath = uri.getPath();
                Log.d("tgwfile", "getRealPathFromUri: " + filePath);
            }
            return filePath;

        } else { // api < 19
            return getDataColumn(uri, null, null);
        }
    }


    private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
        String path = null;

        String[] projection = new String[]{MediaStore.Images.Media.DATA};
        Cursor cursor = null;
        try {
            //uri提供内容的地址，projection查询要返回的列，
            // selection  ：查询where字句， selectionArgs ： 查询条件属性值 ，sortOrder ：结果排序规则
            cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                //getColumnIndexOrThrow 从零开始返回指定列名称，如果不存在将抛出IllegalArgumentException 异常。
                int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
                path = cursor.getString(columnIndex);
                Log.d("tgwgetDataColumn", "getDataColumn: " + path);
            }
        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
            }
        }
        //  "file://" 图片地址要加上file头
        return path;
    }

}
