package com.guido.styletransfer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String MODEL_FILE = "file:///android_asset/style_graph_frozen.pb";
    private static final String INPUT_NODE = "X_inputs";
    private static final String OUTPUT_NODE = "output";
    private int[] intValues;
    private float[] floatValues;
    private int desiredSize = 256;

    private ImageView ivPhoto;

    private String mFilePath;
    private FileInputStream is = null;
    private static final int CODE = 1;

    private TensorFlowInferenceInterface inferenceInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFilePath = Environment.getExternalStorageDirectory().toString();
        mFilePath = mFilePath + File.separator + "Download" + File.separator + "photo.png";

        ivPhoto = (ImageView) findViewById(R.id.ivPhoto);

        Button onCamera = (Button) findViewById(R.id.onCamera);
        onCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Uri uri = Uri.fromFile(new File(mFilePath));
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                startActivityForResult(intent, CODE);
            }
        });

        initTensorFlowAndLoadModel();
    }

    private void initTensorFlowAndLoadModel() {
        intValues = new int[desiredSize * desiredSize];
        floatValues = new float[desiredSize * desiredSize * 3];
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
        Bitmap scaledBitmap = scaleBitmap(bitmap, desiredSize, desiredSize);
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());

        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = ((val >> 16) & 0xFF) * 1.0f;
            floatValues[i * 3 + 1] = ((val >> 8) & 0xFF) * 1.0f;
            floatValues[i * 3 + 2] = (val & 0xFF) * 1.0f;
        }

        // Copy the input data into TensorFlow.
        inferenceInterface.feed(INPUT_NODE, floatValues, 1, desiredSize, desiredSize, 3);
        inferenceInterface.run(new String[]{OUTPUT_NODE});
        inferenceInterface.fetch(OUTPUT_NODE, floatValues);

        for (int i = 0; i < intValues.length; ++i) {
            intValues[i] =
                    0xFF000000
                            | (((int) (floatValues[i * 3])) << 16)
                            | (((int) (floatValues[i * 3 + 1])) << 8)
                            | ((int) (floatValues[i * 3 + 2]));
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
                    is = new FileInputStream(mFilePath);
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
