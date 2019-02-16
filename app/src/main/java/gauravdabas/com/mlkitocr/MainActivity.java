package gauravdabas.com.mlkitocr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    final static int TAKE_PICTURE = 1000;
    ImageView imageView;
    TextView detectedText;
    Button takePhotoButton;
    Button detectButton;

    File outputFile;
    Uri outputUri;

    Bitmap capturedImage;
    FirebaseVisionImage visionImage;

    /**
     * OnCreate will be the first thing android will call during your activity launch
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Getting reference to layout
        imageView = findViewById(R.id.imageView);
        detectedText = findViewById(R.id.detectedText);
        takePhotoButton = findViewById(R.id.cameraButton);
        detectButton = findViewById(R.id.detectButton);

        //Create an output file
        outputFile = new File(
                this.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "test.jpg");

        //Specify a target URI in which to store the image
        outputUri = FileProvider.getUriForFile(getApplicationContext(),
                BuildConfig.APPLICATION_ID + ".provider", outputFile);

        //Generate the intent
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);

        //Setup click listener for take photo button
        takePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(intent, TAKE_PICTURE);
            }
        });

        //Setup click listener for detect button
        detectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                visionImage = FirebaseVisionImage.fromBitmap(capturedImage);
                FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance().getOnDeviceTextRecognizer();

                //Send image to text recognizer for detecting text
                textRecognizer.processImage(visionImage)
                        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                processTextRecognitionResult(firebaseVisionText);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this, "Something went wrong", Toast.LENGTH_SHORT).show();
                            }
                        });

            }
        });
    }

    /** This method will be called after image capture by Camera */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == TAKE_PICTURE) {
            //Check if the result includes a thumbnail Bitmap
            if (data != null) {
                if (data.hasExtra("data")) {
                    Bitmap thumbnail = data.getParcelableExtra("data");
                    imageView.setImageBitmap(thumbnail);
                } else {
                    //If there is no thumbnail image data, then the image is stored in target output URI
                    setupBitmapImage();
                    detectButton.setEnabled(true);
                }
            }
        }
    }

    /** Extract and display text recognized by Firebase Vision
     *  Detected text is in multiple layer -> Full text, lines or individual text element */
    private void processTextRecognitionResult(FirebaseVisionText text) {
        StringBuilder fullText = new StringBuilder();
        List<FirebaseVisionText.TextBlock> blocks = text.getTextBlocks();
        if (blocks.size() == 0) {
            Log.d("TAG", "No text found");
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    fullText.append(elements.get(k).getText());
                    fullText.append(" ");
                }
            }
        }

        //Set text to display
        detectedText.setText(fullText.toString());
    }

    /** Create Bitmap image from the output file */
    public void setupBitmapImage() {
        //Resize the full image to fit in out image view
        int width = imageView.getWidth();
        int height = imageView.getHeight();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(outputFile.getPath(), options);

        int imageWidth = options.outWidth;
        int imageHeight = options.outHeight;

        //Determine how much to scale down the image
        int scaleFactor = Math.min(imageWidth / width, imageHeight / height);

        options.inJustDecodeBounds = false;
        options.inSampleSize = scaleFactor;

        // Decode the image file into a Bitmap sized to fill the View
        Bitmap bitmap = BitmapFactory.decodeFile(outputFile.getPath(), options);

        //Rotate imageView for Portrait mode
        Matrix matrix = new Matrix();
        matrix.postRotate(90);

        capturedImage = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        imageView.setImageBitmap(capturedImage);
    }
}
