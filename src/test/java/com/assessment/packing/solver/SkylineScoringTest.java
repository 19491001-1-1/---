package com.assessment.packing.solver;

public final class SkylineScoringTest {
    public static void main(String[] args) {
        verifiesLeftWallHigherScores();
        verifiesRightWallHigherScores();
        verifiesPlacementSides();
        verifiesInfeasibleScore();
        System.out.println("SkylineScoringTest: 全部通过");
    }

    private static void verifiesLeftWallHigherScores() {
        int left = 8, right = 4;
        checkScore(7, 10, 8, left, right);
        checkScore(6, 10, 4, left, right);
        checkScore(5, 10, 9, left, right);
        checkScore(4, 5, 8, left, right);
        checkScore(3, 10, 6, left, right);
        checkScore(2, 5, 4, left, right);
        checkScore(1, 10, 3, left, right);
        checkScore(0, 5, 6, left, right);
    }

    private static void verifiesRightWallHigherScores() {
        int left = 4, right = 8;
        checkScore(7, 10, 8, left, right);
        checkScore(6, 10, 4, left, right);
        checkScore(5, 10, 9, left, right);
        checkScore(4, 5, 8, left, right);
        checkScore(3, 10, 6, left, right);
        checkScore(2, 5, 4, left, right);
        checkScore(1, 10, 3, left, right);
        checkScore(0, 5, 6, left, right);
    }

    private static void verifiesPlacementSides() {
        check(SkylineSolver.shouldPlaceRight(2, 8, 4), "左墙较高时2分应靠右");
        check(!SkylineSolver.shouldPlaceRight(4, 8, 4), "左墙较高时4分应靠左");
        check(SkylineSolver.shouldPlaceRight(4, 4, 8), "右墙较高时4分应靠右");
        check(SkylineSolver.shouldPlaceRight(0, 4, 8), "右墙较高时0分应靠右");
        check(!SkylineSolver.shouldPlaceRight(2, 4, 8), "右墙较高时2分应靠左");
    }

    private static void verifiesInfeasibleScore() {
        int score = SkylineSolver.score(11, 1, 10, 0, 8, 4, 10);
        check(score == -1, "宽度超过天际线时应为-1分");
    }

    private static void checkScore(int expected, int width, int height, int left, int right) {
        int actual = SkylineSolver.score(width, height, 10, 0, left, right, 20);
        check(actual == expected, "期望" + expected + "分，实际" + actual + "分");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
