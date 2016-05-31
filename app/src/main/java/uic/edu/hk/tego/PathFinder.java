package uic.edu.hk.tego;

import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * PathFinder is able to search for the shortest mPath inside the QuadTree data structure using A*
 */
public class PathFinder {

    private double mUnit;
    private List<Node> mOpenList;
    private List<Node> mClosedList;
    private ArrayList<Vector2> mPath;
    private Node mGoal;
    private QuadTree mQuadTree;

    public PathFinder(QuadTree quadTree) {
        mQuadTree = quadTree;
        mUnit = quadTree.getUnit();
    }

    private void resetSearchAlgorithm() {
        mOpenList = new ArrayList<>();
        mClosedList = new ArrayList<>();
        mPath = new ArrayList<>();
        mGoal = null;
    }

    /**
     * finds the shortest mPath between a start and an end point using A*
     * https://en.wikipedia.org/wiki/A*_search_algorithm
     *
     * @param from start point
     * @param to   end point
     * @return list of way points (empty if not available)
     * @throws Exception when not mPath is found or the search space is not available
     */
    public List<Vector2> findPathBetween(Vector2 from, Vector2 to) throws Exception {
        resetSearchAlgorithm();
        if (!mQuadTree.isFilled(from) || !mQuadTree.isFilled(to)) {
            throw new Exception("fields are not visited in quadtree");
        }
        from = mQuadTree.rasterize(from);
        to = mQuadTree.rasterize(to);
        mGoal = new Node(to);
        mOpenList.add(new Node(from));
        do {
            Node currentNode = getClosestNode();
            if (currentNode.equals(mGoal)) {
                while (currentNode.parent != null) {
                    mPath.add(new Vector2(currentNode.x, currentNode.y));
                    currentNode = currentNode.parent;
                }
                return mPath;
            }
            mOpenList.remove(currentNode);
            mClosedList.add(currentNode);
            expandNode(currentNode);
        } while (!mOpenList.isEmpty());
        throw new Exception("no path found");
    }

    /**
     * expand the mOpenList by the surrounding fields in the quadtree
     *
     * @param currentNode center node for the expansion
     */
    private void expandNode(Node currentNode) {
        Node neighbours[] = new Node[8];
        neighbours[0] = new Node(currentNode.x + mUnit, currentNode.y);
        neighbours[1] = new Node(currentNode.x + mUnit, currentNode.y + mUnit);
        neighbours[2] = new Node(currentNode.x + mUnit, currentNode.y - mUnit);
        neighbours[3] = new Node(currentNode.x - mUnit, currentNode.y);
        neighbours[4] = new Node(currentNode.x - mUnit, currentNode.y + mUnit);
        neighbours[5] = new Node(currentNode.x - mUnit, currentNode.y - mUnit);
        neighbours[6] = new Node(currentNode.x, currentNode.y + mUnit);
        neighbours[7] = new Node(currentNode.x, currentNode.y - mUnit);
        for (Node neighbour : neighbours) {
            if (mClosedList.contains(neighbour) || !mQuadTree.isFilled(new Vector2(neighbour.x, neighbour.y))) {
                continue;
            }
            neighbour.parent = currentNode;
            if (!mOpenList.contains(neighbour) || mOpenList.contains(neighbour) && distance(mOpenList.get(mOpenList.indexOf(neighbour)), mGoal) > distance(neighbour, mGoal)) {
                mOpenList.add(neighbour);
            }
        }
    }

    /**
     * finds the Node with the smallest distance in the mOpenList
     *
     * @return a Node with the smallest distance
     */
    private Node getClosestNode() {
        Node min = mOpenList.get(0);
        for (Node vector : mOpenList) {
            if (distance(vector, mGoal) < distance(min, mGoal)) {
                min = vector;
            }
        }
        return min;
    }

    /**
     * Computes the A* distance f(x) = g(x) + h(x)
     *
     * @param v1 field 1 for comparison
     * @param v2 field 1 for comparison
     * @return the distance
     */
    private int distance(Node v1, Node v2) {
        // g are the hops to the starting point
        int g = 0;
        Node v = v1;
        while (v.parent != null) {
            g++;
            v = v.parent;
        }
        // h is the rounded euclidean space to the end point
        int h = (int) (Math.sqrt((v1.x - v2.x) * (v1.x - v2.x) + (v1.y - v2.y) * (v1.y - v2.y)) / mUnit) + 1;
        return g + h;
    }

    public List<Vector2> findPathBetween(Vector3 a, Vector3 b) throws Exception {
        return findPathBetween(new Vector2(a.x, a.z), new Vector2(b.x, b.z));
    }

    /**
     * Node class for vector2d encapsulation with parent node and equals()
     */
    private class Node {
        final double x, y;
        Node parent;

        public Node(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Node(Vector2 v) {
            this.x = v.getX();
            this.y = v.getY();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Node) {
                Node v = (Node) o;
                return v.x == x && v.y == y;
            }
            return false;
        }
    }
}


