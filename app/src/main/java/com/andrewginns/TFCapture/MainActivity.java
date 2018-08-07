package com.andrewginns.TFCapture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.database.Cursor;
import android.widget.Toast;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private int[] intValues;
    private float[] floatValues;

    private ImageView ivPhoto;

    private FileInputStream is = null;
    private static final int CODE = 1;
    private static final int RESULT_LOAD_IMAGE = 2;

    File photoFile;

    private TensorFlowInferenceInterface inferenceInterface;

//  Model parameters
    private static final String MODEL_FILE = "file:///android_asset/optimized-graph.pb";
    private static final String INPUT_NODE = "inputA";
    private static final String OUTPUT_NODE = "a2b_generator/output_image";
    private static int res = 600;

//  Permissions control
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_W_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_R_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;

    private boolean hasPermission() {
        return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_W_STORAGE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_R_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_W_STORAGE) || shouldShowRequestPermissionRationale(PERMISSION_R_STORAGE)) {
            Toast.makeText(MainActivity.this, "Camera AND storage permission are required for this app", Toast.LENGTH_LONG).show();
        }
        requestPermissions(new String[] {PERMISSION_CAMERA, PERMISSION_W_STORAGE, PERMISSION_R_STORAGE}, PERMISSIONS_REQUEST);
    }

//  Image saving
    public static void saveBitmap(final Bitmap bitmap, final String filename) {
//        String unique = Long.toString(System.currentTimeMillis());
        final String root =
                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
        final File myDir = new File(root);
//        final String myDir = root;
        final File file = new File(myDir, filename);

        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (final Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("ADebugTag", "\nDefault Resolution: " + Integer.toString(res));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ivPhoto = findViewById(R.id.ivPhoto);

//      Buttons
        Button checkPerm = findViewById(R.id.checkPerm);
        Button onCamera = findViewById(R.id.onCamera);
        Button onGallery = findViewById(R.id.onGallery);
//        Button onFolder = findViewById(R.id.onFolder);
        Button changeRes = findViewById(R.id.changeRes);

        if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_W_STORAGE) || shouldShowRequestPermissionRationale(PERMISSION_R_STORAGE)) {
            Toast.makeText(MainActivity.this, "Camera AND storage permission are required for this app", Toast.LENGTH_LONG).show();
        }
        requestPermissions(new String[] {PERMISSION_CAMERA, PERMISSION_W_STORAGE, PERMISSION_R_STORAGE}, PERMISSIONS_REQUEST);

//      Click events
        // Check Permissions
//        checkPerm.setOnClickListener(new View.OnClickListener() {
        checkPerm.setOnClickListener(v -> {
            if(hasPermission()){
                Toast.makeText(
                        MainActivity.this,
                        "Permissions already granted",
                        Toast.LENGTH_LONG)
                        .show();
            }
            else {
                requestPermission();
            }
        });

        // Take new picture
        onCamera.setOnClickListener(v -> {
            initTensorFlowAndLoadModel();
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
        });

        // Load single image from gallery
        onGallery.setOnClickListener(arg0 -> {
            initTensorFlowAndLoadModel();
            Intent i = new Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

            startActivityForResult(i, RESULT_LOAD_IMAGE);
        });

//        // Load a set of images from folder
//        onFolder.setOnClickListener(arg0 -> {
//
//
//                initTensorFlowAndLoadModel();
//                Intent i = new Intent(
//                        Intent.ACTION_PICK,
//                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
//
//                startActivityForResult(i, RESULT_LOAD_IMAGE);
//        });

        // Set the entered resolution
        changeRes.setOnClickListener(v -> {
            Log.d("ADebugTag", "\nPrevious Resolution: " + Integer.toString(res));
            EditText value = findViewById(R.id.resEdit);
            String resolution = value.getText().toString();
            res = Integer.valueOf(resolution);
            initTensorFlowAndLoadModel();
            Log.d("ADebugTag", "\nNew Resolution: " + Integer.toString(res));
        });



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
        long currentTime = System.currentTimeMillis();
        if (requestCode == CODE && resultCode == RESULT_OK) {
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

        if (requestCode == RESULT_LOAD_IMAGE){
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            assert selectedImage != null;
            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            assert cursor != null;
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
            Bitmap bitmap2 = stylizeImage(bitmap);
            ivPhoto.setImageBitmap(bitmap2);

            saveBitmap(bitmap2, (currentTime + "Processed" +  ".png"));
            saveBitmap(BitmapFactory.decodeFile(picturePath), (currentTime + "UnProcessed" + ".png"));
            Toast.makeText(
                    MainActivity.this,
                    "Input and Output images saved to: /sdcard/tensorflow/",
                    Toast.LENGTH_LONG)
                    .show();

        }
    }

//    public class Bucket {
//
//        private String name;
//        private String firstImageContainedPath;
//
//        public Bucket(String name, String firstImageContainedPath) {
//            this.name = name;
//            this.firstImageContainedPath = firstImageContainedPath;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public String getFirstImageContainedPath() {
//            return firstImageContainedPath;
//        }
//    }
//
//    public static List<Bucket> getImageBuckets(Context mContext){
//        List<Bucket> buckets = new ArrayList<>();
//        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
//        String [] projection = {MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATA};
//
//        Cursor cursor = mContext.getContentResolver().query(uri, projection, null, null, null);
//        if(cursor != null){
//            File file;
//            while (cursor.moveToNext()){
//                String bucketPath = cursor.getString(cursor.getColumnIndex(projection[0]));
//                String firstImage = cursor.getString(cursor.getColumnIndex(projection[1]));
//                file = new File(firstImage);
//                if (file.exists() && !bucketSet.contains(bucketName)) {
//                    buckets.add(new Bucket(bucketName, firstImage));
//                }
//            }
//            cursor.close();
//        }
//        return buckets;
//    }

}