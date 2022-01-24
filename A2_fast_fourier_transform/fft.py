# ECSE 316 - Assignment 2
# Dina Shoham and Roey Wine

import argparse
import math
import time

import numpy as np
import matplotlib.colors as colors
import matplotlib.pyplot as plt

# naive 1D discrete fourier transform
def naive_ft(x):
    x = np.asarray(x, dtype=complex)
    N = x.shape[0]
    X = np.zeros(N, dtype=complex)  # initialize result array as an array of 0s

    for k in range(N):
        for n in range(N):
            X[k] += x[n] * np.exp(-2j * np.pi * k * n / N)

    return X


# naive 1D inverse discrete fourier transform
def naive_ift(X):
    X = np.asarray(X, dtype=complex)
    N = X.shape[0]
    x = np.zeros(N, dtype=complex)  # initialize result array as an array of 0s

    for n in range(N):
        for k in range(N):
            x[n] += X[k] * np.exp(2j * np.pi * k * n / N)

        x[n] /= N    

    return x


# 1D cooley-tukey fast fourier transform (divide and conquer algorithm)
# parameters are x (an array) and n_subproblems, which defines the base case for the algo (default value is 8)
def fft(x, n_subproblems=16):
    x = np.asarray(x, dtype=complex)
    N = x.shape[0]
    X = np.zeros(N, dtype=complex)

    if N % 2 != 0:
        raise ValueError("array length must be a power of two")

    # base case
    elif N <= n_subproblems:
        return naive_ft(x)

    else:
        X_even = fft(x[::2])  # all elements at even indeces
        X_odd = fft(x[1::2])  # all elements at odd indeces
        #coeff = np.exp(-2j * np.pi * np.arange(N // 2) / N)

        #X = np.concatenate(X_even + coeff * X_odd, X_even - coeff * X_odd)
        X = np.zeros(N, dtype=complex)

        for n in range(N):
            X[n] = X_even[n % (N//2)] + \
                np.exp(-2j * np.pi * n / N) * X_odd[n % (N//2)]

        return X


# 1D cooley-tukey inverse FFT
def ifft(X, n_subproblems=16):
    X = np.asarray(X, dtype=complex)
    N = X.shape[0]
    x = np.zeros(N, dtype=complex)

    if N % 2 != 0:
        raise ValueError("array length must be a power of two")

    # base case
    elif N <= n_subproblems:
        return naive_ift(X)

    else:
        x_even = ifft(X[::2])  # all elements at even indeces
        x_odd = ifft(X[1::2])  # all elements at odd indeces
        #coeff = 1/N * np.exp(2j * np.pi * np.arange(N // 2) / N)

        #x = np.concatenate(x_even + coeff * x_odd, x_even - coeff * x_odd)
        x = np.zeros(N, dtype=complex)

        for n in range(N):
            x[n] = (N//2) * x_even[n % (N//2)] + \
                np.exp(2j * np.pi * n / N) * (N//2) * x_odd[n % (N//2)]
            x[n] /= N

        return x


# 2D fft
def two_dim_fft(x):
    x = np.asarray(x, dtype=complex)
    N, M = x.shape
    X = np.zeros((N, M), dtype=complex)

    for m in range(M):
        X[:, m] = fft(x[:, m])  # fft of all elements in column m

    for n in range(N):
        X[n, :] = fft(X[n, :])  # fft of all elements in fft'ed row n

    return X


# 2D ifft
def two_dim_ifft(X):
    X = np.asarray(X, dtype=complex)
    N, M = X.shape
    x = np.zeros((N, M), dtype=complex)

    for m in range(M):
        x[:, m] = ifft(X[:, m])

    for n in range(N):
        x[n, :] = ifft(x[n, :])

    return x


# Finds the next closest power of 2 to the input
def new_size(size):
    n = int(math.log(size, 2))
    return int(pow(2, n+1))


def resize(oldImage):
    newShape = new_size(oldImage.shape[0]), new_size(oldImage.shape[1])
    newImage = np.zeros(newShape)
    newImage[:oldImage.shape[0], :oldImage.shape[1]] = oldImage
    return newImage


# parsing command line arguments
def parse_args():
    parser = argparse.ArgumentParser(description="parse switches")
    parser.add_argument('-m', action='store', type=int, default=1)
    parser.add_argument('-i', action='store', default='moonlanding.png')

    args = parser.parse_args()

    return args


# mode 1: image is converted into its FFT form and displayed
def mode_1(image):
    print("mode 1")

    # Collect the original image and pad zeroes to get a new image
    oldImage = plt.imread(image).astype(float)
    newImage = resize(oldImage)

    # Apply a fourier transform
    fftImage = two_dim_fft(newImage)

    # Display the original and fourier transformed image
    fig = plt.figure()
    fig.add_subplot(121)
    plt.title("Original Image")
    plt.imshow(oldImage, cmap="gray")
    fig.add_subplot(122)
    plt.title("Fourier Transformed Image With a Log Scale")
    plt.imshow(np.abs(fftImage), norm=colors.LogNorm(vmin=5))
    plt.show()

    return 


# mode 2: for denoising where the image is denoised by applying an FFT, truncating high frequencies and then displayed
def mode_2(image):
    print("mode 2")

    # Define a threshold frequency for values to keep
    keepRatio = 0.08

    # Collect the original image and pad zeroes to get a new image
    oldImage = plt.imread(image).astype(float)
    newImage = resize(oldImage)

    # Apply a fourier transform
    fftImage = two_dim_fft(newImage)
    rows, columns = fftImage.shape

    print("Ratio of pixels used is {} and the number is ({}, {}) out of ({}, {})".format(
        keepRatio, int(keepRatio * rows), int(keepRatio * columns), rows, columns))

    # Set high frequencies to 0
    fftImage[int(rows * keepRatio) : int(rows * (1 - keepRatio))] = 0
    fftImage[:, int(columns * keepRatio) : int(columns * (1 - keepRatio))] = 0

    # Retrieve denoised image by taking inverse fourier transform
    denoisedImage = two_dim_ifft(fftImage).real

    # Display the original and denoised image
    fig = plt.figure()
    fig.add_subplot(121)
    plt.title("Original Image")
    plt.imshow(oldImage, cmap="gray")
    fig.add_subplot(122)
    plt.title("Denoised Image")
    plt.imshow(denoisedImage, cmap="gray")
    plt.show()

    return 


# mode 3: for compressing and saving the image
def mode_3(image):
    print("mode 3")

    # Collect the original image and pad zeroes to get a new image
    oldImage = plt.imread(image).astype(float)
    newImage = resize(oldImage)

    # Apply a fourier transform
    fftImage = two_dim_fft(newImage)

    # Array of different values to use for compression
    compressionValue = [0, 10, 30, 60, 80, 95]
    compressionIndex = 0

    original = oldImage.shape[0] * oldImage.shape[1]

    # Create a 2 by 3 subplot
    fig, plot = plt.subplots(2, 3)
    for i in range(2):
        for j in range(3):

            # Define upper and lower thresholds based on the current compression
            compression = compressionValue[compressionIndex]
            low = np.percentile(fftImage, (100 - compression)//2)
            up = np.percentile(fftImage, 100 - (100 - compression)//2)
            print('Non zero values for compression of {}% are {} out of {}'.format(compression, 
                int(original * ((100 - compression) / 100.0)), original))

            # Compress the image by only taking the largest percentile coefficients
            compressedFftImage = fftImage * np.logical_or(fftImage <= low, fftImage >= up)

            # Save fourier transform coefficients in a txt file
            a = np.asarray(compressedFftImage)
            np.savetxt('coefficients-{}-compression.csr'.format(compression), a)

            # Show the compressed image in the corresponding subplot
            compressedImage = two_dim_ifft(compressedFftImage).real
            plot[i, j].imshow(compressedImage[:oldImage.shape[0], :oldImage.shape[1]], cmap="gray")
            plot[i, j].set_title('{}% compression'.format(compression))

            compressionIndex += 1

    plt.show()
    return 


# mode 4: for plotting the runtime graphs for the report
def mode_4():
    print("mode 4")
    
    fig, ax = plt.subplots()
    ax.set_xlabel('Problem Size')
    ax.set_ylabel('Runtime (s)')
    ax.set_title('Mode 4 Plot')

    data_x = np.zeros(5) 
    fft_averages = np.zeros(5)
    fft_stdevs = np.zeros(5)

    fft_times = np.zeros(10)

    for n in range(5, 10): 
        print("Input size " + str(2**n) + " x " + str(2**n))
        test_x = np.random.rand(2**n, 2**n)

        for i in range(10):
            print("Running trial " + str(i)) 
            t_i = time.time()
            two_dim_fft(test_x)
            t_f = time.time()
            fft_times[i] = (t_f - t_i)

        data_x[n-5] = 2**n
        fft_averages[n-5] = np.average(fft_times)
        fft_stdevs[n-5] = np.std(fft_times)
        
    plt.errorbar(data_x, fft_averages, yerr=(fft_stdevs * 2), fmt='r--')
    
    print("data_x: ")
    print(data_x)
    print("averages: ")
    print(fft_averages)
    print("std devs: ")
    print(fft_stdevs)
    
    plt.show()

    return 


def test():
    print("testing")
    # one dimension
    a = np.random.random(1024)
    fft1 = np.fft.fft(a)

    # two dimensions
    a2 = np.random.rand(32, 32)
    fft2 = np.fft.fft2(a2)

    tests = (
        (naive_ft, a, fft1),
        (naive_ift, fft1, a),
        (fft, a, fft1),
        (ifft, fft1, a),
        (two_dim_fft, a2, fft2),
        (two_dim_ifft, fft2, a2),
    )

    for method, args, expected in tests:
        if not np.allclose(method(args), expected):
            print(args)
            print(method(args))
            print(expected)
            raise AssertionError(
                "{} failed the test".format(method.__name__))

def main():
    # test()
    args = parse_args()
    img = args.i
    # print(args.m)
    # print(args.i)
    if args.m == 1:
        mode_1(img)
    elif args.m == 2:
        mode_2(img)
    elif args.m == 3:
        mode_3(img)
    elif args.m == 4:
        mode_4()
    else:
        raise ValueError("Mode not recognized. Please enter an integer from 1-4.")


main()
