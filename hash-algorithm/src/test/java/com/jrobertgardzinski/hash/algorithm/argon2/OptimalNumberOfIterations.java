package com.jrobertgardzinski.hash.algorithm.argon2;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Helper;

import static com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm.MEM_LIMIT;
import static com.jrobertgardzinski.hash.algorithm.argon2.Argon2HashAlgorithm.PARALLELISM;

/**
 * Developer utility — run main() to find the optimal ITERATIONS value
 * for the current hardware.
 *
 * @see <a href="https://github.com/phxql/argon2-jvm?tab=readme-ov-file#recommended-parameters">argon2-jvm recommended parameters</a>
 */
public class OptimalNumberOfIterations {

    public static void main(String[] args) {
        long REQUIRED_CALCULATION_TIME = 1000;

        Argon2 argon2 = Argon2Factory.create();
        int iterations = Argon2Helper.findIterations(argon2, REQUIRED_CALCULATION_TIME, MEM_LIMIT, PARALLELISM);

        System.out.println("Set ITERATIONS for Argon2HashAlgorithm equal to: " + iterations);
    }
}
