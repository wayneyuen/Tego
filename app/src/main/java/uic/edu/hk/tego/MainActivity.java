package uic.edu.hk.tego;


import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoCameraPreview;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import org.rajawali3d.math.vector.Vector2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import lejos.remote.ev3.RemoteRequestEV3;
import lejos.remote.ev3.RemoteRequestPilot;
import lejos.robotics.RegulatedMotor;

public class MainActivity extends AppCompatActivity {

    // Permission request action
    public static final int REQUEST_CODE_TANGO_PERMISSION = 0;

    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.relocalizeImage)
    ImageView mImage;

    @BindView(R.id.tangoCamera)
    TangoCameraPreview mCamera;

    @BindView(R.id.adfNameLabel)
    TextView mAdfNameLabel;

    @BindView(R.id.xLabel)
    TextView mXLabel;

    @BindView(R.id.yLabel)
    TextView mYLabel;

    @BindView(R.id.zLabel)
    TextView mZLabel;

    @BindView(R.id.startPointLabel)
    TextView mStartPointLabel;

    @BindView(R.id.endPointLabel)
    TextView mEndPointLabel;

    @BindView(R.id.navigateBtn)
    Button mNavigateBtn;

    @OnClick(R.id.tagBtn)
    void tag() {
        isTag = true;
    }

    @OnClick(R.id.navigateBtn)
    void navigate() {
        isSettingupNavigation = true;
    }

    private RemoteRequestEV3 mEV3;
    private RemoteRequestPilot mPilot;
    private RegulatedMotor mLeftMotor;
    private RegulatedMotor mRightMotor;

    private Tango mTango;
    private TangoConfig mConfig;

    private QuadTree mData;
    private List<Vector2> mPath;
    private Vector2 mStartPoint;
    private Vector2 mEndPoint;

    private boolean isTag = false;
    private boolean isSettingupNavigation = false;
    private boolean isNavigating = false;

    public static final int QUAD_TREE_START = -60;
    public static final int QUAD_TREE_RANGE = 120;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mData = new QuadTree(new Vector2(QUAD_TREE_START, QUAD_TREE_START), QUAD_TREE_RANGE, 8);

        ButterKnife.bind(this);

        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The result of the permission activity.
        //
        // Note that when the permission activity is dismissed, the
        // AreaDescriptionActivity's onResume() callback is called. As the
        // TangoService is connected in the onResume() function, we do not call
        // connect here.
        //
        // Check which request we're responding to
        if (requestCode == REQUEST_CODE_TANGO_PERMISSION) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.arealearning_permission, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Initialize Tango Service as a normal Android Service, since we call
        // mTango.disconnect() in onPause, this will unbind Tango Service, so
        // every time when onResume gets called, we should create a new Tango object.
        mTango = new Tango(this, new Runnable() {
            @Override
            public void run() {

                mCamera.connectToTangoCamera(mTango,
                        TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE);

                mConfig = setupTangoConfig(mTango);

                try {
                    setTangoListeners();
                } catch (TangoErrorException e) {
                    Log.e(TAG, getString(R.string.exception_tango_error), e);
                } catch (SecurityException e) {
                    Log.e(TAG, getString(R.string.permission_motion_tracking), e);
                }

                try {
                    mTango.connect(mConfig);
                } catch (TangoOutOfDateException e) {
                    Log.e(TAG, getString(R.string.exception_out_of_date), e);
                } catch (TangoErrorException e) {
                    Log.e(TAG, getString(R.string.exception_tango_error), e);
                }

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            mTango.disconnect();
            mCamera.disconnectFromTangoCamera();
        } catch (TangoErrorException e) {
            Log.e(TAG, getString(R.string.exception_tango_error), e);
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);

        ArrayList<String> fullUuidList;
        // Returns a list of ADFs with their UUIDs
        fullUuidList = tango.listAreaDescriptions();
        // Load the latest ADF if ADFs are found.
        if (fullUuidList.size() > 0) {
            String uuid = fullUuidList.get(fullUuidList.size() - 1);
            config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, uuid);

            // Display the loaded ADF name in the UI
            TangoAreaDescriptionMetaData metaData = mTango.loadAreaDescriptionMetaData(uuid);
            byte[] nameBytes = metaData.get(TangoAreaDescriptionMetaData.KEY_NAME);
            if (nameBytes != null) {
                final String name = new String(nameBytes);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdfNameLabel.setText(name);
                    }
                });
            }
        }

        return config;
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        final ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData tangoPoseData) {
                if (tangoPoseData.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                        && tangoPoseData.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE
                        && tangoPoseData.statusCode == TangoPoseData.POSE_VALID) {
                    logPose(tangoPoseData);
                    addPoseToQuadTree(tangoPoseData);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mImage.setVisibility(View.GONE);
                        }
                    });

                    if (isTag) {
                        tagLocation(tangoPoseData);
                    }

                    if (isSettingupNavigation) {
                        setupNavigation(tangoPoseData);
                    }

                    if (isNavigating) {
                        try {
                            new Control().execute("up");
                            Thread.sleep(1000);
                            new Control().execute("stop");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        new Connection().execute("disconnect");
                        isNavigating = false;
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData tangoXyzIjData) {
            }

            @Override
            public void onFrameAvailable(int i) {
                if (i == TangoCameraIntrinsics.TANGO_CAMERA_FISHEYE) {
                    mCamera.onFrameAvailable();
                }
            }

            @Override
            public void onTangoEvent(TangoEvent tangoEvent) {

            }
        });
    }

    private void addPoseToQuadTree(TangoPoseData pose) {
        float translations[] = pose.getTranslationAsFloats();
        float x = translations[0];
        float y = translations[1];
        Vector2 point = new Vector2(x, y);
        mData.setFilledInvalidate(point);
    }

    private void setStartPoint(TangoPoseData pose) {
        float translations[] = pose.getTranslationAsFloats();
        float x = translations[0];
        float y = translations[1];
        mStartPoint = new Vector2(x, y);
    }

    private void setEndPoint(TangoPoseData pose) {
        float translations[] = pose.getTranslationAsFloats();
        float x = translations[0];
        float y = translations[1];
        mEndPoint = new Vector2(x, y);
    }

    /**
     * Log the Position and Orientation of the given pose
     * in the Logcat as information.
     *
     * @param pose the pose to log.
     */
    private void logPose(TangoPoseData pose) {
        StringBuilder stringBuilder = new StringBuilder();
        final float translations[] = pose.getTranslationAsFloats();
        stringBuilder.append("Position: ")
                .append(translations[0])
                .append(", ")
                .append(translations[1])
                .append(", ")
                .append(translations[2]);

        float orientation[] = pose.getRotationAsFloats();
        stringBuilder.append(". Orientation: ")
                .append(orientation[0])
                .append(", ")
                .append(orientation[1])
                .append(", ")
                .append(orientation[2])
                .append(", ")
                .append(orientation[3]);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mXLabel.setText(translations[0] + "");
                mYLabel.setText(translations[1] + "");
                mZLabel.setText(translations[2] + "");
            }
        });
    }

    private class Connection extends AsyncTask<String, Integer, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            if (params[0].equals("connect")) {
                try {
                    mEV3 = new RemoteRequestEV3(params[1]);
                    mLeftMotor = mEV3.createRegulatedMotor("A", 'L');
                    mRightMotor = mEV3.createRegulatedMotor("D", 'L');

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (params[0].equals("disconnect")) {
                mLeftMotor.close();
                mRightMotor.close();
                mEV3.disConnect();
                mEV3 = null;
            }

            return -1;
        }
    }

    private class Control extends AsyncTask<String, Integer, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            if (params[0].equals("up")) {
                mLeftMotor.forward();
                mRightMotor.forward();
            } else if (params[0].equals("back")) {
                mLeftMotor.backward();
                mRightMotor.backward();
            } else if (params[0].equals("right")) {
                mLeftMotor.forward();
                mRightMotor.stop(true);
            } else if (params[0].equals("left")) {
                mLeftMotor.stop(true);
                mRightMotor.forward();
            } else if (params[0].equals("stop")) {
                mLeftMotor.stop(true);
                mRightMotor.stop(true);
            }

            return -1;
        }
    }

    private void tagLocation(TangoPoseData tangoPoseData) {
        setEndPoint(tangoPoseData);
        final float translations[] = tangoPoseData.getTranslationAsFloats();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEndPointLabel.setText(Arrays.toString(translations));
                mNavigateBtn.setEnabled(true);
            }
        });
        isTag = false;
    }

    private void setupNavigation(TangoPoseData tangoPoseData) {
        setStartPoint(tangoPoseData);
        if (mStartPoint != null
                && mEndPoint != null) {
            PathFinder finder = new PathFinder(mData);
            try {
                mPath = finder.findPathBetween(mStartPoint, mEndPoint);
                for (Vector2 point : mPath) {
                    Log.i(TAG, point.getX() + ", " + point.getY());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            new Connection().execute("connect", "192.168.43.151");
            isNavigating = true;
        }
        isSettingupNavigation = false;
    }
}




















