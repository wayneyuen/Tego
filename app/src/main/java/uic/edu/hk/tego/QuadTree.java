package uic.edu.hk.tego;

import org.rajawali3d.math.vector.Vector2;

import java.util.ArrayList;
import java.util.List;

public class QuadTree {

    public interface QuadTreeDataListener {
        void OnQuadTreeUpdate();
    }

    public static final double PLANE_SPACER = 0.02;
    private final Vector2 mPosition;
    private final double mHalfRange;
    private int mDepth;
    private final double mRange;
    private boolean mFilled = false;
    private QuadTree[] mChildren = new QuadTree[4];
    private QuadTreeDataListener mListener;

    public QuadTree(Vector2 position, double range, int depth) {
        mPosition = position;
        mRange = range;
        mHalfRange = range / 2.0;
        mDepth = depth;
    }

    public List<Vector2> getFilledEdgePointsAsPolygon() {
        ArrayList<Vector2> list = new ArrayList<>();
        getFilledEdgePointsAsPolygon(list);
        return list;
    }

    private void getFilledEdgePointsAsPolygon(ArrayList<Vector2> list) {
        if (mDepth == 0 && mFilled) {
            list.add(new Vector2(mPosition.getX(), mPosition.getY()));
            list.add(new Vector2(mPosition.getX() + mRange - PLANE_SPACER, mPosition.getY()));
            list.add(new Vector2(mPosition.getX(), mPosition.getY() + mRange - PLANE_SPACER));

            list.add(new Vector2(mPosition.getX(), mPosition.getY() + mRange - PLANE_SPACER));
            list.add(new Vector2(mPosition.getX() + mRange - PLANE_SPACER, mPosition.getY()));
            list.add(new Vector2(mPosition.getX() + mRange - PLANE_SPACER, mPosition.getY() + mRange - PLANE_SPACER));
        } else {
            for (QuadTree child : mChildren) {
                if (child != null) {
                    child.getFilledEdgePointsAsPolygon(list);
                }
            }
        }
    }

    public List<Vector2> getFilledPoints() {
        ArrayList<Vector2> list = new ArrayList<>();
        getFilledPoints(list);
        return list;
    }

    private void getFilledPoints(ArrayList<Vector2> list) {
        if (mDepth == 0 && mFilled) {
            list.add(mPosition);
        } else {
            for (QuadTree child : mChildren) {
                if (child != null) {
                    child.getFilledPoints(list);
                }
            }
        }
    }

    public void setFilledInvalidate(Vector2 point) {
        if (!isFilled(point)) {
            setFilled(point);
            if(mListener != null){
                mListener.OnQuadTreeUpdate();
            }
        }
    }

    public void setListener(QuadTreeDataListener listener) {
        mListener = listener;
    }

    public void setFilled(Vector2 point) {
        if (mDepth == 0) {
            mFilled = true;
        } else {
            int index = getChildIndex(point);
            if (mChildren[index] == null) {
                mChildren[index] = new QuadTree(getChildPositionByIndex(index), mHalfRange, mDepth - 1);
            }
            mChildren[index].setFilled(point);
        }
    }

    private Vector2 getChildPositionByIndex(int index) {
        switch (index) {
            case 0:
                return new Vector2(mPosition.getX(), mPosition.getY());
            case 1:
                return new Vector2(mPosition.getX(), mPosition.getY() + mHalfRange);
            case 2:
                return new Vector2(mPosition.getX() + mHalfRange, mPosition.getY());
            default:
                return new Vector2(mPosition.getX() + mHalfRange, mPosition.getY() + mHalfRange);
        }
    }

    private int getChildIndex(Vector2 point) {
        if (point.getX() < mPosition.getX() + mHalfRange) {
            if (point.getY() < mPosition.getY() + mHalfRange) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (point.getY() < mPosition.getY() + mHalfRange) {
                return 2;
            } else {
                return 3;
            }
        }
    }

    public void clear() {
        if (mDepth == 0) {
            mFilled = false;
        } else {
            for (QuadTree child : mChildren) {
                if (child != null) {
                    child.clear();
                }
            }
        }
    }

    public boolean isFilled(Vector2 to) {
        if (outOfRange(to)) {
            return false;
        } else if (mDepth == 0) {
            return mFilled;
        } else {
            int index = getChildIndex(to);
            return mChildren[index] != null && mChildren[index].isFilled(to);
        }
    }

    private boolean outOfRange(Vector2 to) {
        return to.getX() > mPosition.getX() + mRange ||
                to.getX() < mPosition.getX() ||
                to.getY() > mPosition.getY() + mRange ||
                to.getY() < mPosition.getY();
    }

    public double getUnit() {
        return mRange / (Math.pow(2, mDepth));
    }

    public Vector2 rasterize(Vector2 a) {
        if (mDepth == 0) {
            return mPosition;
        } else {
            int index = getChildIndex(a);
            if (mChildren[index] != null) {
                return mChildren[index].rasterize(a);
            }
        }
        return a;
    }

}
