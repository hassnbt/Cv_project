package com.example.cv_project;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.BiConsumer;

public class MainActivity extends AppCompatActivity {

    private ImageView selectedImage;
    private Button btnCamera, btnGallery, btnSubmit;
    private Uri photoURI;
    private File currentImageFile;
    private File secondImageFile = null; // Optional second image (can be assigned as needed)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectedImage = findViewById(R.id.selected_image);
        btnCamera = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        btnSubmit = findViewById(R.id.btn_submit);

        btnCamera.setOnClickListener(v -> dispatchTakePictureIntent());
        btnGallery.setOnClickListener(v -> pickImageFromGallery());
        btnSubmit.setOnClickListener(v -> {
            if (currentImageFile != null) {
                uploadImage(currentImageFile, "45.0", "y", secondImageFile, "5.0");
            } else {
                Toast.makeText(this, "Please select or capture an image first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating file.", Toast.LENGTH_SHORT).show();
                return;
            }

            currentImageFile = photoFile;
            photoURI = FileProvider.getUriForFile(this,
                    "com.example.cv_project.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            cameraLauncher.launch(takePictureIntent);
        }
    }

    private void pickImageFromGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(galleryIntent);
    }

    ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    selectedImage.setImageURI(photoURI);
                }
            });

    ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    selectedImage.setImageURI(selectedUri);
                    String path = FileUtils.getPath(this, selectedUri);
                    if (path != null) {
                        currentImageFile = new File(path);
                    } else {
                        Toast.makeText(this, "Unable to load image file", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    public void uploadImage(File imageFile, String bearing, String triangulate, File secondImageFile, String baseline) {
        Thread thread = new Thread(() -> {
            try {
                String boundary = Long.toString(System.currentTimeMillis());
                String LINE_FEED = "\r\n";

                URL url = new URL("http://192.168.1.7:5000/process-image"); // Update as needed
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setUseCaches(false);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream outputStream = conn.getOutputStream();
                DataOutputStream writer = new DataOutputStream(outputStream);

                BiConsumer<String, String> writeFormField = (name, value) -> {
                    try {
                        writer.writeBytes("--" + boundary + LINE_FEED);
                        writer.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + LINE_FEED);
                        writer.writeBytes("Content-Type: text/plain; charset=UTF-8" + LINE_FEED);
                        writer.writeBytes(LINE_FEED);
                        writer.writeBytes(value + LINE_FEED);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                };

                // Add form fields
                if (bearing != null) writeFormField.accept("bearing", bearing);
                if (triangulate != null) writeFormField.accept("triangulate", triangulate);
                if (baseline != null) writeFormField.accept("baseline", baseline);

                // Add first image
                writer.writeBytes("--" + boundary + LINE_FEED);
                writer.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + imageFile.getName() + "\"" + LINE_FEED);
                writer.writeBytes("Content-Type: image/jpeg" + LINE_FEED);
                writer.writeBytes(LINE_FEED);

                FileInputStream inputStream = new FileInputStream(imageFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    writer.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                writer.writeBytes(LINE_FEED);

                // Optional second image
                if (secondImageFile != null && secondImageFile.exists()) {
                    writer.writeBytes("--" + boundary + LINE_FEED);
                    writer.writeBytes("Content-Disposition: form-data; name=\"second_image\"; filename=\"" + secondImageFile.getName() + "\"" + LINE_FEED);
                    writer.writeBytes("Content-Type: image/jpeg" + LINE_FEED);
                    writer.writeBytes(LINE_FEED);

                    FileInputStream secondStream = new FileInputStream(secondImageFile);
                    while ((bytesRead = secondStream.read(buffer)) != -1) {
                        writer.write(buffer, 0, bytesRead);
                    }
                    secondStream.close();
                    writer.writeBytes(LINE_FEED);
                }

                writer.writeBytes("--" + boundary + "--" + LINE_FEED);
                writer.flush();
                writer.close();

                int responseCode = conn.getResponseCode();
                InputStream responseStream = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String finalResponse = response.toString();

                runOnUiThread(() -> {
                    Log.d("Response", "Server response: " + finalResponse);
                    Toast.makeText(MainActivity.this, finalResponse, Toast.LENGTH_LONG).show();

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Server Response")
                            .setMessage(finalResponse)
                            .setPositiveButton("OK", null)
                            .show();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }
}
