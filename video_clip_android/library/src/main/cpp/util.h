//
// Created by liubaojia on 20-3-26.
//


#ifndef VIDEO_CLIP_UTIL_H_
#define VIDEO_CLIP_UTIL_H_

#include <algorithm>
#include <unistd.h>
#include <string>

class util {

public:
    static int CheckVideoFileValid(const char* path) {
        if(access(path, 0) != 0) return -1;

//        std::string file(path);
//        std::string suffix = file.erase(0,file.find_last_of('.')+1);
//        std::transform(suffix.begin(),suffix.end(),suffix.begin(),tolower);
//
//        if(suffix != "mp4") return -1;
        return 0;
    }


    static int CheckVideoContentValid(const int width, const int height, const float durationS) {
        //width or height valid
        const auto minEdge = std::min(width, height);
        const auto maxEdge = std::max(width, height);
        if(minEdge < 224 || maxEdge > 4096) return -2;

        //video duration valid
        //float time = duration / 1000 / 1000.f;
        //if(time < 6 || time > 300) return -2; // YRS 最大时长不用限制，外部进行处理了
        if(durationS < 6) return -3;
        return 0;
    }
};


#endif //VIDEO_CLIP_UTIL_H
