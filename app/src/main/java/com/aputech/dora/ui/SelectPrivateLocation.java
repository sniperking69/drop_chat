package com.aputech.dora.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.aputech.dora.Model.User;
import com.aputech.dora.Model.message;
import com.aputech.dora.Model.notification;
import com.aputech.dora.R;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SelectPrivateLocation extends AppCompatActivity implements OnMapReadyCallback {
    private final float DEFAULT_ZOOM = 15;
    MaterialButton Forward;
    FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
    LatLng latLngfinal;
    Uri uri;
    int type;
    String ext;
    ArrayList<User> sendto;
    String txt;
    User currentUser;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private Location mLastKnownLocation;
    private LocationCallback locationCallback;
    private View mapView;
    private FirebaseAuth auth = FirebaseAuth.getInstance();
    private String geocode;
    private TextView resutText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_private_location);
        resutText = findViewById(R.id.dragg_result);
        Forward = findViewById(R.id.forward);
        Intent intent = getIntent();
        currentUser = intent.getParcelableExtra("currentuser");
        sendto = intent.getParcelableArrayListExtra("sendto");
        txt = intent.getStringExtra("Desc");
        type = intent.getIntExtra("type", 1);
        if (intent.getStringExtra("Uri") != null) {
            uri = Uri.parse(intent.getStringExtra("Uri"));
            ext = intent.getStringExtra("ext");
        }
        Forward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int x = 0; x < sendto.size(); x++) {
                    uploadFire(type, txt, ext, uri, sendto.get(x).getUserid());
                }

            }
        });

        Places.initialize(getApplicationContext(), getResources().getString(R.string.google_api_key));
        final PlacesClient placesClient = Places.createClient(this);
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG));
        autocompleteFragment.setHint("Search Location");
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                moveMap(place.getLatLng());
            }

            @Override
            public void onError(Status status) {
                Log.i("Bigpp", "An error occurred: " + status);
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapView = mapFragment.getView();
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(SelectPrivateLocation.this);

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                Geocodeget();
            }
        });
        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                Geocodeget();
            }
        });
        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            View locationButton = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(10, 10, 10, 150);
        }

        //check if gps is enabled or not and then request user to enable it
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(SelectPrivateLocation.this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(SelectPrivateLocation.this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                getDeviceLocation();
            }
        });

        task.addOnFailureListener(SelectPrivateLocation.this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    try {
                        resolvable.startResolutionForResult(SelectPrivateLocation.this, 51);
                    } catch (IntentSender.SendIntentException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 51) {
            if (resultCode == RESULT_OK) {
                getDeviceLocation();
            }
        }
    }


    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        mFusedLocationProviderClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            } else {
                                final LocationRequest locationRequest = LocationRequest.create();
                                locationRequest.setInterval(10000);
                                locationRequest.setFastestInterval(5000);
                                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                                locationCallback = new LocationCallback() {
                                    @Override
                                    public void onLocationResult(LocationResult locationResult) {
                                        super.onLocationResult(locationResult);
                                        if (locationResult == null) {
                                            return;
                                        }
                                        mLastKnownLocation = locationResult.getLastLocation();
                                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                        mFusedLocationProviderClient.removeLocationUpdates(locationCallback);
                                    }
                                };
                                mFusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);

                            }
                        } else {
                            Toast.makeText(SelectPrivateLocation.this, "unable to get last location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void Geocodeget() {
        double lng = mMap.getCameraPosition().target.longitude;
        double lat = mMap.getCameraPosition().target.latitude;
        LatLng latLng = new LatLng(lat, lng);
        Geocoder geocoder = new Geocoder(SelectPrivateLocation.this);
        latLngfinal = latLng;
        try {
            List<Address> addressList = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addressList != null && addressList.size() > 0) {
                String locality = addressList.get(0).getAddressLine(0);
                String country = addressList.get(0).getCountryName();
                if (!locality.isEmpty() && !country.isEmpty())
                    geocode = locality;
                resutText.setText(geocode);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void moveMap(LatLng latLng) {
        float zoom = 15;
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
    }

    public void UploadVideo(final String rf, String ext, Uri videoUri, final String userid) {
        if (videoUri != null) {
            final ProgressDialog progressDialog = new ProgressDialog(SelectPrivateLocation.this);
            progressDialog.setTitle("Uploading...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            final StorageReference ref = firebaseStorage.getReference("videos").child(rf + "." + ext);
            ref.putFile(videoUri)
                    .addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                            if (task.isSuccessful()) {
                                ref.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Uri> task) {
                                        if (task.isSuccessful()) {
                                            progressDialog.dismiss();
                                            db.collection("Inbox").document(rf).update("videoUrl", task.getResult().toString());
                                            notification noti = new notification();
                                            noti.setDocument(rf);
                                            noti.setTyp(1);
                                            noti.setUserid(auth.getUid());
                                            noti.setText(currentUser.getUserName() + "  Sent You Private Post");
                                            CollectionReference notiref = db.collection("Users").document(userid).collection("notify");
                                            notiref.add(noti);
                                            sendto.remove(sendto.size() - 1);
                                            if (sendto.size() == 0) {
                                                Intent intent = new Intent();
                                                setResult(Activity.RESULT_OK, intent);
                                                finish();
                                            }

                                        }

                                    }
                                });
                            } else {
                                progressDialog.dismiss();
                                Toast.makeText(SelectPrivateLocation.this, "Upload Failed Network Error", Toast.LENGTH_LONG).show();
                            }

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Toast.makeText(SelectPrivateLocation.this, "Failed " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot
                                    .getTotalByteCount());
                            progressDialog.setMessage("Uploaded " + (int) progress + "%");
                        }
                    });
        }
    }

    private void uploadFire(final int type, String text, final String ext, final Uri uri, final String userid) {
        final message post = new message();
        GeoPoint geoPoint = new GeoPoint(latLngfinal.latitude, latLngfinal.longitude);
        post.setLocation(geoPoint);
        post.setType(type);
        post.setDescription(text);
        post.setSender(auth.getUid());
        post.setReceiver(userid);
        db.collection("Inbox").add(post).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                final String commentref = documentReference.getId();
                db.collection("Inbox").document(commentref).update("refmsg", commentref).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (type == 1) {
                            notification noti = new notification();
                            noti.setDocument(commentref);
                            noti.setTyp(1);
                            noti.setUserid(auth.getUid());
                            noti.setText(currentUser.getUserName() + "  Sent You Private Post");
                            CollectionReference notiref = db.collection("Users").document(userid).collection("notify");
                            notiref.add(noti);
                            sendto.remove(sendto.size() - 1);
                            if (sendto.size() == 0) {
                                Intent intent = new Intent();
                                setResult(Activity.RESULT_OK, intent);
                                finish();
                            }
                        }
                        if (type == 2) {
                            UploadPicture(commentref, uri, userid);
                        }
                        if (type == 3) {
                            UploadVideo(commentref, ext, uri, userid);
                        }
                        if (type == 4) {
                            UploadAudio(commentref, ext, uri, userid);
                        }

                    }
                });
            }
        });
    }

    private void UploadAudio(final String commentref, String ext, Uri audiouri, final String userid) {
        final ProgressDialog progressDialog = new ProgressDialog(SelectPrivateLocation.this);
        progressDialog.setTitle("Uploading...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        final StorageReference ref = firebaseStorage.getReference("audio").child(commentref + "." + ext);
        ref.putFile(audiouri)
                .addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            ref.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                                @Override
                                public void onComplete(@NonNull Task<Uri> task) {
                                    if (task.isSuccessful()) {
                                        progressDialog.dismiss();
                                        db.collection("Inbox").document(commentref).update("audioUrl", task.getResult().toString());
                                        notification noti = new notification();
                                        noti.setDocument(commentref);
                                        noti.setTyp(1);
                                        noti.setUserid(auth.getUid());
                                        noti.setText(currentUser.getUserName() + "  Sent You Private Post");
                                        CollectionReference notiref = db.collection("Users").document(userid).collection("notify");
                                        notiref.add(noti);
                                        sendto.remove(sendto.size() - 1);
                                        if (sendto.size() == 0) {
                                            Intent intent = new Intent();
                                            setResult(Activity.RESULT_OK, intent);
                                            finish();
                                        }
                                    }
                                }
                            });
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(SelectPrivateLocation.this, "Upload Failed Network Error", Toast.LENGTH_LONG).show();
                        }

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(SelectPrivateLocation.this, "Failed " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot
                                .getTotalByteCount());
                        progressDialog.setMessage("Uploaded " + (int) progress + "%");
                    }
                });
    }

    private void UploadPicture(final String commentref, Uri imguri, final String userid) {
        final ProgressDialog progressDialog = new ProgressDialog(SelectPrivateLocation.this);
        progressDialog.setTitle("Uploading...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        final StorageReference ref = firebaseStorage.getReference("images").child(commentref + "." + ext);
        ref.putFile(imguri)
                .addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            ref.getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                                @Override
                                public void onComplete(@NonNull Task<Uri> task) {
                                    if (task.isSuccessful()) {
                                        progressDialog.dismiss();
                                        db.collection("Inbox").document(commentref).update("imageUrl", task.getResult().toString());
                                        notification noti = new notification();
                                        noti.setDocument(commentref);
                                        noti.setTyp(1);
                                        noti.setUserid(auth.getUid());
                                        noti.setText(currentUser.getUserName() + "  Sent You Private Post");
                                        CollectionReference notiref = db.collection("Users").document(userid).collection("notify");
                                        notiref.add(noti);
                                        sendto.remove(sendto.size() - 1);
                                        if (sendto.size() == 0) {
                                            Intent intent = new Intent();
                                            setResult(Activity.RESULT_OK, intent);
                                            finish();
                                        }
                                    }
                                }
                            });
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(SelectPrivateLocation.this, "Upload Failed Network Error", Toast.LENGTH_LONG).show();
                        }

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(SelectPrivateLocation.this, "Failed " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot
                                .getTotalByteCount());
                        progressDialog.setMessage("Uploaded " + (int) progress + "%");
                    }
                });

    }
}
