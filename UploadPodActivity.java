package com.insertcart.insetcart.ui;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import android.provider.OpenableColumns;
import com.insertcart.insetcart.MainActivity;
import android.Manifest;
import com.insertcart.insetcart.R;
import com.insertcart.insetcart.data.MySharedPreferences;
import com.insertcart.insetcart.data.model.ApiService;
import com.insertcart.insetcart.data.model.PodUploadResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.apache.commons.io.IOUtils;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UploadPodActivity extends AppCompatActivity {
    private MySharedPreferences mySharedPreferences;
    private ProgressBar progressBar;
    private String mytoken, userId;
    private ImageView backbutton, settings;
    private EditText docketNoEditText;
    private Button chooseFileButton, uploadButton;
    private Uri fileUri;
    private static final int REQUEST_READ_STORAGE = 100;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_pod);
        mySharedPreferences = new MySharedPreferences(this);

        mytoken = mySharedPreferences.getToken();

        progressBar = findViewById(R.id.progressBar);
        backbutton = findViewById(R.id.leftButton);
        settings = findViewById(R.id.rightButton);


        docketNoEditText = findViewById(R.id.docketNoEditText);
        chooseFileButton = findViewById(R.id.chooseFileButton);
        uploadButton = findViewById(R.id.uploadButton);

        chooseFileButton.setOnClickListener(v -> chooseFile());
        uploadButton.setOnClickListener(v -> uploadFile());

        backbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Perform the action you want when the button is clicked
                Intent intent = new Intent(UploadPodActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Perform the action you want when the button is clicked
                Intent intent = new Intent(UploadPodActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
    }



    private void chooseFile() {
        // Check for permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_STORAGE);
        } else {
            openFilePickerWithActivityResult();
        }
    }
    private void openFilePickerWithActivityResult() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Allow all file types
        String[] mimeTypes = {"image/*", "application/pdf"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        resultLauncher.launch(intent);  // Use the resultLauncher
    }
    // Initialize resultLauncher somewhere in your Activity
    private ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            fileUri = data.getData();
                        }
                    }
                }
            });

    private File getFileFromContentUri(Uri contentUri) {
        try (InputStream inputStream = getContentResolver().openInputStream(contentUri)) {
            File tempFile = File.createTempFile("upload", ".tmp"); // Change extension if needed
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            IOUtils.copy(inputStream, outputStream); // Consider a library like Apache Commons IO
            return tempFile;
        } catch (IOException e) {
            Log.e("FileUpload", "Error getting file from content Uri: " + e.getMessage());
            return null;
        }
    }
    private String getRealPathFromUri(Uri contentUri) {
        String result = null;
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            result = cursor.getString(columnIndex);
            cursor.close();
        }
        return result;
    }

    private void uploadFile() {
        if (fileUri == null) {
            Toast.makeText(this, "Please choose a file", Toast.LENGTH_SHORT).show();
            return;
        }

        String docketNo = docketNoEditText.getText().toString().trim();
        if (docketNo.isEmpty()) {
            Toast.makeText(this, "Please enter Docket No", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show();
                return;
            }

            // Read content of InputStream into byte array
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
            byte[] fileBytes = byteArrayOutputStream.toByteArray();

            RequestBody requestFile = RequestBody.create(MediaType.parse(getContentResolver().getType(fileUri)), fileBytes);

            String fileName = getFileNameFromUri(fileUri);

            MultipartBody.Part imagePart = MultipartBody.Part.createFormData("base64Image", fileName, requestFile);

            //MultipartBody.Part imagePart = MultipartBody.Part.createFormData("base64Image", "file", requestFile);



            RequestBody docketNoPart = RequestBody.create(MediaType.parse("text/plain"), docketNo);
            RequestBody branchNamePart = RequestBody.create(MediaType.parse("text/plain"), "AGR");

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://www.insertcart.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            String authorization = "Bearer " + mytoken;
            ApiService api = retrofit.create(ApiService.class);
            Log.d("FileUpload", "File path: " + requestFile);
            Log.d("FileUpload", "imagePart: " + imagePart);
            Log.d("FileUpload", "fileUri: " + fileUri);
            Log.d("FileUpload", "docketNo " + docketNo);
            Call<PodUploadResponse> call = api.uploadPOD(authorization, imagePart, docketNoPart, branchNamePart);

            progressBar.setVisibility(View.VISIBLE);

            call.enqueue(new Callback<PodUploadResponse>() {
                @Override
                public void onResponse(Call<PodUploadResponse> call, Response<PodUploadResponse> response) {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        PodUploadResponse uploadResponse = response.body();
                        Toast.makeText(UploadPodActivity.this, uploadResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("UploadError", response.message().toString());
                        Toast.makeText(UploadPodActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<PodUploadResponse> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(UploadPodActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("UploadError", t.getMessage());
                }
            });
        } catch (IOException e) {
            Log.e("FileReadError", "Error reading file: ", e);
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private String getFileNameFromUri(Uri uri) {
        String fileName = "file";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        return fileName;
    }

}
