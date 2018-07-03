package com.andrewginns.CycleGAN;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String MODEL_FILE = "file:///android_asset/2-quant-optimized_graph.pb";
    private static final String INPUT_NODE = "inputA";
    private static final String OUTPUT_NODE = "a2b_generator/output_image";
    private static final int res = 400;

    private int[] intValues;
    private float[] floatValues;

    private ImageView ivPhoto;

    File photoFile;
    private FileInputStream is = null;
    private static final int CODE = 1;

    private TensorFlowInferenceInterface inferenceInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivPhoto = findViewById(R.id.ivPhoto);

        Button onCamera = findViewById(R.id.onCamera);
        onCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    try {
                        photoFile = createImageFile();  //Create temporary image
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (photoFile != null) {
                        //Enable secure sharing of images to other apps through FileProvider
                        Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                photoFile);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(intent, 1);
                    }
                }
            }
        });

        initTensorFlowAndLoadModel();
    }

    private File createImageFile() throws IOException {
        // Name the file
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        // getExternalFilesDir()stores long term data in SDCard/Android/data/your_app/files/
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        assert storageDir != null;
        Log.d("TAH", storageDir.toString());
        //Create a temporary file with prefix >=3 characters. Default suffix is ".tmp"
        return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
    }


    private void initTensorFlowAndLoadModel() {
        intValues = new int[res * res];
        floatValues = new float[res * res * 3];
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);
    }

    private Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null) {
            return null;
        }

        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (!origin.isRecycled()) {
            origin.recycle();
        }
        return newBM;
    }

    private Bitmap stylizeImage(Bitmap bitmap) {
        Bitmap scaledBitmap = scaleBitmap(bitmap, res, res); // desiredSize
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = ((val >> 16) & 0xFF)  / (127.5f) - 1f ; //red

            floatValues[i * 3 + 1] = ((val >> 8) & 0xFF) / (127.5f) - 1f; //green

            floatValues[i * 3 + 2] = (val & 0xFF) / (127.5f) - 1f; //blue
        }

        // Copy the input data into TensorFlow.
        inferenceInterface.feed(INPUT_NODE, floatValues, 1, res, res, 3);
        // Run the inference call.
        inferenceInterface.run(new String[]{OUTPUT_NODE});
        // Copy the output Tensor back into the output array.
        inferenceInterface.fetch(OUTPUT_NODE, floatValues);

        for (int i = 0; i < intValues.length; ++i) {
            intValues[i] =
                    0xFF000000
                            | (((int) ((floatValues[i * 3] + 1f) * 127.5f)) << 16) //red
                            | (((int) ((floatValues[i * 3 + 1] + 1f) * 127.5f)) << 8) //green
                            | ((int) ((floatValues[i * 3 + 2] + 1f) * 127.5f)); //blue
        }
        scaledBitmap.setPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());
        return scaledBitmap;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == CODE) {
                try {
                    is = new FileInputStream(photoFile);
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    Bitmap bitmap2 = stylizeImage(bitmap);
                    ivPhoto.setImageBitmap(bitmap2);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}
