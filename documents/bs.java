// Implement binary search algorithm in Java here
public class bs {
    public static int binarySearch(int[] arr, int target) {
        int left = 0;
        int right = arr.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2; // Prevent potential overflow

            if (arr[mid] == target) {
                return mid; // Target found
            } else if (arr[mid] < target) {
                left = mid + 1; // Search in the right half
            } else {
                right = mid - 1; // Search in the left half
            }
        }

        return -1; // Target not found
    }

    public static void main(String[] args) {
        int[] arr = {2, 5, 7, 8, 11, 12};
        int target = 13;
        int result = binarySearch(arr, target);
        if (result == -1) {
            System.out.println("Element is not present in array");
        } else {
            System.out.println("Element is found at index: " + result);
        }
    }
}
