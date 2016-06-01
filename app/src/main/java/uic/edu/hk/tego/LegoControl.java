package uic.edu.hk.tego;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

import lejos.remote.ev3.RemoteRequestEV3;
import lejos.remote.ev3.RemoteRequestPilot;
import lejos.robotics.RegulatedMotor;

public class LegoControl {
    private RemoteRequestEV3 mEV3;
    private RemoteRequestPilot mPilot;
    private RegulatedMotor mLeftMotor;
    private RegulatedMotor mRightMotor;

    public boolean isConnected = false;
    public boolean isMoving = false;

    public void connect() {
        new Connection().execute("connect", "192.168.43.151");
    }

    public void disconnect() {
        new Connection().execute("disconnect");
    }

    public void up() {
        new Control().execute("up");
    }

    public void back() {
        new Control().execute("back");
    }

    public void left() {
        new Control().execute("left");
    }

    public void right() {
        new Control().execute("right");
    }

    public void stop() {
        new Control().execute("stop");
    }

    public void rotate(int angle) {
        new Rotation().execute(-angle);
    }

    public void travel(int cm) {
        new Travel().execute(cm);
    }

    private class Connection extends AsyncTask<String, Integer, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            if (params[0].equals("connect")) {
                try {
                    mEV3 = new RemoteRequestEV3(params[1]);
                    mPilot = (RemoteRequestPilot) mEV3.createPilot(5.4f, 11.95f, "A", "D");
                    mLeftMotor = mEV3.createRegulatedMotor("A", 'L');
                    mRightMotor = mEV3.createRegulatedMotor("D", 'L');
                    return 1;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (params[0].equals("disconnect")) {
                mLeftMotor.close();
                mRightMotor.close();
                mPilot.close();
                mEV3.disConnect();
                mEV3 = null;
                return 0;
            }

            return -1;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            if (integer == 0) {
                isConnected = false;
                Log.i("LEGO", "DISCONNECTED");
            }
            if (integer == 1) {
                isConnected = true;
                Log.i("LEGO", "CONNECTED");
            }
        }
    }

    private class Control extends AsyncTask<String, Integer, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            if (mEV3 != null) {
                switch (params[0]) {
                    case "up":
                        isMoving = true;
                        mLeftMotor.forward();
                        mRightMotor.forward();
                        break;
                    case "back":
                        isMoving = true;
                        mLeftMotor.backward();
                        mRightMotor.backward();
                        break;
                    case "right":
                        isMoving = true;
                        mLeftMotor.forward();
                        mRightMotor.stop(true);
                        break;
                    case "left":
                        isMoving = true;
                        mLeftMotor.stop(true);
                        mRightMotor.forward();
                        break;
                    case "stop":
                        isMoving = false;
                        mLeftMotor.stop(true);
                        mRightMotor.stop(true);
                        break;
                }
            }
            return -1;
        }
    }

    private class Rotation extends AsyncTask<Integer, Integer, Integer> {

        @Override
        protected Integer doInBackground(Integer... params) {
            isMoving = true;
            mPilot.rotate(params[0]);
            isMoving = false;
            return -1;
        }
    }

    private class Travel extends AsyncTask<Integer, Integer, Integer> {

        @Override
        protected Integer doInBackground(Integer... params) {
            isMoving = true;
            mPilot.travel(20);
            isMoving = false;
            return -1;
        }
    }
}
