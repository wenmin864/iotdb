package org.apache.iotdb.tsfile.encoding;

import java.util.Arrays;

import static java.lang.Math.pow;

public class GroupU{

    public int[] number;
    public int count;
    public int[] count_array;
    public int range;
    public int unique_number;
    public int mask;
    public int left_shift;
    public long[] sorted_value_list; // cdf
    public int[] invert;
    public int[] invert2;

    public int getCount(long long1) {
        return ((int) (long1 & this.mask));
    }
    public int getUniqueValue(long long1) {
        return ((int) ((long1) >> this.left_shift));
    }

    public static int getBitWith(int num) {
        if (num == 0) return 1;
        else return 32 - Integer.numberOfLeadingZeros(num);
    }

    public GroupU(int[] number, int count, int i) {
        this.number = number;
        this.count = count;
        this.range = (int) pow(2,i-1);
        this.count_array = new int[range];
    }

    public int[] getNumber() {
        return number;
    }


    public int getCount() {
        return count;
    }

    public void addNumber(int number) {
        this.number[this.count] = number;
        this.count++;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setCount_array(int max_){
        int k2_end = max_ - this.range;
        double par = this.range/(count*Math.log(this.count));
        this.left_shift = getBitWith(this.count);
        this.mask = (1 << left_shift) - 1;
        invert = new int[this.range+1];
        if (par > 3) {
            int[] value_list = new int[this.count];
            for (int i = 0; i < this.count; i++) {
                int value = this.number[i];
                count_array[k2_end - value]++;
                if (count_array[k2_end - value] == 1) {
                    value_list[unique_number] = value;
                    unique_number++;
                }
            }
            sorted_value_list = new long[unique_number];
            for (int i = 0; i < unique_number; i++) {
                int value = value_list[i];
                sorted_value_list[i] = (((long) (k2_end - value)) << left_shift) + count_array[k2_end - value];
            }
            Arrays.sort(sorted_value_list);

            int cdf_count = 0;
            for (int i = 0; i < unique_number; i++) {
                cdf_count += getCount(sorted_value_list[i]);
                sorted_value_list[i] = (((long) getUniqueValue(sorted_value_list[i])) << left_shift) + cdf_count;//new_value_list[i]
            }
        }else {
            int[] hash = new int[this.range];
            for (int i = 0; i < this.count; i++) {
                int value = this.number[i];
                if (hash[k2_end - value] == 0) {
                    this.unique_number++;
                }
                hash[k2_end - value]++;
            }
            sorted_value_list = new long[unique_number];
            int ccount = 0;
            int index = 0;
            for (int i = 0; i < this.range; i++) {
                if (hash[i] > 0) {
                    ccount += hash[i];
                    sorted_value_list[index] = (((long) i) << left_shift) + ccount;
                    index++;
                }
            }
        }
    }

    public void setinvert(){
        int count = 0;
        for (int i = 0; i < this.range; i++) {
            invert[i] = count;
            if (count < unique_number && getUniqueValue(sorted_value_list[count]) == i){
                count ++;
            }
        }
        invert[this.range] = count;
    }

    public void incrementCount() {
        count++;
    }

    @Override
    public String toString() {
        return "Number: " + number + ", Count: " + count;
    }

}
