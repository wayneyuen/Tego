package uic.edu.hk.tego;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTouch;

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

    @BindView(R.id.angleLabel)
    TextView mAngleLabel;

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

    @OnClick(R.id.rotateBtn)
    void rotate() {
        isRotating = true;
    }

    @OnClick(R.id.connectBtn)
    void connect() {
        mLego = new LegoControl();
        mLego.connect();
    }

    @OnClick(R.id.disconnectBtn)
    void disconnect() {
        mLego.disconnect();
        mLego = null;
    }

    @OnTouch(R.id.upBtn)
    boolean up(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLego.up();
                return true;
            case MotionEvent.ACTION_UP:
                mLego.stop();
                return false;
        }
        return false;
    }

    @OnTouch(R.id.backBtn)
    boolean back(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLego.back();
                return true;
            case MotionEvent.ACTION_UP:
                mLego.stop();
                return false;
        }
        return false;
    }

    @OnTouch(R.id.leftBtn)
    boolean left(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLego.left();
                return true;
            case MotionEvent.ACTION_UP:
                mLego.stop();
                return false;
        }
        return false;
    }

    @OnTouch(R.id.rightBtn)
    boolean right(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLego.right();
                return true;
            case MotionEvent.ACTION_UP:
                mLego.stop();
                return false;
        }
        return false;
    }

    private Tango mTango;
    private TangoConfig mConfig;

    private LegoControl mLego;

    private QuadTree mData;
    private List<Vector2> mPath;
    private int mNowPoint;
    private Vector2 mStartPoint;
    private Vector2 mEndPoint;

    private Vector2 from;
    private Vector2 to;

    private boolean isTag = false;
    private boolean isSettingupNavigation = false;
    private boolean isFirstMove = false;
    private boolean isMoving = false;
    private boolean isRotating = false;

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
                if (tangoPoseData.baseFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE
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

                    if (isFirstMove) {
                        if (!mLego.isMoving) {
                            float[] fromTans = tangoPoseData.getTranslationAsFloats();
                            float fromX = fromTans[0];
                            float fromY = fromTans[1];

                            from = new Vector2(fromX, fromY);

                            mLego.travel(20);
                        } else {
                            float[] nowTrans = tangoPoseData.getTranslationAsFloats();
                            float nowX = nowTrans[0];
                            float nowY = nowTrans[1];

                            Vector2 now = new Vector2(nowX, nowY);
                            to = mPath.get(mNowPoint - 1);

                            int angle = calculateAngle(from, now , to);
                            if (!mLego.isMoving) {
                                from = now;
                                mLego.rotate(angle);
                                Log.i(TAG, "Rotated.................................................. " +
                                        "(" + angle + ")" +
                                        "degrees");
                            } else {
                                mLego.up();
                                Log.i(TAG, "Moving to.................................................. " +
                                        "(" + to.getX() +
                                        ", " +
                                        to.getY() + ")");

                                isFirstMove = false;
                                isMoving = true;
                            }
                        }
                    }

                    if (isMoving) {
                        float[] nowTrans = tangoPoseData.getTranslationAsFloats();
                        float nowX = nowTrans[0];
                        float nowY = nowTrans[1];

                        if (Math.abs(nowX - to.getX()) < 0.05 &&
                                Math.abs(nowY - to.getY()) < 0.05) {
                            mLego.stop();

                            Log.i(TAG, "Arrived at.................................................. " +
                                    "(" + to.getX() +
                                    ", " +
                                    to.getY() + ")");

                            isMoving = false;
                            mNowPoint--;
                            isRotating = mNowPoint >= 0;
                        }
                    }

                    if (isRotating) {
                        float[] nowTrans = tangoPoseData.getTranslationAsFloats();
                        float nowX = nowTrans[0];
                        float nowY = nowTrans[1];

                        Vector2 now = new Vector2(nowX, nowY);
                        to = mPath.get(mNowPoint - 1);

                        int angle = calculateAngle(from, now, to);
                        if (!mLego.isMoving) {
                            from = now;
                            mLego.rotate(angle);
                        } else {
                            mLego.up();

                            Log.i(TAG, "Rotated.................................................. " +
                                    "(" + angle + ")" +
                                    "degrees");

                            Log.i(TAG, "Moving to.................................................. " +
                                    "(" + to.getX() +
                                    ", " +
                                    to.getY() + ")");

                            isRotating = false;
                            isMoving = true;
                        }
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

    private void logPose(TangoPoseData pose) {
        StringBuilder stringBuilder = new StringBuilder();
        final float translations[] = pose.getTranslationAsFloats();
        stringBuilder.append("Position: ")
                .append(translations[0])
                .append(", ")
                .append(translations[1]);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mXLabel.setText(translations[0] + "");
                mYLabel.setText(translations[1] + "");
                mZLabel.setText(translations[2] + "");
            }
        });

        Log.d(TAG, "Pose: " + stringBuilder.toString());
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
                mNowPoint = mPath.size() - 1;
                for (Vector2 point : mPath) {
                    Log.i(TAG, point.getX() + ", " + point.getY());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            isFirstMove = true;
        }
        isSettingupNavigation = false;
    }

    private int calculateAngle(Vector2 from, Vector2 now, Vector2 to) {

//        float[] fromTrans = from.getTranslationAsFloats();
//        float fromX = fromTrans[0];
//        float fromY = fromTrans[1];
//
//        float[] nowTrans = now.getTranslationAsFloats();
//        float nowX = nowTrans[0];
//        float nowY = nowTrans[1];
//
//        float[] toTrans = to.getTranslationAsFloats();
//        float toX = toTrans[0];
//        float toY = toTrans[1];

        Vector2 fromV = new Vector2(
                now.getX() - from.getX(),
                now.getY() - from.getY()
        );

        Vector2 toV = new Vector2(
                to.getX() - now.getX(),
                to.getY() - now.getY()
        );

        double dot = fromV.getX() * toV.getX() + fromV.getY() * toV.getX();
        double fromVLength = Math.sqrt(
                fromV.getX() * fromV.getX() +
                fromV.getY() * fromV.getY()
        );
        double toVLength = Math.sqrt(
                toV.getX() * toV.getX() +
                toV.getY() * toV.getY()
        );

        double angle = dot / (fromVLength * toVLength);

//        double side = fromV.getX() * -toV.getX() + fromV.getY() * toV.getY();
//        if (side < 0) {
//            return (int) Math.round(-angle);
//        }

        return (int) Math.round(angle);
    }
}




















