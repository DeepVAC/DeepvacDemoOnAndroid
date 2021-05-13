//
//  SyszuxVggish.h
//  SyszuxVggish
//
//  Created by civilnet on 2020/3/25.
//  Copyright © 2020 civilnet. All rights reserved.
//
//~/github/essentia-master/build
//g++ -DSYSZUX_PALETTE_WITH_OPENCV -std=c++11 -pipe -Wall -msse -msse2 -mfpmath=sse -O2 -fPIC -pthread -Isrc/examples -I../src/examples -Isrc -I../src -I../src/essentia/utils -Isrc/examples/essentia -I../src/examples/essentia -Isrc/examples/essentia/scheduler -I../src/examples/essentia/scheduler -Isrc/examples/essentia/streaming -I../src/examples/essentia/streaming -Isrc/examples/essentia/streaming/algorithms -I../src/examples/essentia/streaming/algorithms -Isrc/examples/essentia/utils -I../src/examples/essentia/utils -Isrc/examples/3rdparty -I../src/examples/3rdparty -Isrc/examples/3rdparty/spline -I../src/examples/3rdparty/spline -Isrc/examples/3rdparty/nnls -I../src/examples/3rdparty/nnls -Isrc/examples/3rdparty/cephes/bessel -I../src/examples/3rdparty/cephes/bessel -I/usr/include/eigen3 -I/usr/include/x86_64-linux-gnu -I/usr/include/taglib -DHAVE_EIGEN3=1 -DHAVE_AVCODEC=1 -DHAVE_AVFORMAT=1 -DHAVE_AVUTIL=1 -DHAVE_AVRESAMPLE=1 -DHAVE_SAMPLERATE=1 -DHAVE_TAGLIB=1 -DHAVE_YAML=1 -DHAVE_FFTW=1 -DHAVE_LIBCHROMAPRINT=1 -D__STDC_CONSTANT_MACROS -DPYTHONDIR="/usr/local/lib/python3/dist-packages" -DPYTHONARCHDIR="/usr/local/lib/python3/dist-packages" -DHAVE_PYEXT=1 -DHAVE_PYTHON_H=1 -DEIGEN_MPL2_ONLY ../src/examples/standard_stft.cpp -c -o/home/gemfield/github/essentia-master/build/src/examples/standard_stft.cpp.o
//g++ -pthread src/examples/standard_stft.cpp.o -o/home/gemfield/github/essentia-master/build/src/examples/essentia_streaming_stft -Wl,-Bstatic -Lsrc -lessentia -Wl,-Bdynamic -lfftw3f -lavformat -lavcodec -lavutil -lavresample -lsamplerate -ltag -lyaml -lchromaprint -lopencv_core -lopencv_imgcodecs
#ifndef syszux_vggish_h
#define syszux_vggish_h

#include <iostream>
#include <memory>
#include <vector>
#include "algorithmfactory.h"

using namespace std;
using namespace essentia;
using namespace essentia::standard;
class SyszuxVggish
{
public:
    SyszuxVggish(){
        essentia::init();
    }
    ~SyszuxVggish(){
        essentia::shutdown();
    }
    vector<unique_ptr<unsigned char[]> > getVggishInput(vector<float> input_audio, float input_sr, int fs=400, int hs=200, int num2cut=319);
};

#endif /* syszux_vggish_h */
